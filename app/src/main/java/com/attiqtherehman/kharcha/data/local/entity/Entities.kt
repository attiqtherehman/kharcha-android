package com.attiqtherehman.kharcha.data.local.entity

import androidx.room.DatabaseView
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/*
 * Room entities. Conventions: UUID string primary keys; enums stored as their names (TEXT);
 * dates as ISO yyyy-MM-dd, times as HH:mm; timestamps as epoch milliseconds UTC; money as
 * integer minor units. Balances are never stored.
 */

@Serializable
@Entity(
    tableName = "accounts",
    indices = [Index(value = ["isArchived", "sortOrder"])],
)
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val currencyCode: String,
    val openingBalanceMinor: Long,
    val creditLimitMinor: Long?,
    val color: Int,
    val icon: String,
    val notes: String?,
    val isArchived: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
@Entity(
    tableName = "categories",
    indices = [Index(value = ["kind", "isArchived", "sortOrder"])],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val color: Int,
    val icon: String,
    val isFavorite: Boolean,
    val isArchived: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
@Entity(
    tableName = "recurring_transactions",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["toAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index(value = ["isActive", "lastMaterializedDate"]),
        Index(value = ["accountId"]),
        Index(value = ["toAccountId"]),
        Index(value = ["categoryId"]),
    ],
)
data class RecurringTransactionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val frequency: String,
    val intervalCount: Int,
    val anchorDate: String,
    val endDate: String?,
    val timeOfDay: String,
    val lastMaterializedDate: String,
    val isActive: Boolean,
    val type: String,
    val amountMinor: Long,
    val accountId: String,
    val toAccountId: String?,
    val categoryId: String?,
    val payee: String?,
    val description: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["toAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["linkedTransactionId"], onDelete = ForeignKey.SET_NULL, deferred = true),
        ForeignKey(entity = RecurringTransactionEntity::class, parentColumns = ["id"], childColumns = ["recurringTransactionId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index(value = ["accountId", "localDate"]),
        Index(value = ["toAccountId", "localDate"]),
        Index(value = ["categoryId", "localDate"]),
        Index(value = ["localDate"]),
        Index(value = ["linkedTransactionId"]),
        Index(value = ["recurringTransactionId", "occurrenceKey"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val type: String,
    val amountMinor: Long,
    val accountId: String,
    val toAccountId: String?,
    val categoryId: String?,
    val localDate: String,
    val localTime: String,
    val payee: String?,
    val description: String?,
    val notes: String?,
    /** Comma-separated, lower-cased, trimmed. */
    val tags: String?,
    val linkedTransactionId: String?,
    val recurringTransactionId: String?,
    val occurrenceKey: String?,
    val attachmentFileName: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * One row per account touched by a transaction with the signed amount. This is the SQL twin of
 * `LedgerRules.effects` and the only place balance arithmetic exists in SQL.
 */
@DatabaseView(
    viewName = "v_ledger_effects",
    value = """
        SELECT id AS transactionId, accountId, localDate, type,
               CASE type
                 WHEN 'INCOME' THEN amountMinor
                 WHEN 'REFUND' THEN amountMinor
                 WHEN 'ADJUSTMENT' THEN amountMinor
                 WHEN 'EXPENSE' THEN -amountMinor
                 WHEN 'TRANSFER' THEN -amountMinor
                 ELSE 0
               END AS deltaMinor
        FROM transactions
        UNION ALL
        SELECT id AS transactionId, toAccountId AS accountId, localDate, type, amountMinor AS deltaMinor
        FROM transactions
        WHERE type = 'TRANSFER' AND toAccountId IS NOT NULL
    """,
)
data class LedgerEffectView(
    val transactionId: String,
    val accountId: String,
    val localDate: String,
    val type: String,
    val deltaMinor: Long,
)

@Serializable
@Entity(
    tableName = "monthly_budgets",
    indices = [Index(value = ["yearMonth"], unique = true)],
)
data class MonthlyBudgetEntity(
    @PrimaryKey val id: String,
    val yearMonth: String,
    val amountMinor: Long?,
    val savingsTargetMinor: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
@Entity(
    tableName = "category_budgets",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index(value = ["yearMonth", "categoryId"], unique = true),
        Index(value = ["categoryId"]),
    ],
)
data class CategoryBudgetEntity(
    @PrimaryKey val id: String,
    val yearMonth: String,
    val categoryId: String,
    val amountMinor: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
@Entity(
    tableName = "savings_goals",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["linkedAccountId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index(value = ["linkedAccountId"])],
)
data class SavingsGoalEntity(
    @PrimaryKey val id: String,
    val name: String,
    val targetMinor: Long,
    val linkedAccountId: String?,
    val targetDate: String?,
    val notes: String?,
    val color: Int,
    val icon: String,
    val completedAt: Long?,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
@Entity(
    tableName = "goal_contributions",
    foreignKeys = [
        ForeignKey(entity = SavingsGoalEntity::class, parentColumns = ["id"], childColumns = ["goalId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index(value = ["goalId"]), Index(value = ["transactionId"])],
)
data class GoalContributionEntity(
    @PrimaryKey val id: String,
    val goalId: String,
    val localDate: String,
    val amountMinor: Long,
    val note: String?,
    val transactionId: String?,
    val createdAt: Long,
)

@Serializable
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index(value = ["status", "dueDate"]),
        Index(value = ["categoryId"]),
        Index(value = ["accountId"]),
    ],
)
data class ReminderEntity(
    @PrimaryKey val id: String,
    val title: String,
    val amountMinor: Long?,
    val categoryId: String?,
    val accountId: String?,
    val dueDate: String,
    val reminderTime: String,
    /** NONE for one-time, otherwise a Frequency name. */
    val frequency: String,
    val intervalCount: Int,
    val anchorDate: String,
    val endDate: String?,
    /** Comma-separated day offsets, e.g. "3,1,0". */
    val advanceDays: String,
    val notes: String?,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
@Entity(
    tableName = "bill_payments",
    foreignKeys = [
        ForeignKey(entity = ReminderEntity::class, parentColumns = ["id"], childColumns = ["reminderId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index(value = ["reminderId", "dueDate"], unique = true),
        Index(value = ["transactionId"]),
    ],
)
data class BillPaymentEntity(
    @PrimaryKey val id: String,
    val reminderId: String,
    val dueDate: String,
    val paidAt: Long,
    val amountMinor: Long,
    val transactionId: String?,
    val notes: String?,
)

@Serializable
@Entity(tableName = "day_reviews")
data class DayReviewEntity(
    @PrimaryKey val localDate: String,
    val reviewedAt: Long,
)
