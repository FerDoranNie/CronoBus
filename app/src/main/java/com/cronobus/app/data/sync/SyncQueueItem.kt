package com.cronobus.app.data.sync

import com.cronobus.app.data.local.entity.CheckpointEventEntity
import com.cronobus.app.data.local.entity.FineDisputeEntity
import com.cronobus.app.data.local.entity.IncidenceReportEntity
import com.cronobus.app.data.local.entity.ShiftLogEntity

/**
 * Elemento unificado de la cola Store & Forward.
 *
 * Las cuatro tablas operativas se vacían en un **único orden cronológico** para que el backend
 * reconstruya la operación tal como ocurrió (turno → cruces → incidencias → apelaciones), sin
 * importar en qué tabla se persistió cada hecho.
 *
 * El orden usa `timestampUtcMs` como criterio primario y `elapsedRealtimeNanos` como desempate
 * dentro de la misma sesión de arranque (el monotónico se reinicia al reiniciar el dispositivo).
 * Los relojes manipulados son rechazados antes de persistir por `TamperDetector`, por lo que la
 * marca de pared sigue siendo confiable para ordenar.
 */
sealed interface SyncQueueItem {

    /** Identificador local estable, usado como último desempate y para logs. */
    val localId: String

    val timestampUtcMs: Long

    val elapsedRealtimeNanos: Long

    val retryCount: Int

    data class Checkpoint(
        val entity: CheckpointEventEntity,
    ) : SyncQueueItem {
        override val localId: String get() = "checkpoint:${entity.id}"
        override val timestampUtcMs: Long get() = entity.timestampUtcMs
        override val elapsedRealtimeNanos: Long get() = entity.elapsedRealtimeNanos
        override val retryCount: Int get() = entity.retryCount
    }

    data class Incidence(
        val entity: IncidenceReportEntity,
    ) : SyncQueueItem {
        override val localId: String get() = "incidence:${entity.id}"
        override val timestampUtcMs: Long get() = entity.timestampUtcMs
        override val elapsedRealtimeNanos: Long get() = entity.elapsedRealtimeNanos
        override val retryCount: Int get() = entity.retryCount
    }

    data class Dispute(
        val entity: FineDisputeEntity,
    ) : SyncQueueItem {
        override val localId: String get() = "dispute:${entity.id}"
        override val timestampUtcMs: Long get() = entity.timestampUtcMs
        override val elapsedRealtimeNanos: Long get() = entity.elapsedRealtimeNanos
        override val retryCount: Int get() = entity.retryCount
    }

    data class Shift(
        val entity: ShiftLogEntity,
    ) : SyncQueueItem {
        override val localId: String get() = "shift:${entity.shiftId}"
        override val timestampUtcMs: Long get() = entity.startTimestampUtcMs
        override val elapsedRealtimeNanos: Long get() = entity.startElapsedRealtimeNanos
        override val retryCount: Int get() = entity.retryCount
    }

    companion object {
        /**
         * Orden canónico en memoria (pruebas y consumidores que reciban lotes ya hidratados).
         * El orden real del vaciado lo impone `SyncQueueDao` en SQL para que el `LIMIT` global
         * nunca deje atrás un registro más antiguo de otra tabla.
         */
        val CHRONOLOGICAL_ORDER: Comparator<SyncQueueItem> =
            compareBy<SyncQueueItem> { it.timestampUtcMs }
                .thenBy { it.elapsedRealtimeNanos }
                .thenBy { it.localId }
    }
}
