package com.cronobus.app.domain.model

/**
 * Resultado de la evaluación de exención de sanciones por causa de fuerza mayor.
 *
 * Regla de negocio operativa:
 * Si 3 o más unidades distintas reportan bloqueo o tráfico atípico dentro de un radio
 * de 500 metros en una ventana temporal de 15 minutos, las sanciones por retraso de
 * headway quedan automáticamente anuladas o condonadas para las unidades afectadas.
 *
 * @property isExempt Indica si el retraso califica para exención por fuerza mayor.
 * @property qualifyingUnitsCount Número de unidades distintas con reportes válidos en la ventana.
 * @property distinctUnitIds Conjunto de identificadores de unidades que confirmaron la incidencia.
 * @property radiusMeters Radio geográfico de análisis en metros (por defecto 500.0 m).
 * @property windowMinutes Ventana temporal hacia atrás para considerar reportes vigentes (por defecto 15 min).
 * @property matchingReportIds Lista de IDs de los reportes en base de datos que sustentan la exención.
 * @property reasonSummary Resumen explicativo legible para auditoría y visualización de despacho.
 */
data class ForceMajeureResult(
    val isExempt: Boolean,
    val qualifyingUnitsCount: Int,
    val distinctUnitIds: Set<String>,
    val radiusMeters: Double = 500.0,
    val windowMinutes: Int = 15,
    val matchingReportIds: List<Long> = emptyList(),
    val reasonSummary: String,
)

