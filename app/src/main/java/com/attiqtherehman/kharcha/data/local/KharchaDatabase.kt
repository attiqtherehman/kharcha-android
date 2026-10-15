package com.attiqtherehman.kharcha.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.attiqtherehman.kharcha.data.local.dao.AccountDao
import com.attiqtherehman.kharcha.data.local.dao.BudgetDao
import com.attiqtherehman.kharcha.data.local.dao.CategoryDao
import com.attiqtherehman.kharcha.data.local.dao.DayReviewDao
import com.attiqtherehman.kharcha.data.local.dao.RecurringTransactionDao
import com.attiqtherehman.kharcha.data.local.dao.ReminderDao
import com.attiqtherehman.kharcha.data.local.dao.SavingsGoalDao
import com.attiqtherehman.kharcha.data.local.dao.TransactionDao
import com.attiqtherehman.kharcha.data.local.entity.AccountEntity
import com.attiqtherehman.kharcha.data.local.entity.BillPaymentEntity
import com.attiqtherehman.kharcha.data.local.entity.CategoryBudgetEntity
import com.attiqtherehman.kharcha.data.local.entity.CategoryEntity
import com.attiqtherehman.kharcha.data.local.entity.DayReviewEntity
import com.attiqtherehman.kharcha.data.local.entity.GoalContributionEntity
import com.attiqtherehman.kharcha.data.local.entity.LedgerEffectView
import com.attiqtherehman.kharcha.data.local.entity.MonthlyBudgetEntity
import com.attiqtherehman.kharcha.data.local.entity.RecurringTransactionEntity
import com.attiqtherehman.kharcha.data.local.entity.ReminderEntity
import com.attiqtherehman.kharcha.data.local.entity.SavingsGoalEntity
import com.attiqtherehman.kharcha.data.local.entity.TransactionEntity

/**
 * The authoritative local store. Schema is exported to app/schemas for migration tests; there is
 * deliberately no destructive-migration fallback.
 */
@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        RecurringTransactionEntity::class,
        MonthlyBudgetEntity::class,
        CategoryBudgetEntity::class,
        SavingsGoalEntity::class,
        GoalContributionEntity::class,
        ReminderEntity::class,
        BillPaymentEntity::class,
        DayReviewEntity::class,
    ],
    views = [LedgerEffectView::class],
    version = KharchaDatabase.VERSION,
    exportSchema = true,
)
abstract class KharchaDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun recurringTransactionDao(): RecurringTransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun reminderDao(): ReminderDao
    abstract fun dayReviewDao(): DayReviewDao

    companion object {
        const val VERSION = 1
        const val NAME = "kharcha.db"
    }
}
