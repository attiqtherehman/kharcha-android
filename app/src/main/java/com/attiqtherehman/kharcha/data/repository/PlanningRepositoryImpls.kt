package com.attiqtherehman.kharcha.data.repository

import androidx.room.withTransaction
import com.attiqtherehman.kharcha.core.common.DataChangeNotifier
import com.attiqtherehman.kharcha.core.common.Ids
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.core.datetime.DateRange
import com.attiqtherehman.kharcha.core.datetime.range
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.data.local.KharchaDatabase
import com.attiqtherehman.kharcha.data.local.dao.AccountDao
import com.attiqtherehman.kharcha.data.local.dao.BudgetDao
import com.attiqtherehman.kharcha.data.local.dao.DayReviewDao
import com.attiqtherehman.kharcha.data.local.dao.RecurringTransactionDao
import com.attiqtherehman.kharcha.data.local.dao.ReminderDao
import com.attiqtherehman.kharcha.data.local.dao.SavingsGoalDao
import com.attiqtherehman.kharcha.data.local.dao.TransactionDao
import com.attiqtherehman.kharcha.data.local.entity.CategoryBudgetEntity
import com.attiqtherehman.kharcha.data.local.entity.DayReviewEntity
import com.attiqtherehman.kharcha.data.local.entity.MonthlyBudgetEntity
import com.attiqtherehman.kharcha.data.mapper.toDomain
import com.attiqtherehman.kharcha.data.mapper.toEntity
import com.attiqtherehman.kharcha.domain.model.BillPayment
import com.attiqtherehman.kharcha.domain.model.GoalContribution
import com.attiqtherehman.kharcha.domain.model.RecurringTransaction
import com.attiqtherehman.kharcha.domain.model.Reminder
import com.attiqtherehman.kharcha.domain.model.ReminderStatus
import com.attiqtherehman.kharcha.domain.model.SavingsGoal
import com.attiqtherehman.kharcha.domain.model.SavingsGoalProgress
import com.attiqtherehman.kharcha.domain.model.Transaction
import com.attiqtherehman.kharcha.domain.repository.BudgetRepository
import com.attiqtherehman.kharcha.domain.repository.CategoryBudgetLine
import com.attiqtherehman.kharcha.domain.repository.DayReviewRepository
import com.attiqtherehman.kharcha.domain.repository.MonthBudgets
import com.attiqtherehman.kharcha.domain.repository.RecurringTransactionRepository
import com.attiqtherehman.kharcha.domain.repository.ReminderRepository
import com.attiqtherehman.kharcha.domain.repository.SavingsGoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepositoryImpl @Inject constructor(
    private val db: KharchaDatabase,
    private val dao: BudgetDao,
    private val transactionDao: TransactionDao,
    private val clock: Clock,
    private val notifier: DataChangeNotifier,
) : BudgetRepository {

    override fun observeMonth(month: YearMonth): Flow<MonthBudgets> {
        val range = month.range()
        return combine(
            dao.observeMonthly(month.toString()),
            dao.observeCategoryBudgets(month.toString(), range.sqlStart, range.sqlEndExclusive),
            transactionDao.observePeriodTotals(range.sqlStart, range.sqlEndExclusive),
        ) { monthly, lines, totals ->
            MonthBudgets(
                month = month,
                monthly = monthly?.toDomain(),
                lines = lines.map { CategoryBudgetLine(it.budget.toDomain(), it.categoryName, it.categoryColor, it.categoryIcon, Money(it.spentMinor)) },
                totalSpent = Money(totals.expenseMinor),
                saved = Money(totals.savedMinor),
            )
        }
    }

    override suspend fun getMonth(month: YearMonth): MonthBudgets = observeMonth(month).first()

    override suspend fun setMonthly(month: YearMonth, amount: Money?, savingsTarget: Money?) {
        val now = clock.nowMillis()
        val existing = dao.getMonthly(month.toString())
        if (amount == null && savingsTarget == null) {
            if (existing != null) dao.deleteMonthly(month.toString())
        } else {
            dao.upsertMonthly(
                MonthlyBudgetEntity(
                    id = existing?.id ?: Ids.newId(), yearMonth = month.toString(), amountMinor = amount?.minor,
                    savingsTargetMinor = savingsTarget?.minor, createdAt = existing?.createdAt ?: now, updatedAt = now,
                ),
            )
        }
        notifier.financialDataChanged()
    }

    override suspend fun setCategoryBudget(month: YearMonth, categoryId: String, amount: Money) {
        val now = clock.nowMillis()
        val existing = dao.getCategoryBudgets(month.toString()).firstOrNull { it.categoryId == categoryId }
        if (existing == null) {
            dao.insertCategoryBudget(CategoryBudgetEntity(Ids.newId(), month.toString(), categoryId, amount.minor, now, now))
        } else {
            dao.updateCategoryBudget(existing.copy(amountMinor = amount.minor, updatedAt = now))
        }
        notifier.financialDataChanged()
    }

    override suspend fun removeCategoryBudget(id: String) {
        dao.deleteCategoryBudget(id)
        notifier.financialDataChanged()
    }

    override suspend fun copyFromPreviousMonth(month: YearMonth): Int {
        val now = clock.nowMillis()
        val key = month.toString()
        var copied = 0
        db.withTransaction {
            val sourceMonth = dao.monthsWithBudgets().firstOrNull { it < key } ?: return@withTransaction
            val sourceMonthly = dao.getMonthly(sourceMonth)
            if (sourceMonthly != null && dao.getMonthly(key) == null) {
                dao.upsertMonthly(sourceMonthly.copy(id = Ids.newId(), yearMonth = key, createdAt = now, updatedAt = now))
                copied++
            }
            dao.getCategoryBudgets(sourceMonth).forEach { src ->
                val inserted = dao.insertCategoryBudgetIgnore(src.copy(id = Ids.newId(), yearMonth = key, createdAt = now, updatedAt = now))
                if (inserted != -1L) copied++
            }
        }
        if (copied > 0) notifier.financialDataChanged()
        return copied
    }

    override suspend fun monthsWithBudgets(): List<YearMonth> = dao.monthsWithBudgets().map(YearMonth::parse)
}

@Singleton
class SavingsGoalRepositoryImpl @Inject constructor(
    private val db: KharchaDatabase,
    private val dao: SavingsGoalDao,
    private val accountDao: AccountDao,
    private val clock: Clock,
    private val notifier: DataChangeNotifier,
) : SavingsGoalRepository {

    override fun observeGoals(): Flow<List<SavingsGoalProgress>> = combine(
        dao.observeAll(), dao.observeContributionSums(), accountDao.observeBalances(),
    ) { goals, sums, balances ->
        val sumById = sums.associate { it.goalId to Money(it.totalMinor) }
        val balanceById = balances.associate { it.account.id to Money(it.balanceMinor) }
        goals.map { g ->
            val goal = g.toDomain()
            val saved = goal.linkedAccountId?.let { balanceById[it] } ?: sumById[goal.id] ?: Money.ZERO
            SavingsGoalProgress(goal, saved)
        }
    }

    override fun observeGoal(id: String): Flow<SavingsGoalProgress?> = observeGoals().map { list -> list.firstOrNull { it.goal.id == id } }

    override fun observeContributions(goalId: String): Flow<List<GoalContribution>> =
        dao.observeContributions(goalId).map { list -> list.map { it.toDomain() } }

    override suspend fun get(id: String): SavingsGoal? = dao.getById(id)?.toDomain()

    override suspend fun create(goal: SavingsGoal) {
        dao.insert(goal.copy(sortOrder = dao.nextSortOrder()).toEntity())
        notifier.financialDataChanged()
    }

    override suspend fun update(goal: SavingsGoal) {
        dao.update(goal.copy(updatedAt = clock.nowMillis()).toEntity())
        notifier.financialDataChanged()
    }

    override suspend fun delete(id: String) {
        dao.getById(id)?.let { dao.delete(it) }
        notifier.financialDataChanged()
    }

    override suspend fun setCompleted(id: String, completed: Boolean) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(completedAt = if (completed) clock.nowMillis() else null, updatedAt = clock.nowMillis()))
        notifier.financialDataChanged()
    }

    override suspend fun addContribution(contribution: GoalContribution) {
        dao.insertContribution(contribution.toEntity())
        notifier.financialDataChanged()
    }

    override suspend fun deleteContribution(id: String) {
        dao.deleteContribution(id)
        notifier.financialDataChanged()
    }
}

@Singleton
class ReminderRepositoryImpl @Inject constructor(
    private val db: KharchaDatabase,
    private val dao: ReminderDao,
    private val transactionDao: TransactionDao,
    private val clock: Clock,
    private val notifier: DataChangeNotifier,
) : ReminderRepository {

    override fun observeAll(): Flow<List<Reminder>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeDueBy(untilInclusive: LocalDate): Flow<List<Reminder>> =
        dao.observeDueBy(untilInclusive.toString()).map { list -> list.map { it.toDomain() } }

    override fun observeById(id: String): Flow<Reminder?> = dao.observeById(id).map { it?.toDomain() }

    override suspend fun getActive(): List<Reminder> = dao.getActive().map { it.toDomain() }

    override suspend fun get(id: String): Reminder? = dao.getById(id)?.toDomain()

    override suspend fun create(reminder: Reminder) {
        dao.insert(reminder.toEntity())
        notifier.remindersChanged()
    }

    override suspend fun update(reminder: Reminder) {
        dao.update(reminder.copy(updatedAt = clock.nowMillis()).toEntity())
        notifier.remindersChanged()
    }

    override suspend fun delete(id: String) {
        dao.getById(id)?.let { dao.delete(it) }
        notifier.remindersChanged()
    }

    override suspend fun setStatus(id: String, status: ReminderStatus) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(status = status.name, updatedAt = clock.nowMillis()))
        notifier.remindersChanged()
    }

    override fun observePayments(reminderId: String): Flow<List<BillPayment>> =
        dao.observePayments(reminderId).map { list -> list.map { it.toDomain() } }

    override fun observeRecentPayments(limit: Int): Flow<List<BillPayment>> =
        dao.observeRecentPayments(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun paymentExists(reminderId: String, dueDate: LocalDate): Boolean =
        dao.paymentExists(reminderId, dueDate.toString()) > 0

    override suspend fun markPaid(updatedReminder: Reminder, payment: BillPayment, expense: Transaction?) {
        db.withTransaction {
            if (expense != null) transactionDao.insert(expense.toEntity())
            val inserted = dao.insertPaymentIgnore(payment.toEntity())
            check(inserted != -1L) { "payment already recorded" }
            dao.update(updatedReminder.copy(updatedAt = clock.nowMillis()).toEntity())
        }
        notifier.remindersChanged()
        if (expense != null) notifier.financialDataChanged()
    }

    override suspend fun deletePayment(id: String) {
        dao.deletePayment(id)
        notifier.remindersChanged()
    }
}

@Singleton
class RecurringTransactionRepositoryImpl @Inject constructor(
    private val db: KharchaDatabase,
    private val dao: RecurringTransactionDao,
    private val transactionDao: TransactionDao,
    private val clock: Clock,
    private val notifier: DataChangeNotifier,
) : RecurringTransactionRepository {

    override fun observeAll(): Flow<List<RecurringTransaction>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getActive(): List<RecurringTransaction> = dao.getActive().map { it.toDomain() }

    override suspend fun get(id: String): RecurringTransaction? = dao.getById(id)?.toDomain()

    override fun observeById(id: String): Flow<RecurringTransaction?> = dao.observeById(id).map { it?.toDomain() }

    override suspend fun create(rule: RecurringTransaction) {
        dao.insert(rule.toEntity())
        notifier.financialDataChanged()
    }

    override suspend fun update(rule: RecurringTransaction) {
        dao.update(rule.copy(updatedAt = clock.nowMillis()).toEntity())
        notifier.financialDataChanged()
    }

    override suspend fun delete(id: String) {
        dao.getById(id)?.let { dao.delete(it) }
        notifier.financialDataChanged()
    }

    override suspend fun materializedCount(id: String): Int = dao.materializedCount(id)

    override suspend fun materialize(ruleId: String, rows: List<Transaction>, newCursor: LocalDate): Int {
        var inserted = 0
        db.withTransaction {
            rows.forEach { if (transactionDao.insertIgnore(it.toEntity()) != -1L) inserted++ }
            dao.updateCursor(ruleId, newCursor.toString(), clock.nowMillis())
        }
        if (inserted > 0) notifier.financialDataChanged()
        return inserted
    }
}

@Singleton
class DayReviewRepositoryImpl @Inject constructor(
    private val dao: DayReviewDao,
    private val clock: Clock,
) : DayReviewRepository {
    override suspend fun isReviewed(date: LocalDate): Boolean = dao.exists(date.toString()) > 0
    override suspend fun markReviewed(date: LocalDate) = dao.upsert(DayReviewEntity(date.toString(), clock.nowMillis()))
    override suspend fun unmark(date: LocalDate) = dao.delete(date.toString())
    override suspend fun reviewedDates(range: DateRange): Set<LocalDate> =
        dao.between(range.sqlStart, range.endInclusive.toString()).map { LocalDate.parse(it.localDate) }.toSet()
}
