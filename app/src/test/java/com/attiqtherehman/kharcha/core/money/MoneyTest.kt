package com.attiqtherehman.kharcha.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class MoneyTest {

    @Test
    fun arithmetic_isExactOnLongs() {
        assertEquals(Money(300), Money(100) + Money(200))
        assertEquals(Money(-100), Money(100) - Money(200))
        assertEquals(Money(-100), -Money(100))
        assertEquals(Money(500), Money(100) * 5)
        assertEquals(Money(100), Money(-100).abs())
    }

    @Test
    fun arithmetic_overflowsLoudly() {
        assertThrows(ArithmeticException::class.java) { Money(Long.MAX_VALUE) + Money(1) }
        assertThrows(ArithmeticException::class.java) { Money(Long.MIN_VALUE) - Money(1) }
        assertThrows(ArithmeticException::class.java) { Money(Long.MAX_VALUE) * 2 }
    }

    @Test
    fun fromMajor_convertsExactly() {
        assertEquals(Money(125_050), Money.fromMajor(BigDecimal("1250.50"), 2))
        assertEquals(Money(1250), Money.fromMajor(BigDecimal("1250"), 0))
        assertEquals(Money(1_250_000), Money.fromMajor(BigDecimal("1250"), 3))
        assertEquals(Money(-2_000), Money.fromMajor(BigDecimal("-20"), 2))
    }

    @Test
    fun fromMajor_rejectsMorePrecisionThanCurrencyAllows() {
        assertThrows(ArithmeticException::class.java) { Money.fromMajor(BigDecimal("10.005"), 2) }
        assertThrows(ArithmeticException::class.java) { Money.fromMajor(BigDecimal("1.5"), 0) }
    }

    @Test
    fun toBigDecimal_roundTrips() {
        val values = listOf(0L, 1L, -1L, 99L, 100L, 123_456_789L, -987_654_321L)
        for (v in values) {
            val m = Money(v)
            assertEquals(m, Money.fromMajor(m.toBigDecimal(2), 2))
        }
    }

    @Test
    fun comparisons_andFlags() {
        assertTrue(Money(1) > Money.ZERO)
        assertTrue(Money(-1).isNegative)
        assertTrue(Money(0).isZero)
        assertTrue(Money(5).isPositive)
        assertEquals(Money(5), maxOf(Money(5), Money(3)))
        assertEquals(Money(3), minOf(Money(5), Money(3)))
        assertEquals(Money(6), listOf(Money(1), Money(2), Money(3)).sum())
    }

    @Test
    fun ratioTo_handlesZeroTotal() {
        assertEquals(0f, Money(10).ratioTo(Money.ZERO), 0f)
        assertEquals(0.25f, Money(25).ratioTo(Money(100)), 0.0001f)
    }
}
