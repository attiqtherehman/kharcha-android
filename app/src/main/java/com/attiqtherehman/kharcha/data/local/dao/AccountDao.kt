package com.attiqtherehman.kharcha.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.attiqtherehman.kharcha.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

/** An account row plus its derived balance (opening balance + ledger effects). */
data class AccountBalanceRow(
    @Embedded val account: AccountEntity,
    val balanceMinor: Long,
)

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY isArchived, sortOrder, name")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY isArchived, sortOrder, name")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observeById(id: String): Flow<AccountEntity?>

    @Query(
        """
        SELECT a.*, a.openingBalanceMinor + COALESCE(SUM(e.deltaMinor), 0) AS balanceMinor
        FROM accounts a
        LEFT JOIN v_ledger_effects e ON e.accountId = a.id
        GROUP BY a.id
        ORDER BY a.isArchived, a.sortOrder, a.name
        """
    )
    fun observeBalances(): Flow<List<AccountBalanceRow>>

    @Query(
        """
        SELECT a.*, a.openingBalanceMinor + COALESCE(SUM(e.deltaMinor), 0) AS balanceMinor
        FROM accounts a
        LEFT JOIN v_ledger_effects e ON e.accountId = a.id
        GROUP BY a.id
        ORDER BY a.isArchived, a.sortOrder, a.name
        """
    )
    suspend fun getBalances(): List<AccountBalanceRow>

    @Query(
        """
        SELECT a.*, a.openingBalanceMinor + COALESCE(SUM(e.deltaMinor), 0) AS balanceMinor
        FROM accounts a
        LEFT JOIN v_ledger_effects e ON e.accountId = a.id
        WHERE a.id = :id
        GROUP BY a.id
        """
    )
    fun observeBalance(id: String): Flow<AccountBalanceRow?>

    @Query(
        """
        SELECT a.openingBalanceMinor + COALESCE(SUM(e.deltaMinor), 0)
        FROM accounts a
        LEFT JOIN v_ledger_effects e ON e.accountId = a.id AND e.localDate <= :asOfDate
        WHERE a.id = :id
        GROUP BY a.id
        """
    )
    suspend fun balanceAsOf(id: String, asOfDate: String): Long?

    @Query("SELECT MIN(localDate) FROM v_ledger_effects WHERE accountId = :id")
    suspend fun earliestLedgerDate(id: String): String?

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :id OR toAccountId = :id")
    suspend fun transactionCount(id: String): Int

    @Query(
        """
        SELECT (SELECT COUNT(*) FROM transactions WHERE accountId = :id OR toAccountId = :id)
             + (SELECT COUNT(*) FROM recurring_transactions WHERE accountId = :id OR toAccountId = :id)
        """
    )
    suspend fun hardReferenceCount(id: String): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM accounts")
    suspend fun nextSortOrder(): Int

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(accounts: List<AccountEntity>)

    @Update
    suspend fun update(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()
}
