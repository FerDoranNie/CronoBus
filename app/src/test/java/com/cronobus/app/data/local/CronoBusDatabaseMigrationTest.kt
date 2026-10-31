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
 * Valida la migración no destructiva `1 → 2` ([CronoBusDatabase.MIGRATION_1_2]):
 * - Crea el esquema v1 de `checkpoint_events` con datos de evidencia preexistentes.
 * - Ejecuta `MIGRATION_1_2.migrate(db)` y verifica que el índice compuesto
 *   `index_checkpoint_events_checkpointId_routeId_timestampUtcMs` exista sobre
 *   `(checkpointId, routeId, timestampUtcMs)`.
 * - Garantiza que los registros de evidencia del checador se conserven intactos.
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
}
