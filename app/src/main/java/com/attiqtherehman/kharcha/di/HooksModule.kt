package com.attiqtherehman.kharcha.di

import com.attiqtherehman.kharcha.AppStartHook
import com.attiqtherehman.kharcha.domain.model.Transaction
import com.attiqtherehman.kharcha.domain.usecase.ExpenseRecordedListener
import com.attiqtherehman.kharcha.notifications.BudgetAlertChecker
import com.attiqtherehman.kharcha.notifications.ReminderScheduler
import com.attiqtherehman.kharcha.widget.WidgetUpdater
import com.attiqtherehman.kharcha.worker.RecurringStartHook
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import java.time.YearMonth

/** App-start hooks run on every foreground and after boot/time changes (order: recurring rows first, then alarms, then widgets). */
@Module
@InstallIn(SingletonComponent::class)
abstract class HooksModule {
    @Multibinds
    abstract fun appStartHooks(): Set<AppStartHook>

    @Multibinds
    abstract fun expenseListeners(): Set<ExpenseRecordedListener>

    @Binds @IntoSet
    abstract fun recurringHook(impl: RecurringStartHook): AppStartHook

    @Binds @IntoSet
    abstract fun reminderHook(impl: ReminderScheduler): AppStartHook

    @Binds @IntoSet
    abstract fun widgetHook(impl: WidgetUpdater): AppStartHook

    companion object {
        @Provides @IntoSet
        fun budgetAlerts(checker: BudgetAlertChecker): ExpenseRecordedListener = object : ExpenseRecordedListener {
            override suspend fun onExpenseRecorded(transaction: Transaction) = checker.check(YearMonth.from(transaction.date), transaction.categoryId)
        }
    }
}
