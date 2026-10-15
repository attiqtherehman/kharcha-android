package com.attiqtherehman.kharcha.domain.repository

import com.attiqtherehman.kharcha.core.datetime.DateRange
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.domain.ledger.PeriodSummary
import com.attiqtherehman.kharcha.domain.model.Account
import com.attiqtherehman.kharcha.domain.model.AccountBalance
import com.attiqtherehman.kharcha.domain.model.Category
import com.attiqtherehman.kharcha.domain.model.CategoryKind
import com.attiqtherehman.kharcha.domain.model.Transaction
import com.attiqtherehman.kharcha.domain.model.TransactionDetails
import com.attiqtherehman.kharcha.domain.model.TransactionFilter
import com.attiqtherehman.kharcha.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

interface AccountRepository {
    fun observeAccounts(): Flow<List<Account>>
    fun observeBalances(): Flow<List<AccountBalance>>
    fun observeBalance(id: String): Flow<AccountBalance?>
    suspend fun getAccount(id: String): Account?
    suspend fun getAccounts(): List<Account>
    suspend fun getBalances(): List<AccountBalance>
    suspend fun create(account: Account)
    suspend fun update(account: Account)
    suspend fun setArchived(id: String, archived: Boolean)
    /** Deletes only when nothing references the account; otherwise throws HasReferencesException. */
    suspend fun delete(id: String)
    suspend fun hardReferenceCount(id: String): Int
    suspend fun transactionCount(id: String): Int
    suspend fun balanceAsOf(id: String, date: LocalDate): Money
    suspend fun earliestLedgerDate(id: String): LocalDate?
    suspend fun reorder(idsInOrder: List<String>)
}

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    fun observeActive(kind: CategoryKind): Flow<List<Category>>
    suspend fun getAll(): List<Category>
    suspend fun get(id: String): Category?
    suspend fun create(category: Category)
    suspend fun update(category: Category)
    suspend fun setFavorite(id: String, favorite: Boolean)
    suspend fun setArchived(id: String, archived: Boolean)
    suspend fun reorder(kind: CategoryKind, idsInOrder: List<String>)
    /** Hard delete; throws HasReferencesException when history exists. */
    suspend fun delete(id: String)
    suspend fun referenceCount(id: String): Int
    suspend fun mergeInto(fromId: String, toId: String)
    suspend fun favorites(kind: CategoryKind, limit: Int): List<Category>
    suspend fun recentlyUsed(type: TransactionType, limit: Int): List<Category>
}

data class DailyTotal(val date: LocalDate, val income: Money, val expenses: Money)
data class MonthlyTotal(val month: YearMonth, val income: Money, val expenses: Money)

interface TransactionRepository {
    fun observe(filter: TransactionFilter): Flow<List<TransactionDetails>>
    fun observeDetails(id: String): Flow<TransactionDetails?>
    fun observeLinkedTo(id: String): Flow<List<TransactionDetails>>
    fun observeLatest(limit: Int): Flow<List<TransactionDetails>>
    suspend fun get(id: String): Transaction?
    suspend fun getLinkedTo(id: String): List<Transaction>
    suspend fun list(filter: TransactionFilter): List<TransactionDetails>

    /** Inserts one or more rows atomically (e.g. a transfer and its fee). Caller has validated. */
    suspend fun insertAll(transactions: List<Transaction>)
    suspend fun update(transaction: Transaction)
    suspend fun delete(id: String)
    /** Deletes [id] and every row linked to it (fees, refunds) atomically. */
    suspend fun deleteWithLinked(id: String)

    fun observePeriodSummary(range: DateRange): Flow<PeriodSummary>
    suspend fun periodSummary(range: DateRange): PeriodSummary
    fun observeSpendByCategory(range: DateRange): Flow<Map<String?, Money>>
    suspend fun spendByCategory(range: DateRange): Map<String?, Money>
    suspend fun totalSpend(range: DateRange): Money
    suspend fun categorySpend(categoryId: String, range: DateRange): Money
    fun observeDailyTotals(range: DateRange): Flow<List<DailyTotal>>
    fun observeMonthlyTotals(range: DateRange): Flow<List<MonthlyTotal>>

    suspend fun sumRefundsLinkedTo(originalId: String, excludeId: String? = null): Money
    suspend fun recentPayees(type: TransactionType?, limit: Int): List<String>
    suspend fun recentTags(limit: Int): List<String>
    suspend fun countOnDate(date: LocalDate): Int
    suspend fun datesWithTransactions(range: DateRange): Set<LocalDate>
}
