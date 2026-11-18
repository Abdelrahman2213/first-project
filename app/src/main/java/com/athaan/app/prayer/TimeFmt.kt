package com.athaan.app.prayer

import java.util.Calendar
import java.util.TimeZone

object TimeFmt {
    /** "6:19 م" / "6:19 PM" */
    fun clock(millis: Long, ar: Boolean, tz: TimeZone = TimeZone.getDefault()): String {
        val c = Calendar.getInstance(tz).apply { timeInMillis = millis }
        val h24 = c.get(Calendar.HOUR_OF_DAY)
        val m = c.get(Calendar.MINUTE)
        val h12 = if (h24 % 12 == 0) 12 else h24 % 12
        val suffix = if (h24 < 12) (if (ar) "ص" else "AM") else (if (ar) "م" else "PM")
        return "%d:%02d %s".format(h12, m, suffix)
    }

    /** "00:39:12" */
    fun countdown(ms: Long): String {
        val total = (ms.coerceAtLeast(0) + 999) / 1000
        return "%02d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
    }

    /** "3:42" */
    fun mmss(ms: Int): String {
        val s = (ms.coerceAtLeast(0)) / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }
}
