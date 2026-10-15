package com.attiqtherehman.kharcha.domain.recurrence

import com.attiqtherehman.kharcha.domain.model.Frequency
import com.attiqtherehman.kharcha.domain.model.Recurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurrenceMathTest {

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test
    fun monthly31st_clampsWithoutDrift() {
        val r = Recurrence(Frequency.MONTHLY, anchorDate = d(2026, 1, 31))
        assertEquals(d(2026, 1, 31), RecurrenceMath.occurrence(r, 0))
        assertEquals(d(2026, 2, 28), RecurrenceMath.occurrence(r, 1))
        assertEquals(d(2026, 3, 31), RecurrenceMath.occurrence(r, 2))
        assertEquals(d(2026, 4, 30), RecurrenceMath.occurrence(r, 3))
        assertEquals(d(2028, 2, 29), RecurrenceMath.occurrence(r, 25))
    }

    @Test
    fun yearlyFeb29_clampsOffLeapYears() {
        val r = Recurrence(Frequency.YEARLY, anchorDate = d(2024, 2, 29))
        assertEquals(d(2025, 2, 28), RecurrenceMath.occurrence(r, 1))
        assertEquals(d(2028, 2, 29), RecurrenceMath.occurrence(r, 4))
    }

    @Test
    fun intervals_everyNDaysAndMonths() {
        val every3Days = Recurrence(Frequency.DAILY, interval = 3, anchorDate = d(2026, 10, 1))
        assertEquals(listOf(d(2026, 10, 4), d(2026, 10, 7), d(2026, 10, 10)), RecurrenceMath.occurrencesBetween(every3Days, d(2026, 10, 1), d(2026, 10, 10)))
        val quarterly = Recurrence(Frequency.MONTHLY, interval = 3, anchorDate = d(2026, 1, 15))
        assertEquals(listOf(d(2026, 4, 15), d(2026, 7, 15), d(2026, 10, 15)), RecurrenceMath.occurrencesBetween(quarterly, d(2026, 1, 15), d(2026, 12, 31)))
        val biweekly = Recurrence(Frequency.WEEKLY, interval = 2, anchorDate = d(2026, 10, 2))
        assertEquals(listOf(d(2026, 10, 16), d(2026, 10, 30)), RecurrenceMath.occurrencesBetween(biweekly, d(2026, 10, 2), d(2026, 10, 31)))
    }

    @Test
    fun occurrencesBetween_isExclusiveStart_inclusiveEnd_andRespectsEndDate() {
        val r = Recurrence(Frequency.MONTHLY, anchorDate = d(2026, 1, 10), endDate = d(2026, 3, 10))
        assertEquals(listOf(d(2026, 2, 10), d(2026, 3, 10)), RecurrenceMath.occurrencesBetween(r, d(2026, 1, 10), d(2026, 12, 31)))
        assertEquals(listOf(d(2026, 1, 10), d(2026, 2, 10), d(2026, 3, 10)), RecurrenceMath.occurrencesBetween(r, d(2025, 12, 31), d(2026, 12, 31)))
        assertEquals(emptyList<LocalDate>(), RecurrenceMath.occurrencesBetween(r, d(2026, 3, 10), d(2026, 12, 31)))
    }

    @Test
    fun occurrencesBetween_backfillsFromPastAnchor() {
        val r = Recurrence(Frequency.MONTHLY, anchorDate = d(2026, 1, 1))
        val got = RecurrenceMath.occurrencesBetween(r, d(2025, 12, 31), d(2026, 10, 15))
        assertEquals(10, got.size)
        assertEquals(d(2026, 1, 1), got.first())
        assertEquals(d(2026, 10, 1), got.last())
    }

    @Test
    fun nextAfter_andNextOnOrAfter() {
        val r = Recurrence(Frequency.MONTHLY, anchorDate = d(2026, 1, 31))
        assertEquals(d(2026, 2, 28), RecurrenceMath.nextAfter(r, d(2026, 1, 31)))
        assertEquals(d(2026, 3, 31), RecurrenceMath.nextAfter(r, d(2026, 2, 28)))
        assertEquals(d(2026, 1, 31), RecurrenceMath.nextOnOrAfter(r, d(2026, 1, 31)))
        assertEquals(d(2026, 1, 31), RecurrenceMath.nextAfter(r, d(2020, 1, 1)))
        val ended = r.copy(endDate = d(2026, 3, 31))
        assertEquals(d(2026, 3, 31), RecurrenceMath.nextAfter(ended, d(2026, 3, 1)))
        assertNull(RecurrenceMath.nextAfter(ended, d(2026, 3, 31)))
    }

    @Test
    fun isOccurrence() {
        val r = Recurrence(Frequency.WEEKLY, anchorDate = d(2026, 10, 5))
        assertTrue(RecurrenceMath.isOccurrence(r, d(2026, 10, 12)))
        assertFalse(RecurrenceMath.isOccurrence(r, d(2026, 10, 13)))
    }

    @Test
    fun startIndexEstimate_isCorrectFarFromAnchor() {
        val daily = Recurrence(Frequency.DAILY, anchorDate = d(2016, 1, 1))
        assertEquals(listOf(d(2026, 10, 16), d(2026, 10, 17)), RecurrenceMath.occurrencesBetween(daily, d(2026, 10, 15), d(2026, 10, 17)))
        val monthly = Recurrence(Frequency.MONTHLY, interval = 1, anchorDate = d(2010, 5, 31))
        assertEquals(listOf(d(2026, 10, 31), d(2026, 11, 30)), RecurrenceMath.occurrencesBetween(monthly, d(2026, 10, 15), d(2026, 11, 30)))
        val yearly = Recurrence(Frequency.YEARLY, interval = 2, anchorDate = d(2000, 6, 30))
        assertEquals(listOf(d(2028, 6, 30)), RecurrenceMath.occurrencesBetween(yearly, d(2026, 10, 15), d(2029, 1, 1)))
    }
}
