package com.attiqtherehman.kharcha.domain.ledger

import com.attiqtherehman.kharcha.core.datetime.DateRange
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.core.money.sumOfMoney
import com.attiqtherehman.kharcha.domain.model.Account
import com.attiqtherehman.kharcha.domain.model.AccountBalance
import com.attiqtherehman.kharcha.domain.model.AccountType
import com.attiqtherehman.kharcha.domain.model.Transaction

/** Income, spending and savings for a period. Transfers and adjustments never appear here. */
data class PeriodSummary(
    val income: Money,
    val expenses: Money,
    val savedToSavings: Money,
) {
    val cashFlow: Money get() = income - expenses

    /** (income - expenses) / income, or null when there is no income. */
    val savingsRate: Float? get() = if (income.isPositive) cashFlow.ratioTo(income) else null

    companion object {
        val EMPTY = PeriodSummary(Money.ZERO, Money.ZERO, Money.ZERO)
    }
}

/** Totals across accounts; only accounts in the base currency and not archived are included. */
data class NetWorthSummary(
    val spendable: Money,
    val savings: Money,
    val debt: Money,
    val other: Money,
) {
    val netWorth: Money get() = spendable + savings + other - debt

    companion object {
        val EMPTY = NetWorthSummary(Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO)
    }
}

/**
 * In-memory implementations of the report arithmetic. DAO queries implement the same formulas in
 * SQL; tests feed both the same data and compare.
 */
object FinanceCalculator {

    fun summarize(transactions: Iterable<Transaction>, range: DateRange, accountsById: Map<String, Account>): PeriodSummary {
        var income = Money.ZERO
        var expenses = Money.ZERO
        var saved = Money.ZERO
        for (t in transactions) {
            if (t.date !in range) continue
            income += LedgerRules.incomeDelta(t)
            expenses += LedgerRules.spendDelta(t)
            saved += LedgerRules.savedDelta(t, accountsById)
        }
        return PeriodSummary(income, expenses, saved)
    }

    /** Net spend per category id (null key = uncategorised) within [range]. */
    fun spendByCategory(transactions: Iterable<Transaction>, range: DateRange): Map<String?, Money> {
        val out = LinkedHashMap<String?, Money>()
        for (t in transactions) {
            if (t.date !in range) continue
            val delta = LedgerRules.spendDelta(t)
            if (delta.isZero) continue
            out[t.categoryId] = (out[t.categoryId] ?: Money.ZERO) + delta
        }
        return out
    }

    fun netWorth(balances: Iterable<AccountBalance>, baseCurrency: String): NetWorthSummary {
        val eligible = balances.filter { !it.account.isArchived && it.account.currencyCode == baseCurrency }
        val spendable = eligible.filter { it.account.type.isSpendable }.sumOfMoney { it.balance }
        val savings = eligible.filter { it.account.type == AccountType.SAVINGS }.sumOfMoney { it.balance }
        val debt = eligible.filter { it.account.type == AccountType.CREDIT_CARD }.sumOfMoney { it.owed }
        // Positive card balances (overpayment) are money too; include them with "other".
        val cardCredit = eligible.filter { it.account.type == AccountType.CREDIT_CARD && it.balance.isPositive }.sumOfMoney { it.balance }
        val other = eligible.filter { it.account.type == AccountType.OTHER }.sumOfMoney { it.balance } + cardCredit
        return NetWorthSummary(spendable, savings, debt, other)
    }
}
