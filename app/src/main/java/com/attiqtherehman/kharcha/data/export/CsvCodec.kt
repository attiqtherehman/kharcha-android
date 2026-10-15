package com.attiqtherehman.kharcha.data.export

import java.io.BufferedReader
import java.io.OutputStream
import java.io.Reader
import java.io.Writer

/**
 * RFC 4180 CSV writer: UTF-8 with BOM (so Excel on Windows decodes Urdu/English names correctly),
 * CRLF line ends, quoting of fields containing separators, quotes or line breaks, and
 * spreadsheet formula-injection protection for text that starts with = + - @ tab or CR.
 */
class CsvWriter(private val writer: Writer) {

    fun writeRow(fields: List<String?>) {
        val line = fields.joinToString(",") { encode(it) }
        writer.write(line)
        writer.write("\r\n")
    }

    fun flush() = writer.flush()

    companion object {
        const val BOM = "﻿"

        fun open(out: OutputStream): CsvWriter {
            val w = out.bufferedWriter(Charsets.UTF_8)
            w.write(BOM)
            return CsvWriter(w)
        }

        fun encode(raw: String?): String {
            if (raw == null) return ""
            val value = sanitize(raw)
            val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || value.startsWith(" ") || value.endsWith(" ")
            return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
        }

        /** Prefix formula-like text with an apostrophe so spreadsheet apps show it as text. */
        fun sanitize(value: String): String {
            if (value.isEmpty()) return value
            val first = value[0]
            val formulaLike = first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r'
            // Plain negative numbers are data, not formulas.
            return if (formulaLike && !value.isPlainNumber()) "'$value" else value
        }

        private fun String.isPlainNumber(): Boolean = Regex("""^-?\d+(\.\d+)?$""").matches(this)
    }
}

/** RFC 4180 CSV reader supporting quoted fields with embedded commas, quotes and line breaks. */
class CsvReader(reader: Reader) {
    private val input: BufferedReader = reader as? BufferedReader ?: BufferedReader(reader)
    private var first = true

    /** Returns the next record, or null at end of input. Blank lines are skipped. */
    fun readRow(): List<String>? {
        while (true) {
            val row = readRecord() ?: return null
            if (first) {
                first = false
                if (row.isNotEmpty()) row[0] = row[0].removePrefix(CsvWriter.BOM)
            }
            if (row.size == 1 && row[0].isBlank()) continue
            return row
        }
    }

    private fun readRecord(): MutableList<String>? {
        val fields = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var sawAny = false
        while (true) {
            val c = input.read()
            if (c == -1) {
                if (!sawAny) return null
                fields += field.toString()
                return fields
            }
            sawAny = true
            val ch = c.toChar()
            if (inQuotes) {
                if (ch == '"') {
                    input.mark(1)
                    val next = input.read()
                    if (next == '"'.code) {
                        field.append('"')
                    } else {
                        inQuotes = false
                        if (next != -1) input.reset()
                    }
                } else {
                    field.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> { fields += field.toString(); field.setLength(0) }
                    '\r' -> {
                        input.mark(1)
                        val next = input.read()
                        if (next != '\n'.code && next != -1) input.reset()
                        fields += field.toString()
                        return fields
                    }
                    '\n' -> { fields += field.toString(); return fields }
                    else -> field.append(ch)
                }
            }
        }
    }
}
