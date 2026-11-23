package com.athaan.app

import com.athaan.app.data.AppSettings
import com.athaan.app.data.Muezzins
import com.athaan.app.prayer.PrayerSchedule
import com.athaan.app.schedule.AdhanScheduler
import com.athaan.app.schedule.AlarmKind
import org.junit.Assert.assertFalse
import com.athaan.app.data.Places
import com.athaan.app.prayer.AsrMadhab
import com.athaan.app.prayer.CalcMethod
import com.athaan.app.prayer.Prayer
import com.athaan.app.prayer.PrayerTimesCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CoreTest {
    private fun hm(millis: Long, tz: TimeZone): String {
        val c = Calendar.getInstance(tz).apply { timeInMillis = millis }
        return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    @Test fun cairoTimes() {
        val tz = TimeZone.getTimeZone("Africa/Cairo")
        val d = PrayerTimesCalc.compute(2026, 11, 16, 30.0444, 31.2357, CalcMethod.EGYPT, AsrMadhab.STANDARD, tz)
        assertEquals("04:51", hm(d[Prayer.FAJR], tz))
        assertEquals("06:21", hm(d[Prayer.SUNRISE], tz))
        assertEquals("11:41", hm(d[Prayer.DHUHR], tz))
        assertEquals("14:38", hm(d[Prayer.ASR], tz))
        assertEquals("16:58", hm(d[Prayer.MAGHRIB], tz))
        assertEquals("18:19", hm(d[Prayer.ISHA], tz))
    }

    @Test fun makkahUmmAlQura() {
        val tz = TimeZone.getTimeZone("Asia/Riyadh")
        val d = PrayerTimesCalc.compute(2026, 11, 16, 21.4225, 39.8262, CalcMethod.UMM_AL_QURA, AsrMadhab.STANDARD, tz)
        assertEquals("05:13", hm(d[Prayer.FAJR], tz))
        assertEquals("19:09", hm(d[Prayer.ISHA], tz)) // Maghrib + 90 min
    }

    @Test fun cityTimezoneIndependentOfPhone() {
        // A Makkah city chosen on a phone set to Cairo time must still give Makkah's instants.
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Cairo"))
        val tz = TimeZone.getTimeZone("Asia/Riyadh")
        val d = PrayerTimesCalc.compute(2026, 11, 16, 21.4225, 39.8262, CalcMethod.UMM_AL_QURA, AsrMadhab.STANDARD, tz)
        assertEquals("05:13", hm(d[Prayer.FAJR], tz))
    }

    @Test fun timesAreOrdered() {
        val tz = TimeZone.getTimeZone("Europe/London")
        val d = PrayerTimesCalc.compute(2026, 6, 21, 51.5074, -0.1278, CalcMethod.MWL, AsrMadhab.STANDARD, tz)
        val order = Prayer.values().map { d[it] }
        assertEquals(order.sorted(), order)
    }

    @Test fun catalogIsConsistent() {
        assertEquals(Muezzins.all.size, Muezzins.all.map { it.id }.toSet().size)
        Muezzins.all.forEach { assertTrue(it.id, it.hasRegular || it.hasFajr) }
        Muezzins.DEFAULTS.forEach { (p, id) -> assertTrue("$p default $id", Muezzins.byId(id).canCall(p)) }
        assertTrue(Muezzins.all.count { it.hasFajr } >= 10)
        // A muezzin without a Fajr recording is never used for Fajr.
        assertEquals("basit", Muezzins.forPrayer(Prayer.FAJR, "refaat").id)
        assertEquals("refaat", Muezzins.forPrayer(Prayer.ASR, "refaat").id)
        assertTrue(Muezzins.byId("basit").trackFor(Prayer.FAJR).url.contains("fajr", ignoreCase = true))
    }

    @Test fun arabicSearchNormalisation() {
        assertEquals(Places.normalize("الإسكندرية"), Places.normalize("الاسكندريه"))
        assertEquals("sao paulo", Places.normalize("São Paulo"))
    }

    private val cairo = TimeZone.getTimeZone("Africa/Cairo")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int, tz: TimeZone = cairo): Long =
        Calendar.getInstance(tz).apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis
    private val cairoSettings = AppSettings(lat = 30.0444, lng = 31.2357, method = CalcMethod.EGYPT, madhab = AsrMadhab.STANDARD, timezoneId = "Africa/Cairo")

    @Test fun minuteOffsetsShiftTimes() {
        val now = at(2026, 11, 16, 12, 0)
        val s = cairoSettings.copy(offsets = Prayer.values().associateWith { 0 } + (Prayer.FAJR to 5) + (Prayer.ISHA to -3))
        val d = PrayerSchedule.day(s, 0, now)
        assertEquals("04:56", hm(d[Prayer.FAJR], cairo))
        assertEquals("11:41", hm(d[Prayer.DHUHR], cairo))
        assertEquals("18:16", hm(d[Prayer.ISHA], cairo))
    }

    @Test fun quietHours() {
        val s = cairoSettings.copy(quietEnabled = true, quietStart = 9 * 60, quietEnd = 17 * 60,
            quietDays = setOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY))
        assertTrue(s.inQuietHours(at(2026, 11, 16, 10, 0)))   // Monday morning
        assertFalse(s.inQuietHours(at(2026, 11, 16, 17, 0)))  // end is exclusive
        assertFalse(s.inQuietHours(at(2026, 11, 20, 10, 0)))  // Friday
        assertFalse(s.copy(quietEnabled = false).inQuietHours(at(2026, 11, 16, 10, 0)))
        // A night shift that runs past midnight belongs to the day it started.
        val night = s.copy(quietStart = 22 * 60, quietEnd = 6 * 60, quietDays = setOf(Calendar.MONDAY))
        assertTrue(night.inQuietHours(at(2026, 11, 16, 23, 0)))  // Monday night
        assertTrue(night.inQuietHours(at(2026, 11, 17, 3, 0)))   // early Tuesday
        assertFalse(night.inQuietHours(at(2026, 11, 18, 3, 0)))  // early Wednesday
    }

    @Test fun alarmPlan() {
        val now = at(2026, 11, 16, 12, 0) // Monday, after Dhuhr
        val s = cairoSettings.copy(preAlertMinutes = 10, iqama = Prayer.adhanPrayers.associateWith { 0 } + (Prayer.ASR to 15))
        val plan = AdhanScheduler.plan(s, now).associateBy { it.kind }
        val adhan = plan.getValue(AlarmKind.ADHAN)
        assertEquals(Prayer.ASR, adhan.prayer)
        assertEquals("14:38", hm(adhan.at, cairo))
        assertEquals("14:28", hm(plan.getValue(AlarmKind.PRE_ALERT).at, cairo))
        assertEquals("14:53", hm(plan.getValue(AlarmKind.IQAMA).at, cairo))
        // Friday reminder 30 minutes before Jumu'ah, Kahf at 9:30 on Friday.
        val friday = plan.getValue(AlarmKind.FRIDAY)
        assertTrue(PrayerSchedule.isFriday(friday.at, cairo))
        assertEquals(30 * 60_000L, friday.target - friday.at)
        val kahf = plan.getValue(AlarmKind.KAHF)
        assertTrue(PrayerSchedule.isFriday(kahf.at, cairo))
        assertEquals("09:30", hm(kahf.at, cairo))
        // The adhan switch for a prayer turns its alarm off.
        val noAsr = AdhanScheduler.plan(s.copy(enabled = s.enabled + (Prayer.ASR to false)), now).first { it.kind == AlarmKind.ADHAN }
        assertEquals(Prayer.MAGHRIB, noAsr.prayer)
    }

    @Test fun suhoorOnlyInRamadan() {
        assertTrue(AdhanScheduler.plan(cairoSettings, at(2026, 11, 16, 12, 0)).none { it.kind == AlarmKind.SUHOOR && !PrayerSchedule.isRamadan(it.at, cairo) })
    }
}
