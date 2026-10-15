package com.attiqtherehman.kharcha.domain.model

import com.attiqtherehman.kharcha.core.money.Money

data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val currencyCode: String,
    val openingBalance: Money,
    val creditLimit: Money? = null,
    val color: Int,
    val icon: String,
    val notes: String? = null,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

/** An account with its derived (never stored) balance. */
data class AccountBalance(val account: Account, val balance: Money) {
    /** Outstanding debt on a credit card (balance below zero), else zero. */
    val owed: Money get() = if (account.type == AccountType.CREDIT_CARD && balance.isNegative) -balance else Money.ZERO

    /** creditLimit + balance for cards with a limit; null otherwise. */
    val availableCredit: Money? get() = account.creditLimit?.let { it + balance }

    val isOverLimit: Boolean get() = availableCredit?.isNegative == true
}
