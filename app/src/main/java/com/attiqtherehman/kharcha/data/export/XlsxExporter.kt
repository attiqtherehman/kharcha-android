package com.attiqtherehman.kharcha.data.export

import org.dhatim.fastexcel.Workbook
import java.io.OutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A typed cell so spreadsheets get real numbers and dates, not text. */
sealed interface Cell {
    data class Text(val value: String) : Cell
    data class Number(val value: BigDecimal) : Cell
    data class Date(val value: LocalDate) : Cell
    data object Empty : Cell
}

data class Sheet(val name: String, val headers: List<String>, val rows: List<List<Cell>>)

interface XlsxExporter {
    fun write(out: OutputStream, sheets: List<Sheet>)
}

/** Primary implementation using fastexcel (pure JVM, no POI). */
class FastExcelXlsxExporter : XlsxExporter {
    override fun write(out: OutputStream, sheets: List<Sheet>) {
        Workbook(out, "Kharcha", "1.0").use { wb ->
            sheets.forEach { sheet ->
                val ws = wb.newWorksheet(sheet.name.take(31).ifEmpty { "Sheet" })
                sheet.headers.forEachIndexed { c, h -> ws.value(0, c, h); ws.style(0, c).bold().set() }
                sheet.rows.forEachIndexed { r, row ->
                    row.forEachIndexed { c, cell ->
                        when (cell) {
                            is Cell.Text -> ws.value(r + 1, c, cell.value)
                            is Cell.Number -> { ws.value(r + 1, c, cell.value); ws.style(r + 1, c).format("#,##0.00").set() }
                            is Cell.Date -> { ws.value(r + 1, c, cell.value); ws.style(r + 1, c).format("yyyy-mm-dd").set() }
                            Cell.Empty -> Unit
                        }
                    }
                }
                sheet.headers.indices.forEach { c -> ws.width(c, if (c == 0) 18.0 else 16.0) }
            }
        }
    }
}

/**
 * Dependency-free fallback that writes a valid minimal XLSX (inline strings, ISO dates as text)
 * if the fastexcel classes fail to link on a device.
 */
class MinimalXlsxWriter : XlsxExporter {
    override fun write(out: OutputStream, sheets: List<Sheet>) {
        val zip = ZipOutputStream(out)
        fun entry(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        val n = sheets.size.coerceAtLeast(1)
        entry(
            "[Content_Types].xml",
            buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
                append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/>""")
                append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
                for (i in 1..n) append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
                append("</Types>")
            },
        )
        entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
        entry(
            "xl/workbook.xml",
            buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
                sheets.ifEmpty { listOf(Sheet("Sheet", emptyList(), emptyList())) }.forEachIndexed { i, s -> append("""<sheet name="${escape(s.name.take(31))}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""") }
                append("</sheets></workbook>")
            },
        )
        entry(
            "xl/_rels/workbook.xml.rels",
            buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                for (i in 1..n) append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
                append("</Relationships>")
            },
        )
        sheets.ifEmpty { listOf(Sheet("Sheet", emptyList(), emptyList())) }.forEachIndexed { i, sheet ->
            entry(
                "xl/worksheets/sheet${i + 1}.xml",
                buildString {
                    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
                    fun row(r: Int, cells: List<Cell>) {
                        append("<row r=\"$r\">")
                        cells.forEachIndexed { c, cell ->
                            val ref = "${columnName(c)}$r"
                            when (cell) {
                                is Cell.Text -> append("""<c r="$ref" t="inlineStr"><is><t>${escape(cell.value)}</t></is></c>""")
                                is Cell.Number -> append("""<c r="$ref"><v>${cell.value.toPlainString()}</v></c>""")
                                is Cell.Date -> append("""<c r="$ref" t="inlineStr"><is><t>${cell.value}</t></is></c>""")
                                Cell.Empty -> Unit
                            }
                        }
                        append("</row>")
                    }
                    row(1, sheet.headers.map { Cell.Text(it) })
                    sheet.rows.forEachIndexed { r, cells -> row(r + 2, cells) }
                    append("</sheetData></worksheet>")
                },
            )
        }
        zip.finish()
        zip.flush()
    }

    private fun columnName(index: Int): String {
        var i = index
        val sb = StringBuilder()
        do {
            sb.insert(0, ('A' + i % 26))
            i = i / 26 - 1
        } while (i >= 0)
        return sb.toString()
    }

    private fun escape(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

/** Uses fastexcel; falls back to the minimal writer if the library cannot be loaded at runtime. */
class XlsxExporterSelector : XlsxExporter {
    @Volatile private var delegate: XlsxExporter? = null

    override fun write(out: OutputStream, sheets: List<Sheet>) {
        val chosen = delegate ?: resolve().also { delegate = it }
        chosen.write(out, sheets)
    }

    private fun resolve(): XlsxExporter = try {
        Class.forName("org.dhatim.fastexcel.Workbook")
        FastExcelXlsxExporter()
    } catch (e: Throwable) {
        MinimalXlsxWriter()
    }
}
