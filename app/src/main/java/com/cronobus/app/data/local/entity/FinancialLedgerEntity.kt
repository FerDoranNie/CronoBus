package com.cronobus.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad de persistencia local para el libro mayor (ledger) financiero de una unidad.
 *
 * Mantiene el acumulado en tiempo real de multas brutas, condonaciones por fuerza mayor
 * y conteos operativos para asegurar que los cálculos de liquidación y exportación CSV
 * sobrevivan al ciclo de vida del proceso y reinicios de la aplicación.
 */
@Entity(tableName = "financial_ledger")
data class FinancialLedgerEntity(
    @PrimaryKey
    val unitId: String,
    val routeId: String,
    val grossFinesMxn: Double = 0.0,
    val forgivenFinesMxn: Double = 0.0,
    val assessmentsCount: Int = 0,
    val exemptionsCount: Int = 0,
    val updatedAtUtcMs: Long,
) {
    /**
     * Importe neto de sanciones monetarias atribuibles a la unidad (no negativo).
     */
    val netFinesMxn: Double
        get() = (grossFinesMxn - forgivenFinesMxn).coerceAtLeast(0.0)
}
