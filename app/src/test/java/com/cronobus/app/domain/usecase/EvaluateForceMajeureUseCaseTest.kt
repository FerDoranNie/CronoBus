package com.cronobus.app.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cronobus.app.data.local.CronoBusDatabase
import com.cronobus.app.data.local.IncidenceType
import com.cronobus.app.data.local.entity.IncidenceReportEntity
import com.cronobus.app.domain.model.GeoPoint
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regla crítica del modelo: 3 unidades distintas / 500 m / 15 min.
 *
 * Los reportes se insertan en Room real (in-memory) para validar también la consulta bounding-box
 * y el filtro Haversine exacto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EvaluateForceMajeureUseCaseTest {

    private lateinit var database: CronoBusDatabase
    private lateinit var useCase: EvaluateForceMajeureUseCase

    private val routeId = "Ruta 42 - Ramal Centro"
    private val center = GeoPoint(19.4255, -99.1720)
    private val referenceTime = 1_760_000_000_000L

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            CronoBusDatabase::class.java,
        ).allowMainThreadQueries().build()

        useCase = EvaluateForceMajeureUseCase(
            incidenceReportDao = database.incidenceReportDao(),
            nowUtcMs = { referenceTime },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `three distinct units inside the radius activate the exemption`() = runTest {
        insertReport(unitId = "9", latOffset = 0.001, minutesAgo = 2)
        insertReport(unitId = "22", latOffset = -0.002, minutesAgo = 5)
        insertReport(unitId = "14", latOffset = 0.003, minutesAgo = 10)

        val result = useCase.evaluate(routeId, center, referenceTime)

        assertTrue(result.isExempt)
        assertEquals(3, result.qualifyingUnitsCount)
        assertEquals(setOf("9", "14", "22"), result.distinctUnitIds)
        assertEquals(3, result.matchingReportIds.size)
    }

    @Test
    fun `two distinct units are not enough`() = runTest {
        insertReport(unitId = "9", latOffset = 0.001, minutesAgo = 2)
        insertReport(unitId = "22", latOffset = -0.002, minutesAgo = 5)

        val result = useCase.evaluate(routeId, center, referenceTime)

        assertFalse(result.isExempt)
        assertEquals(2, result.qualifyingUnitsCount)
    }

    @Test
    fun `a report outside 500 meters does not count`() = runTest {
        insertReport(unitId = "9", latOffset = 0.001, minutesAgo = 2)
        insertReport(unitId = "22", latOffset = -0.002, minutesAgo = 5)
        // ~667 m al norte: dentro de la caja envolvente amplia pero fuera del radio Haversine.
        insertReport(unitId = "14", latOffset = 0.006, minutesAgo = 5)

        val result = useCase.evaluate(routeId, center, referenceTime)

        assertFalse(result.isExempt)
        assertEquals(2, result.qualifyingUnitsCount)
    }

    @Test
    fun `a report older than the 15 minute window does not count`() = runTest {
        insertReport(unitId = "9", latOffset = 0.001, minutesAgo = 2)
        insertReport(unitId = "22", latOffset = -0.002, minutesAgo = 5)
        insertReport(unitId = "14", latOffset = 0.003, minutesAgo = 16)

        val result = useCase.evaluate(routeId, center, referenceTime)

        assertFalse(result.isExempt)
        assertEquals(2, result.qualifyingUnitsCount)
    }

    @Test
    fun `three reports from the same unit count as one`() = runTest {
        insertReport(unitId = "9", latOffset = 0.001, minutesAgo = 2)
        insertReport(unitId = "9", latOffset = -0.001, minutesAgo = 4)
        insertReport(unitId = "9", latOffset = 0.002, minutesAgo = 6)

        val result = useCase.evaluate(routeId, center, referenceTime)

        assertFalse(result.isExempt)
        assertEquals(1, result.qualifyingUnitsCount)
    }

    @Test
    fun `reports from another route are ignored`() = runTest {
        insertReport(unitId = "9", latOffset = 0.001, minutesAgo = 2, route = "Ruta 15 - Universidad")
        insertReport(unitId = "22", latOffset = -0.002, minutesAgo = 5, route = "Ruta 15 - Universidad")
        insertReport(unitId = "14", latOffset = 0.003, minutesAgo = 8, route = "Ruta 15 - Universidad")

        val result = useCase.evaluate(routeId, center, referenceTime)

        assertFalse(result.isExempt)
        assertEquals(0, result.qualifyingUnitsCount)
    }

    private suspend fun insertReport(
        unitId: String,
        latOffset: Double,
        minutesAgo: Long,
        route: String = routeId,
    ) {
        database.incidenceReportDao().insert(
            IncidenceReportEntity(
                shiftId = "shift-$unitId",
                unitId = unitId,
                routeId = route,
                incidenceType = IncidenceType.BLOCKADE,
                description = "Bloqueo de prueba",
                latitude = center.latitude + latOffset,
                longitude = center.longitude,
                timestampUtcMs = referenceTime - minutesAgo * 60_000L,
                elapsedRealtimeNanos = (referenceTime - minutesAgo * 60_000L) * 1_000L,
            ),
        )
    }
}
