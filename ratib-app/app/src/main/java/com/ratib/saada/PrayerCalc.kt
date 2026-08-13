package com.ratib.saada

import java.util.Calendar
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan
import kotlin.math.PI

/**
 * Self-contained prayer-time calculation (standard astronomical method, the
 * same approach as PrayTimes.org). Returns local clock hours for a given date
 * and location — works fully offline.
 */
object PrayerCalc {

    /** All values are local clock hours in [0,24). */
    data class DayTimes(
        val fajr: Double,
        val sunrise: Double,
        val dhuhr: Double,
        val asr: Double,
        val maghrib: Double,
        val isha: Double
    )

    private data class M(val fajrAngle: Double, val ishaAngle: Double, val ishaMinutes: Int)

    private fun method(i: Int): M = when (i) {
        0 -> M(18.0, 17.0, 0)     // Muslim World League
        1 -> M(18.5, 0.0, 90)     // Umm al-Qura (Isha 90 min after Maghrib)
        2 -> M(19.5, 17.5, 0)     // Egyptian General Authority
        3 -> M(18.0, 18.0, 0)     // University of Islamic Sciences, Karachi
        else -> M(18.0, 17.0, 0)
    }

    private const val ASR_FACTOR = 1.0 // 1 = Shafi'i/Maliki/Hanbali, 2 = Hanafi

    private fun dtr(d: Double) = d * PI / 180.0
    private fun rtd(r: Double) = r * 180.0 / PI
    private fun sinD(d: Double) = sin(dtr(d))
    private fun cosD(d: Double) = cos(dtr(d))
    private fun tanD(d: Double) = tan(dtr(d))
    private fun arcsin(x: Double) = rtd(asin(x))
    private fun arccos(x: Double) = rtd(acos(x))
    private fun arccot(x: Double) = rtd(atan(1.0 / x))
    private fun fix(a: Double, b: Double): Double { var v = a % b; if (v < 0) v += b; return v }
    private fun fixHour(h: Double) = fix(h, 24.0)

    private fun julian(y: Int, m0: Int, d: Int): Double {
        var year = y
        var m = m0
        if (m <= 2) { year -= 1; m += 12 }
        val a = floor(year / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (year + 4716)) + floor(30.6001 * (m + 1)) + d + b - 1524.5
    }

    /** @return declination (deg) and equation of time (hours). */
    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fix(357.529 + 0.98560028 * d, 360.0)
        val q = fix(280.459 + 0.98564736 * d, 360.0)
        val l = fix(q + 1.915 * sinD(g) + 0.020 * sinD(2 * g), 360.0)
        val e = 23.439 - 0.00000036 * d
        val decl = arcsin(sinD(e) * sinD(l))
        val ra = fixHour(rtd(atan2(cosD(e) * sinD(l), cosD(l))) / 15.0)
        val eqt = q / 15.0 - ra
        return Pair(decl, eqt)
    }

    fun compute(cal: Calendar, lat: Double, lng: Double, methodIdx: Int): DayTimes {
        val y = cal.get(Calendar.YEAR)
        val mo = cal.get(Calendar.MONTH) + 1
        val da = cal.get(Calendar.DAY_OF_MONTH)
        val tz = cal.timeZone.getOffset(cal.timeInMillis) / 3600000.0
        val jDate = julian(y, mo, da) - lng / (15.0 * 24.0)
        val m = method(methodIdx)

        fun midDay(t: Double): Double {
            val eqt = sunPosition(jDate + t).second
            return fixHour(12.0 - eqt)
        }

        fun sunAngleTime(angle: Double, t: Double, ccw: Boolean): Double {
            val decl = sunPosition(jDate + t).first
            val noon = midDay(t)
            val x = (-sinD(angle) - sinD(decl) * sinD(lat)) / (cosD(decl) * cosD(lat))
            val hourAngle = arccos(x) / 15.0
            return noon + if (ccw) -hourAngle else hourAngle
        }

        fun asrTime(t: Double): Double {
            val decl = sunPosition(jDate + t).first
            val angle = -arccot(ASR_FACTOR + tanD(abs(lat - decl)))
            return sunAngleTime(angle, t, false)
        }

        // Initial guesses as day fractions, then refine.
        var fajr = 5.0 / 24
        var sunrise = 6.0 / 24
        var dhuhr = 12.0 / 24
        var asr = 13.0 / 24
        var sunset = 18.0 / 24
        var isha = 18.0 / 24

        repeat(3) {
            fajr = sunAngleTime(m.fajrAngle, fajr, true) / 24
            sunrise = sunAngleTime(0.833, sunrise, true) / 24
            dhuhr = midDay(dhuhr) / 24
            asr = asrTime(asr) / 24
            sunset = sunAngleTime(0.833, sunset, false) / 24
            if (m.ishaMinutes == 0) isha = sunAngleTime(m.ishaAngle, isha, false) / 24
        }

        val adj = tz - lng / 15.0
        val fajrH = fixHour(fajr * 24 + adj)
        val sunriseH = fixHour(sunrise * 24 + adj)
        val dhuhrH = fixHour(dhuhr * 24 + adj)
        val asrH = fixHour(asr * 24 + adj)
        val maghribH = fixHour(sunset * 24 + adj)
        val ishaH = if (m.ishaMinutes > 0) fixHour(maghribH + m.ishaMinutes / 60.0)
        else fixHour(isha * 24 + adj)

        return DayTimes(fajrH, sunriseH, dhuhrH, asrH, maghribH, ishaH)
    }
}
