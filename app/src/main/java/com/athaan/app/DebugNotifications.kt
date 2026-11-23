package com.athaan.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.core.app.NotificationManagerCompat
import com.athaan.app.data.SettingsStore
import com.athaan.app.prayer.Prayer
import com.athaan.app.schedule.AdhanScheduler
import com.athaan.app.schedule.AlarmKind
import com.athaan.app.widget.PrayerWidget
import java.io.File

/** Debug-build helpers for the automated UI test: post sample notifications and render the custom views. */
object DebugNotifications {
    private const val TAG = "AthaanDebug"

    fun post(context: Context, what: String) {
        val s = SettingsStore.get(context).current
        val now = System.currentTimeMillis()
        val next = AdhanScheduler.nextAdhan(s, now)
        val p = next?.prayer ?: Prayer.MAGHRIB
        runCatching {
            when (what) {
                "CLEAR" -> NotificationManagerCompat.from(context).cancelAll()
                "SILENT" -> Notifications.showAdhanSilent(context, Prayer.MAGHRIB, s, Notifications.SilentReason.QUIET, now)
                "COUNTDOWN" -> { SettingsStore.get(context).update { it.copy(persistentCountdown = true) }; AdhanScheduler.reschedule(context) }
                "COUNTDOWN_OFF" -> { SettingsStore.get(context).update { it.copy(persistentCountdown = false) }; AdhanScheduler.reschedule(context) }
                "PRE_ALERT" -> Notifications.showReminder(context, AlarmKind.PRE_ALERT, p, now + 9 * 60_000L + 42_000L, s)
                "IQAMA" -> Notifications.showReminder(context, AlarmKind.IQAMA, Prayer.ASR, now, s)
                "FRIDAY" -> Notifications.showReminder(context, AlarmKind.FRIDAY, Prayer.DHUHR, now + 29 * 60_000L, s)
                "SUHOOR" -> Notifications.showReminder(context, AlarmKind.SUHOOR, Prayer.FAJR, now + 44 * 60_000L, s)
                else -> Notifications.showReminder(context, AlarmKind.valueOf(what), null, 0L, s)
            }
        }.onSuccess { Log.i(TAG, "notify $what ok") }.onFailure { Log.e(TAG, "notify $what failed", it) }
    }

    /** Inflates every custom view the way the system does and saves a picture of each. */
    fun renderAll(context: Context) {
        val s = SettingsStore.get(context).current
        val now = System.currentTimeMillis()
        val next = AdhanScheduler.nextAdhan(s, now)
        val card = NotificationCards.Card(
            label = if (s.arabic) "اقترب الأذان · القاهرة" else "Adhan soon · Cairo",
            title = if (s.arabic) (next?.prayer?.ar ?: "المغرب") else (next?.prayer?.en ?: "Maghrib"),
            time = "5:02", text = if (s.arabic) "يؤذّن لها الشيخ عبد الباسط عبد الصمد" else "Called by Abdul Basit",
            countdownTo = now + 9 * 60_000L, dayOf = next?.at ?: now, highlight = next?.prayer,
        )
        val quote = card.copy(countdownTo = null, dayOf = null, quote = "«مَن قرأ سورة الكهف يوم الجمعة أضاء له من النور ما بين الجمعتين»")
        val items = listOf(
            "card_big" to NotificationCards.big(context, card, s),
            "card_small" to NotificationCards.small(context, card, s),
            "card_quote" to NotificationCards.big(context, quote, s),
            "widget" to PrayerWidget.build(context),
        )
        val d = context.resources.displayMetrics.density
        for ((name, rv) in items) {
            runCatching {
                val parent = FrameLayout(context)
                val v = rv.apply(context, parent)
                val w = (360 * d).toInt()
                v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                v.layout(0, 0, w, v.measuredHeight)
                val b = Bitmap.createBitmap(w, v.measuredHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                val c = Canvas(b)
                c.drawColor(if (name == "widget") Color.parseColor("#3A4A63") else Color.WHITE)
                v.draw(c)
                File(context.filesDir, "render_$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }.onSuccess { Log.i(TAG, "render $name ok") }.onFailure { Log.e(TAG, "render $name failed", it) }
        }
    }
}
