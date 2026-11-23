package com.athaan.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.athaan.app.data.AppSettings
import com.athaan.app.data.Muezzin
import com.athaan.app.prayer.Prayer
import com.athaan.app.prayer.PrayerSchedule
import com.athaan.app.prayer.TimeFmt
import com.athaan.app.schedule.AlarmKind
import com.athaan.app.schedule.UpcomingEvent
import com.athaan.app.service.AdhanService

object Notifications {
    const val CH_ADHAN = "adhan_playing"
    const val CH_SILENT = "adhan_silent"
    const val CH_PRE = "pre_alert"
    const val CH_IQAMA = "iqama"
    const val CH_REMIND = "reminders"
    const val CH_COUNTDOWN = "countdown"
    const val ID_PLAYING = 1
    private const val ID_SILENT = 2
    private const val ID_PRE = 3
    private const val ID_IQAMA = 4
    private const val ID_COUNTDOWN = 5
    private const val ID_REMIND = 6

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val adhan = NotificationChannel(CH_ADHAN, "الأذان", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "تشغيل الأذان في وقت الصلاة"
            setSound(null, null)
            enableVibration(false)
        }
        val silent = NotificationChannel(CH_SILENT, "الأذان (صامت)", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "إشعار بدخول وقت الصلاة بدون صوت"
            setSound(null, null)
        }
        val pre = NotificationChannel(CH_PRE, "تنبيه قبل الصلاة", NotificationManager.IMPORTANCE_HIGH)
        val iqama = NotificationChannel(CH_IQAMA, "الإقامة", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build(),
            )
        }
        val remind = NotificationChannel(CH_REMIND, "تذكيرات (الجمعة، الكهف، الأذكار، السحور)", NotificationManager.IMPORTANCE_DEFAULT)
        val countdown = NotificationChannel(CH_COUNTDOWN, "عدّاد الصلاة القادمة", NotificationManager.IMPORTANCE_MIN).apply {
            setShowBadge(false)
            setSound(null, null)
        }
        nm.createNotificationChannels(listOf(adhan, silent, pre, iqama, remind, countdown))
    }

    fun openAppIntent(context: Context, openPlaying: Boolean, route: String? = null): PendingIntent {
        val i = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_PLAYING, openPlaying)
        if (route != null) i.putExtra(MainActivity.EXTRA_ROUTE, route)
        return PendingIntent.getActivity(
            context, (route?.hashCode() ?: 0) + if (openPlaying) 1 else 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun serviceIntent(context: Context, action: String, rc: Int) = PendingIntent.getService(
        context, rc, Intent(context, AdhanService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun buildPlaying(context: Context, prayer: Prayer, muezzin: Muezzin, ar: Boolean, paused: Boolean = false): Notification {
        val stop = serviceIntent(context, AdhanService.ACTION_STOP, 10)
        val title = if (paused) (if (ar) "أذان ${prayer.ar} متوقف" else "${prayer.en} adhan paused")
        else (if (ar) "حان الآن موعد أذان ${prayer.ar}" else "It's time for ${prayer.en}")
        val b = NotificationCompat.Builder(context, CH_ADHAN)
            .setSmallIcon(R.drawable.ic_stat_adhan)
            .setContentTitle(title)
            .setContentText(if (paused) (if (ar) "اضغط «كمّل» للاستماع لباقي الأذان" else "Tap Resume to hear the rest") else muezzin.name(ar))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent(context, true))
        if (paused) {
            b.addAction(0, if (ar) "كمّل" else "Resume", serviceIntent(context, AdhanService.ACTION_RESUME, 11))
            b.addAction(0, if (ar) "إنهاء" else "Dismiss", stop)
        } else {
            b.addAction(0, if (ar) "إيقاف مؤقت" else "Pause", serviceIntent(context, AdhanService.ACTION_PAUSE, 12))
            b.addAction(0, if (ar) "إيقاف الأذان" else "Stop adhan", stop)
        }
        return b.build()
    }

    @SuppressLint("MissingPermission")
    fun showAdhanSilent(context: Context, prayer: Prayer, ar: Boolean, quiet: Boolean) {
        val n = NotificationCompat.Builder(context, CH_SILENT)
            .setSmallIcon(R.drawable.ic_stat_adhan)
            .setContentTitle(if (ar) "حان الآن موعد صلاة ${prayer.ar}" else "It's time for ${prayer.en}")
            .setContentText(
                if (quiet) (if (ar) "وقت الشغل: إشعار بدون صوت" else "Quiet hours: notification only")
                else (if (ar) "الوضع الصامت مفعّل" else "Silent mode is on")
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, false))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_SILENT, n) }
    }

    /** Reminders. Those about an upcoming moment carry a live countdown, so they are never out of date. */
    @SuppressLint("MissingPermission")
    fun showReminder(context: Context, kind: AlarmKind, prayer: Prayer?, target: Long, s: AppSettings) {
        val ar = s.arabic
        val tz = s.zone()
        val p = prayer ?: Prayer.FAJR
        val friday = prayer == Prayer.DHUHR && PrayerSchedule.isFriday(target.takeIf { it > 0 } ?: System.currentTimeMillis(), tz)
        val pName = if (friday) (if (ar) "الجمعة" else "Jumu'ah") else (if (ar) p.ar else p.en)
        val (channel, id, title, text, route) = when (kind) {
            AlarmKind.PRE_ALERT -> R5(CH_PRE, ID_PRE,
                if (ar) "اقترب أذان $pName" else "$pName is coming up",
                if (ar) "الأذان الساعة ${TimeFmt.clock(target, true, tz)}" else "Adhan at ${TimeFmt.clock(target, false, tz)}", null)
            AlarmKind.IQAMA -> R5(CH_IQAMA, ID_IQAMA,
                if (ar) "الإقامة · صلاة $pName" else "Iqama · $pName",
                if (ar) "قوموا إلى الصلاة" else "Time to stand for prayer", null)
            AlarmKind.FRIDAY -> R5(CH_REMIND, ID_REMIND,
                if (ar) "صلاة الجمعة" else "Jumu'ah prayer",
                if (ar) "الأذان الساعة ${TimeFmt.clock(target, true, tz)} · بادر بالذهاب إلى المسجد" else "Adhan at ${TimeFmt.clock(target, false, tz)} · head to the mosque early", null)
            AlarmKind.KAHF -> R5(CH_REMIND, ID_REMIND,
                if (ar) "يوم الجمعة" else "It's Friday",
                if (ar) "لا تنسَ قراءة سورة الكهف والإكثار من الصلاة على النبي ﷺ" else "Remember to read Surat al-Kahf", null)
            AlarmKind.ADHKAR_AM -> R5(CH_REMIND, ID_REMIND,
                if (ar) "أذكار الصباح" else "Morning adhkar", if (ar) "اضغط لقراءة أذكار الصباح" else "Tap to read the morning adhkar", "adhkar:am")
            AlarmKind.ADHKAR_PM -> R5(CH_REMIND, ID_REMIND,
                if (ar) "أذكار المساء" else "Evening adhkar", if (ar) "اضغط لقراءة أذكار المساء" else "Tap to read the evening adhkar", "adhkar:pm")
            AlarmKind.SUHOOR -> R5(CH_REMIND, ID_REMIND,
                if (ar) "السحور" else "Suhoor",
                if (ar) "أذان الفجر الساعة ${TimeFmt.clock(target, true, tz)}" else "Fajr at ${TimeFmt.clock(target, false, tz)}", null)
            AlarmKind.ADHAN -> return
        }
        val b = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_adhan)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (channel == CH_REMIND) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, false, route))
        val countsDown = kind == AlarmKind.PRE_ALERT || kind == AlarmKind.FRIDAY || kind == AlarmKind.SUHOOR
        if (countsDown && target > System.currentTimeMillis()) {
            b.setWhen(target).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
                .setTimeoutAfter(target - System.currentTimeMillis())
        }
        runCatching { NotificationManagerCompat.from(context).notify(id, b.build()) }
    }

    private data class R5(val channel: String, val id: Int, val title: String, val text: String, val route: String?)

    /** Optional always-on notification with a live countdown to the next adhan. */
    @SuppressLint("MissingPermission")
    fun updateCountdown(context: Context, s: AppSettings, next: UpcomingEvent?) {
        val nm = NotificationManagerCompat.from(context)
        if (!s.persistentCountdown || next == null || !s.hasLocation) { nm.cancel(ID_COUNTDOWN); return }
        val ar = s.arabic
        val tz = s.zone()
        val friday = next.prayer == Prayer.DHUHR && PrayerSchedule.isFriday(next.at, tz)
        val name = if (friday) (if (ar) "الجمعة" else "Jumu'ah") else (if (ar) next.prayer.ar else next.prayer.en)
        val n = NotificationCompat.Builder(context, CH_COUNTDOWN)
            .setSmallIcon(R.drawable.ic_stat_adhan)
            .setContentTitle(if (ar) "$name · ${TimeFmt.clock(next.at, true, tz)}" else "$name · ${TimeFmt.clock(next.at, false, tz)}")
            .setContentText(s.place(ar))
            .setWhen(next.at).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openAppIntent(context, false))
            .build()
        runCatching { nm.notify(ID_COUNTDOWN, n) }
    }
}
