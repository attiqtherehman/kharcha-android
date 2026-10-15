package com.attiqtherehman.kharcha.core.datetime

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class RelativeDay { TODAY, YESTERDAY, TOMORROW }

/** Formatting helpers. Relative labels (Today/Yesterday) are resolved to strings by the UI layer. */
object DateFormats {
    private val locale: Locale get() = Locale.getDefault()

    private val medium by lazy { DateTimeFormatter.ofPattern("d MMM yyyy", locale) }
    private val dayShort by lazy { DateTimeFormatter.ofPattern("EEE, d MMM", locale) }
    private val dayShortWithYear by lazy { DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale) }
    private val monthYearFormat by lazy { DateTimeFormatter.ofPattern("MMMM yyyy", locale) }
    private val monthShortFormat by lazy { DateTimeFormatter.ofPattern("MMM", locale) }
    private val time12 by lazy { DateTimeFormatter.ofPattern("h:mm a", locale) }

    fun medium(date: LocalDate): String = medium.format(date)

    fun relativeDay(date: LocalDate, today: LocalDate): RelativeDay? = when (date) {
        today -> RelativeDay.TODAY
        today.minusDays(1) -> RelativeDay.YESTERDAY
        today.plusDays(1) -> RelativeDay.TOMORROW
        else -> null
    }

    /** "Wed, 15 Oct" within the current year, otherwise "Wed, 15 Oct 2025". */
    fun dayHeader(date: LocalDate, today: LocalDate): String =
        if (date.year == today.year) dayShort.format(date) else dayShortWithYear.format(date)

    fun monthYear(month: YearMonth): String = monthYearFormat.format(month)
    fun monthShort(month: YearMonth): String = monthShortFormat.format(month)
    fun time(time: LocalTime): String = time12.format(time)

    /** Stable ISO forms for storage and export. */
    fun iso(date: LocalDate): String = date.toString()
    fun isoTime(time: LocalTime): String = time.withSecond(0).withNano(0).toString().take(5)
}
