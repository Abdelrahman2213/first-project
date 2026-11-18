package com.athaan.app.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import com.athaan.app.Notifications
import com.athaan.app.data.SettingsStore
import com.athaan.app.prayer.Prayer
import com.athaan.app.service.AdhanService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayer = intent.getStringExtra(AdhanScheduler.EXTRA_PRAYER)
            ?.let { runCatching { Prayer.valueOf(it) }.getOrNull() }
        val s = SettingsStore.get(context).current
        android.util.Log.i("AthaanAlarm", "alarm ${intent.action} prayer=$prayer")

        if (prayer != null) {
            when (intent.action) {
                AdhanScheduler.ACTION_ADHAN -> {
                    // Ignore a stale alarm (e.g. delivered very late after the phone was off).
                    val at = intent.getLongExtra(AdhanScheduler.EXTRA_AT, 0L)
                    val fresh = at == 0L || System.currentTimeMillis() - at < 15 * 60_000L
                    if (fresh && s.enabled[prayer] == true) {
                        if (s.vibrate) vibrate(context)
                        if (s.silent) {
                            Notifications.showAdhanSilent(context, prayer, s.arabic)
                        } else {
                            val svc = Intent(context, AdhanService::class.java)
                                .setAction(AdhanService.ACTION_PLAY)
                                .putExtra(AdhanService.EXTRA_PRAYER, prayer.name)
                            try {
                                ContextCompat.startForegroundService(context, svc)
                            } catch (e: Exception) {
                                // The system refused to start playback in the background:
                                // at least tell the user it's prayer time.
                                Notifications.showAdhanSilent(context, prayer, s.arabic)
                            }
                        }
                    }
                }
                AdhanScheduler.ACTION_PRE_ALERT -> {
                    if (s.enabled[prayer] == true) {
                        Notifications.showPreAlert(context, prayer, s.preAlertMinutes, s.arabic)
                    }
                }
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
