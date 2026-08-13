package com.ratib.saada

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Works out every reminder time for today and tomorrow from the prayer times,
 * then arms the single nearest one as a real alarm-clock alarm (which wakes the
 * device and needs no exact-alarm permission). Each time it fires, the next one
 * is armed again.
 */
object ReminderScheduler {

    const val ACTION_FIRE = "com.ratib.saada.ALARM_FIRE"
    const val ACTION_STOP = "com.ratib.saada.ALARM_STOP"
    const val EXTRA_LABEL = "label"
    private const val REQ_ALARM = 4201
    private const val REQ_TEST = 4202

    data class Fire(val timeMillis: Long, val label: String)

    fun rescheduleNext(context: Context) {
        // Never let a scheduling problem crash the app.
        try {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            val firePi = firePendingIntent(context, null)

            if (!ReminderPrefs.master(context) || !ReminderPrefs.hasLocation(context)) {
                am.cancel(firePi)
                return
            }

            val now = System.currentTimeMillis()
            val next = buildFires(context)
                .filter { it.timeMillis > now + 1000 }
                .minByOrNull { it.timeMillis } ?: return

            val show = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val op = firePendingIntent(context, next.label)
            try {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(next.timeMillis, show), op)
            } catch (_: Throwable) {
                try {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeMillis, op)
                } catch (_: Throwable) {
                    am.set(AlarmManager.RTC_WAKEUP, next.timeMillis, op)
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("ReminderScheduler", "rescheduleNext failed", t)
        }
    }

    /** The next reminder that will fire, or null if none is armed. */
    fun nextFire(context: Context): Fire? {
        if (!ReminderPrefs.master(context) || !ReminderPrefs.hasLocation(context)) return null
        val now = System.currentTimeMillis()
        return buildFires(context).filter { it.timeMillis > now + 1000 }.minByOrNull { it.timeMillis }
    }

    /** Fires a real alarm a few seconds from now so the user can test the sound. */
    fun scheduleTest(context: Context, seconds: Int = 5) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val at = System.currentTimeMillis() + seconds * 1000L
        val op = PendingIntent.getBroadcast(
            context, REQ_TEST,
            Intent(context, AlarmReceiver::class.java)
                .setAction(ACTION_FIRE)
                .putExtra(EXTRA_LABEL, context.getString(R.string.test_alarm_label)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val show = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), op)
        } catch (_: Throwable) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
            } catch (_: Throwable) {
                am.set(AlarmManager.RTC_WAKEUP, at, op)
            }
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(firePendingIntent(context, null))
    }

    private fun firePendingIntent(context: Context, label: String?): PendingIntent {
        val i = Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE)
        if (label != null) i.putExtra(EXTRA_LABEL, label)
        return PendingIntent.getBroadcast(
            context, REQ_ALARM, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun midnight(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun buildFires(context: Context): List<Fire> {
        val lat = ReminderPrefs.lat(context) ?: return emptyList()
        val lng = ReminderPrefs.lng(context) ?: return emptyList()
        val method = ReminderPrefs.method(context)
        val raw = ArrayList<Fire>()

        for (dayOffset in 0..1) {
            val cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_MONTH, dayOffset)
            val base = midnight(cal)
            val t = PrayerCalc.compute(cal, lat, lng, method)
            fun at(h: Double) = base + (h * 3600000L).toLong()

            if (ReminderPrefs.asas(context)) {
                raw.add(Fire(at(t.fajr), "الأساس — بعد الفجر"))
                raw.add(Fire(at(t.dhuhr), "الأساس — بعد الظهر"))
                raw.add(Fire(at(t.asr), "الأساس — بعد العصر"))
                raw.add(Fire(at(t.maghrib), "الأساس — بعد المغرب"))
                raw.add(Fire(at(t.isha), "الأساس — بعد العشاء"))
            }
            if (ReminderPrefs.morning(context)) raw.add(Fire(at(t.fajr), "أوراد الصباح"))
            if (ReminderPrefs.evening(context)) raw.add(Fire(at(t.maghrib), "أوراد المساء"))
            if (ReminderPrefs.suhur(context)) {
                val next = Calendar.getInstance(); next.add(Calendar.DAY_OF_MONTH, dayOffset + 1)
                val t2 = PrayerCalc.compute(next, lat, lng, method)
                val lastThirdH = t.maghrib + (t2.fajr + 24.0 - t.maghrib) * 2.0 / 3.0
                raw.add(Fire(base + (lastThirdH * 3600000L).toLong(), "أوراد السحر"))
            }
        }

        // Merge reminders that fall in the same minute into one alarm.
        return raw.groupBy { it.timeMillis / 60000L }
            .map { (_, group) ->
                Fire(group.minOf { it.timeMillis }, group.joinToString("  •  ") { it.label })
            }
            .sortedBy { it.timeMillis }
    }
}
