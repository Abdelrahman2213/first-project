package com.athaan.app.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import com.athaan.app.Notifications
import com.athaan.app.audio.AudioRepository
import com.athaan.app.data.Muezzins
import com.athaan.app.data.SettingsStore
import com.athaan.app.prayer.Prayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AdhanPlayback(
    val prayer: Prayer,
    val muezzinId: String,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val muted: Boolean = false,
)

/** Plays the adhan as a foreground service so it keeps going with the screen off. */
class AdhanService : Service() {

    private var player: MediaPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null
    private var triedFallback = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val prayer = intent.getStringExtra(EXTRA_PRAYER)?.let { runCatching { Prayer.valueOf(it) }.getOrNull() }
                    ?: Prayer.DHUHR
                val muezzinId = intent.getStringExtra(EXTRA_MUEZZIN)
                play(prayer, muezzinId)
            }
            ACTION_STOP -> stopAll()
            ACTION_TOGGLE_MUTE -> toggleMute()
            ACTION_SET_VOLUME -> {
                val v = intent.getFloatExtra(EXTRA_VOLUME, 0.8f)
                if (_state.value?.muted != true) player?.setVolume(v, v)
            }
            else -> if (player == null) stopAll()
        }
        return START_NOT_STICKY
    }

    private fun play(prayer: Prayer, muezzinOverride: String?) {
        val s = SettingsStore.get(this).current
        val m = muezzinOverride?.let { Muezzins.find(it) }?.takeIf { it.canCall(prayer) } ?: s.muezzin(prayer)
        val track = m.trackFor(prayer)
        val notif = Notifications.buildPlaying(this, prayer, m, s.arabic)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, Notifications.ID_PLAYING, notif, type)

        AudioRepository.get(this).stopPreview()
        releasePlayer()
        triedFallback = false
        _state.value = AdhanPlayback(prayer, m.id)

        val repo = AudioRepository.get(this)
        val src = repo.source(track)
        Log.i(TAG, "adhan ${prayer.name} muezzin=${m.id} fajr=${track.fajr} source=$src")
        startPlayer(src, s.volume)

        // Keep an offline copy for next time.
        if (s.autoDownload && !repo.isDownloaded(track)) repo.download(track)
    }

    private fun startPlayer(source: String?, volume: Float) {
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            if (source != null) mp.setDataSource(source)
            else mp.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            mp.setVolume(volume, volume)
            mp.setOnPreparedListener {
                it.start()
                _state.value = _state.value?.copy(durationMs = it.duration)
                startTicker()
            }
            mp.setOnCompletionListener { stopAll() }
            mp.setOnErrorListener { _, what, extra -> Log.w(TAG, "player error $what/$extra"); fallback(volume); true }
            mp.prepareAsync()
        } catch (e: Exception) {
            fallback(volume)
        }
    }

    /** No internet and no offline copy: play the phone's alarm sound instead of staying silent. */
    private fun fallback(volume: Float) {
        releasePlayer()
        if (triedFallback) { stopAll(); return }
        triedFallback = true
        startPlayer(null, volume)
    }

    private fun toggleMute() {
        val st = _state.value ?: return
        val muted = !st.muted
        val v = if (muted) 0f else SettingsStore.get(this).current.volume
        player?.setVolume(v, v)
        _state.value = st.copy(muted = muted)
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val p = player ?: break
                runCatching { _state.value = _state.value?.copy(positionMs = p.currentPosition) }
                delay(500)
            }
        }
    }

    private fun releasePlayer() {
        ticker?.cancel()
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }

    private fun stopAll() {
        releasePlayer()
        _state.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releasePlayer()
        _state.value = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AthaanAdhan"
        const val ACTION_PLAY = "com.athaan.app.PLAY"
        const val ACTION_STOP = "com.athaan.app.STOP"
        const val ACTION_TOGGLE_MUTE = "com.athaan.app.TOGGLE_MUTE"
        const val ACTION_SET_VOLUME = "com.athaan.app.SET_VOLUME"
        const val EXTRA_PRAYER = "prayer"
        const val EXTRA_MUEZZIN = "muezzin"
        const val EXTRA_VOLUME = "volume"

        private val _state = MutableStateFlow<AdhanPlayback?>(null)
        val state: StateFlow<AdhanPlayback?> = _state.asStateFlow()
    }
}
