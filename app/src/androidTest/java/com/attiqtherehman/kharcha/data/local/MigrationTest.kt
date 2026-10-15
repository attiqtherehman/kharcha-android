package com.attiqtherehman.kharcha.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens the exported v1 schema and runs every registered migration up to the current version.
 * When a v2 migration is added, insert rows at v1 here and assert they survive.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KharchaDatabase::class.java)

    @Test
    fun schemaV1_opensAndValidates() {
        val db = helper.createDatabase(TEST_DB, 1)
        db.execSQL(
            "INSERT INTO accounts (id, name, type, currencyCode, openingBalanceMinor, creditLimitMinor, color, icon, notes, isArchived, sortOrder, createdAt, updatedAt) " +
                "VALUES ('a1', 'Cash', 'CASH', 'PKR', 1000, NULL, 0, 'cash', NULL, 0, 0, 0, 0)",
        )
        db.close()
        val migrated = helper.runMigrationsAndValidate(TEST_DB, KharchaDatabase.VERSION, true)
        migrated.query("SELECT COUNT(*) FROM accounts").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
        migrated.query("SELECT deltaMinor FROM v_ledger_effects").use { c -> assertEquals(0, c.count) }
        migrated.close()
    }

    companion object {
        private const val TEST_DB = "migration-test.db"
    }
}
