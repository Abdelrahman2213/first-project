package com.athaan.app

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.athaan.app.data.AppSettings
import com.athaan.app.prayer.Prayer
import com.athaan.app.prayer.PrayerSchedule
import com.athaan.app.prayer.TimeFmt

/**
 * The custom notification views: a compact line for the collapsed state and, when expanded,
 * the app's ink-and-gold prayer card with a live countdown and the day's five times.
 */
object NotificationCards {
    private const val GOLD = 0xFFD6B370.toInt()
    private const val MUTED = 0xFF9AA3B2.toInt()
    private const val TEXT = 0xFFF3EFE6.toInt()

    data class Card(
        /** Small gold line above the title. */
        val label: String,
        /** Large Kufi title: a prayer name or the reminder's subject. */
        val title: String,
        /** Gold time beside the title. */
        val time: String? = null,
        /** Secondary line. */
        val text: String? = null,
        /** Live countdown to this moment. */
        val countdownTo: Long? = null,
        val countLabel: String? = null,
        /** A short text shown in Amiri (hadith, dhikr). */
        val quote: String? = null,
        /** Show the five times of the day that contains this moment, highlighting [highlight]. */
        val dayOf: Long? = null,
        val highlight: Prayer? = null,
    )

    private val NAME_IDS = intArrayOf(R.id.n_name_0, R.id.n_name_1, R.id.n_name_2, R.id.n_name_3, R.id.n_name_4)
    private val TIME_IDS = intArrayOf(R.id.n_ptime_0, R.id.n_ptime_1, R.id.n_ptime_2, R.id.n_ptime_3, R.id.n_ptime_4)
    private val CELL_IDS = intArrayOf(R.id.n_cell_0, R.id.n_cell_1, R.id.n_cell_2, R.id.n_cell_3, R.id.n_cell_4)

    private fun base(countdownTo: Long?): Long? {
        val now = System.currentTimeMillis()
        return countdownTo?.takeIf { it > now }?.let { SystemClock.elapsedRealtime() + (it - now) }
    }

    fun small(context: Context, c: Card, s: AppSettings): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.notif_small)
        rv.setInt(R.id.n_root, "setLayoutDirection", if (s.arabic) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR)
        rv.setTextViewText(R.id.n_s_title, c.title)
        rv.setTextViewText(R.id.n_s_time, c.time ?: "")
        rv.setViewVisibility(R.id.n_s_time, if (c.time.isNullOrEmpty()) View.GONE else View.VISIBLE)
        rv.setTextViewText(R.id.n_s_text, c.text ?: c.label)
        val b = base(c.countdownTo)
        if (b != null) {
            rv.setViewVisibility(R.id.n_s_chrono, View.VISIBLE)
            rv.setChronometer(R.id.n_s_chrono, b, null, true)
            rv.setChronometerCountDown(R.id.n_s_chrono, true)
        } else {
            rv.setViewVisibility(R.id.n_s_chrono, View.GONE)
        }
        return rv
    }

    fun big(context: Context, c: Card, s: AppSettings): RemoteViews {
        val ar = s.arabic
        val tz = s.zone()
        val rv = RemoteViews(context.packageName, R.layout.notif_big)
        rv.setInt(R.id.n_root, "setLayoutDirection", if (ar) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR)
        rv.setImageViewBitmap(R.id.n_ornament, NotificationArt.corner(context, ar))
        rv.setTextViewText(R.id.n_label, c.label)
        rv.setTextViewText(R.id.n_title, c.title)
        rv.setTextViewText(R.id.n_time, c.time ?: "")
        rv.setViewVisibility(R.id.n_time, if (c.time.isNullOrEmpty()) View.GONE else View.VISIBLE)

        val b = base(c.countdownTo)
        if (b != null) {
            rv.setViewVisibility(R.id.n_count_box, View.VISIBLE)
            rv.setTextViewText(R.id.n_count_label, c.countLabel ?: if (ar) "باقي" else "Left")
            rv.setChronometer(R.id.n_chrono, b, null, true)
            rv.setChronometerCountDown(R.id.n_chrono, true)
        } else {
            rv.setViewVisibility(R.id.n_count_box, View.GONE)
        }

        rv.setTextViewText(R.id.n_text, c.text ?: "")
        rv.setViewVisibility(R.id.n_text, if (c.text.isNullOrEmpty()) View.GONE else View.VISIBLE)
        rv.setTextViewText(R.id.n_quote, c.quote ?: "")
        rv.setViewVisibility(R.id.n_quote, if (c.quote.isNullOrEmpty()) View.GONE else View.VISIBLE)

        val at = c.dayOf
        if (at != null && s.hasLocation) {
            val day = PrayerSchedule.day(s, 0, at)
            rv.setViewVisibility(R.id.n_times, View.VISIBLE)
            Prayer.adhanPrayers.forEachIndexed { i, p ->
                val on = p == c.highlight
                val name = if (p == Prayer.DHUHR && PrayerSchedule.isFriday(day[p], tz)) (if (ar) "الجمعة" else "Jumu'ah") else (if (ar) p.ar else p.en)
                rv.setTextViewText(NAME_IDS[i], name)
                rv.setTextViewText(TIME_IDS[i], TimeFmt.clock(day[p], ar, tz).substringBefore(" "))
                rv.setTextColor(NAME_IDS[i], if (on) GOLD else MUTED)
                rv.setTextColor(TIME_IDS[i], if (on) GOLD else TEXT)
                rv.setInt(CELL_IDS[i], "setBackgroundResource", if (on) R.drawable.notif_cell_on else R.drawable.notif_cell_off)
            }
        } else {
            rv.setViewVisibility(R.id.n_times, View.GONE)
        }
        return rv
    }
}
