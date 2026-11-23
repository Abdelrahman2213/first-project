package com.athaan.app.prayer

import android.icu.util.IslamicCalendar
import android.icu.util.ULocale
import com.athaan.app.data.AppSettings
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/** Prayer times as the app uses them: calculated, then shifted by the user's per-prayer minutes. */
object PrayerSchedule {
    fun day(s: AppSettings, dayOffset: Int, now: Long = System.currentTimeMillis()): DayTimes {
        val raw = PrayerTimesCalc.forDayOffset(dayOffset, s.lat, s.lng, s.method, s.madhab, now, s.zone())
        return DayTimes(raw.dateKey, raw.times.mapValues { (p, t) -> t + (s.offsets[p] ?: 0) * 60_000L })
    }

    data class Event(val prayer: Prayer, val at: Long)

    /** Every adhan prayer time from yesterday to two days ahead, in order. */
    fun around(s: AppSettings, now: Long): List<Event> =
        (-1..2).flatMap { d -> day(s, d, now).let { dt -> Prayer.adhanPrayers.map { Event(it, dt[it]) } } }
            .sortedBy { it.at }

    fun isFriday(millis: Long, tz: TimeZone): Boolean =
        Calendar.getInstance(tz).apply { timeInMillis = millis }.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY

    /** Hijri (Umm al-Qura) year, month 0-11 and day for a moment. */
    fun hijri(millis: Long, tz: TimeZone): Triple<Int, Int, Int>? = runCatching {
        val ic = IslamicCalendar(android.icu.util.TimeZone.getTimeZone(tz.id), ULocale("ar"))
        ic.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        ic.time = Date(millis)
        Triple(ic.get(IslamicCalendar.YEAR), ic.get(IslamicCalendar.MONTH), ic.get(IslamicCalendar.DAY_OF_MONTH))
    }.getOrNull()

    fun isRamadan(millis: Long, tz: TimeZone): Boolean = hijri(millis, tz)?.second == 8
}
