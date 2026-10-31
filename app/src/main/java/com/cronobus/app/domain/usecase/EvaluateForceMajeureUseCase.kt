package com.cronobus.app.domain.usecase

import com.cronobus.app.data.local.dao.IncidenceReportDao
import com.cronobus.app.data.local.entity.IncidenceReportEntity
import com.cronobus.app.domain.location.GeoMath
import com.cronobus.app.domain.model.ForceMajeureResult
import com.cronobus.app.domain.model.GeoPoint
import java.util.TreeSet
import kotlinx.coroutines.CancellationException

/**
 * Regla de **Exención Automática por Fuerza Mayor**.
 *
 * Una sanción de headway se condona cuando al menos [minimumDistinctUnits] unidades **distintas**
 * reportaron tráfico o bloqueo dentro de [radiusMeters] metros del punto evaluado y en los últimos
 * [windowMinutes] minutos.
 *
 * Decisiones de implementación:
 * - La consulta SQL reduce primero por ruta, tipo, ventana temporal y caja envolvente; aquí se
 *   aplica Haversine exacto y se cuentan unidades distintas. Un mismo conductor no puede fabricar
 *   tres reportes para auto-eximirse.
 * - Se incluye el reporte de la unidad evaluada: la regla cuenta unidades que confirman el evento,
 *   no excluye a la afectada.
 * - En caso de error de base de datos se devuelve un resultado **no exento** (fail-closed): nunca se
 *   condona una multa por un fallo técnico.
 */
class EvaluateForceMajeureUseCase(
    private val incidenceReportDao: IncidenceReportDao,
    private val nowUtcMs: () -> Long = System::currentTimeMillis,
    val minimumDistinctUnits: Int = 3,
    val radiusMeters: Double = 500.0,
    val windowMinutes: Int = 15,
    private val candidateLimit: Int = 250,
) {

    init {
        require(minimumDistinctUnits > 0) { "minimumDistinctUnits debe ser mayor que 0" }
        require(radiusMeters.isFinite() && radiusMeters > 0.0) { "radiusMeters debe ser positivo" }
        require(windowMinutes > 0) { "windowMinutes debe ser mayor que 0" }
        require(candidateLimit > 0) { "candidateLimit debe ser mayor que 0" }
    }

    /**
     * Evalúa la exención alrededor de [position] para la ruta [routeId].
     *
     * @param referenceTimeUtcMs Instante del cruce/reporte evaluado. Se parametriza para pruebas
     *   deterministas; en producción usa el reloj de pared.
     */
    suspend fun evaluate(
        routeId: String,
        position: GeoPoint,
        referenceTimeUtcMs: Long? = null,
    ): ForceMajeureResult {
        if (routeId.isBlank()) {
            return notExempt("No fue posible evaluar fuerza mayor: ruta vacía.")
        }
        if (!position.isValid) {
            return notExempt("No fue posible evaluar fuerza mayor: coordenada inválida.")
        }

        val reference = referenceTimeUtcMs ?: nowUtcMs()
        val since = reference - windowMinutes * MILLIS_PER_MINUTE
        val latitudeDelta = radiusMeters / GeoMath.metersPerDegreeLatitude(position.latitude)
        val longitudeDelta = radiusMeters / GeoMath.metersPerDegreeLongitude(position.latitude)

        val candidates = try {
            incidenceReportDao.getRecentForceMajeureCandidates(
                routeId = routeId,
                sinceUtcMs = since,
                minLatitude = (position.latitude - latitudeDelta).coerceIn(-90.0, 90.0),
                maxLatitude = (position.latitude + latitudeDelta).coerceIn(-90.0, 90.0),
                minLongitude = (position.longitude - longitudeDelta).coerceIn(-180.0, 180.0),
                maxLongitude = (position.longitude + longitudeDelta).coerceIn(-180.0, 180.0),
                limit = candidateLimit,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // Fail-closed: un error de lectura no debe condonar sanciones.
            return notExempt("No fue posible consultar las incidencias locales.")
        }

        return evaluateCandidates(
            routeId = routeId,
            position = position,
            referenceTimeUtcMs = reference,
            candidates = candidates,
        )
    }

    /** Evaluación pura y determinista sobre candidatos ya cargados (testeable sin Room). */
    internal fun evaluateCandidates(
        routeId: String,
        position: GeoPoint,
        referenceTimeUtcMs: Long,
        candidates: List<IncidenceReportEntity>,
    ): ForceMajeureResult {
        val since = referenceTimeUtcMs - windowMinutes * MILLIS_PER_MINUTE
        val valid = candidates.filter { report ->
            report.routeId == routeId &&
                report.timestampUtcMs in since..referenceTimeUtcMs &&
                report.unitId.isNotBlank() &&
                report.latitude.isFinite() &&
                report.longitude.isFinite() &&
                GeoMath.haversineMeters(
                    position,
                    GeoPoint(report.latitude, report.longitude),
                ) <= radiusMeters
        }

        val distinctUnits = TreeSet<String>().apply {
            valid.mapTo(this) { it.unitId.trim() }
        }
        val isExempt = distinctUnits.size >= minimumDistinctUnits
        val radiusLabel = radiusMeters.toInt()
        val summary = if (isExempt) {
            "Fuerza mayor verificada: ${distinctUnits.size} unidades distintas reportaron " +
                "tráfico o bloqueo en un radio de $radiusLabel m durante los últimos " +
                "$windowMinutes min."
        } else {
            "Sin exención: ${distinctUnits.size} de $minimumDistinctUnits unidades requeridas " +
                "reportaron tráfico o bloqueo en $radiusLabel m / $windowMinutes min."
        }

        return ForceMajeureResult(
            isExempt = isExempt,
            qualifyingUnitsCount = distinctUnits.size,
            distinctUnitIds = distinctUnits,
            radiusMeters = radiusMeters,
            windowMinutes = windowMinutes,
            matchingReportIds = valid.map { it.id }.sorted(),
            reasonSummary = summary,
        )
    }

    private fun notExempt(reason: String): ForceMajeureResult = ForceMajeureResult(
        isExempt = false,
        qualifyingUnitsCount = 0,
        distinctUnitIds = emptySet(),
        radiusMeters = radiusMeters,
        windowMinutes = windowMinutes,
        matchingReportIds = emptyList(),
        reasonSummary = reason.trim(),
    )

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
