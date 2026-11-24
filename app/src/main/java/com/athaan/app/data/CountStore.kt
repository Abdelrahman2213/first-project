package com.athaan.app.data

import android.content.Context
import java.util.Calendar

/**
 * Remembers what the user has counted: progress through each adhkar set (fresh every day, so
 * the morning adhkar start over tomorrow) and the tasbih counters, which keep their totals.
 */
class CountStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("athaan_counts", Context.MODE_PRIVATE)

    private fun today(): String {
        val c = Calendar.getInstance()
        return "%04d%02d%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** Counts for each dhikr of [set] today. */
    fun adhkar(set: AdhkarSet, size: Int): IntArray {
        val raw = prefs.getString("adhkar_${set.name}", null)
        val out = IntArray(size)
        if (raw == null || raw.substringBefore('|') != today()) return out
        raw.substringAfter('|').split(',').forEachIndexed { i, v -> if (i < size) out[i] = v.toIntOrNull() ?: 0 }
        return out
    }

    fun saveAdhkar(set: AdhkarSet, counts: IntArray) {
        prefs.edit().putString("adhkar_${set.name}", today() + "|" + counts.joinToString(",")).apply()
    }

    /** Days on which the whole set was completed, as yyyyMMdd. */
    fun completedDays(set: AdhkarSet): Set<String> = prefs.getStringSet("done_${set.name}", emptySet()) ?: emptySet()

    fun markCompleted(set: AdhkarSet) {
        val days = completedDays(set).toMutableSet().apply { add(today()) }
        // Keep the last year only.
        val keep = days.sortedDescending().take(400).toSet()
        prefs.edit().putStringSet("done_${set.name}", keep).apply()
    }

    /** Consecutive days, ending today or yesterday, on which [set] was completed. */
    fun streak(set: AdhkarSet): Int {
        val days = completedDays(set)
        val c = Calendar.getInstance()
        fun key() = "%04d%02d%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        if (key() !in days) c.add(Calendar.DAY_OF_MONTH, -1)
        var n = 0
        while (key() in days) { n++; c.add(Calendar.DAY_OF_MONTH, -1) }
        return n
    }

    fun tasbihCount(phrase: Int): Int = prefs.getInt("tasbih_$phrase", 0)
    fun tasbihTotal(phrase: Int): Int = prefs.getInt("tasbih_total_$phrase", 0)
    fun tasbihToday(): Int = if (prefs.getString("tasbih_day", "") == today()) prefs.getInt("tasbih_today", 0) else 0

    /** One more tasbih for [phrase]: the current count, its all-time total and today's total. */
    fun addTasbih(phrase: Int, current: Int) {
        val todayCount = tasbihToday() + 1
        prefs.edit()
            .putInt("tasbih_$phrase", current)
            .putInt("tasbih_total_$phrase", tasbihTotal(phrase) + 1)
            .putString("tasbih_day", today())
            .putInt("tasbih_today", todayCount)
            .apply()
    }

    /** Takes back the last tasbih of [phrase] (a mistaken tap). */
    fun undoTasbih(phrase: Int, current: Int) {
        prefs.edit()
            .putInt("tasbih_$phrase", current)
            .putInt("tasbih_total_$phrase", (tasbihTotal(phrase) - 1).coerceAtLeast(0))
            .putInt("tasbih_today", (tasbihToday() - 1).coerceAtLeast(0))
            .apply()
    }

    fun resetTasbih(phrase: Int) { prefs.edit().putInt("tasbih_$phrase", 0).apply() }

    var tasbihPhrase: Int
        get() = prefs.getInt("tasbih_phrase", 0)
        set(v) { prefs.edit().putInt("tasbih_phrase", v).apply() }

    var tasbihTarget: Int
        get() = prefs.getInt("tasbih_target", 33)
        set(v) { prefs.edit().putInt("tasbih_target", v).apply() }

    companion object {
        @Volatile private var instance: CountStore? = null
        fun get(context: Context): CountStore = instance ?: synchronized(this) { instance ?: CountStore(context).also { instance = it } }
    }
}
