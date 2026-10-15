package com.attiqtherehman.kharcha.notifications

import android.content.Context
import com.attiqtherehman.kharcha.R
import com.attiqtherehman.kharcha.core.money.CurrencyInfo
import com.attiqtherehman.kharcha.core.money.MoneyFormatter
import com.attiqtherehman.kharcha.data.prefs.SettingsDataStore
import com.attiqtherehman.kharcha.domain.model.BudgetProgress
import com.attiqtherehman.kharcha.domain.repository.BudgetRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * After an expense is recorded, posts at most one notification per budget line per threshold
 * per month (75 / 90 / 100 %), according to the thresholds enabled in Settings.
 */
@Singleton
class BudgetAlertChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val budgets: BudgetRepository,
    private val settings: SettingsDataStore,
    private val notifier: ReminderNotifier,
) {
    suspend fun check(month: YearMonth, categoryId: String?) {
        val prefs = settings.current()
        val thresholds = listOfNotNull(75.takeIf { prefs.budgetWarn75 }, 90.takeIf { prefs.budgetWarn90 }, 100.takeIf { prefs.budgetWarn100 })
        if (thresholds.isEmpty()) return
        val mb = budgets.getMonth(month)
        val currency = CurrencyInfo.of(prefs.baseCurrency)
        val notified = prefs.budgetAlertsNotified.toMutableSet()
        var changed = false
        fun consider(key: String, label: String, progress: BudgetProgress) {
            val percent = (progress.fraction * 100f).toInt()
            thresholds.filter { percent >= it }.maxOrNull()?.let { t ->
                val k = "${month}|$key|$t"
                if (notified.add(k)) {
                    changed = true
                    notifier.postBudgetWarning(
                        title = context.getString(R.string.budget_alert_title, label, t),
                        text = context.getString(R.string.home_budget_spent_of, MoneyFormatter.format(progress.spent, currency, prefs.numberGrouping, hideZeroFraction = true), MoneyFormatter.format(progress.budgeted, currency, prefs.numberGrouping, hideZeroFraction = true)),
                    )
                }
            }
        }
        mb.overall?.let { consider("overall", context.getString(R.string.budgets_overall), it) }
        if (categoryId != null) mb.lines.firstOrNull { it.budget.categoryId == categoryId }?.let { consider(it.budget.categoryId, it.categoryName, it.progress) }
        if (changed) settings.setBudgetAlertsNotified(notified.filter { it.startsWith(month.toString()) || it.startsWith(month.minusMonths(1).toString()) }.toSet())
    }
}
