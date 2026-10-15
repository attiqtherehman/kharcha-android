package com.attiqtherehman.kharcha.domain.recurrence

import com.attiqtherehman.kharcha.domain.model.Frequency
import com.attiqtherehman.kharcha.domain.model.Recurrence
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Pure date math for recurring transactions and reminders. Every occurrence is computed directly
 * from the anchor (`anchor + n * interval`), which is what makes month-end clamping stable:
 * Jan 31 -> Feb 28 -> Mar 31 -> Apr 30, never drifting to the 28th for good.
 */
object RecurrenceMath {

    fun occurrence(r: Recurrence, n: Int): LocalDate {
        require(n >= 0) { "n must be >= 0" }
        val steps = n.toLong() * r.interval
        return when (r.frequency) {
            Frequency.DAILY -> r.anchorDate.plusDays(steps)
            Frequency.WEEKLY -> r.anchorDate.plusWeeks(steps)
            Frequency.MONTHLY -> r.anchorDate.plusMonths(steps)
            Frequency.YEARLY -> r.anchorDate.plusYears(steps)
        }
    }

    /**
     * Occurrences strictly after [afterExclusive] and on or before [untilInclusive], honouring the
     * rule's end date. [limit] guards against runaway generation.
     */
    fun occurrencesBetween(
        r: Recurrence,
        afterExclusive: LocalDate,
        untilInclusive: LocalDate,
        limit: Int = 1000,
    ): List<LocalDate> {
        val out = ArrayList<LocalDate>()
        val last = r.endDate?.let { if (it.isBefore(untilInclusive)) it else untilInclusive } ?: untilInclusive
        var n = startIndex(r, afterExclusive)
        while (out.size < limit) {
            val d = occurrence(r, n)
            if (d.isAfter(last)) break
            if (d.isAfter(afterExclusive)) out += d
            n++
        }
        return out
    }

    /** The first occurrence strictly after [date], or null when the rule has ended. */
    fun nextAfter(r: Recurrence, date: LocalDate): LocalDate? {
        var n = startIndex(r, date)
        while (true) {
            val d = occurrence(r, n)
            if (r.endDate != null && d.isAfter(r.endDate)) return null
            if (d.isAfter(date)) return d
            n++
        }
    }

    /** The first occurrence on or after [date], or null when the rule has ended. */
    fun nextOnOrAfter(r: Recurrence, date: LocalDate): LocalDate? = nextAfter(r, date.minusDays(1))

    /** True when [date] is exactly an occurrence of the rule. */
    fun isOccurrence(r: Recurrence, date: LocalDate): Boolean = nextOnOrAfter(r, date) == date

    /** A safe lower bound for the index of the first occurrence after [after]. */
    private fun startIndex(r: Recurrence, after: LocalDate): Int {
        if (!after.isAfter(r.anchorDate)) return 0
        val units = when (r.frequency) {
            Frequency.DAILY -> ChronoUnit.DAYS.between(r.anchorDate, after)
            Frequency.WEEKLY -> ChronoUnit.WEEKS.between(r.anchorDate, after)
            Frequency.MONTHLY -> ChronoUnit.MONTHS.between(r.anchorDate, after)
            Frequency.YEARLY -> ChronoUnit.YEARS.between(r.anchorDate, after)
        }
        val estimate = units / r.interval - 1
        return estimate.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }
}
