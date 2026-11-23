package com.athaan.app.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
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
    /** Paused by the user (volume key, power key, flip, or the Pause button); can be resumed. */
    val paused: Boolean = false,
)

/**
 * Plays the adhan as a foreground service so it keeps going with the screen off.
 *
 * While it plays, the volume keys, the power key and turning the phone face down all pause it
 * (each can be switched off in Settings). The notification and the app then offer "Resume";
 * a paused adhan ends by itself after 10 minutes.
 */
class AdhanService : Service() {

    private var player: MediaPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null
    private var fader: Job? = null
    private var limiter: Job? = null
    private var autoStop: Job? = null
    private var triedFallback = false
    private var session: MediaSession? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var flipListener: SensorEventListener? = null
    private var faceDownSince = 0L
    private var seenFaceUp = false
    private var screenLock: PowerManager.WakeLock? = null
    private var volume = 0.8f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val prayer = intent.getStringExtra(EXTRA_PRAYER)?.let { runCatching { Prayer.valueOf(it) }.getOrNull() }
                    ?: Prayer.DHUHR
                play(prayer, intent.getStringExtra(EXTRA_MUEZZIN))
            }
            ACTION_STOP -> stopAll()
            ACTION_PAUSE -> pauseByUser("button")
            ACTION_RESUME -> resume()
            ACTION_TOGGLE_MUTE -> toggleMute()
            ACTION_SET_VOLUME -> {
                volume = intent.getFloatExtra(EXTRA_VOLUME, 0.8f)
                if (_state.value?.muted != true && fader?.isActive != true) player?.setVolume(volume, volume)
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
        volume = s.volume
        _state.value = AdhanPlayback(prayer, m.id)

        val repo = AudioRepository.get(this)
        val src = repo.source(track)
        Log.i(TAG, "adhan ${prayer.name} muezzin=${m.id} fajr=${track.fajr} source=$src")
        startPlayer(src)
        startSilencers()

        // Keep an offline copy for next time.
        if (s.autoDownload && !repo.isDownloaded(track)) repo.download(track)
    }

    private fun startPlayer(source: String?) {
        val s = SettingsStore.get(this).current
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
            val startVol = if (s.fadeIn && source != null) 0.05f * volume else volume
            currentVolume = startVol
            mp.setVolume(startVol, startVol)
            mp.setOnPreparedListener {
                it.start()
                _state.value = _state.value?.copy(durationMs = it.duration)
                startTicker()
                if (s.fadeIn && source != null) fadeTo(volume, 6000)
                limitLength = source != null
                startLimiter()
            }
            mp.setOnCompletionListener { stopAll() }
            mp.setOnErrorListener { _, what, extra -> Log.w(TAG, "player error $what/$extra"); fallback(); true }
            mp.prepareAsync()
        } catch (e: Exception) {
            fallback()
        }
    }

    private fun fadeTo(target: Float, ms: Long) {
        fader?.cancel()
        val p = player ?: return
        fader = scope.launch {
            val steps = 30
            val start = currentVolume
            for (i in 1..steps) {
                if (_state.value?.muted == true) return@launch
                val v = start + (target - start) * i / steps
                currentVolume = v
                runCatching { p.setVolume(v, v) }
                delay(ms / steps)
            }
        }
    }

    private var currentVolume = 0f
    private var limitLength = false

    /** Short adhan: fade out and stop once the chosen length is reached (counted from the start of the recording). */
    private fun startLimiter() {
        limiter?.cancel()
        val max = SettingsStore.get(this).current.adhanLength.maxMs
        val p = player ?: return
        if (!limitLength || max == Long.MAX_VALUE) return
        val left = max - runCatching { p.currentPosition.toLong() }.getOrDefault(0L)
        limiter = scope.launch {
            delay((left - 2500).coerceAtLeast(0))
            fadeTo(0f, 2500)
            delay(2600)
            stopAll()
        }
    }

    /** No internet and no offline copy: play the phone's alarm sound instead of staying silent. */
    private fun fallback() {
        releasePlayer()
        if (triedFallback) { stopAll(); return }
        triedFallback = true
        startPlayer(null)
    }

    // ---------- Pausing the adhan from outside the app ----------

    private fun startSilencers() {
        val s = SettingsStore.get(this).current

        // Volume keys: a media session that handles volume itself receives the keys while the
        // adhan plays, even with the screen off. Any press pauses the adhan.
        if (s.volumeKeysPause && session == null) {
            session = MediaSession(this, "AthaanAdhan").apply {
                setPlaybackToRemote(object : VolumeProvider(VOLUME_CONTROL_RELATIVE, 100, 50) {
                    override fun onAdjustVolume(direction: Int) {
                        if (direction != 0) pauseByUser("volume key")
                    }
                })
                setCallback(object : MediaSession.Callback() {
                    override fun onPause() = pauseByUser("media button")
                    override fun onStop() = stopAll()
                    override fun onPlay() = resume()
                })
                isActive = true
            }
        }
        updateSessionState()

        // Power key: the screen turning off while the adhan plays pauses it.
        if (s.screenOffPause && screenReceiver == null) {
            screenReceiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    when (i.action) {
                        Intent.ACTION_SCREEN_OFF -> pauseByUser("screen off")
                        Intent.ACTION_SCREEN_ON -> if (_state.value?.paused == false) holdScreen()
                    }
                }
            }
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) }
            ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            holdScreen()
        }

        // Turning the phone face down pauses it.
        if (s.flipToSilence && flipListener == null) {
            val sm = getSystemService(SensorManager::class.java)
            val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (sm != null && sensor != null) {
                val l = object : SensorEventListener {
                    override fun onSensorChanged(e: SensorEvent) {
                        val z = e.values[2]
                        val now = System.currentTimeMillis()
                        // Only a turn counts: a phone already lying face down when the adhan starts keeps playing.
                        if (z > -4f) seenFaceUp = true
                        if (z < -8.5f && seenFaceUp) {
                            if (faceDownSince == 0L) faceDownSince = now
                            else if (now - faceDownSince > 700) { faceDownSince = 0L; seenFaceUp = false; pauseByUser("face down") }
                        } else faceDownSince = 0L
                    }
                    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
                }
                sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                flipListener = l
            }
        }
    }

    /**
     * While the adhan plays with the screen on, keep it from timing out, so the screen only
     * goes off when the power key is pressed (which then pauses the adhan).
     */
    @Suppress("DEPRECATION")
    private fun holdScreen() {
        if (!SettingsStore.get(this).current.screenOffPause) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        if (!pm.isInteractive || screenLock?.isHeld == true) return
        screenLock = runCatching {
            pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "Athaan:adhanScreen").apply {
                setReferenceCounted(false)
                acquire(8 * 60_000L)
            }
        }.getOrNull()
    }

    private fun releaseScreen() {
        screenLock?.let { runCatching { if (it.isHeld) it.release() } }
        screenLock = null
    }

    private fun stopSilencers() {
        releaseScreen()
        seenFaceUp = false
        session?.let { runCatching { it.isActive = false; it.release() } }
        session = null
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        screenReceiver = null
        flipListener?.let { l -> runCatching { getSystemService(SensorManager::class.java)?.unregisterListener(l) } }
        flipListener = null
    }

    private fun updateSessionState() {
        val st = _state.value
        session?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_STOP or PlaybackState.ACTION_PLAY_PAUSE)
                .setState(if (st?.paused == true) PlaybackState.STATE_PAUSED else PlaybackState.STATE_PLAYING, (st?.positionMs ?: 0).toLong(), 1f)
                .build()
        )
    }

    private fun pauseByUser(reason: String) {
        val st = _state.value ?: return
        val p = player ?: return
        if (st.paused) return
        Log.i(TAG, "paused by user ($reason)")
        runCatching { p.pause() }
        limiter?.cancel()
        fader?.cancel()
        _state.value = st.copy(paused = true, positionMs = runCatching { p.currentPosition }.getOrDefault(st.positionMs))
        releaseScreen()
        updateSessionState()
        refreshNotification()
        autoStop?.cancel()
        autoStop = scope.launch { delay(10 * 60_000L); stopAll() }
    }

    private fun resume() {
        val st = _state.value ?: return
        val p = player ?: return
        if (!st.paused) return
        Log.i(TAG, "resumed")
        autoStop?.cancel()
        runCatching { p.setVolume(volume, volume); currentVolume = volume; p.start() }
        _state.value = st.copy(paused = false, muted = false)
        startLimiter()
        holdScreen()
        updateSessionState()
        refreshNotification()
    }

    private fun refreshNotification() {
        val st = _state.value ?: return
        val s = SettingsStore.get(this).current
        val n = Notifications.buildPlaying(this, st.prayer, Muezzins.byId(st.muezzinId), s.arabic, paused = st.paused)
        runCatching { NotificationManagerCompat.from(this).notify(Notifications.ID_PLAYING, n) }
    }

    private fun toggleMute() {
        val st = _state.value ?: return
        val muted = !st.muted
        fader?.cancel()
        val v = if (muted) 0f else volume
        currentVolume = v
        player?.setVolume(v, v)
        _state.value = st.copy(muted = muted)
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val p = player ?: break
                runCatching {
                    if (_state.value?.paused != true) _state.value = _state.value?.copy(positionMs = p.currentPosition)
                }
                delay(500)
            }
        }
    }

    private fun releasePlayer() {
        ticker?.cancel(); fader?.cancel(); limiter?.cancel(); autoStop?.cancel()
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
    }

    private fun stopAll() {
        releasePlayer()
        stopSilencers()
        _state.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releasePlayer()
        stopSilencers()
        _state.value = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AthaanAdhan"
        const val ACTION_PLAY = "com.athaan.app.PLAY"
        const val ACTION_STOP = "com.athaan.app.STOP"
        const val ACTION_PAUSE = "com.athaan.app.PAUSE"
        const val ACTION_RESUME = "com.athaan.app.RESUME"
        const val ACTION_TOGGLE_MUTE = "com.athaan.app.TOGGLE_MUTE"
        const val ACTION_SET_VOLUME = "com.athaan.app.SET_VOLUME"
        const val EXTRA_PRAYER = "prayer"
        const val EXTRA_MUEZZIN = "muezzin"
        const val EXTRA_VOLUME = "volume"

        private val _state = MutableStateFlow<AdhanPlayback?>(null)
        val state: StateFlow<AdhanPlayback?> = _state.asStateFlow()
    }
}
