package com.attiqtherehman.kharcha

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.attiqtherehman.kharcha.core.common.DataChangeHub
import com.attiqtherehman.kharcha.data.local.KharchaDatabase
import com.attiqtherehman.kharcha.data.prefs.SettingsDataStore
import com.attiqtherehman.kharcha.data.repository.AccountRepositoryImpl
import com.attiqtherehman.kharcha.data.repository.BudgetRepositoryImpl
import com.attiqtherehman.kharcha.data.repository.CategoryRepositoryImpl
import com.attiqtherehman.kharcha.data.repository.DayReviewRepositoryImpl
import com.attiqtherehman.kharcha.data.repository.RecurringTransactionRepositoryImpl
import com.attiqtherehman.kharcha.data.repository.ReminderRepositoryImpl
import com.attiqtherehman.kharcha.data.repository.SavingsGoalRepositoryImpl
import com.attiqtherehman.kharcha.data.repository.TransactionRepositoryImpl
import com.attiqtherehman.kharcha.domain.usecase.RecordRefundUseCase
import com.attiqtherehman.kharcha.domain.usecase.RecordTransactionUseCase
import com.attiqtherehman.kharcha.domain.usecase.RecordTransferUseCase
import com.attiqtherehman.kharcha.domain.usecase.SeedDefaultsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import java.util.UUID

/** Hand-wired object graph for Robolectric tests: real Room (in-memory), real repositories, fixed clock. */
class TestGraph(val clock: FixedClock = FixedClock()) {
    val context: Context = ApplicationProvider.getApplicationContext()
    val db: KharchaDatabase = Room.inMemoryDatabaseBuilder(context, KharchaDatabase::class.java).allowMainThreadQueries().build()
    val hub = DataChangeHub()
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Fresh preferences file per graph: the production store is a process-wide singleton that would leak state between tests. */
    val settings = SettingsDataStore(PreferenceDataStoreFactory.create(scope = storeScope) { File(context.cacheDir, "settings-${UUID.randomUUID()}.preferences_pb") })
    val accounts = AccountRepositoryImpl(db, db.accountDao(), clock, hub)
    val categories = CategoryRepositoryImpl(db, db.categoryDao(), clock, hub)
    val transactions = TransactionRepositoryImpl(db, db.transactionDao(), clock, hub)
    val budgets = BudgetRepositoryImpl(db, db.budgetDao(), db.transactionDao(), clock, hub)
    val goals = SavingsGoalRepositoryImpl(db, db.savingsGoalDao(), db.accountDao(), clock, hub)
    val reminders = ReminderRepositoryImpl(db, db.reminderDao(), db.transactionDao(), clock, hub)
    val recurring = RecurringTransactionRepositoryImpl(db, db.recurringTransactionDao(), db.transactionDao(), clock, hub)
    val dayReviews = DayReviewRepositoryImpl(db.dayReviewDao(), clock)
    val recordTransaction = RecordTransactionUseCase(transactions, accounts, settings, emptySet())
    val recordTransfer = RecordTransferUseCase(transactions, accounts, clock)
    val recordRefund = RecordRefundUseCase(transactions, accounts)
    val seed = SeedDefaultsUseCase(db, settings, clock)

    fun close() {
        db.close()
        storeScope.cancel()
    }
}
