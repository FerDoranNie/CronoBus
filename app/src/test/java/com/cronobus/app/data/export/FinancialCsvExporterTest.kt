package com.cronobus.app.data.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cronobus.app.domain.model.FinancialCsvRow
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FinancialCsvExporterTest {

    @Test
    fun `writes a real utf8 csv with escaped fields and crlf`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val exporter = FinancialCsvExporter(context) { 1_760_000_000_000L }
        val exportDir = File(context.cacheDir, "test_exports")

        val file = exporter.writeCsv(
            rows = listOf(
                FinancialCsvRow(
                    unitId = "14",
                    routeId = "Ruta 42, \"Centro\"",
                    saasFeeMxn = 750.0,
                    grossFinesMxn = 50.0,
                    forceMajeureForgivenMxn = 50.0,
                    netFinesMxn = 0.0,
                    punctualityBonusMxn = 45.0,
                ),
                FinancialCsvRow(
                    unitId = "9",
                    routeId = "Ruta 15",
                    saasFeeMxn = 750.0,
                    grossFinesMxn = 0.0,
                    forceMajeureForgivenMxn = 0.0,
                    netFinesMxn = 0.0,
                    punctualityBonusMxn = 45.0,
                ),
            ),
            exportDir = exportDir,
        )

        assertTrue(file.exists())
        assertTrue(file.name.startsWith("cronobus_finanzas_"))
        assertTrue(file.name.endsWith(".csv"))

        val text = file.readText(Charsets.UTF_8)
        assertTrue(text.startsWith("\uFEFFunit_id,route_id,"))
        assertTrue(text.contains("\r\n"))
        assertTrue(
            text.contains("14,\"Ruta 42, \"\"Centro\"\"\",750.00,50.00,50.00,0.00,45.00"),
        )
        assertTrue(text.contains("9,Ruta 15,750.00,0.00,0.00,0.00,45.00"))
        assertEquals(2, text.trim().lines().size - 1)
    }

    @Test
    fun `neutralizes spreadsheet formula injection in free text fields`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val exporter = FinancialCsvExporter(context) { 1_760_000_000_001L }

        val file = exporter.writeCsv(
            rows = listOf(
                FinancialCsvRow(
                    unitId = "=HYPERLINK(\"http://x\")",
                    routeId = "@SUM(A1)",
                    saasFeeMxn = 750.0,
                    grossFinesMxn = 0.0,
                    forceMajeureForgivenMxn = 0.0,
                    netFinesMxn = 0.0,
                    punctualityBonusMxn = 0.0,
                ),
            ),
            exportDir = File(context.cacheDir, "test_exports_injection"),
        )

        val dataLine = file.readText(Charsets.UTF_8).trim().lines()[1]
        assertTrue(dataLine.startsWith("\"'=HYPERLINK("))
        assertTrue(dataLine.contains(",'@SUM(A1),"))
    }
}
