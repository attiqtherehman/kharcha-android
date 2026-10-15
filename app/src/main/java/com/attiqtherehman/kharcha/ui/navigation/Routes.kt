package com.attiqtherehman.kharcha.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe Navigation Compose routes. Top-level tabs are objects; detail screens carry ids.

@Serializable object HomeRoute
@Serializable object TransactionsRoute
@Serializable object AccountsRoute
@Serializable object BudgetsRoute
@Serializable object MoreRoute

@Serializable
data class QuickEntryRoute(
    val categoryId: String? = null,
    val amountMinor: Long? = null,
    val accountId: String? = null,
    /** ISO date to prefill (used from the missed-days screen). */
    val date: String? = null,
)

@Serializable data class TransactionDetailRoute(val transactionId: String)

/** [type] is a TransactionType name; [transactionId] null = create, otherwise edit. */
@Serializable data class TransactionFormRoute(val type: String, val transactionId: String? = null, val duplicateOf: String? = null)

@Serializable data class RefundFormRoute(val originalTransactionId: String)

@Serializable data class AccountDetailRoute(val accountId: String)
@Serializable data class AccountFormRoute(val accountId: String? = null)

@Serializable object SavingsGoalsRoute
@Serializable data class SavingsGoalFormRoute(val goalId: String? = null)
@Serializable data class SavingsGoalDetailRoute(val goalId: String)

@Serializable object RemindersRoute
@Serializable data class ReminderFormRoute(val reminderId: String? = null)
@Serializable data class ReminderDetailRoute(val reminderId: String, val markPaid: Boolean = false)

@Serializable object RecurringRoute
@Serializable data class RecurringFormRoute(val ruleId: String? = null)

@Serializable object ReportsRoute
@Serializable object CategoriesRoute
@Serializable object SettingsRoute
@Serializable object ReminderSettingsRoute
@Serializable object AppLockSettingsRoute
@Serializable object BackupRoute
@Serializable object ExportRoute
@Serializable object ImportCsvRoute
@Serializable object MissedDaysRoute
@Serializable object WidgetPresetsRoute
@Serializable object AboutRoute
