package com.athaan.app.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.athaan.app.audio.AudioRepository
import com.athaan.app.data.AppSettings
import com.athaan.app.data.City
import com.athaan.app.data.Muezzin
import com.athaan.app.data.Muezzins
import com.athaan.app.data.SettingsStore
import com.athaan.app.location.LocationHelper
import com.athaan.app.prayer.AsrMadhab
import com.athaan.app.prayer.CalcMethod
import com.athaan.app.prayer.DayTimes
import com.athaan.app.prayer.Prayer
import com.athaan.app.prayer.PrayerTimesCalc
import com.athaan.app.schedule.AdhanScheduler
import com.athaan.app.service.AdhanService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The next prayer, the one before it, and both times — drives the home hero. */
data class NextInfo(val prayer: Prayer, val at: Long, val prevAt: Long)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore.get(app)
    val audio = AudioRepository.get(app)

    val settings: StateFlow<AppSettings> = store.state
    val downloads = audio.downloads
    val preview = audio.preview
    val playback = AdhanService.state

    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    private val _locating = MutableStateFlow(false)
    val locating: StateFlow<Boolean> = _locating.asStateFlow()

    private val _locationError = MutableStateFlow(false)
    val locationError: StateFlow<Boolean> = _locationError.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                _now.value = System.currentTimeMillis()
                delay(1000L - System.currentTimeMillis() % 1000L)
            }
        }
        ensureSelectedDownloaded()
    }

    fun dayTimes(s: AppSettings, offset: Int, now: Long): DayTimes =
        PrayerTimesCalc.forDayOffset(offset, s.lat, s.lng, s.method, s.madhab, now)

    fun nextInfo(s: AppSettings, now: Long): NextInfo {
        val days = (-1..1).map { dayTimes(s, it, now) }
        val all = days.flatMap { d -> Prayer.adhanPrayers.map { it to d[it] } }.sortedBy { it.second }
        val next = all.first { it.second > now }
        val prev = all.lastOrNull { it.second <= now }?.second ?: (next.second - 6 * 3_600_000L)
        return NextInfo(next.first, next.second, prev)
    }

    private fun changed() {
        AdhanScheduler.reschedule(getApplication())
    }

    private fun update(t: (AppSettings) -> AppSettings) {
        store.update(t)
        changed()
    }

    // ---------- Location ----------

    fun detectLocation(onDone: (Boolean) -> Unit = {}) {
        if (_locating.value) return
        _locating.value = true
        _locationError.value = false
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val loc = LocationHelper.currentLocation(ctx)
            if (loc == null) {
                _locating.value = false
                _locationError.value = true
                onDone(false)
                return@launch
            }
            val names = LocationHelper.placeName(ctx, loc.latitude, loc.longitude)
            val coords = "%.3f, %.3f".format(loc.latitude, loc.longitude)
            update {
                it.copy(
                    hasLocation = true, manualLocation = false,
                    lat = loc.latitude, lng = loc.longitude,
                    placeAr = names?.first ?: coords, placeEn = names?.second ?: coords,
                    method = if (!it.hasLocation) suggestedMethod(names?.second) else it.method,
                )
            }
            _locating.value = false
            onDone(true)
        }
    }

    fun setCity(c: City) = update {
        it.copy(
            hasLocation = true, manualLocation = true, lat = c.lat, lng = c.lng,
            placeAr = "${c.ar}، ${c.countryAr}", placeEn = "${c.en}, ${c.countryEn}",
            method = if (!it.hasLocation) suggestedMethod(c.countryEn) else it.method,
        )
    }

    private fun suggestedMethod(place: String?): CalcMethod {
        val p = place ?: return CalcMethod.EGYPT
        return when {
            p.contains("Egypt") || p.contains("Sudan") || p.contains("Libya") -> CalcMethod.EGYPT
            p.contains("Saudi") -> CalcMethod.UMM_AL_QURA
            p.contains("Emirates") || p.contains("UAE") -> CalcMethod.DUBAI
            p.contains("Kuwait") -> CalcMethod.KUWAIT
            p.contains("Qatar") -> CalcMethod.QATAR
            p.contains("Pakistan") || p.contains("India") || p.contains("Bangladesh") -> CalcMethod.KARACHI
            p.contains("United States") || p.contains("Canada") -> CalcMethod.ISNA
            else -> CalcMethod.MWL
        }
    }

    // ---------- Adhan settings ----------

    fun setMuezzin(p: Prayer, id: String) {
        update { it.copy(muezzinFor = it.muezzinFor + (p to id)) }
        ensureSelectedDownloaded()
    }

    fun assign(m: Muezzin, prayers: Set<Prayer>) {
        if (prayers.isEmpty()) return
        update { s -> s.copy(muezzinFor = s.muezzinFor + prayers.associateWith { m.id }) }
        ensureSelectedDownloaded()
    }

    fun setEnabled(p: Prayer, on: Boolean) = update { it.copy(enabled = it.enabled + (p to on)) }
    fun setMethod(m: CalcMethod) = update { it.copy(method = m) }
    fun setMadhab(m: AsrMadhab) = update { it.copy(madhab = m) }
    fun setPreAlert(min: Int) = update { it.copy(preAlertMinutes = min) }
    fun setSilent(on: Boolean) = update { it.copy(silent = on) }
    fun setVibrate(on: Boolean) = update { it.copy(vibrate = on) }
    fun setArabic(on: Boolean) = store.update { it.copy(arabic = on) }
    fun setAutoDownload(on: Boolean) {
        store.update { it.copy(autoDownload = on) }
        ensureSelectedDownloaded()
    }

    fun setVolume(v: Float) {
        store.update { it.copy(volume = v) }
        if (playback.value != null) {
            sendToService(Intent(getApplication(), AdhanService::class.java)
                .setAction(AdhanService.ACTION_SET_VOLUME).putExtra(AdhanService.EXTRA_VOLUME, v))
        }
    }

    /** Downloads the recordings chosen for the five prayers, for offline use. */
    fun ensureSelectedDownloaded() {
        val s = store.current
        if (!s.autoDownload) return
        s.muezzinFor.values.toSet().forEach { audio.download(Muezzins.byId(it)) }
    }

    // ---------- Audio ----------

    fun togglePreview(m: Muezzin) = audio.togglePreview(m, settings.value.volume)
    fun stopPreview() = audio.stopPreview()
    fun download(m: Muezzin) = audio.download(m)
    fun deleteDownload(m: Muezzin) = audio.delete(m)

    fun testAdhan(p: Prayer) {
        audio.stopPreview()
        val i = Intent(getApplication(), AdhanService::class.java)
            .setAction(AdhanService.ACTION_PLAY)
            .putExtra(AdhanService.EXTRA_PRAYER, p.name)
        ContextCompat.startForegroundService(getApplication(), i)
    }

    fun stopAdhan() = sendToService(Intent(getApplication(), AdhanService::class.java).setAction(AdhanService.ACTION_STOP))
    fun toggleMute() = sendToService(Intent(getApplication(), AdhanService::class.java).setAction(AdhanService.ACTION_TOGGLE_MUTE))

    private fun sendToService(i: Intent) {
        runCatching { getApplication<Application>().startService(i) }
    }

    override fun onCleared() {
        audio.stopPreview()
        super.onCleared()
    }
}
