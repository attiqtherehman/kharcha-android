package com.attiqtherehman.kharcha.data.mapper

import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.data.local.dao.AccountBalanceRow
import com.attiqtherehman.kharcha.data.local.dao.TransactionRow
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
import com.attiqtherehman.kharcha.domain.model.Account
import com.attiqtherehman.kharcha.domain.model.AccountBalance
import com.attiqtherehman.kharcha.domain.model.AccountType
import com.attiqtherehman.kharcha.domain.model.BillPayment
import com.attiqtherehman.kharcha.domain.model.Category
import com.attiqtherehman.kharcha.domain.model.CategoryBudget
import com.attiqtherehman.kharcha.domain.model.CategoryKind
import com.attiqtherehman.kharcha.domain.model.DayReview
import com.attiqtherehman.kharcha.domain.model.Frequency
import com.attiqtherehman.kharcha.domain.model.GoalContribution
import com.attiqtherehman.kharcha.domain.model.MonthlyBudget
import com.attiqtherehman.kharcha.domain.model.Recurrence
import com.attiqtherehman.kharcha.domain.model.RecurringTransaction
import com.attiqtherehman.kharcha.domain.model.Reminder
import com.attiqtherehman.kharcha.domain.model.ReminderStatus
import com.attiqtherehman.kharcha.domain.model.SavingsGoal
import com.attiqtherehman.kharcha.domain.model.Transaction
import com.attiqtherehman.kharcha.domain.model.TransactionDetails
import com.attiqtherehman.kharcha.domain.model.TransactionType
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

private const val NO_RECURRENCE = "NONE"

fun AccountEntity.toDomain() = Account(
    id = id, name = name, type = AccountType.valueOf(type), currencyCode = currencyCode,
    openingBalance = Money(openingBalanceMinor), creditLimit = creditLimitMinor?.let(::Money),
    color = color, icon = icon, notes = notes, isArchived = isArchived, sortOrder = sortOrder,
    createdAt = createdAt, updatedAt = updatedAt,
)

fun Account.toEntity() = AccountEntity(
    id = id, name = name, type = type.name, currencyCode = currencyCode,
    openingBalanceMinor = openingBalance.minor, creditLimitMinor = creditLimit?.minor,
    color = color, icon = icon, notes = notes, isArchived = isArchived, sortOrder = sortOrder,
    createdAt = createdAt, updatedAt = updatedAt,
)

fun AccountBalanceRow.toDomain() = AccountBalance(account.toDomain(), Money(balanceMinor))

fun CategoryEntity.toDomain() = Category(
    id = id, name = name, kind = CategoryKind.valueOf(kind), color = color, icon = icon,
    isFavorite = isFavorite, isArchived = isArchived, sortOrder = sortOrder, createdAt = createdAt, updatedAt = updatedAt,
)

fun Category.toEntity() = CategoryEntity(
    id = id, name = name, kind = kind.name, color = color, icon = icon,
    isFavorite = isFavorite, isArchived = isArchived, sortOrder = sortOrder, createdAt = createdAt, updatedAt = updatedAt,
)

fun joinTags(tags: List<String>): String? =
    tags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().joinToString(",").ifEmpty { null }

fun splitTags(raw: String?): List<String> =
    raw?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

fun TransactionEntity.toDomain() = Transaction(
    id = id, type = TransactionType.valueOf(type), amount = Money(amountMinor), accountId = accountId,
    toAccountId = toAccountId, categoryId = categoryId, date = LocalDate.parse(localDate), time = LocalTime.parse(localTime),
    payee = payee, description = description, notes = notes, tags = splitTags(tags),
    linkedTransactionId = linkedTransactionId, recurringTransactionId = recurringTransactionId, occurrenceKey = occurrenceKey,
    attachmentFileName = attachmentFileName, createdAt = createdAt, updatedAt = updatedAt,
)

fun Transaction.toEntity() = TransactionEntity(
    id = id, type = type.name, amountMinor = amount.minor, accountId = accountId, toAccountId = toAccountId,
    categoryId = categoryId, localDate = date.toString(), localTime = time.toString().take(5),
    payee = payee?.trim()?.ifEmpty { null }, description = description?.trim()?.ifEmpty { null },
    notes = notes?.trim()?.ifEmpty { null }, tags = joinTags(tags),
    linkedTransactionId = linkedTransactionId, recurringTransactionId = recurringTransactionId, occurrenceKey = occurrenceKey,
    attachmentFileName = attachmentFileName, createdAt = createdAt, updatedAt = updatedAt,
)

fun TransactionRow.toDomain() = TransactionDetails(
    transaction = tx.toDomain(),
    accountName = accountName,
    accountType = AccountType.valueOf(accountType),
    currencyCode = accountCurrency,
    toAccountName = toAccountName,
    toAccountType = toAccountType?.let { AccountType.valueOf(it) },
    categoryName = categoryName,
    categoryColor = categoryColor,
    categoryIcon = categoryIcon,
)

fun RecurringTransactionEntity.toDomain() = RecurringTransaction(
    id = id, name = name,
    recurrence = Recurrence(Frequency.valueOf(frequency), intervalCount, LocalDate.parse(anchorDate), endDate?.let(LocalDate::parse)),
    timeOfDay = LocalTime.parse(timeOfDay), lastMaterializedDate = LocalDate.parse(lastMaterializedDate), isActive = isActive,
    type = TransactionType.valueOf(type), amount = Money(amountMinor), accountId = accountId, toAccountId = toAccountId,
    categoryId = categoryId, payee = payee, description = description, createdAt = createdAt, updatedAt = updatedAt,
)

fun RecurringTransaction.toEntity() = RecurringTransactionEntity(
    id = id, name = name, frequency = recurrence.frequency.name, intervalCount = recurrence.interval,
    anchorDate = recurrence.anchorDate.toString(), endDate = recurrence.endDate?.toString(),
    timeOfDay = timeOfDay.toString().take(5), lastMaterializedDate = lastMaterializedDate.toString(), isActive = isActive,
    type = type.name, amountMinor = amount.minor, accountId = accountId, toAccountId = toAccountId, categoryId = categoryId,
    payee = payee, description = description, createdAt = createdAt, updatedAt = updatedAt,
)

fun MonthlyBudgetEntity.toDomain() = MonthlyBudget(
    id = id, yearMonth = YearMonth.parse(yearMonth), amount = amountMinor?.let(::Money),
    savingsTarget = savingsTargetMinor?.let(::Money), createdAt = createdAt, updatedAt = updatedAt,
)

fun MonthlyBudget.toEntity() = MonthlyBudgetEntity(
    id = id, yearMonth = yearMonth.toString(), amountMinor = amount?.minor, savingsTargetMinor = savingsTarget?.minor,
    createdAt = createdAt, updatedAt = updatedAt,
)

fun CategoryBudgetEntity.toDomain() = CategoryBudget(
    id = id, yearMonth = YearMonth.parse(yearMonth), categoryId = categoryId, amount = Money(amountMinor),
    createdAt = createdAt, updatedAt = updatedAt,
)

fun CategoryBudget.toEntity() = CategoryBudgetEntity(
    id = id, yearMonth = yearMonth.toString(), categoryId = categoryId, amountMinor = amount.minor,
    createdAt = createdAt, updatedAt = updatedAt,
)

fun SavingsGoalEntity.toDomain() = SavingsGoal(
    id = id, name = name, target = Money(targetMinor), linkedAccountId = linkedAccountId,
    targetDate = targetDate?.let(LocalDate::parse), notes = notes, color = color, icon = icon,
    completedAt = completedAt, sortOrder = sortOrder, createdAt = createdAt, updatedAt = updatedAt,
)

fun SavingsGoal.toEntity() = SavingsGoalEntity(
    id = id, name = name, targetMinor = target.minor, linkedAccountId = linkedAccountId,
    targetDate = targetDate?.toString(), notes = notes, color = color, icon = icon,
    completedAt = completedAt, sortOrder = sortOrder, createdAt = createdAt, updatedAt = updatedAt,
)

fun GoalContributionEntity.toDomain() = GoalContribution(
    id = id, goalId = goalId, date = LocalDate.parse(localDate), amount = Money(amountMinor), note = note,
    transactionId = transactionId, createdAt = createdAt,
)

fun GoalContribution.toEntity() = GoalContributionEntity(
    id = id, goalId = goalId, localDate = date.toString(), amountMinor = amount.minor, note = note,
    transactionId = transactionId, createdAt = createdAt,
)

fun parseAdvanceDays(raw: String): List<Int> =
    raw.split(',').mapNotNull { it.trim().toIntOrNull() }.distinct().sortedDescending()

fun joinAdvanceDays(days: List<Int>): String = days.distinct().sortedDescending().joinToString(",")

fun ReminderEntity.toDomain() = Reminder(
    id = id, title = title, amount = amountMinor?.let(::Money), categoryId = categoryId, accountId = accountId,
    dueDate = LocalDate.parse(dueDate), reminderTime = LocalTime.parse(reminderTime),
    recurrence = if (frequency == NO_RECURRENCE) null else Recurrence(Frequency.valueOf(frequency), intervalCount, LocalDate.parse(anchorDate), endDate?.let(LocalDate::parse)),
    advanceDays = parseAdvanceDays(advanceDays), notes = notes, status = ReminderStatus.valueOf(status),
    createdAt = createdAt, updatedAt = updatedAt,
)

fun Reminder.toEntity() = ReminderEntity(
    id = id, title = title, amountMinor = amount?.minor, categoryId = categoryId, accountId = accountId,
    dueDate = dueDate.toString(), reminderTime = reminderTime.toString().take(5),
    frequency = recurrence?.frequency?.name ?: NO_RECURRENCE, intervalCount = recurrence?.interval ?: 1,
    anchorDate = (recurrence?.anchorDate ?: dueDate).toString(), endDate = recurrence?.endDate?.toString(),
    advanceDays = joinAdvanceDays(advanceDays), notes = notes, status = status.name,
    createdAt = createdAt, updatedAt = updatedAt,
)

fun BillPaymentEntity.toDomain() = BillPayment(
    id = id, reminderId = reminderId, dueDate = LocalDate.parse(dueDate), paidAt = paidAt,
    amount = Money(amountMinor), transactionId = transactionId, notes = notes,
)

fun BillPayment.toEntity() = BillPaymentEntity(
    id = id, reminderId = reminderId, dueDate = dueDate.toString(), paidAt = paidAt,
    amountMinor = amount.minor, transactionId = transactionId, notes = notes,
)

fun DayReviewEntity.toDomain() = DayReview(LocalDate.parse(localDate), reviewedAt)
fun DayReview.toEntity() = DayReviewEntity(date.toString(), reviewedAt)
