package com.athaan.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.athaan.app.data.Muezzin
import com.athaan.app.prayer.Prayer
import com.athaan.app.service.AdhanService

object Notifications {
    const val CH_ADHAN = "adhan_playing"
    const val CH_SILENT = "adhan_silent"
    const val CH_PRE = "pre_alert"
    const val ID_PLAYING = 1
    private const val ID_SILENT = 2
    private const val ID_PRE = 3

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
        val pre = NotificationChannel(CH_PRE, "تنبيه قبل الصلاة", NotificationManager.IMPORTANCE_DEFAULT)
        nm.createNotificationChannels(listOf(adhan, silent, pre))
    }

    fun openAppIntent(context: Context, openPlaying: Boolean): PendingIntent {
        val i = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_PLAYING, openPlaying)
        return PendingIntent.getActivity(
            context, if (openPlaying) 1 else 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun buildPlaying(context: Context, prayer: Prayer, muezzin: Muezzin, ar: Boolean): Notification {
        val stop = PendingIntent.getService(
            context, 10,
            Intent(context, AdhanService::class.java).setAction(AdhanService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (ar) "حان الآن موعد أذان ${prayer.ar}" else "It's time for ${prayer.en}"
        return NotificationCompat.Builder(context, CH_ADHAN)
            .setSmallIcon(R.drawable.ic_stat_adhan)
            .setContentTitle(title)
            .setContentText(muezzin.name(ar))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(openAppIntent(context, true))
            .addAction(0, if (ar) "إيقاف الأذان" else "Stop adhan", stop)
            .build()
    }

    @SuppressLint("MissingPermission")
    fun showAdhanSilent(context: Context, prayer: Prayer, ar: Boolean) {
        val n = NotificationCompat.Builder(context, CH_SILENT)
            .setSmallIcon(R.drawable.ic_stat_adhan)
            .setContentTitle(if (ar) "حان الآن موعد صلاة ${prayer.ar}" else "It's time for ${prayer.en}")
            .setContentText(if (ar) "الوضع الصامت مفعّل" else "Silent mode is on")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, false))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_SILENT, n) }
    }

    @SuppressLint("MissingPermission")
    fun showPreAlert(context: Context, prayer: Prayer, minutes: Int, ar: Boolean) {
        val n = NotificationCompat.Builder(context, CH_PRE)
            .setSmallIcon(R.drawable.ic_stat_adhan)
            .setContentTitle(if (ar) "باقي $minutes دقائق على أذان ${prayer.ar}" else "$minutes minutes until ${prayer.en}")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setTimeoutAfter(minutes * 60_000L)
            .setContentIntent(openAppIntent(context, false))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_PRE, n) }
    }
}
