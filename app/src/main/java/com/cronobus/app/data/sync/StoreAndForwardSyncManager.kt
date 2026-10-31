package com.cronobus.app.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import androidx.room.withTransaction
import com.cronobus.app.data.local.CronoBusDatabase
import com.cronobus.app.data.local.dao.SyncQueueRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Motor **Store & Forward** de CronoBus.
 *
 * Responsabilidades:
 * 1. Observar la conectividad real con [ConnectivityManager.NetworkCallback] (la red debe estar
 *    `VALIDATED`, no basta con estar conectada a un Wi-Fi cautivo).
 * 2. Vaciar las cuatro tablas operativas en orden cronológico global, un registro a la vez, para
 *    no reordenar la narrativa del turno.
 * 3. Aplicar backoff exponencial entre ciclos fallidos, sin bloquear hilos ni quemar batería.
 * 4. Exponer [pendingCount] reactivo (Room `Flow` + `combine`) para el badge `SYNC: N PEND`.
 *
 * Garantías de entrega:
 * - `SYNCING` es un estado transitorio; si el proceso muere a mitad de envío, el siguiente ciclo
 *   lo devuelve a `PENDING` antes de leer (recuperación de *stuck rows*).
 * - Un fallo transitorio suma `retryCount` y vuelve a `PENDING`; al alcanzar
 *   [SyncConfig.maxRetriesBeforeFailed] queda `FAILED` y se reintenta cuando la red se estabiliza.
 * - Un rechazo definitivo queda `FAILED` y no bloquea al resto de la cola.
 *
 * El transporte remoto es inyectable ([SyncTransport]): hasta que la capa Retrofit de la Fase 4
 * llame a [installTransport], la cola se acumula íntegra y el badge muestra el rezago real.
 */
class StoreAndForwardSyncManager internal constructor(
    private val database: CronoBusDatabase,
    private val appContext: Context,
    initialTransport: SyncTransport? = null,
    private val config: SyncConfig = SyncConfig(),
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val monitorConnectivity: Boolean = true,
    autoStart: Boolean = true,
    private val nowUtcMs: () -> Long = System::currentTimeMillis,
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val checkpointDao = database.checkpointEventDao()
    private val incidenceDao = database.incidenceReportDao()
    private val disputeDao = database.fineDisputeDao()
    private val shiftDao = database.shiftLogDao()
    private val syncQueueDao = database.syncQueueDao()

    @Volatile
    private var transport: SyncTransport? = initialTransport

    private val connectivityManager: ConnectivityManager? =
        if (monitorConnectivity) appContext.getSystemService(ConnectivityManager::class.java) else null

    private val drainRequests = Channel<Unit>(Channel.CONFLATED)

    /** Señal exclusiva de "la red volvió": interrumpe el backoff en curso. */
    private val reconnectSignals = Channel<Unit>(Channel.CONFLATED)
    private val drainMutex = Mutex()

    private var workerJob: Job? = null
    private var retryAttempt = 0

    /**
     * Solo se promueven los `FAILED` al arrancar, al recuperar red o al instalar un transporte
     * nuevo. Los reintentos programados por backoff no los reactivan: un rechazo definitivo no
     * debe consumir datos del conductor cada pocos minutos.
     */
    private val promoteFailedOnNextDrain = AtomicBoolean(true)

    private val _isOnline = MutableStateFlow(if (monitorConnectivity) isOnlineNow() else true)

    /** `true` solo si la red activa tiene Internet y pasó la validación del sistema. */
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)

    /** Hay un ciclo de vaciado en curso (para el indicador de despacho). */
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncAttemptUtcMs = MutableStateFlow<Long?>(null)

    /** Marca de pared del último lote procesado; solo para diagnóstico. */
    val lastSyncAttemptUtcMs: StateFlow<Long?> = _lastSyncAttemptUtcMs.asStateFlow()

    /**
     * Pendientes reales de las cuatro tablas. Se recalcula por invalidación de Room: cualquier
     * `insert`, `markAsSynced` o cambio de estado actualiza el badge sin polling.
     */
    val pendingCount: StateFlow<Int> = combine(
        checkpointDao.observePendingCount(),
        incidenceDao.observePendingCount(),
        disputeDao.observePendingCount(),
        shiftDao.observePendingCount(),
    ) { checkpoints, incidences, disputes, shifts ->
        checkpoints + incidences + disputes + shifts
    }.stateIn(scope, SharingStarted.Eagerly, 0)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        // Auditoría Paso 3: `onAvailable` llega ANTES de la validación (portal cautivo, red sin
        // Internet). Marcar online aquí provocaba envíos fallidos que consumían `retryCount`.
        // La transición a online la decide exclusivamente `onCapabilitiesChanged` (VALIDATED).

        override fun onLost(network: Network) {
            // Con `registerDefaultNetworkCallback`, `onLost` significa que ya no hay red por
            // defecto; consultar `activeNetwork` aquí puede devolver todavía la red perdida.
            updateConnectivity(online = false)
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            updateConnectivity(
                online = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            )
        }
    }

    init {
        if (monitorConnectivity) registerNetworkCallback()
        if (autoStart) {
            workerJob = scope.launch { runWorker() }
        }
    }

    /**
     * Conecta el backend real. Hasta que se llame, el sincronizador no descarta ni marca nada
     * como enviado: solo acumula evidencia local.
     */
    fun installTransport(transport: SyncTransport) {
        this.transport = transport
        promoteFailedOnNextDrain.set(true)
        requestSync()
    }

    /** Fuerza un ciclo (por ejemplo, botón "Reintentar sincronización" del despacho). */
    fun requestSync() {
        drainRequests.trySend(Unit)
    }

    // ---------------------------------------------------------------------------------------------
    // Bucle de trabajo
    // ---------------------------------------------------------------------------------------------

    private suspend fun runWorker() {
        // Primer intento inmediato: la app puede abrirse con red y cola pendiente de un turno previo.
        if (_isOnline.value) drainRequests.trySend(Unit)

        for (request in drainRequests) {
            if (!_isOnline.value) continue

            val retryableFailure = drainOnce()
            if (retryableFailure && _isOnline.value) {
                retryAttempt = (retryAttempt + 1).coerceAtMost(MAX_BACKOFF_EXPONENT)
                // El primer reintento espera `baseBackoffMs`; después 2x, 4x, 8x…
                // Auditoría Paso 3: la espera se corta si la red se recupera (offline → online),
                // para no dejar la cola dormida hasta 5 min tras volver la cobertura.
                val reconnected = withTimeoutOrNull(backoffMillis(retryAttempt - 1)) {
                    reconnectSignals.receive()
                } != null
                if (reconnected) retryAttempt = 0
                drainRequests.trySend(Unit)
            } else {
                retryAttempt = 0
            }
        }
    }

    /**
     * Un ciclo de vaciado completo.
     *
     * @return `true` si hubo una falla transitoria y conviene reprogramar con backoff.
     */
    internal suspend fun drainOnce(): Boolean = drainMutex.withLock {
        val activeTransport = transport ?: return@withLock false
        if (!_isOnline.value) return@withLock false

        _isSyncing.value = true
        var retryableFailure = false

        try {
            // Recuperación tras muerte del proceso / cancelación a mitad de envío.
            resetStuckSyncing()
            if (promoteFailedOnNextDrain.getAndSet(false)) {
                promoteFailedToPending()
            }

            var rounds = 0
            while (rounds < config.maxBatchesPerCycle && _isOnline.value) {
                val batch = loadNextBatch()
                if (batch.isEmpty()) break

                val outcome = sendBatch(batch, activeTransport)
                _lastSyncAttemptUtcMs.value = nowUtcMs()

                if (outcome == BatchOutcome.RETRYABLE_FAILURE) {
                    retryableFailure = true
                    break
                }
                rounds++
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (unexpected: Throwable) {
            Log.e(TAG, "Fallo inesperado durante el vaciado de la cola.", unexpected)
            retryableFailure = true
        } finally {
            _isSyncing.value = false
        }

        return retryableFailure
    }

    private suspend fun loadNextBatch(): List<SyncQueueItem> = coroutineScope {
        val refs = syncQueueDao.getNextPendingRefs(config.batchSize)
        if (refs.isEmpty()) return@coroutineScope emptyList()

        val checkpointIds = refs
            .filter { it.kind == SyncQueueRef.KIND_CHECKPOINT }
            .mapNotNull { it.numericId }
        val incidenceIds = refs
            .filter { it.kind == SyncQueueRef.KIND_INCIDENCE }
            .mapNotNull { it.numericId }
        val disputeIds = refs
            .filter { it.kind == SyncQueueRef.KIND_DISPUTE }
            .mapNotNull { it.numericId }
        val shiftIds = refs
            .filter { it.kind == SyncQueueRef.KIND_SHIFT }
            .mapNotNull { it.textId }

        // Cuatro consultas agrupadas en lugar de una por registro (N+1), manteniendo el orden
        // cronológico que fijó la proyección global.
        val checkpoints = async { checkpointDao.getByIds(checkpointIds) }
        val incidences = async { incidenceDao.getByIds(incidenceIds) }
        val disputes = async { disputeDao.getByIds(disputeIds) }
        val shifts = async { shiftDao.getByIds(shiftIds) }

        val checkpointById = checkpoints.await().associateBy { it.id }
        val incidenceById = incidences.await().associateBy { it.id }
        val disputeById = disputes.await().associateBy { it.id }
        val shiftById = shifts.await().associateBy { it.shiftId }

        refs.mapNotNull { ref ->
            when (ref.kind) {
                SyncQueueRef.KIND_CHECKPOINT ->
                    ref.numericId?.let(checkpointById::get)?.let(SyncQueueItem::Checkpoint)

                SyncQueueRef.KIND_INCIDENCE ->
                    ref.numericId?.let(incidenceById::get)?.let(SyncQueueItem::Incidence)

                SyncQueueRef.KIND_DISPUTE ->
                    ref.numericId?.let(disputeById::get)?.let(SyncQueueItem::Dispute)

                SyncQueueRef.KIND_SHIFT ->
                    ref.textId?.let(shiftById::get)?.let(SyncQueueItem::Shift)

                else -> null
            }
        }
    }

    private suspend fun sendBatch(
        batch: List<SyncQueueItem>,
        activeTransport: SyncTransport,
    ): BatchOutcome {
        for (item in batch) {
            markAsSyncing(item)

            val outcome = try {
                activeTransport.send(item)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                // Un transporte defectuoso nunca debe tumbar el servicio: se trata como transitorio.
                Log.w(TAG, "El transporte falló al enviar ${item.localId}.", failure)
                SyncOutcome.RETRYABLE_FAILURE
            }

            when (outcome) {
                SyncOutcome.SUCCESS -> markAsSynced(item)
                SyncOutcome.PERMANENT_FAILURE -> markAsFailed(item)
                SyncOutcome.RETRYABLE_FAILURE -> {
                    registerRetry(item)
                    return BatchOutcome.RETRYABLE_FAILURE
                }
            }
        }
        return BatchOutcome.COMPLETE
    }

    // ---------------------------------------------------------------------------------------------
    // Transiciones de estado (switch explícito por tabla)
    // ---------------------------------------------------------------------------------------------

    private suspend fun resetStuckSyncing() {
        runCatching {
            database.withTransaction {
                checkpointDao.resetStuckSyncing()
                incidenceDao.resetStuckSyncing()
                disputeDao.resetStuckSyncing()
                shiftDao.resetStuckSyncing()
            }
        }.onFailure { Log.w(TAG, "No se pudo recuperar la cola en SYNCING.", it) }
    }

    private suspend fun promoteFailedToPending() {
        runCatching {
            database.withTransaction {
                checkpointDao.promoteFailedToPending()
                incidenceDao.promoteFailedToPending()
                disputeDao.promoteFailedToPending()
                shiftDao.promoteFailedToPending()
            }
        }.onFailure { Log.w(TAG, "No se pudo promover la cola FAILED.", it) }
    }

    private suspend fun markAsSyncing(item: SyncQueueItem) {
        val now = nowUtcMs()
        when (item) {
            is SyncQueueItem.Checkpoint -> checkpointDao.markAsSyncing(item.entity.id, now)
            is SyncQueueItem.Incidence -> incidenceDao.markAsSyncing(item.entity.id, now)
            is SyncQueueItem.Dispute -> disputeDao.markAsSyncing(item.entity.id, now)
            is SyncQueueItem.Shift -> shiftDao.markAsSyncing(item.entity.shiftId, now)
        }
    }

    private suspend fun markAsSynced(item: SyncQueueItem) {
        when (item) {
            is SyncQueueItem.Checkpoint -> checkpointDao.markAsSynced(item.entity.id)
            is SyncQueueItem.Incidence -> incidenceDao.markAsSynced(item.entity.id)
            is SyncQueueItem.Dispute -> disputeDao.markAsSynced(item.entity.id)
            is SyncQueueItem.Shift -> shiftDao.markAsSynced(item.entity.shiftId)
        }
    }

    private suspend fun markAsFailed(item: SyncQueueItem) {
        val now = nowUtcMs()
        when (item) {
            is SyncQueueItem.Checkpoint -> checkpointDao.markAsFailed(item.entity.id, now)
            is SyncQueueItem.Incidence -> incidenceDao.markAsFailed(item.entity.id, now)
            is SyncQueueItem.Dispute -> disputeDao.markAsFailed(item.entity.id, now)
            is SyncQueueItem.Shift -> shiftDao.markAsFailed(item.entity.shiftId, now)
        }
    }

    private suspend fun registerRetry(item: SyncQueueItem) {
        val now = nowUtcMs()
        val maxRetries = config.maxRetriesBeforeFailed
        when (item) {
            is SyncQueueItem.Checkpoint ->
                checkpointDao.incrementRetryCount(item.entity.id, now, maxRetries)

            is SyncQueueItem.Incidence ->
                incidenceDao.incrementRetryCount(item.entity.id, now, maxRetries)

            is SyncQueueItem.Dispute ->
                disputeDao.incrementRetryCount(item.entity.id, now, maxRetries)

            is SyncQueueItem.Shift ->
                shiftDao.incrementRetryCount(item.entity.shiftId, now, maxRetries)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Conectividad
    // ---------------------------------------------------------------------------------------------

    private fun registerNetworkCallback() {
        val manager = connectivityManager ?: return
        try {
            manager.registerDefaultNetworkCallback(networkCallback)
        } catch (security: SecurityException) {
            Log.e(TAG, "Sin ACCESS_NETWORK_STATE: el Store & Forward no observará la red.", security)
        } catch (runtime: RuntimeException) {
            Log.e(TAG, "No fue posible registrar el NetworkCallback.", runtime)
        }
    }

    private fun updateConnectivity(online: Boolean) {
        // Atómico: los callbacks de red llegan en un hilo binder distinto al del worker.
        val wasOffline = !_isOnline.getAndUpdate { online }
        if (online) {
            if (wasOffline) {
                promoteFailedOnNextDrain.set(true)
                reconnectSignals.trySend(Unit)
            }
            requestSync()
        }
    }

    private fun isOnlineNow(): Boolean {
        val manager = connectivityManager ?: return true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun backoffMillis(attempt: Int): Long {
        val exponent = attempt.coerceIn(0, MAX_BACKOFF_EXPONENT)
        val multiplier = 1L shl exponent
        val uncapped = config.baseBackoffMs * multiplier
        return if (uncapped <= 0L) config.maxBackoffMs else uncapped.coerceAtMost(config.maxBackoffMs)
    }

    /** Libera el callback de red y cancela el worker. Solo para pruebas o cierre controlado. */
    internal fun shutdown() {
        workerJob?.cancel()
        workerJob = null
        if (monitorConnectivity) {
            try {
                connectivityManager?.unregisterNetworkCallback(networkCallback)
            } catch (notRegistered: IllegalArgumentException) {
                Log.d(TAG, "El NetworkCallback ya estaba desregistrado.")
            }
        }
        scope.cancel()
        synchronized(Companion) {
            if (instance === this) instance = null
        }
    }

    private enum class BatchOutcome { COMPLETE, RETRYABLE_FAILURE }

    companion object {
        private const val TAG = "CronoBusSync"
        private const val MAX_BACKOFF_EXPONENT = 8

        @Volatile
        private var instance: StoreAndForwardSyncManager? = null

        /** Instancia única para todo el proceso (Activity, servicio y puente JS comparten la cola). */
        fun getInstance(context: Context): StoreAndForwardSyncManager =
            instance ?: synchronized(this) {
                instance ?: StoreAndForwardSyncManager(
                    database = CronoBusDatabase.getInstance(context),
                    appContext = context.applicationContext,
                ).also { instance = it }
            }
    }
}

/**
 * Parámetros del Store & Forward. Los valores por defecto priorizan no gastar datos del conductor
 * en zonas de cobertura intermitente.
 */
data class SyncConfig(
    /** Registros por tabla y por lote (el lote global se recorta a este tamaño tras mezclar). */
    val batchSize: Int = 50,
    /** Tope de lotes por ciclo para no monopolizar la base local. */
    val maxBatchesPerCycle: Int = 100,
    /** Primer reintento a los 2 s; después 4 s, 8 s… */
    val baseBackoffMs: Long = 2_000L,
    /** Techo de espera entre ciclos fallidos. */
    val maxBackoffMs: Long = 5 * 60_000L,
    /** Intentos antes de marcar `FAILED` (se reintenta al recuperar red). */
    val maxRetriesBeforeFailed: Int = 8,
) {
    init {
        require(batchSize > 0) { "batchSize debe ser mayor que 0" }
        require(maxBatchesPerCycle > 0) { "maxBatchesPerCycle debe ser mayor que 0" }
        require(baseBackoffMs > 0L) { "baseBackoffMs debe ser mayor que 0" }
        require(maxBackoffMs >= baseBackoffMs) { "maxBackoffMs no puede ser menor que baseBackoffMs" }
        require(maxRetriesBeforeFailed > 0) { "maxRetriesBeforeFailed debe ser mayor que 0" }
    }
}
