package com.attiqtherehman.kharcha.domain.model

import com.attiqtherehman.kharcha.core.money.Money
import java.time.LocalDate

data class SavingsGoal(
    val id: String,
    val name: String,
    val target: Money,
    /** When set, progress is the account balance; contributions are not counted separately. */
    val linkedAccountId: String? = null,
    val targetDate: LocalDate? = null,
    val notes: String? = null,
    val color: Int,
    val icon: String,
    val completedAt: Long? = null,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

data class GoalContribution(
    val id: String,
    val goalId: String,
    val date: LocalDate,
    /** Signed: withdrawals from a goal are negative. */
    val amount: Money,
    val note: String? = null,
    /** The SAVINGS transfer recorded with this contribution, if any. */
    val transactionId: String? = null,
    val createdAt: Long,
)

data class SavingsGoalProgress(val goal: SavingsGoal, val saved: Money) {
    val remaining: Money get() = if (goal.target > saved) goal.target - saved else Money.ZERO
    val fraction: Float get() = saved.ratioTo(goal.target).coerceIn(0f, 1f)
    val isReached: Boolean get() = saved >= goal.target
}
