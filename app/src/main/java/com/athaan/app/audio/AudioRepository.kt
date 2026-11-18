package com.athaan.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.athaan.app.data.CustomMuezzins
import com.athaan.app.data.Muezzins
import com.athaan.app.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Download state for one recording. progress is 0..1 while downloading. */
sealed interface DownloadState {
    data object None : DownloadState
    data class Downloading(val progress: Float) : DownloadState
    data object Done : DownloadState
    data object Failed : DownloadState
}

data class PreviewState(
    /** Track.key of the recording being previewed. */
    val key: String? = null,
    val playing: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val loading: Boolean = false,
    val error: Boolean = false,
)

/**
 * Keeps offline copies of the recordings (app files dir) and plays previews.
 * Download/preview state is keyed by [Track.key] ("afasy" or "afasy_fajr").
 */
class AudioRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val dir = File(appContext.filesDir, "adhans").apply { mkdirs() }

    init {
        // Remove offline copies that no longer match any recording (e.g. from older versions).
        scope.launch(Dispatchers.IO) {
            val keep = HashSet<String>()
            Muezzins.all.forEach { m ->
                if (m.hasRegular) keep += localFile(Track(m, false)).name
                if (m.hasFajr) keep += localFile(Track(m, true)).name
            }
            dir.listFiles()?.forEach { f ->
                if (f.name !in keep && !f.name.startsWith("custom_") && !f.name.endsWith(".part")) {
                    Log.i(TAG, "removing stale ${f.name}"); f.delete()
                }
            }
        }
    }

    private val _downloads = MutableStateFlow(initialDownloads())
    val downloads: StateFlow<Map<String, DownloadState>> = _downloads.asStateFlow()

    private val _preview = MutableStateFlow(PreviewState())
    val preview: StateFlow<PreviewState> = _preview.asStateFlow()

    private var player: MediaPlayer? = null
    private var ticker: Job? = null

    /**
     * Offline copy of a recording. The file name carries a hash of the source URL, so if a
     * recording is ever replaced in the catalog the old copy is never played again.
     * The user's own recordings ("custom:") keep their plain names.
     */
    fun localFile(t: Track): File =
        if (t.url.startsWith(CustomMuezzins.SCHEME)) File(dir, "${t.key}.mp3")
        else File(dir, "${t.key}_${Integer.toHexString(t.url.hashCode())}.mp3")
    fun isDownloaded(t: Track): Boolean = localFile(t).let { it.exists() && it.length() > 10_000 }

    /** Local file path if downloaded, otherwise the online URL. */
    fun source(t: Track): String = if (isDownloaded(t)) localFile(t).absolutePath else t.url

    private fun initialDownloads(): Map<String, DownloadState> {
        val m = HashMap<String, DownloadState>()
        Muezzins.all.forEach { mz ->
            listOf(true, false).forEach { fajr ->
                val t = Track(mz, fajr)
                if ((fajr && mz.hasFajr) || (!fajr && mz.hasRegular)) {
                    m[t.key] = if (isDownloaded(t)) DownloadState.Done else DownloadState.None
                }
            }
        }
        return m
    }

    private fun setDl(key: String, s: DownloadState) = _downloads.update { it + (key to s) }

    fun download(t: Track) {
        val cur = _downloads.value[t.key]
        if (cur is DownloadState.Downloading) return
        if (isDownloaded(t)) { setDl(t.key, DownloadState.Done); return }
        setDl(t.key, DownloadState.Downloading(0f))
        scope.launch {
            val ok = withContext(Dispatchers.IO) { downloadBlocking(t) { p -> setDl(t.key, DownloadState.Downloading(p)) } }
            setDl(t.key, if (ok) DownloadState.Done else DownloadState.Failed)
        }
    }

    /** Synchronous download, safe to call from a background thread. */
    fun downloadBlocking(t: Track, onProgress: (Float) -> Unit = {}): Boolean {
        val tmp = File(dir, "${t.key}.part")
        if (t.url.startsWith(CustomMuezzins.SCHEME)) return isDownloaded(t)
        return try {
            var url = URL(t.url)
            var conn: HttpURLConnection
            var redirects = 0
            while (true) {
                conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.instanceFollowRedirects = true
                val code = conn.responseCode
                if (code in 300..399 && redirects < 5) {
                    url = URL(url, conn.getHeaderField("Location"))
                    conn.disconnect(); redirects++; continue
                }
                if (code != 200) { conn.disconnect(); return false }
                break
            }
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    var read: Int
                    var sum = 0L
                    var lastReport = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        sum += read
                        if (total > 0 && sum - lastReport > 64 * 1024) {
                            lastReport = sum
                            onProgress((sum.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                    if (total > 0 && sum < total) throw java.io.IOException("truncated")
                }
            }
            conn.disconnect()
            tmp.renameTo(localFile(t))
        } catch (e: Exception) {
            Log.w(TAG, "download failed ${t.key}", e)
            tmp.delete()
            false
        }
    }

    fun delete(t: Track) {
        if (_preview.value.key == t.key) stopPreview()
        localFile(t).delete()
        setDl(t.key, DownloadState.None)
    }

    // ---------- Preview playback ----------

    fun togglePreview(t: Track, volume: Float) {
        val st = _preview.value
        val p = player
        if (st.key == t.key && p != null && !st.loading) {
            if (p.isPlaying) { p.pause(); _preview.value = st.copy(playing = false) }
            else { p.start(); _preview.value = st.copy(playing = true); startTicker() }
            return
        }
        stopPreview()
        _preview.value = PreviewState(key = t.key, loading = true)
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(source(t))
            mp.setVolume(volume, volume)
            mp.setOnPreparedListener {
                if (player !== it) return@setOnPreparedListener
                it.start()
                _preview.value = PreviewState(t.key, playing = true, durationMs = it.duration)
                startTicker()
            }
            mp.setOnCompletionListener { stopPreview() }
            mp.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "preview error $what/$extra for ${t.key}")
                releasePlayer()
                _preview.value = PreviewState(key = t.key, error = true)
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.w(TAG, "preview failed ${t.key}", e)
            releasePlayer()
            _preview.value = PreviewState(key = t.key, error = true)
        }
    }

    fun seekPreview(fraction: Float) {
        val p = player ?: return
        val d = _preview.value.durationMs
        if (d > 0) runCatching { p.seekTo((d * fraction).toInt()) }
    }

    fun setPreviewVolume(v: Float) {
        runCatching { player?.setVolume(v, v) }
    }

    private fun releasePlayer() {
        ticker?.cancel()
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
    }

    fun stopPreview() {
        releasePlayer()
        _preview.value = PreviewState()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val p = player ?: break
                runCatching {
                    _preview.value = _preview.value.copy(positionMs = p.currentPosition, playing = p.isPlaying)
                }
                delay(250)
            }
        }
    }

    companion object {
        private const val TAG = "AthaanAudio"
        @Volatile private var instance: AudioRepository? = null
        fun get(context: Context): AudioRepository =
            instance ?: synchronized(this) {
                instance ?: AudioRepository(context).also { instance = it }
            }
    }
}
