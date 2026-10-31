package com.cronobus.app.data.local

/**
 * Nivel agregado de ocupación en la unidad de transporte.
 *
 * Cumplimiento LGPDPPSO: El conteo es estrictamente cualitativo y agregado (BAJA, MEDIA, LLENO).
 * Queda estrictamente prohibido capturar o almacenar datos biométricos, reconocimiento facial,
 * o identificadores personales de pasajeros.
 */
enum class OccupancyLevel {
    BAJA,
    MEDIA,
    LLENO,
}

