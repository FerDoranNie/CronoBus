package com.cronobus.app.domain.security

import com.cronobus.app.domain.model.GeoPoint
import com.cronobus.app.domain.model.LocationSample
import com.cronobus.app.domain.model.TamperType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TamperDetectorTest {

    private var nowNanos = 1_000_000_000_000L
    private var nowWallMs = 1_700_000_000_000L

    private val detector = TamperDetector(
        monotonicNowNanos = { nowNanos },
        wallClockNowMs = { nowWallMs },
    )

    private val position = GeoPoint(19.4326, -99.1332)

    @Test
    fun `detecta mock location y ordena detener el rastreo`() {
        val report = detector.inspectFix(
            sample(isMock = true),
        )

        assertFalse(report.acceptFix)
        assertTrue(report.stopTracking)
        assertEquals(TamperType.MOCK_LOCATION, report.events.single().type)
    }

    @Test
    fun `detecta manipulacion del reloj comparando hora de pared y monotono`() {
        val first = detector.inspectFix(sample())
        assertTrue(first.acceptFix)

        nowNanos += ONE_SECOND_NANOS
        // El usuario adelanta 60 s el reloj del teléfono mientras el monotónico solo avanza 1 s.
        nowWallMs += 60_000L
        val report = detector.inspectFix(sample())

        assertFalse(report.acceptFix)
        assertTrue(report.stopTracking)
        assertEquals(TamperType.CLOCK_MANIPULATION, report.events.single().type)
    }

    @Test
    fun `detecta fixes con marca de tiempo futura`() {
        val report = detector.inspectFix(sample(elapsedNanos = nowNanos + 10_000_000_000L))

        assertFalse(report.acceptFix)
        assertTrue(report.stopTracking)
        assertEquals(TamperType.CLOCK_MANIPULATION, report.events.single().type)
    }

    @Test
    fun `descarta fixes obsoletos`() {
        val report = detector.inspectFix(sample(elapsedNanos = nowNanos - 60_000_000_000L))

        assertFalse(report.acceptFix)
        assertFalse(report.stopTracking)
        assertEquals(TamperType.STALE_FIX, report.events.single().type)
    }

    @Test
    fun `marca apagado sospechoso cerca de estacion con bateria suficiente`() {
        detector.updateBattery(levelPct = 78, isCharging = false)

        val report = detector.inspectProviderDisabled(position, distanceToNearestZoneMeters = 120.0)

        assertTrue(report.isTampered)
        val event = report.events.single()
        assertEquals(TamperType.NEAR_STATION_SHUTDOWN, event.type)
        assertEquals(78, event.batteryLevelPct)
        assertEquals(false, event.isCharging)
        assertEquals(120.0, event.distanceToNearestZoneMeters!!, 0.001)
    }

    @Test
    fun `un apagado con bateria baja no se considera evasion`() {
        detector.updateBattery(levelPct = 8, isCharging = false)

        val report = detector.inspectProviderDisabled(position, distanceToNearestZoneMeters = 90.0)

        assertEquals(TamperType.GPS_PROVIDER_DISABLED, report.events.single().type)
    }

    @Test
    fun `suprime alertas repetidas dentro de la ventana`() {
        detector.updateBattery(levelPct = 90, isCharging = false)

        val first = detector.inspectProviderDisabled(position, 80.0)
        nowNanos += 10 * ONE_SECOND_NANOS
        val second = detector.inspectProviderDisabled(position, 80.0)

        assertEquals(1, first.events.size)
        assertTrue(second.events.isEmpty())
    }

    @Test
    fun `reset limpia el ancla temporal`() {
        detector.inspectFix(sample())
        detector.reset()

        nowNanos += ONE_SECOND_NANOS
        nowWallMs += 60_000L
        val report = detector.inspectFix(sample())

        assertTrue("Sin ancla previa no debe reportarse drift", report.acceptFix)
    }

    private fun sample(
        isMock: Boolean = false,
        elapsedNanos: Long = nowNanos,
        wallClockEpochMs: Long = nowWallMs,
    ): LocationSample = LocationSample(
        position = position,
        accuracyMeters = 5.0,
        speedMps = 8.0,
        bearingDegrees = 90.0,
        altitudeMeters = 2_240.0,
        provider = "gps",
        isMock = isMock,
        elapsedRealtimeNanos = elapsedNanos,
        wallClockEpochMs = wallClockEpochMs,
    )

    private companion object {
        const val ONE_SECOND_NANOS = 1_000_000_000L
    }
}
