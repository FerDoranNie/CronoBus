package com.cronobus.app.domain.location

import com.cronobus.app.domain.model.GeoPoint
import com.cronobus.app.domain.model.LocationSample
import com.cronobus.app.domain.model.LocationUpdate
import kotlin.math.cos
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KalmanLocationFilterTest {

    private val anchor = GeoPoint(19.4326, -99.1332)

    @Test
    fun `reduce el ruido de un vehiculo detenido`() {
        val filter = KalmanLocationFilter()
        val random = Random(seed = 7)
        val rawErrors = mutableListOf<Double>()
        val filteredErrors = mutableListOf<Double>()

        for (second in 0 until SAMPLE_COUNT) {
            val north = (random.nextDouble() - 0.5) * 2.0 * NOISE_METERS
            val east = (random.nextDouble() - 0.5) * 2.0 * NOISE_METERS
            val noisyPosition = offsetMeters(anchor, north, east)

            rawErrors += GeoMath.haversineMeters(anchor, noisyPosition)

            val update = filter.update(
                sample(
                    position = noisyPosition,
                    speedMps = 0.0,
                    elapsedNanos = second * ONE_SECOND_NANOS,
                ),
            )
            assertNotNull(update)
            if (second >= WARMUP_SAMPLES) {
                filteredErrors += GeoMath.haversineMeters(anchor, update!!.position)
            }
        }

        val rawMean = rawErrors.average()
        val filteredMean = filteredErrors.average()
        assertTrue(
            "El filtro debe reducir el error medio (raw=$rawMean, filtrado=$filteredMean)",
            filteredMean < rawMean * 0.7,
        )
        val last = filter.latest
        assertNotNull(last)
        assertTrue("La velocidad debe permanecer cerca de cero", last!!.speedMps < 1.0)
    }

    @Test
    fun `estima la velocidad de un vehiculo en movimiento constante`() {
        val filter = KalmanLocationFilter()
        val random = Random(seed = 21)
        var last: LocationUpdate? = null

        for (second in 0 until SAMPLE_COUNT) {
            val distance = MOVING_SPEED_MPS * second
            val noisyPosition = offsetMeters(
                anchor,
                north = distance + (random.nextDouble() - 0.5) * 2.0 * 4.0,
                east = (random.nextDouble() - 0.5) * 2.0 * 4.0,
            )
            last = filter.update(
                sample(
                    position = noisyPosition,
                    speedMps = MOVING_SPEED_MPS,
                    elapsedNanos = second * ONE_SECOND_NANOS,
                ),
            )
            assertNotNull(last)
        }

        assertEquals(MOVING_SPEED_MPS, last!!.speedMps, 2.5)
        val expectedPosition = offsetMeters(anchor, MOVING_SPEED_MPS * (SAMPLE_COUNT - 1), 0.0)
        assertTrue(
            "La posición filtrada no debe rezagarse más de 15 m",
            GeoMath.haversineMeters(expectedPosition, last.position) < 15.0,
        )
    }

    @Test
    fun `descarta lecturas con precision pobre o coordenadas invalidas`() {
        val filter = KalmanLocationFilter()

        assertNull(filter.update(sample(anchor, accuracyMeters = 250.0)))
        assertNull(filter.update(sample(GeoPoint(Double.NaN, 0.0))))
        assertNull(filter.latest)
    }

    @Test
    fun `una precision desconocida se trata de forma conservadora`() {
        val filter = KalmanLocationFilter()

        val update = filter.update(sample(anchor, accuracyMeters = Double.NaN))

        assertNotNull(update)
        assertEquals(30.0, update!!.sourceAccuracyMeters, 0.001)
        assertTrue(update.accuracyMeters >= 3.0)
    }

    @Test
    fun `descarta lecturas fuera de orden`() {
        val filter = KalmanLocationFilter()
        filter.update(sample(anchor, elapsedNanos = 10 * ONE_SECOND_NANOS))

        assertNull(filter.update(sample(anchor, elapsedNanos = 9 * ONE_SECOND_NANOS)))
        assertNull(filter.update(sample(anchor, elapsedNanos = 10 * ONE_SECOND_NANOS)))
    }

    @Test
    fun `se reancla tras perder la senal mas alla de la ventana de coasting`() {
        val filter = KalmanLocationFilter()
        filter.update(sample(anchor, elapsedNanos = 0L))

        val reacquired = filter.update(
            sample(
                position = offsetMeters(anchor, 5_000.0, 0.0),
                elapsedNanos = 60 * ONE_SECOND_NANOS,
            ),
        )

        assertNotNull(reacquired)
        assertEquals(false, reacquired!!.isFiltered)
    }

    @Test
    fun `reancla cuando el racimo de saltos imposibles persiste`() {
        val filter = KalmanLocationFilter()
        filter.update(sample(anchor, elapsedNanos = 0L))

        val farAway = offsetMeters(anchor, 3_000.0, 0.0)
        var accepted: LocationUpdate? = null
        for (index in 1..3) {
            accepted = filter.update(
                sample(farAway, elapsedNanos = index * ONE_SECOND_NANOS),
            )
        }

        assertNotNull("Tras 3 rechazos el filtro debe reanclarse al nuevo racimo", accepted)
        assertEquals(false, accepted!!.isFiltered)
    }

    @Test
    fun `reiniciar limpia el estado`() {
        val filter = KalmanLocationFilter()
        filter.update(sample(anchor, elapsedNanos = 0L))
        assertNotNull(filter.latest)

        filter.reset()

        assertNull(filter.latest)
        val afterReset = filter.update(sample(anchor, elapsedNanos = 0L))
        assertNotNull(afterReset)
        assertEquals(false, afterReset!!.isFiltered)
    }

    private fun sample(
        position: GeoPoint,
        accuracyMeters: Double = 6.0,
        speedMps: Double = 0.0,
        elapsedNanos: Long = 0L,
    ): LocationSample = LocationSample(
        position = position,
        accuracyMeters = accuracyMeters,
        speedMps = speedMps,
        bearingDegrees = 0.0,
        altitudeMeters = 2_240.0,
        provider = "gps",
        isMock = false,
        elapsedRealtimeNanos = elapsedNanos,
        wallClockEpochMs = 1_700_000_000_000L + elapsedNanos / 1_000_000L,
    )

    private fun offsetMeters(origin: GeoPoint, north: Double, east: Double): GeoPoint {
        val metersPerLat = METERS_PER_DEGREE_LATITUDE
        val metersPerLng = METERS_PER_DEGREE_LATITUDE * cos(Math.toRadians(origin.latitude))
        return GeoPoint(
            latitude = origin.latitude + north / metersPerLat,
            longitude = origin.longitude + east / metersPerLng,
        )
    }

    private companion object {
        const val SAMPLE_COUNT = 40
        const val WARMUP_SAMPLES = 8
        const val NOISE_METERS = 10.0
        const val MOVING_SPEED_MPS = 11.0
        const val ONE_SECOND_NANOS = 1_000_000_000L
        const val METERS_PER_DEGREE_LATITUDE = 111_320.0
    }
}
