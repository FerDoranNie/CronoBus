package com.cronobus.app.domain.usecase

import com.cronobus.app.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassengerEtaCalculatorUseCaseTest {

    private val useCase = PassengerEtaCalculatorUseCase()

    private val routeId = "Ruta 42 - Ramal Centro"
    private val sevilla = GeoPoint(19.4270, -99.1676)
    private val terminal = GeoPoint(19.4215, -99.1824)
    private val now = 1_760_000_000_000L

    @Test
    fun `base eta uses distance and filtered speed`() {
        val quote = useCase.calculate(
            routeId = routeId,
            stopId = "terminal",
            stopName = "Terminal Centro",
            unitId = "14",
            unitPosition = sevilla,
            stopPosition = terminal,
            averageSpeedMps = 10.0,
            activeIncidentsCount = 0,
            occupancyLevel = "Baja",
            calculatedAtEpochMs = now,
        )

        assertTrue(quote.distanceMeters > 1_500.0)
        assertEquals(10.0, quote.filteredSpeedMps, 0.001)
        assertTrue(quote.etaMinutes >= 4)
        assertEquals("BAJA", quote.occupancyLevel)
        assertFalse(quote.isStaleCache)
    }

    @Test
    fun `each incident adds three minutes to the eta`() {
        val withoutIncidents = useCase.calculate(
            routeId, "terminal", "Terminal Centro", "14", sevilla, terminal,
            averageSpeedMps = 10.0,
            activeIncidentsCount = 0,
            occupancyLevel = "MEDIA",
            calculatedAtEpochMs = now,
        )
        val withTwoIncidents = useCase.calculate(
            routeId, "terminal", "Terminal Centro", "14", sevilla, terminal,
            averageSpeedMps = 10.0,
            activeIncidentsCount = 2,
            occupancyLevel = "MEDIA",
            calculatedAtEpochMs = now,
        )

        assertEquals(withoutIncidents.etaMinutes + 6, withTwoIncidents.etaMinutes)
        assertEquals(6, useCase.incidentDelayMinutes(2))
    }

    @Test
    fun `incident delay is capped`() {
        assertEquals(15, useCase.incidentDelayMinutes(50))
    }

    @Test
    fun `stopped unit uses the minimum operational speed instead of infinity`() {
        val quote = useCase.calculate(
            routeId, "terminal", "Terminal Centro", "14", sevilla, terminal,
            averageSpeedMps = 0.0,
            activeIncidentsCount = 0,
            occupancyLevel = "LLENO",
            calculatedAtEpochMs = now,
        )

        assertEquals(2.0, quote.filteredSpeedMps, 0.001)
        assertTrue(quote.etaMinutes >= 15)
        assertEquals("LLENO", quote.occupancyLevel)
    }

    @Test
    fun `invalid coordinates return an unavailable quote marked stale`() {
        val quote = useCase.calculate(
            routeId, "terminal", "Terminal Centro", "14",
            unitPosition = GeoPoint(Double.NaN, 0.0),
            stopPosition = terminal,
            averageSpeedMps = 10.0,
            activeIncidentsCount = 1,
            occupancyLevel = "BAJA",
            calculatedAtEpochMs = now,
        )

        assertEquals(0, quote.etaMinutes)
        assertEquals(0.0, quote.distanceMeters, 0.001)
        assertTrue(quote.isStaleCache)
    }

    @Test
    fun `speed is clamped to the operational maximum`() {
        val quote = useCase.calculate(
            routeId, "diana", "Estación Diana", "14", sevilla, terminal,
            averageSpeedMps = 999.0,
            activeIncidentsCount = 0,
            occupancyLevel = "BAJA",
            calculatedAtEpochMs = now,
        )

        assertEquals(22.0, quote.filteredSpeedMps, 0.001)
        assertTrue(quote.etaMinutes >= 1)
    }
}
