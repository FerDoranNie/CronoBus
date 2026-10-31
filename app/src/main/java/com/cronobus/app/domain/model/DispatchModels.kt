package com.cronobus.app.domain.model

/**
 * Representa una posición asignada en la cola rotativa de despacho equitativo en base/terminal.
 *
 * @property position Posición actual en el orden de salida (1-indexed).
 * @property unitId Identificador de la unidad de transporte.
 * @property driverAlias Nombre o alias del operador asignado a la unidad.
 * @property routeId Identificador de la ruta o ramal a operar.
 * @property scheduledDepartureEpochMs Hora estimada/programada de salida en epoch milisegundos.
 * @property accumulatedFineMxn Monto acumulado de sanciones pendientes de liquidación en MXN.
 * @property bonusEligible Indica si la unidad califica para el bono de puntualidad operativo.
 * @property forceMajeureExemptionsCount Cantidad de sanciones exentas por causa de fuerza mayor en el turno.
 */
data class DispatchQueueSlot(
    val position: Int,
    val unitId: String,
    val driverAlias: String,
    val routeId: String,
    val scheduledDepartureEpochMs: Long,
    val accumulatedFineMxn: Double,
    val bonusEligible: Boolean,
    val forceMajeureExemptionsCount: Int,
)

/**
 * Fila consolidada para la auditoría y exportación financiera en formato CSV.
 * Sustenta el Modelo Financiero Mixto (SaaS + Fines + Bonuses - Forgiven).
 *
 * @property unitId Identificador de la unidad auditada.
 * @property routeId Identificador de la ruta o ramal.
 * @property saasFeeMxn Cuota de plataforma SaaS asignada a la unidad en MXN.
 * @property grossFinesMxn Monto bruto total de multas calculadas durante el periodo en MXN.
 * @property forceMajeureForgivenMxn Monto total de multas condonadas por fuerza mayor en MXN.
 * @property netFinesMxn Monto neto exigible de multas (grossFinesMxn - forceMajeureForgivenMxn).
 * @property punctualityBonusMxn Bonificación ganada por cumplimiento estricto de headway en MXN.
 */
data class FinancialCsvRow(
    val unitId: String,
    val routeId: String,
    val saasFeeMxn: Double,
    val grossFinesMxn: Double,
    val forceMajeureForgivenMxn: Double,
    val netFinesMxn: Double,
    val punctualityBonusMxn: Double,
)

