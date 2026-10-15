package com.attiqtherehman.kharcha.domain.model

import com.attiqtherehman.kharcha.core.money.Money
import java.time.LocalDate
import java.time.LocalTime

/**
 * A bill/payment reminder. [dueDate] is the current (next unpaid) occurrence; recurring reminders
 * advance it when marked paid. [advanceDays] lists how many days before the due date to notify
 * (0 = on the day).
 */
data class Reminder(
    val id: String,
    val title: String,
    val amount: Money? = null,
    val categoryId: String? = null,
    val accountId: String? = null,
    val dueDate: LocalDate,
    val reminderTime: LocalTime,
    /** null = one-time. */
    val recurrence: Recurrence? = null,
    val advanceDays: List<Int> = listOf(1, 0),
    val notes: String? = null,
    val status: ReminderStatus = ReminderStatus.ACTIVE,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val isRecurring: Boolean get() = recurrence != null
}

data class BillPayment(
    val id: String,
    val reminderId: String,
    val dueDate: LocalDate,
    val paidAt: Long,
    val amount: Money,
    val transactionId: String? = null,
    val notes: String? = null,
)

data class DayReview(val date: LocalDate, val reviewedAt: Long)
