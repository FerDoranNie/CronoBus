package com.cronobus.app.bridge

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.room.withTransaction
import com.cronobus.app.data.local.CronoBusDatabase
import com.cronobus.app.data.local.IncidenceType
import com.cronobus.app.data.local.OccupancyLevel
import com.cronobus.app.data.local.entity.FineDisputeEntity
import com.cronobus.app.data.local.entity.IncidenceReportEntity
import com.cronobus.app.data.local.entity.ShiftLogEntity
import com.cronobus.app.data.sync.StoreAndForwardSyncManager
import com.cronobus.app.domain.model.GeofenceEvent
import com.cronobus.app.domain.model.LocationUpdate
import com.cronobus.app.domain.model.TamperEvent
import com.cronobus.app.domain.model.TamperType
import com.cronobus.app.service.LocationTrackingService
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Puente bidireccional entre la SPA local ([cronobus.html]) y el núcleo nativo.
 *
 * ## Dirección JS → Kotlin (`window.CronoBusNative`)
 * Solo se exponen cuatro comandos de negocio, con tipos primitivos (el puente de Android no
 * admite objetos ni callbacks):
 * - [startShift]: registra el turno con checklist LFT/LGPDPPSO y arranca el GPS en primer plano.
 * - [reportIncidence]: persiste una incidencia georreferenciada en la cola Store & Forward.
 * - [disputeFine]: guarda la apelación con la telemetría GPS adjunta como evidencia.
 * - [reportOccupancy]: actualiza el aforo agregado (`BAJA` / `MEDIA` / `LLENO`).
 *
 * ## Dirección Kotlin → JS (`window.CronoBus.*`)
 * Las llamadas se ejecutan siempre en el hilo principal mediante `evaluateJavascript`, con el
 * payload serializado por [JSONObject.quote] (nunca por concatenación de strings). Esto evita
 * inyección de JavaScript desde nombres de ruta, alias del conductor o descripciones de incidencia.
 *
 * ## Seguridad
 * - El puente solo se registra en el `WebView` local; el cliente bloquea cualquier navegación
 *   fuera de `file:///android_asset/` (ver `MainActivity`).
 * - Los métodos validan longitud y rango de cada argumento: el contenido de la SPA no se asume
 *   confiable aunque sea un asset propio.
 * - Un incidente o apelación exige un *fix* GPS fresco y no simulado; sin evidencia no se crea
 *   el registro (evita apelaciones con coordenadas inventadas).
 * - No se persiste ningún dato biométrico ni identificador de pasajero.
 */
class CronoBusJsBridge(
    context: Context,
    private val database: CronoBusDatabase = CronoBusDatabase.getInstance(context),
    private val syncManager: StoreAndForwardSyncManager =
        StoreAndForwardSyncManager.getInstance(context),
) {

    private val appContext = context.applicationContext
    private val shiftDao = database.shiftLogDao()
    private val incidenceDao = database.incidenceReportDao()
    private val disputeDao = database.fineDisputeDao()

    /**
     * Auditoría Paso 3: las escrituras en Room viven en un scope de **proceso**, no de Activity.
     * Antes `close()` cancelaba este scope en `onDestroy`, de modo que rotar la pantalla a mitad
     * de un `startShift` / `reportIncidence` podía abortar la transacción y perder evidencia.
     */
    private val scope = processScope
    private val mainHandler = Handler(Looper.getMainLooper())
    private val shiftMutex = Mutex()

    /** Evita turnos duplicados por doble toque en "Iniciar Turno" mientras el primero persiste. */
    private val shiftStartInFlight = AtomicBoolean(false)

    @Volatile
    private var webViewRef: WeakReference<WebView>? = null

    /** Turno abierto en memoria; la base local sigue siendo la fuente de verdad. */
    @Volatile
    var activeShift: ShiftLogEntity? = null
        private set

    /**
     * Aforo agregado vigente. Se adjunta al siguiente [CheckpointEventEntity] que persista el
     * checador digital (Fase 4); el esquema no guarda ocupación como tabla independiente.
     */
    @Volatile
    var occupancyLevel: OccupancyLevel = OccupancyLevel.BAJA
        private set

    /** La Activity registra aquí el lanzador de permisos de ubicación (debe correr en main). */
    @Volatile
    var onLocationPermissionRequired: (() -> Unit)? = null

    @Volatile
    private var awaitingLocationPermission = false

    init {
        // Recupera el turno tras recrear la Activity o reiniciar el proceso.
        scope.launch {
            runCatching { shiftDao.getActiveShift() }
                .onSuccess { activeShift = it }
                .onFailure { Log.w(TAG, "No se pudo recuperar el turno activo.", it) }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Ciclo de vida del WebView
    // ---------------------------------------------------------------------------------------------

    fun attach(webView: WebView) {
        webViewRef = WeakReference(webView)
    }

    fun detach(webView: WebView) {
        if (webViewRef?.get() === webView) webViewRef = null
    }

    /** Reemite el estado actual cuando la SPA termina de cargar (evita perder el primer push). */
    fun onPageReady() {
        pushSyncState(
            pendingCount = syncManager.pendingCount.value,
            online = syncManager.isOnline.value,
            syncing = syncManager.isSyncing.value,
        )
        LocationTrackingService.latestUpdate.value?.let(::pushTelemetry)
    }

    fun close() {
        // Solo se desconecta la UI; las escrituras en curso terminan en `processScope`.
        webViewRef = null
        onLocationPermissionRequired = null
    }

    // ---------------------------------------------------------------------------------------------
    // JS → Kotlin
    // ---------------------------------------------------------------------------------------------

    @JavascriptInterface
    fun startShift(
        driverAlias: String,
        unitId: String,
        routeId: String,
        brakesOk: Boolean,
        lightsOk: Boolean,
        oilOk: Boolean,
        consentAccepted: Boolean,
    ) = jsGuard("startShift") {
        if (!consentAccepted) {
            pushError(
                "startShift",
                "Se requiere aceptar el consentimiento de geolocalización laboral (LFT/LGPDPPSO).",
            )
            return@jsGuard
        }

        if (awaitingLocationPermission) {
            pushError("startShift", "Ya hay una solicitud de permiso de ubicación en curso.")
            return@jsGuard
        }

        val alias = driverAlias.normalized(MAX_ALIAS_LENGTH)
        val unit = unitId.normalized(MAX_UNIT_LENGTH)
        val route = routeId.normalized(MAX_ROUTE_LENGTH)
        if (alias.isBlank() || unit.isBlank() || route.isBlank()) {
            pushError("startShift", "Alias, unidad y ruta son obligatorios.")
            return@jsGuard
        }

        if (!shiftStartInFlight.compareAndSet(false, true)) {
            pushError("startShift", "El turno ya se está registrando.")
            return@jsGuard
        }

        scope.launch {
            try {
                startShiftLocked(alias, unit, route, brakesOk, lightsOk, oilOk)
            } finally {
                shiftStartInFlight.set(false)
            }
        }
    }

    private suspend fun startShiftLocked(
        alias: String,
        unit: String,
        route: String,
        brakesOk: Boolean,
        lightsOk: Boolean,
        oilOk: Boolean,
    ) {
        shiftMutex.withLock {
            try {
                val nowUtcMs = System.currentTimeMillis()
                val elapsedNanos = SystemClock.elapsedRealtimeNanos()
                val shift = ShiftLogEntity(
                    shiftId = UUID.randomUUID().toString(),
                    driverAlias = alias,
                    unitId = unit,
                    routeId = route,
                    brakesOk = brakesOk,
                    lightsOk = lightsOk,
                    oilOk = oilOk,
                    privacyAndLftConsentAccepted = true,
                    consentTimestampUtcMs = nowUtcMs,
                    startTimestampUtcMs = nowUtcMs,
                    startElapsedRealtimeNanos = elapsedNanos,
                )

                database.withTransaction {
                    // Un turno nuevo cierra el anterior: nunca quedan dos turnos abiertos.
                    shiftDao.getActiveShift()?.let { previous ->
                        shiftDao.updateEndShift(previous.shiftId, nowUtcMs, elapsedNanos)
                    }
                    shiftDao.insert(shift)
                }

                activeShift = shift
                occupancyLevel = OccupancyLevel.BAJA

                if (LocationTrackingService.hasLocationPermission(appContext)) {
                    startTrackingAndReport(shift)
                } else {
                    awaitingLocationPermission = true
                    pushShiftStarted(shift, trackingActive = false, permissionRequired = true)
                    val requestPermission = onLocationPermissionRequired
                    if (requestPermission != null) {
                        mainHandler.post(requestPermission)
                    } else {
                        Log.w(TAG, "Sin lanzador de permisos registrado por la Activity.")
                    }
                }
            } catch (failure: Throwable) {
                Log.e(TAG, "No fue posible registrar el turno.", failure)
                pushError("startShift", "No se pudo guardar el turno en la base local.")
            }
        }
    }

    /**
     * Invocado por la Activity cuando el conductor concede o niega los permisos de ubicación.
     * Debe llamarse en el hilo principal.
     *
     * Auditoría Paso 3: si la Activity se recreó mientras el diálogo estaba abierto (rotación),
     * el resultado llega a un puente nuevo con `awaitingLocationPermission = false`. Antes se
     * descartaba y el turno quedaba sin GPS; ahora se arranca el rastreo si hay turno abierto.
     */
    fun onLocationPermissionResult(granted: Boolean) {
        val wasAwaiting = awaitingLocationPermission
        awaitingLocationPermission = false

        if (!granted) {
            if (wasAwaiting) {
                pushError(
                    "startShift",
                    "Ubicación denegada: el turno quedó registrado, pero el GPS no se activó.",
                )
            }
            return
        }

        if (LocationTrackingService.isRunning.value) return
        scope.launch {
            val shift = currentShiftOrLoad() ?: return@launch
            mainHandler.post { startTrackingAndReport(shift) }
        }
    }

    @JavascriptInterface
    fun reportIncidence(type: String) = jsGuard("reportIncidence") {
        val incidenceType = parseIncidenceType(type)
        if (incidenceType == null) {
            pushError("reportIncidence", "Tipo de incidencia no reconocido.")
            return@jsGuard
        }

        val fix = freshFix()
        if (fix == null) {
            pushError(
                "reportIncidence",
                "Sin GPS válido todavía: espera unos segundos para georreferenciar el reporte.",
            )
            return@jsGuard
        }

        scope.launch {
            try {
                val shift = currentShiftOrLoad()
                if (shift == null) {
                    pushError("reportIncidence", "No hay un turno activo.")
                    return@launch
                }

                val nowUtcMs = System.currentTimeMillis()
                val report = IncidenceReportEntity(
                    shiftId = shift.shiftId,
                    unitId = shift.unitId,
                    routeId = shift.routeId,
                    incidenceType = incidenceType,
                    description = "Reporte del conductor: ${incidenceType.name.lowercase(Locale.ROOT)}",
                    latitude = fix.position.latitude,
                    longitude = fix.position.longitude,
                    timestampUtcMs = nowUtcMs,
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
                )
                val localId = incidenceDao.insert(report)

                pushEvent(
                    "incidenceReported",
                    JSONObject()
                        .put("localId", localId)
                        .put("type", incidenceType.name)
                        .put("shiftId", shift.shiftId)
                        .put("lat", fix.position.latitude)
                        .put("lng", fix.position.longitude)
                        .put("pendingSyncCount", syncManager.pendingCount.value),
                )
            } catch (failure: Throwable) {
                Log.e(TAG, "No fue posible guardar la incidencia.", failure)
                pushError("reportIncidence", "No se pudo persistir la incidencia.")
            }
        }
    }

    @JavascriptInterface
    fun disputeFine(fineId: String, reason: String) = jsGuard("disputeFine") {
        val normalizedFineId = fineId.normalized(MAX_FINE_ID_LENGTH)
        val normalizedReason = reason.normalized(MAX_REASON_LENGTH)
        if (normalizedFineId.isBlank() || normalizedReason.isBlank()) {
            pushError("disputeFine", "La multa y el motivo de la apelación son obligatorios.")
            return@jsGuard
        }

        val fix = freshFix()
        if (fix == null) {
            pushError(
                "disputeFine",
                "La apelación requiere un fix GPS reciente como evidencia.",
            )
            return@jsGuard
        }

        scope.launch {
            try {
                val shift = currentShiftOrLoad()
                if (shift == null) {
                    pushError("disputeFine", "No hay un turno activo.")
                    return@launch
                }

                val nowUtcMs = System.currentTimeMillis()
                val telemetry = JSONObject()
                    .put("schemaVersion", TELEMETRY_SCHEMA_VERSION)
                    .put("shiftId", shift.shiftId)
                    .put("unitId", shift.unitId)
                    .put("routeId", shift.routeId)
                    .put("occupancy", occupancyLevel.name)
                    .put("latestFix", fix.toJson())
                    .put("pendingSyncCount", syncManager.pendingCount.value)
                    .put("online", syncManager.isOnline.value)
                    .put("capturedAtUtcMs", nowUtcMs)
                    .put("capturedElapsedRealtimeNanos", SystemClock.elapsedRealtimeNanos())

                val dispute = FineDisputeEntity(
                    fineId = normalizedFineId,
                    shiftId = shift.shiftId,
                    unitId = shift.unitId,
                    reason = normalizedReason,
                    photoEvidenceUri = null, // La cámara se conecta en la Fase 4.
                    attachedTelemetryJson = telemetry.toString(),
                    latitude = fix.position.latitude,
                    longitude = fix.position.longitude,
                    timestampUtcMs = nowUtcMs,
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
                )
                val localId = disputeDao.insert(dispute)

                pushEvent(
                    "disputeFiled",
                    JSONObject()
                        .put("localId", localId)
                        .put("fineId", normalizedFineId)
                        .put("pendingSyncCount", syncManager.pendingCount.value),
                )
            } catch (failure: Throwable) {
                Log.e(TAG, "No fue posible guardar la apelación.", failure)
                pushError("disputeFine", "No se pudo persistir la apelación.")
            }
        }
    }

    @JavascriptInterface
    fun reportOccupancy(level: String) = jsGuard("reportOccupancy") {
        val parsed = parseOccupancy(level)
        if (parsed == null) {
            pushError("reportOccupancy", "Nivel de aforo no reconocido (BAJA/MEDIA/LLENO).")
            return@jsGuard
        }

        scope.launch {
            val shift = currentShiftOrLoad()
            if (shift == null) {
                pushError("reportOccupancy", "No hay un turno activo.")
                return@launch
            }
            occupancyLevel = parsed
            pushEvent(
                "occupancyUpdated",
                JSONObject()
                    .put("level", parsed.name)
                    .put("shiftId", shift.shiftId),
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Kotlin → JS
    // ---------------------------------------------------------------------------------------------

    fun pushTelemetry(update: LocationUpdate) {
        val payload = update.toJson()
        payload.put("shiftId", activeShift?.shiftId ?: JSONObject.NULL)
        payload.put("occupancy", occupancyLevel.name)
        payload.put("trackingActive", LocationTrackingService.isRunning.value)
        callJs("onTelemetry", payload)
    }

    fun pushGeofenceEvent(event: GeofenceEvent) {
        callJs(
            "onGeofence",
            JSONObject()
                .put("zoneId", event.zoneId)
                .put("zoneName", event.zoneName)
                .put("transition", event.transition.name)
                .put("lat", event.position.latitude)
                .put("lng", event.position.longitude)
                .put("distanceToBoundaryMeters", event.signedDistanceToBoundaryMeters)
                .put("occurredAtMonotonicNanos", event.occurredAtMonotonicNanos)
                .put("confirmedAtMonotonicNanos", event.confirmedAtMonotonicNanos)
                .put("dwellMillis", event.dwellMillis)
                .put("shiftId", activeShift?.shiftId ?: JSONObject.NULL),
        )
    }

    fun pushTamperAlert(event: TamperEvent) {
        val payload = JSONObject()
            .put("type", event.type.name)
            .put("blocking", event.type.isBlocking())
            .put("description", event.description)
            .put("detectedAtMonotonicNanos", event.detectedAtMonotonicNanos)
            .put("detectedAtEpochMs", event.detectedAtEpochMs)
            .putOpt("distanceToNearestZoneMeters", event.distanceToNearestZoneMeters)
            .putOpt("speedMps", event.speedMps)
            .putOpt("batteryLevelPct", event.batteryLevelPct)
            .putOpt("isCharging", event.isCharging)
            .put("position", event.position?.toJson() ?: JSONObject.NULL)
        callJs("onTamper", payload)
    }

    /** Badge `SYNC: N PEND`; se mantiene por compatibilidad con la integración de Step 1. */
    fun pushSyncQueueCount(count: Int) {
        pushSyncState(count, syncManager.isOnline.value, syncManager.isSyncing.value)
    }

    fun pushSyncState(pendingCount: Int, online: Boolean, syncing: Boolean) {
        callJs(
            "onSyncQueue",
            JSONObject()
                .put("count", pendingCount)
                .put("online", online)
                .put("syncing", syncing),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Internos
    // ---------------------------------------------------------------------------------------------

    private fun startTrackingAndReport(shift: ShiftLogEntity) {
        val trackingActive = LocationTrackingService.start(appContext)
        pushShiftStarted(shift, trackingActive, permissionRequired = false)
        if (!trackingActive) {
            pushError(
                "startShift",
                "El turno quedó guardado, pero el sistema rechazó el rastreo en primer plano.",
            )
        }
    }

    private fun pushShiftStarted(
        shift: ShiftLogEntity,
        trackingActive: Boolean,
        permissionRequired: Boolean,
    ) {
        pushEvent(
            "shiftStarted",
            JSONObject()
                .put("shiftId", shift.shiftId)
                .put("unitId", shift.unitId)
                .put("routeId", shift.routeId)
                .put("trackingActive", trackingActive)
                .put("permissionRequired", permissionRequired)
                .put("pendingSyncCount", syncManager.pendingCount.value),
        )
    }

    private suspend fun currentShiftOrLoad(): ShiftLogEntity? {
        activeShift?.let { return it }
        return runCatching { shiftDao.getActiveShift() }
            .onFailure { Log.w(TAG, "No se pudo leer el turno activo.", it) }
            .getOrNull()
            ?.also { activeShift = it }
    }

    /**
     * Solo acepta un fix de los últimos 60 s y no simulado. El filtro de Kalman ya publica valores
     * finitos; si el servicio está detenido, el fix viejo se descarta para no firmar evidencia falsa.
     */
    private fun freshFix(): LocationUpdate? {
        val update = LocationTrackingService.latestUpdate.value ?: return null
        if (update.isMock) return null
        val ageNanos = SystemClock.elapsedRealtimeNanos() - update.elapsedRealtimeNanos
        return if (ageNanos in 0L..MAX_FIX_AGE_NANOS) update else null
    }

    private fun LocationUpdate.toJson(): JSONObject = JSONObject()
        .put("lat", position.latitude)
        .put("lng", position.longitude)
        .put("speedMps", speedMps)
        .put("speedKmh", speedKmh)
        .put("bearingDegrees", bearingDegrees)
        .put("accuracyMeters", accuracyMeters)
        .put("sourceAccuracyMeters", sourceAccuracyMeters)
        .put("isFiltered", isFiltered)
        .put("isMock", isMock)
        .put("elapsedRealtimeNanos", elapsedRealtimeNanos)
        .put("timestampUtcMs", wallClockEpochMs)
        .putOpt("altitudeMeters", altitudeMeters)
        .putOpt("provider", provider)

    private fun com.cronobus.app.domain.model.GeoPoint.toJson(): JSONObject = JSONObject()
        .put("lat", latitude)
        .put("lng", longitude)

    private fun pushError(operation: String, message: String) {
        pushEvent(
            "error",
            JSONObject()
                .put("operation", operation)
                .put("message", message),
        )
    }

    private fun pushEvent(event: String, payload: JSONObject) {
        payload.put("event", event)
        payload.put("emittedAtUtcMs", System.currentTimeMillis())
        callJs("onNativeEvent", payload)
    }

    private fun callJs(function: String, payload: JSONObject) {
        val webView = webViewRef?.get() ?: return
        val script = buildString {
            append("window.CronoBus&&typeof window.CronoBus.")
            append(function)
            append("==='function'&&window.CronoBus.")
            append(function)
            append("(JSON.parse(")
            append(JSONObject.quote(payload.toString()))
            append("));")
        }

        val dispatch = Runnable {
            try {
                webView.evaluateJavascript(script, null)
            } catch (failure: Throwable) {
                // El WebView pudo destruirse entre el chequeo y la ejecución.
                Log.d(TAG, "evaluateJavascript descartado (WebView no disponible).")
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            dispatch.run()
        } else {
            mainHandler.post(dispatch)
        }
    }

    private inline fun jsGuard(operation: String, block: () -> Unit) {
        try {
            block()
        } catch (failure: Throwable) {
            Log.e(TAG, "Fallo en el comando JS $operation.", failure)
            pushError(operation, "Error interno del puente nativo.")
        }
    }

    private fun String.normalized(maxLength: Int): String = trim().take(maxLength)

    private fun parseIncidenceType(raw: String): IncidenceType? =
        when (raw.trim().lowercase(Locale.ROOT)) {
            "trafico", "tráfico", "traffic" -> IncidenceType.TRAFFIC
            "bloqueo", "blockade", "block" -> IncidenceType.BLOCKADE
            "accidente", "accident" -> IncidenceType.ACCIDENT
            "falla", "mecanica", "mecánica", "mechanical_failure", "mechanical" ->
                IncidenceType.MECHANICAL_FAILURE

            else -> runCatching {
                IncidenceType.valueOf(raw.trim().uppercase(Locale.ROOT))
            }.getOrNull()
        }

    private fun parseOccupancy(raw: String): OccupancyLevel? =
        when (raw.trim().lowercase(Locale.ROOT)) {
            "baja", "low" -> OccupancyLevel.BAJA
            "media", "medium", "mid" -> OccupancyLevel.MEDIA
            "lleno", "full", "high" -> OccupancyLevel.LLENO
            else -> runCatching {
                OccupancyLevel.valueOf(raw.trim().uppercase(Locale.ROOT))
            }.getOrNull()
        }

    private fun TamperType.isBlocking(): Boolean =
        this == TamperType.MOCK_LOCATION || this == TamperType.CLOCK_MANIPULATION

    companion object {
        private const val TAG = "CronoBusJsBridge"

        /**
         * Scope de escrituras compartido por todas las instancias del puente (una por Activity).
         * Nunca se cancela: vive lo mismo que el proceso, igual que la base y el sincronizador.
         */
        private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Nombre exacto del objeto inyectado en el contexto JavaScript. */
        const val JS_INTERFACE_NAME = "CronoBusNative"

        private const val TELEMETRY_SCHEMA_VERSION = 1
        private const val MAX_FIX_AGE_NANOS = 60_000L * 1_000_000L

        private const val MAX_ALIAS_LENGTH = 64
        private const val MAX_UNIT_LENGTH = 32
        private const val MAX_ROUTE_LENGTH = 96
        private const val MAX_FINE_ID_LENGTH = 64
        private const val MAX_REASON_LENGTH = 500
    }
}
