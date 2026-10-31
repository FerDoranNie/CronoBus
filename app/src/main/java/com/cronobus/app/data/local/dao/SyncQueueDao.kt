package com.cronobus.app.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query

/**
 * Referencia ligera a un registro pendiente de sincronizar.
 *
 * Es una proyección (no una entidad): permite que SQLite haga el `UNION ALL` de las cuatro tablas
 * y devuelva **el siguiente lote en orden cronológico global** con un solo `ORDER BY ... LIMIT`.
 * Sin esta consulta, tomar lotes por tabla y mezclarlos en memoria podía enviar un evento de una
 * tabla antes que otro de timestamp menor perteneciente a una tabla cuyo lote quedó truncado.
 */
data class SyncQueueRef(
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "numericId") val numericId: Long?,
    @ColumnInfo(name = "textId") val textId: String?,
    @ColumnInfo(name = "timestampUtcMs") val timestampUtcMs: Long,
    @ColumnInfo(name = "elapsedRealtimeNanos") val elapsedRealtimeNanos: Long,
) {
    companion object {
        const val KIND_CHECKPOINT = "CHECKPOINT"
        const val KIND_INCIDENCE = "INCIDENCE"
        const val KIND_DISPUTE = "DISPUTE"
        const val KIND_SHIFT = "SHIFT"
    }
}

/**
 * Cola global de sincronización.
 *
 * El `ORDER BY` usa el reloj de pared como criterio primario y el monotónico como desempate; los
 * fixes con reloj manipulado nunca llegan a persistirse (los bloquea `TamperDetector`).
 */
@Dao
interface SyncQueueDao {

    @Query(
        """
        SELECT 'CHECKPOINT' AS kind,
               id AS numericId,
               CAST(NULL AS TEXT) AS textId,
               timestampUtcMs AS timestampUtcMs,
               elapsedRealtimeNanos AS elapsedRealtimeNanos
        FROM checkpoint_events
        WHERE syncStatus = 'PENDING'

        UNION ALL

        SELECT 'INCIDENCE' AS kind,
               id AS numericId,
               CAST(NULL AS TEXT) AS textId,
               timestampUtcMs AS timestampUtcMs,
               elapsedRealtimeNanos AS elapsedRealtimeNanos
        FROM incidence_reports
        WHERE syncStatus = 'PENDING'

        UNION ALL

        SELECT 'DISPUTE' AS kind,
               id AS numericId,
               CAST(NULL AS TEXT) AS textId,
               timestampUtcMs AS timestampUtcMs,
               elapsedRealtimeNanos AS elapsedRealtimeNanos
        FROM fine_disputes
        WHERE syncStatus = 'PENDING'

        UNION ALL

        SELECT 'SHIFT' AS kind,
               CAST(NULL AS INTEGER) AS numericId,
               shiftId AS textId,
               startTimestampUtcMs AS timestampUtcMs,
               startElapsedRealtimeNanos AS elapsedRealtimeNanos
        FROM shift_logs
        WHERE syncStatus = 'PENDING'

        ORDER BY timestampUtcMs ASC, elapsedRealtimeNanos ASC, kind ASC
        LIMIT :limit
        """,
    )
    suspend fun getNextPendingRefs(limit: Int): List<SyncQueueRef>
}
