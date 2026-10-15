package com.attiqtherehman.kharcha.data.backup

import android.content.Context
import android.net.Uri
import com.attiqtherehman.kharcha.data.local.entity.AccountEntity
import com.attiqtherehman.kharcha.data.local.entity.BillPaymentEntity
import com.attiqtherehman.kharcha.data.local.entity.CategoryBudgetEntity
import com.attiqtherehman.kharcha.data.local.entity.CategoryEntity
import com.attiqtherehman.kharcha.data.local.entity.DayReviewEntity
import com.attiqtherehman.kharcha.data.local.entity.GoalContributionEntity
import com.attiqtherehman.kharcha.data.local.entity.MonthlyBudgetEntity
import com.attiqtherehman.kharcha.data.local.entity.RecurringTransactionEntity
import com.attiqtherehman.kharcha.data.local.entity.ReminderEntity
import com.attiqtherehman.kharcha.data.local.entity.SavingsGoalEntity
import com.attiqtherehman.kharcha.data.local.entity.TransactionEntity
import com.attiqtherehman.kharcha.data.prefs.PortableSettings
import com.attiqtherehman.kharcha.domain.model.AccountType
import com.attiqtherehman.kharcha.domain.model.CategoryKind
import com.attiqtherehman.kharcha.domain.model.Frequency
import com.attiqtherehman.kharcha.domain.model.ReminderStatus
import com.attiqtherehman.kharcha.domain.model.TransactionType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject

class BackupException(message: String) : Exception(message)

/** Everything decoded from an archive, already checked against itself. */
class BackupContents(
    val manifest: BackupManifest,
    val settings: PortableSettings?,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val recurring: List<RecurringTransactionEntity>,
    val transactions: List<TransactionEntity>,
    val goals: List<SavingsGoalEntity>,
    val contributions: List<GoalContributionEntity>,
    val reminders: List<ReminderEntity>,
    val payments: List<BillPaymentEntity>,
    val monthlyBudgets: List<MonthlyBudgetEntity>,
    val categoryBudgets: List<CategoryBudgetEntity>,
    val dayReviews: List<DayReviewEntity>,
    val attachmentNames: List<String>,
    val archiveFile: File,
) {
    fun counts(): Map<String, Int> = linkedMapOf(
        "accounts" to accounts.size, "categories" to categories.size, "recurring_transactions" to recurring.size,
        "transactions" to transactions.size, "savings_goals" to goals.size, "goal_contributions" to contributions.size,
        "reminders" to reminders.size, "bill_payments" to payments.size, "monthly_budgets" to monthlyBudgets.size,
        "category_budgets" to categoryBudgets.size, "day_reviews" to dayReviews.size,
    )
}

/** Copies the archive locally, parses and validates it completely before anything is written. */
class BackupReader @Inject constructor(@ApplicationContext private val context: Context) {

    fun copyToCache(uri: Uri): File {
        val dir = File(context.cacheDir, "restore").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "backup-${System.currentTimeMillis()}.zip")
        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            ?: throw BackupException("Cannot open the selected file")
        return target
    }

    fun read(archive: File): BackupContents {
        val zip = try { ZipFile(archive) } catch (e: Exception) { throw BackupException("Not a ZIP archive") }
        zip.use { z ->
            val json = BackupFormat.json
            fun text(name: String): String? {
                val entry = z.getEntry(name) ?: return null
                if (entry.size > BackupFormat.MAX_ENTRY_BYTES) throw BackupException("Entry $name is too large")
                return z.getInputStream(entry).bufferedReader().use { it.readText() }
            }
            val manifestText = text(BackupFormat.MANIFEST) ?: throw BackupException("manifest.json is missing")
            val manifest = try { json.decodeFromString(BackupManifest.serializer(), manifestText) } catch (e: Exception) { throw BackupException("manifest.json is unreadable") }
            if (manifest.formatVersion > BackupFormat.FORMAT_VERSION) throw BackupException("newer")
            if (manifest.schemaVersion > BackupFormat.schemaVersion) throw BackupException("newer")
            val settings = text(BackupFormat.SETTINGS)?.let { runCatching { json.decodeFromString(PortableSettings.serializer(), it) }.getOrNull() }

            fun <T> table(name: String, serializer: KSerializer<T>): List<T> {
                val info = manifest.tables[name] ?: return emptyList()
                val body = text(info.file) ?: throw BackupException("${info.file} is missing")
                val rows = try { json.decodeFromString(ListSerializer(serializer), body) } catch (e: Exception) { throw BackupException("${info.file} is unreadable: ${e.message?.take(120)}") }
                if (rows.size != info.rowCount) throw BackupException("${info.file}: expected ${info.rowCount} rows, found ${rows.size}")
                return rows
            }
            val contents = BackupContents(
                manifest = manifest,
                settings = settings,
                accounts = table("accounts", serializer()),
                categories = table("categories", serializer()),
                recurring = table("recurring_transactions", serializer()),
                transactions = table("transactions", serializer()),
                goals = table("savings_goals", serializer()),
                contributions = table("goal_contributions", serializer()),
                reminders = table("reminders", serializer()),
                payments = table("bill_payments", serializer()),
                monthlyBudgets = table("monthly_budgets", serializer()),
                categoryBudgets = table("category_budgets", serializer()),
                dayReviews = table("day_reviews", serializer()),
                attachmentNames = manifest.attachments.filter { name ->
                    BackupFormat.attachmentNamePattern.matches(name) && z.getEntry(BackupFormat.ATTACHMENTS_DIR + name) != null
                },
                archiveFile = archive,
            )
            validateSelfContained(contents)
            return contents
        }
    }

    /** Structural checks that do not depend on the local database. */
    private fun validateSelfContained(c: BackupContents) {
        fun <T : Enum<T>> check(values: Array<T>, v: String, what: String) {
            if (values.none { it.name == v }) throw BackupException("invalid $what \"$v\"")
        }
        c.accounts.forEach { check(AccountType.entries.toTypedArray(), it.type, "account type") }
        c.categories.forEach { check(CategoryKind.entries.toTypedArray(), it.kind, "category kind") }
        c.transactions.forEach { t ->
            check(TransactionType.entries.toTypedArray(), t.type, "transaction type")
            if (t.type == "TRANSFER" && t.toAccountId == null) throw BackupException("transfer ${t.id} has no destination")
            if (t.type != "ADJUSTMENT" && t.amountMinor <= 0) throw BackupException("transaction ${t.id} has a non-positive amount")
        }
        c.recurring.forEach { check(Frequency.entries.toTypedArray(), it.frequency, "frequency") }
        c.reminders.forEach { r ->
            if (r.frequency != "NONE") check(Frequency.entries.toTypedArray(), r.frequency, "reminder frequency")
            check(ReminderStatus.entries.toTypedArray(), r.status, "reminder status")
        }
        val ids = HashSet<String>()
        listOf(c.accounts.map { it.id }, c.categories.map { it.id }, c.transactions.map { it.id }, c.recurring.map { it.id }, c.goals.map { it.id },
            c.contributions.map { it.id }, c.reminders.map { it.id }, c.payments.map { it.id }, c.monthlyBudgets.map { it.id }, c.categoryBudgets.map { it.id })
            .forEach { list -> list.forEach { id -> if (!ids.add(id)) throw BackupException("duplicate id $id") } }
    }
}
