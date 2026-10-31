package com.cronobus.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cronobus.app.data.local.SyncStatus

/**
 * Bitácora de turno de conductor.
 *
 * Cumplimiento legal LFT y LGPDPPSO:
 * - Registra la firma y consentimiento expreso de geolocalización laboral activa antes de rastrear.
 * - Registra el checklist de mantenimiento preventivo (frenos, luces, aceite) para seguridad en el transporte.
 * - No almacena datos personales sensibles ni biométricos; únicamente el alias operativo (`driverAlias`).
 */
@Entity(tableName = "shift_logs")
data class ShiftLogEntity(
    @PrimaryKey
    val shiftId: String,
    val driverAlias: String,
    val unitId: String,
    val routeId: String,
    val brakesOk: Boolean,
    val lightsOk: Boolean,
    val oilOk: Boolean,
    val privacyAndLftConsentAccepted: Boolean,
    val consentTimestampUtcMs: Long,
    val startTimestampUtcMs: Long,
    val startElapsedRealtimeNanos: Long,
    val endTimestampUtcMs: Long? = null,
    val endElapsedRealtimeNanos: Long? = null,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val retryCount: Int = 0,
)

