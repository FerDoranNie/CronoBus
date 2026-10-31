package com.cronobus.app.domain.model

/**
 * Estado operativo del intervalo (headway) de la unidad al cruzar un punto de control.
 */
enum class HeadwayStatus {
    /**
     * Dentro del margen de tolerancia programado.
     */
    ON_TIME,

    /**
     * Desvío moderado respecto a la frecuencia óptima; genera alerta preventiva.
     */
    WARNING,

    /**
     * Retraso excesivo fuera de tolerancia; sugiere sanción económica según el tabulador.
     */
    LATE_FINE,

    /**
     * Retraso justificado y condonado por regla de fuerza mayor verificada.
     */
    EXEMPT_FORCE_MAJEURE,
}

/**
 * Evaluación cuantitativa y cualitativa del intervalo de paso de una unidad en una geocerca.
 *
 * @property checkpointId Identificador único del punto de control o geocerca.
 * @property checkpointName Nombre humano legible de la parada o checador.
 * @property targetHeadwaySeconds Intervalo programado o meta entre unidades consecutivas (en segundos).
 * @property actualHeadwaySeconds Intervalo real registrado desde la unidad precedente (en segundos).
 * @property deltaSeconds Diferencia cuantitativa (actual - target). Valores positivos indican retraso.
 * @property status Clasificación operativa del paso según la tolerancia definida.
 * @property suggestedFineAmountMxn Monto propuesto de sanción en MXN (0.0 si está a tiempo o exento).
 * @property forceMajeureResult Información detallada del análisis de fuerza mayor si fue evaluado.
 */
data class HeadwayAssessment(
    val checkpointId: String,
    val checkpointName: String,
    val targetHeadwaySeconds: Long,
    val actualHeadwaySeconds: Long,
    val deltaSeconds: Long,
    val status: HeadwayStatus,
    val suggestedFineAmountMxn: Double,
    val forceMajeureResult: ForceMajeureResult? = null,
)

