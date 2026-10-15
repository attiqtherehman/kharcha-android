package com.attiqtherehman.kharcha.data.local

import androidx.sqlite.db.SimpleSQLiteQuery
import com.attiqtherehman.kharcha.data.local.dao.TransactionDao
import com.attiqtherehman.kharcha.domain.model.TransactionFilter

/** Builds the dynamic WHERE clause for transaction lists from a [TransactionFilter]. */
object TransactionQueryBuilder {

    fun build(filter: TransactionFilter): SimpleSQLiteQuery {
        val where = ArrayList<String>()
        val args = ArrayList<Any>()

        filter.types?.takeIf { it.isNotEmpty() }?.let { types ->
            where += "t.type IN (${placeholders(types.size)})"
            args.addAll(types.map { it.name })
        }
        filter.accountIds?.takeIf { it.isNotEmpty() }?.let { ids ->
            where += "(t.accountId IN (${placeholders(ids.size)}) OR t.toAccountId IN (${placeholders(ids.size)}))"
            args.addAll(ids)
            args.addAll(ids)
        }
        filter.categoryIds?.takeIf { it.isNotEmpty() }?.let { ids ->
            where += "t.categoryId IN (${placeholders(ids.size)})"
            args.addAll(ids)
        }
        if (filter.uncategorisedOnly) {
            where += "t.categoryId IS NULL AND t.type IN ('INCOME', 'EXPENSE', 'REFUND')"
        }
        filter.range?.let { range ->
            where += "t.localDate >= ? AND t.localDate < ?"
            args += range.sqlStart
            args += range.sqlEndExclusive
        }
        filter.query?.trim()?.takeIf { it.isNotEmpty() }?.let { q ->
            val like = "%" + escapeLike(q) + "%"
            where += "(t.payee LIKE ? ESCAPE '\\' OR t.description LIKE ? ESCAPE '\\' OR t.notes LIKE ? ESCAPE '\\' " +
                "OR t.tags LIKE ? ESCAPE '\\' OR c.name LIKE ? ESCAPE '\\' OR a.name LIKE ? ESCAPE '\\' OR ta.name LIKE ? ESCAPE '\\')"
            repeat(7) { args += like }
        }
        filter.tag?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { tag ->
            where += "(',' || t.tags || ',') LIKE ? ESCAPE '\\'"
            args += "%," + escapeLike(tag) + ",%"
        }

        val sql = buildString {
            append(TransactionDao.ROW_SELECT)
            if (where.isNotEmpty()) append(" WHERE ").append(where.joinToString(" AND "))
            append(TransactionDao.ROW_ORDER)
            filter.limit?.let { append(" LIMIT ").append(it) }
        }
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    private fun placeholders(n: Int): String = List(n) { "?" }.joinToString(",")

    private fun escapeLike(s: String): String =
        s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
