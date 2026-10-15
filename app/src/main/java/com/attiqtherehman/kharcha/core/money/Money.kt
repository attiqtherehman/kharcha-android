package com.attiqtherehman.kharcha.core.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * An amount in integer minor units (paisa for PKR). Arithmetic is exact and throws on overflow
 * rather than wrapping. The currency is carried by the owning account/setting, not here.
 */
@JvmInline
value class Money(val minor: Long) : Comparable<Money> {

    operator fun plus(other: Money): Money = Money(Math.addExact(minor, other.minor))
    operator fun minus(other: Money): Money = Money(Math.subtractExact(minor, other.minor))
    operator fun unaryMinus(): Money = Money(Math.negateExact(minor))
    operator fun times(factor: Int): Money = Money(Math.multiplyExact(minor, factor.toLong()))

    val isZero: Boolean get() = minor == 0L
    val isNegative: Boolean get() = minor < 0L
    val isPositive: Boolean get() = minor > 0L

    fun abs(): Money = if (minor < 0L) -this else this

    override fun compareTo(other: Money): Int = minor.compareTo(other.minor)

    fun toBigDecimal(minorDigits: Int): BigDecimal =
        BigDecimal.valueOf(minor).movePointLeft(minorDigits)

    /** Ratio of this to [total]; 0 when total is zero. */
    fun ratioTo(total: Money): Float =
        if (total.minor == 0L) 0f else (minor.toDouble() / total.minor.toDouble()).toFloat()

    companion object {
        val ZERO = Money(0L)

        /** Exact conversion; throws [ArithmeticException] when [major] has more precision than allowed. */
        fun fromMajor(major: BigDecimal, minorDigits: Int): Money =
            Money(major.setScale(minorDigits, RoundingMode.UNNECESSARY).movePointRight(minorDigits).longValueExact())

        fun fromMajor(major: Long, minorDigits: Int): Money =
            fromMajor(BigDecimal.valueOf(major), minorDigits)
    }
}

fun Iterable<Money>.sum(): Money = fold(Money.ZERO) { acc, m -> acc + m }

inline fun <T> Iterable<T>.sumOfMoney(selector: (T) -> Money): Money {
    var acc = Money.ZERO
    for (item in this) acc += selector(item)
    return acc
}

fun maxOf(a: Money, b: Money): Money = if (a >= b) a else b
fun minOf(a: Money, b: Money): Money = if (a <= b) a else b
