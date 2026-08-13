package com.ratib.saada

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Receives the alarm-clock fire and posts a full-screen, high-priority alarm
 * notification that opens the ringing alarm screen (even over the lock screen),
 * then arms the next reminder.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ReminderScheduler.ACTION_STOP) {
            NotificationManagerCompat.from(context).cancel(NOTIF_ID)
            return
        }

        val label = intent.getStringExtra(ReminderScheduler.EXTRA_LABEL)
            ?: context.getString(R.string.app_name)

        ensureChannel(context)
        postAlarm(context, label)

        // Arm the following reminder.
        ReminderScheduler.rescheduleNext(context)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val alarmUri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val ch = NotificationChannel(CHANNEL, context.getString(R.string.reminders_channel), NotificationManager.IMPORTANCE_HIGH)
        ch.setSound(
            alarmUri,
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        ch.enableVibration(true)
        nm.createNotificationChannel(ch)
    }

    private fun postAlarm(context: Context, label: String) {
        val fullScreen = PendingIntent.getActivity(
            context, 1,
            Intent(context, AlarmActivity::class.java)
                .putExtra(ReminderScheduler.EXTRA_LABEL, label)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getBroadcast(
            context, 2,
            Intent(context, AlarmReceiver::class.java).setAction(ReminderScheduler.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(label)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(0, context.getString(R.string.stop), stop)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, n)
        } catch (_: SecurityException) {
        }
    }

    companion object {
        const val CHANNEL = "ratib_reminders"
        const val NOTIF_ID = 7701
    }
}
