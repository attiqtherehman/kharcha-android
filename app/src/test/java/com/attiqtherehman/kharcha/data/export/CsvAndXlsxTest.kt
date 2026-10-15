package com.attiqtherehman.kharcha.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.math.BigDecimal
import java.time.LocalDate
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class CsvAndXlsxTest {

    @Test
    fun csv_escapesCommasQuotesAndNewlines_andRoundTrips() {
        val rows = listOf(
            listOf("plain", "with, comma", "with \"quote\"", "multi\nline", " padded "),
            listOf("Urdu: کھانا", "", "a\r\nb", "=SUM(A1)", "-12.50"),
        )
        val out = ByteArrayOutputStream()
        val w = CsvWriter.open(out)
        rows.forEach { w.writeRow(it) }
        w.flush()
        val text = out.toString("UTF-8")
        assertTrue(text.startsWith(CsvWriter.BOM))
        assertTrue(text.contains("\"with, comma\""))
        assertTrue(text.contains("\"with \"\"quote\"\"\""))
        assertTrue(text.contains("'=SUM(A1)")) // formula neutralised
        assertTrue(text.contains(",-12.50")) // plain negative numbers untouched

        val reader = CsvReader(StringReader(text))
        val back = generateSequence { reader.readRow() }.toList()
        assertEquals(2, back.size)
        assertEquals(rows[0], back[0])
        assertEquals(listOf("Urdu: کھانا", "", "a\r\nb", "'=SUM(A1)", "-12.50"), back[1])
    }

    @Test
    fun csv_sanitisesEveryFormulaPrefix() {
        for (p in listOf("=", "+", "@", "\t", "\r")) assertTrue(CsvWriter.sanitize(p + "x").startsWith("'"))
        assertEquals("-5", CsvWriter.sanitize("-5"))
        assertEquals("-5.25", CsvWriter.sanitize("-5.25"))
        assertEquals("'-5x", CsvWriter.sanitize("-5x"))
        assertEquals("", CsvWriter.sanitize(""))
    }

    @Test
    fun csv_readerHandlesBomAndBlankLines() {
        val text = "﻿date,amount\r\n\r\n2026-10-15,100\n"
        val reader = CsvReader(StringReader(text))
        assertEquals(listOf("date", "amount"), reader.readRow())
        assertEquals(listOf("2026-10-15", "100"), reader.readRow())
        assertEquals(null, reader.readRow())
    }

    private val sheet = Sheet(
        "Transactions",
        listOf("date", "amount", "payee"),
        listOf(
            listOf(Cell.Date(LocalDate.of(2026, 10, 15)), Cell.Number(BigDecimal("1250.50")), Cell.Text("Café & <Bakery>")),
            listOf(Cell.Date(LocalDate.of(2026, 10, 16)), Cell.Number(BigDecimal("-20.00")), Cell.Empty),
        ),
    )

    /** Reads through the central directory (like Excel does); fastexcel omits sizes from local headers. */
    private fun entries(bytes: ByteArray): Map<String, String> {
        val tmp = File.createTempFile("kharcha-xlsx", ".zip").apply { deleteOnExit(); writeBytes(bytes) }
        return ZipFile(tmp).use { zip ->
            zip.entries().asSequence().associate { e -> e.name to zip.getInputStream(e).readBytes().toString(Charsets.UTF_8) }
        }
    }

    private fun assertWellFormedXml(xml: String) {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
        assertNotNull(doc.documentElement)
    }

    @Test
    fun minimalXlsx_isValidPackage_andReopens() {
        val out = ByteArrayOutputStream()
        MinimalXlsxWriter().write(out, listOf(sheet, Sheet("Accounts", listOf("name"), listOf(listOf(Cell.Text("Cash"))))))
        val files = entries(out.toByteArray())
        for (required in listOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml")) {
            assertTrue(required, files.containsKey(required))
            assertWellFormedXml(files.getValue(required))
        }
        val sheet1 = files.getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet1.contains("<v>1250.50</v>"))
        assertTrue(sheet1.contains("Café &amp; &lt;Bakery&gt;"))
        assertTrue(files.getValue("xl/workbook.xml").contains("name=\"Accounts\""))
    }

    @Test
    fun fastExcelXlsx_writesTypedCells_andReopens() {
        val out = ByteArrayOutputStream()
        FastExcelXlsxExporter().write(out, listOf(sheet))
        val files = entries(out.toByteArray())
        assertTrue(files.keys.any { it.startsWith("xl/worksheets/sheet") })
        val sheetXml = files.entries.first { it.key.startsWith("xl/worksheets/sheet") }.value
        assertWellFormedXml(sheetXml)
        assertTrue(sheetXml.contains("1250.5"))
        assertWellFormedXml(files.getValue("xl/workbook.xml"))
    }

    @Test
    fun selector_fallsBackGracefully() {
        val out = ByteArrayOutputStream()
        XlsxExporterSelector().write(out, listOf(sheet))
        assertTrue(entries(out.toByteArray()).containsKey("xl/workbook.xml"))
        StringWriter().close()
    }
}
