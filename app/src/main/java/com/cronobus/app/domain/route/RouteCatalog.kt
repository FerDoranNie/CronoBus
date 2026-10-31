package com.cronobus.app.domain.route

import com.cronobus.app.domain.model.GeoPoint
import com.cronobus.app.domain.model.GeofenceGeometry
import com.cronobus.app.domain.model.GeofenceZone
import java.util.Locale

/**
 * Catálogo operativo de rutas y paradas del MVP.
 *
 * En producción esta información vendrá del backend (contrato de ruta versionado). Mientras tanto,
 * la Ruta 42 replica exactamente las geocercas y coordenadas del prototipo
 * [`cronobus.html`], de modo que el checador digital, el headway y el ETA del pasajero usen los
 * mismos puntos que la UI.
 */
object RouteCatalog {

    const val ROUTE_42_ID = "Ruta 42 - Ramal Centro"

    /**
     * Parada/checador de la ruta.
     *
     * @param scheduledOffsetMinutes Minuto programado desde la salida de base. Se usa como
     *   referencia de frecuencia y para ordenar el checador.
     */
    data class Stop(
        val id: String,
        val name: String,
        val position: GeoPoint,
        val scheduledOffsetMinutes: Int,
    )

    private const val STOP_RADIUS_METERS = 50.0
    private const val STOP_DWELL_MILLIS = 5_000L
    private const val STOP_EXIT_HYSTERESIS_METERS = 10.0

    /** Puntos del prototipo (Ciudad de México, corredor Centro–Diana). */
    val route42Stops: List<Stop> = listOf(
        Stop(
            id = "sevilla",
            name = "Base A - Sevilla",
            position = GeoPoint(19.4270, -99.1676),
            scheduledOffsetMinutes = 0,
        ),
        Stop(
            id = "diana",
            name = "Estación Diana",
            position = GeoPoint(19.4255, -99.1720),
            scheduledOffsetMinutes = 4,
        ),
        Stop(
            id = "hospital",
            name = "Hospital Gral.",
            position = GeoPoint(19.4239, -99.1772),
            scheduledOffsetMinutes = 8,
        ),
        Stop(
            id = "terminal",
            name = "Terminal Centro",
            position = GeoPoint(19.4215, -99.1824),
            scheduledOffsetMinutes = 12,
        ),
    )

    /**
     * Resuelve las paradas de una ruta. Tolera variantes del nombre usadas por la SPA
     * ("Ruta 42 - Centro/Diana", "Ruta 42 - Ramal Centro", "ruta 42"...).
     */
    fun stopsFor(routeId: String): List<Stop> {
        val normalized = routeId.trim().lowercase(Locale.ROOT)
        return when {
            normalized.contains("42") -> route42Stops
            else -> emptyList()
        }
    }

    /** Busca una parada por id o por nombre visible (los `<select>` de la SPA envían el id). */
    fun findStop(routeId: String, stopIdOrName: String): Stop? {
        val needle = stopIdOrName.trim().lowercase(Locale.ROOT)
        return stopsFor(routeId).firstOrNull {
            it.id == needle || it.name.lowercase(Locale.ROOT) == needle
        }
    }

    /**
     * Geocercas de la ruta listas para [com.cronobus.app.service.LocationTrackingService].
     * La histéresis de 5 s y la banda de salida de 10 m son las mismas del motor de geocercas.
     */
    fun zonesFor(routeId: String): List<GeofenceZone> =
        stopsFor(routeId).map { stop ->
            GeofenceZone(
                id = stop.id,
                name = stop.name,
                geometry = GeofenceGeometry.Circle(
                    center = stop.position,
                    radiusMeters = STOP_RADIUS_METERS,
                ),
                dwellMillis = STOP_DWELL_MILLIS,
                exitHysteresisMeters = STOP_EXIT_HYSTERESIS_METERS,
                scheduledOffsetMinutes = stop.scheduledOffsetMinutes,
            )
        }

    /** Parada por defecto del tablero público si la SPA no envía una selección. */
    val defaultPassengerStop: Stop
        get() = route42Stops.first { it.id == "diana" }
}
