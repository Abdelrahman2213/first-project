package com.athaan.app

import com.athaan.app.data.Muezzins
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
}
