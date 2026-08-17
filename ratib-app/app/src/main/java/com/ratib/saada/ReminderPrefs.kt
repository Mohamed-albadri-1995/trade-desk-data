package com.ratib.saada

import android.content.Context
import java.util.TimeZone

/** Stores the reminder settings: which reminders are on, the location, and the method. */
object ReminderPrefs {
    private const val P = "reminder_prefs"
    private fun sp(c: Context) = c.getSharedPreferences(P, Context.MODE_PRIVATE)

    fun master(c: Context) = sp(c).getBoolean("master", true)

    /** The adhan itself: a full alarm that rings until stopped. */
    fun prayer(c: Context) = sp(c).getBoolean("prayer", true)

    fun asas(c: Context) = sp(c).getBoolean("asas", true)
    fun morning(c: Context) = sp(c).getBoolean("morning", true)
    fun evening(c: Context) = sp(c).getBoolean("evening", true)
    fun suhur(c: Context) = sp(c).getBoolean("suhur", true)

    fun setFlag(c: Context, key: String, value: Boolean) =
        sp(c).edit().putBoolean(key, value).apply()

    fun lat(c: Context) = sp(c).getString("lat", null)?.toDoubleOrNull()
    fun lng(c: Context) = sp(c).getString("lng", null)?.toDoubleOrNull()
    fun setLocation(c: Context, lat: Double, lng: Double) =
        sp(c).edit().putString("lat", lat.toString()).putString("lng", lng.toString()).apply()

    fun method(c: Context) = sp(c).getInt("method", 1)
    fun setMethod(c: Context, m: Int) = sp(c).edit().putInt("method", m).apply()

    fun hasLocation(c: Context) = lat(c) != null && lng(c) != null

    /** True while the stored location is the timezone estimate, not a real fix. */
    fun isLocationApproximate(c: Context) = sp(c).getBoolean("approx", false)

    /**
     * Gives the app a workable location the moment it is installed, so the
     * reminders are armed without anyone opening the settings and pressing save.
     *
     * Prayer times need coordinates, and waiting for a GPS fix means no alarms
     * until one arrives — indoors that can be never. The device's UTC offset
     * fixes longitude to within its timezone (each hour is 15° of it), which is
     * what the times mostly turn on; latitude is taken as the tariqa's own
     * region. The estimate is replaced the moment a real fix is captured.
     */
    fun seedLocationIfMissing(c: Context) {
        if (hasLocation(c)) return
        val offsetHours = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3600000.0
        val lng = (offsetHours * 15.0).coerceIn(-180.0, 180.0)
        setLocation(c, DEFAULT_LATITUDE, lng)
        sp(c).edit().putBoolean("approx", true).apply()
    }

    /** Stores a real fix and clears the approximate flag. */
    fun setExactLocation(c: Context, lat: Double, lng: Double) {
        setLocation(c, lat, lng)
        sp(c).edit().putBoolean("approx", false).apply()
    }

    /** Khartoum, the home of the السجادة السليمانية. */
    private const val DEFAULT_LATITUDE = 15.5
}
