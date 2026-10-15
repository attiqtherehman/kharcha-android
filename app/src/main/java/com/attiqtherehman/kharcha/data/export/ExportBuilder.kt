package com.attiqtherehman.kharcha.data.export

import com.attiqtherehman.kharcha.core.datetime.DateRange
import com.attiqtherehman.kharcha.core.money.CurrencyInfo
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.data.local.dao.AccountDao
import com.attiqtherehman.kharcha.data.local.dao.BudgetDao
import com.attiqtherehman.kharcha.data.local.dao.CategoryDao
import com.attiqtherehman.kharcha.data.local.dao.ReminderDao
import com.attiqtherehman.kharcha.data.local.dao.SavingsGoalDao
import com.attiqtherehman.kharcha.data.local.dao.TransactionDao
import com.attiqtherehman.kharcha.data.mapper.splitTags
import com.attiqtherehman.kharcha.data.prefs.SettingsDataStore
import com.attiqtherehman.kharcha.domain.model.TransactionFilter
import com.attiqtherehman.kharcha.domain.model.TransactionType
import com.attiqtherehman.kharcha.data.local.TransactionQueryBuilder
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class ExportDataset { ALL_TRANSACTIONS, INCOME, EXPENSES, TRANSFERS, ACCOUNTS, BUDGETS, GOALS, REMINDERS, PAYMENTS }

/** Builds typed sheets for CSV and XLSX export from the database. */
class ExportBuilder @Inject constructor(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val goalDao: SavingsGoalDao,
    private val reminderDao: ReminderDao,
    private val settings: SettingsDataStore,
) {
    private fun Money.cell(currency: CurrencyInfo) = Cell.Number(toBigDecimal(currency.minorDigits))

    suspend fun build(dataset: ExportDataset, range: DateRange?): Sheet {
        val prefs = settings.current()
        val base = CurrencyInfo.of(prefs.baseCurrency)
        return when (dataset) {
            ExportDataset.ALL_TRANSACTIONS -> transactions("Transactions", null, range)
            ExportDataset.INCOME -> transactions("Income", setOf(TransactionType.INCOME), range)
            ExportDataset.EXPENSES -> transactions("Expenses", setOf(TransactionType.EXPENSE, TransactionType.REFUND), range)
            ExportDataset.TRANSFERS -> transactions("Transfers", setOf(TransactionType.TRANSFER), range)
            ExportDataset.ACCOUNTS -> {
                val rows = accountDao.getBalances().map { r ->
                    val c = CurrencyInfo.of(r.account.currencyCode)
                    listOf(
                        Cell.Text(r.account.id), Cell.Text(r.account.name), Cell.Text(r.account.type), Cell.Text(r.account.currencyCode),
                        Money(r.account.openingBalanceMinor).cell(c), r.account.creditLimitMinor?.let { Money(it).cell(c) } ?: Cell.Empty,
                        Money(r.balanceMinor).cell(c), Cell.Text(if (r.account.isArchived) "yes" else "no"), Cell.Text(r.account.notes ?: ""),
                    )
                }
                Sheet("Accounts", listOf("id", "name", "type", "currency", "opening_balance", "credit_limit", "balance", "archived", "notes"), rows)
            }
            ExportDataset.BUDGETS -> {
                val cats = categoryDao.getAll().associateBy { it.id }
                val monthly = budgetDao.getAllMonthly().map { m ->
                    listOf(Cell.Text(m.yearMonth), Cell.Text(""), Cell.Text(""), m.amountMinor?.let { Money(it).cell(base) } ?: Cell.Empty, m.savingsTargetMinor?.let { Money(it).cell(base) } ?: Cell.Empty, Cell.Text(m.id))
                }
                val perCategory = budgetDao.getAllCategoryBudgets().map { b ->
                    listOf(Cell.Text(b.yearMonth), Cell.Text(cats[b.categoryId]?.name ?: ""), Cell.Text(b.categoryId), Money(b.amountMinor).cell(base), Cell.Empty, Cell.Text(b.id))
                }
                Sheet("Budgets", listOf("month", "category", "category_id", "amount", "savings_target", "id"), monthly + perCategory)
            }
            ExportDataset.GOALS -> {
                val accounts = accountDao.getAll().associateBy { it.id }
                val rows = goalDao.getAll().map { g ->
                    listOf(Cell.Text(g.id), Cell.Text(g.name), Money(g.targetMinor).cell(base), g.targetDate?.let { Cell.Date(LocalDate.parse(it)) } ?: Cell.Empty, Cell.Text(g.linkedAccountId?.let { accounts[it]?.name } ?: ""), Cell.Text(g.linkedAccountId ?: ""), Cell.Text(if (g.completedAt != null) "yes" else "no"), Cell.Text(g.notes ?: ""))
                }
                Sheet("Savings goals", listOf("id", "name", "target", "target_date", "linked_account", "linked_account_id", "completed", "notes"), rows)
            }
            ExportDataset.REMINDERS -> {
                val cats = categoryDao.getAll().associateBy { it.id }
                val accounts = accountDao.getAll().associateBy { it.id }
                val rows = reminderDao.getAll().map { r ->
                    listOf(Cell.Text(r.id), Cell.Text(r.title), r.amountMinor?.let { Money(it).cell(base) } ?: Cell.Empty, Cell.Date(LocalDate.parse(r.dueDate)), Cell.Text(r.reminderTime), Cell.Text(r.frequency), Cell.Number(r.intervalCount.toBigDecimal()), Cell.Text(r.endDate ?: ""), Cell.Text(r.advanceDays), Cell.Text(r.categoryId?.let { cats[it]?.name } ?: ""), Cell.Text(r.accountId?.let { accounts[it]?.name } ?: ""), Cell.Text(r.status), Cell.Text(r.notes ?: ""))
                }
                Sheet("Bill reminders", listOf("id", "title", "amount", "due_date", "time", "repeat", "interval", "end_date", "advance_days", "category", "account", "status", "notes"), rows)
            }
            ExportDataset.PAYMENTS -> {
                val reminders = reminderDao.getAll().associateBy { it.id }
                val rows = reminderDao.getAllPayments().map { p ->
                    listOf(Cell.Text(p.id), Cell.Text(reminders[p.reminderId]?.title ?: ""), Cell.Text(p.reminderId), Cell.Date(LocalDate.parse(p.dueDate)), Cell.Date(Instant.ofEpochMilli(p.paidAt).atZone(ZoneId.systemDefault()).toLocalDate()), Money(p.amountMinor).cell(base), Cell.Text(p.transactionId ?: ""), Cell.Text(p.notes ?: ""))
                }
                Sheet("Payments", listOf("id", "reminder", "reminder_id", "due_date", "paid_on", "amount", "transaction_id", "notes"), rows)
            }
        }
    }

    private suspend fun transactions(name: String, types: Set<TransactionType>?, range: DateRange?): Sheet {
        val rows = transactionDao.getRows(TransactionQueryBuilder.build(TransactionFilter(types = types, range = range)))
            .sortedWith(compareBy({ it.tx.localDate }, { it.tx.localTime }))
            .map { r ->
                val c = CurrencyInfo.of(r.accountCurrency)
                val t = r.tx
                listOf(
                    Cell.Date(LocalDate.parse(t.localDate)), Cell.Text(t.localTime), Cell.Text(t.type), Money(t.amountMinor).cell(c), Cell.Text(r.accountCurrency),
                    Cell.Text(r.accountName), Cell.Text(r.toAccountName ?: ""), Cell.Text(r.categoryName ?: ""), Cell.Text(t.payee ?: ""),
                    Cell.Text(t.description ?: ""), Cell.Text(t.notes ?: ""), Cell.Text(splitTags(t.tags).joinToString(", ")),
                    Cell.Text(t.id), Cell.Text(t.accountId), Cell.Text(t.toAccountId ?: ""), Cell.Text(t.categoryId ?: ""), Cell.Text(t.linkedTransactionId ?: ""),
                )
            }
        return Sheet(
            name,
            listOf("date", "time", "type", "amount", "currency", "account", "to_account", "category", "payee", "description", "notes", "tags", "id", "account_id", "to_account_id", "category_id", "linked_transaction_id"),
            rows,
        )
    }

    fun writeCsv(out: OutputStream, sheet: Sheet) {
        val w = CsvWriter.open(out)
        w.writeRow(sheet.headers)
        sheet.rows.forEach { row ->
            w.writeRow(
                row.map { cell ->
                    when (cell) {
                        is Cell.Text -> cell.value
                        is Cell.Number -> cell.value.toPlainString()
                        is Cell.Date -> cell.value.toString()
                        Cell.Empty -> ""
                    }
                },
            )
        }
        w.flush()
    }
}
