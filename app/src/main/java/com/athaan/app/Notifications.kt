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
import android.media.session.MediaSession
import android.graphics.drawable.Icon
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

    /** Gold used for the notification's icon and app name. */
    private const val ACCENT = 0xFFB8903F.toInt()
    private const val INK = 0xFF101A2E.toInt()

    private fun prayerLabel(p: Prayer, at: Long, s: AppSettings): String {
        val ar = s.arabic
        return if (p == Prayer.DHUHR && PrayerSchedule.isFriday(at, s.zone())) (if (ar) "الجمعة" else "Jumu'ah") else (if (ar) p.ar else p.en)
    }

    private fun city(s: AppSettings) = s.place(s.arabic).substringBefore(if (s.arabic) "،" else ",")

    /**
     * The playing adhan as a media player: artwork, progress, and pause/resume and stop.
     * On Android 13+ the controls come from the media session; older versions use these actions.
     */
    fun buildPlaying(
        context: Context, prayer: Prayer, muezzin: Muezzin, s: AppSettings,
        paused: Boolean = false, token: MediaSession.Token? = null,
    ): Notification {
        val ar = s.arabic
        val now = System.currentTimeMillis()
        val name = prayerLabel(prayer, now, s)
        val stop = serviceIntent(context, AdhanService.ACTION_STOP, 10)
        val toggle = if (paused) Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_n_play), if (ar) "كمّل" else "Resume",
            serviceIntent(context, AdhanService.ACTION_RESUME, 11)).build()
        else Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_n_pause), if (ar) "إيقاف مؤقت" else "Pause",
            serviceIntent(context, AdhanService.ACTION_PAUSE, 12)).build()
        val end = Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_n_stop),
            if (paused) (if (ar) "إنهاء" else "Dismiss") else (if (ar) "إيقاف الأذان" else "Stop adhan"), stop).build()

        val style = Notification.MediaStyle().setShowActionsInCompactView(0, 1)
        if (token != null) style.setMediaSession(token)
        return Notification.Builder(context, CH_ADHAN)
            .setSmallIcon(R.drawable.ic_stat_adhan)
            .setContentTitle(if (ar) "أذان $name" else "$name adhan")
            .setContentText(if (paused) (if (ar) "متوقف مؤقتًا · اضغط كمّل للاستماع لباقي الأذان" else "Paused · tap Resume to hear the rest") else muezzin.title(ar))
            .setSubText("${TimeFmt.clock(now, ar, s.zone())} · ${city(s)}")
            .setLargeIcon(NotificationArt.adhanArt())
            .setStyle(style)
            .setColor(INK)
            .setColorized(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(context, true))
            .addAction(toggle)
            .addAction(end)
            .build()
    }

    /** Builds a card notification and posts it. */
    @SuppressLint("MissingPermission")
    private fun post(
        context: Context, s: AppSettings, channel: String, id: Int, card: NotificationCards.Card,
        route: String? = null, action: NotificationCompat.Action? = null, timeoutAt: Long? = null,
        priority: Int = NotificationCompat.PRIORITY_HIGH, category: String = NotificationCompat.CATEGORY_REMINDER,
    ) {
        val small = NotificationCards.small(context, card, s)
        val b = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_adhan)
            .setColor(ACCENT)
            .setContentTitle(listOfNotNull(card.title, card.time).joinToString(" · "))
            .setContentText(card.text ?: card.label)
            .setSubText(card.label)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(small)
            .setCustomBigContentView(NotificationCards.big(context, card, s))
            .setCustomHeadsUpContentView(NotificationCards.small(context, card, s))
            .setCategory(category)
            .setPriority(priority)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(card.countdownTo == null)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, false, route))
        if (action != null) b.addAction(action)
        if (timeoutAt != null && timeoutAt > System.currentTimeMillis()) b.setTimeoutAfter(timeoutAt - System.currentTimeMillis())
        runCatching { NotificationManagerCompat.from(context).notify(id, b.build()) }
    }

    enum class SilentReason { SILENT, QUIET, BLOCKED }

    fun showAdhanSilent(context: Context, prayer: Prayer, s: AppSettings, reason: SilentReason, at: Long = System.currentTimeMillis()) {
        val ar = s.arabic
        val tz = s.zone()
        val card = NotificationCards.Card(
            label = if (ar) "حان الآن وقت الصلاة" else "It's prayer time",
            title = prayerLabel(prayer, at, s),
            time = TimeFmt.clock(at, ar, tz),
            text = when (reason) {
                SilentReason.QUIET -> if (ar) "وقت الشغل · وصلك إشعار بدون صوت الأذان" else "Quiet hours · notification only"
                SilentReason.SILENT -> if (ar) "الوضع الصامت مفعّل · وصلك إشعار بدون صوت الأذان" else "Silent mode is on · notification only"
                SilentReason.BLOCKED -> if (ar) "الجهاز منع تشغيل الأذان في الخلفية · استثنِ التطبيق من توفير البطارية" else "The phone blocked background playback · exclude the app from battery saving"
            },
            dayOf = at, highlight = prayer,
        )
        post(context, s, CH_SILENT, ID_SILENT, card)
    }

    /** Reminders. Those about an upcoming moment carry a live countdown, so they are never out of date. */
    fun showReminder(context: Context, kind: AlarmKind, prayer: Prayer?, target: Long, s: AppSettings) {
        val ar = s.arabic
        val tz = s.zone()
        val p = prayer ?: Prayer.FAJR
        val clock = if (target > 0) TimeFmt.clock(target, ar, tz) else null
        when (kind) {
            AlarmKind.PRE_ALERT -> post(context, s, CH_PRE, ID_PRE, NotificationCards.Card(
                label = (if (ar) "اقترب الأذان · " else "Adhan soon · ") + city(s),
                title = prayerLabel(p, target, s), time = clock,
                text = (if (ar) "يؤذّن لها " else "Called by ") + s.muezzin(p).title(ar),
                countdownTo = target, countLabel = if (ar) "باقي على الأذان" else "Until the adhan",
                dayOf = target, highlight = p,
            ), timeoutAt = target)
            AlarmKind.IQAMA -> post(context, s, CH_IQAMA, ID_IQAMA, NotificationCards.Card(
                label = (if (ar) "صلاة " else "") + prayerLabel(p, target, s),
                title = if (ar) "الإقامة" else "Iqama", time = clock,
                text = if (ar) "حان وقت إقامة الصلاة" else "Time to stand for prayer",
                quote = if (ar) "قد قامت الصلاة، قد قامت الصلاة" else null,
                dayOf = target, highlight = p,
            ), timeoutAt = target + 30 * 60_000L)
            AlarmKind.FRIDAY -> post(context, s, CH_REMIND, ID_REMIND, NotificationCards.Card(
                label = if (ar) "صلاة الجمعة" else "Jumu'ah prayer",
                title = if (ar) "الجمعة" else "Jumu'ah", time = clock,
                text = if (ar) "بادر بالذهاب إلى المسجد، ويُستحب الغُسل والطِّيب والتبكير" else "Head to the mosque early",
                countdownTo = target, countLabel = if (ar) "باقي على الأذان" else "Until the adhan",
            ), timeoutAt = target)
            AlarmKind.KAHF -> post(context, s, CH_REMIND, ID_REMIND, NotificationCards.Card(
                label = if (ar) "يوم الجمعة" else "It's Friday",
                title = if (ar) "سورة الكهف" else "Surat al-Kahf",
                text = if (ar) "وأكثِر من الصلاة على النبي ﷺ" else "And send many blessings upon the Prophet ﷺ",
                quote = if (ar) "«مَن قرأ سورة الكهف يوم الجمعة أضاء له من النور ما بين الجمعتين»"
                else "“Whoever reads Surat al-Kahf on Friday, light will shine for him between the two Fridays.”",
            ), priority = NotificationCompat.PRIORITY_DEFAULT)
            AlarmKind.ADHKAR_AM, AlarmKind.ADHKAR_PM -> {
                val am = kind == AlarmKind.ADHKAR_AM
                val route = if (am) "adhkar:am" else "adhkar:pm"
                post(context, s, CH_REMIND, ID_REMIND, NotificationCards.Card(
                    label = if (ar) "حصّن يومك" else "A moment of remembrance",
                    title = if (am) (if (ar) "أذكار الصباح" else "Morning adhkar") else (if (ar) "أذكار المساء" else "Evening adhkar"),
                    text = if (ar) "اضغط لقراءتها وعدّها" else "Tap to read and count them",
                    quote = if (am) "أَصْبَحْنا وأَصْبَحَ المُلْكُ لله، والحمدُ لله" else "أَمْسَيْنا وأَمْسى المُلْكُ لله، والحمدُ لله",
                ), route = route, priority = NotificationCompat.PRIORITY_DEFAULT,
                    action = NotificationCompat.Action(R.drawable.ic_n_book, if (ar) "اقرأ الأذكار" else "Read", openAppIntent(context, false, route)))
            }
            AlarmKind.SUHOOR -> post(context, s, CH_REMIND, ID_REMIND, NotificationCards.Card(
                label = if (ar) "رمضان كريم" else "Ramadan",
                title = if (ar) "السحور" else "Suhoor", time = clock,
                text = if (ar) "الإمساك مع أذان الفجر الساعة $clock" else "Stop eating at the Fajr adhan, $clock",
                countdownTo = target, countLabel = if (ar) "باقي على الفجر" else "Until Fajr",
                quote = if (ar) "«تسحّروا فإنّ في السَّحور بركة»" else "“Take suhoor, for in suhoor there is blessing.”",
            ), timeoutAt = target)
            AlarmKind.ADHAN -> Unit
        }
    }

    /** Optional always-on notification with a live countdown to the next adhan. */
    @SuppressLint("MissingPermission")
    fun updateCountdown(context: Context, s: AppSettings, next: UpcomingEvent?) {
        val nm = NotificationManagerCompat.from(context)
        if (!s.persistentCountdown || next == null || !s.hasLocation) { nm.cancel(ID_COUNTDOWN); return }
        val ar = s.arabic
        val card = NotificationCards.Card(
            label = (if (ar) "الصلاة القادمة · " else "Next prayer · ") + city(s),
            title = prayerLabel(next.prayer, next.at, s),
            time = TimeFmt.clock(next.at, ar, s.zone()),
            text = (if (ar) "يؤذّن لها " else "Called by ") + s.muezzin(next.prayer).title(ar),
            countdownTo = next.at, countLabel = if (ar) "باقي" else "Left",
            dayOf = next.at, highlight = next.prayer,
        )
        val n = NotificationCompat.Builder(context, CH_COUNTDOWN)
            .setSmallIcon(R.drawable.ic_stat_adhan)
            .setColor(ACCENT)
            .setContentTitle("${card.title} · ${card.time}")
            .setContentText(card.label)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(NotificationCards.small(context, card, s))
            .setCustomBigContentView(NotificationCards.big(context, card, s))
            .setShowWhen(false)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openAppIntent(context, false))
            .build()
        runCatching { nm.notify(ID_COUNTDOWN, n) }
    }
}
