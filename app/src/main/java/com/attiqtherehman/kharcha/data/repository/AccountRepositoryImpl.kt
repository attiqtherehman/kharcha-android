package com.attiqtherehman.kharcha.data.repository

import androidx.room.withTransaction
import com.attiqtherehman.kharcha.core.common.DataChangeNotifier
import com.attiqtherehman.kharcha.core.common.HasReferencesException
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.data.local.KharchaDatabase
import com.attiqtherehman.kharcha.data.local.dao.AccountDao
import com.attiqtherehman.kharcha.data.mapper.toDomain
import com.attiqtherehman.kharcha.data.mapper.toEntity
import com.attiqtherehman.kharcha.domain.model.Account
import com.attiqtherehman.kharcha.domain.model.AccountBalance
import com.attiqtherehman.kharcha.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val db: KharchaDatabase,
    private val dao: AccountDao,
    private val clock: Clock,
    private val notifier: DataChangeNotifier,
) : AccountRepository {

    override fun observeAccounts(): Flow<List<Account>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeBalances(): Flow<List<AccountBalance>> = dao.observeBalances().map { rows -> rows.map { it.toDomain() } }

    override fun observeBalance(id: String): Flow<AccountBalance?> = dao.observeBalance(id).map { it?.toDomain() }

    override suspend fun getAccount(id: String): Account? = dao.getById(id)?.toDomain()

    override suspend fun getAccounts(): List<Account> = dao.getAll().map { it.toDomain() }

    override suspend fun getBalances(): List<AccountBalance> = dao.getBalances().map { it.toDomain() }

    override suspend fun create(account: Account) {
        val order = dao.nextSortOrder()
        dao.insert(account.copy(sortOrder = order).toEntity())
        notifier.financialDataChanged()
    }

    override suspend fun update(account: Account) {
        dao.update(account.copy(updatedAt = clock.nowMillis()).toEntity())
        notifier.financialDataChanged()
    }

    override suspend fun setArchived(id: String, archived: Boolean) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(isArchived = archived, updatedAt = clock.nowMillis()))
        notifier.financialDataChanged()
    }

    override suspend fun delete(id: String) {
        val references = dao.hardReferenceCount(id)
        if (references > 0) throw HasReferencesException(references)
        dao.getById(id)?.let { dao.delete(it) }
        notifier.financialDataChanged()
    }

    override suspend fun hardReferenceCount(id: String): Int = dao.hardReferenceCount(id)

    override suspend fun transactionCount(id: String): Int = dao.transactionCount(id)

    override suspend fun balanceAsOf(id: String, date: LocalDate): Money =
        Money(dao.balanceAsOf(id, date.toString()) ?: 0L)

    override suspend fun earliestLedgerDate(id: String): LocalDate? = dao.earliestLedgerDate(id)?.let(LocalDate::parse)

    override suspend fun reorder(idsInOrder: List<String>) {
        val now = clock.nowMillis()
        db.withTransaction {
            idsInOrder.forEachIndexed { index, id ->
                dao.getById(id)?.let { if (it.sortOrder != index) dao.update(it.copy(sortOrder = index, updatedAt = now)) }
            }
        }
        notifier.financialDataChanged()
    }
}
