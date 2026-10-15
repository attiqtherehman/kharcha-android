package com.attiqtherehman.kharcha.domain.usecase

import com.attiqtherehman.kharcha.core.common.Ids
import com.attiqtherehman.kharcha.core.common.LedgerViolationException
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.data.prefs.SettingsDataStore
import com.attiqtherehman.kharcha.data.seed.DefaultData
import com.attiqtherehman.kharcha.domain.ledger.LedgerRules
import com.attiqtherehman.kharcha.domain.model.Account
import com.attiqtherehman.kharcha.domain.model.Transaction
import com.attiqtherehman.kharcha.domain.model.TransactionType
import com.attiqtherehman.kharcha.domain.repository.AccountRepository
import com.attiqtherehman.kharcha.domain.repository.TransactionRepository
import java.time.LocalDate
import javax.inject.Inject

class RefundExceedsOriginalException(val remaining: Money) : IllegalArgumentException("refund exceeds remaining amount")
class TransactionTypeChangeException : IllegalArgumentException("transaction type cannot be changed; delete and re-create instead")

private suspend fun AccountRepository.byId(): Map<String, Account> = getAccounts().associateBy { it.id }

private fun requireValid(t: Transaction, accounts: Map<String, Account>) {
    val violations = LedgerRules.validate(t, accounts)
    if (violations.isNotEmpty()) throw LedgerViolationException(violations)
}

/** Hook run after an expense lands so the budget layer can warn about thresholds. */
interface ExpenseRecordedListener {
    suspend fun onExpenseRecorded(transaction: Transaction)
}

/** Records an INCOME or EXPENSE (or a bare REFUND/ADJUSTMENT already validated by a sibling use case). */
class RecordTransactionUseCase @Inject constructor(
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val settings: SettingsDataStore,
    private val listeners: Set<@JvmSuppressWildcards ExpenseRecordedListener>,
) {
    suspend operator fun invoke(transaction: Transaction): Transaction {
        require(transaction.type == TransactionType.INCOME || transaction.type == TransactionType.EXPENSE) {
            "use the dedicated use case for ${transaction.type}"
        }
        requireValid(transaction, accounts.byId())
        transactions.insertAll(listOf(transaction))
        settings.setLastUsedAccountId(transaction.accountId)
        if (transaction.type == TransactionType.EXPENSE) listeners.forEach { runCatching { it.onExpenseRecorded(transaction) } }
        return transaction
    }
}

/** Records a transfer and, optionally, a linked fee expense on the source account, atomically. */
class RecordTransferUseCase @Inject constructor(
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        transfer: Transaction,
        fee: Money? = null,
        feeCategoryId: String? = DefaultData.BANK_FEES_CATEGORY_ID,
        feeDescription: String? = "ATM fee",
    ): List<Transaction> {
        require(transfer.type == TransactionType.TRANSFER)
        val byId = accounts.byId()
        requireValid(transfer, byId)
        val rows = ArrayList<Transaction>(2)
        rows += transfer
        if (fee != null && fee.isPositive) {
            val now = clock.nowMillis()
            val feeRow = Transaction(
                id = Ids.newId(), type = TransactionType.EXPENSE, amount = fee, accountId = transfer.accountId,
                categoryId = feeCategoryId, date = transfer.date, time = transfer.time,
                payee = transfer.payee, description = feeDescription, linkedTransactionId = transfer.id,
                createdAt = now, updatedAt = now,
            )
            requireValid(feeRow, byId)
            rows += feeRow
        }
        transactions.insertAll(rows)
        return rows
    }
}

/** Records a refund, optionally linked to the original expense, never exceeding what is left of it. */
class RecordRefundUseCase @Inject constructor(
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
) {
    suspend operator fun invoke(refund: Transaction): Transaction {
        require(refund.type == TransactionType.REFUND)
        requireValid(refund, accounts.byId())
        refund.linkedTransactionId?.let { originalId ->
            val original = requireNotNull(transactions.get(originalId)) { "original transaction missing" }
            require(original.type == TransactionType.EXPENSE) { "refunds link to expenses only" }
            val remaining = original.amount - transactions.sumRefundsLinkedTo(originalId, excludeId = refund.id)
            if (refund.amount > remaining) throw RefundExceedsOriginalException(remaining)
        }
        transactions.insertAll(listOf(refund))
        return refund
    }
}

/** "Set balance to X": records a signed ADJUSTMENT for the difference. */
class AdjustBalanceUseCase @Inject constructor(
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(accountId: String, actualBalance: Money, date: LocalDate, description: String?): Transaction? {
        val balances = accounts.getBalances()
        val current = balances.firstOrNull { it.account.id == accountId } ?: error("account missing")
        val delta = actualBalance - current.balance
        if (delta.isZero) return null
        val now = clock.nowMillis()
        val row = Transaction(
            id = Ids.newId(), type = TransactionType.ADJUSTMENT, amount = delta, accountId = accountId,
            date = date, time = clock.nowTime(), description = description ?: "Balance adjustment",
            createdAt = now, updatedAt = now,
        )
        requireValid(row, accounts.byId())
        transactions.insertAll(listOf(row))
        return row
    }
}

/**
 * Changes the opening balance. With no ledger rows the column is edited directly; otherwise an
 * auditable ADJUSTMENT dated at the earliest ledger entry keeps every historical balance correct.
 */
class FixOpeningBalanceUseCase @Inject constructor(
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(accountId: String, newOpening: Money): Transaction? {
        val account = requireNotNull(accounts.getAccount(accountId)) { "account missing" }
        if (account.openingBalance == newOpening) return null
        if (accounts.transactionCount(accountId) == 0) {
            accounts.update(account.copy(openingBalance = newOpening))
            return null
        }
        val delta = newOpening - account.openingBalance
        val now = clock.nowMillis()
        val row = Transaction(
            id = Ids.newId(), type = TransactionType.ADJUSTMENT, amount = delta, accountId = accountId,
            date = accounts.earliestLedgerDate(accountId) ?: clock.today(), time = java.time.LocalTime.MIDNIGHT,
            description = "Opening balance correction", createdAt = now, updatedAt = now,
        )
        transactions.insertAll(listOf(row))
        return row
    }
}

class UpdateTransactionUseCase @Inject constructor(
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val settings: SettingsDataStore,
) {
    suspend operator fun invoke(updated: Transaction): Transaction {
        val existing = requireNotNull(transactions.get(updated.id)) { "transaction missing" }
        if (existing.type != updated.type) throw TransactionTypeChangeException()
        requireValid(updated, accounts.byId())
        if (updated.type == TransactionType.REFUND) {
            updated.linkedTransactionId?.let { originalId ->
                val original = requireNotNull(transactions.get(originalId))
                val remaining = original.amount - transactions.sumRefundsLinkedTo(originalId, excludeId = updated.id)
                if (updated.amount > remaining) throw RefundExceedsOriginalException(remaining)
            }
        }
        transactions.update(updated)
        if (updated.type == TransactionType.EXPENSE || updated.type == TransactionType.INCOME) {
            settings.setLastUsedAccountId(updated.accountId)
        }
        return updated
    }
}

class DeleteTransactionUseCase @Inject constructor(
    private val transactions: TransactionRepository,
) {
    /** Deletes the row; with [includeLinked] also removes its fee/refund rows (otherwise their link is nulled by the FK). */
    suspend operator fun invoke(id: String, includeLinked: Boolean) {
        if (includeLinked) transactions.deleteWithLinked(id) else transactions.delete(id)
    }
}
