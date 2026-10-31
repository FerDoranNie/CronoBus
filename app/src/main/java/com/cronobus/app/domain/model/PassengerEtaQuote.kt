package com.cronobus.app.domain.model

/**
 * Estimación calculada de tiempo de llegada (ETA) y métricas de servicio para pasajeros en parada.
 *
 * @property routeId Identificador de la ruta o ramal consultado.
 * @property stopId Identificador único de la parada o checador seleccionado.
 * @property stopName Nombre público de la parada.
 * @property nearestUnitId Identificador de la unidad más próxima en ruta hacia la parada.
 * @property distanceMeters Distancia geográfica aproximada por ruta hacia la parada en metros.
 * @property filteredSpeedMps Velocidad media estimada de la unidad (derivada de Kalman) en m/s.
 * @property activeIncidentsCount Cantidad de reportes de tráfico o bloqueo activos en el tramo.
 * @property etaMinutes Tiempo estimado de arribo redondeado en minutos.
 * @property occupancyLevel Nivel de ocupación reportado ("BAJA", "MEDIA", "LLENO").
 * @property isStaleCache Indica si la cotización proviene de una copia fuera de línea por falta de red.
 * @property calculatedAtEpochMs Marca temporal epoch en milisegundos en que se generó la cotización.
 */
data class PassengerEtaQuote(
    val routeId: String,
    val stopId: String,
    val stopName: String,
    val nearestUnitId: String,
    val distanceMeters: Double,
    val filteredSpeedMps: Double,
    val activeIncidentsCount: Int,
    val etaMinutes: Int,
    val occupancyLevel: String,
    val isStaleCache: Boolean = false,
    val calculatedAtEpochMs: Long,
)

