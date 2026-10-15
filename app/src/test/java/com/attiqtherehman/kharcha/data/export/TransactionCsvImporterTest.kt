package com.attiqtherehman.kharcha.data.export

import com.attiqtherehman.kharcha.TestData
import com.attiqtherehman.kharcha.TestGraph
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.domain.model.AccountType
import com.attiqtherehman.kharcha.domain.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.StringReader

@RunWith(RobolectricTestRunner::class)
class TransactionCsvImporterTest {

    private lateinit var g: TestGraph
    private lateinit var importer: TransactionCsvImporter

    @Before fun setUp() = runTest {
        g = TestGraph()
        g.accounts.create(TestData.account(id = "cash", name = "Cash Wallet", type = AccountType.CASH, opening = 0))
        g.accounts.create(TestData.account(id = "bank", name = "Bank", type = AccountType.BANK, opening = 0))
        g.categories.create(TestData.category(id = "food", name = "Food & Dining"))
        importer = TransactionCsvImporter(g.accounts, g.categories, g.transactions, g.settings, g.clock)
    }
    @After fun tearDown() = g.close()

    private val csv = """
        date,type,amount,account,category,to_account,payee,description,notes,tags
        2026-10-01,EXPENSE,"1,250.50",cash wallet,food & dining,,Cafe,Lunch,,"work, lunch"
        02/10/2026,INCOME,50000,Bank,Salary,,,October pay,,
        2026-10-03,TRANSFER,10000,Bank,,Cash Wallet,,ATM,,
        2026-10-04,EXPENSE,abc,Cash Wallet,Food & Dining,,,,,
        2026-10-05,EXPENSE,10.005,Cash Wallet,Food & Dining,,,,,
        2026-10-06,EXPENSE,500,Unknown Acc,Food & Dining,,,,,
    """.trimIndent()

    @Test
    fun preview_reportsErrorsPerRow_andCreatesMissingNamesWhenAllowed() = runTest {
        val preview = importer.preview(StringReader(csv), createMissing = true)
        assertEquals(6, preview.rows.size)
        assertEquals(4, preview.validCount)
        assertEquals(2, preview.errorCount)
        assertTrue(preview.rows[3].errors.first().contains("invalid amount"))
        assertTrue(preview.rows[4].errors.first().contains("too many decimals"))
        assertEquals(setOf("Unknown Acc"), preview.missingAccounts)
        assertEquals(setOf("Salary|INCOME"), preview.missingCategories)
        assertEquals(Money(125_050), preview.rows[0].transaction!!.amount)
        assertEquals(TransactionType.TRANSFER, preview.rows[2].transaction!!.type)

        val strict = importer.preview(StringReader(csv), createMissing = false)
        assertEquals(2, strict.validCount)
        assertTrue(strict.rows[1].errors.first().contains("unknown category"))
    }

    @Test
    fun import_insertsValidRows_createsMissing_andIsIdempotentOnReimport() = runTest {
        val preview = importer.preview(StringReader(csv), createMissing = true)
        val result = importer.import(preview)
        assertEquals(4, result.imported)
        assertEquals(1, result.createdAccounts)
        assertEquals(1, result.createdCategories)
        assertEquals(4, g.db.transactionDao().count())
        assertEquals(Money(-10_000_00 + 50_000_00), g.accounts.getBalances().first { it.account.id == "bank" }.balance)

        // Export then re-import: ids are carried in the id column, so nothing duplicates.
        val builder = ExportBuilder(g.db.transactionDao(), g.db.accountDao(), g.db.categoryDao(), g.db.budgetDao(), g.db.savingsGoalDao(), g.db.reminderDao(), g.settings)
        val out = java.io.ByteArrayOutputStream()
        builder.writeCsv(out, builder.build(ExportDataset.ALL_TRANSACTIONS, null))
        val again = importer.preview(StringReader(out.toString("UTF-8")), createMissing = true)
        assertEquals(4, again.validCount)
        val second = importer.import(again)
        assertEquals(0, second.imported)
        assertEquals(4, second.skippedExisting)
        assertEquals(4, g.db.transactionDao().count())
    }

    @Test
    fun preview_rejectsMissingRequiredColumns() = runTest {
        val preview = importer.preview(StringReader("date,amount\n2026-01-01,5\n"), createMissing = true)
        assertTrue(preview.headerErrors.first().contains("type"))
        assertEquals(0, preview.rows.size)
    }
}
