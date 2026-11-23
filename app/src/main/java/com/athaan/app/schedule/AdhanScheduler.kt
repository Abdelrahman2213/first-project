package com.athaan.app.schedule

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.athaan.app.Notifications
import com.athaan.app.data.AppSettings
import com.athaan.app.data.SettingsStore
import com.athaan.app.prayer.Prayer
import com.athaan.app.prayer.PrayerSchedule
import com.athaan.app.widget.PrayerWidget
import java.util.Calendar

data class UpcomingEvent(val prayer: Prayer, val at: Long)

/** Everything the app can remind about. Each kind keeps one alarm armed for its next occurrence. */
enum class AlarmKind(val requestCode: Int) {
    ADHAN(100),       // the adhan itself
    PRE_ALERT(101),   // "N minutes to the adhan"
    IQAMA(102),       // iqama, N minutes after the adhan
    FRIDAY(103),      // before Jumu'ah
    KAHF(104),        // Surat al-Kahf on Friday morning
    ADHKAR_AM(105),   // morning adhkar, after Fajr
    ADHKAR_PM(106),   // evening adhkar, after Asr
    SUHOOR(107),      // Ramadan: before Fajr
}

/**
 * Keeps the app's alarms armed. Every alarm, when it fires, calls [reschedule] again, so the
 * chain never breaks; reboots, time changes and app updates re-arm it too (BootReceiver).
 */
object AdhanScheduler {
    const val ACTION_ALARM = "com.athaan.app.ACTION_ALARM"
    const val EXTRA_KIND = "kind"
    const val EXTRA_PRAYER = "prayer"
    /** When the alarm was due. */
    const val EXTRA_AT = "at"
    /** The moment the reminder is about (e.g. the adhan time for a pre-alert). */
    const val EXTRA_TARGET = "target"
    private const val TAG = "AthaanSchedule"

    /** Next enabled adhan strictly after [after]. */
    fun nextAdhan(s: AppSettings, after: Long = System.currentTimeMillis()): UpcomingEvent? =
        PrayerSchedule.around(s, after).firstOrNull { it.at > after && s.enabled[it.prayer] == true }
            ?.let { UpcomingEvent(it.prayer, it.at) }

    fun canExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    internal data class Plan(val kind: AlarmKind, val at: Long, val prayer: Prayer?, val target: Long)

    /** Works out when each kind of alarm should next fire. */
    internal fun plan(s: AppSettings, now: Long): List<Plan> {
        val tz = s.zone()
        val events = PrayerSchedule.around(s, now).filter { s.enabled[it.prayer] == true }
        val out = mutableListOf<Plan>()

        events.firstOrNull { it.at > now }?.let { out += Plan(AlarmKind.ADHAN, it.at, it.prayer, it.at) }

        if (s.preAlertMinutes > 0) {
            val lead = s.preAlertMinutes * 60_000L
            events.firstOrNull { it.at - lead > now }?.let { out += Plan(AlarmKind.PRE_ALERT, it.at - lead, it.prayer, it.at) }
        }

        events.mapNotNull { e -> (s.iqama[e.prayer] ?: 0).takeIf { it > 0 }?.let { e to e.at + it * 60_000L } }
            .firstOrNull { it.second > now }
            ?.let { (e, at) -> out += Plan(AlarmKind.IQAMA, at, e.prayer, at) }

        val all = PrayerSchedule.around(s, now) // reminders below don't depend on the adhan switch
        if (s.fridayMinutes > 0) {
            nextFridayDhuhr(s, now)?.let { dhuhr ->
                val at = dhuhr - s.fridayMinutes * 60_000L
                if (at > now) out += Plan(AlarmKind.FRIDAY, at, Prayer.DHUHR, dhuhr)
            }
        }
        if (s.kahfReminder) {
            out += Plan(AlarmKind.KAHF, nextFridayAt(now, 9 * 60 + 30, tz), null, 0)
        }
        if (s.adhkarReminders) {
            all.firstOrNull { it.prayer == Prayer.FAJR && it.at + 30 * 60_000L > now }
                ?.let { out += Plan(AlarmKind.ADHKAR_AM, it.at + 30 * 60_000L, Prayer.FAJR, 0) }
            all.firstOrNull { it.prayer == Prayer.ASR && it.at + 20 * 60_000L > now }
                ?.let { out += Plan(AlarmKind.ADHKAR_PM, it.at + 20 * 60_000L, Prayer.ASR, 0) }
        }
        if (s.suhoorMinutes > 0) {
            all.firstOrNull { it.prayer == Prayer.FAJR && it.at - s.suhoorMinutes * 60_000L > now && PrayerSchedule.isRamadan(it.at, tz) }
                ?.let { out += Plan(AlarmKind.SUHOOR, it.at - s.suhoorMinutes * 60_000L, Prayer.FAJR, it.at) }
        }
        return out
    }

    private fun nextFridayDhuhr(s: AppSettings, now: Long): Long? {
        for (d in 0..7) {
            val t = PrayerSchedule.day(s, d, now)[Prayer.DHUHR]
            if (PrayerSchedule.isFriday(t, s.zone()) && t > now) return t
        }
        return null
    }

    private fun nextFridayAt(now: Long, minuteOfDay: Int, tz: java.util.TimeZone): Long {
        val c = Calendar.getInstance(tz).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60); set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        while (c.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY || c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    @SuppressLint("ScheduleExactAlarm", "MissingPermission")
    fun reschedule(context: Context) {
        val s = SettingsStore.get(context).current
        val am = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()

        AlarmKind.values().forEach { cancel(context, it) }
        if (s.hasLocation) {
            for (p in plan(s, now)) {
                val pi = pending(context, p)
                // Alarm-clock alarms are exact, survive Doze and battery savers, and may start
                // the adhan from the background. Used for anything time-critical.
                val critical = p.kind == AlarmKind.ADHAN || p.kind == AlarmKind.PRE_ALERT ||
                    p.kind == AlarmKind.IQAMA || p.kind == AlarmKind.SUHOOR
                if (critical && canExact(context)) {
                    runCatching {
                        am.setAlarmClock(AlarmManager.AlarmClockInfo(p.at, Notifications.openAppIntent(context, false)), pi)
                    }.onFailure { setExactish(context, am, p.at, pi) }
                } else {
                    setExactish(context, am, p.at, pi)
                }
                if (p.kind == AlarmKind.ADHAN) Log.i(TAG, "next adhan ${p.prayer?.name} at ${p.at} exact=${canExact(context)}")
                else Log.i(TAG, "next ${p.kind} at ${p.at}")
            }
        }
        Notifications.updateCountdown(context, s, nextAdhan(s, now))
        PrayerWidget.updateAll(context)
    }

    @SuppressLint("ScheduleExactAlarm", "MissingPermission")
    private fun setExactish(context: Context, am: AlarmManager, at: Long, pi: PendingIntent) {
        runCatching {
            if (canExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }.onFailure { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
    }

    private fun base(context: Context) = Intent(context, AlarmReceiver::class.java).setAction(ACTION_ALARM)

    private fun pending(context: Context, p: Plan): PendingIntent =
        PendingIntent.getBroadcast(
            context, p.kind.requestCode,
            base(context).putExtra(EXTRA_KIND, p.kind.name).putExtra(EXTRA_PRAYER, p.prayer?.name)
                .putExtra(EXTRA_AT, p.at).putExtra(EXTRA_TARGET, p.target),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun cancel(context: Context, kind: AlarmKind) {
        val pi = PendingIntent.getBroadcast(
            context, kind.requestCode, base(context),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pi)
        pi.cancel()
    }
}
