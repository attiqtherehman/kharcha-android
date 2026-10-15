package com.attiqtherehman.kharcha.data.backup

import com.attiqtherehman.kharcha.data.local.KharchaDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Versioned backup archive layout:
 *
 * ```
 * manifest.json            BackupManifest
 * settings.json            PortableSettings
 * tables/<table>.json      JSON array of rows (entity field names)
 * attachments/<uuid>.<ext> receipt images
 * ```
 */
object BackupFormat {
    const val FORMAT_VERSION = 1
    const val MANIFEST = "manifest.json"
    const val SETTINGS = "settings.json"
    const val TABLES_DIR = "tables/"
    const val ATTACHMENTS_DIR = "attachments/"
    const val MAX_ENTRY_BYTES = 256L * 1024 * 1024

    /** Insert order satisfies foreign keys; delete in reverse. */
    val TABLES_IN_ORDER = listOf(
        "accounts", "categories", "recurring_transactions", "transactions", "savings_goals", "goal_contributions",
        "reminders", "bill_payments", "monthly_budgets", "category_budgets", "day_reviews",
    )

    val attachmentNamePattern = Regex("""^[0-9a-f-]{36}\.(jpg|jpeg|png|webp)$""")

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
        prettyPrint = false
    }

    fun tableFile(table: String) = "$TABLES_DIR$table.json"
    val schemaVersion: Int get() = KharchaDatabase.VERSION
}

@Serializable
data class TableInfo(val file: String, val rowCount: Int)

@Serializable
data class BackupManifest(
    val formatVersion: Int,
    val schemaVersion: Int,
    val appVersionCode: Int,
    val appVersionName: String,
    val createdAt: Long,
    val deviceZone: String,
    val baseCurrency: String,
    val tables: Map<String, TableInfo>,
    val attachments: List<String>,
)
