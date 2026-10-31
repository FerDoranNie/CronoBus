package com.cronobus.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cronobus.app.data.local.OccupancyLevel
import com.cronobus.app.data.local.SyncStatus

/**
 * Evento de cruce o presencia en geocerca de punto de control (checador digital).
 *
 * Persistido localmente en Room y encolado para sincronización Store & Forward.
 */
@Entity(tableName = "checkpoint_events")
data class CheckpointEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val shiftId: String,
    val unitId: String,
    val routeId: String,
    val checkpointId: String,
    val checkpointName: String,
    val eventType: String, // "ENTER" o "EXIT"
    val latitude: Double,
    val longitude: Double,
    val filteredSpeedMps: Double,
    val headwayDeltaSeconds: Long,
    val occupancyLevel: OccupancyLevel,
    val timestampUtcMs: Long,
    val elapsedRealtimeNanos: Long,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val retryCount: Int = 0,
    val lastSyncAttemptMs: Long? = null,
)

