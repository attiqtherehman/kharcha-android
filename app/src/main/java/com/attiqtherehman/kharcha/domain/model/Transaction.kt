package com.attiqtherehman.kharcha.domain.model

import com.attiqtherehman.kharcha.core.money.Money
import java.time.LocalDate
import java.time.LocalTime

/**
 * A single ledger row. For TRANSFER both [accountId] (source) and [toAccountId] (destination)
 * are set; for every other type only [accountId]. [amount] is positive except for ADJUSTMENT,
 * which is a signed delta.
 */
data class Transaction(
    val id: String,
    val type: TransactionType,
    val amount: Money,
    val accountId: String,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val date: LocalDate,
    val time: LocalTime,
    val payee: String? = null,
    val description: String? = null,
    val notes: String? = null,
    val tags: List<String> = emptyList(),
    /** ATM fee -> its TRANSFER; REFUND -> its original EXPENSE. */
    val linkedTransactionId: String? = null,
    val recurringTransactionId: String? = null,
    val occurrenceKey: String? = null,
    val attachmentFileName: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val isTransfer: Boolean get() = type == TransactionType.TRANSFER
}
