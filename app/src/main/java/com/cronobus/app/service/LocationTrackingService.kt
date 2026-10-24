package com.cronobus.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.cronobus.app.domain.location.GeofenceEngine
import com.cronobus.app.domain.location.KalmanLocationFilter
import com.cronobus.app.domain.security.TamperDetector
import com.cronobus.app.domain.model.GeoPoint
import com.cronobus.app.domain.model.GeofenceEvent
import com.cronobus.app.domain.model.GeofenceZone
import com.cronobus.app.domain.model.LocationSample
import com.cronobus.app.domain.model.LocationUpdate
import com.cronobus.app.domain.model.TamperEvent
import com.example.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano que mantiene el rastreo GPS vivo con la pantalla apagada o la app en
 * segundo plano (requisito operativo del conductor).
 *
 * Flujo de datos:
 * `FusedLocationProviderClient` → [TamperDetector] → [KalmanLocationFilter] → [GeofenceEngine]
 * → `Flow<LocationUpdate>`.
 *
 * Contrato de consumo:
 * - [updates] emite cada fix filtrado (con `replay = 1` para el primer suscriptor).
 * - [latestUpdate] conserva solo el último para la UI.
 * - [tamperAlerts] publica anomalías anti-sabotaje (mock, reloj, GPS apagado).
 * - [geofenceEvents] publica cruces ENTER/EXIT confirmados.
 *
 * El servicio se inicia explícitamente desde la UI con [start] (nunca desde un receptor en
 * segundo plano) para cumplir las restricciones de Android 12+.
 */
class LocationTrackingService : Service() {

    private lateinit var executor: ExecutorService
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val kalmanFilter = KalmanLocationFilter()
    private val geofenceEngine = GeofenceEngine(nowNanos = { SystemClock.elapsedRealtimeNanos() })
    private val tamperDetector = TamperDetector(
        monotonicNowNanos = { SystemClock.elapsedRealtimeNanos() },
    )

    private var trackingStarted = false
    private var batteryRegistered = false
    private var zonesJob: Job? = null
    private var providerUnavailableSinceNanos = 0L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(::handleLocation)
        }

        override fun onLocationAvailability(availability: LocationAvailability) {
            if (availability.isLocationAvailable) {
                providerUnavailableSinceNanos = 0L
            } else {
                handleProviderUnavailable()
            }
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val percentage = if (level >= 0 && scale > 0) level * 100 / scale else null
            val charging = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING,
                BatteryManager.BATTERY_STATUS_FULL,
                -> true

                BatteryManager.BATTERY_STATUS_DISCHARGING,
                BatteryManager.BATTERY_STATUS_NOT_CHARGING,
                -> false

                else -> null
            }
            tamperDetector.updateBattery(percentage, charging)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "cronobus-gps")
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, null -> {
                if (!hasLocationPermission()) {
                    Log.w(TAG, "Permiso ACCESS_FINE_LOCATION no concedido; no se puede rastrear.")
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (!promoteToForeground()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startTracking()
            }

            else -> {
                Log.w(TAG, "Acción desconocida: ${intent.action}")
                return START_NOT_STICKY
            }
        }
        // Reinicio automático tras muerte del proceso: el sistema entrega un intent nulo.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopTracking()
        if (!executor.isShutdown) executor.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------------------------------------
    // Arranque y parada
    // ---------------------------------------------------------------------------------------------

    private fun startTracking() {
        if (trackingStarted) return
        trackingStarted = true
        _isRunning.value = true

        registerBatteryMonitor()
        zonesJob = scope.launch {
            geofenceZones.collect { zones -> geofenceEngine.replaceZones(zones) }
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_METERS)
            .setWaitForAccurateLocation(false)
            .setMaxUpdateAgeMillis(0L)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(request, executor, locationCallback)
            // Semilla inmediata para que la UI no espere el primer intervalo.
            fusedLocationClient.lastLocation.addOnSuccessListener(executor) { location ->
                location?.let(::handleLocation)
            }
        } catch (security: SecurityException) {
            Log.e(TAG, "Permiso de ubicación revocado mientras el servicio estaba activo.", security)
            stopTracking()
            stopSelf()
        } catch (runtime: RuntimeException) {
            Log.e(TAG, "No fue posible registrar el callback de ubicación.", runtime)
            stopTracking()
            stopSelf()
        }
    }

    private fun stopTracking() {
        if (!trackingStarted) {
            _isRunning.value = false
            return
        }
        trackingStarted = false

        zonesJob?.cancel()
        zonesJob = null

        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (runtime: RuntimeException) {
            Log.w(TAG, "removeLocationUpdates falló durante el cierre.", runtime)
        }

        unregisterBatteryMonitor()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)

        kalmanFilter.reset()
        tamperDetector.reset()
        geofenceEngine.reset()
        providerUnavailableSinceNanos = 0L

        _isRunning.value = false
    }

    private fun promoteToForeground(): Boolean {
        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        return try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                foregroundServiceType,
            )
            true
        } catch (security: SecurityException) {
            Log.e(TAG, "Sin permiso para iniciar el servicio de ubicación en primer plano.", security)
            false
        } catch (runtime: RuntimeException) {
            // Android 12+ lanza ForegroundServiceStartNotAllowedException (IllegalStateException)
            // si el arranque ocurre en segundo plano; Android 14+ puede rechazar el tipo.
            Log.e(TAG, "El sistema rechazó el servicio en primer plano.", runtime)
            false
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Procesamiento de lecturas
    // ---------------------------------------------------------------------------------------------

    private fun handleLocation(location: Location) {
        if (!trackingStarted) return
        val sample = location.toSample()

        // 1) Anti-sabotaje: un fix manipulado nunca entra al filtro ni a las geocercas.
        val tamperReport = tamperDetector.inspectFix(sample)
        tamperReport.events.forEach { _tamperAlerts.tryEmit(it) }
        if (tamperReport.stopTracking) {
            Log.w(
                TAG,
                "Rastreo interrumpido por anti-sabotaje: " +
                    tamperReport.events.joinToString { it.type.name },
            )
            stopTracking()
            stopSelf()
            return
        }
        if (!tamperReport.acceptFix) return

        // 2) Suavizado de ruido urbano.
        val update = kalmanFilter.update(sample) ?: return
        _latestUpdate.value = update
        _updates.tryEmit(update)

        // 3) Geocercas con histéresis de 5 s.
        geofenceEngine
            .evaluate(update.position, SystemClock.elapsedRealtimeNanos())
            .forEach { _geofenceEvents.tryEmit(it) }
    }

    private fun handleProviderUnavailable() {
        if (!trackingStarted) return
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        if (providerUnavailableSinceNanos == 0L) providerUnavailableSinceNanos = nowNanos
        // Gracia para no confundir un túnel o un cañón urbano con un apagado deliberado.
        if (nowNanos - providerUnavailableSinceNanos < PROVIDER_LOSS_GRACE_NANOS) return

        val position = _latestUpdate.value?.position ?: kalmanFilter.latest?.position
        val nearestZoneDistance = position?.let { geofenceEngine.distanceToNearestZoneMeters(it) }
        tamperDetector.inspectProviderDisabled(position, nearestZoneDistance).events
            .forEach { _tamperAlerts.tryEmit(it) }
    }

    private fun Location.toSample(): LocationSample = LocationSample(
        position = GeoPoint(latitude, longitude),
        accuracyMeters = if (hasAccuracy()) accuracy.toDouble() else UNKNOWN_ACCURACY_METERS,
        speedMps = if (hasSpeed()) speed.toDouble() else 0.0,
        bearingDegrees = if (hasBearing()) bearing.toDouble() else 0.0,
        altitudeMeters = if (hasAltitude()) altitude else null,
        provider = provider,
        isMock = isMockProvider(),
        elapsedRealtimeNanos = elapsedRealtimeNanos,
        wallClockEpochMs = time,
    )

    @Suppress("DEPRECATION")
    private fun Location.isMockProvider(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock else isFromMockProvider

    // ---------------------------------------------------------------------------------------------
    // Notificación, batería y permisos
    // ---------------------------------------------------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.location_tracking_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.location_tracking_channel_description)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val immutableFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            PendingIntent.getActivity(this, REQUEST_CONTENT, intent, immutableFlags)
        }
        val stopIntent = Intent(this, LocationTrackingActionReceiver::class.java)
            .setAction(LocationTrackingActionReceiver.ACTION_STOP_TRACKING)
        val stopPendingIntent = PendingIntent.getBroadcast(this, REQUEST_STOP, stopIntent, immutableFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_cronobus_location)
            .setContentTitle(getString(R.string.location_tracking_title))
            .setContentText(getString(R.string.location_tracking_text))
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .addAction(0, getString(R.string.location_tracking_stop), stopPendingIntent)
            .build()
    }

    private fun registerBatteryMonitor() {
        if (batteryRegistered) return
        val stickyIntent = ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // ACTION_BATTERY_CHANGED es sticky: entrega el nivel actual sin esperar al próximo cambio.
        stickyIntent?.let { batteryReceiver.onReceive(this, it) }
        batteryRegistered = true
    }

    private fun unregisterBatteryMonitor() {
        if (!batteryRegistered) return
        try {
            unregisterReceiver(batteryReceiver)
        } catch (illegal: IllegalArgumentException) {
            Log.w(TAG, "El receptor de batería ya estaba desregistrado.", illegal)
        }
        batteryRegistered = false
    }

    private fun hasLocationPermission(): Boolean = hasLocationPermission(this)

    companion object {
        private const val TAG = "LocationTrackingService"

        const val ACTION_START = "com.cronobus.app.action.START_LOCATION_TRACKING"

        private const val CHANNEL_ID = "cronobus_location_tracking"
        private const val NOTIFICATION_ID = 0x0C0B05
        private const val REQUEST_CONTENT = 100
        private const val REQUEST_STOP = 101

        private const val UPDATE_INTERVAL_MS = 3_000L
        private const val MIN_UPDATE_INTERVAL_MS = 1_000L
        private const val MIN_UPDATE_DISTANCE_METERS = 2f
        private const val UNKNOWN_ACCURACY_METERS = 30.0
        private const val PROVIDER_LOSS_GRACE_NANOS = 30_000L * 1_000_000L

        private val _updates = MutableSharedFlow<LocationUpdate>(
            replay = 1,
            extraBufferCapacity = 32,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        private val _tamperAlerts = MutableSharedFlow<TamperEvent>(
            extraBufferCapacity = 16,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        private val _geofenceEvents = MutableSharedFlow<GeofenceEvent>(
            extraBufferCapacity = 16,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        private val _latestUpdate = MutableStateFlow<LocationUpdate?>(null)
        private val _isRunning = MutableStateFlow(false)
        private val _geofenceZones = MutableStateFlow<List<GeofenceZone>>(emptyList())

        /** Fixes filtrados; `replay = 1` entrega el último conocido al suscriptor nuevo. */
        val updates: SharedFlow<LocationUpdate> = _updates.asSharedFlow()

        /** Alertas anti-sabotaje (mock, manipulación de reloj, apagado cerca de estación). */
        val tamperAlerts: SharedFlow<TamperEvent> = _tamperAlerts.asSharedFlow()

        /** Cruces de geocerca confirmados (ENTER/EXIT). */
        val geofenceEvents: SharedFlow<GeofenceEvent> = _geofenceEvents.asSharedFlow()

        /** Último fix filtrado para la UI y el puente JS. */
        val latestUpdate: StateFlow<LocationUpdate?> = _latestUpdate.asStateFlow()

        /** Indica si el rastreo está activo (útil para el badge del turno). */
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        /** Geocercas cargadas por la ruta activa; el servicio las sincroniza al arrancar. */
        val geofenceZones: StateFlow<List<GeofenceZone>> = _geofenceZones.asStateFlow()

        /**
         * Registra las geocercas de la ruta antes (o durante) el turno. Es idempotente y no
         * conserva estados de geocercas eliminadas.
         */
        fun setGeofenceZones(zones: List<GeofenceZone>) {
            _geofenceZones.value = zones.toList()
        }

        /** Debe invocarse con la app en primer plano (restricción de Android 12+). */
        fun start(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java)
                .setAction(ACTION_START)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (illegal: IllegalStateException) {
                // Incluye ForegroundServiceStartNotAllowedException (Android 12+).
                Log.e(TAG, "No se pudo iniciar el rastreo en primer plano.", illegal)
            }
        }

        /** Detiene el rastreo y retira la notificación persistente. */
        fun stop(context: Context) {
            context.stopService(Intent(context, LocationTrackingService::class.java))
        }

        /**
         * Android 14+ exige ubicación precisa para un `foregroundServiceType="location"` con
         * `PRIORITY_HIGH_ACCURACY`. Sin `ACCESS_FINE_LOCATION` el servicio no arranca.
         */
        fun hasLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
    }
}
