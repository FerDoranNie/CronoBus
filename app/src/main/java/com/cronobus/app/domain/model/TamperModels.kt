package com.cronobus.app.domain.model

/**
 * Tipos de anomalía anti-sabotaje. `MOCK_LOCATION` y `CLOCK_MANIPULATION` son bloqueantes:
 * el servicio de rastreo debe detenerse y registrar la alerta para auditoría.
 */
enum class TamperType {
    /** Coordenadas inyectadas por una app de simulación (`Location.isMock`). */
    MOCK_LOCATION,

    /** La hora de pared se movió respecto al reloj monotónico, o llegó un fix del futuro. */
    CLOCK_MANIPULATION,

    /** Fix demasiado viejo para confiar en él (buffer del proveedor). */
    STALE_FIX,

    /** El proveedor de ubicación dejó de entregar datos (GPS apagado, modo avión, etc.). */
    GPS_PROVIDER_DISABLED,

    /** Apagado del GPS cerca de una geocerca con batería suficiente: posible evasión de control. */
    NEAR_STATION_SHUTDOWN,
}

/**
 * Alerta anti-sabotaje lista para persistirse (Room) o enviarse al Panel de Despacho.
 *
 * @param distanceToNearestZoneMeters Distancia al borde de la geocerca más cercana, si se conoce.
 * @param detectedAtMonotonicNanos Reloj monotónico; inmune al cambio manual de hora.
 * @param detectedAtEpochMs Hora de pared solo como referencia de auditoría.
 */
data class TamperEvent(
    val type: TamperType,
    val description: String,
    val position: GeoPoint?,
    val distanceToNearestZoneMeters: Double?,
    val speedMps: Double?,
    val batteryLevelPct: Int?,
    val isCharging: Boolean?,
    val detectedAtMonotonicNanos: Long,
    val detectedAtEpochMs: Long,
)

/**
 * Resultado de inspeccionar un fix o el estado del proveedor.
 *
 * @param acceptFix El fix puede entrar al filtro de Kalman y a las geocercas.
 * @param stopTracking Se debe interrumpir el turno y escalar la alerta (mock o reloj manipulado).
 */
data class TamperReport(
    val events: List<TamperEvent>,
    val acceptFix: Boolean,
    val stopTracking: Boolean,
) {
    val isTampered: Boolean
        get() = events.isNotEmpty()

    companion object {
        val CLEAR = TamperReport(events = emptyList(), acceptFix = true, stopTracking = false)
    }
}
