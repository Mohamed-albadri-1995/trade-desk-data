package com.dalail.rahamat

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

/**
 * The daily call to the ward.
 *
 * The book is read a حزب a day, of an evening, and the Islamic day turns at
 * sunset — so the call that comes on a Sunday evening names «ورد ليلة
 * الاثنين» and opens Monday's حزب, not Sunday's. See Book.wardDay.
 *
 * It is set with setAlarmClock rather than a window, which is the one exact
 * alarm the system grants without asking the reader for a permission and which
 * Doze does not hold back, and it is posted on a channel of high importance so
 * it arrives with its sound instead of waiting in the shade. One alarm, armed
 * again each time it fires and after the phone restarts.
 */
object Reminder {

    const val PREFS = "dalail_prefs"
    const val ON = "remind_on"
    const val HOUR = "remind_hour"
    const val MINUTE = "remind_minute"
    // The ward is read of an evening, after the day has turned.
    const val DEFAULT_HOUR = 19
    const val DEFAULT_MINUTE = 0

    private const val CHANNEL = "ward"
    private const val NOTE_ID = 1
    private const val REQUEST = 100

    fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isOn(context: Context) = prefs(context).getBoolean(ON, true)

    fun hour(context: Context) = prefs(context).getInt(HOUR, DEFAULT_HOUR)

    fun minute(context: Context) = prefs(context).getInt(MINUTE, DEFAULT_MINUTE)

    fun set(context: Context, on: Boolean, hour: Int, minute: Int) {
        prefs(context).edit()
            .putBoolean(ON, on).putInt(HOUR, hour).putInt(MINUTE, minute).apply()
        schedule(context)
    }

    /** Where the system sends the reader when it shows the standing alarm. */
    private fun showBook(context: Context): PendingIntent = PendingIntent.getActivity(
        context, REQUEST + 1, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE else 0)
    )

    /** Arms the next call, or takes it down if the reminder is off. */
    fun schedule(context: Context) {
        val alarms = ContextCompat.getSystemService(context, AlarmManager::class.java)
            ?: return
        val pending = PendingIntent.getBroadcast(
            context, REQUEST, Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    PendingIntent.FLAG_IMMUTABLE else 0)
        )
        if (!isOn(context)) {
            alarms.cancel(pending)
            return
        }
        val at = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour(context))
            set(Calendar.MINUTE, minute(context))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        // setAlarmClock, rather than a window: the ward has an hour and the
        // call should come at it. This is the one exact alarm the system grants
        // without asking the reader for a permission, and Doze does not hold it
        // back — which is what «a reminder at the level of the system» means.
        alarms.setAlarmClock(
            AlarmManager.AlarmClockInfo(at.timeInMillis, showBook(context)), pending
        )
    }

    /** Shows the call, and arms tomorrow's. */
    fun fire(context: Context) {
        Book.load(context)
        val hizb = Book.hizbOfWard()
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL, context.getString(R.string.channel_ward),
                    // High, so the call arrives with its sound and shows itself
                    // over whatever is on the screen rather than waiting in the
                    // shade to be found.
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.channel_ward_about)
                    enableVibration(true)
                }
            )
        }
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_PAGE, (hizb?.page ?: 1) - 1)
        }
        val tap = PendingIntent.getActivity(
            context, REQUEST, open,
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val note: Notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(Book.wardTitle(context))
            .setContentText(hizb?.label ?: context.getString(R.string.app_name))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        runCatching { manager?.notify(NOTE_ID, note) }
        schedule(context)
    }
}

/** Wakes on the daily alarm. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) = Reminder.fire(context)
}

/** Alarms do not survive a restart, so they are armed again after one. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) Reminder.schedule(context)
    }
}
