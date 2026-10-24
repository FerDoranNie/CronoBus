package com.cronobus.app.domain.location

import com.cronobus.app.domain.model.GeoPoint
import com.cronobus.app.domain.model.GeofenceGeometry
import com.cronobus.app.domain.model.GeofenceTransition
import com.cronobus.app.domain.model.GeofenceZone
import kotlin.math.cos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceEngineTest {

    private val station = GeoPoint(19.4326, -99.1332)
    private val baseNanos = 5_000_000_000L
    private val oneSecond = 1_000_000_000L

    @Test
    fun `confirma la entrada solo despues de la ventana de histeresis`() {
        val engine = GeofenceEngine(listOf(circularZone()))
        val inside = offsetMeters(station, 50.0, 0.0)

        assertTrue(engine.evaluate(inside, baseNanos).isEmpty())
        assertTrue(engine.evaluate(inside, baseNanos + 4 * oneSecond).isEmpty())

        val events = engine.evaluate(inside, baseNanos + 5 * oneSecond)

        assertEquals(1, events.size)
        val entry = events.first()
        assertEquals(GeofenceTransition.ENTER, entry.transition)
        assertEquals("st-diana", entry.zoneId)
        assertEquals(baseNanos, entry.occurredAtMonotonicNanos)
        assertEquals(baseNanos + 5 * oneSecond, entry.confirmedAtMonotonicNanos)
        assertTrue(engine.isInside("st-diana"))
    }

    @Test
    fun `no duplica la entrada en lecturas posteriores`() {
        val engine = GeofenceEngine(listOf(circularZone()))
        val inside = offsetMeters(station, 20.0, 0.0)
        engine.evaluate(inside, baseNanos)
        engine.evaluate(inside, baseNanos + 5 * oneSecond)

        assertTrue(engine.evaluate(inside, baseNanos + 6 * oneSecond).isEmpty())
        assertEquals(1, engine.snapshot().count { it.isInside })
    }

    @Test
    fun `la salida exige abandonar la banda de histeresis`() {
        val engine = GeofenceEngine(listOf(circularZone(exitHysteresisMeters = 10.0)))
        confirmEntry(engine, baseNanos)

        // 105 m: fuera del radio (100 m) pero dentro de la banda (110 m) -> sigue dentro.
        assertTrue(engine.evaluate(offsetMeters(station, 105.0, 0.0), baseNanos + 10 * oneSecond).isEmpty())
        assertTrue(engine.isInside("st-diana"))

        // 120 m: inicia la ventana de salida, aún no se confirma.
        val outside = offsetMeters(station, 120.0, 0.0)
        assertTrue(engine.evaluate(outside, baseNanos + 15 * oneSecond).isEmpty())

        val events = engine.evaluate(outside, baseNanos + 20 * oneSecond)
        assertEquals(1, events.size)
        assertEquals(GeofenceTransition.EXIT, events.first().transition)
        assertFalse(engine.isInside("st-diana"))
    }

    @Test
    fun `un rebote dentro de la ventana cancela la salida pendiente`() {
        val engine = GeofenceEngine(listOf(circularZone()))
        confirmEntry(engine, baseNanos)
        val inside = offsetMeters(station, 30.0, 0.0)

        // Primer aviso de salida y regreso inmediato: no debe emitirse EXIT ni ENTER doble.
        assertTrue(engine.evaluate(offsetMeters(station, 140.0, 0.0), baseNanos + 10 * oneSecond).isEmpty())
        assertTrue(engine.evaluate(inside, baseNanos + 11 * oneSecond).isEmpty())
        assertTrue(engine.evaluate(inside, baseNanos + 30 * oneSecond).isEmpty())
        assertTrue(engine.isInside("st-diana"))
    }

    @Test
    fun `evalua geocercas poligonales con ray casting`() {
        val square = listOf(
            GeoPoint(19.4320, -99.1340),
            GeoPoint(19.4332, -99.1340),
            GeoPoint(19.4332, -99.1324),
            GeoPoint(19.4320, -99.1324),
        )
        val polygonZone = GeofenceZone(
            id = "corredor-centro",
            name = "Corredor Centro",
            geometry = GeofenceGeometry.Polygon(square),
            dwellMillis = 0L,
        )
        val engine = GeofenceEngine(listOf(polygonZone))

        val entry = engine.evaluate(GeoPoint(19.4326, -99.1332), 0L)
        assertEquals(1, entry.size)
        assertEquals(GeofenceTransition.ENTER, entry.first().transition)

        val exit = engine.evaluate(GeoPoint(19.4300, -99.1332), oneSecond)
        assertEquals(1, exit.size)
        assertEquals(GeofenceTransition.EXIT, exit.first().transition)

        assertTrue(engine.evaluate(GeoPoint(19.4300, -99.1332), 2 * oneSecond).isEmpty())
    }

    @Test
    fun `reemplazar zonas conserva el estado de las existentes y elimina las ausentes`() {
        val engine = GeofenceEngine(listOf(circularZone()))
        confirmEntry(engine, baseNanos)

        engine.replaceZones(listOf(circularZone(name = "Estación Diana (renombrada)")))

        assertTrue(engine.isInside("st-diana"))
        assertEquals("Estación Diana (renombrada)", engine.snapshot().single().zoneName)

        engine.replaceZones(emptyList())
        assertEquals(0, engine.snapshot().size)
    }

    private fun confirmEntry(engine: GeofenceEngine, startNanos: Long) {
        val inside = offsetMeters(station, 40.0, 0.0)
        engine.evaluate(inside, startNanos)
        val events = engine.evaluate(inside, startNanos + 5 * oneSecond)
        assertEquals(GeofenceTransition.ENTER, events.single().transition)
    }

    private fun circularZone(
        exitHysteresisMeters: Double = 10.0,
        name: String = "Estación Diana",
    ): GeofenceZone = GeofenceZone(
        id = "st-diana",
        name = name,
        geometry = GeofenceGeometry.Circle(center = station, radiusMeters = 100.0),
        dwellMillis = 5_000L,
        exitHysteresisMeters = exitHysteresisMeters,
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
        const val METERS_PER_DEGREE_LATITUDE = 111_320.0
    }
}
