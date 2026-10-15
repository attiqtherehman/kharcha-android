package com.attiqtherehman.kharcha.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.attiqtherehman.kharcha.data.local.entity.AccountEntity
import com.attiqtherehman.kharcha.data.local.entity.CategoryEntity
import com.attiqtherehman.kharcha.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/** A transaction joined with the display fields of its account(s) and category. */
data class TransactionRow(
    @Embedded val tx: TransactionEntity,
    val accountName: String,
    val accountType: String,
    val accountCurrency: String,
    val toAccountName: String?,
    val toAccountType: String?,
    val categoryName: String?,
    val categoryColor: Int?,
    val categoryIcon: String?,
    val categoryKind: String?,
)

data class PeriodTotalsRow(val incomeMinor: Long, val expenseMinor: Long, val savedMinor: Long)
data class CategorySpendRow(val categoryId: String?, val spentMinor: Long)
data class DailyTotalRow(val localDate: String, val incomeMinor: Long, val expenseMinor: Long)
data class MonthlyTotalRow(val month: String, val incomeMinor: Long, val expenseMinor: Long)
data class PayeeRow(val payee: String, val lastUsed: Long)

@Dao
interface TransactionDao {

    companion object {
        const val ROW_SELECT = """
            SELECT t.*, a.name AS accountName, a.type AS accountType, a.currencyCode AS accountCurrency,
                   ta.name AS toAccountName, ta.type AS toAccountType,
                   c.name AS categoryName, c.color AS categoryColor, c.icon AS categoryIcon, c.kind AS categoryKind
            FROM transactions t
            JOIN accounts a ON a.id = t.accountId
            LEFT JOIN accounts ta ON ta.id = t.toAccountId
            LEFT JOIN categories c ON c.id = t.categoryId
        """
        const val ROW_ORDER = " ORDER BY t.localDate DESC, t.localTime DESC, t.createdAt DESC"
    }

    @RawQuery(observedEntities = [TransactionEntity::class, AccountEntity::class, CategoryEntity::class])
    fun observeRows(query: SupportSQLiteQuery): Flow<List<TransactionRow>>

    @RawQuery
    suspend fun getRows(query: SupportSQLiteQuery): List<TransactionRow>

    @Query("$ROW_SELECT WHERE t.id = :id")
    fun observeRow(id: String): Flow<TransactionRow?>

    @Query("$ROW_SELECT WHERE t.id = :id")
    suspend fun getRow(id: String): TransactionRow?

    @Query("$ROW_SELECT WHERE t.localDate >= :from AND t.localDate < :toExclusive $ROW_ORDER LIMIT :limit")
    fun observeRecent(from: String, toExclusive: String, limit: Int): Flow<List<TransactionRow>>

    @Query("$ROW_SELECT $ROW_ORDER LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<TransactionRow>>

    @Query("$ROW_SELECT WHERE t.linkedTransactionId = :id $ROW_ORDER")
    fun observeLinkedTo(id: String): Flow<List<TransactionRow>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE linkedTransactionId = :id")
    suspend fun getLinkedTo(id: String): List<TransactionEntity>

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE type = 'REFUND' AND linkedTransactionId = :originalId AND id <> :excludeId")
    suspend fun sumRefundsLinkedTo(originalId: String, excludeId: String): Long

    @Query("SELECT * FROM transactions ORDER BY localDate, localTime, createdAt")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id > :afterId ORDER BY id LIMIT :limit")
    suspend fun pageById(afterId: String, limit: Int): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE localDate = :date")
    suspend fun countOnDate(date: String): Int

    @Query("SELECT DISTINCT localDate FROM transactions WHERE localDate >= :from AND localDate <= :to")
    suspend fun datesWithTransactions(from: String, to: String): List<String>

    @Query(
        """
        SELECT
          COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amountMinor ELSE 0 END), 0) AS incomeMinor,
          COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amountMinor WHEN t.type = 'REFUND' THEN -t.amountMinor ELSE 0 END), 0) AS expenseMinor,
          COALESCE(SUM(CASE
              WHEN t.type = 'TRANSFER' AND ta.type = 'SAVINGS' AND fa.type <> 'SAVINGS' THEN t.amountMinor
              WHEN t.type = 'TRANSFER' AND fa.type = 'SAVINGS' AND ta.type <> 'SAVINGS' THEN -t.amountMinor
              ELSE 0 END), 0) AS savedMinor
        FROM transactions t
        JOIN accounts fa ON fa.id = t.accountId
        LEFT JOIN accounts ta ON ta.id = t.toAccountId
        WHERE t.localDate >= :from AND t.localDate < :toExclusive
        """
    )
    fun observePeriodTotals(from: String, toExclusive: String): Flow<PeriodTotalsRow>

    @Query(
        """
        SELECT
          COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amountMinor ELSE 0 END), 0) AS incomeMinor,
          COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amountMinor WHEN t.type = 'REFUND' THEN -t.amountMinor ELSE 0 END), 0) AS expenseMinor,
          COALESCE(SUM(CASE
              WHEN t.type = 'TRANSFER' AND ta.type = 'SAVINGS' AND fa.type <> 'SAVINGS' THEN t.amountMinor
              WHEN t.type = 'TRANSFER' AND fa.type = 'SAVINGS' AND ta.type <> 'SAVINGS' THEN -t.amountMinor
              ELSE 0 END), 0) AS savedMinor
        FROM transactions t
        JOIN accounts fa ON fa.id = t.accountId
        LEFT JOIN accounts ta ON ta.id = t.toAccountId
        WHERE t.localDate >= :from AND t.localDate < :toExclusive
        """
    )
    suspend fun getPeriodTotals(from: String, toExclusive: String): PeriodTotalsRow

    @Query(
        """
        SELECT categoryId,
               COALESCE(SUM(CASE type WHEN 'EXPENSE' THEN amountMinor WHEN 'REFUND' THEN -amountMinor ELSE 0 END), 0) AS spentMinor
        FROM transactions
        WHERE type IN ('EXPENSE', 'REFUND') AND localDate >= :from AND localDate < :toExclusive
        GROUP BY categoryId
        ORDER BY spentMinor DESC
        """
    )
    fun observeSpendByCategory(from: String, toExclusive: String): Flow<List<CategorySpendRow>>

    @Query(
        """
        SELECT categoryId,
               COALESCE(SUM(CASE type WHEN 'EXPENSE' THEN amountMinor WHEN 'REFUND' THEN -amountMinor ELSE 0 END), 0) AS spentMinor
        FROM transactions
        WHERE type IN ('EXPENSE', 'REFUND') AND localDate >= :from AND localDate < :toExclusive
        GROUP BY categoryId
        ORDER BY spentMinor DESC
        """
    )
    suspend fun getSpendByCategory(from: String, toExclusive: String): List<CategorySpendRow>

    @Query(
        """
        SELECT COALESCE(SUM(CASE type WHEN 'EXPENSE' THEN amountMinor WHEN 'REFUND' THEN -amountMinor ELSE 0 END), 0)
        FROM transactions
        WHERE type IN ('EXPENSE', 'REFUND') AND localDate >= :from AND localDate < :toExclusive
        """
    )
    suspend fun getTotalSpend(from: String, toExclusive: String): Long

    @Query(
        """
        SELECT COALESCE(SUM(CASE type WHEN 'EXPENSE' THEN amountMinor WHEN 'REFUND' THEN -amountMinor ELSE 0 END), 0)
        FROM transactions
        WHERE type IN ('EXPENSE', 'REFUND') AND categoryId = :categoryId AND localDate >= :from AND localDate < :toExclusive
        """
    )
    suspend fun getCategorySpend(categoryId: String, from: String, toExclusive: String): Long

    @Query(
        """
        SELECT localDate,
               COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountMinor ELSE 0 END), 0) AS incomeMinor,
               COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountMinor WHEN type = 'REFUND' THEN -amountMinor ELSE 0 END), 0) AS expenseMinor
        FROM transactions
        WHERE localDate >= :from AND localDate < :toExclusive
        GROUP BY localDate
        ORDER BY localDate
        """
    )
    fun observeDailyTotals(from: String, toExclusive: String): Flow<List<DailyTotalRow>>

    @Query(
        """
        SELECT substr(localDate, 1, 7) AS month,
               COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountMinor ELSE 0 END), 0) AS incomeMinor,
               COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountMinor WHEN type = 'REFUND' THEN -amountMinor ELSE 0 END), 0) AS expenseMinor
        FROM transactions
        WHERE localDate >= :from AND localDate < :toExclusive
        GROUP BY month
        ORDER BY month
        """
    )
    fun observeMonthlyTotals(from: String, toExclusive: String): Flow<List<MonthlyTotalRow>>

    @Query(
        """
        SELECT payee, MAX(createdAt) AS lastUsed FROM transactions
        WHERE payee IS NOT NULL AND payee <> '' AND (:type IS NULL OR type = :type)
        GROUP BY payee ORDER BY lastUsed DESC LIMIT :limit
        """
    )
    suspend fun recentPayees(type: String?, limit: Int): List<PayeeRow>

    @Query("SELECT tags FROM transactions WHERE tags IS NOT NULL AND tags <> '' ORDER BY createdAt DESC LIMIT 500")
    suspend fun recentTagStrings(): List<String>

    @Query("SELECT * FROM transactions WHERE attachmentFileName IS NOT NULL")
    suspend fun withAttachments(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    /** Used by the recurring materializer: duplicates on (rule, occurrence) are silently skipped. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(transactions: List<TransactionEntity>)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
