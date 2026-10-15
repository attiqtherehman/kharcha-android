package com.attiqtherehman.kharcha.domain.ledger

import com.attiqtherehman.kharcha.TestData
import com.attiqtherehman.kharcha.TestData.adjustment
import com.attiqtherehman.kharcha.TestData.expense
import com.attiqtherehman.kharcha.TestData.income
import com.attiqtherehman.kharcha.TestData.refund
import com.attiqtherehman.kharcha.TestData.transfer
import com.attiqtherehman.kharcha.core.datetime.DateRange
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.core.money.sumOfMoney
import com.attiqtherehman.kharcha.domain.model.AccountBalance
import com.attiqtherehman.kharcha.domain.model.AccountType
import com.attiqtherehman.kharcha.domain.model.TransferKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class LedgerRulesTest {

    private val bank = TestData.account(id = "bank", type = AccountType.BANK, opening = 100_000_00)
    private val cash = TestData.account(id = "cash", type = AccountType.CASH, opening = 5_000_00)
    private val savings = TestData.account(id = "savings", type = AccountType.SAVINGS, opening = 0)
    private val card = TestData.account(id = "card", type = AccountType.CREDIT_CARD, opening = 0, creditLimit = 200_000_00)
    private val accounts = listOf(bank, cash, savings, card).associateBy { it.id }
    private val october = DateRange.ofMonth(YearMonth.of(2026, 10))

    @Test
    fun income_increasesReceivingAccount() {
        val txs = listOf(income(50_000_00, "bank"))
        assertEquals(Money(150_000_00), LedgerRules.balance(bank, txs))
        assertEquals(Money(5_000_00), LedgerRules.balance(cash, txs))
    }

    @Test
    fun expense_decreasesPayingAccount() {
        val txs = listOf(expense(1_200_00, "cash"))
        assertEquals(Money(3_800_00), LedgerRules.balance(cash, txs))
        assertEquals(Money(100_000_00), LedgerRules.balance(bank, txs))
    }

    @Test
    fun transfer_movesMoney_andTotalIsInvariant() {
        val before = listOf(bank, cash, savings, card).sumOfMoney { it.openingBalance }
        val txs = listOf(transfer(10_000_00, "bank", "cash"), transfer(2_500_00, "cash", "savings"))
        assertEquals(Money(90_000_00), LedgerRules.balance(bank, txs))
        assertEquals(Money(12_500_00), LedgerRules.balance(cash, txs))
        assertEquals(Money(2_500_00), LedgerRules.balance(savings, txs))
        val after = listOf(bank, cash, savings, card).sumOfMoney { LedgerRules.balance(it, txs) }
        assertEquals(before, after)
    }

    @Test
    fun transfer_neverCountsAsIncomeOrExpense() {
        val txs = listOf(transfer(10_000_00, "bank", "cash"))
        val summary = FinanceCalculator.summarize(txs, october, accounts)
        assertEquals(Money.ZERO, summary.income)
        assertEquals(Money.ZERO, summary.expenses)
    }

    @Test
    fun atmWithdrawal_transferPlusSeparateFee() {
        val withdrawal = transfer(10_000_00, "bank", "cash", id = "atm")
        val fee = TestData.tx(com.attiqtherehman.kharcha.domain.model.TransactionType.EXPENSE, 25_00, "bank", category = "fees", linked = "atm")
        val txs = listOf(withdrawal, fee)
        assertEquals(Money(89_975_00), LedgerRules.balance(bank, txs))
        assertEquals(Money(15_000_00), LedgerRules.balance(cash, txs))
        val summary = FinanceCalculator.summarize(txs, october, accounts)
        assertEquals("only the fee is spending", Money(25_00), summary.expenses)
        assertEquals(TransferKind.ATM_WITHDRAWAL, LedgerRules.transferKind(withdrawal, accounts, hasLinkedFee = true))
    }

    @Test
    fun openingBalance_isNotIncome() {
        val summary = FinanceCalculator.summarize(emptyList(), october, accounts)
        assertEquals(Money.ZERO, summary.income)
        assertEquals(Money(100_000_00), LedgerRules.balance(bank, emptyList()))
    }

    @Test
    fun savingsTransfer_isNotExpense_butCountsAsSaved() {
        val txs = listOf(transfer(20_000_00, "bank", "savings"), transfer(5_000_00, "savings", "bank"))
        val summary = FinanceCalculator.summarize(txs, october, accounts)
        assertEquals(Money.ZERO, summary.expenses)
        assertEquals(Money(15_000_00), summary.savedToSavings)
        assertEquals(TransferKind.SAVINGS, LedgerRules.transferKind(txs[0], accounts))
    }

    @Test
    fun creditCard_purchaseIsExpense_repaymentIsTransfer() {
        val purchase = expense(30_000_00, "card", category = "shopping")
        val repayment = transfer(30_000_00, "bank", "card")
        val afterPurchase = AccountBalance(card, LedgerRules.balance(card, listOf(purchase)))
        assertEquals(Money(-30_000_00), afterPurchase.balance)
        assertEquals(Money(30_000_00), afterPurchase.owed)
        assertEquals(Money(170_000_00), afterPurchase.availableCredit)

        val txs = listOf(purchase, repayment)
        val summary = FinanceCalculator.summarize(txs, october, accounts)
        assertEquals("purchase counted exactly once", Money(30_000_00), summary.expenses)
        assertEquals(Money.ZERO, LedgerRules.balance(card, txs))
        assertEquals(Money(70_000_00), LedgerRules.balance(bank, txs))
        assertEquals(TransferKind.CARD_REPAYMENT, LedgerRules.transferKind(repayment, accounts))
    }

    @Test
    fun creditCard_overLimit() {
        val over = AccountBalance(card, Money(-250_000_00))
        assertTrue(over.isOverLimit)
        assertEquals(Money(-50_000_00), over.availableCredit)
    }

    @Test
    fun refund_reducesSpend_neverIncome_increasesAccount() {
        val original = expense(4_000_00, "bank", category = "shopping", id = "orig")
        val back = refund(1_000_00, "bank", original = "orig", category = "shopping")
        val txs = listOf(original, back)
        assertEquals(Money(97_000_00), LedgerRules.balance(bank, txs))
        val summary = FinanceCalculator.summarize(txs, october, accounts)
        assertEquals(Money(3_000_00), summary.expenses)
        assertEquals(Money.ZERO, summary.income)
        assertEquals(mapOf<String?, Money>("shopping" to Money(3_000_00)), FinanceCalculator.spendByCategory(txs, october))
    }

    @Test
    fun adjustment_movesNetWorthOnly() {
        val txs = listOf(adjustment(-500_00, "cash"), adjustment(1_000_00, "bank"))
        assertEquals(Money(4_500_00), LedgerRules.balance(cash, txs))
        assertEquals(Money(101_000_00), LedgerRules.balance(bank, txs))
        val summary = FinanceCalculator.summarize(txs, october, accounts)
        assertEquals(PeriodSummary.EMPTY, summary)
    }

    @Test
    fun balanceAsOfDate_excludesLaterRows() {
        val txs = listOf(
            expense(1_000_00, "cash", date = LocalDate.of(2026, 10, 1)),
            expense(2_000_00, "cash", date = LocalDate.of(2026, 10, 20)),
        )
        assertEquals(Money(4_000_00), LedgerRules.balance(cash, txs, asOf = LocalDate.of(2026, 10, 15)))
        assertEquals(Money(2_000_00), LedgerRules.balance(cash, txs))
    }

    @Test
    fun editAndDelete_recomputeNaturally() {
        val t = expense(1_000_00, "cash", id = "t1")
        val edited = t.copy(amount = Money(1_500_00))
        assertEquals(Money(4_000_00), LedgerRules.balance(cash, listOf(t)))
        assertEquals(Money(3_500_00), LedgerRules.balance(cash, listOf(edited)))
        assertEquals(Money(5_000_00), LedgerRules.balance(cash, emptyList()))
        val tr = transfer(1_000_00, "bank", "cash", id = "tr")
        val movedToSavings = tr.copy(toAccountId = "savings")
        assertEquals(Money(6_000_00), LedgerRules.balance(cash, listOf(tr)))
        assertEquals(Money(5_000_00), LedgerRules.balance(cash, listOf(movedToSavings)))
        assertEquals(Money(1_000_00), LedgerRules.balance(savings, listOf(movedToSavings)))
    }

    @Test
    fun netWorth_definitions() {
        val balances = listOf(
            AccountBalance(bank, Money(100_000_00)),
            AccountBalance(cash, Money(5_000_00)),
            AccountBalance(savings, Money(50_000_00)),
            AccountBalance(card, Money(-20_000_00)),
            AccountBalance(TestData.account(id = "usd", type = AccountType.BANK, currency = "USD"), Money(999_00)),
            AccountBalance(TestData.account(id = "old", type = AccountType.CASH, archived = true), Money(777_00)),
        )
        val nw = FinanceCalculator.netWorth(balances, "PKR")
        assertEquals(Money(105_000_00), nw.spendable)
        assertEquals(Money(50_000_00), nw.savings)
        assertEquals(Money(20_000_00), nw.debt)
        assertEquals(Money(135_000_00), nw.netWorth)
    }

    @Test
    fun validation_catchesInvariantBreaks() {
        assertEquals(listOf(LedgerRules.Violation.TO_ACCOUNT_REQUIRED), LedgerRules.validate(transfer(1, "bank", "cash").copy(toAccountId = null), accounts))
        assertEquals(listOf(LedgerRules.Violation.SAME_ACCOUNT), LedgerRules.validate(transfer(1, "bank", "bank"), accounts))
        val usd = TestData.account(id = "usd", currency = "USD")
        assertEquals(listOf(LedgerRules.Violation.CURRENCY_MISMATCH), LedgerRules.validate(transfer(1, "bank", "usd"), accounts + ("usd" to usd)))
        assertEquals(listOf(LedgerRules.Violation.CATEGORY_NOT_ALLOWED), LedgerRules.validate(transfer(1, "bank", "cash").copy(categoryId = "x"), accounts))
        assertEquals(listOf(LedgerRules.Violation.AMOUNT_NOT_POSITIVE), LedgerRules.validate(expense(0, "cash"), accounts))
        assertEquals(listOf(LedgerRules.Violation.ADJUSTMENT_ZERO), LedgerRules.validate(adjustment(0, "cash"), accounts))
        assertEquals(listOf(LedgerRules.Violation.TO_ACCOUNT_NOT_ALLOWED), LedgerRules.validate(expense(5, "cash").copy(toAccountId = "bank"), accounts))
        assertEquals(listOf(LedgerRules.Violation.UNKNOWN_ACCOUNT), LedgerRules.validate(expense(5, "nope"), accounts))
        assertEquals(emptyList<LedgerRules.Violation>(), LedgerRules.validate(adjustment(-5, "cash"), accounts))
        assertEquals(emptyList<LedgerRules.Violation>(), LedgerRules.validate(transfer(5, "bank", "cash"), accounts))
    }
}
