package com.cronobus.app.domain.model

/** Geometría soportada por el motor de geocercas. */
sealed interface GeofenceGeometry {
    /** Geocerca radial (base, estación o terminal). */
    data class Circle(
        val center: GeoPoint,
        val radiusMeters: Double,
    ) : GeofenceGeometry {
        init {
            require(radiusMeters.isFinite() && radiusMeters > 0.0) {
                "radiusMeters debe ser finito y mayor que 0"
            }
        }
    }

    /** Geocerca poligonal (tramo de ruta, corredor o perímetro irregular). */
    data class Polygon(
        val vertices: List<GeoPoint>,
    ) : GeofenceGeometry {
        init {
            require(vertices.size >= MIN_VERTICES) { "Un polígono requiere al menos $MIN_VERTICES vértices" }
            require(vertices.all { it.isValid }) { "Todos los vértices deben ser coordenadas válidas" }
        }

        companion object {
            const val MIN_VERTICES = 3
        }
    }
}

/**
 * Definición inmutable de una geocerca operativa.
 *
 * @param dwellMillis Permanencia mínima dentro/fuera para confirmar la transición. Evita rebotes
 *   por ruido GPS en el borde (por defecto 5 s, según especificación de producto).
 * @param exitHysteresisMeters Banda muerta de salida: estando dentro, hay que alejarse este margen
 *   del borde para considerarse fuera.
 */
data class GeofenceZone(
    val id: String,
    val name: String,
    val geometry: GeofenceGeometry,
    val dwellMillis: Long = DEFAULT_DWELL_MILLIS,
    val exitHysteresisMeters: Double = DEFAULT_EXIT_HYSTERESIS_METERS,
    val scheduledOffsetMinutes: Int? = null,
) {
    init {
        require(id.isNotBlank()) { "id no puede estar vacío" }
        require(name.isNotBlank()) { "name no puede estar vacío" }
        require(dwellMillis >= 0L) { "dwellMillis no puede ser negativo" }
        require(exitHysteresisMeters.isFinite() && exitHysteresisMeters >= 0.0) {
            "exitHysteresisMeters debe ser finito y no negativo"
        }
    }

    companion object {
        const val DEFAULT_DWELL_MILLIS = 5_000L
        const val DEFAULT_EXIT_HYSTERESIS_METERS = 10.0
    }
}

enum class GeofenceTransition { ENTER, EXIT }

/**
 * Transición confirmada de geocerca.
 *
 * [occurredAtMonotonicNanos] marca el instante en que la condición empezó a cumplirse (precisión
 * real del cruce) y [confirmedAtMonotonicNanos] el instante en que se superó la ventana de
 * histéresis. Así el cálculo de headway no arrastra el retraso de confirmación.
 */
data class GeofenceEvent(
    val zoneId: String,
    val zoneName: String,
    val transition: GeofenceTransition,
    val position: GeoPoint,
    val signedDistanceToBoundaryMeters: Double,
    val occurredAtMonotonicNanos: Long,
    val confirmedAtMonotonicNanos: Long,
) {
    val dwellMillis: Long
        get() = (confirmedAtMonotonicNanos - occurredAtMonotonicNanos) / NANOS_PER_MILLI

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

/** Instantánea del estado interno del motor, útil para el checador digital del conductor. */
data class GeofenceZoneStatus(
    val zoneId: String,
    val zoneName: String,
    val isInside: Boolean,
    val pendingTransition: GeofenceTransition?,
)
