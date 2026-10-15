package com.attiqtherehman.kharcha.data.backup

import androidx.room.withTransaction
import com.attiqtherehman.kharcha.core.common.DataChangeHub
import com.attiqtherehman.kharcha.data.local.KharchaDatabase
import com.attiqtherehman.kharcha.data.prefs.SettingsDataStore
import java.util.zip.ZipFile
import javax.inject.Inject

enum class RestoreMode { REPLACE, MERGE }
enum class MergePolicy { SKIP, UPDATE_IF_NEWER, OVERWRITE }

data class InvalidRow(val table: String, val id: String, val reason: String)

class TableReport {
    var inserted = 0
    var updated = 0
    var skipped = 0
    val invalid = ArrayList<InvalidRow>()
}

data class RestoreReport(val mode: RestoreMode, val tables: Map<String, TableReport>) {
    val totalInserted: Int get() = tables.values.sumOf { it.inserted }
    val totalInvalid: Int get() = tables.values.sumOf { it.invalid.size }
}

/**
 * Applies a validated [BackupContents] to the database, either replacing everything or merging
 * by id. All row writes happen inside one Room transaction; attachments are staged first and
 * swapped/merged only after the commit, so a failure never leaves a half-restored database.
 */
class BackupRestorer @Inject constructor(
    private val db: KharchaDatabase,
    private val settings: SettingsDataStore,
    private val attachments: AttachmentStore,
    private val hub: DataChangeHub,
) {

    suspend fun restore(contents: BackupContents, mode: RestoreMode, policy: MergePolicy): RestoreReport {
        val report = BackupFormat.TABLES_IN_ORDER.associateWith { TableReport() }
        val plan = buildPlan(contents, mode, report)
        val staging = extractAttachments(contents)
        db.withTransaction {
            if (mode == RestoreMode.REPLACE) deleteEverything()
            applyPlan(plan, mode, policy, report)
        }
        if (mode == RestoreMode.REPLACE) {
            attachments.swapInStaging(staging)
            contents.settings?.let { settings.applyPortable(it) }
        } else {
            attachments.mergeFromStaging(staging)
        }
        hub.financialDataChanged()
        hub.remindersChanged()
        return RestoreReport(mode, report)
    }

    /** Rows per table after reference checks; soft references to missing rows are nulled. */
    private class Plan(val c: BackupContents) {
        val accounts = ArrayList<com.attiqtherehman.kharcha.data.local.entity.AccountEntity>()
        val categories = ArrayList<com.attiqtherehman.kharcha.data.local.entity.CategoryEntity>()
        val recurring = ArrayList<com.attiqtherehman.kharcha.data.local.entity.RecurringTransactionEntity>()
        val transactions = ArrayList<com.attiqtherehman.kharcha.data.local.entity.TransactionEntity>()
        val goals = ArrayList<com.attiqtherehman.kharcha.data.local.entity.SavingsGoalEntity>()
        val contributions = ArrayList<com.attiqtherehman.kharcha.data.local.entity.GoalContributionEntity>()
        val reminders = ArrayList<com.attiqtherehman.kharcha.data.local.entity.ReminderEntity>()
        val payments = ArrayList<com.attiqtherehman.kharcha.data.local.entity.BillPaymentEntity>()
        val monthlyBudgets = ArrayList<com.attiqtherehman.kharcha.data.local.entity.MonthlyBudgetEntity>()
        val categoryBudgets = ArrayList<com.attiqtherehman.kharcha.data.local.entity.CategoryBudgetEntity>()
        val dayReviews = ArrayList<com.attiqtherehman.kharcha.data.local.entity.DayReviewEntity>()
    }

    private suspend fun buildPlan(c: BackupContents, mode: RestoreMode, report: Map<String, TableReport>): Plan {
        val plan = Plan(c)
        val localAccounts = if (mode == RestoreMode.MERGE) db.accountDao().getAll().map { it.id }.toSet() else emptySet()
        val localCategories = if (mode == RestoreMode.MERGE) db.categoryDao().getAll().map { it.id }.toSet() else emptySet()
        val localRecurring = if (mode == RestoreMode.MERGE) db.recurringTransactionDao().getAll().map { it.id }.toSet() else emptySet()
        val localTransactions = if (mode == RestoreMode.MERGE) db.transactionDao().getAll().map { it.id }.toSet() else emptySet()
        val localGoals = if (mode == RestoreMode.MERGE) db.savingsGoalDao().getAll().map { it.id }.toSet() else emptySet()
        val localReminders = if (mode == RestoreMode.MERGE) db.reminderDao().getAll().map { it.id }.toSet() else emptySet()

        val accountIds = localAccounts + c.accounts.map { it.id }
        plan.accounts += c.accounts
        val categoryIds = localCategories + c.categories.map { it.id }
        plan.categories += c.categories

        val recurringIds = HashSet(localRecurring)
        for (r in c.recurring) {
            when {
                r.accountId !in accountIds -> report.getValue("recurring_transactions").invalid += InvalidRow("recurring_transactions", r.id, "unknown account")
                r.toAccountId != null && r.toAccountId !in accountIds -> report.getValue("recurring_transactions").invalid += InvalidRow("recurring_transactions", r.id, "unknown destination account")
                else -> { plan.recurring += r.copy(categoryId = r.categoryId?.takeIf { it in categoryIds }); recurringIds += r.id }
            }
        }
        val transactionIds = HashSet(localTransactions)
        val occurrenceKeys = HashSet<String>()
        for (t in c.transactions) {
            val reason = when {
                t.accountId !in accountIds -> "unknown account"
                t.toAccountId != null && t.toAccountId !in accountIds -> "unknown destination account"
                t.categoryId != null && t.categoryId !in categoryIds -> "unknown category"
                t.recurringTransactionId != null && t.occurrenceKey != null && !occurrenceKeys.add("${t.recurringTransactionId}|${t.occurrenceKey}") -> "duplicate recurring occurrence"
                else -> null
            }
            if (reason != null) report.getValue("transactions").invalid += InvalidRow("transactions", t.id, reason)
            else { plan.transactions += t.copy(recurringTransactionId = t.recurringTransactionId?.takeIf { it in recurringIds }); transactionIds += t.id }
        }
        // Self references: null links to rows that will not exist.
        val plannedTx = plan.transactions.map { it.id }.toSet() + localTransactions
        for (i in plan.transactions.indices) {
            val t = plan.transactions[i]
            if (t.linkedTransactionId != null && t.linkedTransactionId !in plannedTx) plan.transactions[i] = t.copy(linkedTransactionId = null)
        }
        val goalIds = HashSet(localGoals)
        for (g in c.goals) { plan.goals += g.copy(linkedAccountId = g.linkedAccountId?.takeIf { it in accountIds }); goalIds += g.id }
        for (gc in c.contributions) {
            if (gc.goalId !in goalIds) report.getValue("goal_contributions").invalid += InvalidRow("goal_contributions", gc.id, "unknown goal")
            else plan.contributions += gc.copy(transactionId = gc.transactionId?.takeIf { it in plannedTx })
        }
        val reminderIds = HashSet(localReminders)
        for (r in c.reminders) {
            plan.reminders += r.copy(categoryId = r.categoryId?.takeIf { it in categoryIds }, accountId = r.accountId?.takeIf { it in accountIds })
            reminderIds += r.id
        }
        val paymentKeys = HashSet<String>()
        for (p in c.payments) {
            when {
                p.reminderId !in reminderIds -> report.getValue("bill_payments").invalid += InvalidRow("bill_payments", p.id, "unknown reminder")
                !paymentKeys.add("${p.reminderId}|${p.dueDate}") -> report.getValue("bill_payments").invalid += InvalidRow("bill_payments", p.id, "duplicate payment for due date")
                else -> plan.payments += p.copy(transactionId = p.transactionId?.takeIf { it in plannedTx })
            }
        }
        val months = HashSet<String>()
        for (m in c.monthlyBudgets) {
            if (!months.add(m.yearMonth)) report.getValue("monthly_budgets").invalid += InvalidRow("monthly_budgets", m.id, "duplicate month") else plan.monthlyBudgets += m
        }
        val budgetKeys = HashSet<String>()
        for (b in c.categoryBudgets) {
            when {
                b.categoryId !in categoryIds -> report.getValue("category_budgets").invalid += InvalidRow("category_budgets", b.id, "unknown category")
                !budgetKeys.add("${b.yearMonth}|${b.categoryId}") -> report.getValue("category_budgets").invalid += InvalidRow("category_budgets", b.id, "duplicate month/category")
                else -> plan.categoryBudgets += b
            }
        }
        plan.dayReviews += c.dayReviews
        return plan
    }

    private fun extractAttachments(c: BackupContents): java.io.File {
        val staging = attachments.stagingDirectory()
        if (c.attachmentNames.isEmpty()) return staging
        ZipFile(c.archiveFile).use { zip ->
            for (name in c.attachmentNames) {
                val entry = zip.getEntry(BackupFormat.ATTACHMENTS_DIR + name) ?: continue
                zip.getInputStream(entry).use { attachments.write(name, it, staging) }
            }
        }
        return staging
    }

    private suspend fun deleteEverything() {
        db.reminderDao().deleteAllPayments()
        db.savingsGoalDao().deleteAllContributions()
        db.budgetDao().deleteAllCategoryBudgets()
        db.budgetDao().deleteAllMonthly()
        db.dayReviewDao().deleteAll()
        db.transactionDao().deleteAll()
        db.reminderDao().deleteAll()
        db.savingsGoalDao().deleteAll()
        db.recurringTransactionDao().deleteAll()
        db.categoryDao().deleteAll()
        db.accountDao().deleteAll()
    }

    private suspend fun applyPlan(plan: Plan, mode: RestoreMode, policy: MergePolicy, report: Map<String, TableReport>) {
        if (mode == RestoreMode.REPLACE) {
            db.accountDao().insertAll(plan.accounts); report.getValue("accounts").inserted = plan.accounts.size
            db.categoryDao().insertAll(plan.categories); report.getValue("categories").inserted = plan.categories.size
            db.recurringTransactionDao().insertAll(plan.recurring); report.getValue("recurring_transactions").inserted = plan.recurring.size
            plan.transactions.chunked(500).forEach { db.transactionDao().insertAll(it) }; report.getValue("transactions").inserted = plan.transactions.size
            db.savingsGoalDao().insertAll(plan.goals); report.getValue("savings_goals").inserted = plan.goals.size
            db.savingsGoalDao().insertAllContributions(plan.contributions); report.getValue("goal_contributions").inserted = plan.contributions.size
            db.reminderDao().insertAll(plan.reminders); report.getValue("reminders").inserted = plan.reminders.size
            db.reminderDao().insertAllPayments(plan.payments); report.getValue("bill_payments").inserted = plan.payments.size
            db.budgetDao().insertAllMonthly(plan.monthlyBudgets); report.getValue("monthly_budgets").inserted = plan.monthlyBudgets.size
            db.budgetDao().insertAllCategoryBudgets(plan.categoryBudgets); report.getValue("category_budgets").inserted = plan.categoryBudgets.size
            db.dayReviewDao().upsertAll(plan.dayReviews); report.getValue("day_reviews").inserted = plan.dayReviews.size
            return
        }
        // MERGE: decide per row against the current database.
        fun decide(existingUpdatedAt: Long?, incomingUpdatedAt: Long?): Boolean? = when {
            existingUpdatedAt == null -> null // insert
            policy == MergePolicy.SKIP -> false
            policy == MergePolicy.OVERWRITE -> true
            else -> incomingUpdatedAt != null && existingUpdatedAt < incomingUpdatedAt
        }
        val accDao = db.accountDao(); val localAcc = accDao.getAll().associateBy { it.id }
        for (a in plan.accounts) when (decide(localAcc[a.id]?.updatedAt, a.updatedAt)) { null -> { accDao.insert(a); report.getValue("accounts").inserted++ }; true -> { accDao.update(a); report.getValue("accounts").updated++ }; false -> report.getValue("accounts").skipped++ }
        val catDao = db.categoryDao(); val localCat = catDao.getAll().associateBy { it.id }
        for (c in plan.categories) when (decide(localCat[c.id]?.updatedAt, c.updatedAt)) { null -> { catDao.insert(c); report.getValue("categories").inserted++ }; true -> { catDao.update(c); report.getValue("categories").updated++ }; false -> report.getValue("categories").skipped++ }
        val recDao = db.recurringTransactionDao(); val localRec = recDao.getAll().associateBy { it.id }
        for (r in plan.recurring) when (decide(localRec[r.id]?.updatedAt, r.updatedAt)) { null -> { recDao.insert(r); report.getValue("recurring_transactions").inserted++ }; true -> { recDao.update(r); report.getValue("recurring_transactions").updated++ }; false -> report.getValue("recurring_transactions").skipped++ }
        val txDao = db.transactionDao(); val localTx = txDao.getAll().associateBy { it.id }
        val localOccurrences = localTx.values.filter { it.recurringTransactionId != null }.map { "${it.recurringTransactionId}|${it.occurrenceKey}" }.toHashSet()
        for (t in plan.transactions) {
            val existing = localTx[t.id]
            if (existing == null && t.recurringTransactionId != null && "${t.recurringTransactionId}|${t.occurrenceKey}" in localOccurrences) {
                report.getValue("transactions").invalid += InvalidRow("transactions", t.id, "occurrence already recorded"); continue
            }
            when (decide(existing?.updatedAt, t.updatedAt)) { null -> { txDao.insert(t); report.getValue("transactions").inserted++ }; true -> { txDao.update(t); report.getValue("transactions").updated++ }; false -> report.getValue("transactions").skipped++ }
        }
        val goalDao = db.savingsGoalDao(); val localGoals = goalDao.getAll().associateBy { it.id }
        for (g in plan.goals) when (decide(localGoals[g.id]?.updatedAt, g.updatedAt)) { null -> { goalDao.insert(g); report.getValue("savings_goals").inserted++ }; true -> { goalDao.update(g); report.getValue("savings_goals").updated++ }; false -> report.getValue("savings_goals").skipped++ }
        val localContrib = goalDao.getAllContributions().associateBy { it.id }
        for (gc in plan.contributions) when (decide(localContrib[gc.id]?.createdAt, null)) { null -> { goalDao.insertContribution(gc); report.getValue("goal_contributions").inserted++ }; true -> { goalDao.updateContribution(gc); report.getValue("goal_contributions").updated++ }; false -> report.getValue("goal_contributions").skipped++ }
        val remDao = db.reminderDao(); val localRem = remDao.getAll().associateBy { it.id }
        for (r in plan.reminders) when (decide(localRem[r.id]?.updatedAt, r.updatedAt)) { null -> { remDao.insert(r); report.getValue("reminders").inserted++ }; true -> { remDao.update(r); report.getValue("reminders").updated++ }; false -> report.getValue("reminders").skipped++ }
        val localPay = remDao.getAllPayments().associateBy { it.id }
        val localPayKeys = localPay.values.map { "${it.reminderId}|${it.dueDate}" }.toHashSet()
        for (p in plan.payments) {
            val existing = localPay[p.id]
            if (existing == null && "${p.reminderId}|${p.dueDate}" in localPayKeys) { report.getValue("bill_payments").invalid += InvalidRow("bill_payments", p.id, "payment already recorded"); continue }
            when (decide(existing?.paidAt, null)) { null -> { remDao.insertPaymentIgnore(p); report.getValue("bill_payments").inserted++ }; true -> { remDao.updatePayment(p); report.getValue("bill_payments").updated++ }; false -> report.getValue("bill_payments").skipped++ }
        }
        val budDao = db.budgetDao(); val localMonthly = budDao.getAllMonthly().associateBy { it.yearMonth }
        for (m in plan.monthlyBudgets) {
            val existing = localMonthly[m.yearMonth]
            when (decide(existing?.updatedAt, m.updatedAt)) { null -> { budDao.upsertMonthly(m); report.getValue("monthly_budgets").inserted++ }; true -> { budDao.upsertMonthly(m.copy(id = existing!!.id)); report.getValue("monthly_budgets").updated++ }; false -> report.getValue("monthly_budgets").skipped++ }
        }
        val localCb = budDao.getAllCategoryBudgets()
        val localCbById = localCb.associateBy { it.id }
        val localCbByKey = localCb.associateBy { "${it.yearMonth}|${it.categoryId}" }
        for (b in plan.categoryBudgets) {
            val existing = localCbById[b.id] ?: localCbByKey["${b.yearMonth}|${b.categoryId}"]
            when (decide(existing?.updatedAt, b.updatedAt)) { null -> { budDao.insertCategoryBudget(b); report.getValue("category_budgets").inserted++ }; true -> { budDao.updateCategoryBudget(b.copy(id = existing!!.id)); report.getValue("category_budgets").updated++ }; false -> report.getValue("category_budgets").skipped++ }
        }
        val localDays = db.dayReviewDao().getAll().map { it.localDate }.toSet()
        for (d in plan.dayReviews) { if (d.localDate in localDays) report.getValue("day_reviews").skipped++ else { db.dayReviewDao().upsert(d); report.getValue("day_reviews").inserted++ } }
    }
}
