package com.attiqtherehman.kharcha.core.money

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatterTest {

    private val pkr = CurrencyInfo.PKR
    private val jpy = CurrencyInfo.of("JPY")
    private val kwd = CurrencyInfo.of("KWD")

    @Test
    fun format_standardGrouping() {
        assertEquals("Rs 0.00", MoneyFormatter.format(Money(0), pkr))
        assertEquals("Rs 0.05", MoneyFormatter.format(Money(5), pkr))
        assertEquals("Rs 1,250.00", MoneyFormatter.format(Money(125_000), pkr))
        assertEquals("Rs 1,250,000.75", MoneyFormatter.format(Money(125_000_075), pkr))
        assertEquals("-Rs 1,250.00", MoneyFormatter.format(Money(-125_000), pkr))
    }

    @Test
    fun format_southAsianGrouping() {
        assertEquals("Rs 12,50,000.00", MoneyFormatter.format(Money(125_000_000), pkr, NumberGrouping.SOUTH_ASIAN))
        assertEquals("Rs 1,00,00,000.00", MoneyFormatter.format(Money(1_000_000_000), pkr, NumberGrouping.SOUTH_ASIAN))
        assertEquals("Rs 999.00", MoneyFormatter.format(Money(99_900), pkr, NumberGrouping.SOUTH_ASIAN))
        assertEquals("Rs 1,000.00", MoneyFormatter.format(Money(100_000), pkr, NumberGrouping.SOUTH_ASIAN))
    }

    @Test
    fun format_respectsCurrencyPrecision() {
        assertEquals("¥ 1,250", MoneyFormatter.format(Money(1_250), jpy))
        assertEquals("KWD 1.250", MoneyFormatter.format(Money(1_250), kwd))
    }

    @Test
    fun format_options() {
        assertEquals("1,250.00", MoneyFormatter.format(Money(125_000), pkr, withSymbol = false))
        assertEquals("+Rs 1,250.00", MoneyFormatter.format(Money(125_000), pkr, signed = true))
        assertEquals("Rs 1,250", MoneyFormatter.format(Money(125_000), pkr, hideZeroFraction = true))
        assertEquals("Rs 1,250.50", MoneyFormatter.format(Money(125_050), pkr, hideZeroFraction = true))
    }

    @Test
    fun toPlainDecimal_forExports() {
        assertEquals("-1250.50", MoneyFormatter.toPlainDecimal(Money(-125_050), 2))
        assertEquals("0.00", MoneyFormatter.toPlainDecimal(Money(0), 2))
        assertEquals("1250", MoneyFormatter.toPlainDecimal(Money(1_250), 0))
    }

    @Test
    fun formatCompact() {
        assertEquals("Rs 950", MoneyFormatter.formatCompact(Money(95_000), pkr))
        assertEquals("Rs 1.2K", MoneyFormatter.formatCompact(Money(125_000), pkr))
        assertEquals("Rs 2M", MoneyFormatter.formatCompact(Money(200_000_000), pkr))
        assertEquals("Rs 12.5L", MoneyFormatter.formatCompact(Money(125_000_000), pkr, NumberGrouping.SOUTH_ASIAN))
        assertEquals("Rs 2.1Cr", MoneyFormatter.formatCompact(Money(2_100_000_000), pkr, NumberGrouping.SOUTH_ASIAN))
        assertEquals("-Rs 1.2K", MoneyFormatter.formatCompact(Money(-125_000), pkr))
    }
}
