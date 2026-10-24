package com.cronobus.app.domain.location

import com.cronobus.app.domain.model.GeoPoint
import com.cronobus.app.domain.model.LocationSample
import com.cronobus.app.domain.model.LocationUpdate
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Filtro de Kalman 2D para GPS urbano (cañón de edificios, rebote multipath y túneles).
 *
 * Estado: `x = [lat, lng, vNorte(m/s), vEste(m/s)]`.
 *
 * - **Predicción** con modelo de velocidad constante y ruido de aceleración (WHITE_NOISE),
 *   expresado en metros y convertido a grados con [GeoMath], de modo que el filtro se comporte
 *   igual en cualquier latitud. La matriz Q se aproxima en diagonal (posición y velocidad
 *   independientes): estable numéricamente en móvil y suficiente para ruido GPS urbano.
 * - **Actualización** con la precisión reportada (`accuracyMeters`) como matriz R, más un piso de
 *   confianza para no sobreajustar lecturas "demasiado buenas".
 * - **Robustez**:
 *   * rechaza lecturas inválidas, de precisión pobre o con `dt <= 0` (fuera de orden);
 *   * rechaza "teleports" (saltos imposibles) y, si el racimo persiste, se re-ancla;
 *   * se re-ancla tras una pérdida de señal mayor a [FilterConfig.maxCoastMillis];
 *   * detecta maniobras (frenado/aceleración bruscos) inyectando ruido de proceso.
 *
 * Es Kotlin puro: no depende de Android y se puede probar en JVM.
 */
class KalmanLocationFilter(
    private val config: FilterConfig = FilterConfig(),
) {

    data class FilterConfig(
        /** Desviación de aceleración esperada en ciudad (m/s²). Más alto = más reactivo y ruidoso. */
        val accelerationNoiseMps2: Double = 1.6,
        /** Piso de precisión para no sobreconfiar en reportes de 1-2 m. */
        val minAccuracyMeters: Double = 3.0,
        /** Lecturas con peor precisión se descartan. */
        val maxAccuracyMeters: Double = 100.0,
        /** Incertidumbre inicial de velocidad (m/s). */
        val initialVelocitySigmaMps: Double = 12.0,
        /** Hueco máximo de señal que se puede "navegar a ciegas" antes de re-anclar. */
        val maxCoastMillis: Long = 20_000L,
        /** Velocidad implícita máxima plausible para un autobús (m/s). ~198 km/h. */
        val maxPlausibleSpeedMps: Double = 55.0,
        /** Distancia mínima para aplicar la compuerta de teleport. */
        val minPlausibleCheckDistanceMeters: Double = 100.0,
        /** Rechazos consecutivos antes de aceptar que el autobús cambió de racimo real. */
        val maxConsecutiveRejections: Int = 3,
        /** Por debajo de esta velocidad se considera detenido y se anula la deriva. */
        val stopSpeedThresholdMps: Double = 0.6,
        /** Por debajo de esta velocidad no se estima rumbo (se conserva el del proveedor). */
        val minBearingSpeedMps: Double = 1.0,
        /** Diferencia de velocidad que dispara la inyección de ruido por maniobra (m/s). */
        val maneuverSpeedThresholdMps: Double = 4.0,
        /** Factor de ruido de proceso ante maniobra detectada. */
        val maneuverNoiseBoost: Double = 4.0,
    )

    private val state = DoubleArray(STATE_SIZE)
    private val covariance = Array(STATE_SIZE) { DoubleArray(STATE_SIZE) }

    private var initialized = false
    private var lastAcceptedMonoNanos = 0L
    private var consecutiveRejections = 0

    @Volatile
    private var latestUpdate: LocationUpdate? = null

    /** Último resultado entregado (o `null` si el filtro está reiniciado). */
    val latest: LocationUpdate?
        get() = latestUpdate

    /**
     * Procesa una lectura cruda.
     *
     * @return lectura filtrada, o `null` si la muestra debe descartarse (inválida, imprecisa,
     *   fuera de orden o salto imposible).
     */
    @Synchronized
    fun update(sample: LocationSample): LocationUpdate? {
        if (!sample.position.isValid) return null

        val accuracy = sanitizeAccuracy(sample.accuracyMeters)
        if (accuracy > config.maxAccuracyMeters) return null

        if (!initialized) return initialize(sample, accuracy)

        val deltaNanos = sample.elapsedRealtimeNanos - lastAcceptedMonoNanos
        if (deltaNanos <= 0L) return null
        val deltaSeconds = deltaNanos / NANOS_PER_SECOND

        if (deltaNanos > config.maxCoastMillis * NANOS_PER_MILLI) {
            return initialize(sample, accuracy)
        }

        val previous = latestUpdate ?: return initialize(sample, accuracy)
        val jumpMeters = GeoMath.haversineMeters(previous.position, sample.position)
        val impliedSpeed = jumpMeters / deltaSeconds
        if (jumpMeters > config.minPlausibleCheckDistanceMeters &&
            impliedSpeed > config.maxPlausibleSpeedMps
        ) {
            consecutiveRejections++
            if (consecutiveRejections >= config.maxConsecutiveRejections) {
                return initialize(sample, accuracy)
            }
            return null
        }
        consecutiveRejections = 0

        val estimatedSpeed = hypot(state[VELOCITY_NORTH], state[VELOCITY_EAST])
        // Velocidad desconocida (NaN) nunca dispara maniobra: evita inflar Q sin evidencia.
        val isManeuver = sample.speedMps.isFinite() &&
            abs(sample.speedMps - estimatedSpeed) > config.maneuverSpeedThresholdMps
        predict(
            deltaSeconds = deltaSeconds,
            noiseScale = if (isManeuver) config.maneuverNoiseBoost else 1.0,
        )
        if (!applyMeasurement(sample.position, accuracy)) {
            // Matriz singular o estado NaN: re-anclar sobre la lectura (ya validada) en lugar de
            // perder el fix y dejar `latest` apuntando a un estado corrupto.
            return initialize(sample, accuracy)
        }

        lastAcceptedMonoNanos = sample.elapsedRealtimeNanos
        return publish(sample, accuracy, filtered = true)
    }

    /** Reinicia el estado (fin de turno / cambio de unidad). */
    @Synchronized
    fun reset() {
        initialized = false
        lastAcceptedMonoNanos = 0L
        consecutiveRejections = 0
        state.fill(0.0)
        covariance.forEach { it.fill(0.0) }
        latestUpdate = null
    }

    private fun initialize(sample: LocationSample, accuracyMeters: Double): LocationUpdate {
        state[LATITUDE] = sample.position.latitude
        state[LONGITUDE] = sample.position.longitude

        val reportedSpeed = if (sample.speedMps.isFinite()) {
            sample.speedMps.coerceIn(0.0, config.maxPlausibleSpeedMps)
        } else {
            0.0
        }
        // Sin rumbo confiable (NaN/∞) la velocidad inicial se fija en 0: 0 · cos(NaN) sería NaN y
        // envenenaría todo el estado del filtro.
        if (sample.bearingDegrees.isFinite()) {
            val bearingRadians = Math.toRadians(GeoMath.normalizeDegrees(sample.bearingDegrees))
            state[VELOCITY_NORTH] = reportedSpeed * cos(bearingRadians)
            state[VELOCITY_EAST] = reportedSpeed * sin(bearingRadians)
        } else {
            state[VELOCITY_NORTH] = 0.0
            state[VELOCITY_EAST] = 0.0
        }

        covariance.forEach { it.fill(0.0) }
        val metersPerLat = GeoMath.metersPerDegreeLatitude(state[LATITUDE])
        val metersPerLng = GeoMath.metersPerDegreeLongitude(state[LATITUDE])
        covariance[LATITUDE][LATITUDE] = sq(accuracyMeters / metersPerLat)
        covariance[LONGITUDE][LONGITUDE] = sq(accuracyMeters / metersPerLng)
        covariance[VELOCITY_NORTH][VELOCITY_NORTH] = sq(config.initialVelocitySigmaMps)
        covariance[VELOCITY_EAST][VELOCITY_EAST] = sq(config.initialVelocitySigmaMps)

        initialized = true
        lastAcceptedMonoNanos = sample.elapsedRealtimeNanos
        consecutiveRejections = 0

        return publish(sample, accuracyMeters, filtered = false)
    }

    /** Predicción: x ← F·x ; P ← F·P·Fᵀ + Q. */
    private fun predict(deltaSeconds: Double, noiseScale: Double) {
        val metersPerLat = GeoMath.metersPerDegreeLatitude(state[LATITUDE])
        val metersPerLng = GeoMath.metersPerDegreeLongitude(state[LATITUDE])
        val factorLat = deltaSeconds / metersPerLat
        val factorLng = deltaSeconds / metersPerLng

        state[LATITUDE] += factorLat * state[VELOCITY_NORTH]
        state[LONGITUDE] += factorLng * state[VELOCITY_EAST]

        val previous = covariance
        val transformed = Array(STATE_SIZE) { row ->
            DoubleArray(STATE_SIZE) { column ->
                when (row) {
                    LATITUDE -> previous[row][column] + factorLat * previous[VELOCITY_NORTH][column]
                    LONGITUDE -> previous[row][column] + factorLng * previous[VELOCITY_EAST][column]
                    else -> previous[row][column]
                }
            }
        }

        val sigma = config.accelerationNoiseMps2 * noiseScale
        val positionNoiseMeters = 0.5 * sigma * deltaSeconds * deltaSeconds
        val velocityNoise = sigma * deltaSeconds

        for (row in 0 until STATE_SIZE) {
            for (column in 0 until STATE_SIZE) {
                val propagated = transformed[row][column] + when (column) {
                    LATITUDE -> transformed[row][VELOCITY_NORTH] * factorLat
                    LONGITUDE -> transformed[row][VELOCITY_EAST] * factorLng
                    else -> 0.0
                }
                covariance[row][column] = propagated
            }
        }
        covariance[LATITUDE][LATITUDE] += sq(positionNoiseMeters / metersPerLat)
        covariance[LONGITUDE][LONGITUDE] += sq(positionNoiseMeters / metersPerLng)
        covariance[VELOCITY_NORTH][VELOCITY_NORTH] += sq(velocityNoise)
        covariance[VELOCITY_EAST][VELOCITY_EAST] += sq(velocityNoise)

        symmetrize()
    }

    /**
     * Corrección de Kalman para medición 2D (lat, lng):
     * `K = P·Hᵀ·(H·P·Hᵀ + R)⁻¹ ; x ← x + K·y ; P ← (I − K·H)·P`.
     *
     * @return `false` si la matriz quedó singular o el estado degeneró a NaN.
     */
    private fun applyMeasurement(position: GeoPoint, accuracyMeters: Double): Boolean {
        val metersPerLat = GeoMath.metersPerDegreeLatitude(state[LATITUDE])
        val metersPerLng = GeoMath.metersPerDegreeLongitude(state[LATITUDE])

        // R en grados² (diagonal: lat y lng se asumen independientes).
        val varianceLat = sq(accuracyMeters / metersPerLat)
        val varianceLng = sq(accuracyMeters / metersPerLng)

        val s00 = covariance[LATITUDE][LATITUDE] + varianceLat
        val s01 = covariance[LATITUDE][LONGITUDE]
        val s10 = covariance[LONGITUDE][LATITUDE]
        val s11 = covariance[LONGITUDE][LONGITUDE] + varianceLng
        val determinant = s00 * s11 - s01 * s10
        if (!determinant.isFinite() || abs(determinant) < MIN_DETERMINANT) return false

        val inverse00 = s11 / determinant
        val inverse01 = -s01 / determinant
        val inverse10 = -s10 / determinant
        val inverse11 = s00 / determinant

        val gain = Array(STATE_SIZE) { index ->
            doubleArrayOf(
                covariance[index][LATITUDE] * inverse00 + covariance[index][LONGITUDE] * inverse10,
                covariance[index][LATITUDE] * inverse01 + covariance[index][LONGITUDE] * inverse11,
            )
        }

        val innovationLat = position.latitude - state[LATITUDE]
        val innovationLng = position.longitude - state[LONGITUDE]
        for (index in 0 until STATE_SIZE) {
            state[index] += gain[index][0] * innovationLat + gain[index][1] * innovationLng
        }

        // P ← P − K·(H·P) usando las filas 0 y 1 originales.
        val originalRowLat = covariance[LATITUDE].copyOf()
        val originalRowLng = covariance[LONGITUDE].copyOf()
        for (row in 0 until STATE_SIZE) {
            for (column in 0 until STATE_SIZE) {
                covariance[row][column] -= gain[row][0] * originalRowLat[column] +
                    gain[row][1] * originalRowLng[column]
            }
        }
        symmetrize()

        return state.all { it.isFinite() }
    }

    private fun publish(
        sample: LocationSample,
        sanitizedAccuracyMeters: Double,
        filtered: Boolean,
    ): LocationUpdate {
        var speed = hypot(state[VELOCITY_NORTH], state[VELOCITY_EAST])
        if (!speed.isFinite()) speed = 0.0

        if (speed < config.stopSpeedThresholdMps) {
            state[VELOCITY_NORTH] = 0.0
            state[VELOCITY_EAST] = 0.0
            speed = 0.0
        }

        val bearing = if (speed >= config.minBearingSpeedMps) {
            GeoMath.normalizeDegrees(Math.toDegrees(atan2(state[VELOCITY_EAST], state[VELOCITY_NORTH])))
        } else if (sample.bearingDegrees.isFinite()) {
            GeoMath.normalizeDegrees(sample.bearingDegrees)
        } else {
            // Detenido y sin rumbo del proveedor: conservar el último rumbo publicado.
            latestUpdate?.bearingDegrees ?: 0.0
        }

        val metersPerLat = GeoMath.metersPerDegreeLatitude(state[LATITUDE])
        val metersPerLng = GeoMath.metersPerDegreeLongitude(state[LATITUDE])
        val sigmaLat = sqrt(max(covariance[LATITUDE][LATITUDE], 0.0)) * metersPerLat
        val sigmaLng = sqrt(max(covariance[LONGITUDE][LONGITUDE], 0.0)) * metersPerLng
        val sigma = max(sigmaLat, sigmaLng)
        val estimatedAccuracy = if (sigma.isFinite()) {
            sigma.coerceAtLeast(config.minAccuracyMeters)
        } else {
            sanitizedAccuracyMeters
        }

        val update = LocationUpdate(
            position = GeoPoint(state[LATITUDE], state[LONGITUDE]),
            speedMps = speed,
            bearingDegrees = bearing,
            accuracyMeters = estimatedAccuracy,
            sourceAccuracyMeters = sanitizedAccuracyMeters,
            altitudeMeters = sample.altitudeMeters,
            provider = sample.provider,
            isMock = sample.isMock,
            isFiltered = filtered,
            elapsedRealtimeNanos = sample.elapsedRealtimeNanos,
            wallClockEpochMs = sample.wallClockEpochMs,
        )
        latestUpdate = update
        return update
    }

    private fun sanitizeAccuracy(reported: Double): Double = when {
        !reported.isFinite() || reported <= 0.0 -> DEFAULT_UNKNOWN_ACCURACY_METERS
        reported < config.minAccuracyMeters -> config.minAccuracyMeters
        else -> reported
    }

    private fun symmetrize() {
        for (row in 0 until STATE_SIZE) {
            for (column in row + 1 until STATE_SIZE) {
                val symmetric = 0.5 * (covariance[row][column] + covariance[column][row])
                covariance[row][column] = symmetric
                covariance[column][row] = symmetric
            }
        }
    }

    private fun sq(value: Double): Double = value * value

    private companion object {
        const val STATE_SIZE = 4
        const val LATITUDE = 0
        const val LONGITUDE = 1
        const val VELOCITY_NORTH = 2
        const val VELOCITY_EAST = 3

        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val NANOS_PER_MILLI = 1_000_000L
        const val DEFAULT_UNKNOWN_ACCURACY_METERS = 30.0

        /** Determinante mínimo de la innovación antes de considerar la matriz singular. */
        const val MIN_DETERMINANT = 1e-24
    }
}
