package com.cronobus.app.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cronobus.app.data.local.CronoBusDatabase
import com.cronobus.app.data.local.IncidenceType
import com.cronobus.app.data.local.OccupancyLevel
import com.cronobus.app.data.local.entity.CheckpointEventEntity
import com.cronobus.app.data.local.entity.FineDisputeEntity
import com.cronobus.app.data.local.entity.IncidenceReportEntity
import com.cronobus.app.data.local.entity.ShiftLogEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifica el contrato de entrega del Store & Forward sin red ni backend reales.
 *
 * Cubre las dos garantías críticas del MVP:
 * 1. El lote se vacía en orden cronológico **global** aunque los registros vivan en tablas distintas.
 * 2. Un fallo transitorio no consume el resto de la cola ni pierde el registro.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoreAndForwardSyncManagerTest {

    private lateinit var database: CronoBusDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            CronoBusDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `drains every table in global chronological order`() = runTest {
        database.shiftLogDao().insert(shift(startAtUtcMs = 200L))
        database.checkpointEventDao().insert(checkpoint(atUtcMs = 100L))
        database.incidenceReportDao().insert(incidence(atUtcMs = 300L))
        database.fineDisputeDao().insert(dispute(atUtcMs = 400L))

        val transport = RecordingTransport()
        val manager = manager(transport, StandardTestDispatcher(testScheduler))

        val retryable = manager.drainOnce()

        assertFalse(retryable)
        assertEquals(
            listOf(100L, 200L, 300L, 400L),
            transport.sent.map { it.timestampUtcMs },
        )
        assertEquals(4, transport.sent.size)
        assertEquals(0, database.checkpointEventDao().getPendingEvents(10).size)
        assertEquals(0, database.incidenceReportDao().getPendingReports(10).size)
        assertEquals(0, database.fineDisputeDao().getPendingDisputes(10).size)
        assertEquals(0, database.shiftLogDao().getPendingShifts(10).size)

        manager.shutdown()
    }

    @Test
    fun `transient failure keeps the record pending and stops the batch`() = runTest {
        database.checkpointEventDao().insert(checkpoint(atUtcMs = 100L, checkpointId = "a"))
        database.checkpointEventDao().insert(checkpoint(atUtcMs = 200L, checkpointId = "b"))

        val transport = RecordingTransport { attempt ->
            if (attempt == 0) SyncOutcome.RETRYABLE_FAILURE else SyncOutcome.SUCCESS
        }
        val manager = manager(transport, StandardTestDispatcher(testScheduler))

        val retryable = manager.drainOnce()

        assertTrue(retryable)
        assertEquals(1, transport.sent.size)

        val pending = database.checkpointEventDao().getPendingEvents(10)
        assertEquals(2, pending.size)
        assertEquals(1, pending.first().retryCount)
        assertEquals("a", pending.first().checkpointId)

        manager.shutdown()
    }

    /**
     * Auditoría Paso 3: si el turno se cierra (updateEndShift → PENDING) mientras su versión
     * anterior está en vuelo, el ACK del envío viejo no debe marcarlo SYNCED; si lo hiciera,
     * la hora de fin nunca llegaría al backend.
     */
    @Test
    fun `shift closed while in flight is re-queued instead of marked synced`() = runTest {
        database.shiftLogDao().insert(shift(startAtUtcMs = 100L))

        val shiftDao = database.shiftLogDao()
        val transport = object : SyncTransport {
            val sentEndTimes = mutableListOf<Long?>()
            override suspend fun send(item: SyncQueueItem): SyncOutcome {
                sentEndTimes += (item as SyncQueueItem.Shift).entity.endTimestampUtcMs
                if (sentEndTimes.size == 1) shiftDao.updateEndShift("shift-1", 900L, 900_000L)
                return SyncOutcome.SUCCESS
            }
        }
        val manager = manager(transport, StandardTestDispatcher(testScheduler))

        manager.drainOnce()

        // Primer envío sin hora de fin; el ACK no pisa el PENDING, así que el siguiente lote del
        // mismo ciclo reenvía la versión cerrada y solo entonces queda SYNCED.
        assertEquals(listOf<Long?>(null, 900L), transport.sentEndTimes)
        assertEquals(0, shiftDao.getPendingShifts(10).size)

        manager.shutdown()
    }

    @Test
    fun `terminal transitions are ignored unless the row is SYNCING`() = runTest {
        val dao = database.checkpointEventDao()
        val id = dao.insert(checkpoint(atUtcMs = 100L))

        dao.markAsSynced(id)
        dao.markAsFailed(id, 1L)
        dao.incrementRetryCount(id, 1L, maxRetries = 1)

        val row = dao.getPendingEvents(10).single()
        assertEquals(0, row.retryCount)
    }

    private fun manager(
        transport: SyncTransport,
        dispatcher: CoroutineDispatcher,
    ): StoreAndForwardSyncManager = StoreAndForwardSyncManager(
        database = database,
        appContext = ApplicationProvider.getApplicationContext(),
        initialTransport = transport,
        config = SyncConfig(baseBackoffMs = 10L, maxBackoffMs = 10L),
        dispatcher = dispatcher,
        monitorConnectivity = false,
        autoStart = false,
        nowUtcMs = { 1_700_000_000_000L },
    )

    private class RecordingTransport(
        private val outcomeForAttempt: (Int) -> SyncOutcome = { SyncOutcome.SUCCESS },
    ) : SyncTransport {
        val sent = mutableListOf<SyncQueueItem>()

        override suspend fun send(item: SyncQueueItem): SyncOutcome {
            sent += item
            return outcomeForAttempt(sent.size - 1)
        }
    }

    private fun shift(startAtUtcMs: Long) = ShiftLogEntity(
        shiftId = "shift-1",
        driverAlias = "Op. Prueba",
        unitId = "14",
        routeId = "Ruta 42",
        brakesOk = true,
        lightsOk = true,
        oilOk = true,
        privacyAndLftConsentAccepted = true,
        consentTimestampUtcMs = startAtUtcMs,
        startTimestampUtcMs = startAtUtcMs,
        startElapsedRealtimeNanos = startAtUtcMs * 1_000L,
    )

    private fun checkpoint(
        atUtcMs: Long,
        checkpointId: String = "cp-1",
    ) = CheckpointEventEntity(
        shiftId = "shift-1",
        unitId = "14",
        routeId = "Ruta 42",
        checkpointId = checkpointId,
        checkpointName = "Base A",
        eventType = "ENTER",
        latitude = 19.4326,
        longitude = -99.1332,
        filteredSpeedMps = 8.5,
        headwayDeltaSeconds = 0L,
        occupancyLevel = OccupancyLevel.BAJA,
        timestampUtcMs = atUtcMs,
        elapsedRealtimeNanos = atUtcMs * 1_000L,
    )

    private fun incidence(atUtcMs: Long) = IncidenceReportEntity(
        shiftId = "shift-1",
        unitId = "14",
        routeId = "Ruta 42",
        incidenceType = IncidenceType.TRAFFIC,
        description = "Tráfico intenso",
        latitude = 19.4326,
        longitude = -99.1332,
        timestampUtcMs = atUtcMs,
        elapsedRealtimeNanos = atUtcMs * 1_000L,
    )

    private fun dispute(atUtcMs: Long) = FineDisputeEntity(
        fineId = "FINE-1",
        shiftId = "shift-1",
        unitId = "14",
        reason = "Bloqueo documentado",
        attachedTelemetryJson = "{}",
        latitude = 19.4326,
        longitude = -99.1332,
        timestampUtcMs = atUtcMs,
        elapsedRealtimeNanos = atUtcMs * 1_000L,
    )
}
