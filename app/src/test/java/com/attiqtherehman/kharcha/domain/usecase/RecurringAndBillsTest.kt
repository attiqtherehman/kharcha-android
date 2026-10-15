package com.attiqtherehman.kharcha.domain.usecase

import com.attiqtherehman.kharcha.TestData
import com.attiqtherehman.kharcha.TestGraph
import com.attiqtherehman.kharcha.core.common.AlreadyPaidException
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.domain.model.AccountType
import com.attiqtherehman.kharcha.domain.model.Frequency
import com.attiqtherehman.kharcha.domain.model.Recurrence
import com.attiqtherehman.kharcha.domain.model.RecurringTransaction
import com.attiqtherehman.kharcha.domain.model.Reminder
import com.attiqtherehman.kharcha.domain.model.ReminderStatus
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
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class RecurringAndBillsTest {

    private lateinit var g: TestGraph

    @Before fun setUp() = runTest {
        g = TestGraph()
        g.accounts.create(TestData.account(id = "bank", type = AccountType.BANK, opening = 0))
        g.accounts.create(TestData.account(id = "cash", type = AccountType.CASH, opening = 0))
        g.categories.create(TestData.category(id = "rent"))
    }
    @After fun tearDown() = g.close()

    private fun rule(anchor: LocalDate, cursor: LocalDate = anchor.minusDays(1)) = RecurringTransaction(
        id = "salary", name = "Rent", recurrence = Recurrence(Frequency.MONTHLY, anchorDate = anchor), timeOfDay = LocalTime.of(9, 0),
        lastMaterializedDate = cursor, type = TransactionType.EXPENSE, amount = Money(50_000_00), accountId = "bank", categoryId = "rent",
        createdAt = TestData.NOW, updatedAt = TestData.NOW,
    )

    @Test
    fun materializer_recordsEachOccurrenceOnce_andCatchesUp() = runTest {
        g.recurring.create(rule(LocalDate.of(2026, 1, 31)))
        val materialize = MaterializeRecurringTransactionsUseCase(g.recurring, g.clock)
        val first = materialize(today = LocalDate.of(2026, 4, 15))
        assertEquals(3, first.inserted) // Jan 31, Feb 28, Mar 31 (Apr 30 is after the 15th)
        val dates = g.db.transactionDao().getAll().map { it.localDate }.sorted()
        assertEquals(listOf("2026-01-31", "2026-02-28", "2026-03-31"), dates.take(3))
        assertEquals(3, dates.size)
        val again = materialize(today = LocalDate.of(2026, 4, 15))
        assertEquals(0, again.inserted)
        val later = materialize(today = LocalDate.of(2026, 6, 1))
        assertEquals(2, later.inserted) // Apr 30, May 31
        assertEquals(5, g.db.transactionDao().count())
    }

    @Test
    fun materializer_deletedOccurrence_isNotRegenerated_andUniqueIndexBlocksDuplicates() = runTest {
        g.recurring.create(rule(LocalDate.of(2026, 1, 10)))
        val materialize = MaterializeRecurringTransactionsUseCase(g.recurring, g.clock)
        materialize(today = LocalDate.of(2026, 3, 1))
        val feb = g.db.transactionDao().getAll().first { it.localDate == "2026-02-10" }
        g.transactions.delete(feb.id)
        materialize(today = LocalDate.of(2026, 3, 1))
        assertEquals(1, g.db.transactionDao().getAll().count { it.localDate == "2026-02-10" }.let { if (it == 0) 1 else 0 })
        // Force a replay from an earlier cursor: the unique index still prevents duplicates of existing rows.
        g.db.recurringTransactionDao().updateCursor("salary", "2025-12-31", 0L)
        val replay = materialize(today = LocalDate.of(2026, 3, 1))
        assertEquals("only the deleted February row is re-created on explicit replay", 1, replay.inserted)
        assertEquals(1, g.db.transactionDao().getAll().count { it.localDate == "2026-01-10" })
    }

    @Test
    fun materializer_stopsAtEndDate_andDeactivates() = runTest {
        g.recurring.create(rule(LocalDate.of(2026, 1, 1)).copy(recurrence = Recurrence(Frequency.MONTHLY, anchorDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2026, 2, 1))))
        val materialize = MaterializeRecurringTransactionsUseCase(g.recurring, g.clock)
        materialize(today = LocalDate.of(2026, 6, 1))
        assertEquals(2, g.db.transactionDao().count())
        assertTrue(!g.recurring.get("salary")!!.isActive)
    }

    @Test
    fun markPaid_recordsExpense_advancesClamped_andIsIdempotent() = runTest {
        val reminder = Reminder(
            id = "r", title = "Rent", amount = Money(50_000_00), categoryId = "rent", accountId = "bank", dueDate = LocalDate.of(2026, 1, 31),
            reminderTime = LocalTime.of(9, 0), recurrence = Recurrence(Frequency.MONTHLY, anchorDate = LocalDate.of(2026, 1, 31)),
            createdAt = TestData.NOW, updatedAt = TestData.NOW,
        )
        g.reminders.create(reminder)
        val markPaid = MarkBillPaidUseCase(g.reminders, g.accounts, g.clock)
        val payment = markPaid("r", Money(50_000_00), LocalDate.of(2026, 1, 30), "bank", createExpense = true)
        assertTrue(payment.transactionId != null)
        assertEquals(Money(-50_000_00), g.accounts.getBalances().first { it.account.id == "bank" }.balance)
        assertEquals(LocalDate.of(2026, 2, 28), g.reminders.get("r")!!.dueDate)
        // Second payment for the same (original) due date is impossible: the reminder has moved on and the unique key guards the table.
        g.reminders.update(g.reminders.get("r")!!.copy(dueDate = LocalDate.of(2026, 1, 31)))
        assertTrue(runCatching { markPaid("r", Money(1), LocalDate.of(2026, 1, 31), "bank", createExpense = false) }.exceptionOrNull() is AlreadyPaidException)
        assertEquals(1, g.db.reminderDao().getAllPayments().size)
        // Advance twice more: Feb 28 -> Mar 31 -> Apr 30 (no drift).
        g.reminders.update(g.reminders.get("r")!!.copy(dueDate = LocalDate.of(2026, 2, 28)))
        markPaid("r", Money(1), LocalDate.of(2026, 2, 28), null, createExpense = false)
        assertEquals(LocalDate.of(2026, 3, 31), g.reminders.get("r")!!.dueDate)
        markPaid("r", Money(1), LocalDate.of(2026, 3, 31), null, createExpense = false)
        assertEquals(LocalDate.of(2026, 4, 30), g.reminders.get("r")!!.dueDate)
    }

    @Test
    fun markPaid_oneTimeReminder_completes() = runTest {
        g.reminders.create(Reminder(id = "o", title = "Fee", dueDate = LocalDate.of(2026, 5, 5), reminderTime = LocalTime.NOON, createdAt = 0, updatedAt = 0))
        MarkBillPaidUseCase(g.reminders, g.accounts, g.clock)("o", Money(100), LocalDate.of(2026, 5, 5), null, createExpense = false)
        assertEquals(ReminderStatus.COMPLETED, g.reminders.get("o")!!.status)
    }
}
