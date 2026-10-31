package com.cronobus.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.cronobus.app.data.local.entity.IncidenceReportEntity
import kotlinx.coroutines.flow.Flow

/**
 * Acceso a los reportes de incidencia operativa.
 *
 * Además de la cola Store & Forward expone la consulta que alimenta la regla de **Exención
 * Automática por Fuerza Mayor** (≥ 3 unidades en 500 m / 15 min).
 */
@Dao
interface IncidenceReportDao {

    @Insert
    suspend fun insert(report: IncidenceReportEntity): Long

    @Query(
        """
        SELECT * FROM incidence_reports
        WHERE syncStatus = 'PENDING'
        ORDER BY timestampUtcMs ASC, elapsedRealtimeNanos ASC, id ASC
        LIMIT :limit
        """,
    )
    suspend fun getPendingReports(limit: Int): List<IncidenceReportEntity>

    @Query("SELECT * FROM incidence_reports WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<IncidenceReportEntity>

    @Query(
        """
        UPDATE incidence_reports
        SET syncStatus = 'SYNCING', lastSyncAttemptMs = :attemptAtUtcMs
        WHERE id = :id AND syncStatus != 'SYNCED'
        """,
    )
    suspend fun markAsSyncing(id: Long, attemptAtUtcMs: Long)

    @Query("UPDATE incidence_reports SET syncStatus = 'SYNCED' WHERE id = :id")
    suspend fun markAsSynced(id: Long)

    @Query(
        """
        UPDATE incidence_reports
        SET syncStatus = 'FAILED', lastSyncAttemptMs = :attemptAtUtcMs
        WHERE id = :id
        """,
    )
    suspend fun markAsFailed(id: Long, attemptAtUtcMs: Long)

    @Query(
        """
        UPDATE incidence_reports
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

    @Query("UPDATE incidence_reports SET syncStatus = 'PENDING' WHERE syncStatus = 'SYNCING'")
    suspend fun resetStuckSyncing(): Int

    @Query("UPDATE incidence_reports SET syncStatus = 'PENDING' WHERE syncStatus = 'FAILED'")
    suspend fun promoteFailedToPending(): Int

    @Query("SELECT COUNT(*) FROM incidence_reports WHERE syncStatus != 'SYNCED'")
    fun observePendingCount(): Flow<Int>

    /**
     * Candidatos recientes de la misma ruta dentro de la caja envolvente de la ventana radial.
     *
     * SQLite no garantiza funciones trigonométricas en todas las versiones de Android, por lo que
     * el filtro exacto de Haversine (≤ 500 m) y el conteo de unidades distintas se aplican en
     * `EvaluateForceMajeureUseCase` (Fase 4) sobre este conjunto ya reducido por índice.
     *
     * Solo considera `TRAFFIC` y `BLOCKADE`, que son los eventos que justifican una exención.
     */
    @Query(
        """
        SELECT * FROM incidence_reports
        WHERE routeId = :routeId
          AND incidenceType IN ('TRAFFIC', 'BLOCKADE')
          AND timestampUtcMs >= :sinceUtcMs
          AND latitude BETWEEN :minLatitude AND :maxLatitude
          AND longitude BETWEEN :minLongitude AND :maxLongitude
        ORDER BY timestampUtcMs DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecentForceMajeureCandidates(
        routeId: String,
        sinceUtcMs: Long,
        minLatitude: Double,
        maxLatitude: Double,
        minLongitude: Double,
        maxLongitude: Double,
        limit: Int,
    ): List<IncidenceReportEntity>

    /** Historial reciente de un tramo, útil para el panel de despacho. */
    @Query(
        """
        SELECT * FROM incidence_reports
        WHERE routeId = :routeId AND timestampUtcMs >= :sinceUtcMs
        ORDER BY timestampUtcMs DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecentByRoute(
        routeId: String,
        sinceUtcMs: Long,
        limit: Int,
    ): List<IncidenceReportEntity>
}
