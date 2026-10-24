package com.cronobus.app.domain.security

import com.cronobus.app.domain.model.GeoPoint
import com.cronobus.app.domain.model.LocationSample
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresión de la auditoría (Semana 1 · Paso 3): la detección de reloj manipulado debe basarse en
 * la hora del sistema, no en `Location.time`, que el proveedor fusionado toma de fuentes distintas.
 */
class TamperClockSourceTest {

    private var nowNanos = 5_000_000_000_000L
    private var nowWallMs = 1_700_000_000_000L
    private val detector = TamperDetector(
        monotonicNowNanos = { nowNanos },
        wallClockNowMs = { nowWallMs },
    )

    @Test
    fun `cambio de fuente GNSS a red con hora del proveedor desfasada no bloquea el turno`() {
        assertTrue(detector.inspectFix(sample(providerTimeMs = nowWallMs)).acceptFix)

        nowNanos += ONE_SECOND_NANOS
        nowWallMs += 1_000L
        // El proveedor de red reporta su `time` 90 s adelantado respecto al GNSS.
        val report = detector.inspectFix(sample(providerTimeMs = nowWallMs + 90_000L))

        assertTrue(report.acceptFix)
        assertFalse(report.stopTracking)
    }

    @Test
    fun `atrasar la hora del sistema se detecta aunque el proveedor reporte hora correcta`() {
        detector.inspectFix(sample(providerTimeMs = nowWallMs))

        nowNanos += ONE_SECOND_NANOS
        val honestProviderTime = nowWallMs + 1_000L
        nowWallMs -= 30 * 60_000L // el conductor atrasa el reloj 30 min
        val report = detector.inspectFix(sample(providerTimeMs = honestProviderTime))

        assertFalse(report.acceptFix)
        assertTrue(report.stopTracking)
    }

    private fun sample(providerTimeMs: Long) = LocationSample(
        position = GeoPoint(19.4326, -99.1332),
        accuracyMeters = 5.0,
        speedMps = 8.0,
        bearingDegrees = 90.0,
        altitudeMeters = null,
        provider = "fused",
        isMock = false,
        elapsedRealtimeNanos = nowNanos,
        wallClockEpochMs = providerTimeMs,
    )

    private companion object {
        const val ONE_SECOND_NANOS = 1_000_000_000L
    }
}

