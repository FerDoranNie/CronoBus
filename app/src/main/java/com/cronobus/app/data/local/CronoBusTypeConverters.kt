package com.cronobus.app.data.local

import androidx.room.TypeConverter

/**
 * Conversores de tipo para almacenar y leer Enums en SQLite / Room.
 * Maneja valores desconocidos o nulos de forma segura y tolerante a fallos.
 */
class CronoBusTypeConverters {

    @TypeConverter
    fun fromSyncStatus(status: SyncStatus?): String? = status?.name

    @TypeConverter
    fun toSyncStatus(value: String?): SyncStatus? = value?.let {
        try {
            SyncStatus.valueOf(it)
        } catch (_: IllegalArgumentException) {
            SyncStatus.PENDING
        }
    }

    @TypeConverter
    fun fromOccupancyLevel(level: OccupancyLevel?): String? = level?.name

    @TypeConverter
    fun toOccupancyLevel(value: String?): OccupancyLevel? = value?.let {
        try {
            OccupancyLevel.valueOf(it)
        } catch (_: IllegalArgumentException) {
            OccupancyLevel.BAJA
        }
    }

    @TypeConverter
    fun fromIncidenceType(type: IncidenceType?): String? = type?.name

    @TypeConverter
    fun toIncidenceType(value: String?): IncidenceType? = value?.let {
        try {
            IncidenceType.valueOf(it)
        } catch (_: IllegalArgumentException) {
            IncidenceType.TRAFFIC
        }
    }
}

