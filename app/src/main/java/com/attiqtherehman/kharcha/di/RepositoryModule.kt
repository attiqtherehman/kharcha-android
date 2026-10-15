package com.attiqtherehman.kharcha.di

import com.attiqtherehman.kharcha.core.common.DataChangeHub
import com.attiqtherehman.kharcha.core.common.DataChangeNotifier
import com.attiqtherehman.kharcha.data.repository.AccountRepositoryImpl
import com.attiqtherehman.kharcha.data.repository.BudgetRepositoryImpl
import com.attiqtherehman.kharcha.data.repository.CategoryRepositoryImpl
import com.attiqtherehman.kharcha.data.repository.DayReviewRepositoryImpl
import com.attiqtherehman.kharcha.data.repository.RecurringTransactionRepositoryImpl
import com.attiqtherehman.kharcha.data.repository.ReminderRepositoryImpl
import com.attiqtherehman.kharcha.data.repository.SavingsGoalRepositoryImpl
import com.attiqtherehman.kharcha.data.repository.TransactionRepositoryImpl
import com.attiqtherehman.kharcha.domain.repository.AccountRepository
import com.attiqtherehman.kharcha.domain.repository.BudgetRepository
import com.attiqtherehman.kharcha.domain.repository.CategoryRepository
import com.attiqtherehman.kharcha.domain.repository.DayReviewRepository
import com.attiqtherehman.kharcha.domain.repository.RecurringTransactionRepository
import com.attiqtherehman.kharcha.domain.repository.ReminderRepository
import com.attiqtherehman.kharcha.domain.repository.SavingsGoalRepository
import com.attiqtherehman.kharcha.domain.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository
    @Binds abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository
    @Binds abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository
    @Binds abstract fun bindBudgetRepository(impl: BudgetRepositoryImpl): BudgetRepository
    @Binds abstract fun bindSavingsGoalRepository(impl: SavingsGoalRepositoryImpl): SavingsGoalRepository
    @Binds abstract fun bindReminderRepository(impl: ReminderRepositoryImpl): ReminderRepository
    @Binds abstract fun bindRecurringTransactionRepository(impl: RecurringTransactionRepositoryImpl): RecurringTransactionRepository
    @Binds abstract fun bindDayReviewRepository(impl: DayReviewRepositoryImpl): DayReviewRepository
    @Binds abstract fun bindDataChangeNotifier(impl: DataChangeHub): DataChangeNotifier
}
