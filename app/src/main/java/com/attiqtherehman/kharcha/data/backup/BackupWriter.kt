package com.attiqtherehman.kharcha.data.backup

import com.attiqtherehman.kharcha.BuildConfig
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.data.local.KharchaDatabase
import com.attiqtherehman.kharcha.data.local.entity.TransactionEntity
import com.attiqtherehman.kharcha.data.prefs.SettingsDataStore
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/** Streams the whole database, portable settings and attachments into a ZIP. */
class BackupWriter @Inject constructor(
    private val db: KharchaDatabase,
    private val settings: SettingsDataStore,
    private val attachments: AttachmentStore,
    private val clock: Clock,
) {
    data class Progress(val step: String, val done: Int, val total: Int)

    suspend fun write(out: OutputStream, onProgress: (Progress) -> Unit = {}) {
        val json = BackupFormat.json
        val prefs = settings.current()
        val counts = linkedMapOf(
            "accounts" to db.accountDao().count(),
            "categories" to db.categoryDao().count(),
            "recurring_transactions" to db.recurringTransactionDao().getAll().size,
            "transactions" to db.transactionDao().count(),
            "savings_goals" to db.savingsGoalDao().getAll().size,
            "goal_contributions" to db.savingsGoalDao().getAllContributions().size,
            "reminders" to db.reminderDao().getAll().size,
            "bill_payments" to db.reminderDao().getAllPayments().size,
            "monthly_budgets" to db.budgetDao().getAllMonthly().size,
            "category_budgets" to db.budgetDao().getAllCategoryBudgets().size,
            "day_reviews" to db.dayReviewDao().getAll().size,
        )
        val attachmentNames = attachments.list().filter { BackupFormat.attachmentNamePattern.matches(it) }
        val manifest = BackupManifest(
            formatVersion = BackupFormat.FORMAT_VERSION,
            schemaVersion = BackupFormat.schemaVersion,
            appVersionCode = BuildConfig.VERSION_CODE,
            appVersionName = BuildConfig.VERSION_NAME,
            createdAt = clock.nowMillis(),
            deviceZone = clock.zone.id,
            baseCurrency = prefs.baseCurrency,
            tables = counts.mapValues { (name, n) -> TableInfo(BackupFormat.tableFile(name), n) },
            attachments = attachmentNames,
        )
        val total = counts.size + attachmentNames.size + 2
        var done = 0
        val zip = ZipOutputStream(out.buffered()).apply { setLevel(Deflater.BEST_SPEED) }
        fun entry(name: String, block: () -> Unit) {
            zip.putNextEntry(ZipEntry(name))
            block()
            zip.closeEntry()
        }
        entry(BackupFormat.MANIFEST) { zip.write(json.encodeToString(manifest).toByteArray()) }
        onProgress(Progress("manifest", ++done, total))
        entry(BackupFormat.SETTINGS) { zip.write(json.encodeToString(settings.toPortable(prefs)).toByteArray()) }
        onProgress(Progress("settings", ++done, total))

        suspend fun <T> table(name: String, serializer: KSerializer<T>, rows: suspend () -> List<T>) {
            coroutineContext.ensureActive()
            entry(BackupFormat.tableFile(name)) { zip.write(json.encodeToString(ListSerializer(serializer), kotlinx.coroutines.runBlocking { rows() }).toByteArray()) }
            onProgress(Progress(name, ++done, total))
        }
        table("accounts", serializer()) { db.accountDao().getAll() }
        table("categories", serializer()) { db.categoryDao().getAll() }
        table("recurring_transactions", serializer()) { db.recurringTransactionDao().getAll() }
        // Transactions can be large: stream the array in pages instead of materialising the list.
        coroutineContext.ensureActive()
        entry(BackupFormat.tableFile("transactions")) {
            zip.write("[".toByteArray())
            var first = true
            var last = ""
            while (true) {
                val page = kotlinx.coroutines.runBlocking { db.transactionDao().pageById(last, 500) }
                if (page.isEmpty()) break
                for (row in page) {
                    if (!first) zip.write(",".toByteArray())
                    first = false
                    zip.write(json.encodeToString(TransactionEntity.serializer(), row).toByteArray())
                }
                last = page.last().id
            }
            zip.write("]".toByteArray())
        }
        onProgress(Progress("transactions", ++done, total))
        table("savings_goals", serializer()) { db.savingsGoalDao().getAll() }
        table("goal_contributions", serializer()) { db.savingsGoalDao().getAllContributions() }
        table("reminders", serializer()) { db.reminderDao().getAll() }
        table("bill_payments", serializer()) { db.reminderDao().getAllPayments() }
        table("monthly_budgets", serializer()) { db.budgetDao().getAllMonthly() }
        table("category_budgets", serializer()) { db.budgetDao().getAllCategoryBudgets() }
        table("day_reviews", serializer()) { db.dayReviewDao().getAll() }

        for (name in attachmentNames) {
            coroutineContext.ensureActive()
            val file = attachments.file(name)
            if (!file.isFile) continue
            entry(BackupFormat.ATTACHMENTS_DIR + name) { file.inputStream().use { it.copyTo(zip) } }
            onProgress(Progress(name, ++done, total))
        }
        zip.finish()
        zip.flush()
    }
}
