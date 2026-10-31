package com.cronobus.app.domain.usecase

import com.cronobus.app.domain.model.DispatchQueueSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FairDispatchRotationUseCaseTest {

    private val useCase = FairDispatchRotationUseCase(
        departureIntervalSeconds = 300L,
        punctualityBonusMxn = 45.0,
    )

    private val now = 1_760_000_000_000L

    @Test
    fun `queue puts never dispatched units first and excludes unavailable ones`() {
        val queue = useCase.buildQueue(
            candidates = listOf(
                candidate("B", lastDeparture = 5_000L),
                candidate("A", lastDeparture = null),
                candidate("D", lastDeparture = null, available = false),
                candidate("C", lastDeparture = 1_000L),
            ),
            nowUtcMs = now,
        )

        assertEquals(listOf("A", "C", "B"), queue.map { it.unitId })
        assertEquals(listOf(1, 2, 3), queue.map { it.position })
        assertEquals(now, queue[0].scheduledDepartureEpochMs)
        assertEquals(now + 300_000L, queue[1].scheduledDepartureEpochMs)
        assertEquals(now + 600_000L, queue[2].scheduledDepartureEpochMs)
    }

    @Test
    fun `departing unit moves to the back and everyone advances`() {
        val initial = useCase.buildQueue(
            candidates = listOf(
                candidate("A", lastDeparture = 1L),
                candidate("B", lastDeparture = 2L),
                candidate("C", lastDeparture = 3L),
            ),
            nowUtcMs = now,
        )

        val rotated = useCase.rotateAfterDeparture(initial, departingUnitId = "A", nowUtcMs = now + 10_000L)

        assertEquals(listOf("B", "C", "A"), rotated.map { it.unitId })
        assertEquals(listOf(1, 2, 3), rotated.map { it.position })
        assertEquals(now + 310_000L, rotated[0].scheduledDepartureEpochMs)
        assertEquals(now + 610_000L, rotated[1].scheduledDepartureEpochMs)
        assertEquals(now + 910_000L, rotated[2].scheduledDepartureEpochMs)
    }

    @Test
    fun `unknown departing unit leaves the queue intact`() {
        val queue = useCase.buildQueue(
            candidates = listOf(candidate("A"), candidate("B")),
            nowUtcMs = now,
        )

        val rotated = useCase.rotateAfterDeparture(queue, departingUnitId = "ZZZ", nowUtcMs = now)

        assertEquals(queue, rotated)
    }

    @Test
    fun `metrics update disqualifies the bonus when fines exist`() {
        val queue = useCase.buildQueue(
            candidates = listOf(candidate("A"), candidate("B")),
            nowUtcMs = now,
        )

        val updated = useCase.updateMetrics(
            queue = queue,
            unitId = "B",
            accumulatedFineMxn = 50.0,
            forceMajeureExemptionsCount = 2,
        )

        val unitB = updated.first { it.unitId == "B" }
        assertEquals(50.0, unitB.accumulatedFineMxn, 0.001)
        assertFalse(unitB.bonusEligible)
        assertEquals(2, unitB.forceMajeureExemptionsCount)
        assertEquals(0.0, useCase.bonusFor(unitB), 0.001)

        val unitA = updated.first { it.unitId == "A" }
        assertTrue(unitA.bonusEligible)
        assertEquals(45.0, useCase.bonusFor(unitA), 0.001)
    }

    @Test
    fun `next slot is the minimum position`() {
        val queue = useCase.buildQueue(
            candidates = listOf(candidate("A"), candidate("B"), candidate("C")),
            nowUtcMs = now,
        )

        assertEquals("A", useCase.nextSlot(queue)?.unitId)
        assertNull(useCase.nextSlot(emptyList()))
    }

    @Test
    fun `empty queue is handled`() {
        assertEquals(emptyList<DispatchQueueSlot>(), useCase.buildQueue(emptyList(), now))
    }

    private fun candidate(
        unitId: String,
        lastDeparture: Long? = null,
        available: Boolean = true,
    ) = DispatchCandidate(
        unitId = unitId,
        driverAlias = "Operador $unitId",
        routeId = "Ruta 42 - Ramal Centro",
        lastDepartureEpochMs = lastDeparture,
        accumulatedFineMxn = 0.0,
        forceMajeureExemptionsCount = 0,
        available = available,
    )
}
