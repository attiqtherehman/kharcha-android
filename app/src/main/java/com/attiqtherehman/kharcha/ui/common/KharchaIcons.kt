package com.attiqtherehman.kharcha.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
import com.attiqtherehman.kharcha.domain.model.AccountType
import com.attiqtherehman.kharcha.domain.model.TransactionType

/** Maps the string icon keys stored in the database to Material icons. */
object KharchaIcons {

    private val byKey: Map<String, ImageVector> = mapOf(
        "restaurant" to Icons.Filled.Restaurant,
        "grocery" to Icons.Filled.LocalGroceryStore,
        "fuel" to Icons.Filled.LocalGasStation,
        "shopping" to Icons.Filled.ShoppingBag,
        "bills" to Icons.Filled.Receipt,
        "home" to Icons.Filled.Home,
        "health" to Icons.Filled.LocalHospital,
        "spa" to Icons.Filled.Spa,
        "movie" to Icons.Filled.Movie,
        "family" to Icons.Filled.FamilyRestroom,
        "school" to Icons.Filled.School,
        "flight" to Icons.Filled.Flight,
        "build" to Icons.Filled.Build,
        "loan" to Icons.Filled.RequestQuote,
        "landscape" to Icons.Filled.Landscape,
        "gift" to Icons.Filled.CardGiftcard,
        "bank" to Icons.Filled.AccountBalance,
        "more" to Icons.Filled.MoreHoriz,
        "work" to Icons.Filled.Work,
        "laptop" to Icons.Filled.Laptop,
        "store" to Icons.Filled.Storefront,
        "apartment" to Icons.Filled.Apartment,
        "trending" to Icons.Filled.TrendingUp,
        "wallet" to Icons.Filled.AccountBalanceWallet,
        "card" to Icons.Filled.CreditCard,
        "savings" to Icons.Filled.Savings,
        "phone" to Icons.Filled.PhoneAndroid,
        "wifi" to Icons.Filled.Wifi,
        "bolt" to Icons.Filled.Bolt,
        "water" to Icons.Filled.WaterDrop,
        "car" to Icons.Filled.DirectionsCar,
        "pets" to Icons.Filled.Pets,
        "fitness" to Icons.Filled.FitnessCenter,
        "coffee" to Icons.Filled.LocalCafe,
        "cake" to Icons.Filled.Cake,
        "baby" to Icons.Filled.ChildCare,
        "medical" to Icons.Filled.MedicalServices,
        "charity" to Icons.Filled.VolunteerActivism,
        "subscription" to Icons.Filled.Subscriptions,
        "insurance" to Icons.Filled.HealthAndSafety,
        "tools" to Icons.Filled.Handyman,
        "cash" to Icons.Filled.Payments,
        "other" to Icons.Filled.Wallet,
    )

    fun category(key: String?): ImageVector = byKey[key] ?: Icons.Filled.Category

    fun account(type: AccountType, key: String? = null): ImageVector = byKey[key] ?: when (type) {
        AccountType.CASH -> Icons.Filled.Payments
        AccountType.BANK -> Icons.Filled.AccountBalance
        AccountType.SAVINGS -> Icons.Filled.Savings
        AccountType.DIGITAL_WALLET -> Icons.Filled.PhoneAndroid
        AccountType.CREDIT_CARD -> Icons.Filled.CreditCard
        AccountType.OTHER -> Icons.Filled.Wallet
    }

    fun transactionType(type: TransactionType): ImageVector = when (type) {
        TransactionType.INCOME -> Icons.Filled.ArrowDownward
        TransactionType.EXPENSE -> Icons.Filled.ArrowUpward
        TransactionType.TRANSFER -> Icons.Filled.SwapHoriz
        TransactionType.REFUND -> Icons.Filled.Undo
        TransactionType.ADJUSTMENT -> Icons.Filled.Tune
    }

    val allKeys: List<String> get() = byKey.keys.toList()
}
