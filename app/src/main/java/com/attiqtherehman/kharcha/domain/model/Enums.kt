package com.attiqtherehman.kharcha.domain.model

enum class AccountType {
    CASH, BANK, SAVINGS, DIGITAL_WALLET, CREDIT_CARD, OTHER;

    /** Money the user can spend directly (dashboard "available"). */
    val isSpendable: Boolean get() = this == CASH || this == BANK || this == DIGITAL_WALLET
    val isLiability: Boolean get() = this == CREDIT_CARD
}

enum class CategoryKind { INCOME, EXPENSE }

enum class TransactionType {
    INCOME, EXPENSE, TRANSFER, REFUND, ADJUSTMENT;

    val usesCategory: Boolean get() = this == INCOME || this == EXPENSE || this == REFUND
}

enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

enum class ReminderStatus { ACTIVE, PAUSED, COMPLETED }

/** Derived classification of a transfer, used for labels only; never stored. */
enum class TransferKind { GENERAL, ATM_WITHDRAWAL, SAVINGS, CARD_REPAYMENT }
