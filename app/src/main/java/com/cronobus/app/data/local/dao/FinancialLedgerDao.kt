package com.cronobus.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.cronobus.app.data.local.entity.FinancialLedgerEntity
import kotlinx.coroutines.flow.Flow

/**
 * Acceso al libro mayor financiero por unidad (Modelo Financiero Mixto).
 *
 * El ledger **no** forma parte de la cola Store & Forward: es un acumulado local que debe
 * sobrevivir a la muerte del proceso para que el `.csv` de cierre de turno no pierda sanciones
 * ni condonaciones. La fuente de verdad sigue siendo la base Room; la memoria del
 * [com.cronobus.app.bridge.CronoBusJsBridge] solo actúa como caché de lectura rápida.
 *
 * [accumulateAssessment] es incremental y transaccional: dos cruces del checador no pueden pisarse
 * entre sí ni perder un importe por una actualización de "leer-modificar-escribir" sin bloqueo.
 */
@Dao
abstract class FinancialLedgerDao {

    @Query("SELECT * FROM financial_ledger ORDER BY unitId ASC")
    abstract suspend fun getAll(): List<FinancialLedgerEntity>

    @Query("SELECT * FROM financial_ledger WHERE unitId = :unitId LIMIT 1")
    abstract suspend fun getByUnit(unitId: String): FinancialLedgerEntity?

    /** Observa el ledger completo para futuras pantallas de liquidación en Compose. */
    @Query("SELECT * FROM financial_ledger ORDER BY unitId ASC")
    abstract fun observeAll(): Flow<List<FinancialLedgerEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfAbsent(ledger: FinancialLedgerEntity): Long

    @Update
    abstract suspend fun update(ledger: FinancialLedgerEntity)

    /**
     * Registra una evaluación de headway para [unitId].
     *
     * - [grossFineMxn] es el importe teórico aunque luego se condone (trazabilidad bruta/neta).
     * - [forgivenMxn] se acota a `[0, grossFineMxn]` para que una condonación no produzca un neto
     *   negativo por datos corruptos.
     * - Un importe condonado incrementa también el contador de exenciones usado por el despacho
     *   equitativo.
     */
    @Transaction
    open suspend fun accumulateAssessment(
        unitId: String,
        routeId: String,
        grossFineMxn: Double,
        forgivenMxn: Double,
        atUtcMs: Long,
    ) {
        val safeGross = if (grossFineMxn.isFinite()) grossFineMxn.coerceAtLeast(0.0) else 0.0
        val safeForgiven =
            if (forgivenMxn.isFinite()) forgivenMxn.coerceIn(0.0, safeGross) else 0.0
        val previous = getByUnit(unitId)

        if (previous == null) {
            insertIfAbsent(
                FinancialLedgerEntity(
                    unitId = unitId,
                    routeId = routeId,
                    grossFinesMxn = safeGross,
                    forgivenFinesMxn = safeForgiven,
                    assessmentsCount = 1,
                    exemptionsCount = if (safeForgiven > 0.0) 1 else 0,
                    updatedAtUtcMs = atUtcMs,
                ),
            )
            return
        }

        update(
            previous.copy(
                routeId = routeId,
                grossFinesMxn = previous.grossFinesMxn + safeGross,
                forgivenFinesMxn = previous.forgivenFinesMxn + safeForgiven,
                assessmentsCount = previous.assessmentsCount + 1,
                exemptionsCount = previous.exemptionsCount + if (safeForgiven > 0.0) 1 else 0,
                updatedAtUtcMs = atUtcMs,
            ),
        )
    }

    /** Borra el acumulado. Solo para mantenimiento explícito o pruebas; nunca en producción. */
    @Query("DELETE FROM financial_ledger")
    abstract suspend fun clear()

    @Query("SELECT COUNT(*) FROM financial_ledger")
    abstract suspend fun count(): Int
}
