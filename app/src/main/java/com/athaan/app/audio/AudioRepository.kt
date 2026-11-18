package com.athaan.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.athaan.app.data.Muezzin
import com.athaan.app.data.Muezzins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val muezzinId: String? = null,
    val playing: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val loading: Boolean = false,
)

/**
 * Keeps the offline copies of the recordings (app files dir) and plays previews.
 */
class AudioRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val dir = File(appContext.filesDir, "adhans").apply { mkdirs() }

    private val _downloads = MutableStateFlow<Map<String, DownloadState>>(initialDownloads())
    val downloads: StateFlow<Map<String, DownloadState>> = _downloads.asStateFlow()

    private val _preview = MutableStateFlow(PreviewState())
    val preview: StateFlow<PreviewState> = _preview.asStateFlow()

    private var player: MediaPlayer? = null
    private var ticker: Job? = null

    fun localFile(m: Muezzin): File = File(dir, "${m.id}.mp3")
    fun isDownloaded(m: Muezzin): Boolean = localFile(m).let { it.exists() && it.length() > 10_000 }

    /** Local file path if downloaded, otherwise the online URL. */
    fun source(m: Muezzin): String = if (isDownloaded(m)) localFile(m).absolutePath else m.url

    private fun initialDownloads(): Map<String, DownloadState> =
        Muezzins.all.associate { it.id to if (isDownloaded(it)) DownloadState.Done else DownloadState.None }

    private fun setDl(id: String, s: DownloadState) {
        _downloads.value = _downloads.value + (id to s)
    }

    fun download(m: Muezzin) {
        val cur = _downloads.value[m.id]
        if (cur is DownloadState.Downloading || isDownloaded(m)) return
        setDl(m.id, DownloadState.Downloading(0f))
        scope.launch {
            val ok = withContext(Dispatchers.IO) { downloadBlocking(m) { p -> setDl(m.id, DownloadState.Downloading(p)) } }
            setDl(m.id, if (ok) DownloadState.Done else DownloadState.Failed)
        }
    }

    /** Synchronous download, safe to call from a background thread. */
    fun downloadBlocking(m: Muezzin, onProgress: (Float) -> Unit = {}): Boolean {
        val tmp = File(dir, "${m.id}.part")
        return try {
            var url = URL(m.url)
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
                }
            }
            conn.disconnect()
            tmp.renameTo(localFile(m))
        } catch (e: Exception) {
            tmp.delete()
            false
        }
    }

    fun delete(m: Muezzin) {
        localFile(m).delete()
        setDl(m.id, DownloadState.None)
    }

    // ---------- Preview playback ----------

    fun togglePreview(m: Muezzin, volume: Float) {
        val st = _preview.value
        if (st.muezzinId == m.id && player != null) {
            val p = player!!
            if (p.isPlaying) { p.pause(); _preview.value = st.copy(playing = false) }
            else { p.start(); _preview.value = st.copy(playing = true); startTicker() }
            return
        }
        stopPreview()
        _preview.value = PreviewState(muezzinId = m.id, loading = true)
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(source(m))
            mp.setVolume(volume, volume)
            mp.setOnPreparedListener {
                if (player !== it) return@setOnPreparedListener
                it.start()
                _preview.value = PreviewState(m.id, playing = true, durationMs = it.duration)
                startTicker()
            }
            mp.setOnCompletionListener { stopPreview() }
            mp.setOnErrorListener { _, _, _ -> stopPreview(); true }
            mp.prepareAsync()
        } catch (e: Exception) {
            stopPreview()
        }
    }

    fun seekPreview(fraction: Float) {
        val p = player ?: return
        val d = _preview.value.durationMs
        if (d > 0) p.seekTo((d * fraction).toInt())
    }

    fun stopPreview() {
        ticker?.cancel()
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
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
        @Volatile private var instance: AudioRepository? = null
        fun get(context: Context): AudioRepository =
            instance ?: synchronized(this) {
                instance ?: AudioRepository(context).also { instance = it }
            }
    }
}
