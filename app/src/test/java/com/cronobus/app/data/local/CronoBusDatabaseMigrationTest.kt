package com.cronobus.app.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Valida las migraciones no destructivas de CronoBus:
 * - `1 → 2`: índice compuesto de headway, conservando la evidencia del checador.
 * - `2 → 3`: tabla `financial_ledger` del Modelo Financiero Mixto, conservando toda la evidencia.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CronoBusDatabaseMigrationTest {

    @Test
    fun `migration 1 to 2 adds composite headway index and preserves existing evidence`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "cronobus_migration_1_2_test.db"
        context.deleteDatabase(dbName)

        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `checkpoint_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `shiftId` TEXT NOT NULL,
                        `unitId` TEXT NOT NULL,
                        `routeId` TEXT NOT NULL,
                        `checkpointId` TEXT NOT NULL,
                        `checkpointName` TEXT NOT NULL,
                        `eventType` TEXT NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `filteredSpeedMps` REAL NOT NULL,
                        `headwayDeltaSeconds` INTEGER NOT NULL,
                        `occupancyLevel` TEXT NOT NULL,
                        `timestampUtcMs` INTEGER NOT NULL,
                        `elapsedRealtimeNanos` INTEGER NOT NULL,
                        `syncStatus` TEXT NOT NULL,
                        `retryCount` INTEGER NOT NULL,
                        `lastSyncAttemptMs` INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_checkpoint_events_syncStatus_timestampUtcMs` " +
                        "ON `checkpoint_events` (`syncStatus`, `timestampUtcMs`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_checkpoint_events_shiftId` " +
                        "ON `checkpoint_events` (`shiftId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_checkpoint_events_checkpointId` " +
                        "ON `checkpoint_events` (`checkpointId`)",
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build(),
        )

        try {
            val db = helper.writableDatabase
            db.execSQL(
                """
                INSERT INTO `checkpoint_events` (
                    `shiftId`, `unitId`, `routeId`, `checkpointId`, `checkpointName`,
                    `eventType`, `latitude`, `longitude`, `filteredSpeedMps`,
                    `headwayDeltaSeconds`, `occupancyLevel`, `timestampUtcMs`,
                    `elapsedRealtimeNanos`, `syncStatus`, `retryCount`, `lastSyncAttemptMs`
                ) VALUES (
                    'shift-u14', '14', 'Ruta 42 - Ramal Centro', 'diana', 'Estación Diana',
                    'ENTER', 19.4255, -99.1720, 7.5,
                    45, 'MEDIA', 1760000000000,
                    1760000000000000, 'PENDING', 0, NULL
                )
                """.trimIndent(),
            )

            // Ejecuta la migración 1 → 2 dos veces para comprobar idempotencia (IF NOT EXISTS).
            CronoBusDatabase.MIGRATION_1_2.migrate(db)
            CronoBusDatabase.MIGRATION_1_2.migrate(db)

            val indexNames = mutableListOf<String>()
            db.query("PRAGMA index_list(`checkpoint_events`)").use { cursor ->
                val nameCol = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) {
                    indexNames += cursor.getString(nameCol)
                }
            }
            val expectedIndex = "index_checkpoint_events_checkpointId_routeId_timestampUtcMs"
            assertTrue(
                "El índice compuesto $expectedIndex debe existir tras MIGRATION_1_2",
                expectedIndex in indexNames,
            )

            val indexedColumns = mutableListOf<String>()
            db.query("PRAGMA index_info(`$expectedIndex`)").use { cursor ->
                val nameCol = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) {
                    indexedColumns += cursor.getString(nameCol)
                }
            }
            assertEquals(
                listOf("checkpointId", "routeId", "timestampUtcMs"),
                indexedColumns,
            )

            db.query("SELECT unitId, checkpointId, headwayDeltaSeconds, occupancyLevel FROM `checkpoint_events`")
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("14", cursor.getString(0))
                    assertEquals("diana", cursor.getString(1))
                    assertEquals(45L, cursor.getLong(2))
                    assertEquals("MEDIA", cursor.getString(3))
                }
        } finally {
            helper.close()
            context.deleteDatabase(dbName)
        }
    }

    /**
     * Valida `MIGRATION_2_3`: crea la tabla `financial_ledger` con el esquema exacto de
     * [com.cronobus.app.data.local.entity.FinancialLedgerEntity], conserva la evidencia previa y
     * es idempotente (`IF NOT EXISTS`).
     */
    @Test
    fun `migration 2 to 3 creates financial ledger and preserves existing evidence`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "cronobus_migration_2_3_test.db"
        context.deleteDatabase(dbName)

        val callback = object : SupportSQLiteOpenHelper.Callback(2) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `checkpoint_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `shiftId` TEXT NOT NULL,
                        `unitId` TEXT NOT NULL,
                        `routeId` TEXT NOT NULL,
                        `checkpointId` TEXT NOT NULL,
                        `checkpointName` TEXT NOT NULL,
                        `eventType` TEXT NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `filteredSpeedMps` REAL NOT NULL,
                        `headwayDeltaSeconds` INTEGER NOT NULL,
                        `occupancyLevel` TEXT NOT NULL,
                        `timestampUtcMs` INTEGER NOT NULL,
                        `elapsedRealtimeNanos` INTEGER NOT NULL,
                        `syncStatus` TEXT NOT NULL,
                        `retryCount` INTEGER NOT NULL,
                        `lastSyncAttemptMs` INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_checkpoint_events_checkpointId_routeId_timestampUtcMs` " +
                        "ON `checkpoint_events` (`checkpointId`, `routeId`, `timestampUtcMs`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_checkpoint_events_syncStatus_timestampUtcMs` " +
                        "ON `checkpoint_events` (`syncStatus`, `timestampUtcMs`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_checkpoint_events_shiftId` " +
                        "ON `checkpoint_events` (`shiftId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_checkpoint_events_checkpointId` " +
                        "ON `checkpoint_events` (`checkpointId`)",
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build(),
        )

        try {
            val db = helper.writableDatabase
            db.execSQL(
                """
                INSERT INTO `checkpoint_events` (
                    `shiftId`, `unitId`, `routeId`, `checkpointId`, `checkpointName`,
                    `eventType`, `latitude`, `longitude`, `filteredSpeedMps`,
                    `headwayDeltaSeconds`, `occupancyLevel`, `timestampUtcMs`,
                    `elapsedRealtimeNanos`, `syncStatus`, `retryCount`, `lastSyncAttemptMs`
                ) VALUES (
                    'shift-u14', '14', 'Ruta 42 - Ramal Centro', 'diana', 'Estación Diana',
                    'ENTER', 19.4255, -99.1720, 7.5,
                    45, 'MEDIA', 1760000000000,
                    1760000000000000, 'PENDING', 0, NULL
                )
                """.trimIndent(),
            )

            // Idempotencia: una actualización parcial no debe fallar al reejecutar el paso.
            CronoBusDatabase.MIGRATION_2_3.migrate(db)
            CronoBusDatabase.MIGRATION_2_3.migrate(db)

            val columns = mutableListOf<String>()
            db.query("PRAGMA table_info(`financial_ledger`)").use { cursor ->
                val nameCol = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) {
                    columns += cursor.getString(nameCol)
                }
            }
            assertEquals(
                listOf(
                    "unitId",
                    "routeId",
                    "grossFinesMxn",
                    "forgivenFinesMxn",
                    "assessmentsCount",
                    "exemptionsCount",
                    "updatedAtUtcMs",
                ),
                columns,
            )

            db.execSQL(
                """
                INSERT INTO `financial_ledger` (
                    `unitId`, `routeId`, `grossFinesMxn`, `forgivenFinesMxn`,
                    `assessmentsCount`, `exemptionsCount`, `updatedAtUtcMs`
                ) VALUES ('14', 'Ruta 42 - Ramal Centro', 120.0, 40.0, 3, 1, 1760000000000)
                """.trimIndent(),
            )
            db.query(
                "SELECT unitId, grossFinesMxn, forgivenFinesMxn, assessmentsCount, exemptionsCount " +
                    "FROM `financial_ledger`",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("14", cursor.getString(0))
                assertEquals(120.0, cursor.getDouble(1), 0.0001)
                assertEquals(40.0, cursor.getDouble(2), 0.0001)
                assertEquals(3, cursor.getInt(3))
                assertEquals(1, cursor.getInt(4))
            }

            db.query("SELECT unitId, checkpointId, occupancyLevel FROM `checkpoint_events`")
                .use { cursor ->
                    assertTrue("La evidencia del checador debe sobrevivir a 2 → 3", cursor.moveToFirst())
                    assertEquals("14", cursor.getString(0))
                    assertEquals("diana", cursor.getString(1))
                    assertEquals("MEDIA", cursor.getString(2))
                }
        } finally {
            helper.close()
            context.deleteDatabase(dbName)
        }
    }
}
