package com.cronobus.app.data.sync

/**
 * Resultado de un intento de envío al backend.
 *
 * La distinción es deliberada: un error de red o un 5xx merece backoff exponencial, mientras que
 * un 4xx de validación no debe reintentarse indefinidamente (se marca `FAILED` y se reencola solo
 * cuando la conectividad cambia, para no quemar batería ni datos del conductor).
 */
enum class SyncOutcome {
    SUCCESS,

    /** Falla transitoria: reintentar con backoff exponencial. */
    RETRYABLE_FAILURE,

    /** Rechazo definitivo del servidor: dejar evidencia en `FAILED` y continuar con el resto. */
    PERMANENT_FAILURE,
}

/**
 * Transporte remoto de la cola. Se inyecta desde la capa de datos (Retrofit / Firebase) para que
 * el sincronizador sea testeable sin red y sin acoplarse a un endpoint concreto.
 *
 * Implementaciones esperadas:
 * - Producción: `RetrofitSyncTransport` (Fase 4) que traduzca cada [SyncQueueItem] a su DTO.
 * - Pruebas: un fake que devuelva [SyncOutcome.SUCCESS] o falle a voluntad.
 */
fun interface SyncTransport {
    suspend fun send(item: SyncQueueItem): SyncOutcome
}
