package com.athaan.app.prayer

import android.icu.util.IslamicCalendar
import android.icu.util.ULocale
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

object HijriDate {
    private val monthsAr = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
    )
    private val monthsEn = listOf(
        "Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Akhir", "Jumada al-Ula", "Jumada al-Akhirah",
        "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qadah", "Dhu al-Hijjah"
    )
    private val daysAr = listOf("الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")
    private val daysEn = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    private val gMonthsAr = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
    private val gMonthsEn = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

    /** "الإثنين · 6 جمادى الآخرة 1448 هـ" */
    fun hijriLine(millis: Long, ar: Boolean, tz: TimeZone = TimeZone.getDefault()): String {
        val g = Calendar.getInstance(tz).apply { timeInMillis = millis }
        val dayName = (if (ar) daysAr else daysEn)[g.get(Calendar.DAY_OF_WEEK) - 1]
        return try {
            val ic = IslamicCalendar(android.icu.util.TimeZone.getTimeZone(tz.id), ULocale("ar"))
            ic.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
            ic.time = Date(millis)
            val d = ic.get(IslamicCalendar.DAY_OF_MONTH)
            val m = ic.get(IslamicCalendar.MONTH)
            val y = ic.get(IslamicCalendar.YEAR)
            if (ar) "$dayName · $d ${monthsAr[m]} $y هـ" else "$dayName · $d ${monthsEn[m]} $y AH"
        } catch (e: Exception) {
            dayName
        }
    }

    /** "16 نوفمبر" */
    fun gregorianShort(millis: Long, ar: Boolean, tz: TimeZone = TimeZone.getDefault()): String {
        val g = Calendar.getInstance(tz).apply { timeInMillis = millis }
        val m = (if (ar) gMonthsAr else gMonthsEn)[g.get(Calendar.MONTH)]
        return "${g.get(Calendar.DAY_OF_MONTH)} $m"
    }
}
