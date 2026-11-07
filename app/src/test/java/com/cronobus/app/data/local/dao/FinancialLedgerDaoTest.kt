package com.cronobus.app.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cronobus.app.data.local.CronoBusDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifica el acumulado financiero persistente (migración Room `2 → 3`):
 * - La primera evaluación crea la fila de la unidad.
 * - Las siguientes son incrementales y no pierden importes entre cruces.
 * - Una condonación por fuerza mayor suma al bruto y a las exenciones, pero el neto no baja de 0.
 * - Los valores no finitos o fuera de rango se neutralizan antes de escribir.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FinancialLedgerDaoTest {

    private lateinit var database: CronoBusDatabase
    private lateinit var dao: FinancialLedgerDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            CronoBusDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.financialLedgerDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `accumulates gross fines and exemptions atomically`() = runTest {
        dao.accumulateAssessment(
            unitId = "14",
            routeId = "Ruta 42 - Ramal Centro",
            grossFineMxn = 120.0,
            forgivenMxn = 0.0,
            atUtcMs = 1_760_000_000_000L,
        )
        dao.accumulateAssessment(
            unitId = "14",
            routeId = "Ruta 42 - Ramal Centro",
            grossFineMxn = 80.0,
            forgivenMxn = 80.0,
            atUtcMs = 1_760_000_060_000L,
        )
        dao.accumulateAssessment(
            unitId = "14",
            routeId = "Ruta 42 - Ramal Centro",
            grossFineMxn = 50.0,
            forgivenMxn = 50.0,
            atUtcMs = 1_760_000_120_000L,
        )

        val ledger = dao.getByUnit("14")
        assertNotNull(ledger)
        requireNotNull(ledger)

        assertEquals(250.0, ledger.grossFinesMxn, 0.0001)
        assertEquals(130.0, ledger.forgivenFinesMxn, 0.0001)
        assertEquals(120.0, ledger.netFinesMxn, 0.0001)
        assertEquals(3, ledger.assessmentsCount)
        assertEquals(2, ledger.exemptionsCount)
        assertEquals(1_760_000_120_000L, ledger.updatedAtUtcMs)
    }

    @Test
    fun `sanitizes negative and non finite amounts before persisting`() = runTest {
        dao.accumulateAssessment(
            unitId = "09",
            routeId = "Ruta 42 - Ramal Centro",
            grossFineMxn = Double.NaN,
            forgivenMxn = Double.POSITIVE_INFINITY,
            atUtcMs = 1L,
        )
        dao.accumulateAssessment(
            unitId = "09",
            routeId = "Ruta 42 - Ramal Centro",
            grossFineMxn = -15.0,
            forgivenMxn = 40.0,
            atUtcMs = 2L,
        )

        val ledger = dao.getByUnit("09")
        assertNotNull(ledger)
        requireNotNull(ledger)

        assertEquals(0.0, ledger.grossFinesMxn, 0.0001)
        assertEquals(0.0, ledger.forgivenFinesMxn, 0.0001)
        assertEquals(0.0, ledger.netFinesMxn, 0.0001)
        assertEquals(2, ledger.assessmentsCount)
        assertEquals(0, ledger.exemptionsCount)
    }

    @Test
    fun `keeps one row per unit and lists them ordered`() = runTest {
        dao.accumulateAssessment("14", "Ruta 42", 10.0, 0.0, 10L)
        dao.accumulateAssessment("09", "Ruta 42", 20.0, 0.0, 20L)
        dao.accumulateAssessment("14", "Ruta 42", 5.0, 0.0, 30L)

        val all = dao.getAll()
        assertEquals(2, all.size)
        assertEquals(listOf("09", "14"), all.map { it.unitId })
        assertEquals(2, dao.count())
        assertEquals(15.0, dao.getByUnit("14")!!.grossFinesMxn, 0.0001)

        dao.clear()
        assertTrue(dao.getAll().isEmpty())
    }
}
