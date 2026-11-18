package com.athaan.app.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Muezzins the user added from audio files on the phone. The recordings are copied into the
 * app's own storage (the same folder as downloaded recordings), so they always work offline.
 */
object CustomMuezzins {
    private const val TAG = "AthaanCustom"
    private const val PREFS = "athaan_custom"
    const val SCHEME = "custom:"

    /** Observable list (Compose re-reads it when it changes). */
    val list = mutableStateListOf<Muezzin>()
    @Volatile private var loaded = false

    private fun dir(context: Context) = File(context.applicationContext.filesDir, "adhans").apply { mkdirs() }

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("list", "[]") ?: "[]"
        runCatching {
            val arr = JSONArray(raw)
            val out = ArrayList<Muezzin>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.getString("id")
                val hasReg = File(dir(context), "$id.mp3").exists()
                val hasFajr = File(dir(context), "${id}_fajr.mp3").exists()
                if (!hasReg && !hasFajr) continue
                out += make(id, o.getString("name"), o.optString("note"), hasReg, hasFajr)
            }
            list.clear(); list.addAll(out)
        }.onFailure { Log.w(TAG, "load failed", it) }
    }

    private fun make(id: String, name: String, note: String, hasReg: Boolean, hasFajr: Boolean) = Muezzin(
        id = id, nameAr = name, nameEn = name,
        originAr = note.ifBlank { "صوت أضفته" }, originEn = note.ifBlank { "Added by you" },
        region = Region.CUSTOM,
        url = if (hasReg) "$SCHEME$id" else null,
        fajrUrl = if (hasFajr) "$SCHEME${id}_fajr" else null,
    )

    private fun save(context: Context) {
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(JSONObject().put("id", m.id).put("name", m.nameAr).put("note", if (m.originAr == "صوت أضفته") "" else m.originAr))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("list", arr.toString()).apply()
    }

    sealed interface AddResult {
        data class Added(val muezzin: Muezzin) : AddResult
        data object NoFile : AddResult
        data object NotAudio : AddResult
        data object Failed : AddResult
    }

    /** Copies the picked files and adds the muezzin. At least one of [regular] / [fajr] is required. */
    suspend fun add(context: Context, name: String, note: String, regular: Uri?, fajr: Uri?): AddResult = withContext(Dispatchers.IO) {
        if (regular == null && fajr == null) return@withContext AddResult.NoFile
        val id = "custom_" + UUID.randomUUID().toString().take(8)
        val d = dir(context)
        val copied = mutableListOf<File>()
        try {
            for ((uri, suffix) in listOf(regular to "", fajr to "_fajr")) {
                uri ?: continue
                val f = File(d, "$id$suffix.mp3")
                context.contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
                    ?: return@withContext AddResult.Failed
                copied += f
                if (!isAudio(f)) { copied.forEach { it.delete() }; return@withContext AddResult.NotAudio }
            }
            val m = make(id, name.trim(), note.trim(), regular != null, fajr != null)
            withContext(Dispatchers.Main) { list.add(m); save(context) }
            AddResult.Added(m)
        } catch (e: Exception) {
            Log.w(TAG, "add failed", e)
            copied.forEach { it.delete() }
            AddResult.Failed
        }
    }

    fun remove(context: Context, id: String) {
        File(dir(context), "$id.mp3").delete()
        File(dir(context), "${id}_fajr.mp3").delete()
        list.removeAll { it.id == id }
        save(context)
    }

    private fun isAudio(f: File): Boolean = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(f.absolutePath)
            val hasAudio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            hasAudio == "yes" && dur > 3_000
        } finally { r.release() }
    }.getOrDefault(false)

    /** Debug builds only: adds a custom muezzin from an existing file (used by the UI test). */
    suspend fun addFromFile(context: Context, name: String, file: File, fajr: Boolean): Muezzin? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        val id = "custom_" + UUID.randomUUID().toString().take(8)
        file.copyTo(File(dir(context), if (fajr) "${id}_fajr.mp3" else "$id.mp3"), overwrite = true)
        val m = make(id, name, "", !fajr, fajr)
        withContext(Dispatchers.Main) { list.add(m); save(context) }
        m
    }
}
