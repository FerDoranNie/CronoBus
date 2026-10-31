package com.cronobus.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cronobus.app.data.local.entity.ShiftLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * Acceso a la bitácora de turnos (consentimiento LFT / LGPDPPSO + checklist preventivo).
 *
 * El turno se sincroniza al iniciar (el despacho necesita saber qué unidad está en ruta) y se
 * vuelve a encolar al cerrarlo: [updateEndShift] fuerza `PENDING` para reenviar el registro
 * completo con su hora de fin.
 */
@Dao
interface ShiftLogDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(shift: ShiftLogEntity)

    /** Turno abierto más reciente, o `null` si el conductor no tiene turno activo. */
    @Query(
        """
        SELECT * FROM shift_logs
        WHERE endTimestampUtcMs IS NULL
        ORDER BY startTimestampUtcMs DESC
        LIMIT 1
        """,
    )
    suspend fun getActiveShift(): ShiftLogEntity?

    @Query(
        """
        UPDATE shift_logs
        SET endTimestampUtcMs = :endTimestampUtcMs,
            endElapsedRealtimeNanos = :endElapsedRealtimeNanos,
            syncStatus = 'PENDING',
            retryCount = 0
        WHERE shiftId = :shiftId
        """,
    )
    suspend fun updateEndShift(
        shiftId: String,
        endTimestampUtcMs: Long,
        endElapsedRealtimeNanos: Long,
    )

    @Query(
        """
        SELECT * FROM shift_logs
        WHERE syncStatus = 'PENDING'
        ORDER BY startTimestampUtcMs ASC, shiftId ASC
        LIMIT :limit
        """,
    )
    suspend fun getPendingShifts(limit: Int): List<ShiftLogEntity>

    @Query("SELECT * FROM shift_logs WHERE shiftId IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<ShiftLogEntity>

    @Query(
        """
        UPDATE shift_logs
        SET syncStatus = 'SYNCING', lastSyncAttemptMs = :attemptAtUtcMs
        WHERE shiftId = :shiftId AND syncStatus != 'SYNCED'
        """,
    )
    suspend fun markAsSyncing(shiftId: String, attemptAtUtcMs: Long)

    @Query("UPDATE shift_logs SET syncStatus = 'SYNCED' WHERE shiftId = :shiftId")
    suspend fun markAsSynced(shiftId: String)

    @Query(
        """
        UPDATE shift_logs
        SET syncStatus = 'FAILED', lastSyncAttemptMs = :attemptAtUtcMs
        WHERE shiftId = :shiftId
        """,
    )
    suspend fun markAsFailed(shiftId: String, attemptAtUtcMs: Long)

    @Query(
        """
        UPDATE shift_logs
        SET retryCount = retryCount + 1,
            lastSyncAttemptMs = :attemptAtUtcMs,
            syncStatus = CASE
                WHEN retryCount + 1 >= :maxRetries THEN 'FAILED'
                ELSE 'PENDING'
            END
        WHERE shiftId = :shiftId
        """,
    )
    suspend fun incrementRetryCount(shiftId: String, attemptAtUtcMs: Long, maxRetries: Int)

    @Query("UPDATE shift_logs SET syncStatus = 'PENDING' WHERE syncStatus = 'SYNCING'")
    suspend fun resetStuckSyncing(): Int

    @Query("UPDATE shift_logs SET syncStatus = 'PENDING' WHERE syncStatus = 'FAILED'")
    suspend fun promoteFailedToPending(): Int

    @Query("SELECT COUNT(*) FROM shift_logs WHERE syncStatus != 'SYNCED'")
    fun observePendingCount(): Flow<Int>
}
