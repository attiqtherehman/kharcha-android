package com.attiqtherehman.kharcha.domain.model

import com.attiqtherehman.kharcha.core.money.Money
import java.time.LocalDate
import java.time.LocalTime

/**
 * A repeating schedule. Occurrences are always computed from [anchorDate] (the first occurrence),
 * never stepped from the previous one, so day-of-month clamping (31st -> 28th -> 31st) never drifts.
 */
data class Recurrence(
    val frequency: Frequency,
    val interval: Int = 1,
    val anchorDate: LocalDate,
    val endDate: LocalDate? = null,
) {
    init {
        require(interval >= 1) { "interval must be >= 1" }
        require(endDate == null || !endDate.isBefore(anchorDate)) { "endDate before anchorDate" }
    }
}

/** A rule that materialises real [Transaction] rows for each due occurrence. */
data class RecurringTransaction(
    val id: String,
    val name: String,
    val recurrence: Recurrence,
    val timeOfDay: LocalTime,
    /** Cursor: occurrences on or before this date have been considered. */
    val lastMaterializedDate: LocalDate,
    val isActive: Boolean = true,
    val type: TransactionType,
    val amount: Money,
    val accountId: String,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val payee: String? = null,
    val description: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
