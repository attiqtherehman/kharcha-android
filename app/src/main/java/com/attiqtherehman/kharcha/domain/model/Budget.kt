package com.attiqtherehman.kharcha.domain.model

import com.attiqtherehman.kharcha.core.money.Money
import java.time.YearMonth

data class MonthlyBudget(
    val id: String,
    val yearMonth: YearMonth,
    val amount: Money?,
    val savingsTarget: Money?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class CategoryBudget(
    val id: String,
    val yearMonth: YearMonth,
    val categoryId: String,
    val amount: Money,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class BudgetWarningLevel { NONE, AT_75, AT_90, OVER }

/** Budgeted vs actual for one line (overall or a category). */
data class BudgetProgress(val budgeted: Money, val spent: Money) {
    val remaining: Money get() = budgeted - spent
    val isOver: Boolean get() = spent > budgeted
    val fraction: Float get() = if (budgeted.isZero) (if (spent.isPositive) 1f else 0f) else spent.ratioTo(budgeted)
    val percent: Int get() = (fraction * 100f).toInt()
    val warningLevel: BudgetWarningLevel
        get() = when {
            budgeted.isZero -> BudgetWarningLevel.NONE
            spent >= budgeted -> BudgetWarningLevel.OVER
            fraction >= 0.90f -> BudgetWarningLevel.AT_90
            fraction >= 0.75f -> BudgetWarningLevel.AT_75
            else -> BudgetWarningLevel.NONE
        }
}
