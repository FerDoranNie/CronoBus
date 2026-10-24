package com.cronobus.app.domain.location

import com.cronobus.app.domain.model.GeoPoint
import com.cronobus.app.domain.model.LocationSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regresiones de la auditoría (Semana 1 · Paso 3): estabilidad numérica ante entradas degeneradas. */
class KalmanRobustnessTest {

    private val anchor = GeoPoint(19.4326, -99.1332)

    @Test
    fun `rumbo y velocidad desconocidos (NaN) no envenenan el estado`() {
        val filter = KalmanLocationFilter()

        val first = filter.update(sample(speed = 5.0, bearing = Double.NaN, nanos = 0L))
        assertNotNull(first)
        assertFinite(first!!.position.latitude, first.position.longitude, first.speedMps, first.bearingDegrees)

        for (second in 1..10) {
            val update = filter.update(
                sample(speed = Double.NaN, bearing = Double.NaN, nanos = second * ONE_SECOND_NANOS),
            )
            assertNotNull("El fix $second debe aceptarse", update)
            assertFinite(update!!.position.latitude, update.position.longitude, update.speedMps, update.bearingDegrees)
        }
    }

    @Test
    fun `precision cero o infinita se sanea a valor conservador`() {
        val filter = KalmanLocationFilter()

        val zero = filter.update(sample(accuracy = 0.0, nanos = 0L))
        assertEquals(30.0, zero!!.sourceAccuracyMeters, 0.001)

        val infinite = filter.update(sample(accuracy = Double.POSITIVE_INFINITY, nanos = ONE_SECOND_NANOS))
        assertNotNull(infinite)
        assertEquals(30.0, infinite!!.sourceAccuracyMeters, 0.001)
        assertTrue(infinite.isFiltered)
    }

    @Test
    fun `lecturas con dt casi cero no producen NaN`() {
        val filter = KalmanLocationFilter()
        filter.update(sample(nanos = 1_000L))

        val update = filter.update(sample(nanos = 1_001L))

        assertNotNull(update)
        assertFinite(update!!.position.latitude, update.position.longitude, update.speedMps)
    }

    @Test
    fun `la primera lectura y el reanclaje no se marcan como filtrados`() {
        val filter = KalmanLocationFilter()
        assertFalse(filter.update(sample(nanos = 0L))!!.isFiltered)
        assertTrue(filter.update(sample(nanos = ONE_SECOND_NANOS))!!.isFiltered)
        assertFalse(filter.update(sample(nanos = 40 * ONE_SECOND_NANOS))!!.isFiltered)
    }

    private fun assertFinite(vararg values: Double) {
        values.forEach { assertTrue("Valor no finito: $it", it.isFinite()) }
    }

    private fun sample(
        speed: Double = 0.0,
        bearing: Double = 0.0,
        accuracy: Double = 6.0,
        nanos: Long,
    ) = LocationSample(
        position = anchor,
        accuracyMeters = accuracy,
        speedMps = speed,
        bearingDegrees = bearing,
        altitudeMeters = null,
        provider = "fused",
        isMock = false,
        elapsedRealtimeNanos = nanos,
        wallClockEpochMs = 1_700_000_000_000L + nanos / 1_000_000L,
    )

    private companion object {
        const val ONE_SECOND_NANOS = 1_000_000_000L
    }
}

