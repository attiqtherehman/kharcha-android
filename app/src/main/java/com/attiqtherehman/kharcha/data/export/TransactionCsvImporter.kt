package com.attiqtherehman.kharcha.data.export

import com.attiqtherehman.kharcha.core.common.Ids
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.core.money.CurrencyInfo
import com.attiqtherehman.kharcha.core.money.MoneyParseResult
import com.attiqtherehman.kharcha.core.money.MoneyParser
import com.attiqtherehman.kharcha.data.prefs.SettingsDataStore
import com.attiqtherehman.kharcha.data.seed.DefaultData
import com.attiqtherehman.kharcha.domain.ledger.LedgerRules
import com.attiqtherehman.kharcha.domain.model.Account
import com.attiqtherehman.kharcha.domain.model.AccountType
import com.attiqtherehman.kharcha.domain.model.Category
import com.attiqtherehman.kharcha.domain.model.CategoryKind
import com.attiqtherehman.kharcha.domain.model.Transaction
import com.attiqtherehman.kharcha.domain.model.TransactionType
import com.attiqtherehman.kharcha.domain.repository.AccountRepository
import com.attiqtherehman.kharcha.domain.repository.CategoryRepository
import com.attiqtherehman.kharcha.domain.repository.TransactionRepository
import java.io.Reader
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Parses a transaction CSV into a preview (valid rows + per-row errors) and imports the valid
 * rows. Accounts and categories are matched by name (case-insensitive); unknown names are
 * either created (option) or reported as errors. Rows that carry an `id` column keep that id,
 * so re-importing the same file never duplicates (existing ids are skipped).
 */
class TransactionCsvImporter @Inject constructor(
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val transactions: TransactionRepository,
    private val settings: SettingsDataStore,
    private val clock: Clock,
) {
    data class ParsedRow(val line: Int, val transaction: Transaction?, val errors: List<String>, val newAccount: String?, val newToAccount: String?, val newCategory: String?)

    data class Preview(
        val rows: List<ParsedRow>,
        val missingAccounts: Set<String>,
        val missingCategories: Set<String>,
        val headerErrors: List<String>,
    ) {
        val validCount: Int get() = rows.count { it.transaction != null }
        val errorCount: Int get() = rows.count { it.transaction == null }
    }

    data class Result(val imported: Int, val skippedExisting: Int, val createdAccounts: Int, val createdCategories: Int)

    private val dateFormats = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("d/M/yyyy"), DateTimeFormatter.ofPattern("d-M-yyyy"),
        DateTimeFormatter.ofPattern("yyyy/M/d"), DateTimeFormatter.ofPattern("d MMM yyyy"), DateTimeFormatter.ofPattern("MMM d, yyyy"),
    )

    suspend fun preview(reader: Reader, createMissing: Boolean): Preview {
        val prefs = settings.current()
        val currency = CurrencyInfo.of(prefs.baseCurrency)
        val csv = CsvReader(reader)
        val header = csv.readRow()?.map { it.trim().lowercase().replace(' ', '_') } ?: return Preview(emptyList(), emptySet(), emptySet(), listOf("The file is empty"))
        val required = listOf("date", "type", "amount", "account")
        val missingHeaders = required.filter { it !in header }
        if (missingHeaders.isNotEmpty()) return Preview(emptyList(), emptySet(), emptySet(), listOf("Missing columns: ${missingHeaders.joinToString()}"))
        val idx = header.withIndex().associate { it.value to it.index }
        fun List<String>.col(name: String): String? = idx[name]?.let { getOrNull(it)?.trim()?.takeIf { v -> v.isNotEmpty() } }

        val accountByName = accounts.getAccounts().filter { !it.isArchived }.associateBy { it.name.lowercase() }
        val categoryByName = categories.getAll().filter { !it.isArchived }.associateBy { it.name.lowercase() + "|" + it.kind.name }
        val missingAccounts = LinkedHashSet<String>()
        val missingCategories = LinkedHashSet<String>()
        val rows = ArrayList<ParsedRow>()
        var line = 1
        while (true) {
            val raw = csv.readRow() ?: break
            line++
            val errors = ArrayList<String>()
            val date = raw.col("date")?.let(::parseDate) ?: run { errors += "invalid date"; null }
            val type = raw.col("type")?.uppercase()?.let { t -> TransactionType.entries.firstOrNull { it.name == t } }
                ?: run { errors += "type must be INCOME, EXPENSE, TRANSFER or REFUND"; null }
            if (type == TransactionType.ADJUSTMENT) errors += "adjustments cannot be imported"
            val amount = raw.col("amount")?.let { MoneyParser.parse(it, currency) }.let { r ->
                when (r) {
                    is MoneyParseResult.Success -> r.money.abs().takeIf { it.isPositive } ?: run { errors += "amount must be greater than zero"; null }
                    is MoneyParseResult.Failure -> { errors += "invalid amount (${r.reason.name.lowercase().replace('_', ' ')})"; null }
                    null -> { errors += "amount missing"; null }
                }
            }
            val accountName = raw.col("account")
            val account = accountName?.let { accountByName[it.lowercase()] }
            var newAccount: String? = null
            if (accountName == null) errors += "account missing"
            else if (account == null) { if (createMissing) { newAccount = accountName; missingAccounts += accountName } else errors += "unknown account \"$accountName\"" }
            val toName = raw.col("to_account")
            val toAccount = toName?.let { accountByName[it.lowercase()] }
            var newToAccount: String? = null
            if (type == TransactionType.TRANSFER) {
                if (toName == null) errors += "to_account missing for transfer"
                else if (toAccount == null) { if (createMissing) { newToAccount = toName; missingAccounts += toName } else errors += "unknown account \"$toName\"" }
            }
            val categoryName = raw.col("category")
            val kind = if (type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
            val category = categoryName?.let { categoryByName[it.lowercase() + "|" + kind.name] }
            var newCategory: String? = null
            if (categoryName != null && category == null && type?.usesCategory == true) {
                if (createMissing) { newCategory = categoryName; missingCategories += "$categoryName|${kind.name}" } else errors += "unknown category \"$categoryName\""
            }
            val time = raw.col("time")?.let { runCatching { LocalTime.parse(it.take(5)) }.getOrNull() } ?: LocalTime.NOON
            val id = raw.col("id")?.takeIf { it.length in 8..64 } ?: Ids.newId()
            val tx = if (errors.isEmpty() && date != null && type != null && amount != null) {
                Transaction(
                    id = id, type = type, amount = amount, accountId = account?.id ?: "new:$accountName",
                    toAccountId = if (type == TransactionType.TRANSFER) (toAccount?.id ?: "new:$toName") else null,
                    categoryId = if (type.usesCategory) (category?.id ?: newCategory?.let { "new:$it|${kind.name}" }) else null,
                    date = date, time = time, payee = raw.col("payee"), description = raw.col("description"), notes = raw.col("notes"),
                    tags = raw.col("tags")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
                    createdAt = clock.nowMillis(), updatedAt = clock.nowMillis(),
                )
            } else null
            rows += ParsedRow(line, tx, errors, newAccount, newToAccount, newCategory)
        }
        return Preview(rows, missingAccounts, missingCategories, emptyList())
    }

    suspend fun import(preview: Preview): Result {
        val now = clock.nowMillis()
        val prefs = settings.current()
        var createdAccounts = 0
        var createdCategories = 0
        val accountIdByName = accounts.getAccounts().associate { it.name.lowercase() to it.id }.toMutableMap()
        for (name in preview.missingAccounts) {
            if (accountIdByName.containsKey(name.lowercase())) continue
            val account = Account(id = Ids.newId(), name = name, type = AccountType.OTHER, currencyCode = prefs.baseCurrency, openingBalance = com.attiqtherehman.kharcha.core.money.Money.ZERO, color = DefaultData.palette[12].toInt(), icon = "wallet", createdAt = now, updatedAt = now)
            accounts.create(account); createdAccounts++
            accountIdByName[name.lowercase()] = account.id
        }
        val categoryIdByKey = categories.getAll().associate { (it.name.lowercase() + "|" + it.kind.name) to it.id }.toMutableMap()
        for (key in preview.missingCategories) {
            if (categoryIdByKey.containsKey(key.lowercase())) continue
            val (name, kindName) = key.split('|')
            val category = Category(id = Ids.newId(), name = name, kind = CategoryKind.valueOf(kindName), color = DefaultData.palette[20].toInt(), icon = "more", createdAt = now, updatedAt = now)
            categories.create(category); createdCategories++
            categoryIdByKey[key.lowercase()] = category.id
        }
        fun resolveAccount(id: String): String = if (id.startsWith("new:")) accountIdByName[id.removePrefix("new:").lowercase()] ?: id else id
        fun resolveCategory(id: String?): String? = id?.let { if (it.startsWith("new:")) categoryIdByKey[it.removePrefix("new:").lowercase()] else it }

        val accountsById = accounts.getAccounts().associateBy { it.id }
        val toInsert = ArrayList<Transaction>()
        var skipped = 0
        for (row in preview.rows) {
            val tx = row.transaction ?: continue
            if (transactions.get(tx.id) != null) { skipped++; continue }
            val resolved = tx.copy(accountId = resolveAccount(tx.accountId), toAccountId = tx.toAccountId?.let(::resolveAccount), categoryId = resolveCategory(tx.categoryId))
            if (LedgerRules.validate(resolved, accountsById).isEmpty()) toInsert += resolved else skipped++
        }
        toInsert.chunked(200).forEach { transactions.insertAll(it) }
        return Result(toInsert.size, skipped, createdAccounts, createdCategories)
    }

    private fun parseDate(raw: String): LocalDate? {
        val s = raw.trim().let { if (it.length >= 10 && it[4] == '-' && it[7] == '-') it.take(10) else it }
        for (f in dateFormats) runCatching { return LocalDate.parse(s, f) }
        return null
    }
}
