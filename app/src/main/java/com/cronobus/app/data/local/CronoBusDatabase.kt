package com.cronobus.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.cronobus.app.data.local.dao.CheckpointEventDao
import com.cronobus.app.data.local.dao.FinancialLedgerDao
import com.cronobus.app.data.local.dao.FineDisputeDao
import com.cronobus.app.data.local.dao.IncidenceReportDao
import com.cronobus.app.data.local.dao.ShiftLogDao
import com.cronobus.app.data.local.dao.SyncQueueDao
import com.cronobus.app.data.local.entity.CheckpointEventEntity
import com.cronobus.app.data.local.entity.FinancialLedgerEntity
import com.cronobus.app.data.local.entity.FineDisputeEntity
import com.cronobus.app.data.local.entity.IncidenceReportEntity
import com.cronobus.app.data.local.entity.ShiftLogEntity

/**
 * Base de datos local **offline-first** de CronoBus (Store & Forward).
 *
 * - `version = 3` con `exportSchema = false`: el esquema todavía evoluciona dentro del MVP. Antes
 *   de publicar en producción hay que activar `exportSchema` y versionar las migraciones; nunca
 *   usar `fallbackToDestructiveMigration()` porque borraría evidencia de turnos y apelaciones.
 * - `MIGRATION_2_3` agrega el ledger financiero sin tocar las tablas de evidencia existentes.
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
        FinancialLedgerEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(CronoBusTypeConverters::class)
abstract class CronoBusDatabase : RoomDatabase() {

    abstract fun checkpointEventDao(): CheckpointEventDao

    abstract fun incidenceReportDao(): IncidenceReportDao

    abstract fun fineDisputeDao(): FineDisputeDao

    abstract fun shiftLogDao(): ShiftLogDao

    abstract fun financialLedgerDao(): FinancialLedgerDao

    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        const val DATABASE_NAME = "cronobus.db"

        /** v2: índice compuesto para el headway; no toca datos (evidencia intacta). */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_checkpoint_events_checkpointId_routeId_timestampUtcMs` " +
                        "ON `checkpoint_events` (`checkpointId`, `routeId`, `timestampUtcMs`)",
                )
            }
        }

        /**
         * v3: ledger financiero persistente por unidad.
         *
         * Se crea una tabla nueva (no se altera ninguna existente), por lo que la evidencia de
         * turnos, cruces, incidencias y apelaciones queda intacta. Los importes se acumulan de
         * forma incremental en [FinancialLedgerDao.accumulateAssessment].
         */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `financial_ledger` (
                        `unitId` TEXT NOT NULL,
                        `routeId` TEXT NOT NULL,
                        `grossFinesMxn` REAL NOT NULL,
                        `forgivenFinesMxn` REAL NOT NULL,
                        `assessmentsCount` INTEGER NOT NULL,
                        `exemptionsCount` INTEGER NOT NULL,
                        `updatedAtUtcMs` INTEGER NOT NULL,
                        PRIMARY KEY(`unitId`)
                    )
                    """.trimIndent(),
                )
            }
        }

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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                // Sin fallback destructivo: una migración faltante debe fallar en QA, no en la calle.
                .build()
    }
}
