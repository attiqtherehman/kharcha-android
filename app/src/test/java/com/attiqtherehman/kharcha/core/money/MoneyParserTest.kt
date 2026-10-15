package com.attiqtherehman.kharcha.core.money

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyParserTest {

    private val pkr = CurrencyInfo.PKR

    private fun ok(raw: String, minor: Long) =
        assertEquals(MoneyParseResult.Success(Money(minor)), MoneyParser.parse(raw, pkr))

    private fun fail(raw: String, reason: MoneyParseResult.Reason) =
        assertEquals(MoneyParseResult.Failure(reason), MoneyParser.parse(raw, pkr))

    @Test
    fun parsesCommonInputs() {
        ok("1250", 125_000)
        ok("1,250.50", 125_050)
        ok("Rs 1250", 125_000)
        ok("PKR 1,250", 125_000)
        ok("-20", -2_000)
        ok("+20", 2_000)
        ok(".5", 50)
        ok("1.", 100)
        ok("  7.25 ", 725)
        ok("0", 0)
    }

    @Test
    fun rejectsExtraPrecisionInsteadOfRounding() {
        fail("10.005", MoneyParseResult.Reason.TOO_MANY_DECIMALS)
        fail("1.999", MoneyParseResult.Reason.TOO_MANY_DECIMALS)
        ok("10.500", 1_050) // trailing zeros are not extra precision
    }

    @Test
    fun rejectsGarbage() {
        fail("", MoneyParseResult.Reason.EMPTY)
        fail("   ", MoneyParseResult.Reason.EMPTY)
        fail("-", MoneyParseResult.Reason.EMPTY)
        fail("abc", MoneyParseResult.Reason.INVALID)
        fail("1.2.3", MoneyParseResult.Reason.INVALID)
        fail("12e3", MoneyParseResult.Reason.INVALID)
        fail("99999999999999999999", MoneyParseResult.Reason.TOO_LARGE)
    }

    @Test
    fun zeroDecimalCurrency() {
        val jpy = CurrencyInfo.of("JPY")
        assertEquals(MoneyParseResult.Success(Money(1_250)), MoneyParser.parse("1,250", jpy))
        assertEquals(MoneyParseResult.Failure(MoneyParseResult.Reason.TOO_MANY_DECIMALS), MoneyParser.parse("1.5", jpy))
    }
}
