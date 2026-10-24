package com.cronobus.app.domain.model

/**
 * Coordenada geográfica WGS-84 inmutable.
 *
 * Se valida con [isValid] en lugar de lanzar excepciones en el constructor porque las lecturas
 * crudas del GPS pueden llegar corruptas (NaN / fuera de rango) y el motor debe descartarlas
 * sin tumbar el servicio en primer plano.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    val isValid: Boolean
        get() = latitude.isFinite() &&
            longitude.isFinite() &&
            latitude in MIN_LATITUDE..MAX_LATITUDE &&
            longitude in MIN_LONGITUDE..MAX_LONGITUDE

    companion object {
        const val MIN_LATITUDE = -90.0
        const val MAX_LATITUDE = 90.0
        const val MIN_LONGITUDE = -180.0
        const val MAX_LONGITUDE = 180.0
    }
}
