package com.cronobus.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cronobus.app.data.local.IncidenceType
import com.cronobus.app.data.local.SyncStatus

/**
 * Reporte de incidencia operativa en ruta (tráfico pesado, bloqueo, accidente o falla mecánica).
 *
 * Utilizado para notificar al despacho y alimentar el algoritmo de exención de multas por fuerza mayor.
 */
@Entity(tableName = "incidence_reports")
data class IncidenceReportEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val shiftId: String,
    val unitId: String,
    val routeId: String,
    val incidenceType: IncidenceType,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val photoEvidenceUri: String? = null,
    val timestampUtcMs: Long,
    val elapsedRealtimeNanos: Long,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val retryCount: Int = 0,
    val lastSyncAttemptMs: Long? = null,
)

