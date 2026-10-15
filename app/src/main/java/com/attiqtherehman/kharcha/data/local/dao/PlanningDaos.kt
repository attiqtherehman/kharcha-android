package com.attiqtherehman.kharcha.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.attiqtherehman.kharcha.data.local.entity.BillPaymentEntity
import com.attiqtherehman.kharcha.data.local.entity.CategoryBudgetEntity
import com.attiqtherehman.kharcha.data.local.entity.CategoryEntity
import com.attiqtherehman.kharcha.data.local.entity.DayReviewEntity
import com.attiqtherehman.kharcha.data.local.entity.GoalContributionEntity
import com.attiqtherehman.kharcha.data.local.entity.MonthlyBudgetEntity
import com.attiqtherehman.kharcha.data.local.entity.RecurringTransactionEntity
import com.attiqtherehman.kharcha.data.local.entity.ReminderEntity
import com.attiqtherehman.kharcha.data.local.entity.SavingsGoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringTransactionDao {

    @Query("SELECT * FROM recurring_transactions ORDER BY isActive DESC, name")
    fun observeAll(): Flow<List<RecurringTransactionEntity>>

    @Query("SELECT * FROM recurring_transactions ORDER BY isActive DESC, name")
    suspend fun getAll(): List<RecurringTransactionEntity>

    @Query("SELECT * FROM recurring_transactions WHERE isActive = 1")
    suspend fun getActive(): List<RecurringTransactionEntity>

    @Query("SELECT * FROM recurring_transactions WHERE id = :id")
    suspend fun getById(id: String): RecurringTransactionEntity?

    @Query("SELECT * FROM recurring_transactions WHERE id = :id")
    fun observeById(id: String): Flow<RecurringTransactionEntity?>

    @Query("SELECT COUNT(*) FROM transactions WHERE recurringTransactionId = :id")
    suspend fun materializedCount(id: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(rule: RecurringTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(rules: List<RecurringTransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rules: List<RecurringTransactionEntity>)

    @Update
    suspend fun update(rule: RecurringTransactionEntity)

    @Query("UPDATE recurring_transactions SET lastMaterializedDate = :date, updatedAt = :now WHERE id = :id")
    suspend fun updateCursor(id: String, date: String, now: Long)

    @Delete
    suspend fun delete(rule: RecurringTransactionEntity)

    @Query("DELETE FROM recurring_transactions")
    suspend fun deleteAll()
}

/** Category budget joined with its category and the net spend for its month. */
data class CategoryBudgetRow(
    @Embedded val budget: CategoryBudgetEntity,
    val categoryName: String,
    val categoryColor: Int,
    val categoryIcon: String,
    val spentMinor: Long,
)

@Dao
interface BudgetDao {

    @Query("SELECT * FROM monthly_budgets WHERE yearMonth = :yearMonth")
    fun observeMonthly(yearMonth: String): Flow<MonthlyBudgetEntity?>

    @Query("SELECT * FROM monthly_budgets WHERE yearMonth = :yearMonth")
    suspend fun getMonthly(yearMonth: String): MonthlyBudgetEntity?

    @Query("SELECT * FROM monthly_budgets ORDER BY yearMonth")
    suspend fun getAllMonthly(): List<MonthlyBudgetEntity>

    @Query("SELECT * FROM monthly_budgets WHERE yearMonth < :yearMonth ORDER BY yearMonth DESC LIMIT 1")
    suspend fun latestMonthlyBefore(yearMonth: String): MonthlyBudgetEntity?

    @Query(
        """
        SELECT b.*, c.name AS categoryName, c.color AS categoryColor, c.icon AS categoryIcon,
               (SELECT COALESCE(SUM(CASE t.type WHEN 'EXPENSE' THEN t.amountMinor WHEN 'REFUND' THEN -t.amountMinor ELSE 0 END), 0)
                FROM transactions t
                WHERE t.type IN ('EXPENSE', 'REFUND') AND t.categoryId = b.categoryId
                  AND t.localDate >= :from AND t.localDate < :toExclusive) AS spentMinor
        FROM category_budgets b
        JOIN categories c ON c.id = b.categoryId
        WHERE b.yearMonth = :yearMonth
        ORDER BY c.sortOrder, c.name
        """
    )
    fun observeCategoryBudgets(yearMonth: String, from: String, toExclusive: String): Flow<List<CategoryBudgetRow>>

    @Query("SELECT * FROM category_budgets WHERE yearMonth = :yearMonth")
    suspend fun getCategoryBudgets(yearMonth: String): List<CategoryBudgetEntity>

    @Query("SELECT * FROM category_budgets ORDER BY yearMonth, categoryId")
    suspend fun getAllCategoryBudgets(): List<CategoryBudgetEntity>

    @Query("SELECT * FROM category_budgets WHERE id = :id")
    suspend fun getCategoryBudget(id: String): CategoryBudgetEntity?

    @Query("SELECT DISTINCT yearMonth FROM category_budgets UNION SELECT yearMonth FROM monthly_budgets ORDER BY yearMonth DESC")
    suspend fun monthsWithBudgets(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMonthly(budget: MonthlyBudgetEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllMonthly(budgets: List<MonthlyBudgetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllMonthly(budgets: List<MonthlyBudgetEntity>)

    @Query("DELETE FROM monthly_budgets WHERE yearMonth = :yearMonth")
    suspend fun deleteMonthly(yearMonth: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategoryBudget(budget: CategoryBudgetEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategoryBudgetIgnore(budget: CategoryBudgetEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllCategoryBudgets(budgets: List<CategoryBudgetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllCategoryBudgets(budgets: List<CategoryBudgetEntity>)

    @Update
    suspend fun updateCategoryBudget(budget: CategoryBudgetEntity)

    @Query("DELETE FROM category_budgets WHERE id = :id")
    suspend fun deleteCategoryBudget(id: String)

    @Query("DELETE FROM monthly_budgets")
    suspend fun deleteAllMonthly()

    @Query("DELETE FROM category_budgets")
    suspend fun deleteAllCategoryBudgets()
}

data class GoalContributionSumRow(val goalId: String, val totalMinor: Long)

@Dao
interface SavingsGoalDao {

    @Query("SELECT * FROM savings_goals ORDER BY completedAt IS NOT NULL, sortOrder, name")
    fun observeAll(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals ORDER BY sortOrder, name")
    suspend fun getAll(): List<SavingsGoalEntity>

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun getById(id: String): SavingsGoalEntity?

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    fun observeById(id: String): Flow<SavingsGoalEntity?>

    @Query("SELECT goalId, COALESCE(SUM(amountMinor), 0) AS totalMinor FROM goal_contributions GROUP BY goalId")
    fun observeContributionSums(): Flow<List<GoalContributionSumRow>>

    @Query("SELECT * FROM goal_contributions WHERE goalId = :goalId ORDER BY localDate DESC, createdAt DESC")
    fun observeContributions(goalId: String): Flow<List<GoalContributionEntity>>

    @Query("SELECT * FROM goal_contributions ORDER BY localDate, createdAt")
    suspend fun getAllContributions(): List<GoalContributionEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM savings_goals")
    suspend fun nextSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(goal: SavingsGoalEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(goals: List<SavingsGoalEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(goals: List<SavingsGoalEntity>)

    @Update
    suspend fun update(goal: SavingsGoalEntity)

    @Delete
    suspend fun delete(goal: SavingsGoalEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertContribution(contribution: GoalContributionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllContributions(contributions: List<GoalContributionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllContributions(contributions: List<GoalContributionEntity>)

    @Update
    suspend fun updateContribution(contribution: GoalContributionEntity)

    @Query("DELETE FROM goal_contributions WHERE id = :id")
    suspend fun deleteContribution(id: String)

    @Query("DELETE FROM savings_goals")
    suspend fun deleteAll()

    @Query("DELETE FROM goal_contributions")
    suspend fun deleteAllContributions()
}

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders ORDER BY CASE status WHEN 'ACTIVE' THEN 0 WHEN 'PAUSED' THEN 1 ELSE 2 END, dueDate, reminderTime")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY dueDate")
    suspend fun getAll(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE status = 'ACTIVE'")
    suspend fun getActive(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE status = 'ACTIVE' AND dueDate <= :untilInclusive ORDER BY dueDate, reminderTime")
    fun observeDueBy(untilInclusive: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE id = :id")
    fun observeById(id: String): Flow<ReminderEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(reminder: ReminderEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(reminders: List<ReminderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(reminders: List<ReminderEntity>)

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Delete
    suspend fun delete(reminder: ReminderEntity)

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()

    @Query("SELECT * FROM bill_payments WHERE reminderId = :reminderId ORDER BY dueDate DESC")
    fun observePayments(reminderId: String): Flow<List<BillPaymentEntity>>

    @Query("SELECT * FROM bill_payments ORDER BY paidAt DESC LIMIT :limit")
    fun observeRecentPayments(limit: Int): Flow<List<BillPaymentEntity>>

    @Query("SELECT * FROM bill_payments ORDER BY dueDate")
    suspend fun getAllPayments(): List<BillPaymentEntity>

    @Query("SELECT COUNT(*) FROM bill_payments WHERE reminderId = :reminderId AND dueDate = :dueDate")
    suspend fun paymentExists(reminderId: String, dueDate: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPaymentIgnore(payment: BillPaymentEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllPayments(payments: List<BillPaymentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllPayments(payments: List<BillPaymentEntity>)

    @Update
    suspend fun updatePayment(payment: BillPaymentEntity)

    @Query("DELETE FROM bill_payments WHERE id = :id")
    suspend fun deletePayment(id: String)

    @Query("DELETE FROM bill_payments")
    suspend fun deleteAllPayments()
}

@Dao
interface DayReviewDao {

    @Query("SELECT COUNT(*) FROM day_reviews WHERE localDate = :date")
    suspend fun exists(date: String): Int

    @Query("SELECT * FROM day_reviews WHERE localDate >= :from AND localDate <= :to")
    suspend fun between(from: String, to: String): List<DayReviewEntity>

    @Query("SELECT * FROM day_reviews ORDER BY localDate")
    suspend fun getAll(): List<DayReviewEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(review: DayReviewEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(reviews: List<DayReviewEntity>)

    @Query("DELETE FROM day_reviews WHERE localDate = :date")
    suspend fun delete(date: String)

    @Query("DELETE FROM day_reviews")
    suspend fun deleteAll()
}

/** Shared helper used by category screens: category plus whether it has history. */
data class CategoryUsageRow(
    @Embedded val category: CategoryEntity,
    val usageCount: Int,
)
