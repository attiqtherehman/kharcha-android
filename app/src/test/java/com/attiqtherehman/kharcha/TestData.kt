package com.attiqtherehman.kharcha

import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.domain.model.Account
import com.attiqtherehman.kharcha.domain.model.AccountType
import com.attiqtherehman.kharcha.domain.model.Category
import com.attiqtherehman.kharcha.domain.model.CategoryKind
import com.attiqtherehman.kharcha.domain.model.Transaction
import com.attiqtherehman.kharcha.domain.model.TransactionType
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/** Deterministic, adjustable clock for tests. Karachi has no DST, so local dates are stable. */
class FixedClock(
    private var instant: Instant = Instant.parse("2026-10-15T10:00:00Z"),
    override val zone: ZoneId = ZoneId.of("Asia/Karachi"),
) : Clock {
    override fun now(): Instant = instant
    fun advance(duration: Duration) { instant = instant.plus(duration) }
    fun set(newInstant: Instant) { instant = newInstant }
}

object TestData {
    const val NOW = 1_760_000_000_000L
    val DAY: LocalDate = LocalDate.of(2026, 10, 15)
    val TIME: LocalTime = LocalTime.of(12, 0)

    fun account(
        id: String = UUID.randomUUID().toString(),
        name: String = "Account $id",
        type: AccountType = AccountType.BANK,
        opening: Long = 0L,
        currency: String = "PKR",
        creditLimit: Long? = null,
        archived: Boolean = false,
    ) = Account(
        id = id, name = name, type = type, currencyCode = currency,
        openingBalance = Money(opening), creditLimit = creditLimit?.let(::Money),
        color = 0xFF000000.toInt(), icon = "wallet", isArchived = archived,
        createdAt = NOW, updatedAt = NOW,
    )

    fun category(
        id: String = UUID.randomUUID().toString(),
        name: String = "Category $id",
        kind: CategoryKind = CategoryKind.EXPENSE,
    ) = Category(id = id, name = name, kind = kind, color = 0xFF000000.toInt(), icon = "tag", createdAt = NOW, updatedAt = NOW)

    fun tx(
        type: TransactionType,
        amount: Long,
        account: String,
        to: String? = null,
        category: String? = null,
        date: LocalDate = DAY,
        id: String = UUID.randomUUID().toString(),
        linked: String? = null,
        payee: String? = null,
    ) = Transaction(
        id = id, type = type, amount = Money(amount), accountId = account, toAccountId = to,
        categoryId = category, date = date, time = TIME, payee = payee,
        linkedTransactionId = linked, createdAt = NOW, updatedAt = NOW,
    )

    fun income(amount: Long, account: String, category: String? = null, date: LocalDate = DAY) =
        tx(TransactionType.INCOME, amount, account, category = category, date = date)

    fun expense(amount: Long, account: String, category: String? = null, date: LocalDate = DAY, id: String = UUID.randomUUID().toString()) =
        tx(TransactionType.EXPENSE, amount, account, category = category, date = date, id = id)

    fun transfer(amount: Long, from: String, to: String, date: LocalDate = DAY, id: String = UUID.randomUUID().toString()) =
        tx(TransactionType.TRANSFER, amount, from, to = to, date = date, id = id)

    fun refund(amount: Long, account: String, original: String?, category: String? = null, date: LocalDate = DAY) =
        tx(TransactionType.REFUND, amount, account, category = category, date = date, linked = original)

    fun adjustment(delta: Long, account: String, date: LocalDate = DAY) =
        tx(TransactionType.ADJUSTMENT, delta, account, date = date)
}
