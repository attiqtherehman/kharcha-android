package com.attiqtherehman.kharcha.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import com.attiqtherehman.kharcha.AppStartHook
import com.attiqtherehman.kharcha.core.common.DataChangeHub
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.core.money.CurrencyInfo
import com.attiqtherehman.kharcha.data.prefs.SettingsDataStore
import com.attiqtherehman.kharcha.di.ApplicationScope
import com.attiqtherehman.kharcha.domain.model.Reminder
import com.attiqtherehman.kharcha.domain.repository.DayReviewRepository
import com.attiqtherehman.kharcha.domain.repository.ReminderRepository
import com.attiqtherehman.kharcha.domain.repository.TransactionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One "next wake" alarm for everything: the daily 22:30 reminder and every bill advance notice.
 * Every change path (app start, boot, time change, reminder edits, settings) converges on
 * [rescheduleAll], which cancels and re-sets the single PendingIntent, so duplicates are impossible.
 *
 * At fire time [evaluateAndReschedule] posts every notification whose instant falls after the
 * stored watermark and up to now, then stores the new watermark and arms the next wake. Late or
 * repeated wakes therefore never double-notify, and a phone that was off catches up.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminders: ReminderRepository,
    private val settings: SettingsDataStore,
    private val dayReviews: DayReviewRepository,
    private val transactions: TransactionRepository,
    private val notifier: ReminderNotifier,
    private val clock: Clock,
    @ApplicationScope scope: CoroutineScope,
    hub: DataChangeHub,
) : AppStartHook {

    data class Due(val reminder: Reminder?, val offsetDays: Int, val fireAt: Instant) {
        val isDaily: Boolean get() = reminder == null
    }

    private val mutex = Mutex()
    private val alarmManager get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val _nextWake = MutableStateFlow<Instant?>(null)
    /** For the settings screen: when the next notification is armed for. */
    val nextWake: StateFlow<Instant?> = _nextWake

    init {
        scope.launch { hub.reminderEvents.collect { runCatching { rescheduleAll() } } }
    }

    override suspend fun onAppStart() {
        notifier.ensureChannels()
        evaluateAndReschedule()
    }

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun isIgnoringBatteryOptimizations(): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)

    /** All future-or-recent notification instants, unsorted. */
    suspend fun computeDue(from: LocalDate): List<Due> {
        val zone = clock.zone
        val out = ArrayList<Due>()
        val prefs = settings.current()
        if (prefs.dailyReminderEnabled) {
            // Every day from `from` through tomorrow: catch-up evaluation looks a few days back
            // (so a late wake still finds today's instant) and rescheduling happens at every fire.
            val last = clock.today().plusDays(1)
            var date = from
            while (!date.isAfter(last)) {
                out += Due(null, 0, ZonedDateTime.of(date, prefs.dailyReminderTime, zone).toInstant())
                date = date.plusDays(1)
            }
        }
        for (r in reminders.getActive()) {
            for (offset in r.advanceDays.distinct()) {
                val date = r.dueDate.minusDays(offset.toLong())
                out += Due(r, offset, ZonedDateTime.of(date, r.reminderTime, zone).toInstant())
            }
        }
        return out
    }

    suspend fun rescheduleAll() = mutex.withLock { armNext(computeDue(clock.today())) }

    /** Called by the alarm receiver (and app start): post what is due, then arm the next wake. */
    suspend fun evaluateAndReschedule() = mutex.withLock {
        val now = clock.now()
        val today = clock.today()
        val prefs = settings.current()
        val watermark = Instant.ofEpochMilli(prefs.lastReminderEvaluationAt)
        val due = computeDue(today.minusDays(3))
        val window = due.filter { it.fireAt.isAfter(watermark) && !it.fireAt.isAfter(now) }
        val currency = CurrencyInfo.of(prefs.baseCurrency)

        // Daily reminder: only if it is still the same local day, nothing recorded and not reviewed.
        window.filter { it.isDaily }.maxByOrNull { it.fireAt }?.let { daily ->
            val fireDate = daily.fireAt.atZone(clock.zone).toLocalDate()
            if (fireDate == today && !dayReviews.isReviewed(today) && transactions.countOnDate(today) == 0) {
                notifier.postDailyReminder(today)
            }
        }
        // Bills: one notification per reminder, the most recent offset wins after a long sleep.
        window.filter { !it.isDaily }.groupBy { it.reminder!!.id }.values.forEach { group ->
            val latest = group.maxByOrNull { it.fireAt }!!
            notifier.postBillReminder(latest.reminder!!, latest.offsetDays, currency, today)
        }
        settings.setLastReminderEvaluationAt(now.toEpochMilli())
        armNext(due)
    }

    private fun armNext(due: List<Due>) {
        val now = clock.now()
        val next = due.map { it.fireAt }.filter { it.isAfter(now) }.minOrNull()
        val pi = wakeIntent()
        alarmManager.cancel(pi)
        _nextWake.value = next
        if (next == null) return
        val millis = next.toEpochMilli()
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    private fun wakeIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, RC_WAKE,
        Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_WAKE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val RC_WAKE = 1001
    }
}
