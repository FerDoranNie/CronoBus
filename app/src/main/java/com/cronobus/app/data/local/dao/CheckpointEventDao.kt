package com.cronobus.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.cronobus.app.data.local.entity.CheckpointEventEntity
import kotlinx.coroutines.flow.Flow

/**
 * Acceso a los cruces de geocerca del checador digital.
 *
 * Todas las consultas de la cola operan con los literales `PENDING` / `SYNCING` / `SYNCED` /
 * `FAILED`, que es exactamente lo que persiste [com.cronobus.app.data.local.CronoBusTypeConverters].
 * Se evita parametrizar enums para que la consulta sea inmune a cambios de nombre del enum.
 */
@Dao
interface CheckpointEventDao {

    @Insert
    suspend fun insert(event: CheckpointEventEntity): Long

    /**
     * Lote pendiente en orden cronológico estricto (reloj de pared + reloj monotónico + id como
     * desempate estable). El Store & Forward vacía la cola en ese mismo orden.
     */
    @Query(
        """
        SELECT * FROM checkpoint_events
        WHERE syncStatus = 'PENDING'
        ORDER BY timestampUtcMs ASC, elapsedRealtimeNanos ASC, id ASC
        LIMIT :limit
        """,
    )
    suspend fun getPendingEvents(limit: Int): List<CheckpointEventEntity>

    /** Hidrata el lote global que devuelve [SyncQueueDao] sin perder el orden de la proyección. */
    @Query("SELECT * FROM checkpoint_events WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<CheckpointEventEntity>

    @Query(
        """
        UPDATE checkpoint_events
        SET syncStatus = 'SYNCING', lastSyncAttemptMs = :attemptAtUtcMs
        WHERE id = :id AND syncStatus != 'SYNCED'
        """,
    )
    suspend fun markAsSyncing(id: Long, attemptAtUtcMs: Long)

    @Query("UPDATE checkpoint_events SET syncStatus = 'SYNCED' WHERE id = :id")
    suspend fun markAsSynced(id: Long)

    @Query(
        """
        UPDATE checkpoint_events
        SET syncStatus = 'FAILED', lastSyncAttemptMs = :attemptAtUtcMs
        WHERE id = :id
        """,
    )
    suspend fun markAsFailed(id: Long, attemptAtUtcMs: Long)

    /**
     * Suma un intento fallido. Si se alcanza [maxRetries] el registro queda en `FAILED` para que
     * deje de competir por ancho de banda; el próximo cambio de conectividad lo vuelve a promover
     * a `PENDING` (ver [promoteFailedToPending]).
     */
    @Query(
        """
        UPDATE checkpoint_events
        SET retryCount = retryCount + 1,
            lastSyncAttemptMs = :attemptAtUtcMs,
            syncStatus = CASE
                WHEN retryCount + 1 >= :maxRetries THEN 'FAILED'
                ELSE 'PENDING'
            END
        WHERE id = :id
        """,
    )
    suspend fun incrementRetryCount(id: Long, attemptAtUtcMs: Long, maxRetries: Int)

    /**
     * Recupera registros que quedaron en `SYNCING` por muerte del proceso o cancelación del
     * coroutine a mitad de envío. Se ejecuta al inicio de cada ciclo de vaciado.
     */
    @Query("UPDATE checkpoint_events SET syncStatus = 'PENDING' WHERE syncStatus = 'SYNCING'")
    suspend fun resetStuckSyncing(): Int

    /** Reintenta los fallidos permanentes cuando la red vuelve a estar validada. */
    @Query("UPDATE checkpoint_events SET syncStatus = 'PENDING' WHERE syncStatus = 'FAILED'")
    suspend fun promoteFailedToPending(): Int

    /** Total de registros sin confirmar; alimenta el badge `SYNC: N PEND` del WebView. */
    @Query("SELECT COUNT(*) FROM checkpoint_events WHERE syncStatus != 'SYNCED'")
    fun observePendingCount(): Flow<Int>
}
