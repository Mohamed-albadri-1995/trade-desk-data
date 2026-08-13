package com.ratib.saada

import android.content.Context

/** Stores the reminder settings: which reminders are on, the location, and the method. */
object ReminderPrefs {
    private const val P = "reminder_prefs"
    private fun sp(c: Context) = c.getSharedPreferences(P, Context.MODE_PRIVATE)

    fun master(c: Context) = sp(c).getBoolean("master", true)
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
}
