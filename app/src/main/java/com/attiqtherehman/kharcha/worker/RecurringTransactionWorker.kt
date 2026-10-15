package com.attiqtherehman.kharcha.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.attiqtherehman.kharcha.AppStartHook
import com.attiqtherehman.kharcha.domain.usecase.MaterializeRecurringTransactionsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Daily safety net that records due recurring transactions even if the app is not opened. */
@HiltWorker
class RecurringTransactionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val materialize: MaterializeRecurringTransactionsUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        materialize()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "recurring-materialize"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<RecurringTransactionWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** On app start: materialise immediately (no WorkManager hop) and make sure the daily job exists. */
@Singleton
class RecurringStartHook @Inject constructor(
    @ApplicationContext private val context: Context,
    private val materialize: MaterializeRecurringTransactionsUseCase,
) : AppStartHook {
    override suspend fun onAppStart() {
        materialize()
        RecurringTransactionWorker.enqueue(context)
    }
}
