package com.rachitratnammudaliar.duedial.reminders

import android.Manifest
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
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rachitratnammudaliar.duedial.MainActivity
import com.rachitratnammudaliar.duedial.R
import com.rachitratnammudaliar.duedial.data.AppDatabase
import com.rachitratnammudaliar.duedial.data.Subscription
import com.rachitratnammudaliar.duedial.data.shareMinor
import com.rachitratnammudaliar.duedial.data.toPlan
import com.rachitratnammudaliar.duedial.domain.EventKind
import com.rachitratnammudaliar.duedial.domain.Money
import com.rachitratnammudaliar.duedial.domain.PlanEvent
import com.rachitratnammudaliar.duedial.domain.Plans
import com.rachitratnammudaliar.duedial.domain.Renewals
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Renewal reminders. Everything runs on the phone; no internet is used. */
object Reminders {

    private const val CHANNEL_ID = "renewals"
    private const val WORK_NAME = "duedial-daily-check"
    private const val TEST_ID = 999_999

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Renewal reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Reminders before a subscription renews" }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    /** Runs the daily check at about 9:00 every morning. */
    fun schedule(context: Context) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(LocalTime.of(9, 0))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delayMinutes = Duration.between(now, next).toMinutes().coerceAtLeast(1)

        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** The reminder to show today for [s], if any. */
    fun dueEvent(s: Subscription, today: LocalDate): PlanEvent? =
        Plans.reminderDue(s.toPlan(), s.remindDaysBefore, today)

    fun notify(context: Context, s: Subscription, event: PlanEvent, today: LocalDate) {
        val days = Renewals.daysUntil(today, event.date)
        val day = event.date.format(DateTimeFormatter.ofPattern("d MMM"))
        val price = Money.format(s.shareMinor(), s.currency)
        val title = if (s.nickname.isNotBlank()) "${s.name} (${s.nickname})" else s.name
        val text = when (event.kind) {
            EventKind.CHARGE ->
                if (days == 0L) "$price is due today" else "Renews ${Renewals.relativeLabel(days)}, $day · $price"
            EventKind.TRIAL_ENDS ->
                if (days == 0L) {
                    "Free trial ended today. You'll be charged $price unless you've cancelled."
                } else {
                    "Free trial ends $day. Cancel by ${event.date.minusDays(1).format(DateTimeFormatter.ofPattern("d MMM"))} to avoid paying $price."
                }
            EventKind.FREE_ENDS -> "Free period ends ${Renewals.relativeLabel(days)}, $day"
            EventKind.SUBSCRIPTION_ENDS -> "Ends ${Renewals.relativeLabel(days)}, $day"
        }
        show(context, s.id.toInt(), title, text)
    }

    /** Opens Duedial's notification settings. */
    fun notificationSettingsIntent(context: Context): Intent =
        Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens the app's system page, where battery can be set to "Unrestricted" (needed on Samsung). */
    fun appSettingsIntent(context: Context): Intent =
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(android.net.Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun showTest(context: Context) {
        show(context, TEST_ID, "Duedial reminders are on", "You'll get a notice like this before each renewal.")
    }

    private fun show(context: Context, id: Int, title: String, text: String) {
        if (!canNotify(context)) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission was revoked between the check and the call. Nothing to do.
        }
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val today = LocalDate.now()
        val all = AppDatabase.get(applicationContext).subscriptions().getAll()
        all.forEach { s ->
            Reminders.dueEvent(s, today)?.let { Reminders.notify(applicationContext, s, it, today) }
        }
        return Result.success()
    }
}
