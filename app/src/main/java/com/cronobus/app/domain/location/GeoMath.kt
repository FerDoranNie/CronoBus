package com.cronobus.app.domain.location

import com.cronobus.app.domain.model.GeoPoint
import com.cronobus.app.domain.model.GeofenceGeometry
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Utilidades geodésicas compartidas por el filtro de Kalman y el motor de geocercas.
 *
 * Distancias: Haversine sobre esfera de radio medio WGS-84 (error < 0.5 % en escalas urbanas).
 * Conversión grados↔metros: aproximación polinómica WGS-84 (estable en todo México).
 * Distancia a segmentos: proyección local equirectangular (válida para tramos < 5 km).
 */
object GeoMath {

    const val EARTH_RADIUS_METERS = 6_371_008.8

    /** Metros por grado de latitud en la latitud dada. */
    fun metersPerDegreeLatitude(latitudeDeg: Double): Double {
        val phi = Math.toRadians(latitudeDeg)
        return 111_132.92 -
            559.82 * cos(2.0 * phi) +
            1.175 * cos(4.0 * phi) -
            0.0023 * cos(6.0 * phi)
    }

    /** Metros por grado de longitud en la latitud dada (mínimo 1 µm para evitar división por cero). */
    fun metersPerDegreeLongitude(latitudeDeg: Double): Double {
        val phi = Math.toRadians(latitudeDeg)
        val meters = 111_412.84 * cos(phi) - 93.5 * cos(3.0 * phi) + 0.118 * cos(5.0 * phi)
        return meters.coerceAtLeast(1e-6)
    }

    /** Distancia Haversine en metros entre dos coordenadas. */
    fun haversineMeters(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLat = lat2 - lat1
        val deltaLng = Math.toRadians(to.longitude - from.longitude)
        val sinLat = sin(deltaLat / 2.0)
        val sinLng = sin(deltaLng / 2.0)
        val h = sinLat * sinLat + cos(lat1) * cos(lat2) * sinLng * sinLng
        return 2.0 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(h)))
    }

    /** Rumbo inicial en grados [0, 360) desde [from] hacia [to]. */
    fun initialBearingDegrees(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLng = Math.toRadians(to.longitude - from.longitude)
        val y = sin(deltaLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLng)
        return normalizeDegrees(Math.toDegrees(atan2(y, x)))
    }

    /** Normaliza un ángulo a [0, 360). */
    fun normalizeDegrees(degrees: Double): Double {
        val normalized = degrees % 360.0
        return if (normalized < 0.0) normalized + 360.0 else normalized
    }

    /**
     * Ray-Casting: ¿está el punto dentro del polígono?
     *
     * Se trabaja en el plano (longitud = x, latitud = y), aproximación estándar válida para
     * geocercas urbanas. Los puntos exactamente sobre un borde quedan indefinidos; la histéresis
     * temporal del [GeofenceEngine] absorbe ese caso.
     */
    fun contains(point: GeoPoint, vertices: List<GeoPoint>): Boolean {
        if (vertices.size < GeofenceGeometry.Polygon.MIN_VERTICES) return false
        var inside = false
        var previous = vertices.lastIndex
        for (current in vertices.indices) {
            val currentVertex = vertices[current]
            val previousVertex = vertices[previous]
            val y = point.latitude
            val x = point.longitude
            val crossesLatitude = (currentVertex.latitude > y) != (previousVertex.latitude > y)
            if (crossesLatitude) {
                val intersectionX = (previousVertex.longitude - currentVertex.longitude) *
                    (y - currentVertex.latitude) /
                    (previousVertex.latitude - currentVertex.latitude) +
                    currentVertex.longitude
                if (x < intersectionX) inside = !inside
            }
            previous = current
        }
        return inside
    }

    /** Distancia mínima en metros del punto al borde del polígono. */
    fun distanceToPolygonBoundaryMeters(point: GeoPoint, vertices: List<GeoPoint>): Double {
        if (vertices.isEmpty()) return Double.NaN
        if (vertices.size == 1) return haversineMeters(point, vertices.first())
        var nearest = Double.MAX_VALUE
        for (index in vertices.indices) {
            val start = vertices[index]
            val end = vertices[(index + 1) % vertices.size]
            nearest = min(nearest, distanceToSegmentMeters(point, start, end))
        }
        return nearest
    }

    /**
     * Distancia punto↔segmento geodésico, proyectando localmente en metros alrededor del punto.
     * Error despreciable para tramos urbanos.
     */
    fun distanceToSegmentMeters(point: GeoPoint, start: GeoPoint, end: GeoPoint): Double {
        val metersPerLat = metersPerDegreeLatitude(point.latitude)
        val metersPerLng = metersPerDegreeLongitude(point.latitude)
        val ax = (start.longitude - point.longitude) * metersPerLng
        val ay = (start.latitude - point.latitude) * metersPerLat
        val bx = (end.longitude - point.longitude) * metersPerLng
        val by = (end.latitude - point.latitude) * metersPerLat
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= 1e-9) return sqrt(ax * ax + ay * ay)
        val t = (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
        val closestX = ax + t * dx
        val closestY = ay + t * dy
        return sqrt(closestX * closestX + closestY * closestY)
    }

    /**
     * Distancia con signo al borde de la geometría: negativa dentro, positiva fuera.
     * Permite alimentar la banda de histéresis de salida.
     */
    fun signedDistanceToBoundaryMeters(point: GeoPoint, geometry: GeofenceGeometry): Double =
        when (geometry) {
            is GeofenceGeometry.Circle -> haversineMeters(point, geometry.center) - geometry.radiusMeters
            is GeofenceGeometry.Polygon -> {
                val distance = distanceToPolygonBoundaryMeters(point, geometry.vertices)
                if (contains(point, geometry.vertices)) -distance else distance
            }
        }

    /** Distancia de proximidad a la geocerca: 0 si el punto ya está dentro. */
    fun distanceToZoneMeters(point: GeoPoint, geometry: GeofenceGeometry): Double =
        signedDistanceToBoundaryMeters(point, geometry).coerceAtLeast(0.0)
}
