package com.cronobus.app.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cronobus.app.data.local.CronoBusDatabase
import com.cronobus.app.data.local.OccupancyLevel
import com.cronobus.app.data.local.entity.CheckpointEventEntity
import com.cronobus.app.domain.model.ForceMajeureResult
import com.cronobus.app.domain.model.HeadwayStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HeadwayCalculatorUseCaseTest {

    private lateinit var database: CronoBusDatabase
    private lateinit var useCase: HeadwayCalculatorUseCase

    private val routeId = "Ruta 42 - Ramal Centro"

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            CronoBusDatabase::class.java,
        ).allowMainThreadQueries().build()

        useCase = HeadwayCalculatorUseCase(database.checkpointEventDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `on time when the delta is inside the tolerance band`() {
        val early = useCase.assess("cp", "Diana", 300L, 250L)
        assertEquals(-50L, early.deltaSeconds)
        assertEquals(HeadwayStatus.ON_TIME, early.status)
        assertEquals(0.0, early.suggestedFineAmountMxn, 0.001)

        val late = useCase.assess("cp", "Diana", 300L, 340L)
        assertEquals(40L, late.deltaSeconds)
        assertEquals(HeadwayStatus.ON_TIME, late.status)
    }

    @Test
    fun `moderate delay is a warning without a fine`() {
        val assessment = useCase.assess("cp", "Diana", 300L, 400L)

        assertEquals(100L, assessment.deltaSeconds)
        assertEquals(HeadwayStatus.WARNING, assessment.status)
        assertEquals(0.0, assessment.suggestedFineAmountMxn, 0.001)
    }

    @Test
    fun `delay beyond the warning window produces a per minute fine`() {
        val assessment = useCase.assess("cp", "Diana", 300L, 600L)

        assertEquals(300L, assessment.deltaSeconds)
        assertEquals(HeadwayStatus.LATE_FINE, assessment.status)
        // 5 minutos excedidos × $10
        assertEquals(50.0, assessment.suggestedFineAmountMxn, 0.001)
    }

    @Test
    fun `fine is capped to avoid disproportionate sanctions`() {
        val assessment = useCase.assess("cp", "Diana", 300L, 2_100L)

        assertEquals(HeadwayStatus.LATE_FINE, assessment.status)
        assertEquals(200.0, assessment.suggestedFineAmountMxn, 0.001)
    }

    @Test
    fun `force majeure converts a late fine into an exemption`() {
        val forceMajeure = ForceMajeureResult(
            isExempt = true,
            qualifyingUnitsCount = 3,
            distinctUnitIds = setOf("9", "14", "22"),
            reasonSummary = "Fuerza mayor verificada",
        )

        val assessment = useCase.assess("cp", "Diana", 300L, 600L, forceMajeure)

        assertEquals(HeadwayStatus.EXEMPT_FORCE_MAJEURE, assessment.status)
        assertEquals(0.0, assessment.suggestedFineAmountMxn, 0.001)
        assertEquals(forceMajeure, assessment.forceMajeureResult)
        // El monto teórico sigue disponible para el CSV.
        assertEquals(50.0, useCase.fineForDelta(assessment.deltaSeconds), 0.001)
    }

    @Test
    fun `crossing uses the previous different unit as reference`() = runTest {
        val previousAt = 1_760_000_000_000L
        database.checkpointEventDao().insert(
            checkpoint(unitId = "9", checkpointId = "diana", atUtcMs = previousAt),
        )

        val assessment = useCase.assessCrossing(
            checkpointId = "diana",
            checkpointName = "Estación Diana",
            routeId = routeId,
            unitId = "14",
            observedAtUtcMs = previousAt + 600_000L,
        )

        assertEquals(600L, assessment?.actualHeadwaySeconds)
        assertEquals(300L, assessment?.deltaSeconds)
        assertEquals(HeadwayStatus.LATE_FINE, assessment?.status)
    }

    @Test
    fun `first crossing has no reference and is not sanctioned`() = runTest {
        val assessment = useCase.assessCrossing(
            checkpointId = "diana",
            checkpointName = "Estación Diana",
            routeId = routeId,
            unitId = "14",
            observedAtUtcMs = 1_760_000_000_000L,
        )

        assertNull(assessment)
    }

    @Test
    fun `own previous crossing is ignored`() = runTest {
        val previousAt = 1_760_000_000_000L
        database.checkpointEventDao().insert(
            checkpoint(unitId = "14", checkpointId = "diana", atUtcMs = previousAt),
        )

        val assessment = useCase.assessCrossing(
            checkpointId = "diana",
            checkpointName = "Estación Diana",
            routeId = routeId,
            unitId = "14",
            observedAtUtcMs = previousAt + 600_000L,
        )

        assertNull(assessment)
    }

    @Test
    fun `stale reference crossing is not used for a fine`() = runTest {
        val previousAt = 1_760_000_000_000L
        database.checkpointEventDao().insert(
            checkpoint(unitId = "9", checkpointId = "diana", atUtcMs = previousAt),
        )

        // Tres horas después no hay una referencia operativa de headway.
        val assessment = useCase.assessCrossing(
            checkpointId = "diana",
            checkpointName = "Estación Diana",
            routeId = routeId,
            unitId = "14",
            observedAtUtcMs = previousAt + 3L * 60L * 60L * 1_000L,
        )

        assertNull(assessment)
    }

    @Test
    fun `fine starts only after the warning threshold`() {
        assertEquals(0.0, useCase.fineForDelta(180L), 0.001)
        assertTrue(useCase.fineForDelta(181L) > 0.0)
    }

    private fun checkpoint(
        unitId: String,
        checkpointId: String,
        atUtcMs: Long,
    ) = CheckpointEventEntity(
        shiftId = "shift-$unitId",
        unitId = unitId,
        routeId = routeId,
        checkpointId = checkpointId,
        checkpointName = "Estación Diana",
        eventType = "ENTER",
        latitude = 19.4255,
        longitude = -99.1720,
        filteredSpeedMps = 7.0,
        headwayDeltaSeconds = 0L,
        occupancyLevel = OccupancyLevel.BAJA,
        timestampUtcMs = atUtcMs,
        elapsedRealtimeNanos = atUtcMs * 1_000L,
    )
}
