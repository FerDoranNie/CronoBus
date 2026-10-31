package com.cronobus.app.domain.usecase

import com.cronobus.app.data.local.dao.CheckpointEventDao
import com.cronobus.app.domain.model.ForceMajeureResult
import com.cronobus.app.domain.model.HeadwayAssessment
import com.cronobus.app.domain.model.HeadwayStatus
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.round

/**
 * Parámetros del tabulador de headway.
 *
 * @param targetHeadwaySeconds Intervalo objetivo entre unidades en el mismo punto de control.
 * @param onTimeToleranceSeconds Banda neutra alrededor del objetivo (adelanto o retraso).
 * @param warningToleranceSeconds Retraso máximo sin sanción; a partir de aquí se cobra.
 * @param finePerLateMinuteMxn Tarifa por minuto de retraso excedido.
 * @param maxFineMxn Tope de sanción por cruce para evitar multas desproporcionadas por un atasco.
 * @param maxReferenceAgeSeconds Antigüedad máxima del cruce precedente para considerarlo headway
 *   válido. Un cruce de hace horas no es una referencia operativa y no debe generar una multa.
 */
data class HeadwayPolicy(
    val targetHeadwaySeconds: Long = 300L,
    val onTimeToleranceSeconds: Long = 60L,
    val warningToleranceSeconds: Long = 180L,
    val finePerLateMinuteMxn: Double = 10.0,
    val maxFineMxn: Double = 200.0,
    val maxReferenceAgeSeconds: Long = 2L * 60L * 60L,
) {
    init {
        require(targetHeadwaySeconds > 0L) { "targetHeadwaySeconds debe ser mayor que 0" }
        require(onTimeToleranceSeconds >= 0L) { "onTimeToleranceSeconds no puede ser negativo" }
        require(warningToleranceSeconds >= onTimeToleranceSeconds) {
            "warningToleranceSeconds debe ser mayor o igual que onTimeToleranceSeconds"
        }
        require(finePerLateMinuteMxn >= 0.0) { "finePerLateMinuteMxn no puede ser negativo" }
        require(maxFineMxn >= 0.0) { "maxFineMxn no puede ser negativo" }
        require(maxReferenceAgeSeconds > 0L) { "maxReferenceAgeSeconds debe ser mayor que 0" }
    }
}

/**
 * Calcula el intervalo real entre unidades y sugiere sanción o exención.
 *
 * El headway se mide contra el cruce más reciente de **otra unidad** en el mismo punto de control
 * y ruta. El primer cruce del día no tiene referencia: [assessCrossing] devuelve `null` y el
 * checador lo registra sin sanción.
 *
 * Reglas:
 * - `|actual - objetivo| <= onTimeTolerance` → `ON_TIME`.
 * - Adelanto excesivo (carrerita) → `WARNING`; no se sanciona aquí porque la sanción por
 *   invasión de carril requiere validación del despacho.
 * - Retraso entre `onTimeTolerance` y `warningTolerance` → `WARNING`.
 * - Retraso mayor a `warningTolerance` → `LATE_FINE` (minutos excedidos × tarifa, con tope).
 * - Si [ForceMajeureResult.isExempt] es verdadero y había sanción → `EXEMPT_FORCE_MAJEURE`.
 */
class HeadwayCalculatorUseCase(
    private val checkpointEventDao: CheckpointEventDao,
    val policy: HeadwayPolicy = HeadwayPolicy(),
) {

    /**
     * Evalúa un cruce contra el último cruce de otra unidad en el mismo checkpoint.
     *
     * @return `null` si no existe una unidad precedente dentro de la ruta (primer paso del día).
     */
    suspend fun assessCrossing(
        checkpointId: String,
        checkpointName: String,
        routeId: String,
        unitId: String,
        observedAtUtcMs: Long,
        targetHeadwaySeconds: Long = policy.targetHeadwaySeconds,
        forceMajeure: ForceMajeureResult? = null,
    ): HeadwayAssessment? {
        if (checkpointId.isBlank() || routeId.isBlank() || unitId.isBlank()) return null

        val previous = checkpointEventDao.getPreviousCrossing(
            checkpointId = checkpointId,
            routeId = routeId,
            unitId = unitId,
            beforeUtcMs = observedAtUtcMs,
        ) ?: return null

        val elapsedSeconds = ((observedAtUtcMs - previous.timestampUtcMs) / 1_000L)
            .coerceAtLeast(0L)
        if (elapsedSeconds > policy.maxReferenceAgeSeconds) return null

        return assess(
            checkpointId = checkpointId,
            checkpointName = checkpointName,
            targetHeadwaySeconds = targetHeadwaySeconds,
            actualHeadwaySeconds = elapsedSeconds,
            forceMajeure = forceMajeure,
        )
    }

    /** Evaluación pura, sin dependencias de Android ni de Room. */
    fun assess(
        checkpointId: String,
        checkpointName: String,
        targetHeadwaySeconds: Long,
        actualHeadwaySeconds: Long,
        forceMajeure: ForceMajeureResult? = null,
    ): HeadwayAssessment {
        require(targetHeadwaySeconds > 0L) { "targetHeadwaySeconds debe ser mayor que 0" }
        require(actualHeadwaySeconds >= 0L) { "actualHeadwaySeconds no puede ser negativo" }

        val delta = actualHeadwaySeconds - targetHeadwaySeconds
        val absoluteDelta = abs(delta)
        val baseStatus = when {
            absoluteDelta <= policy.onTimeToleranceSeconds -> HeadwayStatus.ON_TIME
            delta < 0L -> HeadwayStatus.WARNING
            delta <= policy.warningToleranceSeconds -> HeadwayStatus.WARNING
            else -> HeadwayStatus.LATE_FINE
        }

        val exempt = baseStatus == HeadwayStatus.LATE_FINE && forceMajeure?.isExempt == true
        val status = if (exempt) HeadwayStatus.EXEMPT_FORCE_MAJEURE else baseStatus
        val fine = if (status == HeadwayStatus.LATE_FINE) fineForDelta(delta) else 0.0

        return HeadwayAssessment(
            checkpointId = checkpointId,
            checkpointName = checkpointName,
            targetHeadwaySeconds = targetHeadwaySeconds,
            actualHeadwaySeconds = actualHeadwaySeconds,
            deltaSeconds = delta,
            status = status,
            suggestedFineAmountMxn = fine,
            forceMajeureResult = forceMajeure,
        )
    }

    /**
     * Sanción teórica para un retraso, **antes** de aplicar exenciones. Se expone para que el
     * exportador financiero pueda registrar el monto condonado por fuerza mayor.
     */
    fun fineForDelta(deltaSeconds: Long): Double {
        if (deltaSeconds <= policy.warningToleranceSeconds) return 0.0
        val lateMinutes = ceil(deltaSeconds.toDouble() / SECONDS_PER_MINUTE).toLong()
        val raw = lateMinutes * policy.finePerLateMinuteMxn
        return round(min(raw, policy.maxFineMxn) * 100.0) / 100.0
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60.0
    }
}
