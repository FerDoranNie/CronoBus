package com.cronobus.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cronobus.app.data.local.SyncStatus

/**
 * Registro de apelación de multa impuesta por exceso de intervalo o desvío de horario.
 *
 * Incluye evidencia fotográfica opcional y copia de la telemetría local persistida para auditoría.
 */
@Entity(tableName = "fine_disputes")
data class FineDisputeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val fineId: String,
    val shiftId: String,
    val unitId: String,
    val reason: String,
    val photoEvidenceUri: String? = null,
    val attachedTelemetryJson: String,
    val latitude: Double,
    val longitude: Double,
    val timestampUtcMs: Long,
    val elapsedRealtimeNanos: Long,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val retryCount: Int = 0,
    val lastSyncAttemptMs: Long? = null,
)

