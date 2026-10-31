package com.cronobus.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.cronobus.app.data.local.dao.CheckpointEventDao
import com.cronobus.app.data.local.dao.FineDisputeDao
import com.cronobus.app.data.local.dao.IncidenceReportDao
import com.cronobus.app.data.local.dao.ShiftLogDao
import com.cronobus.app.data.local.dao.SyncQueueDao
import com.cronobus.app.data.local.entity.CheckpointEventEntity
import com.cronobus.app.data.local.entity.FineDisputeEntity
import com.cronobus.app.data.local.entity.IncidenceReportEntity
import com.cronobus.app.data.local.entity.ShiftLogEntity

/**
 * Base de datos local **offline-first** de CronoBus (Store & Forward).
 *
 * - `version = 1` con `exportSchema = false`: el esquema todavía evoluciona dentro del MVP. Antes
 *   de publicar en producción hay que activar `exportSchema` y versionar las migraciones; nunca
 *   usar `fallbackToDestructiveMigration()` porque borraría evidencia de turnos y apelaciones.
 * - Los `TypeConverters` serializan los enums como su `name` en texto plano, legible para auditoría.
 * - El journal por defecto de Room es WAL, adecuado para escrituras concurrentes del servicio GPS,
 *   el puente JS y el sincronizador.
 */
@Database(
    entities = [
        CheckpointEventEntity::class,
        IncidenceReportEntity::class,
        FineDisputeEntity::class,
        ShiftLogEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(CronoBusTypeConverters::class)
abstract class CronoBusDatabase : RoomDatabase() {

    abstract fun checkpointEventDao(): CheckpointEventDao

    abstract fun incidenceReportDao(): IncidenceReportDao

    abstract fun fineDisputeDao(): FineDisputeDao

    abstract fun shiftLogDao(): ShiftLogDao

    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        const val DATABASE_NAME = "cronobus.db"

        @Volatile
        private var instance: CronoBusDatabase? = null

        /**
         * Singleton thread-safe (double-checked locking) ligado al `applicationContext` para no
         * retener Activities en el sincronizador ni en el puente JS.
         */
        fun getInstance(context: Context): CronoBusDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): CronoBusDatabase =
            Room.databaseBuilder(context, CronoBusDatabase::class.java, DATABASE_NAME)
                // Sin fallback destructivo: una migración faltante debe fallar en QA, no en la calle.
                .build()
    }
}
