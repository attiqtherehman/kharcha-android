package com.attiqtherehman.kharcha.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.attiqtherehman.kharcha.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY kind, isArchived, sortOrder, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY kind, isArchived, sortOrder, name")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE kind = :kind AND isArchived = 0 ORDER BY sortOrder, name")
    fun observeActive(kind: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE kind = :kind AND isFavorite = 1 AND isArchived = 0 ORDER BY sortOrder, name LIMIT :limit")
    suspend fun favorites(kind: String, limit: Int): List<CategoryEntity>

    /** Category ids by most recent use for [type] transactions. */
    @Query(
        """
        SELECT categoryId FROM transactions
        WHERE type = :type AND categoryId IS NOT NULL
        GROUP BY categoryId
        ORDER BY MAX(createdAt) DESC
        LIMIT :limit
        """
    )
    suspend fun recentlyUsedIds(type: String, limit: Int): List<String>

    @Query(
        """
        SELECT (SELECT COUNT(*) FROM transactions WHERE categoryId = :id)
             + (SELECT COUNT(*) FROM category_budgets WHERE categoryId = :id)
             + (SELECT COUNT(*) FROM recurring_transactions WHERE categoryId = :id)
             + (SELECT COUNT(*) FROM reminders WHERE categoryId = :id)
        """
    )
    suspend fun referenceCount(id: String): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM categories WHERE kind = :kind")
    suspend fun nextSortOrder(kind: String): Int

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Update
    suspend fun updateAll(categories: List<CategoryEntity>)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    @Query("UPDATE transactions SET categoryId = :to WHERE categoryId = :from")
    suspend fun reassignTransactions(from: String, to: String)

    @Query("UPDATE recurring_transactions SET categoryId = :to WHERE categoryId = :from")
    suspend fun reassignRecurring(from: String, to: String)

    @Query("UPDATE reminders SET categoryId = :to WHERE categoryId = :from")
    suspend fun reassignReminders(from: String, to: String)

    @Query("DELETE FROM category_budgets WHERE categoryId = :from AND yearMonth IN (SELECT yearMonth FROM category_budgets WHERE categoryId = :to)")
    suspend fun dropConflictingBudgets(from: String, to: String)

    @Query("UPDATE category_budgets SET categoryId = :to WHERE categoryId = :from")
    suspend fun reassignBudgets(from: String, to: String)

    /** Moves every reference from [from] to [to] then deletes [from]. */
    @Transaction
    suspend fun mergeInto(from: CategoryEntity, to: String) {
        reassignTransactions(from.id, to)
        reassignRecurring(from.id, to)
        reassignReminders(from.id, to)
        dropConflictingBudgets(from.id, to)
        reassignBudgets(from.id, to)
        delete(from)
    }
}
