package com.athaan.app.data

import android.content.Context
import android.content.SharedPreferences
import com.athaan.app.prayer.AsrMadhab
import com.athaan.app.prayer.CalcMethod
import com.athaan.app.prayer.Prayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val preAlertMinutes: Int = 10,
    val silent: Boolean = false,
    val vibrate: Boolean = true,
    val volume: Float = 0.8f,
    val arabic: Boolean = true,
    val light: Boolean = false,
    val autoDownload: Boolean = true,
) {
    fun place(ar: Boolean = arabic) = if (ar) placeAr else placeEn
    fun zone(): TimeZone = timezoneId?.let { TimeZone.getTimeZone(it) } ?: TimeZone.getDefault()
    fun muezzin(p: Prayer): Muezzin = Muezzins.forPrayer(p, muezzinFor[p])
    fun track(p: Prayer): Track = muezzin(p).trackFor(p)
}

/** Settings stored in SharedPreferences and exposed as a StateFlow. */
class SettingsStore private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("athaan_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val current: AppSettings get() = _state.value

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        save(next)
        _state.value = next
    }

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
            method = runCatching { CalcMethod.valueOf(prefs.getString("method", d.method.name)!!) }.getOrDefault(d.method),
            madhab = runCatching { AsrMadhab.valueOf(prefs.getString("madhab", d.madhab.name)!!) }.getOrDefault(d.madhab),
            muezzinFor = Prayer.adhanPrayers.associateWith { p ->
                Muezzins.forPrayer(p, prefs.getString("muezzin_${p.name}", null)).id
            },
            enabled = Prayer.adhanPrayers.associateWith { prefs.getBoolean("enabled_${it.name}", true) },
            preAlertMinutes = prefs.getInt("preAlert", d.preAlertMinutes),
            silent = prefs.getBoolean("silent", d.silent),
            vibrate = prefs.getBoolean("vibrate", d.vibrate),
            volume = prefs.getFloat("volume", d.volume),
            arabic = prefs.getBoolean("arabic", d.arabic),
            light = prefs.getBoolean("light", d.light),
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
            putInt("preAlert", s.preAlertMinutes)
            putBoolean("silent", s.silent)
            putBoolean("vibrate", s.vibrate)
            putFloat("volume", s.volume)
            putBoolean("arabic", s.arabic)
            putBoolean("light", s.light)
            putBoolean("autoDownload", s.autoDownload)
        }.apply()
    }

    companion object {
        @Volatile private var instance: SettingsStore? = null
        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context).also { instance = it }
            }
    }
}
