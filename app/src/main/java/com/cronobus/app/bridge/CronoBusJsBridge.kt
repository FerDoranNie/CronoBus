package com.cronobus.app.bridge

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.room.withTransaction
import com.cronobus.app.data.export.DisputePhoto
import com.cronobus.app.data.export.DisputePhotoStore
import com.cronobus.app.data.export.FinancialCsvExporter
import com.cronobus.app.data.local.CronoBusDatabase
import com.cronobus.app.data.local.IncidenceType
import com.cronobus.app.data.local.OccupancyLevel
import com.cronobus.app.data.local.dao.CheckpointEventDao
import com.cronobus.app.data.local.dao.IncidenceReportDao
import com.cronobus.app.data.local.entity.CheckpointEventEntity
import com.cronobus.app.data.local.entity.FinancialLedgerEntity
import com.cronobus.app.data.local.entity.FineDisputeEntity
import com.cronobus.app.data.local.entity.IncidenceReportEntity
import com.cronobus.app.data.local.entity.ShiftLogEntity
import com.cronobus.app.data.sync.StoreAndForwardSyncManager
import com.cronobus.app.domain.model.DispatchQueueSlot
import com.cronobus.app.domain.model.FinancialCsvRow
import com.cronobus.app.domain.model.ForceMajeureResult
import com.cronobus.app.domain.model.GeoPoint
import com.cronobus.app.domain.model.GeofenceEvent
import com.cronobus.app.domain.model.GeofenceTransition
import com.cronobus.app.domain.model.HeadwayAssessment
import com.cronobus.app.domain.model.HeadwayStatus
import com.cronobus.app.domain.model.LocationUpdate
import com.cronobus.app.domain.model.PassengerEtaQuote
import com.cronobus.app.domain.model.TamperEvent
import com.cronobus.app.domain.model.TamperType
import com.cronobus.app.domain.route.RouteCatalog
import com.cronobus.app.domain.usecase.DispatchCandidate
import com.cronobus.app.domain.usecase.EvaluateForceMajeureUseCase
import com.cronobus.app.domain.usecase.FairDispatchRotationUseCase
import com.cronobus.app.domain.usecase.HeadwayCalculatorUseCase
import com.cronobus.app.domain.usecase.PassengerEtaCalculatorUseCase
import com.cronobus.app.service.LocationTrackingService
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Puente bidireccional entre la SPA local ([cronobus.html]) y el núcleo nativo.
 *
 * ## Dirección JS → Kotlin (`window.CronoBusNative`)
 * Se exponen comandos de negocio con tipos primitivos (el puente de Android no admite objetos ni
 * callbacks):
 * - [startShift]: registra el turno con checklist LFT/LGPDPPSO y arranca el GPS en primer plano.
 * - [reportIncidence]: persiste una incidencia georreferenciada en la cola Store & Forward.
 * - [disputeFine]: guarda la apelación con la telemetría GPS y la foto de evidencia adjunta.
 * - [reportOccupancy]: actualiza el aforo agregado (`BAJA` / `MEDIA` / `LLENO`).
 * - [captureDisputePhoto] / [discardDisputePhoto]: captura y libera evidencia con FileProvider.
 * - [exportFinancialCsv]: genera el CSV real del Modelo Financiero Mixto.
 * - [requestPassengerEta]: cotiza el arribo al pasajero con velocidad Kalman y retrasos.
 * - [requestDispatchQueue] / [dispatchNextUnit]: cola rotativa equitativa del despacho.
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
    private val incidenceDao: IncidenceReportDao = database.incidenceReportDao(),
    private val checkpointDao: CheckpointEventDao = database.checkpointEventDao(),
    private val forceMajeureUseCase: EvaluateForceMajeureUseCase =
        EvaluateForceMajeureUseCase(incidenceDao),
    private val headwayUseCase: HeadwayCalculatorUseCase =
        HeadwayCalculatorUseCase(checkpointDao),
    private val etaUseCase: PassengerEtaCalculatorUseCase = PassengerEtaCalculatorUseCase(),
    private val dispatchUseCase: FairDispatchRotationUseCase = FairDispatchRotationUseCase(),
    private val csvExporter: FinancialCsvExporter = FinancialCsvExporter(context),
    private val photoStore: DisputePhotoStore = DisputePhotoStore(context),
) {

    private val appContext = context.applicationContext
    private val shiftDao = database.shiftLogDao()
    private val disputeDao = database.fineDisputeDao()
    private val ledgerDao = database.financialLedgerDao()

    /**
     * Auditoría Paso 3: las escrituras en Room viven en un scope de **proceso**, no de Activity.
     * Antes `close()` cancelaba este scope en `onDestroy`, de modo que rotar la pantalla a mitad
     * de un `startShift` / `reportIncidence` podía abortar la transacción y perder evidencia.
     */
    private val scope = processScope
    private val mainHandler = Handler(Looper.getMainLooper())
    private val shiftMutex = Mutex()

    /**
     * Auditoría Paso 3 (S3): cada ENTER lanzaba una corrutina independiente. Dos cruces casi
     * simultáneos podían leer el mismo `getPreviousCrossing` antes de insertar y duplicar headway
     * y sanciones en el ledger. Se serializa el registro completo del checador.
     */
    private val checkpointMutex = Mutex()

    /**
     * Hidratación única del ledger financiero desde Room. Varias corrutinas (resumen, despacho,
     * exportación CSV y cruces del checador) pueden pedir el estado a la vez; el mutex evita leer
     * la tabla dos veces y publicar un resumen incompleto.
     */
    private val ledgerHydrationMutex = Mutex()

    @Volatile
    private var ledgerHydrated = false

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
     *
     * Vive en [SessionState] para sobrevivir a una rotación de Activity: el conductor no debe
     * perder el aforo reportado si gira el teléfono.
     */
    var occupancyLevel: OccupancyLevel
        get() = SessionState.occupancyLevel
        private set(value) {
            SessionState.occupancyLevel = value
        }

    /** La Activity registra aquí el lanzador de permisos de ubicación (debe correr en main). */
    @Volatile
    var onLocationPermissionRequired: (() -> Unit)? = null

    /** La Activity registra aquí el lanzador de cámara para evidencia de apelaciones. */
    @Volatile
    var onCapturePhotoRequested: ((Uri) -> Unit)? = null

    /** La Activity registra aquí la hoja de compartir del CSV exportado. */
    @Volatile
    var onCsvReadyForShare: ((Uri) -> Unit)? = null

    @Volatile
    private var awaitingLocationPermission = false

    /**
     * Métricas financieras de la sesión por unidad.
     *
     * `grossFinesMxn` incluye el monto teórico de sanciones que luego fueron condonadas; así el
     * CSV puede mostrar bruto, condonado y neto sin perder trazabilidad.
     */
    private data class UnitFinancialState(
        val routeId: String,
        val grossFinesMxn: Double = 0.0,
        val forgivenFinesMxn: Double = 0.0,
        val assessments: Int = 0,
        val exemptions: Int = 0,
    ) {
        val netFinesMxn: Double
            get() = (grossFinesMxn - forgivenFinesMxn).coerceAtLeast(0.0)
    }

    private data class EtaRequest(
        val routeId: String,
        val stopId: String,
    )

    /**
     * Estado de sesión compartido por las instancias del puente. La Activity se recrea en cada
     * rotación, pero la cola de despacho, el aforo, las métricas del CSV y la foto pendiente deben
     * sobrevivir mientras viva el proceso.
     *
     * `ledger` es una caché de lectura del ledger persistido en Room (`financial_ledger`). Al
     * arrancar el proceso se hidrata desde la base; cada evaluación se escribe de forma
     * incremental y transaccional, de modo que una muerte del proceso no pierde sanciones ni
     * condonaciones antes de exportar el `.csv`.
     */
    private object SessionState {
        val ledger = ConcurrentHashMap<String, UnitFinancialState>()
        val lastDepartureByUnit = ConcurrentHashMap<String, Long>()
        val speedSamples = ArrayDeque<Double>()
        val dispatchMutex = Mutex()

        @Volatile
        var dispatchQueue: List<DispatchQueueSlot> = emptyList()

        @Volatile
        var pendingPhoto: DisputePhoto? = null

        @Volatile
        var lastEtaRequest: EtaRequest? = null

        @Volatile
        var lastEtaRefreshMs: Long = 0L

        @Volatile
        var occupancyLevel: OccupancyLevel = OccupancyLevel.BAJA
    }

    init {
        // Recupera el ledger antes de emitir el primer resumen financiero.
        scope.launch { ensureFinancialLedgerHydrated() }

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
        scope.launch {
            currentShiftOrLoad()?.let { shift ->
                LocationTrackingService.setGeofenceZones(RouteCatalog.zonesFor(shift.routeId))
                if (
                    !LocationTrackingService.isRunning.value &&
                    LocationTrackingService.hasLocationPermission(appContext)
                ) {
                    mainHandler.post { startTrackingAndReport(shift) }
                }
            }
            refreshDispatchQueue()
            pushFinancialSummary()
        }
    }

    fun close() {
        // Solo se desconecta la UI; las escrituras en curso terminan en `processScope`.
        webViewRef = null
        onLocationPermissionRequired = null
        onCapturePhotoRequested = null
        onCsvReadyForShare = null
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

                // Geocercas de la ruta: el checador digital nativo y el headway usan los mismos
                // puntos que pinta la SPA. Para rutas sin catálogo se limpian las zonas previas.
                LocationTrackingService.setGeofenceZones(RouteCatalog.zonesFor(route))

                refreshDispatchQueue()
                pushFinancialSummary()

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

                // La incidencia recién persistida ya cuenta para la ventana de fuerza mayor.
                val forceMajeure = forceMajeureUseCase.evaluate(
                    routeId = shift.routeId,
                    position = GeoPoint(report.latitude, report.longitude),
                    referenceTimeUtcMs = nowUtcMs,
                )
                pushForceMajeure(forceMajeure)

                pushEvent(
                    "incidenceReported",
                    JSONObject()
                        .put("localId", localId)
                        .put("type", incidenceType.name)
                        .put("shiftId", shift.shiftId)
                        .put("lat", fix.position.latitude)
                        .put("lng", fix.position.longitude)
                        .put("forceMajeureExempt", forceMajeure.isExempt)
                        .put("pendingSyncCount", syncManager.pendingCount.value),
                )

                refreshPassengerEtaIfRequested()
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

                // Solo se adjunta una captura completa; una cámara cancelada no deja evidencia rota.
                val photo = SessionState.pendingPhoto?.takeIf(photoStore::isUsable)
                val nowUtcMs = System.currentTimeMillis()
                val telemetry = JSONObject()
                    .put("schemaVersion", TELEMETRY_SCHEMA_VERSION)
                    .put("shiftId", shift.shiftId)
                    .put("unitId", shift.unitId)
                    .put("routeId", shift.routeId)
                    .put("occupancy", occupancyLevel.name)
                    .put("latestFix", fix.toJson())
                    .put("photoAttached", photo != null)
                    .put("pendingSyncCount", syncManager.pendingCount.value)
                    .put("online", syncManager.isOnline.value)
                    .put("capturedAtUtcMs", nowUtcMs)
                    .put("capturedElapsedRealtimeNanos", SystemClock.elapsedRealtimeNanos())

                val dispute = FineDisputeEntity(
                    fineId = normalizedFineId,
                    shiftId = shift.shiftId,
                    unitId = shift.unitId,
                    reason = normalizedReason,
                    photoEvidenceUri = photo?.uri?.toString(),
                    attachedTelemetryJson = telemetry.toString(),
                    latitude = fix.position.latitude,
                    longitude = fix.position.longitude,
                    timestampUtcMs = nowUtcMs,
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
                )
                val localId = disputeDao.insert(dispute)
                // Auditoría Paso 3 (S3): si el conductor tomó otra foto mientras se insertaba,
                // no se debe descartar la nueva (quedaría huérfana y sin evidencia).
                synchronized(SessionState) {
                    if (SessionState.pendingPhoto === photo) SessionState.pendingPhoto = null
                }

                pushEvent(
                    "disputeFiled",
                    JSONObject()
                        .put("localId", localId)
                        .put("fineId", normalizedFineId)
                        .put("photoAttached", photo != null)
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
    // Evidencia fotográfica (FileProvider)
    // ---------------------------------------------------------------------------------------------

    @JavascriptInterface
    fun captureDisputePhoto() = jsGuard("captureDisputePhoto") {
        val requestCapture = onCapturePhotoRequested
        if (requestCapture == null) {
            pushError("captureDisputePhoto", "La cámara no está disponible en este contexto.")
            return@jsGuard
        }

        try {
            val photo = photoStore.createPhoto()
            SessionState.pendingPhoto?.let(photoStore::delete)
            SessionState.pendingPhoto = photo
            mainHandler.post { requestCapture(photo.uri) }
        } catch (failure: Throwable) {
            Log.e(TAG, "No se pudo preparar el archivo de evidencia.", failure)
            pushError("captureDisputePhoto", "No se pudo preparar la cámara.")
        }
    }

    /** Nombre del archivo de evidencia pendiente, para `onSaveInstanceState`. */
    fun pendingPhotoFileName(): String? = SessionState.pendingPhoto?.file?.name

    /**
     * Auditoría Paso 3 (S3): en gama baja Android suele matar el proceso mientras la app de cámara
     * está al frente. Al volver, `SessionState` está vacío y la foto se reportaba como fallida y
     * quedaba huérfana en caché. La Activity restaura aquí el nombre guardado en su estado.
     */
    fun restorePendingPhoto(fileName: String?) {
        if (fileName.isNullOrBlank() || SessionState.pendingPhoto != null) return
        SessionState.pendingPhoto = photoStore.fromFileName(fileName)
    }

    /** Invocado por MainActivity cuando la app de cámara termina. */
    fun onPhotoCaptureResult(success: Boolean) {
        val photo = SessionState.pendingPhoto
        if (success && photo != null && photoStore.isUsable(photo)) {
            pushEvent(
                "photoCaptured",
                JSONObject()
                    .put("fileName", photo.file.name)
                    .put("sizeBytes", photo.file.length()),
            )
            return
        }

        photo?.let(photoStore::delete)
        SessionState.pendingPhoto = null
        pushError(
            "captureDisputePhoto",
            if (success) {
                "La cámara devolvió una imagen vacía. Intenta de nuevo."
            } else {
                "Captura cancelada."
            },
        )
    }

    /** Invocado por MainActivity si no hay app de cámara o el sistema rechaza el intento. */
    fun onPhotoCaptureLaunchFailed(message: String = "No hay una app de cámara disponible.") {
        SessionState.pendingPhoto?.let(photoStore::delete)
        SessionState.pendingPhoto = null
        pushError("captureDisputePhoto", message)
    }

    @JavascriptInterface
    fun discardDisputePhoto() = jsGuard("discardDisputePhoto") {
        SessionState.pendingPhoto?.let(photoStore::delete)
        SessionState.pendingPhoto = null
    }

    // ---------------------------------------------------------------------------------------------
    // Pasajero (ETA) y despacho equitativo
    // ---------------------------------------------------------------------------------------------

    @JavascriptInterface
    fun requestPassengerEta(routeId: String, stopId: String) = jsGuard("requestPassengerEta") {
        val normalizedRoute = routeId.normalized(MAX_ROUTE_LENGTH)
        val stop = RouteCatalog.findStop(normalizedRoute, stopId)
        if (stop == null) {
            pushPassengerEtaUnavailable("Ruta o parada sin catálogo operativo.")
            return@jsGuard
        }

        val request = EtaRequest(routeId = normalizedRoute, stopId = stop.id)
        SessionState.lastEtaRequest = request
        SessionState.lastEtaRefreshMs = 0L
        scope.launch {
            buildAndPushEta(request, LocationTrackingService.latestUpdate.value)
        }
    }

    @JavascriptInterface
    fun requestDispatchQueue() = jsGuard("requestDispatchQueue") {
        scope.launch { refreshDispatchQueue() }
    }

    @JavascriptInterface
    fun dispatchNextUnit() = jsGuard("dispatchNextUnit") {
        scope.launch {
            var advanced: DispatchQueueSlot? = null
            var queueSize = 0

            SessionState.dispatchMutex.withLock {
                var queue = SessionState.dispatchQueue
                if (queue.isEmpty()) queue = buildDispatchQueue()
                val next = dispatchUseCase.nextSlot(queue)
                if (next == null) {
                    SessionState.dispatchQueue = emptyList()
                    return@withLock
                }

                val now = System.currentTimeMillis()
                SessionState.lastDepartureByUnit[next.unitId] = now
                SessionState.dispatchQueue = dispatchUseCase.rotateAfterDeparture(
                    queue = queue,
                    departingUnitId = next.unitId,
                    nowUtcMs = now,
                )
                advanced = next
                queueSize = SessionState.dispatchQueue.size
            }

            val dispatched = advanced
            if (dispatched == null) {
                pushError("dispatchNextUnit", "No hay unidades activas en la cola de despacho.")
                return@launch
            }

            pushDispatchQueue()
            pushEvent(
                "dispatchAdvanced",
                JSONObject()
                    .put("unitId", dispatched.unitId)
                    .put("queueSize", queueSize),
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Exportación financiera
    // ---------------------------------------------------------------------------------------------

    @JavascriptInterface
    fun exportFinancialCsv() = jsGuard("exportFinancialCsv") {
        scope.launch {
            try {
                val rows = buildFinancialCsvRows()
                if (rows.isEmpty()) {
                    pushError("exportFinancialCsv", "No hay turnos ni métricas para exportar.")
                    return@launch
                }

                val export = csvExporter.export(rows)
                pushEvent(
                    "csvExported",
                    JSONObject()
                        .put("fileName", export.fileName)
                        .put("rowCount", export.rowCount),
                )
                pushFinancialSummary()

                val share = onCsvReadyForShare
                if (share != null) {
                    mainHandler.post { share(export.uri) }
                }
            } catch (failure: Throwable) {
                Log.e(TAG, "No se pudo generar el CSV financiero.", failure)
                pushError("exportFinancialCsv", "No se pudo generar el archivo CSV.")
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Kotlin → JS
    // ---------------------------------------------------------------------------------------------

    fun pushTelemetry(update: LocationUpdate) {
        recordSpeedSample(update.speedMps)

        val payload = update.toJson()
        payload.put("shiftId", activeShift?.shiftId ?: JSONObject.NULL)
        payload.put("occupancy", occupancyLevel.name)
        payload.put("trackingActive", LocationTrackingService.isRunning.value)
        callJs("onTelemetry", payload)

        refreshPassengerEtaIfDue(update)
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

        // Solo la entrada al checador cuenta para el headway; la salida solo actualiza el mapa.
        if (event.transition == GeofenceTransition.ENTER) {
            scope.launch { recordCheckpoint(event) }
        }
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

    private fun pushHeadwayAssessment(assessment: HeadwayAssessment, unitId: String) {
        callJs(
            "onHeadway",
            JSONObject()
                .put("unitId", unitId)
                .put("checkpointId", assessment.checkpointId)
                .put("checkpointName", assessment.checkpointName)
                .put("targetHeadwaySeconds", assessment.targetHeadwaySeconds)
                .put("actualHeadwaySeconds", assessment.actualHeadwaySeconds)
                .put("deltaSeconds", assessment.deltaSeconds)
                .put("status", assessment.status.name)
                .put("suggestedFineAmountMxn", assessment.suggestedFineAmountMxn)
                .putOpt("forceMajeureExempt", assessment.forceMajeureResult?.isExempt)
                .putOpt("forceMajeureSummary", assessment.forceMajeureResult?.reasonSummary),
        )
    }

    private fun pushForceMajeure(result: ForceMajeureResult) {
        val matchingIds = JSONArray()
        result.matchingReportIds.forEach { matchingIds.put(it) }

        callJs(
            "onForceMajeure",
            JSONObject()
                .put("isExempt", result.isExempt)
                .put("qualifyingUnitsCount", result.qualifyingUnitsCount)
                .put("minimumDistinctUnits", forceMajeureUseCase.minimumDistinctUnits)
                .put("radiusMeters", result.radiusMeters)
                .put("windowMinutes", result.windowMinutes)
                .put("matchingReportIds", matchingIds)
                .put("reasonSummary", result.reasonSummary),
        )
    }

    private fun pushDispatchQueue() {
        val queue = SessionState.dispatchQueue
        val slots = JSONArray()
        queue.sortedBy { it.position }.forEach { slot ->
            slots.put(
                JSONObject()
                    .put("position", slot.position)
                    .put("unitId", slot.unitId)
                    .put("driverAlias", slot.driverAlias)
                    .put("routeId", slot.routeId)
                    .put("scheduledDepartureEpochMs", slot.scheduledDepartureEpochMs)
                    .put("accumulatedFineMxn", round2(slot.accumulatedFineMxn))
                    .put("bonusEligible", slot.bonusEligible)
                    .put("bonusMxn", round2(dispatchUseCase.bonusFor(slot)))
                    .put("forceMajeureExemptionsCount", slot.forceMajeureExemptionsCount),
            )
        }

        callJs(
            "onDispatchQueue",
            JSONObject()
                .put("slots", slots)
                .putOpt("nextUnitId", dispatchUseCase.nextSlot(queue)?.unitId),
        )
    }

    private fun pushPassengerEta(quote: PassengerEtaQuote) {
        callJs(
            "onPassengerEta",
            JSONObject()
                .put("routeId", quote.routeId)
                .put("stopId", quote.stopId)
                .put("stopName", quote.stopName)
                .put("nearestUnitId", quote.nearestUnitId)
                .put("distanceMeters", round2(quote.distanceMeters))
                .put("filteredSpeedMps", round2(quote.filteredSpeedMps))
                .put("filteredSpeedKmh", round2(quote.filteredSpeedMps * 3.6))
                .put("activeIncidentsCount", quote.activeIncidentsCount)
                .put("incidentDelayMinutes", etaUseCase.incidentDelayMinutes(quote.activeIncidentsCount))
                .put("etaMinutes", quote.etaMinutes)
                .put("occupancyLevel", quote.occupancyLevel)
                .put("isStaleCache", quote.isStaleCache)
                .put("calculatedAtEpochMs", quote.calculatedAtEpochMs),
        )
    }

    private fun pushPassengerEtaUnavailable(message: String) {
        callJs("onPassengerEtaUnavailable", JSONObject().put("message", message))
    }

    private suspend fun pushFinancialSummary() {
        ensureFinancialLedgerHydrated()
        val shifts = runCatching { shiftDao.getRecentShifts(FINANCIAL_SHIFT_LIMIT) }
            .getOrDefault(emptyList())
        val units = LinkedHashMap<String, String>()
        shifts.forEach { units.putIfAbsent(it.unitId, it.routeId) }
        SessionState.ledger.forEach { (unitId, state) -> units.putIfAbsent(unitId, state.routeId) }

        var saasTotal = 0.0
        var grossFines = 0.0
        var forgiven = 0.0
        var bonuses = 0.0

        units.forEach { (unitId, _) ->
            val state = SessionState.ledger[unitId]
            saasTotal += SAAS_FEE_PER_UNIT_MXN
            if (state != null) {
                grossFines += state.grossFinesMxn
                forgiven += state.forgivenFinesMxn
                if (state.assessments > 0 && state.netFinesMxn <= 0.0) {
                    bonuses += dispatchUseCase.punctualityBonusMxn
                }
            }
        }

        callJs(
            "onFinancialSummary",
            JSONObject()
                .put("activeUnits", shifts.count { it.endTimestampUtcMs == null })
                .put("fleetSize", DEFAULT_FLEET_SIZE)
                .put("unitCount", units.size)
                .put("saasTotalMxn", round2(saasTotal))
                .put("grossFinesMxn", round2(grossFines))
                .put("forgivenMxn", round2(forgiven))
                .put("netFinesMxn", round2((grossFines - forgiven).coerceAtLeast(0.0)))
                .put("bonusTotalMxn", round2(bonuses)),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Persistencia del checador, headway y métricas
    // ---------------------------------------------------------------------------------------------

    /**
     * Carga en [SessionState.ledger] el acumulado persistido. `putIfAbsent` conserva el estado en
     * memoria si ya es más reciente (misma sesión, rotación de Activity).
     */
    private suspend fun ensureFinancialLedgerHydrated() {
        if (ledgerHydrated) return

        ledgerHydrationMutex.withLock {
            if (ledgerHydrated) return

            val persisted = runCatching { ledgerDao.getAll() }
            if (persisted.isFailure) {
                Log.w(
                    TAG,
                    "No se pudo leer el ledger financiero persistido.",
                    persisted.exceptionOrNull(),
                )
                return
            }

            persisted.getOrThrow().forEach { entity ->
                SessionState.ledger.putIfAbsent(entity.unitId, entity.toSessionState())
            }
            ledgerHydrated = true
        }
    }

    private fun FinancialLedgerEntity.toSessionState() = UnitFinancialState(
        routeId = routeId,
        grossFinesMxn = grossFinesMxn,
        forgivenFinesMxn = forgivenFinesMxn,
        assessments = assessmentsCount,
        exemptions = exemptionsCount,
    )

    private suspend fun recordCheckpoint(event: GeofenceEvent) {
        try {
            checkpointMutex.withLock { recordCheckpointLocked(event) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            // Auditoría Paso 3 (S3): `assessCrossing` lee Room sin try; un error de disco
            // tumbaba el proceso desde `processScope`.
            Log.e(TAG, "No se pudo evaluar el cruce de ${event.zoneName}.", failure)
            pushError("geofence", "No se pudo evaluar el cruce del checador.")
        }
    }

    private suspend fun recordCheckpointLocked(event: GeofenceEvent) {
        val shift = currentShiftOrLoad() ?: return
        val observedAtUtcMs = System.currentTimeMillis()
        val forceMajeure = forceMajeureUseCase.evaluate(
            routeId = shift.routeId,
            position = event.position,
            referenceTimeUtcMs = observedAtUtcMs,
        )
        val assessment = headwayUseCase.assessCrossing(
            checkpointId = event.zoneId,
            checkpointName = event.zoneName,
            routeId = shift.routeId,
            unitId = shift.unitId,
            observedAtUtcMs = observedAtUtcMs,
            forceMajeure = forceMajeure,
        )
        val fix = LocationTrackingService.latestUpdate.value

        try {
            checkpointDao.insert(
                CheckpointEventEntity(
                    shiftId = shift.shiftId,
                    unitId = shift.unitId,
                    routeId = shift.routeId,
                    checkpointId = event.zoneId,
                    checkpointName = event.zoneName,
                    eventType = event.transition.name,
                    latitude = event.position.latitude,
                    longitude = event.position.longitude,
                    filteredSpeedMps = fix?.speedMps ?: 0.0,
                    headwayDeltaSeconds = assessment?.deltaSeconds ?: 0L,
                    occupancyLevel = occupancyLevel,
                    timestampUtcMs = observedAtUtcMs,
                    elapsedRealtimeNanos = event.occurredAtMonotonicNanos,
                ),
            )
        } catch (failure: Throwable) {
            Log.e(TAG, "No se pudo persistir el cruce de ${event.zoneName}.", failure)
            pushError("geofence", "No se pudo guardar el cruce del checador.")
        }

        if (assessment != null) {
            updateFinancialLedger(shift.unitId, shift.routeId, assessment, observedAtUtcMs)
            pushHeadwayAssessment(assessment, shift.unitId)
        }
        pushForceMajeure(forceMajeure)
        pushFinancialSummary()
        refreshPassengerEtaIfRequested()
    }

    private suspend fun updateFinancialLedger(
        unitId: String,
        routeId: String,
        assessment: HeadwayAssessment,
        observedAtUtcMs: Long,
    ) {
        // La caché debe partir del acumulado persistido; de lo contrario un reinicio del proceso
        // haría que el primer cruce pareciera la única sanción de la unidad.
        ensureFinancialLedgerHydrated()

        val theoreticalFine = when (assessment.status) {
            HeadwayStatus.LATE_FINE -> assessment.suggestedFineAmountMxn
            HeadwayStatus.EXEMPT_FORCE_MAJEURE -> headwayUseCase.fineForDelta(assessment.deltaSeconds)
            else -> 0.0
        }
        val forgiven = if (assessment.status == HeadwayStatus.EXEMPT_FORCE_MAJEURE) {
            theoreticalFine
        } else {
            0.0
        }

        val updated = SessionState.ledger.compute(unitId) { _, previous ->
            val base = previous ?: UnitFinancialState(routeId = routeId)
            base.copy(
                routeId = routeId,
                grossFinesMxn = base.grossFinesMxn + theoreticalFine,
                forgivenFinesMxn = base.forgivenFinesMxn + forgiven,
                assessments = base.assessments + 1,
                exemptions = base.exemptions + if (forgiven > 0.0) 1 else 0,
            )
        } ?: return

        // Escritura incremental y transaccional. Si falla (disco lleno, corrupción), la sesión
        // conserva el importe en memoria y el conductor ve un error explícito; no se silencia.
        runCatching {
            ledgerDao.accumulateAssessment(
                unitId = unitId,
                routeId = routeId,
                grossFineMxn = theoreticalFine,
                forgivenMxn = forgiven,
                atUtcMs = observedAtUtcMs,
            )
        }.onFailure { failure ->
            Log.e(TAG, "No se pudo persistir el ledger financiero de la unidad $unitId.", failure)
            pushError("financialLedger", "No se pudo guardar el acumulado financiero local.")
        }

        SessionState.dispatchMutex.withLock {
            SessionState.dispatchQueue = dispatchUseCase.updateMetrics(
                queue = SessionState.dispatchQueue,
                unitId = unitId,
                accumulatedFineMxn = updated.netFinesMxn,
                forceMajeureExemptionsCount = updated.exemptions,
            )
        }
        pushDispatchQueue()
    }

    // ---------------------------------------------------------------------------------------------
    // Despacho equitativo
    // ---------------------------------------------------------------------------------------------

    private suspend fun buildDispatchQueue(): List<DispatchQueueSlot> {
        ensureFinancialLedgerHydrated()
        val shifts = runCatching { shiftDao.getActiveShifts() }.getOrDefault(emptyList())
        val nowUtcMs = System.currentTimeMillis()

        val candidates = shifts.map { shift ->
            SessionState.lastDepartureByUnit.putIfAbsent(
                shift.unitId,
                shift.startTimestampUtcMs,
            )
            val ledger = SessionState.ledger[shift.unitId]
            DispatchCandidate(
                unitId = shift.unitId,
                driverAlias = shift.driverAlias,
                routeId = shift.routeId,
                lastDepartureEpochMs = SessionState.lastDepartureByUnit[shift.unitId],
                accumulatedFineMxn = ledger?.netFinesMxn ?: 0.0,
                forceMajeureExemptionsCount = ledger?.exemptions ?: 0,
            )
        }

        return dispatchUseCase.buildQueue(candidates, nowUtcMs)
    }

    private suspend fun refreshDispatchQueue() {
        SessionState.dispatchMutex.withLock {
            SessionState.dispatchQueue = buildDispatchQueue()
        }
        pushDispatchQueue()
    }

    // ---------------------------------------------------------------------------------------------
    // Finanzas y ETA
    // ---------------------------------------------------------------------------------------------

    private suspend fun buildFinancialCsvRows(): List<FinancialCsvRow> {
        ensureFinancialLedgerHydrated()
        val shifts = runCatching { shiftDao.getRecentShifts(FINANCIAL_SHIFT_LIMIT) }
            .getOrDefault(emptyList())
        val units = LinkedHashMap<String, String>()
        shifts.forEach { units.putIfAbsent(it.unitId, it.routeId) }
        SessionState.ledger.forEach { (unitId, state) -> units.putIfAbsent(unitId, state.routeId) }

        return units.map { (unitId, routeId) ->
            val state = SessionState.ledger[unitId]
            val gross = state?.grossFinesMxn ?: 0.0
            val forgiven = state?.forgivenFinesMxn ?: 0.0
            val net = (gross - forgiven).coerceAtLeast(0.0)
            val bonus = if (state != null && state.assessments > 0 && net <= 0.0) {
                dispatchUseCase.punctualityBonusMxn
            } else {
                0.0
            }

            FinancialCsvRow(
                unitId = unitId,
                routeId = routeId,
                saasFeeMxn = SAAS_FEE_PER_UNIT_MXN,
                grossFinesMxn = round2(gross),
                forceMajeureForgivenMxn = round2(forgiven),
                netFinesMxn = round2(net),
                punctualityBonusMxn = round2(bonus),
            )
        }.sortedBy { it.unitId }
    }

    private suspend fun buildAndPushEta(request: EtaRequest, update: LocationUpdate?) {
        val stop = RouteCatalog.findStop(request.routeId, request.stopId)
        if (stop == null) {
            pushPassengerEtaUnavailable("La ruta o parada no tiene coordenadas configuradas.")
            return
        }
        if (update == null || !update.position.isValid || update.isMock) {
            pushPassengerEtaUnavailable("Sin telemetría GPS válida para calcular el ETA.")
            return
        }

        val ageNanos = SystemClock.elapsedRealtimeNanos() - update.elapsedRealtimeNanos
        if (ageNanos !in 0L..MAX_FIX_AGE_NANOS) {
            pushPassengerEtaUnavailable("La última posición GPS está desactualizada.")
            return
        }

        val nowUtcMs = System.currentTimeMillis()
        val since = nowUtcMs - ETA_INCIDENT_WINDOW_MINUTES * 60_000L
        val incidents = runCatching {
            incidenceDao.countRecentRouteIncidents(request.routeId, since)
        }.getOrDefault(0)

        pushPassengerEta(
            etaUseCase.calculate(
                routeId = request.routeId,
                stopId = stop.id,
                stopName = stop.name,
                unitId = activeShift?.unitId ?: UNKNOWN_UNIT_ID,
                unitPosition = update.position,
                stopPosition = stop.position,
                averageSpeedMps = averageFilteredSpeed(update.speedMps),
                activeIncidentsCount = incidents,
                occupancyLevel = occupancyLevel.name,
                calculatedAtEpochMs = nowUtcMs,
            ),
        )
    }

    private fun refreshPassengerEtaIfDue(update: LocationUpdate) {
        val request = SessionState.lastEtaRequest ?: return
        val nowElapsed = SystemClock.elapsedRealtime()
        if (nowElapsed - SessionState.lastEtaRefreshMs < ETA_REFRESH_INTERVAL_MS) return

        SessionState.lastEtaRefreshMs = nowElapsed
        scope.launch { buildAndPushEta(request, update) }
    }

    private fun refreshPassengerEtaIfRequested() {
        val request = SessionState.lastEtaRequest ?: return
        SessionState.lastEtaRefreshMs = 0L
        scope.launch {
            buildAndPushEta(request, LocationTrackingService.latestUpdate.value)
        }
    }

    private fun recordSpeedSample(speedMps: Double) {
        if (!speedMps.isFinite() || speedMps < 0.0) return
        synchronized(SessionState.speedSamples) {
            SessionState.speedSamples.addLast(speedMps)
            while (SessionState.speedSamples.size > MAX_SPEED_SAMPLES) {
                SessionState.speedSamples.removeFirst()
            }
        }
    }

    private fun averageFilteredSpeed(fallback: Double): Double {
        val samples = synchronized(SessionState.speedSamples) {
            SessionState.speedSamples.filter { it.isFinite() && it >= 0.0 }
        }
        return if (samples.isEmpty()) fallback else samples.average()
    }

    private fun round2(value: Double): Double =
        kotlin.math.round(value * 100.0) / 100.0

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
        private val processScope = CoroutineScope(
            SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, failure ->
                // Auditoría Paso 3 (S3): con SupervisorJob una excepción no capturada en un
                // `launch` llega al handler por defecto y Android mata el proceso (y el GPS).
                Log.e(TAG, "Excepción no controlada en el puente nativo.", failure)
            },
        )

        /** Nombre exacto del objeto inyectado en el contexto JavaScript. */
        const val JS_INTERFACE_NAME = "CronoBusNative"

        private const val TELEMETRY_SCHEMA_VERSION = 1
        private const val MAX_FIX_AGE_NANOS = 60_000L * 1_000_000L

        private const val MAX_SPEED_SAMPLES = 12
        private const val ETA_REFRESH_INTERVAL_MS = 10_000L
        private const val ETA_INCIDENT_WINDOW_MINUTES = 15L

        private const val FINANCIAL_SHIFT_LIMIT = 50
        private const val SAAS_FEE_PER_UNIT_MXN = 750.0
        private const val DEFAULT_FLEET_SIZE = 8
        private const val UNKNOWN_UNIT_ID = "N/D"

        private const val MAX_ALIAS_LENGTH = 64
        private const val MAX_UNIT_LENGTH = 32
        private const val MAX_ROUTE_LENGTH = 96
        private const val MAX_FINE_ID_LENGTH = 64
        private const val MAX_REASON_LENGTH = 500
    }
}
