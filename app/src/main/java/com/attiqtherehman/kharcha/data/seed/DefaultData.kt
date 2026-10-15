package com.attiqtherehman.kharcha.data.seed

import com.attiqtherehman.kharcha.core.common.Ids
import com.attiqtherehman.kharcha.data.local.entity.AccountEntity
import com.attiqtherehman.kharcha.data.local.entity.CategoryEntity
import com.attiqtherehman.kharcha.domain.model.AccountType
import com.attiqtherehman.kharcha.domain.model.CategoryKind

/**
 * Seed data for a fresh install. Ids are deterministic (derived from the key) so that backups from
 * two installs merge without duplicating "Food & Dining" twice.
 */
object DefaultData {

    data class CategorySeed(
        val key: String,
        val name: String,
        val kind: CategoryKind,
        val icon: String,
        val color: Long,
        val favorite: Boolean = false,
    ) {
        val id: String get() = Ids.seeded("category:$key")
    }

    val categories: List<CategorySeed> = listOf(
        CategorySeed("food-dining", "Food & Dining", CategoryKind.EXPENSE, "restaurant", 0xFFE57343, favorite = true),
        CategorySeed("groceries", "Groceries", CategoryKind.EXPENSE, "grocery", 0xFF4CAF50, favorite = true),
        CategorySeed("transport-fuel", "Transport & Fuel", CategoryKind.EXPENSE, "fuel", 0xFF3F8CFF, favorite = true),
        CategorySeed("shopping", "Shopping", CategoryKind.EXPENSE, "shopping", 0xFFAB47BC, favorite = true),
        CategorySeed("bills-utilities", "Bills & Utilities", CategoryKind.EXPENSE, "bills", 0xFFFFA726),
        CategorySeed("rent", "Rent", CategoryKind.EXPENSE, "home", 0xFF8D6E63),
        CategorySeed("healthcare", "Healthcare", CategoryKind.EXPENSE, "health", 0xFFEF5350),
        CategorySeed("personal-care", "Personal Care", CategoryKind.EXPENSE, "spa", 0xFFEC407A),
        CategorySeed("entertainment", "Entertainment", CategoryKind.EXPENSE, "movie", 0xFF7E57C2),
        CategorySeed("family", "Family", CategoryKind.EXPENSE, "family", 0xFF26A69A),
        CategorySeed("education", "Education", CategoryKind.EXPENSE, "school", 0xFF5C6BC0),
        CategorySeed("travel", "Travel", CategoryKind.EXPENSE, "flight", 0xFF29B6F6),
        CategorySeed("home-maintenance", "Home Maintenance", CategoryKind.EXPENSE, "build", 0xFF8D6E63),
        CategorySeed("loan-payments", "Loan Payments", CategoryKind.EXPENSE, "loan", 0xFF78909C),
        CategorySeed("plot-property", "Plot / Property Payments", CategoryKind.EXPENSE, "landscape", 0xFF9CCC65),
        CategorySeed("gifts-donations", "Gifts & Donations", CategoryKind.EXPENSE, "gift", 0xFFF06292),
        CategorySeed("bank-fees", "Bank Fees", CategoryKind.EXPENSE, "bank", 0xFF90A4AE),
        CategorySeed("other-expense", "Other", CategoryKind.EXPENSE, "more", 0xFFBDBDBD),

        CategorySeed("salary", "Salary", CategoryKind.INCOME, "work", 0xFF2E7D32, favorite = true),
        CategorySeed("freelance", "Freelance", CategoryKind.INCOME, "laptop", 0xFF00897B),
        CategorySeed("business", "Business", CategoryKind.INCOME, "store", 0xFF558B2F),
        CategorySeed("rental-income", "Rental Income", CategoryKind.INCOME, "apartment", 0xFF6D4C41),
        CategorySeed("investment-income", "Investment Income", CategoryKind.INCOME, "trending", 0xFF1565C0),
        CategorySeed("gifts-income", "Gifts", CategoryKind.INCOME, "gift", 0xFFD81B60),
        CategorySeed("other-income", "Other Income", CategoryKind.INCOME, "more", 0xFF9E9E9E),
    )

    val BANK_FEES_CATEGORY_ID: String = Ids.seeded("category:bank-fees")
    val CASH_WALLET_ACCOUNT_ID: String = Ids.seeded("account:cash-wallet")
    val BANK_ACCOUNT_ID: String = Ids.seeded("account:bank")

    fun categoryEntities(now: Long): List<CategoryEntity> {
        var expenseOrder = 0
        var incomeOrder = 0
        return categories.map { seed ->
            val order = if (seed.kind == CategoryKind.EXPENSE) expenseOrder++ else incomeOrder++
            CategoryEntity(
                id = seed.id, name = seed.name, kind = seed.kind.name, color = seed.color.toInt(), icon = seed.icon,
                isFavorite = seed.favorite, isArchived = false, sortOrder = order, createdAt = now, updatedAt = now,
            )
        }
    }

    fun accountEntities(now: Long, currency: String): List<AccountEntity> = listOf(
        AccountEntity(
            id = CASH_WALLET_ACCOUNT_ID, name = "Cash Wallet", type = AccountType.CASH.name, currencyCode = currency,
            openingBalanceMinor = 0L, creditLimitMinor = null, color = 0xFF2E7D32.toInt(), icon = "wallet",
            notes = null, isArchived = false, sortOrder = 0, createdAt = now, updatedAt = now,
        ),
        AccountEntity(
            id = BANK_ACCOUNT_ID, name = "Bank Account", type = AccountType.BANK.name, currencyCode = currency,
            openingBalanceMinor = 0L, creditLimitMinor = null, color = 0xFF1565C0.toInt(), icon = "bank",
            notes = null, isArchived = false, sortOrder = 1, createdAt = now, updatedAt = now,
        ),
    )

    /** Palette offered in the account/category editors. */
    val palette: List<Long> = listOf(
        0xFFE57343, 0xFF4CAF50, 0xFF3F8CFF, 0xFFAB47BC, 0xFFFFA726, 0xFF8D6E63, 0xFFEF5350, 0xFFEC407A,
        0xFF7E57C2, 0xFF26A69A, 0xFF5C6BC0, 0xFF29B6F6, 0xFF78909C, 0xFF9CCC65, 0xFFF06292, 0xFF2E7D32,
        0xFF00897B, 0xFF1565C0, 0xFFD81B60, 0xFF6D4C41, 0xFF90A4AE, 0xFFBDBDBD,
    )

    /** Icon keys understood by the UI icon map. */
    val icons: List<String> = listOf(
        "restaurant", "grocery", "fuel", "shopping", "bills", "home", "health", "spa", "movie", "family", "school",
        "flight", "build", "loan", "landscape", "gift", "bank", "more", "work", "laptop", "store", "apartment",
        "trending", "wallet", "card", "savings", "phone", "wifi", "bolt", "water", "car", "pets", "fitness",
        "coffee", "cake", "baby", "medical", "charity", "subscription", "insurance", "tools",
    )
}
