package com.attiqtherehman.kharcha.domain.usecase

import androidx.room.withTransaction
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.data.local.KharchaDatabase
import com.attiqtherehman.kharcha.data.prefs.SettingsDataStore
import com.attiqtherehman.kharcha.data.seed.DefaultData
import javax.inject.Inject

/** Populates default categories and starter accounts on first launch. Idempotent. */
class SeedDefaultsUseCase @Inject constructor(
    private val db: KharchaDatabase,
    private val settings: SettingsDataStore,
    private val clock: Clock,
) {
    suspend operator fun invoke() {
        val now = clock.nowMillis()
        val current = settings.current()
        var seededAccount = false
        db.withTransaction {
            if (db.categoryDao().count() == 0) {
                db.categoryDao().insertAll(DefaultData.categoryEntities(now))
            }
            if (db.accountDao().count() == 0) {
                db.accountDao().insertAll(DefaultData.accountEntities(now, current.baseCurrency))
                seededAccount = true
            }
        }
        if (seededAccount || current.defaultAccountId == null) {
            val accounts = db.accountDao().getAll()
            if (accounts.isNotEmpty() && (current.defaultAccountId == null || accounts.none { it.id == current.defaultAccountId })) {
                settings.setDefaultAccountId(accounts.firstOrNull { it.id == DefaultData.CASH_WALLET_ACCOUNT_ID }?.id ?: accounts.first().id)
            }
        }
    }
}
