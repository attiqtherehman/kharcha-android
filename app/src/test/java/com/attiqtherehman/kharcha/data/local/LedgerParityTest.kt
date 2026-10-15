package com.attiqtherehman.kharcha.data.local

import com.attiqtherehman.kharcha.TestData
import com.attiqtherehman.kharcha.TestGraph
import com.attiqtherehman.kharcha.core.datetime.DateRange
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.domain.ledger.FinanceCalculator
import com.attiqtherehman.kharcha.domain.ledger.LedgerRules
import com.attiqtherehman.kharcha.domain.model.AccountType
import com.attiqtherehman.kharcha.domain.model.Transaction
import com.attiqtherehman.kharcha.domain.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.YearMonth
import kotlin.random.Random

/** The SQL view and the Kotlin ledger rules must agree on every balance and every report figure. */
@RunWith(RobolectricTestRunner::class)
class LedgerParityTest {

    private lateinit var g: TestGraph

    @Before fun setUp() { g = TestGraph() }
    @After fun tearDown() { g.close() }

    @Test
    fun sqlBalances_matchKotlinRules_onRandomLedger() = runTest {
        val accounts = listOf(
            TestData.account(id = "cash", type = AccountType.CASH, opening = 5_000_00),
            TestData.account(id = "bank", type = AccountType.BANK, opening = 250_000_00),
            TestData.account(id = "sav", type = AccountType.SAVINGS, opening = 0),
            TestData.account(id = "card", type = AccountType.CREDIT_CARD, opening = -10_000_00, creditLimit = 100_000_00),
        )
        accounts.forEach { g.accounts.create(it) }
        val cats = listOf(TestData.category(id = "food"), TestData.category(id = "shop"), TestData.category(id = "salary", kind = com.attiqtherehman.kharcha.domain.model.CategoryKind.INCOME))
        cats.forEach { g.categories.create(it) }
        val rnd = Random(42)
        val ids = accounts.map { it.id }
        val txs = ArrayList<Transaction>()
        repeat(300) { i ->
            val date = LocalDate.of(2026, 1, 1).plusDays(rnd.nextInt(0, 300).toLong())
            val amount = rnd.nextLong(1, 50_000_00)
            val from = ids.random(rnd)
            val tx = when (rnd.nextInt(5)) {
                0 -> TestData.income(amount, from, category = "salary", date = date)
                1, 2 -> TestData.expense(amount, from, category = listOf("food", "shop").random(rnd), date = date)
                3 -> TestData.transfer(amount, from, (ids - from).random(rnd), date = date)
                else -> TestData.adjustment(if (rnd.nextBoolean()) amount else -amount, from, date = date)
            }
            txs += tx
        }
        // A few refunds linked to expenses.
        txs.filter { it.type == TransactionType.EXPENSE }.take(10).forEach { e ->
            txs += TestData.refund(e.amount.minor / 2 + 1, e.accountId, original = e.id, category = e.categoryId, date = e.date.plusDays(3))
        }
        g.transactions.insertAll(txs)

        val sql = g.accounts.getBalances().associate { it.account.id to it.balance }
        for (a in accounts) {
            assertEquals("balance of ${a.id}", LedgerRules.balance(a, txs), sql.getValue(a.id))
        }
        val sumOpening = accounts.sumOf { it.openingBalance.minor }
        val transfersOnly = txs.filter { it.type == TransactionType.TRANSFER }
        // Transfers never change the total across accounts.
        assertEquals(sumOpening, accounts.sumOf { LedgerRules.balance(it, transfersOnly).minor })

        for (month in 1..10) {
            val range = DateRange.ofMonth(YearMonth.of(2026, month))
            val kotlin = FinanceCalculator.summarize(txs, range, accounts.associateBy { it.id })
            val sqlSummary = g.transactions.periodSummary(range)
            assertEquals("income $month", kotlin.income, sqlSummary.income)
            assertEquals("expenses $month", kotlin.expenses, sqlSummary.expenses)
            assertEquals("saved $month", kotlin.savedToSavings, sqlSummary.savedToSavings)
            val kotlinSpend = FinanceCalculator.spendByCategory(txs, range)
            val sqlSpend = g.transactions.spendByCategory(range)
            assertEquals("spend by category $month", kotlinSpend.filterValues { !it.isZero }, sqlSpend.filterValues { !it.isZero })
            assertEquals("total spend $month", kotlinSpend.values.fold(Money.ZERO) { a, b -> a + b }, g.transactions.totalSpend(range))
        }
        val asOf = LocalDate.of(2026, 6, 15)
        for (a in accounts) assertEquals("as-of ${a.id}", LedgerRules.balance(a, txs, asOf), g.accounts.balanceAsOf(a.id, asOf))
    }

    @Test
    fun editAndDelete_recomputeBalances() = runTest {
        g.accounts.create(TestData.account(id = "cash", type = AccountType.CASH, opening = 1_000_00))
        g.accounts.create(TestData.account(id = "bank", type = AccountType.BANK, opening = 0))
        val t = TestData.transfer(400_00, "bank", "cash", id = "t1")
        g.transactions.insertAll(listOf(t))
        assertEquals(Money(1_400_00), g.accounts.getBalances().first { it.account.id == "cash" }.balance)
        g.transactions.update(t.copy(amount = Money(100_00)))
        assertEquals(Money(1_100_00), g.accounts.getBalances().first { it.account.id == "cash" }.balance)
        assertEquals(Money(-100_00), g.accounts.getBalances().first { it.account.id == "bank" }.balance)
        g.transactions.delete("t1")
        assertEquals(Money(1_000_00), g.accounts.getBalances().first { it.account.id == "cash" }.balance)
        assertEquals(Money.ZERO, g.accounts.getBalances().first { it.account.id == "bank" }.balance)
    }

    @Test
    fun failedMultiRowInsert_rollsBackCompletely() = runTest {
        g.accounts.create(TestData.account(id = "cash", type = AccountType.CASH, opening = 0))
        val good = TestData.expense(100, "cash", id = "ok")
        val bad = TestData.expense(100, "missing-account", id = "bad") // FK violation
        val failed = runCatching { g.transactions.insertAll(listOf(good, bad)) }
        assertTrue(failed.isFailure)
        assertEquals(null, g.transactions.get("ok"))
        assertEquals(Money.ZERO, g.accounts.getBalances().first().balance)
    }

    @Test
    fun accountDelete_refusedWhileReferenced_andArchiveKeepsHistory() = runTest {
        g.accounts.create(TestData.account(id = "cash", type = AccountType.CASH, opening = 0))
        g.transactions.insertAll(listOf(TestData.expense(5, "cash")))
        assertTrue(runCatching { g.accounts.delete("cash") }.exceptionOrNull() is com.attiqtherehman.kharcha.core.common.HasReferencesException)
        g.accounts.setArchived("cash", true)
        assertEquals(1, g.accounts.transactionCount("cash"))
        assertTrue(g.accounts.getAccount("cash")!!.isArchived)
    }

    @Test
    fun atmWithdrawal_isTransferPlusLinkedFee() = runTest {
        g.accounts.create(TestData.account(id = "cash", type = AccountType.CASH, opening = 0))
        g.accounts.create(TestData.account(id = "bank", type = AccountType.BANK, opening = 20_000_00))
        g.categories.create(TestData.category(id = com.attiqtherehman.kharcha.data.seed.DefaultData.BANK_FEES_CATEGORY_ID, name = "Bank Fees"))
        val rows = g.recordTransfer(TestData.transfer(10_000_00, "bank", "cash", id = "atm"), fee = Money(25_00))
        assertEquals(2, rows.size)
        assertEquals("atm", rows[1].linkedTransactionId)
        val balances = g.accounts.getBalances().associate { it.account.id to it.balance }
        assertEquals(Money(9_975_00), balances["bank"])
        assertEquals(Money(10_000_00), balances["cash"])
        val month = DateRange.ofMonth(YearMonth.from(TestData.DAY))
        assertEquals(Money(25_00), g.transactions.periodSummary(month).expenses)
        // Deleting the transfer with its linked fee removes both.
        g.transactions.deleteWithLinked("atm")
        assertEquals(Money(20_000_00), g.accounts.getBalances().first { it.account.id == "bank" }.balance)
        assertEquals(Money.ZERO, g.transactions.periodSummary(month).expenses)
    }

    @Test
    fun refund_cannotExceedRemaining() = runTest {
        g.accounts.create(TestData.account(id = "bank", type = AccountType.BANK, opening = 0))
        g.transactions.insertAll(listOf(TestData.expense(1_000, "bank", id = "orig")))
        g.recordRefund(TestData.refund(600, "bank", original = "orig"))
        val second = runCatching { g.recordRefund(TestData.refund(600, "bank", original = "orig")) }
        assertTrue(second.exceptionOrNull() is com.attiqtherehman.kharcha.domain.usecase.RefundExceedsOriginalException)
        g.recordRefund(TestData.refund(400, "bank", original = "orig"))
        assertEquals(Money.ZERO, g.accounts.getBalances().first().balance)
    }
}
