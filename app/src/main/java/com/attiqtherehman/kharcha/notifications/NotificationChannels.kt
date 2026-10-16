package com.attiqtherehman.kharcha.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.attiqtherehman.kharcha.MainActivity
import com.attiqtherehman.kharcha.R
import com.attiqtherehman.kharcha.core.datetime.DateFormats
import com.attiqtherehman.kharcha.core.money.CurrencyInfo
import com.attiqtherehman.kharcha.core.money.MoneyFormatter
import com.attiqtherehman.kharcha.domain.model.Reminder
import com.attiqtherehman.kharcha.feature.quickentry.QuickEntryActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

object NotificationIds {
    const val CHANNEL_DAILY = "reminders_daily"
    const val CHANNEL_BILLS = "reminders_bills"
    const val DAILY_ID = 1
    const val BILL_ID = 2
    const val BUDGET_ID = 3
    fun billTag(reminderId: String, offsetDays: Int) = "bill:$reminderId:$offsetDays"
}

/** Creates channels (idempotent) and posts the app's notifications. */
@Singleton
class ReminderNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    fun ensureChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(NotificationIds.CHANNEL_DAILY, context.getString(R.string.notification_channel_daily), NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(NotificationIds.CHANNEL_BILLS, context.getString(R.string.notification_channel_bills), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    fun notificationsAllowed(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun postDailyReminder(date: LocalDate) {
        if (!notificationsAllowed()) return
        val openQuickEntry = PendingIntent.getActivity(
            context, 100, QuickEntryActivity.intent(context), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val markReviewed = PendingIntent.getBroadcast(
            context, 101,
            Intent(context, NotificationActionReceiver::class.java).setAction(NotificationActionReceiver.ACTION_MARK_REVIEWED).putExtra(NotificationActionReceiver.EXTRA_DATE, date.toString()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NotificationIds.CHANNEL_DAILY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.daily_reminder_title))
            .setContentText(context.getString(R.string.daily_reminder_text))
            .setContentIntent(openQuickEntry)
            .addAction(0, context.getString(R.string.daily_reminder_action_add), openQuickEntry)
            .addAction(0, context.getString(R.string.daily_reminder_action_reviewed), markReviewed)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        post(null, NotificationIds.DAILY_ID, notification)
    }

    fun cancelDailyReminder() = NotificationManagerCompat.from(context).cancel(NotificationIds.DAILY_ID)

    fun postBillReminder(reminder: Reminder, offsetDays: Int, currency: CurrencyInfo, today: LocalDate) {
        if (!notificationsAllowed()) return
        val open = PendingIntent.getActivity(
            context, reminder.id.hashCode(),
            Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra(MainActivity.EXTRA_OPEN_REMINDER, reminder.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val markPaid = PendingIntent.getActivity(
            context, reminder.id.hashCode() + 1,
            Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra(MainActivity.EXTRA_OPEN_REMINDER, reminder.id).putExtra(MainActivity.EXTRA_MARK_PAID, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dueLabel = when {
            reminder.dueDate == today -> context.getString(R.string.today).lowercase()
            reminder.dueDate == today.plusDays(1) -> context.getString(R.string.tomorrow).lowercase()
            else -> DateFormats.medium(reminder.dueDate)
        }
        val builder = NotificationCompat.Builder(context, NotificationIds.CHANNEL_BILLS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.bill_notification_title, reminder.title, dueLabel))
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.bill_notification_action_paid), markPaid)
            .addAction(0, context.getString(R.string.bill_notification_action_open), open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        reminder.amount?.let { builder.setContentText(context.getString(R.string.bill_notification_text, MoneyFormatter.format(it, currency))) }
        post(NotificationIds.billTag(reminder.id, offsetDays), NotificationIds.BILL_ID, builder.build())
    }

    fun cancelBillReminders(reminderId: String, offsets: List<Int>) {
        val manager = NotificationManagerCompat.from(context)
        offsets.forEach { manager.cancel(NotificationIds.billTag(reminderId, it), NotificationIds.BILL_ID) }
    }

    fun postBudgetWarning(title: String, text: String) {
        if (!notificationsAllowed()) return
        val open = PendingIntent.getActivity(context, 102, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, NotificationIds.CHANNEL_BILLS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        post("budget:$title", NotificationIds.BUDGET_ID, notification)
    }

    /**
     * Every caller is already gated by [notificationsAllowed], but lint only trusts a permission
     * check that sits in the same method as the `notify` call, so it is repeated here.
     */
    private fun post(tag: String?, id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(tag, id, notification)
    }
}
