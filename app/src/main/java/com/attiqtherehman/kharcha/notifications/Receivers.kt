package com.attiqtherehman.kharcha.notifications

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.attiqtherehman.kharcha.AppStartTasks
import com.attiqtherehman.kharcha.core.common.DataChangeHub
import com.attiqtherehman.kharcha.di.ApplicationScope
import com.attiqtherehman.kharcha.domain.repository.DayReviewRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import javax.inject.Inject

/** Runs [block] under goAsync with a budget safely inside the receiver's 10 s limit. */
internal fun BroadcastReceiver.runAsync(scope: CoroutineScope, block: suspend () -> Unit) {
    val pending = goAsync()
    scope.launch {
        try {
            withTimeoutOrNull(8_000) { block() }
        } finally {
            pending.finish()
        }
    }
}

/** Fired by the single "next wake" alarm. */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_WAKE) return
        runAsync(scope) { scheduler.evaluateAndReschedule() }
    }

    companion object {
        const val ACTION_WAKE = "com.attiqtherehman.kharcha.ACTION_WAKE"
    }
}

/** Handles notification action buttons that do not open the app. */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject lateinit var dayReviews: DayReviewRepository
    @Inject lateinit var notifier: ReminderNotifier
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MARK_REVIEWED -> {
                val date = intent.getStringExtra(EXTRA_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
                runAsync(scope) {
                    dayReviews.markReviewed(date)
                    notifier.cancelDailyReminder()
                }
            }
        }
    }

    companion object {
        const val ACTION_MARK_REVIEWED = "com.attiqtherehman.kharcha.ACTION_MARK_REVIEWED"
        const val EXTRA_DATE = "date"
    }
}

/** Boot, clock/zone changes and app updates: recompute alarms, recurring rows and widgets. */
@AndroidEntryPoint
class SystemEventsReceiver : BroadcastReceiver() {
    @Inject lateinit var appStartTasks: AppStartTasks
    @Inject lateinit var hub: DataChangeHub
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> runAsync(scope) {
                appStartTasks.runBlockingHooks()
                hub.financialDataChanged()
            }
        }
    }
}
