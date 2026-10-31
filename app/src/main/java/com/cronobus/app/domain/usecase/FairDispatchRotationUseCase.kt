package com.cronobus.app.domain.usecase

import com.cronobus.app.domain.model.DispatchQueueSlot

/**
 * Candidato de unidad para la cola rotativa.
 *
 * @param lastDepartureEpochMs Última salida conocida; `null` si la unidad no ha salido en el
 *   periodo actual y por tanto tiene prioridad.
 * @param accumulatedFineMxn Multas netas acumuladas en el turno (solo informativo para la UI).
 * @param forceMajeureExemptionsCount Sanciones condonadas; se muestra para auditoría y no penaliza
 *   la elegibilidad al bono.
 */
data class DispatchCandidate(
    val unitId: String,
    val driverAlias: String,
    val routeId: String,
    val lastDepartureEpochMs: Long? = null,
    val accumulatedFineMxn: Double = 0.0,
    val forceMajeureExemptionsCount: Int = 0,
    val available: Boolean = true,
)

/**
 * Rotación equitativa de salidas en base/terminal.
 *
 * El algoritmo es determinista:
 * 1. Las unidades disponibles que **nunca** han salido van primero.
 * 2. Después, la unidad con la salida más antigua.
 * 3. Empate por `unitId` para que dos administradores vean el mismo orden.
 *
 * [rotateAfterDeparture] mueve la unidad despachada al final y recalcula los horarios relativos a
 * `now`, garantizando que nadie acumule dos salidas antes de que el resto haya salido una vez.
 *
 * La clase es sin estado; el llamador conserva la lista resultante (el puente JS la mantiene en
 * memoria y la reconstruye desde los turnos activos si el proceso se reinicia).
 */
class FairDispatchRotationUseCase(
    val departureIntervalSeconds: Long = 300L,
    val punctualityBonusMxn: Double = 45.0,
) {

    init {
        require(departureIntervalSeconds > 0L) { "departureIntervalSeconds debe ser mayor que 0" }
        require(punctualityBonusMxn >= 0.0) { "punctualityBonusMxn no puede ser negativo" }
    }

    /** Construye (o reconstruye) la cola a partir de las unidades activas. */
    fun buildQueue(
        candidates: List<DispatchCandidate>,
        nowUtcMs: Long,
    ): List<DispatchQueueSlot> {
        val available = candidates
            .filter { it.available && it.unitId.isNotBlank() }
            .distinctBy { it.unitId }
            .sortedWith(
                compareBy<DispatchCandidate> { it.lastDepartureEpochMs ?: Long.MIN_VALUE }
                    .thenBy { it.unitId },
            )

        return available.mapIndexed { index, candidate ->
            DispatchQueueSlot(
                position = index + 1,
                unitId = candidate.unitId,
                driverAlias = candidate.driverAlias,
                routeId = candidate.routeId,
                scheduledDepartureEpochMs = nowUtcMs + index * departureIntervalMillis(),
                accumulatedFineMxn = candidate.accumulatedFineMxn.coerceAtLeast(0.0),
                bonusEligible = candidate.accumulatedFineMxn <= 0.0,
                forceMajeureExemptionsCount = candidate.forceMajeureExemptionsCount.coerceAtLeast(0),
            )
        }
    }

    /**
     * Registra la salida de la primera unidad y rota el resto.
     *
     * @return La cola rotada. Si [departingUnitId] no existe, devuelve la cola original ordenada.
     */
    fun rotateAfterDeparture(
        queue: List<DispatchQueueSlot>,
        departingUnitId: String,
        nowUtcMs: Long,
    ): List<DispatchQueueSlot> {
        val ordered = queue.sortedBy { it.position }
        val departing = ordered.firstOrNull { it.unitId == departingUnitId } ?: return ordered

        // La unidad que sale pasa al final; las demás conservan su orden relativo.
        val rotated = ordered.filterNot { it.unitId == departingUnitId } + departing.copy(
            scheduledDepartureEpochMs = nowUtcMs + ordered.size * departureIntervalMillis(),
        )

        return rotated.mapIndexed { index, slot ->
            slot.copy(
                position = index + 1,
                scheduledDepartureEpochMs = nowUtcMs + (index + 1) * departureIntervalMillis(),
            )
        }
    }

    /** Actualiza métricas de una unidad sin alterar su posición en la cola. */
    fun updateMetrics(
        queue: List<DispatchQueueSlot>,
        unitId: String,
        accumulatedFineMxn: Double,
        forceMajeureExemptionsCount: Int,
    ): List<DispatchQueueSlot> = queue.map { slot ->
        if (slot.unitId != unitId) {
            slot
        } else {
            val fines = accumulatedFineMxn.coerceAtLeast(0.0)
            slot.copy(
                accumulatedFineMxn = fines,
                bonusEligible = fines <= 0.0,
                forceMajeureExemptionsCount = forceMajeureExemptionsCount.coerceAtLeast(0),
            )
        }
    }

    /** Siguiente unidad a despachar, o `null` si la cola está vacía. */
    fun nextSlot(queue: List<DispatchQueueSlot>): DispatchQueueSlot? =
        queue.minByOrNull { it.position }

    /** Bono de puntualidad aplicable a una unidad sin sanciones netas. */
    fun bonusFor(slot: DispatchQueueSlot): Double =
        if (slot.bonusEligible && slot.accumulatedFineMxn <= 0.0) punctualityBonusMxn else 0.0

    private fun departureIntervalMillis(): Long = departureIntervalSeconds * 1_000L
}
