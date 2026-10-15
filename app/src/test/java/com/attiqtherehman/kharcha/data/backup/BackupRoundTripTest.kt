package com.attiqtherehman.kharcha.data.backup

import com.attiqtherehman.kharcha.TestData
import com.attiqtherehman.kharcha.TestGraph
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.data.mapper.toEntity
import com.attiqtherehman.kharcha.domain.model.AccountType
import com.attiqtherehman.kharcha.domain.model.BillPayment
import com.attiqtherehman.kharcha.domain.model.CategoryBudget
import com.attiqtherehman.kharcha.domain.model.Frequency
import com.attiqtherehman.kharcha.domain.model.Recurrence
import com.attiqtherehman.kharcha.domain.model.Reminder
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class BackupRoundTripTest {

    private lateinit var source: TestGraph
    private lateinit var target: TestGraph

    @Before fun setUp() { source = TestGraph(); target = TestGraph() }
    @After fun tearDown() { source.close(); target.close() }

    private suspend fun seed(g: TestGraph) {
        g.seed()
        g.accounts.create(TestData.account(id = "bank", name = "Bank", type = AccountType.BANK, opening = 100_000_00))
        g.accounts.create(TestData.account(id = "cash", name = "Cash", type = AccountType.CASH, opening = 1_000_00))
        val food = g.categories.getAll().first { it.name == "Food & Dining" }
        g.transactions.insertAll(
            listOf(
                TestData.expense(250_00, "cash", category = food.id, id = "e1"),
                TestData.transfer(5_000_00, "bank", "cash", id = "t1"),
                TestData.tx(com.attiqtherehman.kharcha.domain.model.TransactionType.EXPENSE, 25_00, "bank", linked = "t1", id = "fee1"),
            ),
        )
        g.budgets.setCategoryBudget(YearMonth.of(2026, 10), food.id, Money(10_000_00))
        g.reminders.create(
            Reminder(
                id = "r1", title = "Electricity", amount = Money(4_500_00), dueDate = LocalDate.of(2026, 10, 20), reminderTime = LocalTime.of(9, 0),
                recurrence = Recurrence(Frequency.MONTHLY, anchorDate = LocalDate.of(2026, 10, 20)), createdAt = TestData.NOW, updatedAt = TestData.NOW,
            ),
        )
        g.reminders.markPaid(
            g.reminders.get("r1")!!.copy(dueDate = LocalDate.of(2026, 11, 20)),
            BillPayment(id = "p1", reminderId = "r1", dueDate = LocalDate.of(2026, 10, 20), paidAt = TestData.NOW, amount = Money(4_500_00)),
            null,
        )
        g.dayReviews.markReviewed(LocalDate.of(2026, 10, 14))
    }

    private suspend fun writeArchive(g: TestGraph): File {
        val attachments = AttachmentStore(g.context)
        val writer = BackupWriter(g.db, g.settings, attachments, g.clock)
        val bytes = ByteArrayOutputStream().also { writer.write(it) }.toByteArray()
        return File.createTempFile("kharcha", ".zip").apply { writeBytes(bytes) }
    }

    @Test
    fun replace_restoresEveryTableExactly() = runTest {
        seed(source)
        val archive = writeArchive(source)
        val contents = BackupReader(target.context).read(archive)
        assertEquals(source.db.transactionDao().count(), contents.transactions.size)

        // Target has unrelated data that must disappear.
        target.accounts.create(TestData.account(id = "other", type = AccountType.OTHER, opening = 77))
        val report = BackupRestorer(target.db, target.settings, AttachmentStore(target.context), target.hub).restore(contents, RestoreMode.REPLACE, MergePolicy.SKIP)
        assertEquals(0, report.totalInvalid)
        assertEquals(null, target.accounts.getAccount("other"))
        assertEquals(source.db.accountDao().getAll().sortedBy { it.id }, target.db.accountDao().getAll().sortedBy { it.id })
        assertEquals(source.db.categoryDao().getAll().sortedBy { it.id }, target.db.categoryDao().getAll().sortedBy { it.id })
        assertEquals(source.db.transactionDao().getAll().sortedBy { it.id }, target.db.transactionDao().getAll().sortedBy { it.id })
        assertEquals(source.db.budgetDao().getAllCategoryBudgets().sortedBy { it.id }, target.db.budgetDao().getAllCategoryBudgets().sortedBy { it.id })
        assertEquals(source.db.reminderDao().getAll().sortedBy { it.id }, target.db.reminderDao().getAll().sortedBy { it.id })
        assertEquals(source.db.reminderDao().getAllPayments().sortedBy { it.id }, target.db.reminderDao().getAllPayments().sortedBy { it.id })
        assertEquals(source.db.dayReviewDao().getAll().sortedBy { it.localDate }, target.db.dayReviewDao().getAll().sortedBy { it.localDate })
        // Linked transfer/fee relationship survives.
        assertEquals("t1", target.transactions.get("fee1")!!.linkedTransactionId)
        // Balances recompute identically.
        assertEquals(source.accounts.getBalances().map { it.balance }, target.accounts.getBalances().map { it.balance })
    }

    @Test
    fun invalidArchive_isRejected_andLeavesDatabaseUntouched() = runTest {
        seed(target)
        val before = target.db.transactionDao().getAll()
        val broken = File.createTempFile("broken", ".zip")
        ZipOutputStream(broken.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write("{\"formatVersion\":1".toByteArray()); zip.closeEntry()
        }
        assertTrue(runCatching { BackupReader(target.context).read(broken) }.exceptionOrNull() is BackupException)
        val notZip = File.createTempFile("notzip", ".zip").apply { writeText("hello") }
        assertTrue(runCatching { BackupReader(target.context).read(notZip) }.exceptionOrNull() is BackupException)
        assertEquals(before, target.db.transactionDao().getAll())
    }

    @Test
    fun newerFormat_isRefused() = runTest {
        seed(source)
        val archive = writeArchive(source)
        val newer = File.createTempFile("newer", ".zip")
        java.util.zip.ZipFile(archive).use { zin ->
            ZipOutputStream(newer.outputStream()).use { zout ->
                for (e in zin.entries()) {
                    zout.putNextEntry(ZipEntry(e.name))
                    val bytes = zin.getInputStream(e).readBytes()
                    if (e.name == "manifest.json") zout.write(String(bytes).replace("\"formatVersion\":1", "\"formatVersion\":99").toByteArray()) else zout.write(bytes)
                    zout.closeEntry()
                }
            }
        }
        val error = runCatching { BackupReader(target.context).read(newer) }.exceptionOrNull()
        assertEquals("newer", error?.message)
    }

    @Test
    fun merge_insertsNew_skipsExisting_neverMatchesByAmount() = runTest {
        seed(source)
        seed(target) // same seeded categories/accounts (deterministic ids) and same transactions
        target.transactions.insertAll(listOf(TestData.expense(250_00, "cash", id = "local-only")))
        source.transactions.insertAll(listOf(TestData.expense(250_00, "cash", id = "source-only"))) // same amount as local-only
        val contents = BackupReader(target.context).read(writeArchive(source))
        val report = BackupRestorer(target.db, target.settings, AttachmentStore(target.context), target.hub).restore(contents, RestoreMode.MERGE, MergePolicy.SKIP)
        val tx = report.tables.getValue("transactions")
        assertEquals(1, tx.inserted)
        assertEquals(3, tx.skipped)
        assertEquals(0, tx.invalid.size)
        assertEquals(5, target.db.transactionDao().count())
        assertEquals(1, report.tables.getValue("bill_payments").skipped)
    }

    @Test
    fun merge_updateIfNewer_usesUpdatedAt() = runTest {
        seed(source)
        seed(target)
        val edited = source.transactions.get("e1")!!.copy(amount = Money(999_00), updatedAt = TestData.NOW + 10_000)
        source.db.transactionDao().update(edited.toEntity())
        val contents = BackupReader(target.context).read(writeArchive(source))
        val restorer = BackupRestorer(target.db, target.settings, AttachmentStore(target.context), target.hub)
        val skip = restorer.restore(contents, RestoreMode.MERGE, MergePolicy.SKIP)
        assertEquals(Money(250_00), target.transactions.get("e1")!!.amount)
        assertEquals(0, skip.tables.getValue("transactions").updated)
        val newer = restorer.restore(contents, RestoreMode.MERGE, MergePolicy.UPDATE_IF_NEWER)
        assertEquals(1, newer.tables.getValue("transactions").updated)
        assertEquals(Money(999_00), target.transactions.get("e1")!!.amount)
    }

    @Test
    fun merge_missingHardReference_isReportedInvalid_softReferenceIsNulled() = runTest {
        seed(source)
        source.accounts.create(TestData.account(id = "ghost", type = AccountType.BANK, opening = 0))
        source.transactions.insertAll(listOf(TestData.expense(10, "ghost", id = "ghost-tx")))
        val archive = writeArchive(source)
        // Remove the ghost account from the archive to simulate a damaged/partial backup.
        val contents = BackupReader(target.context).read(archive)
        val damaged = BackupContents(
            manifest = contents.manifest, settings = contents.settings, accounts = contents.accounts.filter { it.id != "ghost" }, categories = contents.categories,
            recurring = contents.recurring, transactions = contents.transactions, goals = contents.goals, contributions = contents.contributions,
            reminders = contents.reminders, payments = contents.payments, monthlyBudgets = contents.monthlyBudgets, categoryBudgets = contents.categoryBudgets,
            dayReviews = contents.dayReviews, attachmentNames = contents.attachmentNames, archiveFile = contents.archiveFile,
        )
        val report = BackupRestorer(target.db, target.settings, AttachmentStore(target.context), target.hub).restore(damaged, RestoreMode.MERGE, MergePolicy.SKIP)
        val invalid = report.tables.getValue("transactions").invalid
        assertEquals(listOf("ghost-tx"), invalid.map { it.id })
        assertEquals(null, target.transactions.get("ghost-tx"))
        assertTrue(target.transactions.get("fee1") != null)
    }
}
