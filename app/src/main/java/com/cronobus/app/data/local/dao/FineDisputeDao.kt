package com.cronobus.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.cronobus.app.data.local.entity.FineDisputeEntity
import kotlinx.coroutines.flow.Flow

/**
 * Acceso a las apelaciones de multa con su evidencia y telemetría adjunta.
 *
 * La telemetría viaja como JSON inmutable ([FineDisputeEntity.attachedTelemetryJson]) para que la
 * apelación siga siendo auditable aunque el resto de la base local se depure.
 */
@Dao
interface FineDisputeDao {

    @Insert
    suspend fun insert(dispute: FineDisputeEntity): Long

    @Query(
        """
        SELECT * FROM fine_disputes
        WHERE syncStatus = 'PENDING'
        ORDER BY timestampUtcMs ASC, elapsedRealtimeNanos ASC, id ASC
        LIMIT :limit
        """,
    )
    suspend fun getPendingDisputes(limit: Int): List<FineDisputeEntity>

    @Query("SELECT * FROM fine_disputes WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<FineDisputeEntity>

    @Query(
        """
        UPDATE fine_disputes
        SET syncStatus = 'SYNCING', lastSyncAttemptMs = :attemptAtUtcMs
        WHERE id = :id AND syncStatus != 'SYNCED'
        """,
    )
    suspend fun markAsSyncing(id: Long, attemptAtUtcMs: Long)

    @Query("UPDATE fine_disputes SET syncStatus = 'SYNCED' WHERE id = :id")
    suspend fun markAsSynced(id: Long)

    @Query(
        """
        UPDATE fine_disputes
        SET syncStatus = 'FAILED', lastSyncAttemptMs = :attemptAtUtcMs
        WHERE id = :id
        """,
    )
    suspend fun markAsFailed(id: Long, attemptAtUtcMs: Long)

    @Query(
        """
        UPDATE fine_disputes
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

    @Query("UPDATE fine_disputes SET syncStatus = 'PENDING' WHERE syncStatus = 'SYNCING'")
    suspend fun resetStuckSyncing(): Int

    @Query("UPDATE fine_disputes SET syncStatus = 'PENDING' WHERE syncStatus = 'FAILED'")
    suspend fun promoteFailedToPending(): Int

    @Query("SELECT COUNT(*) FROM fine_disputes WHERE syncStatus != 'SYNCED'")
    fun observePendingCount(): Flow<Int>
}
