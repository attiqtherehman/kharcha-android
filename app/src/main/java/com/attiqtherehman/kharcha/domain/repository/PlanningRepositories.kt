package com.attiqtherehman.kharcha.domain.repository

import com.attiqtherehman.kharcha.core.datetime.DateRange
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.domain.model.BillPayment
import com.attiqtherehman.kharcha.domain.model.BudgetProgress
import com.attiqtherehman.kharcha.domain.model.CategoryBudget
import com.attiqtherehman.kharcha.domain.model.GoalContribution
import com.attiqtherehman.kharcha.domain.model.MonthlyBudget
import com.attiqtherehman.kharcha.domain.model.RecurringTransaction
import com.attiqtherehman.kharcha.domain.model.Reminder
import com.attiqtherehman.kharcha.domain.model.ReminderStatus
import com.attiqtherehman.kharcha.domain.model.SavingsGoal
import com.attiqtherehman.kharcha.domain.model.SavingsGoalProgress
import com.attiqtherehman.kharcha.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

data class CategoryBudgetLine(
    val budget: CategoryBudget,
    val categoryName: String,
    val categoryColor: Int,
    val categoryIcon: String,
    val spent: Money,
) {
    val progress: BudgetProgress get() = BudgetProgress(budget.amount, spent)
}

data class MonthBudgets(
    val month: YearMonth,
    val monthly: MonthlyBudget?,
    val lines: List<CategoryBudgetLine>,
    /** Net spend across all categories (same formula as the dashboard). */
    val totalSpent: Money,
    /** Net transfers into savings accounts this month. */
    val saved: Money,
) {
    val overall: BudgetProgress? get() = monthly?.amount?.let { BudgetProgress(it, totalSpent) }
    val savingsProgress: BudgetProgress? get() = monthly?.savingsTarget?.let { BudgetProgress(it, saved) }
}

interface BudgetRepository {
    fun observeMonth(month: YearMonth): Flow<MonthBudgets>
    suspend fun getMonth(month: YearMonth): MonthBudgets
    suspend fun setMonthly(month: YearMonth, amount: Money?, savingsTarget: Money?)
    suspend fun setCategoryBudget(month: YearMonth, categoryId: String, amount: Money)
    suspend fun removeCategoryBudget(id: String)
    /** Copies the most recent earlier month's budgets into [month], skipping categories already set. Returns rows copied. */
    suspend fun copyFromPreviousMonth(month: YearMonth): Int
    suspend fun monthsWithBudgets(): List<YearMonth>
}

interface SavingsGoalRepository {
    fun observeGoals(): Flow<List<SavingsGoalProgress>>
    fun observeGoal(id: String): Flow<SavingsGoalProgress?>
    fun observeContributions(goalId: String): Flow<List<GoalContribution>>
    suspend fun get(id: String): SavingsGoal?
    suspend fun create(goal: SavingsGoal)
    suspend fun update(goal: SavingsGoal)
    suspend fun delete(id: String)
    suspend fun setCompleted(id: String, completed: Boolean)
    suspend fun addContribution(contribution: GoalContribution)
    suspend fun deleteContribution(id: String)
}

interface ReminderRepository {
    fun observeAll(): Flow<List<Reminder>>
    fun observeDueBy(untilInclusive: LocalDate): Flow<List<Reminder>>
    fun observeById(id: String): Flow<Reminder?>
    suspend fun getActive(): List<Reminder>
    suspend fun get(id: String): Reminder?
    suspend fun create(reminder: Reminder)
    suspend fun update(reminder: Reminder)
    suspend fun delete(id: String)
    suspend fun setStatus(id: String, status: ReminderStatus)
    fun observePayments(reminderId: String): Flow<List<BillPayment>>
    fun observeRecentPayments(limit: Int): Flow<List<BillPayment>>
    suspend fun paymentExists(reminderId: String, dueDate: LocalDate): Boolean
    /** Atomically records the payment, the optional expense and the advanced reminder. */
    suspend fun markPaid(updatedReminder: Reminder, payment: BillPayment, expense: Transaction?)
    suspend fun deletePayment(id: String)
}

interface RecurringTransactionRepository {
    fun observeAll(): Flow<List<RecurringTransaction>>
    suspend fun getActive(): List<RecurringTransaction>
    suspend fun get(id: String): RecurringTransaction?
    fun observeById(id: String): Flow<RecurringTransaction?>
    suspend fun create(rule: RecurringTransaction)
    suspend fun update(rule: RecurringTransaction)
    suspend fun delete(id: String)
    suspend fun materializedCount(id: String): Int
    /** Inserts [rows] (ignoring duplicates on rule+occurrence) and advances the cursor, atomically. Returns rows inserted. */
    suspend fun materialize(ruleId: String, rows: List<Transaction>, newCursor: LocalDate): Int
}

interface DayReviewRepository {
    suspend fun isReviewed(date: LocalDate): Boolean
    suspend fun markReviewed(date: LocalDate)
    suspend fun unmark(date: LocalDate)
    suspend fun reviewedDates(range: DateRange): Set<LocalDate>
}
