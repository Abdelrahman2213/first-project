package com.athaan.app.prayer

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

enum class Prayer(val ar: String, val en: String, val hasAdhan: Boolean = true) {
    FAJR("الفجر", "Fajr"),
    SUNRISE("الشروق", "Sunrise", hasAdhan = false),
    DHUHR("الظهر", "Dhuhr"),
    ASR("العصر", "Asr"),
    MAGHRIB("المغرب", "Maghrib"),
    ISHA("العشاء", "Isha");

    companion object {
        val adhanPrayers = values().filter { it.hasAdhan }
    }
}

/** Calculation methods: Fajr angle, Isha angle (or fixed minutes after Maghrib), Dhuhr adjustment in minutes. */
enum class CalcMethod(
    val ar: String,
    val en: String,
    val fajrAngle: Double,
    val ishaAngle: Double,
    val ishaMinutes: Int = 0,
    val dhuhrAdjustMin: Int = 1,
) {
    EGYPT("الهيئة المصرية العامة للمساحة", "Egyptian General Authority of Survey", 19.5, 17.5),
    MWL("رابطة العالم الإسلامي", "Muslim World League", 18.0, 17.0),
    UMM_AL_QURA("أم القرى (مكة المكرمة)", "Umm al-Qura, Makkah", 18.5, 0.0, ishaMinutes = 90, dhuhrAdjustMin = 0),
    KARACHI("جامعة العلوم الإسلامية بكراتشي", "University of Islamic Sciences, Karachi", 18.0, 18.0),
    ISNA("الجمعية الإسلامية لأمريكا الشمالية", "Islamic Society of North America", 15.0, 15.0),
    DUBAI("دبي", "Dubai", 18.2, 18.2),
    KUWAIT("الكويت", "Kuwait", 18.0, 17.5),
    QATAR("قطر", "Qatar", 18.0, 0.0, ishaMinutes = 90),
}

/** Asr: Standard (Shafi'i, Maliki, Hanbali) shadow factor 1, Hanafi factor 2. */
enum class AsrMadhab(val ar: String, val en: String, val factor: Int) {
    STANDARD("الجمهور (شافعي، مالكي، حنبلي)", "Standard (Shafi'i, Maliki, Hanbali)", 1),
    HANAFI("الحنفي", "Hanafi", 2),
}

/** Prayer times for one day, as epoch millis. */
data class DayTimes(val dateKey: String, val times: Map<Prayer, Long>) {
    operator fun get(p: Prayer): Long = times.getValue(p)
}

/**
 * Astronomical prayer-time calculation (the PrayTimes.org algorithm, angle-based
 * high-latitude rule). Accurate to about a minute.
 */
object PrayerTimesCalc {

    private const val D2R = Math.PI / 180.0
    private fun dsin(d: Double) = sin(d * D2R)
    private fun dcos(d: Double) = cos(d * D2R)
    private fun dtan(d: Double) = tan(d * D2R)
    private fun dasin(x: Double) = asin(x) / D2R
    private fun dacos(x: Double) = acos(x) / D2R
    private fun datan2(y: Double, x: Double) = atan2(y, x) / D2R
    private fun dacot(x: Double) = atan(1.0 / x) / D2R
    private fun fix(a: Double, b: Double): Double {
        val r = a - b * floor(a / b)
        return if (r < 0) r + b else r
    }

    private fun julian(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    /** returns (declination, equation of time) */
    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fix(357.529 + 0.98560028 * d, 360.0)
        val q = fix(280.459 + 0.98564736 * d, 360.0)
        val l = fix(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g), 360.0)
        val e = 23.439 - 0.00000036 * d
        val ra = datan2(dcos(e) * dsin(l), dcos(l)) / 15.0
        val eqt = q / 15.0 - fix(ra, 24.0)
        val decl = dasin(dsin(e) * dsin(l))
        return decl to eqt
    }

    /**
     * Calculates times for the given calendar day (year, month 1-12, day) in the device time zone.
     */
    fun compute(
        year: Int, month: Int, day: Int,
        lat: Double, lng: Double,
        method: CalcMethod, madhab: AsrMadhab,
        tz: TimeZone = TimeZone.getDefault(),
    ): DayTimes {
        val jDate = julian(year, month, day) - lng / (15.0 * 24.0)

        fun midDay(t: Double): Double = fix(12 - sunPosition(jDate + t).second, 24.0)

        fun sunAngleTime(angle: Double, t: Double, ccw: Boolean): Double {
            val decl = sunPosition(jDate + t).first
            val noon = midDay(t)
            val x = (-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat))
            if (x < -1 || x > 1) return Double.NaN
            val tt = dacos(x) / 15.0
            return if (ccw) noon - tt else noon + tt
        }

        fun asrTime(factor: Int, t: Double): Double {
            val decl = sunPosition(jDate + t).first
            val angle = -dacot(factor + dtan(abs(lat - decl)))
            return sunAngleTime(angle, t, false)
        }

        var fajr = sunAngleTime(method.fajrAngle, 5 / 24.0, true)
        val sunrise = sunAngleTime(0.833, 6 / 24.0, true)
        val dhuhr = midDay(12 / 24.0)
        val asr = asrTime(madhab.factor, 13 / 24.0)
        val maghrib = sunAngleTime(0.833, 18 / 24.0, false)
        var isha = if (method.ishaMinutes > 0) maghrib + method.ishaMinutes / 60.0
        else sunAngleTime(method.ishaAngle, 18 / 24.0, false)

        // High latitudes: angle-based night portion.
        val night = 24 - (maghrib - sunrise)
        val fajrPortion = method.fajrAngle / 60.0 * night
        if (fajr.isNaN() || (sunrise - fajr) > fajrPortion) fajr = sunrise - fajrPortion
        if (method.ishaMinutes == 0) {
            val ishaPortion = method.ishaAngle / 60.0 * night
            if (isha.isNaN() || (isha - maghrib) > ishaPortion) isha = maghrib + ishaPortion
        }

        // Local midnight in the target zone, then add hours (computed in UTC, shifted by -lng/15).
        val cal = Calendar.getInstance(tz).apply {
            clear()
            set(year, month - 1, day, 0, 0, 0)
        }
        val midnightLocal = cal.timeInMillis
        val offsetHours = tz.getOffset(midnightLocal + 12 * 3_600_000L) / 3_600_000.0

        fun toMillis(hours: Double, adjustMin: Int = 0): Long {
            val h = hours + offsetHours - lng / 15.0
            // Round to the nearest minute.
            val minutes = Math.round(h * 60.0) + adjustMin
            return midnightLocal + minutes * 60_000L
        }

        val key = "%04d-%02d-%02d".format(year, month, day)
        return DayTimes(
            key,
            mapOf(
                Prayer.FAJR to toMillis(fajr),
                Prayer.SUNRISE to toMillis(sunrise),
                Prayer.DHUHR to toMillis(dhuhr, method.dhuhrAdjustMin),
                Prayer.ASR to toMillis(asr),
                Prayer.MAGHRIB to toMillis(maghrib),
                Prayer.ISHA to toMillis(isha),
            )
        )
    }

    fun forDayOffset(
        offsetDays: Int, lat: Double, lng: Double,
        method: CalcMethod, madhab: AsrMadhab,
        now: Long = System.currentTimeMillis(),
    ): DayTimes {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, offsetDays)
        }
        return compute(
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
            lat, lng, method, madhab, c.timeZone
        )
    }
}
