package com.attiqtherehman.kharcha.domain.usecase

import com.attiqtherehman.kharcha.core.common.AlreadyPaidException
import com.attiqtherehman.kharcha.core.common.Ids
import com.attiqtherehman.kharcha.core.common.LedgerViolationException
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.domain.ledger.LedgerRules
import com.attiqtherehman.kharcha.domain.model.BillPayment
import com.attiqtherehman.kharcha.domain.model.GoalContribution
import com.attiqtherehman.kharcha.domain.model.ReminderStatus
import com.attiqtherehman.kharcha.domain.model.Transaction
import com.attiqtherehman.kharcha.domain.model.TransactionType
import com.attiqtherehman.kharcha.domain.recurrence.RecurrenceMath
import com.attiqtherehman.kharcha.domain.repository.AccountRepository
import com.attiqtherehman.kharcha.domain.repository.RecurringTransactionRepository
import com.attiqtherehman.kharcha.domain.repository.ReminderRepository
import com.attiqtherehman.kharcha.domain.repository.SavingsGoalRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Creates real transaction rows for every recurring occurrence that has become due. Safe to run
 * any number of times: the unique (rule, occurrence) index makes duplicates impossible and the
 * cursor keeps each run cheap.
 */
class MaterializeRecurringTransactionsUseCase @Inject constructor(
    private val rules: RecurringTransactionRepository,
    private val clock: Clock,
) {
    data class Result(val inserted: Int, val rulesProcessed: Int)

    suspend operator fun invoke(today: LocalDate = clock.today()): Result {
        var inserted = 0
        var processed = 0
        for (rule in rules.getActive()) {
            processed++
            val occurrences = RecurrenceMath.occurrencesBetween(rule.recurrence, rule.lastMaterializedDate, today, limit = 400)
            val ended = rule.recurrence.endDate?.let { !it.isAfter(today) } == true
            if (occurrences.isEmpty()) {
                if (ended) rules.update(rule.copy(isActive = false))
                continue
            }
            val now = clock.nowMillis()
            val rows = occurrences.map { date ->
                Transaction(
                    id = Ids.newId(), type = rule.type, amount = rule.amount, accountId = rule.accountId,
                    toAccountId = rule.toAccountId, categoryId = rule.categoryId, date = date, time = rule.timeOfDay,
                    payee = rule.payee, description = rule.description ?: rule.name,
                    recurringTransactionId = rule.id, occurrenceKey = date.toString(),
                    createdAt = now, updatedAt = now,
                )
            }
            inserted += rules.materialize(rule.id, rows, occurrences.last())
            if (ended) rules.update(rule.copy(isActive = false, lastMaterializedDate = occurrences.last()))
        }
        return Result(inserted, processed)
    }
}

/** Marks the current occurrence of a bill as paid, optionally recording the expense, and advances it. */
class MarkBillPaidUseCase @Inject constructor(
    private val reminders: ReminderRepository,
    private val accounts: AccountRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        reminderId: String,
        amount: Money,
        paidOn: LocalDate,
        accountId: String?,
        createExpense: Boolean,
        notes: String? = null,
    ): BillPayment {
        val reminder = requireNotNull(reminders.get(reminderId)) { "reminder missing" }
        if (reminders.paymentExists(reminderId, reminder.dueDate)) throw AlreadyPaidException()
        val now = clock.nowMillis()
        val expense: Transaction? = if (createExpense && accountId != null && amount.isPositive) {
            val tx = Transaction(
                id = Ids.newId(), type = TransactionType.EXPENSE, amount = amount, accountId = accountId,
                categoryId = reminder.categoryId, date = paidOn, time = clock.nowTime(), payee = reminder.title,
                description = "Bill payment", notes = notes, createdAt = now, updatedAt = now,
            )
            val violations = LedgerRules.validate(tx, accounts.getAccounts().associateBy { it.id })
            if (violations.isNotEmpty()) throw LedgerViolationException(violations)
            tx
        } else null
        val payment = BillPayment(
            id = Ids.newId(), reminderId = reminderId, dueDate = reminder.dueDate, paidAt = now,
            amount = amount, transactionId = expense?.id, notes = notes,
        )
        val next = reminder.recurrence?.let { RecurrenceMath.nextAfter(it, reminder.dueDate) }
        val updated = if (next == null) {
            reminder.copy(status = ReminderStatus.COMPLETED)
        } else {
            reminder.copy(dueDate = next, status = ReminderStatus.ACTIVE)
        }
        reminders.markPaid(updated, payment, expense)
        return payment
    }
}

/** Skips the current occurrence of a recurring bill without recording a payment. */
class SkipBillOccurrenceUseCase @Inject constructor(private val reminders: ReminderRepository) {
    suspend operator fun invoke(reminderId: String) {
        val reminder = requireNotNull(reminders.get(reminderId))
        val next = reminder.recurrence?.let { RecurrenceMath.nextAfter(it, reminder.dueDate) }
        reminders.update(if (next == null) reminder.copy(status = ReminderStatus.COMPLETED) else reminder.copy(dueDate = next))
    }
}

/**
 * Adds money to a goal. When the goal is linked to a savings account and a source account is
 * given, a SAVINGS transfer is recorded as well (progress then follows the account balance, so
 * the contribution row is history only and never double counts).
 */
class AddGoalContributionUseCase @Inject constructor(
    private val goals: SavingsGoalRepository,
    private val recordTransfer: RecordTransferUseCase,
    private val clock: Clock,
) {
    suspend operator fun invoke(goalId: String, amount: Money, date: LocalDate, note: String?, fromAccountId: String?): GoalContribution {
        val goal = requireNotNull(goals.get(goalId)) { "goal missing" }
        val now = clock.nowMillis()
        var transactionId: String? = null
        if (goal.linkedAccountId != null && fromAccountId != null && amount.isPositive) {
            val transfer = Transaction(
                id = Ids.newId(), type = TransactionType.TRANSFER, amount = amount, accountId = fromAccountId,
                toAccountId = goal.linkedAccountId, date = date, time = clock.nowTime(),
                description = "Savings: ${goal.name}", createdAt = now, updatedAt = now,
            )
            recordTransfer(transfer, fee = null)
            transactionId = transfer.id
        }
        val contribution = GoalContribution(
            id = Ids.newId(), goalId = goalId, date = date, amount = amount, note = note, transactionId = transactionId, createdAt = now,
        )
        goals.addContribution(contribution)
        return contribution
    }
}
