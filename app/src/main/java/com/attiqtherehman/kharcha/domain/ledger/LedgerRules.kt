package com.attiqtherehman.kharcha.domain.ledger

import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.domain.model.Account
import com.attiqtherehman.kharcha.domain.model.AccountType
import com.attiqtherehman.kharcha.domain.model.Transaction
import com.attiqtherehman.kharcha.domain.model.TransactionType
import com.attiqtherehman.kharcha.domain.model.TransferKind
import java.time.LocalDate

/**
 * The single Kotlin definition of how a transaction moves money. The SQL view `v_ledger_effects`
 * encodes exactly the same table; a parity test keeps them in sync.
 *
 * | type       | accountId | toAccountId |
 * |------------|-----------|-------------|
 * | INCOME     | +amount   |             |
 * | EXPENSE    | -amount   |             |
 * | REFUND     | +amount   |             |
 * | TRANSFER   | -amount   | +amount     |
 * | ADJUSTMENT | +amount (signed delta)  |
 */
object LedgerRules {

    data class Effect(val accountId: String, val delta: Money)

    fun effects(t: Transaction): List<Effect> = when (t.type) {
        TransactionType.INCOME, TransactionType.REFUND -> listOf(Effect(t.accountId, t.amount))
        TransactionType.EXPENSE -> listOf(Effect(t.accountId, -t.amount))
        TransactionType.ADJUSTMENT -> listOf(Effect(t.accountId, t.amount))
        TransactionType.TRANSFER -> listOf(
            Effect(t.accountId, -t.amount),
            Effect(requireNotNull(t.toAccountId) { "TRANSFER ${t.id} has no destination" }, t.amount),
        )
    }

    /** Opening balance plus every effect on the account, optionally only up to [asOf] (inclusive). */
    fun balance(account: Account, transactions: Iterable<Transaction>, asOf: LocalDate? = null): Money {
        var total = account.openingBalance
        for (t in transactions) {
            if (asOf != null && t.date.isAfter(asOf)) continue
            for (e in effects(t)) if (e.accountId == account.id) total += e.delta
        }
        return total
    }

    /** Contribution of a row to "spending": expenses count, refunds reduce, nothing else counts. */
    fun spendDelta(t: Transaction): Money = when (t.type) {
        TransactionType.EXPENSE -> t.amount
        TransactionType.REFUND -> -t.amount
        else -> Money.ZERO
    }

    fun incomeDelta(t: Transaction): Money =
        if (t.type == TransactionType.INCOME) t.amount else Money.ZERO

    /** Net amount moved into SAVINGS accounts from non-savings accounts by a transfer. */
    fun savedDelta(t: Transaction, accountsById: Map<String, Account>): Money {
        if (t.type != TransactionType.TRANSFER) return Money.ZERO
        val from = accountsById[t.accountId]?.type ?: return Money.ZERO
        val to = accountsById[t.toAccountId]?.type ?: return Money.ZERO
        return when {
            to == AccountType.SAVINGS && from != AccountType.SAVINGS -> t.amount
            from == AccountType.SAVINGS && to != AccountType.SAVINGS -> -t.amount
            else -> Money.ZERO
        }
    }

    fun transferKind(t: Transaction, accountsById: Map<String, Account>, hasLinkedFee: Boolean = false): TransferKind {
        if (t.type != TransactionType.TRANSFER) return TransferKind.GENERAL
        val from = accountsById[t.accountId]?.type
        val to = accountsById[t.toAccountId]?.type
        return when {
            to == AccountType.CREDIT_CARD -> TransferKind.CARD_REPAYMENT
            to == AccountType.SAVINGS -> TransferKind.SAVINGS
            from == AccountType.BANK && to == AccountType.CASH -> TransferKind.ATM_WITHDRAWAL
            hasLinkedFee -> TransferKind.ATM_WITHDRAWAL
            else -> TransferKind.GENERAL
        }
    }

    enum class Violation {
        UNKNOWN_ACCOUNT,
        UNKNOWN_TO_ACCOUNT,
        TO_ACCOUNT_REQUIRED,
        TO_ACCOUNT_NOT_ALLOWED,
        SAME_ACCOUNT,
        CURRENCY_MISMATCH,
        CATEGORY_NOT_ALLOWED,
        AMOUNT_NOT_POSITIVE,
        ADJUSTMENT_ZERO,
        ARCHIVED_ACCOUNT,
    }

    /** Invariants Room cannot express as CHECK constraints. Empty list = valid. */
    fun validate(t: Transaction, accountsById: Map<String, Account>): List<Violation> {
        val out = mutableListOf<Violation>()
        val from = accountsById[t.accountId]
        if (from == null) out += Violation.UNKNOWN_ACCOUNT
        when (t.type) {
            TransactionType.TRANSFER -> {
                val toId = t.toAccountId
                if (toId == null) {
                    out += Violation.TO_ACCOUNT_REQUIRED
                } else {
                    val to = accountsById[toId]
                    if (to == null) out += Violation.UNKNOWN_TO_ACCOUNT
                    if (toId == t.accountId) out += Violation.SAME_ACCOUNT
                    if (from != null && to != null && from.currencyCode != to.currencyCode) out += Violation.CURRENCY_MISMATCH
                    if (to?.isArchived == true) out += Violation.ARCHIVED_ACCOUNT
                }
                if (t.categoryId != null) out += Violation.CATEGORY_NOT_ALLOWED
                if (!t.amount.isPositive) out += Violation.AMOUNT_NOT_POSITIVE
            }
            TransactionType.ADJUSTMENT -> {
                if (t.toAccountId != null) out += Violation.TO_ACCOUNT_NOT_ALLOWED
                if (t.categoryId != null) out += Violation.CATEGORY_NOT_ALLOWED
                if (t.amount.isZero) out += Violation.ADJUSTMENT_ZERO
            }
            TransactionType.INCOME, TransactionType.EXPENSE, TransactionType.REFUND -> {
                if (t.toAccountId != null) out += Violation.TO_ACCOUNT_NOT_ALLOWED
                if (!t.amount.isPositive) out += Violation.AMOUNT_NOT_POSITIVE
            }
        }
        if (from?.isArchived == true) out += Violation.ARCHIVED_ACCOUNT
        return out
    }
}
