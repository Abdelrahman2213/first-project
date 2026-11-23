package com.athaan.app.data

import android.content.Context
import android.content.SharedPreferences
import com.athaan.app.prayer.AsrMadhab
import com.athaan.app.prayer.CalcMethod
import com.athaan.app.prayer.Prayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

data class AppSettings(
    val hasLocation: Boolean = false,
    val lat: Double = 30.0444,
    val lng: Double = 31.2357,
    val placeAr: String = "القاهرة، مصر",
    val placeEn: String = "Cairo, Egypt",
    val manualLocation: Boolean = false,
    /** IANA zone of a manually chosen city; null = the phone's own time zone. */
    val timezoneId: String? = null,
    val method: CalcMethod = CalcMethod.EGYPT,
    val madhab: AsrMadhab = AsrMadhab.STANDARD,
    val muezzinFor: Map<Prayer, String> = Muezzins.DEFAULTS,
    val enabled: Map<Prayer, Boolean> = Prayer.adhanPrayers.associateWith { true },
    /** Minutes added to (or taken from) each calculated time, to match the local mosque. */
    val offsets: Map<Prayer, Int> = Prayer.values().associateWith { 0 },
    /** Minutes after the adhan for the iqama reminder; 0 = off. */
    val iqama: Map<Prayer, Int> = Prayer.adhanPrayers.associateWith { 0 },
    val preAlertMinutes: Int = 10,
    val silent: Boolean = false,
    val vibrate: Boolean = true,
    val volume: Float = 0.8f,
    val adhanLength: AdhanLength = AdhanLength.FULL,
    val fadeIn: Boolean = true,
    // Ways to silence a playing adhan
    val volumeKeysPause: Boolean = true,
    val screenOffPause: Boolean = true,
    val flipToSilence: Boolean = true,
    // Quiet ("work") hours: notification only, no sound
    val quietEnabled: Boolean = false,
    val quietStart: Int = 9 * 60,
    val quietEnd: Int = 17 * 60,
    val quietDays: Set<Int> = setOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY),
    // Reminders
    val fridayMinutes: Int = 30,
    val kahfReminder: Boolean = true,
    val adhkarReminders: Boolean = false,
    val suhoorMinutes: Int = 45,
    val persistentCountdown: Boolean = false,
    // Look
    val arabic: Boolean = true,
    val light: Boolean = false,
    val palette: LightPalette = LightPalette.IVORY,
    val pattern: HeroPattern = HeroPattern.MOSQUE,
    val autoDownload: Boolean = true,
) {
    fun place(ar: Boolean = arabic) = if (ar) placeAr else placeEn
    fun zone(): TimeZone = timezoneId?.let { TimeZone.getTimeZone(it) } ?: TimeZone.getDefault()
    fun muezzin(p: Prayer): Muezzin = Muezzins.forPrayer(p, muezzinFor[p])
    fun track(p: Prayer): Track = muezzin(p).trackFor(p)

    /** True when [millis] falls inside the user's quiet hours. */
    fun inQuietHours(millis: Long): Boolean {
        if (!quietEnabled) return false
        val c = Calendar.getInstance(zone()).apply { timeInMillis = millis }
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val day = c.get(Calendar.DAY_OF_WEEK)
        return if (quietStart <= quietEnd) {
            day in quietDays && m >= quietStart && m < quietEnd
        } else {
            // Over midnight: the evening part belongs to the selected day.
            (day in quietDays && m >= quietStart) ||
                (m < quietEnd && (((day + 5) % 7) + 1) in quietDays)
        }
    }
}

/** Settings stored in SharedPreferences and exposed as a StateFlow. */
class SettingsStore private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val current: AppSettings get() = _state.value

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        save(next)
        _state.value = next
    }

    private inline fun <reified E : Enum<E>> enumPref(key: String, def: E): E =
        runCatching { enumValueOf<E>(prefs.getString(key, def.name)!!) }.getOrDefault(def)

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            hasLocation = prefs.getBoolean("hasLocation", false),
            lat = prefs.getString("latS", null)?.toDoubleOrNull() ?: prefs.getFloat("lat", d.lat.toFloat()).toDouble(),
            lng = prefs.getString("lngS", null)?.toDoubleOrNull() ?: prefs.getFloat("lng", d.lng.toFloat()).toDouble(),
            placeAr = prefs.getString("placeAr", d.placeAr) ?: d.placeAr,
            placeEn = prefs.getString("placeEn", d.placeEn) ?: d.placeEn,
            manualLocation = prefs.getBoolean("manualLocation", false),
            timezoneId = prefs.getString("timezoneId", null),
            method = enumPref("method", d.method),
            madhab = enumPref("madhab", d.madhab),
            muezzinFor = Prayer.adhanPrayers.associateWith { p ->
                Muezzins.forPrayer(p, prefs.getString("muezzin_${p.name}", null)).id
            },
            enabled = Prayer.adhanPrayers.associateWith { prefs.getBoolean("enabled_${it.name}", true) },
            offsets = Prayer.values().associateWith { prefs.getInt("offset_${it.name}", 0) },
            iqama = Prayer.adhanPrayers.associateWith { prefs.getInt("iqama_${it.name}", 0) },
            preAlertMinutes = prefs.getInt("preAlert", d.preAlertMinutes),
            silent = prefs.getBoolean("silent", d.silent),
            vibrate = prefs.getBoolean("vibrate", d.vibrate),
            volume = prefs.getFloat("volume", d.volume),
            adhanLength = enumPref("adhanLength", d.adhanLength),
            fadeIn = prefs.getBoolean("fadeIn", d.fadeIn),
            volumeKeysPause = prefs.getBoolean("volumeKeysPause", d.volumeKeysPause),
            screenOffPause = prefs.getBoolean("screenOffPause", d.screenOffPause),
            flipToSilence = prefs.getBoolean("flipToSilence", d.flipToSilence),
            quietEnabled = prefs.getBoolean("quietEnabled", d.quietEnabled),
            quietStart = prefs.getInt("quietStart", d.quietStart),
            quietEnd = prefs.getInt("quietEnd", d.quietEnd),
            quietDays = prefs.getString("quietDays", null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.toSet() ?: d.quietDays,
            fridayMinutes = prefs.getInt("fridayMinutes", d.fridayMinutes),
            kahfReminder = prefs.getBoolean("kahfReminder", d.kahfReminder),
            adhkarReminders = prefs.getBoolean("adhkarReminders", d.adhkarReminders),
            suhoorMinutes = prefs.getInt("suhoorMinutes", d.suhoorMinutes),
            persistentCountdown = prefs.getBoolean("persistentCountdown", d.persistentCountdown),
            arabic = prefs.getBoolean("arabic", d.arabic),
            light = prefs.getBoolean("light", d.light),
            palette = enumPref("palette", d.palette),
            pattern = enumPref("pattern", d.pattern),
            autoDownload = prefs.getBoolean("autoDownload", d.autoDownload),
        )
    }

    private fun save(s: AppSettings) {
        prefs.edit().apply {
            putBoolean("hasLocation", s.hasLocation)
            putString("latS", s.lat.toString())
            putString("lngS", s.lng.toString())
            putString("placeAr", s.placeAr)
            putString("placeEn", s.placeEn)
            putBoolean("manualLocation", s.manualLocation)
            if (s.timezoneId != null) putString("timezoneId", s.timezoneId) else remove("timezoneId")
            putString("method", s.method.name)
            putString("madhab", s.madhab.name)
            s.muezzinFor.forEach { (p, id) -> putString("muezzin_${p.name}", id) }
            s.enabled.forEach { (p, on) -> putBoolean("enabled_${p.name}", on) }
            s.offsets.forEach { (p, m) -> putInt("offset_${p.name}", m) }
            s.iqama.forEach { (p, m) -> putInt("iqama_${p.name}", m) }
            putInt("preAlert", s.preAlertMinutes)
            putBoolean("silent", s.silent)
            putBoolean("vibrate", s.vibrate)
            putFloat("volume", s.volume)
            putString("adhanLength", s.adhanLength.name)
            putBoolean("fadeIn", s.fadeIn)
            putBoolean("volumeKeysPause", s.volumeKeysPause)
            putBoolean("screenOffPause", s.screenOffPause)
            putBoolean("flipToSilence", s.flipToSilence)
            putBoolean("quietEnabled", s.quietEnabled)
            putInt("quietStart", s.quietStart)
            putInt("quietEnd", s.quietEnd)
            putString("quietDays", s.quietDays.joinToString(","))
            putInt("fridayMinutes", s.fridayMinutes)
            putBoolean("kahfReminder", s.kahfReminder)
            putBoolean("adhkarReminders", s.adhkarReminders)
            putInt("suhoorMinutes", s.suhoorMinutes)
            putBoolean("persistentCountdown", s.persistentCountdown)
            putBoolean("arabic", s.arabic)
            putBoolean("light", s.light)
            putString("palette", s.palette.name)
            putString("pattern", s.pattern.name)
            putBoolean("autoDownload", s.autoDownload)
        }.apply()
    }

    // ---------- Backup ----------

    /** All settings as JSON (the user's own audio files are not included). */
    fun exportJson(): String {
        val o = JSONObject().put("app", "athaan").put("version", 1)
        val values = JSONObject()
        prefs.all.forEach { (k, v) ->
            when (v) {
                is Boolean -> values.put(k, JSONObject().put("t", "b").put("v", v))
                is Int -> values.put(k, JSONObject().put("t", "i").put("v", v))
                is Long -> values.put(k, JSONObject().put("t", "l").put("v", v))
                is Float -> values.put(k, JSONObject().put("t", "f").put("v", v.toDouble()))
                is String -> values.put(k, JSONObject().put("t", "s").put("v", v))
            }
        }
        return o.put("settings", values).toString(2)
    }

    /** Restores settings exported by [exportJson]. Returns false if the file isn't one. */
    @Synchronized
    fun importJson(json: String): Boolean = runCatching {
        val o = JSONObject(json)
        if (o.optString("app") != "athaan") return false
        val values = o.getJSONObject("settings")
        val e = prefs.edit().clear()
        values.keys().forEach { k ->
            val item = values.getJSONObject(k)
            when (item.getString("t")) {
                "b" -> e.putBoolean(k, item.getBoolean("v"))
                "i" -> e.putInt(k, item.getInt("v"))
                "l" -> e.putLong(k, item.getLong("v"))
                "f" -> e.putFloat(k, item.getDouble("v").toFloat())
                "s" -> e.putString(k, item.getString("v"))
            }
        }
        e.commit()
        _state.value = load()
        true
    }.getOrDefault(false)

    companion object {
        private const val PREFS = "athaan_settings"
        @Volatile private var instance: SettingsStore? = null
        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context).also { instance = it }
            }
    }
}
