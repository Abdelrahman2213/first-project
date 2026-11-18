package com.athaan.app.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.athaan.app.data.AppSettings
import com.athaan.app.data.SettingsStore
import com.athaan.app.prayer.Prayer
import com.athaan.app.prayer.PrayerTimesCalc

data class UpcomingEvent(val prayer: Prayer, val at: Long)

/**
 * Keeps two alarms armed: the next adhan and the next "before prayer" reminder.
 * Each alarm, when it fires, re-arms the next one.
 */
object AdhanScheduler {
    const val ACTION_ADHAN = "com.athaan.app.ACTION_ADHAN"
    const val ACTION_PRE_ALERT = "com.athaan.app.ACTION_PRE_ALERT"
    const val EXTRA_PRAYER = "prayer"
    const val EXTRA_AT = "at"

    private const val RC_ADHAN = 100
    private const val RC_PRE = 101

    /** Next enabled adhan strictly after [after]. Looks 3 days ahead. */
    fun nextAdhan(s: AppSettings, after: Long = System.currentTimeMillis()): UpcomingEvent? {
        for (d in 0..2) {
            val day = PrayerTimesCalc.forDayOffset(d, s.lat, s.lng, s.method, s.madhab, after)
            for (p in Prayer.adhanPrayers) {
                val t = day[p]
                if (t > after && s.enabled[p] == true) return UpcomingEvent(p, t)
            }
        }
        return null
    }

    fun canExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    fun reschedule(context: Context) {
        val s = SettingsStore.get(context).current
        val am = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()

        cancel(context, ACTION_ADHAN, RC_ADHAN)
        cancel(context, ACTION_PRE_ALERT, RC_PRE)
        if (!s.hasLocation) return

        nextAdhan(s, now)?.let { ev ->
            set(context, am, ev.at, pending(context, ACTION_ADHAN, RC_ADHAN, ev))
        }

        if (s.preAlertMinutes > 0) {
            val lead = s.preAlertMinutes * 60_000L
            // The next adhan whose reminder time is still in the future.
            var search = now
            var ev = nextAdhan(s, search)
            var guard = 0
            while (ev != null && ev.at - lead <= now && guard++ < 10) {
                search = ev.at
                ev = nextAdhan(s, search)
            }
            ev?.let {
                set(context, am, it.at - lead, pending(context, ACTION_PRE_ALERT, RC_PRE, it))
            }
        }
    }

    @android.annotation.SuppressLint("ScheduleExactAlarm", "MissingPermission")
    private fun set(context: Context, am: AlarmManager, at: Long, pi: PendingIntent) {
        if (canExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun intent(context: Context, action: String) =
        Intent(context, AlarmReceiver::class.java).setAction(action)

    private fun pending(context: Context, action: String, rc: Int, ev: UpcomingEvent): PendingIntent =
        PendingIntent.getBroadcast(
            context, rc,
            intent(context, action).putExtra(EXTRA_PRAYER, ev.prayer.name).putExtra(EXTRA_AT, ev.at),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun cancel(context: Context, action: String, rc: Int) {
        val pi = PendingIntent.getBroadcast(
            context, rc, intent(context, action),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pi)
        pi.cancel()
    }
}
