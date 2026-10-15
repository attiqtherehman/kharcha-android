package com.attiqtherehman.kharcha.data.repository

import androidx.room.withTransaction
import com.attiqtherehman.kharcha.core.common.DataChangeNotifier
import com.attiqtherehman.kharcha.core.datetime.DateRange
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.data.local.KharchaDatabase
import com.attiqtherehman.kharcha.data.local.TransactionQueryBuilder
import com.attiqtherehman.kharcha.data.local.dao.TransactionDao
import com.attiqtherehman.kharcha.data.mapper.splitTags
import com.attiqtherehman.kharcha.data.mapper.toDomain
import com.attiqtherehman.kharcha.data.mapper.toEntity
import com.attiqtherehman.kharcha.domain.ledger.PeriodSummary
import com.attiqtherehman.kharcha.domain.model.Transaction
import com.attiqtherehman.kharcha.domain.model.TransactionDetails
import com.attiqtherehman.kharcha.domain.model.TransactionFilter
import com.attiqtherehman.kharcha.domain.model.TransactionType
import com.attiqtherehman.kharcha.domain.repository.DailyTotal
import com.attiqtherehman.kharcha.domain.repository.MonthlyTotal
import com.attiqtherehman.kharcha.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val db: KharchaDatabase,
    private val dao: TransactionDao,
    private val clock: Clock,
    private val notifier: DataChangeNotifier,
) : TransactionRepository {

    override fun observe(filter: TransactionFilter): Flow<List<TransactionDetails>> =
        dao.observeRows(TransactionQueryBuilder.build(filter)).map { rows -> rows.map { it.toDomain() } }

    override fun observeDetails(id: String): Flow<TransactionDetails?> = dao.observeRow(id).map { it?.toDomain() }

    override fun observeLinkedTo(id: String): Flow<List<TransactionDetails>> =
        dao.observeLinkedTo(id).map { rows -> rows.map { it.toDomain() } }

    override fun observeLatest(limit: Int): Flow<List<TransactionDetails>> =
        dao.observeLatest(limit).map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(id: String): Transaction? = dao.getById(id)?.toDomain()

    override suspend fun getLinkedTo(id: String): List<Transaction> = dao.getLinkedTo(id).map { it.toDomain() }

    override suspend fun list(filter: TransactionFilter): List<TransactionDetails> =
        dao.getRows(TransactionQueryBuilder.build(filter)).map { it.toDomain() }

    override suspend fun insertAll(transactions: List<Transaction>) {
        if (transactions.isEmpty()) return
        db.withTransaction { dao.insertAll(transactions.map { it.toEntity() }) }
        notifier.financialDataChanged()
    }

    override suspend fun update(transaction: Transaction) {
        dao.update(transaction.copy(updatedAt = clock.nowMillis()).toEntity())
        notifier.financialDataChanged()
    }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
        notifier.financialDataChanged()
    }

    override suspend fun deleteWithLinked(id: String) {
        db.withTransaction {
            dao.getLinkedTo(id).forEach { dao.deleteById(it.id) }
            dao.deleteById(id)
        }
        notifier.financialDataChanged()
    }

    override fun observePeriodSummary(range: DateRange): Flow<PeriodSummary> =
        dao.observePeriodTotals(range.sqlStart, range.sqlEndExclusive).map {
            PeriodSummary(Money(it.incomeMinor), Money(it.expenseMinor), Money(it.savedMinor))
        }

    override suspend fun periodSummary(range: DateRange): PeriodSummary =
        dao.getPeriodTotals(range.sqlStart, range.sqlEndExclusive).let {
            PeriodSummary(Money(it.incomeMinor), Money(it.expenseMinor), Money(it.savedMinor))
        }

    override fun observeSpendByCategory(range: DateRange): Flow<Map<String?, Money>> =
        dao.observeSpendByCategory(range.sqlStart, range.sqlEndExclusive).map { rows ->
            rows.associate { it.categoryId to Money(it.spentMinor) }
        }

    override suspend fun spendByCategory(range: DateRange): Map<String?, Money> =
        dao.getSpendByCategory(range.sqlStart, range.sqlEndExclusive).associate { it.categoryId to Money(it.spentMinor) }

    override suspend fun totalSpend(range: DateRange): Money = Money(dao.getTotalSpend(range.sqlStart, range.sqlEndExclusive))

    override suspend fun categorySpend(categoryId: String, range: DateRange): Money =
        Money(dao.getCategorySpend(categoryId, range.sqlStart, range.sqlEndExclusive))

    override fun observeDailyTotals(range: DateRange): Flow<List<DailyTotal>> =
        dao.observeDailyTotals(range.sqlStart, range.sqlEndExclusive).map { rows ->
            rows.map { DailyTotal(LocalDate.parse(it.localDate), Money(it.incomeMinor), Money(it.expenseMinor)) }
        }

    override fun observeMonthlyTotals(range: DateRange): Flow<List<MonthlyTotal>> =
        dao.observeMonthlyTotals(range.sqlStart, range.sqlEndExclusive).map { rows ->
            rows.map { MonthlyTotal(YearMonth.parse(it.month), Money(it.incomeMinor), Money(it.expenseMinor)) }
        }

    override suspend fun sumRefundsLinkedTo(originalId: String, excludeId: String?): Money =
        Money(dao.sumRefundsLinkedTo(originalId, excludeId ?: ""))

    override suspend fun recentPayees(type: TransactionType?, limit: Int): List<String> =
        dao.recentPayees(type?.name, limit).map { it.payee }

    override suspend fun recentTags(limit: Int): List<String> {
        val counts = HashMap<String, Int>()
        dao.recentTagStrings().forEach { raw -> splitTags(raw).forEach { counts[it] = (counts[it] ?: 0) + 1 } }
        return counts.entries.sortedByDescending { it.value }.take(limit).map { it.key }
    }

    override suspend fun countOnDate(date: LocalDate): Int = dao.countOnDate(date.toString())

    override suspend fun datesWithTransactions(range: DateRange): Set<LocalDate> =
        dao.datesWithTransactions(range.sqlStart, range.endInclusive.toString()).map(LocalDate::parse).toSet()
}
