package com.athaan.app.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.athaan.app.Notifications
import com.athaan.app.data.SettingsStore
import com.athaan.app.prayer.Prayer
import com.athaan.app.service.AdhanService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(AdhanScheduler.EXTRA_KIND)?.let { runCatching { AlarmKind.valueOf(it) }.getOrNull() }
        val prayer = intent.getStringExtra(AdhanScheduler.EXTRA_PRAYER)?.let { runCatching { Prayer.valueOf(it) }.getOrNull() }
        val at = intent.getLongExtra(AdhanScheduler.EXTRA_AT, 0L)
        val target = intent.getLongExtra(AdhanScheduler.EXTRA_TARGET, 0L)
        val s = SettingsStore.get(context).current
        val now = System.currentTimeMillis()
        Log.i("AthaanAlarm", "alarm $kind prayer=$prayer due=$at late=${(now - at) / 1000}s")

        // Ignore a stale alarm (e.g. delivered long after the phone was off).
        val fresh = at == 0L || now - at < 15 * 60_000L
        if (fresh && kind != null) {
            when (kind) {
                AlarmKind.ADHAN -> if (prayer != null && s.enabled[prayer] == true) {
                    if (s.vibrate) vibrate(context)
                    if (s.silent || s.inQuietHours(now)) {
                        Notifications.showAdhanSilent(context, prayer, s.arabic, quiet = !s.silent)
                    } else {
                        val svc = Intent(context, AdhanService::class.java)
                            .setAction(AdhanService.ACTION_PLAY)
                            .putExtra(AdhanService.EXTRA_PRAYER, prayer.name)
                        try {
                            ContextCompat.startForegroundService(context, svc)
                        } catch (e: Exception) {
                            // The system refused to start playback in the background:
                            // at least tell the user it's prayer time.
                            Notifications.showAdhanSilent(context, prayer, s.arabic, quiet = false)
                        }
                    }
                }
                else -> Notifications.showReminder(context, kind, prayer, target, s)
            }
        }
        AdhanScheduler.reschedule(context)
    }

    private fun vibrate(context: Context) {
        val pattern = longArrayOf(0, 400, 250, 400, 250, 700)
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        runCatching { v?.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
    }
}
