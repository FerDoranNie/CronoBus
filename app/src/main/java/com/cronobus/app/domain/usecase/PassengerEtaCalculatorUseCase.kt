package com.cronobus.app.domain.usecase

import com.cronobus.app.domain.location.GeoMath
import com.cronobus.app.domain.model.GeoPoint
import com.cronobus.app.domain.model.PassengerEtaQuote
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min

/**
 * Estima el arribo de la unidad a una parada para el tablero público.
 *
 * Considera:
 * - Distancia Haversine corregida por [routeDetourFactor] (la ruta urbana no es una línea recta).
 * - Velocidad media filtrada por Kalman. Si la unidad está detenida (0 m/s) se usa una velocidad
 *   mínima operativa para no devolver un ETA infinito; si el dato es inválido, idem.
 * - Retrasos por incidencias activas: cada reporte suma [delayPerIncidentMinutes] minutos, con un
 *   tope de [maxIncidentsConsidered] para que una zona con decenas de reportes no infle el ETA.
 *
 * El resultado se redondea hacia arriba: es preferible prometer 6 minutos y llegar en 5 que al
 * revés. El modelo incluye `isStaleCache` para que la UI distinga una cotización offline.
 */
class PassengerEtaCalculatorUseCase(
    private val routeDetourFactor: Double = 1.25,
    private val minEffectiveSpeedMps: Double = 2.0,
    private val maxEffectiveSpeedMps: Double = 22.0,
    private val delayPerIncidentMinutes: Int = 3,
    private val maxIncidentsConsidered: Int = 5,
) {

    init {
        require(routeDetourFactor >= 1.0 && routeDetourFactor.isFinite()) {
            "routeDetourFactor debe ser finito y >= 1.0"
        }
        require(minEffectiveSpeedMps > 0.0) { "minEffectiveSpeedMps debe ser positivo" }
        require(maxEffectiveSpeedMps >= minEffectiveSpeedMps) {
            "maxEffectiveSpeedMps debe ser mayor o igual que minEffectiveSpeedMps"
        }
        require(delayPerIncidentMinutes >= 0) { "delayPerIncidentMinutes no puede ser negativo" }
        require(maxIncidentsConsidered >= 0) { "maxIncidentsConsidered no puede ser negativo" }
    }

    fun calculate(
        routeId: String,
        stopId: String,
        stopName: String,
        unitId: String,
        unitPosition: GeoPoint,
        stopPosition: GeoPoint,
        averageSpeedMps: Double,
        activeIncidentsCount: Int,
        occupancyLevel: String,
        calculatedAtEpochMs: Long,
        isStaleCache: Boolean = false,
    ): PassengerEtaQuote {
        // Auditoría Paso 3 (LGPDPPSO): solo se publican los niveles agregados permitidos.
        val occupancy = occupancyLevel.trim().uppercase(Locale.ROOT)
            .takeIf { it in ALLOWED_OCCUPANCY } ?: UNKNOWN_OCCUPANCY
        if (routeId.isBlank() || stopId.isBlank() || !unitPosition.isValid || !stopPosition.isValid) {
            return PassengerEtaQuote(
                routeId = routeId,
                stopId = stopId,
                stopName = stopName,
                nearestUnitId = unitId,
                distanceMeters = 0.0,
                filteredSpeedMps = 0.0,
                activeIncidentsCount = activeIncidentsCount.coerceAtLeast(0),
                etaMinutes = 0,
                occupancyLevel = occupancy,
                isStaleCache = true,
                calculatedAtEpochMs = calculatedAtEpochMs,
            )
        }

        val routeDistanceMeters = GeoMath.haversineMeters(unitPosition, stopPosition) *
            routeDetourFactor

        val effectiveSpeed = if (averageSpeedMps.isFinite() && averageSpeedMps > 0.0) {
            averageSpeedMps.coerceIn(minEffectiveSpeedMps, maxEffectiveSpeedMps)
        } else {
            minEffectiveSpeedMps
        }

        val travelSeconds = routeDistanceMeters / effectiveSpeed
        val incidents = activeIncidentsCount.coerceIn(0, maxIncidentsConsidered)
        val delaySeconds = incidents * delayPerIncidentMinutes * SECONDS_PER_MINUTE
        val totalSeconds = travelSeconds + delaySeconds
        val etaMinutes = ceil(totalSeconds / SECONDS_PER_MINUTE).toInt().coerceAtLeast(1)

        return PassengerEtaQuote(
            routeId = routeId,
            stopId = stopId,
            stopName = stopName,
            nearestUnitId = unitId,
            distanceMeters = routeDistanceMeters,
            filteredSpeedMps = effectiveSpeed,
            activeIncidentsCount = activeIncidentsCount.coerceAtLeast(0),
            etaMinutes = etaMinutes,
            occupancyLevel = occupancy,
            isStaleCache = isStaleCache,
            calculatedAtEpochMs = calculatedAtEpochMs,
        )
    }

    /** Penalización total en minutos aplicada por incidencias, útil para explicar el ETA en UI. */
    fun incidentDelayMinutes(activeIncidentsCount: Int): Int =
        min(activeIncidentsCount.coerceAtLeast(0), maxIncidentsConsidered) * delayPerIncidentMinutes

    private companion object {
        const val SECONDS_PER_MINUTE = 60.0
        const val UNKNOWN_OCCUPANCY = "DESCONOCIDO"
        val ALLOWED_OCCUPANCY = setOf("BAJA", "MEDIA", "LLENO")
    }
}
