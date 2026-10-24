package com.cronobus.app.domain.location

import com.cronobus.app.domain.model.GeoPoint
import com.cronobus.app.domain.model.GeofenceEvent
import com.cronobus.app.domain.model.GeofenceGeometry
import com.cronobus.app.domain.model.GeofenceTransition
import com.cronobus.app.domain.model.GeofenceZone
import com.cronobus.app.domain.model.GeofenceZoneStatus

/**
 * Motor de geocercas circulares (Haversine) y poligonales (Ray-Casting) con histéresis temporal.
 *
 * **Regla de confirmación:** una transición ENTER/EXIT solo se emite cuando la condición
 * geométrica se mantiene de forma continua durante `zone.dwellMillis` (5 s por defecto). Esto
 * elimina los rebotes por ruido GPS en el borde y las lecturas dobles del checador digital.
 * El evento conserva `occurredAtMonotonicNanos` (inicio de la condición) para no perder precisión
 * en el cálculo de headway.
 *
 * **Banda de salida:** al estar dentro, el vehículo debe alejarse `exitHysteresisMeters` del borde
 * para que la salida empiece a contar. Entrar siempre exige cruzar el borde geométrico real.
 *
 * **Concurrencia:** todos los métodos públicos están sincronizados, por lo que puede invocarse
 * desde el executor del servicio y leerse desde la UI sin condiciones de carrera.
 */
class GeofenceEngine(
    zones: List<GeofenceZone> = emptyList(),
    private val nowNanos: () -> Long = { System.nanoTime() },
) {

    private val states = LinkedHashMap<String, ZoneState>()

    init {
        zones.forEach(::register)
    }

    /** Agrega la geocerca o actualiza su definición conservando el estado ya acumulado. */
    @Synchronized
    fun register(zone: GeofenceZone) {
        val existing = states[zone.id]
        if (existing == null) {
            states[zone.id] = ZoneState(zone)
        } else {
            existing.zone = zone
        }
    }

    /** Reemplaza el conjunto completo (por ejemplo al cargar las 4 estaciones de la ruta). */
    @Synchronized
    fun replaceZones(zones: List<GeofenceZone>) {
        val incomingIds = zones.mapTo(HashSet()) { it.id }
        states.keys.retainAll(incomingIds)
        zones.forEach(::register)
    }

    /**
     * Evalúa una posición y devuelve únicamente las transiciones confirmadas en esta lectura.
     *
     * @param monotonicNanos Reloj monotónico en nanosegundos. En Android usar
     *   `SystemClock.elapsedRealtimeNanos()`; en pruebas, un reloj falso.
     */
    @Synchronized
    fun evaluate(position: GeoPoint, monotonicNanos: Long = nowNanos()): List<GeofenceEvent> {
        if (!position.isValid || states.isEmpty()) return emptyList()

        val events = ArrayList<GeofenceEvent>(states.size)
        for (state in states.values) {
            val desired = desiredTransition(state, position)
            if (desired == null) {
                state.pendingTransition = null
                state.pendingPosition = null
                continue
            }

            if (state.pendingTransition != desired) {
                state.pendingTransition = desired
                state.pendingSinceNanos = monotonicNanos
                state.pendingPosition = position
            }

            val requiredNanos = state.zone.dwellMillis * NANOS_PER_MILLI
            val elapsedNanos = monotonicNanos - state.pendingSinceNanos
            if (elapsedNanos < 0L) {
                // Reloj no monotónico: reiniciar la ventana en lugar de confirmar un cruce falso.
                state.pendingSinceNanos = monotonicNanos
                continue
            }
            if (elapsedNanos < requiredNanos) continue

            state.isInside = desired == GeofenceTransition.ENTER
            state.pendingTransition = null
            events += GeofenceEvent(
                zoneId = state.zone.id,
                zoneName = state.zone.name,
                transition = desired,
                position = state.pendingPosition ?: position,
                signedDistanceToBoundaryMeters = GeoMath.signedDistanceToBoundaryMeters(
                    position,
                    state.zone.geometry,
                ),
                occurredAtMonotonicNanos = state.pendingSinceNanos,
                confirmedAtMonotonicNanos = monotonicNanos,
            )
            state.pendingPosition = null
        }
        return events
    }

    /** Estado actual de cada geocerca (para el checador digital). */
    @Synchronized
    fun snapshot(): List<GeofenceZoneStatus> = states.values.map { state ->
        GeofenceZoneStatus(
            zoneId = state.zone.id,
            zoneName = state.zone.name,
            isInside = state.isInside,
            pendingTransition = state.pendingTransition,
        )
    }

    @Synchronized
    fun isInside(zoneId: String): Boolean = states[zoneId]?.isInside ?: false

    /** Distancia de proximidad (metros) al borde de la geocerca más cercana; 0 si está dentro. */
    @Synchronized
    fun distanceToNearestZoneMeters(position: GeoPoint): Double? {
        if (!position.isValid || states.isEmpty()) return null
        return states.values.minOf { state ->
            GeoMath.distanceToZoneMeters(position, state.zone.geometry)
        }
    }

    @Synchronized
    fun zones(): List<GeofenceZone> = states.values.map { it.zone }

    /** Limpia estados y transiciones pendientes (fin de turno). Las definiciones se conservan. */
    @Synchronized
    fun reset() {
        states.values.forEach { state -> state.reset() }
    }

    private fun desiredTransition(state: ZoneState, position: GeoPoint): GeofenceTransition? {
        val currentlyInside = state.isInside
        val candidateInside = isGeometricallyInside(state.zone, position, currentlyInside)
        return when {
            candidateInside == currentlyInside -> null
            candidateInside -> GeofenceTransition.ENTER
            else -> GeofenceTransition.EXIT
        }
    }

    private fun isGeometricallyInside(
        zone: GeofenceZone,
        position: GeoPoint,
        currentlyInside: Boolean,
    ): Boolean = when (val geometry = zone.geometry) {
        is GeofenceGeometry.Circle -> {
            val distance = GeoMath.haversineMeters(position, geometry.center)
            val threshold = if (currentlyInside) {
                geometry.radiusMeters + zone.exitHysteresisMeters
            } else {
                geometry.radiusMeters
            }
            distance <= threshold
        }

        is GeofenceGeometry.Polygon -> {
            when {
                GeoMath.contains(position, geometry.vertices) -> true
                currentlyInside ->
                    GeoMath.distanceToPolygonBoundaryMeters(position, geometry.vertices) <=
                        zone.exitHysteresisMeters
                else -> false
            }
        }
    }

    private class ZoneState(var zone: GeofenceZone) {
        var isInside: Boolean = false
        var pendingTransition: GeofenceTransition? = null
        var pendingSinceNanos: Long = 0L
        var pendingPosition: GeoPoint? = null

        fun reset() {
            isInside = false
            pendingTransition = null
            pendingSinceNanos = 0L
            pendingPosition = null
        }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
