package com.cronobus.app.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.cronobus.app.domain.model.FinancialCsvRow
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resultado de una exportación financiera.
 *
 * @param uri URI `content://` emitida por [FileProvider]; nunca se expone una ruta absoluta a JS.
 * @param fileName Nombre visible para el usuario.
 * @param rowCount Filas de datos exportadas (sin contar el encabezado).
 */
data class FinancialCsvExport(
    val uri: Uri,
    val fileName: String,
    val rowCount: Int,
)

/**
 * Genera el CSV del Modelo Financiero Mixto (SaaS + Multas + Bonos − Condonaciones).
 *
 * Formato:
 * - UTF-8 con BOM para que Excel en Windows abra los acentos correctamente.
 * - Fin de línea CRLF.
 * - Escapado RFC 4180 (comillas dobles duplicadas, campos con coma/comilla/salto entrecomillados).
 * - Números con punto decimal y dos cifras, sin separador de miles.
 *
 * El archivo se escribe en `cache/exports/`, ruta declarada en `res/xml/file_paths.xml`, y se
 * comparte con `FileProvider` para que ninguna app externa reciba un `file://`.
 */
class FinancialCsvExporter(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val appContext = context.applicationContext

    suspend fun export(rows: List<FinancialCsvRow>): FinancialCsvExport = withContext(Dispatchers.IO) {
        val file = writeCsv(rows, exportDirectory())

        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}$FILE_PROVIDER_SUFFIX",
            file,
        )

        FinancialCsvExport(
            uri = uri,
            fileName = file.name,
            rowCount = rows.size,
        )
    }

    /**
     * Escribe el CSV en [exportDir]. Se separa de [export] para poder verificar el formato en
     * pruebas JVM sin depender de la resolución de raíces de `FileProvider` de Robolectric.
     */
    internal fun writeCsv(rows: List<FinancialCsvRow>, exportDir: File): File {
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            error("No se pudo crear el directorio de exportaciones.")
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(clock()))
        val file = File(exportDir, "cronobus_finanzas_$timestamp.csv")

        BufferedWriter(OutputStreamWriter(file.outputStream(), Charsets.UTF_8)).use { writer ->
            writer.write(UTF8_BOM)
            writer.write(HEADER.joinToString(separator = ","))
            writer.write(CRLF)

            rows.forEach { row ->
                writer.write(
                    listOf(
                        escape(row.unitId),
                        escape(row.routeId),
                        formatMoney(row.saasFeeMxn),
                        formatMoney(row.grossFinesMxn),
                        formatMoney(row.forceMajeureForgivenMxn),
                        formatMoney(row.netFinesMxn),
                        formatMoney(row.punctualityBonusMxn),
                    ).joinToString(separator = ","),
                )
                writer.write(CRLF)
            }
        }

        return file
    }

    private fun exportDirectory(): File =
        File(appContext.cacheDir, EXPORT_DIRECTORY).apply {
            if (!exists() && !mkdirs()) {
                error("No se pudo crear el directorio de exportaciones.")
            }
        }

    private fun formatMoney(value: Double): String {
        val safe = if (value.isFinite()) value else 0.0
        return String.format(Locale.US, "%.2f", safe)
    }

    /**
     * Auditoría Paso 3 (OWASP CSV Injection): `unitId` y `routeId` vienen de texto libre del
     * conductor. Un valor como `=HYPERLINK(...)` se ejecutaría como fórmula en Excel/Sheets.
     */
    internal fun neutralizeFormula(value: String): String =
        if (value.isNotEmpty() && value[0] in FORMULA_PREFIXES) "'$value" else value

    private fun escape(raw: String): String {
        val value = neutralizeFormula(raw)
        val needsQuoting = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuoting) "\"$escaped\"" else escaped
    }

    private companion object {
        const val EXPORT_DIRECTORY = "exports"
        const val FILE_PROVIDER_SUFFIX = ".fileprovider"
        const val UTF8_BOM = "\uFEFF"
        const val CRLF = "\r\n"
        val FORMULA_PREFIXES = charArrayOf('=', '+', '-', '@', '\t', '\r')

        val HEADER = listOf(
            "unit_id",
            "route_id",
            "saas_fee_mxn",
            "gross_fines_mxn",
            "force_majeure_forgiven_mxn",
            "net_fines_mxn",
            "punctuality_bonus_mxn",
        )
    }
}
