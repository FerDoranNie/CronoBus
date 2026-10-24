package com.cronobus.app.domain.model

/**
 * Lectura filtrada y lista para consumo (UI, geocercas, Room, Store & Forward).
 *
 * [accuracyMeters] es la precisión *estimada* por el filtro (1σ), mientras que
 * [sourceAccuracyMeters] conserva la precisión reportada por el proveedor GPS, ya saneada
 * (NaN o ≤ 0 → 30 m; piso de 3 m para evitar sobreconfianza).
 *
 * @param isFiltered `false` cuando el filtro se re-ancló (primera lectura, pérdida de señal
 *   prolongada o racimo de outliers persistente). La lectura sigue siendo válida.
 */
data class LocationUpdate(
    val position: GeoPoint,
    val speedMps: Double,
    val bearingDegrees: Double,
    val accuracyMeters: Double,
    val sourceAccuracyMeters: Double,
    val altitudeMeters: Double?,
    val provider: String?,
    val isMock: Boolean,
    val isFiltered: Boolean,
    val elapsedRealtimeNanos: Long,
    val wallClockEpochMs: Long,
) {
    val speedKmh: Double
        get() = speedMps * 3.6
}
