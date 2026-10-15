package com.attiqtherehman.kharcha.core.money

import kotlin.math.abs

object MoneyFormatter {

    /**
     * Formats [money] as e.g. "Rs 1,250.00", "-Rs 1,250.00" or (signed) "+Rs 1,250.00".
     * Never uses floating point; works directly on the minor-unit digits.
     */
    fun format(
        money: Money,
        currency: CurrencyInfo,
        grouping: NumberGrouping = NumberGrouping.STANDARD,
        withSymbol: Boolean = true,
        signed: Boolean = false,
        hideZeroFraction: Boolean = false,
    ): String {
        val negative = money.minor < 0L
        val number = formatNumber(money, currency.minorDigits, grouping, hideZeroFraction)
        val sign = when {
            negative -> "-"
            signed && money.minor > 0L -> "+"
            else -> ""
        }
        return if (withSymbol) "$sign${currency.symbol} $number" else "$sign$number"
    }

    /** Unsigned magnitude as a plain number string with grouping, e.g. "1,250.00". */
    fun formatNumber(
        money: Money,
        minorDigits: Int,
        grouping: NumberGrouping = NumberGrouping.STANDARD,
        hideZeroFraction: Boolean = false,
    ): String {
        val absMinor = if (money.minor == Long.MIN_VALUE) Long.MAX_VALUE else abs(money.minor)
        val digits = absMinor.toString()
        val intPart: String
        val fracPart: String
        if (minorDigits == 0) {
            intPart = digits
            fracPart = ""
        } else {
            val padded = digits.padStart(minorDigits + 1, '0')
            intPart = padded.dropLast(minorDigits)
            fracPart = padded.takeLast(minorDigits)
        }
        val grouped = group(intPart, grouping)
        return if (fracPart.isEmpty() || (hideZeroFraction && fracPart.all { it == '0' })) grouped else "$grouped.$fracPart"
    }

    /** Plain decimal for CSV/XLSX, no grouping, always full precision, e.g. "-1250.00". */
    fun toPlainDecimal(money: Money, minorDigits: Int): String {
        val sign = if (money.minor < 0L) "-" else ""
        return sign + formatNumber(money, minorDigits, NumberGrouping.STANDARD, hideZeroFraction = false).replace(",", "")
    }

    /** Short form for charts/widgets: "Rs 1.2K", "Rs 12.5L", "Rs 2.1Cr" (South Asian) or K/M/B (standard). */
    fun formatCompact(money: Money, currency: CurrencyInfo, grouping: NumberGrouping = NumberGrouping.STANDARD): String {
        val negative = money.minor < 0L
        val absMinor = if (money.minor == Long.MIN_VALUE) Long.MAX_VALUE else abs(money.minor)
        val major = absMinor / pow10(currency.minorDigits)
        val (value, suffix) = when (grouping) {
            NumberGrouping.SOUTH_ASIAN -> when {
                major >= 10_000_000L -> scaled(major, 10_000_000L) to "Cr"
                major >= 100_000L -> scaled(major, 100_000L) to "L"
                major >= 1_000L -> scaled(major, 1_000L) to "K"
                else -> major.toString() to ""
            }
            NumberGrouping.STANDARD -> when {
                major >= 1_000_000_000L -> scaled(major, 1_000_000_000L) to "B"
                major >= 1_000_000L -> scaled(major, 1_000_000L) to "M"
                major >= 1_000L -> scaled(major, 1_000L) to "K"
                else -> major.toString() to ""
            }
        }
        val sign = if (negative) "-" else ""
        return "$sign${currency.symbol} $value$suffix"
    }

    /** One decimal place, dropping a trailing ".0": 1250/1000 -> "1.2", 2000/1000 -> "2". */
    private fun scaled(value: Long, unit: Long): String {
        val whole = value / unit
        val tenth = (value % unit) * 10 / unit
        return if (tenth == 0L) whole.toString() else "$whole.$tenth"
    }

    fun group(intPart: String, grouping: NumberGrouping): String {
        if (intPart.length <= 3) return intPart
        return when (grouping) {
            NumberGrouping.STANDARD -> groupEvery(intPart, 3)
            NumberGrouping.SOUTH_ASIAN -> {
                val head = intPart.dropLast(3)
                val tail = intPart.takeLast(3)
                groupEvery(head, 2) + "," + tail
            }
        }
    }

    private fun groupEvery(s: String, size: Int): String {
        val sb = StringBuilder()
        val firstGroup = s.length % size
        var i = 0
        if (firstGroup > 0) {
            sb.append(s, 0, firstGroup)
            i = firstGroup
        }
        while (i < s.length) {
            if (sb.isNotEmpty()) sb.append(',')
            sb.append(s, i, i + size)
            i += size
        }
        return sb.toString()
    }

    private fun pow10(n: Int): Long {
        var r = 1L
        repeat(n) { r *= 10L }
        return r
    }
}
