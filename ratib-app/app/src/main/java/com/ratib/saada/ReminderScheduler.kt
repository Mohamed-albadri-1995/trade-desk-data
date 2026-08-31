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
    const val EXTRA_KIND = "kind"

    /** Minutes after the adhan at which the الأساس ward is read. */
    const val WARD_DELAY_MINUTES = 20
    private const val REQ_ALARM = 4201
    private const val REQ_TEST = 4202

    /** How a reminder announces itself. */
    enum class Kind {
        /** A prayer time: calls the adhan, and keeps calling until it is stopped. */
        ADHAN,

        /** Meant to wake you — أوراد السحر. Rings like any alarm clock. */
        ALARM,

        /** A ward that follows a prayer you are already awake for: a short tone. */
        WARD;

        val isFullAlarm get() = this != WARD
    }

    data class Fire(val timeMillis: Long, val label: String, val kind: Kind)

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
            val op = firePendingIntent(context, next.label, next.kind)
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
                .putExtra(EXTRA_LABEL, context.getString(R.string.test_alarm_label))
                .putExtra(EXTRA_KIND, Kind.ADHAN.name),
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

    private fun firePendingIntent(
        context: Context,
        label: String?,
        kind: Kind = Kind.ADHAN
    ): PendingIntent {
        val i = Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE)
        if (label != null) i.putExtra(EXTRA_LABEL, label)
        i.putExtra(EXTRA_KIND, kind.name)
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

            val prayers = listOf(
                t.fajr to "الفجر", t.dhuhr to "الظهر", t.asr to "العصر",
                t.maghrib to "المغرب", t.isha to "العشاء"
            )
            // The adhan itself, called until it is stopped.
            if (ReminderPrefs.prayer(context)) {
                for ((h, name) in prayers) raw.add(Fire(at(h), "أذان $name", Kind.ADHAN))
            }
            // The ward is read after the prayer, not at the adhan, so it sounds
            // its own short tone twenty minutes later.
            if (ReminderPrefs.asas(context)) {
                val delay = WARD_DELAY_MINUTES * 60000L
                for ((h, name) in prayers) {
                    raw.add(Fire(at(h) + delay, "الأساس — بعد $name", Kind.WARD))
                }
            }
            if (ReminderPrefs.morning(context)) {
                raw.add(Fire(at(t.fajr) + WARD_DELAY_MINUTES * 60000L, "أوراد الصباح", Kind.WARD))
            }
            if (ReminderPrefs.evening(context)) {
                raw.add(Fire(at(t.maghrib) + WARD_DELAY_MINUTES * 60000L, "أوراد المساء", Kind.WARD))
            }
            if (ReminderPrefs.suhur(context)) {
                val next = Calendar.getInstance(); next.add(Calendar.DAY_OF_MONTH, dayOffset + 1)
                val t2 = PrayerCalc.compute(next, lat, lng, method)
                // The night runs from مغرب to the next فجر. Its last third begins
                // two thirds of the way through, and the middle of that third —
                // which is when the ward is wanted — is five sixths through.
                val nightHours = t2.fajr + 24.0 - t.maghrib
                val lastThirdH = t.maghrib + nightHours * 5.0 / 6.0
                // Its whole purpose is to wake you, so it rings like an alarm
                // clock — not the adhan, which announces a time you are up for.
                raw.add(Fire(base + (lastThirdH * 3600000L).toLong(), "أوراد السحر", Kind.ALARM))
            }
        }

        // Merge reminders that fall in the same minute into one alarm.
        return raw.groupBy { (it.timeMillis / 60000L) to it.kind }
            .map { (key, group) ->
                Fire(
                    group.minOf { it.timeMillis },
                    group.joinToString("  •  ") { it.label },
                    key.second
                )
            }
            .sortedBy { it.timeMillis }
    }
}
