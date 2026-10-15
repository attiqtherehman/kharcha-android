package com.attiqtherehman.kharcha.domain.model

import com.attiqtherehman.kharcha.core.datetime.DateRange

/** A transaction with the display fields of its account(s) and category resolved. */
data class TransactionDetails(
    val transaction: Transaction,
    val accountName: String,
    val accountType: AccountType,
    val currencyCode: String,
    val toAccountName: String?,
    val toAccountType: AccountType?,
    val categoryName: String?,
    val categoryColor: Int?,
    val categoryIcon: String?,
) {
    val id: String get() = transaction.id
}

/** Filter for transaction lists. Null members mean "no restriction". */
data class TransactionFilter(
    val types: Set<TransactionType>? = null,
    val accountIds: Set<String>? = null,
    val categoryIds: Set<String>? = null,
    val range: DateRange? = null,
    val query: String? = null,
    val tag: String? = null,
    val uncategorisedOnly: Boolean = false,
    val limit: Int? = null,
) {
    val isEmpty: Boolean
        get() = types == null && accountIds == null && categoryIds == null && range == null &&
            query.isNullOrBlank() && tag.isNullOrBlank() && !uncategorisedOnly
}
