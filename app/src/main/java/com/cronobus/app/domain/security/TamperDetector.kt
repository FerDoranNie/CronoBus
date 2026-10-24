package com.cronobus.app.domain.security

import com.cronobus.app.domain.model.GeoPoint
import com.cronobus.app.domain.model.LocationSample
import com.cronobus.app.domain.model.TamperEvent
import com.cronobus.app.domain.model.TamperReport
import com.cronobus.app.domain.model.TamperType
import kotlin.math.abs

/**
 * Detector anti-sabotaje del rastreo.
 *
 * Audita tres fraudes típicos en operación de transporte:
 * 1. **Mock Location:** una app externa inyecta coordenadas (`isMock`). Bloquea el fix y detiene
 *    el rastreo (alerta escalable al Panel de Despacho).
 * 2. **Manipulación de reloj:** compara el avance de la hora de pared contra el reloj monotónico
 *    (`elapsedRealtimeNanos`), que no puede alterarse sin reiniciar el dispositivo. También detecta
 *    fixes "del futuro". Bloquea el rastreo.
 * 3. **Apagado sospechoso:** el proveedor de GPS deja de entregar datos cerca de una geocerca
 *    (<= 300 m) con batería suficiente (> 15 %), señal de evasión de control.
 *
 * Es Kotlin puro: los relojes y la batería se inyectan, lo que permite probarlo en JVM sin Android.
 *
 * @param monotonicNowNanos En Android: `{ SystemClock.elapsedRealtimeNanos() }`.
 * @param wallClockNowMs En Android: `{ System.currentTimeMillis() }` (solo auditoría).
 */
class TamperDetector(
    private val monotonicNowNanos: () -> Long,
    private val wallClockNowMs: () -> Long = System::currentTimeMillis,
    private val config: Config = Config(),
) {

    data class Config(
        /** Antigüedad máxima de un fix antes de considerarlo obsoleto. */
        val maxFixAgeMillis: Long = 30_000L,
        /** Margen de tolerancia para marcas de tiempo ligeramente adelantadas. */
        val maxFutureSkewMillis: Long = 2_000L,
        /** Desfase máximo tolerado entre hora de pared y reloj monotónico. */
        val maxClockDriftMillis: Long = 10_000L,
        /** Radio alrededor de una geocerca donde un apagado se considera sospechoso. */
        val suspiciousShutdownRadiusMeters: Double = 300.0,
        /** Batería mínima para que un apagado cerca de estación sea sospechoso. */
        val minBatteryToFlagShutdownPct: Int = 15,
        /** Ventana para no duplicar alertas del mismo tipo. */
        val duplicateSuppressionMillis: Long = 60_000L,
    )

    private data class FixAnchor(
        val position: GeoPoint,
        val monotonicNanos: Long,
        val wallClockEpochMs: Long,
    )

    private var lastAcceptedFix: FixAnchor? = null
    private var batteryLevelPct: Int? = null
    private var batteryCharging: Boolean? = null
    private val lastEmittedAtNanos = HashMap<TamperType, Long>()

    /** Actualiza el snapshot de batería (en Android se alimenta de `ACTION_BATTERY_CHANGED`). */
    @Synchronized
    fun updateBattery(levelPct: Int?, isCharging: Boolean?) {
        batteryLevelPct = levelPct?.takeIf { it in 0..100 }
        batteryCharging = isCharging
    }

    /**
     * Inspecciona un fix antes de pasarlo al filtro de Kalman.
     *
     * @return [TamperReport] con `acceptFix = false` si la lectura no debe usarse y
     *   `stopTracking = true` ante mock o manipulación de reloj.
     */
    @Synchronized
    fun inspectFix(sample: LocationSample): TamperReport {
        if (!sample.position.isValid) {
            return TamperReport(events = emptyList(), acceptFix = false, stopTracking = false)
        }

        val nowNanos = monotonicNowNanos()
        val rawEvents = ArrayList<TamperEvent>(2)

        if (sample.isMock) {
            rawEvents += buildEvent(
                type = TamperType.MOCK_LOCATION,
                description = "El proveedor reporta coordenadas simuladas (Mock Location).",
                position = sample.position,
                distanceToNearestZoneMeters = null,
                speedMps = sample.speedMps,
                nowNanos = nowNanos,
            )
        }

        if (sample.elapsedRealtimeNanos > 0L && nowNanos > 0L) {
            val ageMillis = (nowNanos - sample.elapsedRealtimeNanos) / NANOS_PER_MILLI
            when {
                ageMillis > config.maxFixAgeMillis -> rawEvents += buildEvent(
                    type = TamperType.STALE_FIX,
                    description = "Fix GPS con $ageMillis ms de antigüedad; descartado.",
                    position = sample.position,
                    distanceToNearestZoneMeters = null,
                    speedMps = sample.speedMps,
                    nowNanos = nowNanos,
                )

                ageMillis < -config.maxFutureSkewMillis -> rawEvents += buildEvent(
                    type = TamperType.CLOCK_MANIPULATION,
                    description = "Fix con marca de tiempo futura (${-ageMillis} ms): hora del dispositivo alterada.",
                    position = sample.position,
                    distanceToNearestZoneMeters = null,
                    speedMps = sample.speedMps,
                    nowNanos = nowNanos,
                )
            }
        }

        val previous = lastAcceptedFix
        if (previous != null && sample.elapsedRealtimeNanos > previous.monotonicNanos) {
            val wallDeltaMillis = sample.wallClockEpochMs - previous.wallClockEpochMs
            val monotonicDeltaMillis =
                (sample.elapsedRealtimeNanos - previous.monotonicNanos) / NANOS_PER_MILLI
            val driftMillis = wallDeltaMillis - monotonicDeltaMillis
            if (abs(driftMillis) > config.maxClockDriftMillis) {
                rawEvents += buildEvent(
                    type = TamperType.CLOCK_MANIPULATION,
                    description = "Desfase hora de pared vs. reloj monotónico de $driftMillis ms.",
                    position = sample.position,
                    distanceToNearestZoneMeters = null,
                    speedMps = sample.speedMps,
                    nowNanos = nowNanos,
                )
            }
        }

        val acceptFix = rawEvents.isEmpty()
        if (acceptFix) {
            lastAcceptedFix = FixAnchor(
                position = sample.position,
                monotonicNanos = sample.elapsedRealtimeNanos,
                wallClockEpochMs = sample.wallClockEpochMs,
            )
        }

        return TamperReport(
            events = filterDuplicates(rawEvents, nowNanos),
            acceptFix = acceptFix,
            stopTracking = rawEvents.any { it.type.isBlocking },
        )
    }

    /**
     * Inspecciona la pérdida del proveedor de ubicación (GPS apagado, modo avión o sensor caído).
     *
     * @param position Última posición conocida.
     * @param distanceToNearestZoneMeters Distancia al borde de la geocerca más cercana, si se conoce.
     */
    @Synchronized
    fun inspectProviderDisabled(
        position: GeoPoint?,
        distanceToNearestZoneMeters: Double?,
    ): TamperReport {
        val nowNanos = monotonicNowNanos()
        val lowBattery = batteryLevelPct?.let { it <= config.minBatteryToFlagShutdownPct } ?: false
        val nearZone = distanceToNearestZoneMeters != null &&
            distanceToNearestZoneMeters <= config.suspiciousShutdownRadiusMeters
        val suspiciousShutdown = nearZone && !lowBattery

        val type = if (suspiciousShutdown) {
            TamperType.NEAR_STATION_SHUTDOWN
        } else {
            TamperType.GPS_PROVIDER_DISABLED
        }
        val description = buildString {
            append("El proveedor de ubicación dejó de entregar datos.")
            distanceToNearestZoneMeters?.let {
                append(" Geocerca más cercana a ${it.toInt()} m.")
            }
            batteryLevelPct?.let { append(" Batería ${it}%.") }
            if (lowBattery) append(" Descarga de batería plausible.")
        }

        val event = buildEvent(
            type = type,
            description = description,
            position = position,
            distanceToNearestZoneMeters = distanceToNearestZoneMeters,
            speedMps = null,
            nowNanos = nowNanos,
        )
        val events = filterDuplicates(listOf(event), nowNanos)
        return TamperReport(events = events, acceptFix = true, stopTracking = false)
    }

    /** Limpia el historial (nuevo turno). La batería se conserva porque la alimenta el sistema. */
    @Synchronized
    fun reset() {
        lastAcceptedFix = null
        lastEmittedAtNanos.clear()
    }

    private fun buildEvent(
        type: TamperType,
        description: String,
        position: GeoPoint?,
        distanceToNearestZoneMeters: Double?,
        speedMps: Double?,
        nowNanos: Long,
    ): TamperEvent = TamperEvent(
        type = type,
        description = description,
        position = position,
        distanceToNearestZoneMeters = distanceToNearestZoneMeters,
        speedMps = speedMps,
        batteryLevelPct = batteryLevelPct,
        isCharging = batteryCharging,
        detectedAtMonotonicNanos = nowNanos,
        detectedAtEpochMs = wallClockNowMs(),
    )

    private fun filterDuplicates(events: List<TamperEvent>, nowNanos: Long): List<TamperEvent> {
        if (events.isEmpty()) return emptyList()
        val suppressionNanos = config.duplicateSuppressionMillis * NANOS_PER_MILLI
        return events.filter { event ->
            val lastEmitted = lastEmittedAtNanos[event.type]
            if (lastEmitted != null && nowNanos - lastEmitted < suppressionNanos) {
                false
            } else {
                lastEmittedAtNanos[event.type] = nowNanos
                true
            }
        }
    }

    private val TamperType.isBlocking: Boolean
        get() = this == TamperType.MOCK_LOCATION || this == TamperType.CLOCK_MANIPULATION

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
