package com.athaan.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.athaan.app.MainActivity
import com.athaan.app.R
import com.athaan.app.data.SettingsStore
import com.athaan.app.prayer.HijriDate
import com.athaan.app.prayer.Prayer
import com.athaan.app.prayer.PrayerSchedule
import com.athaan.app.prayer.TimeFmt
import com.athaan.app.schedule.AdhanScheduler

/** Home-screen widget: next prayer with a live countdown, and the day's five times. */
class PrayerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, build(context)) }
    }

    override fun onEnabled(context: Context) {
        AdhanScheduler.reschedule(context)
    }

    companion object {
        private val NAME_IDS = intArrayOf(R.id.w_name_0, R.id.w_name_1, R.id.w_name_2, R.id.w_name_3, R.id.w_name_4)
        private val TIME_IDS = intArrayOf(R.id.w_time_0, R.id.w_time_1, R.id.w_time_2, R.id.w_time_3, R.id.w_time_4)
        private const val GOLD = 0xFFD6B370.toInt()
        private const val TEXT = 0xFFF3EFE6.toInt()
        private const val MUTED = 0xFF9AA3B2.toInt()

        fun updateAll(context: Context) {
            runCatching {
                val m = AppWidgetManager.getInstance(context)
                val ids = m.getAppWidgetIds(ComponentName(context, PrayerWidget::class.java))
                if (ids.isNotEmpty()) {
                    val rv = build(context)
                    ids.forEach { m.updateAppWidget(it, rv) }
                }
            }
        }

        fun build(context: Context): RemoteViews {
            val s = SettingsStore.get(context).current
            val ar = s.arabic
            val tz = s.zone()
            val now = System.currentTimeMillis()
            val rv = RemoteViews(context.packageName, R.layout.widget_prayer)
            rv.setInt(R.id.w_root, "setLayoutDirection", if (ar) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR)

            val open = PendingIntent.getActivity(
                context, 77, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            rv.setOnClickPendingIntent(R.id.w_root, open)

            if (!s.hasLocation) {
                rv.setTextViewText(R.id.w_label, if (ar) "أذان" else "Athaan")
                rv.setTextViewText(R.id.w_next, if (ar) "افتح التطبيق لتحديد موقعك" else "Open the app to set your location")
                rv.setTextViewText(R.id.w_next_time, "")
                rv.setViewVisibility(R.id.w_countdown, View.GONE)
                rv.setViewVisibility(R.id.w_times, View.GONE)
                return rv
            }

            val next = PrayerSchedule.around(s, now).first { it.at > now }
            val friday = next.prayer == Prayer.DHUHR && PrayerSchedule.isFriday(next.at, tz)
            val name = if (friday) (if (ar) "الجمعة" else "Jumu'ah") else (if (ar) next.prayer.ar else next.prayer.en)
            rv.setTextViewText(R.id.w_label, (if (ar) "الصلاة القادمة · " else "Next · ") + s.place(ar).substringBefore(if (ar) "،" else ","))
            rv.setTextViewText(R.id.w_next, name)
            rv.setTextViewText(R.id.w_next_time, TimeFmt.clock(next.at, ar, tz))
            rv.setViewVisibility(R.id.w_countdown, View.VISIBLE)
            rv.setChronometer(R.id.w_countdown, SystemClock.elapsedRealtime() + (next.at - now), null, true)
            rv.setChronometerCountDown(R.id.w_countdown, true)
            rv.setTextViewText(R.id.w_date, HijriDate.hijriDate(now, ar, tz))

            // The five times of the day the next prayer belongs to.
            val day = PrayerSchedule.day(s, 0, next.at)
            rv.setViewVisibility(R.id.w_times, View.VISIBLE)
            Prayer.adhanPrayers.forEachIndexed { i, p ->
                val isNext = p == next.prayer
                rv.setTextViewText(NAME_IDS[i], if (ar) p.ar else p.en)
                rv.setTextViewText(TIME_IDS[i], TimeFmt.clock(day[p], ar, tz).substringBefore(" "))
                rv.setTextColor(NAME_IDS[i], if (isNext) GOLD else MUTED)
                rv.setTextColor(TIME_IDS[i], if (isNext) GOLD else TEXT)
            }
            return rv
        }
    }
}
