package com.attiqtherehman.kharcha.di

import android.content.Context
import androidx.room.Room
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.core.datetime.SystemClock
import com.attiqtherehman.kharcha.data.local.KharchaDatabase
import com.attiqtherehman.kharcha.data.local.dao.AccountDao
import com.attiqtherehman.kharcha.data.local.dao.BudgetDao
import com.attiqtherehman.kharcha.data.local.dao.CategoryDao
import com.attiqtherehman.kharcha.data.local.dao.DayReviewDao
import com.attiqtherehman.kharcha.data.local.dao.RecurringTransactionDao
import com.attiqtherehman.kharcha.data.local.dao.ReminderDao
import com.attiqtherehman.kharcha.data.local.dao.SavingsGoalDao
import com.attiqtherehman.kharcha.data.local.dao.TransactionDao
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ClockModule {
    @Binds
    abstract fun bindClock(impl: SystemClock): Clock
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KharchaDatabase =
        Room.databaseBuilder(context, KharchaDatabase::class.java, KharchaDatabase.NAME).build()

    @Provides fun accountDao(db: KharchaDatabase): AccountDao = db.accountDao()
    @Provides fun categoryDao(db: KharchaDatabase): CategoryDao = db.categoryDao()
    @Provides fun transactionDao(db: KharchaDatabase): TransactionDao = db.transactionDao()
    @Provides fun recurringTransactionDao(db: KharchaDatabase): RecurringTransactionDao = db.recurringTransactionDao()
    @Provides fun budgetDao(db: KharchaDatabase): BudgetDao = db.budgetDao()
    @Provides fun savingsGoalDao(db: KharchaDatabase): SavingsGoalDao = db.savingsGoalDao()
    @Provides fun reminderDao(db: KharchaDatabase): ReminderDao = db.reminderDao()
    @Provides fun dayReviewDao(db: KharchaDatabase): DayReviewDao = db.dayReviewDao()
}
