package com.attiqtherehman.kharcha.core.datetime

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Inclusive date range used by every filter, report and budget query. */
data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    init {
        require(!endInclusive.isBefore(start)) { "end $endInclusive is before start $start" }
    }

    val endExclusive: LocalDate get() = endInclusive.plusDays(1)
    val dayCount: Long get() = ChronoUnit.DAYS.between(start, endExclusive)

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(endInclusive)

    /** The range of equal length immediately before this one. */
    fun previous(): DateRange = DateRange(start.minusDays(dayCount), start.minusDays(1))

    /** SQL-friendly bounds: [startKey, endExclusiveKey) on the yyyy-MM-dd text column. */
    val sqlStart: String get() = start.toString()
    val sqlEndExclusive: String get() = endExclusive.toString()

    companion object {
        fun ofDay(day: LocalDate) = DateRange(day, day)
        fun ofMonth(month: YearMonth) = DateRange(month.atDay(1), month.atEndOfMonth())
        fun ofYear(year: Int) = DateRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
        fun ofWeek(anyDay: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): DateRange {
            val start = anyDay.with(TemporalAdjusters.previousOrSame(weekStart))
            return DateRange(start, start.plusDays(6))
        }
        fun lastNDays(today: LocalDate, days: Int) = DateRange(today.minusDays((days - 1).toLong()), today)
    }
}

enum class PeriodPreset { TODAY, THIS_WEEK, THIS_MONTH, LAST_MONTH, THIS_YEAR, CUSTOM }

fun PeriodPreset.toRange(today: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): DateRange? = when (this) {
    PeriodPreset.TODAY -> DateRange.ofDay(today)
    PeriodPreset.THIS_WEEK -> DateRange.ofWeek(today, weekStart)
    PeriodPreset.THIS_MONTH -> DateRange.ofMonth(YearMonth.from(today))
    PeriodPreset.LAST_MONTH -> DateRange.ofMonth(YearMonth.from(today).minusMonths(1))
    PeriodPreset.THIS_YEAR -> DateRange.ofYear(today.year)
    PeriodPreset.CUSTOM -> null
}

/** Stable "yyyy-MM" key used in budget tables and backups. */
fun YearMonth.key(): String = toString()
fun yearMonthFromKey(key: String): YearMonth = YearMonth.parse(key)
fun YearMonth.range(): DateRange = DateRange.ofMonth(this)
