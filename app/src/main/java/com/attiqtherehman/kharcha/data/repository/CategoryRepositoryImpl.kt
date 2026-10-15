package com.attiqtherehman.kharcha.data.repository

import androidx.room.withTransaction
import com.attiqtherehman.kharcha.core.common.DataChangeNotifier
import com.attiqtherehman.kharcha.core.common.HasReferencesException
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.data.local.KharchaDatabase
import com.attiqtherehman.kharcha.data.local.dao.CategoryDao
import com.attiqtherehman.kharcha.data.mapper.toDomain
import com.attiqtherehman.kharcha.data.mapper.toEntity
import com.attiqtherehman.kharcha.domain.model.Category
import com.attiqtherehman.kharcha.domain.model.CategoryKind
import com.attiqtherehman.kharcha.domain.model.TransactionType
import com.attiqtherehman.kharcha.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val db: KharchaDatabase,
    private val dao: CategoryDao,
    private val clock: Clock,
    private val notifier: DataChangeNotifier,
) : CategoryRepository {

    override fun observeAll(): Flow<List<Category>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeActive(kind: CategoryKind): Flow<List<Category>> =
        dao.observeActive(kind.name).map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(): List<Category> = dao.getAll().map { it.toDomain() }

    override suspend fun get(id: String): Category? = dao.getById(id)?.toDomain()

    override suspend fun create(category: Category) {
        val order = dao.nextSortOrder(category.kind.name)
        dao.insert(category.copy(sortOrder = order).toEntity())
        notifier.financialDataChanged()
    }

    override suspend fun update(category: Category) {
        dao.update(category.copy(updatedAt = clock.nowMillis()).toEntity())
        notifier.financialDataChanged()
    }

    override suspend fun setFavorite(id: String, favorite: Boolean) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(isFavorite = favorite, updatedAt = clock.nowMillis()))
        notifier.financialDataChanged()
    }

    override suspend fun setArchived(id: String, archived: Boolean) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(isArchived = archived, isFavorite = existing.isFavorite && !archived, updatedAt = clock.nowMillis()))
        notifier.financialDataChanged()
    }

    override suspend fun reorder(kind: CategoryKind, idsInOrder: List<String>) {
        val now = clock.nowMillis()
        db.withTransaction {
            idsInOrder.forEachIndexed { index, id ->
                dao.getById(id)?.let { if (it.sortOrder != index) dao.update(it.copy(sortOrder = index, updatedAt = now)) }
            }
        }
        notifier.financialDataChanged()
    }

    override suspend fun delete(id: String) {
        val references = dao.referenceCount(id)
        if (references > 0) throw HasReferencesException(references)
        dao.getById(id)?.let { dao.delete(it) }
        notifier.financialDataChanged()
    }

    override suspend fun referenceCount(id: String): Int = dao.referenceCount(id)

    override suspend fun mergeInto(fromId: String, toId: String) {
        require(fromId != toId) { "cannot merge a category into itself" }
        val from = dao.getById(fromId) ?: return
        requireNotNull(dao.getById(toId)) { "target category missing" }
        dao.mergeInto(from, toId)
        notifier.financialDataChanged()
    }

    override suspend fun favorites(kind: CategoryKind, limit: Int): List<Category> =
        dao.favorites(kind.name, limit).map { it.toDomain() }

    override suspend fun recentlyUsed(type: TransactionType, limit: Int): List<Category> {
        val ids = dao.recentlyUsedIds(type.name, limit)
        if (ids.isEmpty()) return emptyList()
        val byId = dao.getAll().associateBy { it.id }
        return ids.mapNotNull { byId[it]?.takeIf { c -> !c.isArchived }?.toDomain() }
    }
}
