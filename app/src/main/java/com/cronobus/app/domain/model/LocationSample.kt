package com.cronobus.app.domain.model

/**
 * Lectura cruda de ubicación, ya desacoplada de `android.location.Location` para que el motor
 * de dominio sea 100% Kotlin puro y testeable en JVM (JUnit).
 *
 * @param elapsedRealtimeNanos Reloj monotónico del dispositivo (`SystemClock.elapsedRealtimeNanos`).
 *   Es la única base de tiempo confiable para detectar manipulación de hora y calcular `dt`.
 * @param wallClockEpochMs Marca de tiempo de pared reportada por el proveedor (solo auditoría/UI).
 */
data class LocationSample(
    val position: GeoPoint,
    val accuracyMeters: Double,
    val speedMps: Double,
    val bearingDegrees: Double,
    val altitudeMeters: Double?,
    val provider: String?,
    val isMock: Boolean,
    val elapsedRealtimeNanos: Long,
    val wallClockEpochMs: Long,
)
