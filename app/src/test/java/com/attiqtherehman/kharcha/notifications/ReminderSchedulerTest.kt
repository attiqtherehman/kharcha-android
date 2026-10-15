package com.attiqtherehman.kharcha.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import com.attiqtherehman.kharcha.TestData
import com.attiqtherehman.kharcha.TestGraph
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.domain.model.AccountType
import com.attiqtherehman.kharcha.domain.model.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
class ReminderSchedulerTest {

    private lateinit var g: TestGraph
    private lateinit var scheduler: ReminderScheduler
    private lateinit var alarmManager: AlarmManager
    private lateinit var notificationManager: NotificationManager
    private val zone = ZoneId.of("Asia/Karachi")

    @Before
    fun setUp() = runTest {
        g = TestGraph(com.attiqtherehman.kharcha.FixedClock(ZonedDateTime.of(2026, 10, 15, 10, 0, 0, 0, zone).toInstant(), zone))
        g.accounts.create(TestData.account(id = "bank", type = AccountType.BANK, opening = 0))
        shadowOf(g.context as android.app.Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        alarmManager = g.context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        notificationManager = g.context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifier = ReminderNotifier(g.context).also { it.ensureChannels() }
        scheduler = ReminderScheduler(g.context, g.reminders, g.settings, g.dayReviews, g.transactions, notifier, g.clock, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), g.hub)
    }

    @After fun tearDown() = g.close()

    private fun at(date: LocalDate, time: LocalTime): Instant = ZonedDateTime.of(date, time, zone).toInstant()

    @Test
    fun rescheduleAll_armsExactlyOneAlarm_forTheEarliestInstant() = runTest {
        g.reminders.create(Reminder(id = "a", title = "Gas", dueDate = LocalDate.of(2026, 10, 20), reminderTime = LocalTime.of(8, 0), advanceDays = listOf(3, 0), createdAt = 0, updatedAt = 0))
        g.reminders.create(Reminder(id = "b", title = "Net", dueDate = LocalDate.of(2026, 10, 16), reminderTime = LocalTime.of(7, 30), advanceDays = listOf(0), createdAt = 0, updatedAt = 0))
        scheduler.rescheduleAll()
        scheduler.rescheduleAll() // idempotent: cancel + set
        val shadow = shadowOf(alarmManager)
        assertEquals(1, shadow.scheduledAlarms.size)
        // Earliest is the daily reminder today at 22:30? No: "b" at 16th 07:30 is later than today 22:30 (15th). Daily wins.
        assertEquals(at(LocalDate.of(2026, 10, 15), LocalTime.of(22, 30)).toEpochMilli(), shadow.scheduledAlarms.first().triggerAtTime)
        assertEquals(scheduler.nextWake.value, Instant.ofEpochMilli(shadow.scheduledAlarms.first().triggerAtTime))
    }

    @Test
    fun evaluate_postsDailyReminder_onlyOnce_andSkipsWhenReviewedOrRecorded() = runTest {
        // Move to 22:31 today.
        g.clock.set(at(LocalDate.of(2026, 10, 15), LocalTime.of(22, 31)))
        scheduler.evaluateAndReschedule()
        assertEquals(1, shadowOf(notificationManager).allNotifications.size)
        // Fired again a minute later: watermark prevents a duplicate.
        notificationManager.cancelAll()
        g.clock.advance(Duration.ofMinutes(1))
        scheduler.evaluateAndReschedule()
        assertEquals(0, shadowOf(notificationManager).allNotifications.size)
        // Next day, already reviewed: no reminder.
        g.dayReviews.markReviewed(LocalDate.of(2026, 10, 16))
        g.clock.set(at(LocalDate.of(2026, 10, 16), LocalTime.of(22, 31)))
        scheduler.evaluateAndReschedule()
        assertEquals(0, shadowOf(notificationManager).allNotifications.size)
        // Day after, an expense was recorded today: no reminder.
        g.transactions.insertAll(listOf(TestData.expense(100, "bank", date = LocalDate.of(2026, 10, 17))))
        g.clock.set(at(LocalDate.of(2026, 10, 17), LocalTime.of(22, 31)))
        scheduler.evaluateAndReschedule()
        assertEquals(0, shadowOf(notificationManager).allNotifications.size)
        // Day after that, nothing recorded: reminder returns.
        g.clock.set(at(LocalDate.of(2026, 10, 18), LocalTime.of(22, 31)))
        scheduler.evaluateAndReschedule()
        assertEquals(1, shadowOf(notificationManager).allNotifications.size)
    }

    @Test
    fun evaluate_postsBillAdvanceNotices_andCatchesUpWithoutDuplicates() = runTest {
        g.reminders.create(Reminder(id = "bill", title = "Electricity", amount = Money(4_500_00), dueDate = LocalDate.of(2026, 10, 20), reminderTime = LocalTime.of(9, 0), advanceDays = listOf(3, 1, 0), createdAt = 0, updatedAt = 0))
        scheduler.rescheduleAll()
        // 3 days before at 09:00.
        g.clock.set(at(LocalDate.of(2026, 10, 17), LocalTime.of(9, 1)))
        scheduler.evaluateAndReschedule()
        val shadow = shadowOf(notificationManager)
        assertEquals(1, shadow.allNotifications.size)
        assertNotNull(shadow.getNotification(NotificationIds.billTag("bill", 3), NotificationIds.BILL_ID))
        notificationManager.cancelAll()
        // Phone was off for days; one wake on the due day posts only the latest offset.
        g.clock.set(at(LocalDate.of(2026, 10, 20), LocalTime.of(12, 0)))
        scheduler.evaluateAndReschedule()
        val bills = shadow.allNotifications.size
        assertEquals(1, bills)
        assertNotNull(shadow.getNotification(NotificationIds.billTag("bill", 0), NotificationIds.BILL_ID))
        assertNull(shadow.getNotification(NotificationIds.billTag("bill", 1), NotificationIds.BILL_ID))
    }

    @Test
    fun changingDailyTime_reschedules() = runTest {
        scheduler.rescheduleAll()
        val before = shadowOf(alarmManager).scheduledAlarms.first().triggerAtTime
        g.settings.setDailyReminderTime(LocalTime.of(21, 0))
        scheduler.rescheduleAll()
        val after = shadowOf(alarmManager).scheduledAlarms.first().triggerAtTime
        assertEquals(at(LocalDate.of(2026, 10, 15), LocalTime.of(21, 0)).toEpochMilli(), after)
        assertEquals(true, after < before)
        g.settings.setDailyReminderEnabled(false)
        scheduler.rescheduleAll()
        assertEquals(0, shadowOf(alarmManager).scheduledAlarms.size)
    }
}
