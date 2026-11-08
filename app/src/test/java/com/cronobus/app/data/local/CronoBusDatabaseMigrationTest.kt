package com.cronobus.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    /**
     * Auditoría Paso 3 (S4): ejecutar `MIGRATION_2_3.migrate(db)` a mano no prueba que Room acepte
     * el esquema resultante. Aquí se abre una base **Room real** tras migrar, lo que dispara
     * `onValidateSchema` (comparación `TableInfo` columna a columna, tipos, PK y nulabilidad contra
     * `FinancialLedgerEntity`). Si el DDL de la migración divergiera de la entidad, Room lanzaría
     * `IllegalStateException: Migration didn't properly handle: financial_ledger`.
     *
     * La base v2 se obtiene creando el esquema real de Room v3 y revirtiéndolo a v2 (sin
     * `financial_ledger`, `user_version = 2`), así las tablas de evidencia son byte a byte las que
     * genera KSP y no una copia manual que pueda quedar desactualizada.
     */
    @Test
    fun `room opens real database after migrating 2 to 3 and validates ledger schema`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "cronobus_room_open_2_3_test.db"
        context.deleteDatabase(dbName)

        try {
            prepareVersion2Database(context, dbName)

            val migrated = Room.databaseBuilder(context, CronoBusDatabase::class.java, dbName)
                .addMigrations(CronoBusDatabase.MIGRATION_1_2, CronoBusDatabase.MIGRATION_2_3)
                .allowMainThreadQueries()
                .build()
            try {
                val ledgerDao = migrated.financialLedgerDao()
                runBlocking {
                    // Primer acceso: abre la base, migra 2 → 3 y valida el esquema completo.
                    assertEquals(0, ledgerDao.count())

                    val first = ledgerDao.accumulateAssessment(
                        unitId = "14",
                        routeId = "Ruta 42 - Ramal Centro",
                        grossFineMxn = 120.0,
                        forgivenMxn = 0.0,
                        atUtcMs = 1_760_000_000_000L,
                    )
                    val second = ledgerDao.accumulateAssessment(
                        unitId = "14",
                        routeId = "Ruta 42 - Ramal Centro",
                        grossFineMxn = 80.0,
                        forgivenMxn = 80.0,
                        atUtcMs = 1_760_000_060_000L,
                    )
                    assertEquals(120.0, first.grossFinesMxn, 0.0001)
                    assertEquals(200.0, second.grossFinesMxn, 0.0001)
                    assertEquals(80.0, second.forgivenFinesMxn, 0.0001)
                    assertEquals(2, second.assessmentsCount)
                    assertEquals(1, second.exemptionsCount)
                    assertEquals(second, ledgerDao.getByUnit("14"))
                }

                val db = migrated.openHelper.writableDatabase
                assertEquals(3, db.version)
                db.query("SELECT unitId, checkpointId, occupancyLevel FROM `checkpoint_events`")
                    .use { cursor ->
                        assertTrue("La evidencia v2 debe sobrevivir a Room 2 → 3", cursor.moveToFirst())
                        assertEquals("14", cursor.getString(0))
                        assertEquals("diana", cursor.getString(1))
                        assertEquals("MEDIA", cursor.getString(2))
                    }
            } finally {
                migrated.close()
            }

            // Reapertura en frío (equivalente a muerte del proceso): el ledger sigue ahí.
            val reopened = Room.databaseBuilder(context, CronoBusDatabase::class.java, dbName)
                .addMigrations(CronoBusDatabase.MIGRATION_1_2, CronoBusDatabase.MIGRATION_2_3)
                .allowMainThreadQueries()
                .build()
            try {
                runBlocking {
                    val persisted = reopened.financialLedgerDao().getByUnit("14")
                    assertNotNull(persisted)
                    assertEquals(120.0, persisted!!.netFinesMxn, 0.0001)
                }
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(dbName)
        }
    }

    /** Sin `MIGRATION_2_3` Room debe fallar en QA (nunca hay fallback destructivo). */
    @Test
    fun `room refuses to open version 2 database without migration 2 to 3`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "cronobus_room_missing_2_3_test.db"
        context.deleteDatabase(dbName)

        try {
            prepareVersion2Database(context, dbName)

            val database = Room.databaseBuilder(context, CronoBusDatabase::class.java, dbName)
                .addMigrations(CronoBusDatabase.MIGRATION_1_2)
                .allowMainThreadQueries()
                .build()
            try {
                val failure = runCatching { database.openHelper.writableDatabase }.exceptionOrNull()
                assertTrue(
                    "Se esperaba IllegalStateException por migración faltante, se obtuvo $failure",
                    failure is IllegalStateException,
                )
            } finally {
                database.close()
            }

            // La evidencia no se borró por el intento fallido.
            SQLiteDatabase.openDatabase(
                context.getDatabasePath(dbName).path,
                null,
                SQLiteDatabase.OPEN_READONLY,
            ).use { raw ->
                raw.rawQuery("SELECT COUNT(*) FROM `checkpoint_events`", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(1, cursor.getInt(0))
                }
            }
        } finally {
            context.deleteDatabase(dbName)
        }
    }

    /**
     * Crea el esquema real de Room, inserta evidencia del checador y revierte la base al estado
     * v2: sin `financial_ledger`, `user_version = 2` y hash de identidad distinto al de v3.
     */
    private fun prepareVersion2Database(context: Context, dbName: String) {
        val bootstrap = Room.databaseBuilder(context, CronoBusDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        try {
            bootstrap.openHelper.writableDatabase.execSQL(
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
        } finally {
            bootstrap.close()
        }

        SQLiteDatabase.openDatabase(
            context.getDatabasePath(dbName).path,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { raw ->
            raw.execSQL("DROP TABLE IF EXISTS `financial_ledger`")
            raw.execSQL("UPDATE room_master_table SET identity_hash = 'cronobus-v2-test-hash'")
            raw.version = 2
        }
    }
}
