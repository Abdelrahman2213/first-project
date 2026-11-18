package com.athaan.app.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.athaan.app.audio.AudioRepository
import com.athaan.app.data.AppSettings
import com.athaan.app.data.City
import com.athaan.app.data.Country
import com.athaan.app.data.Muezzin
import com.athaan.app.data.Places
import com.athaan.app.data.SettingsStore
import com.athaan.app.data.Track
import com.athaan.app.location.LocateResult
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

enum class LocStatus { IDLE, LOCATING, DENIED, SERVICES_OFF, NOT_FOUND }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore.get(app)
    val audio = AudioRepository.get(app)

    val settings: StateFlow<AppSettings> = store.state
    val downloads = audio.downloads
    val preview = audio.preview
    val playback = AdhanService.state

    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    private val _loc = MutableStateFlow(LocStatus.IDLE)
    val locStatus: StateFlow<LocStatus> = _loc.asStateFlow()

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
        PrayerTimesCalc.forDayOffset(offset, s.lat, s.lng, s.method, s.madhab, now, s.zone())

    fun nextInfo(s: AppSettings, now: Long): NextInfo {
        val days = (-1..1).map { dayTimes(s, it, now) }
        val all = days.flatMap { d -> Prayer.adhanPrayers.map { it to d[it] } }.sortedBy { it.second }
        val next = all.first { it.second > now }
        val prev = all.lastOrNull { it.second <= now }?.second ?: (next.second - 6 * 3_600_000L)
        return NextInfo(next.first, next.second, prev)
    }

    private fun update(t: (AppSettings) -> AppSettings) {
        store.update(t)
        AdhanScheduler.reschedule(getApplication())
    }

    // ---------- Location ----------

    fun clearLocStatus() { _loc.value = LocStatus.IDLE }
    fun setLocDenied() { _loc.value = LocStatus.DENIED }

    /** Finds the phone's location and saves it. Permission must already be granted. */
    fun detectLocation(onDone: (LocStatus) -> Unit = {}) {
        if (_loc.value == LocStatus.LOCATING) return
        _loc.value = LocStatus.LOCATING
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val status = when (val r = runCatching { LocationHelper.locate(ctx) }.getOrDefault(LocateResult.NotFound)) {
                LocateResult.NoPermission -> LocStatus.DENIED
                LocateResult.ServicesOff -> LocStatus.SERVICES_OFF
                LocateResult.NotFound -> LocStatus.NOT_FOUND
                is LocateResult.Found -> {
                    val lat = r.location.latitude
                    val lng = r.location.longitude
                    val geo = LocationHelper.placeName(ctx, lat, lng)
                    val near = if (geo == null) Places.nearest(ctx, lat, lng, null) else null
                    val country = geo?.countryCode ?: near?.country
                    val countryNameAr = country?.let { Country(it, it, 0).name(true) }
                    val countryNameEn = country?.let { Country(it, it, 0).name(false) }
                    val coords = "%.3f, %.3f".format(java.util.Locale.ROOT, lat, lng)
                    val placeAr = geo?.ar ?: near?.let { listOfNotNull(it.name(true), countryNameAr).joinToString("، ") } ?: coords
                    val placeEn = geo?.en ?: near?.let { listOfNotNull(it.name(false), countryNameEn).joinToString(", ") } ?: coords
                    update {
                        it.copy(
                            hasLocation = true, manualLocation = false, timezoneId = null,
                            lat = lat, lng = lng, placeAr = placeAr, placeEn = placeEn,
                            method = if (!it.hasLocation) suggestedMethod(country) else it.method,
                        )
                    }
                    LocStatus.IDLE
                }
            }
            _loc.value = status
            onDone(status)
        }
    }

    fun setCity(c: City) {
        val country = Country(c.country, c.country, 0)
        update {
            it.copy(
                hasLocation = true, manualLocation = true, lat = c.lat, lng = c.lng,
                timezoneId = c.timezone.ifBlank { null },
                placeAr = "${c.name(true)}، ${country.name(true)}",
                placeEn = "${c.name(false)}, ${country.name(false)}",
                method = suggestedMethod(c.country),
            )
        }
        _loc.value = LocStatus.IDLE
    }

    private fun suggestedMethod(countryCode: String?): CalcMethod = when (countryCode?.uppercase()) {
        null -> CalcMethod.EGYPT
        "EG", "SD", "LY", "SY", "LB", "IQ", "JO", "PS", "MY", "SS" -> CalcMethod.EGYPT
        "SA", "YE" -> CalcMethod.UMM_AL_QURA
        "AE" -> CalcMethod.DUBAI
        "KW" -> CalcMethod.KUWAIT
        "QA", "BH", "OM" -> CalcMethod.QATAR
        "PK", "IN", "BD", "AF" -> CalcMethod.KARACHI
        "US", "CA" -> CalcMethod.ISNA
        else -> CalcMethod.MWL
    }

    // ---------- Adhan settings ----------

    fun setMuezzin(p: Prayer, id: String) {
        update { it.copy(muezzinFor = it.muezzinFor + (p to id)) }
        ensureSelectedDownloaded()
    }

    fun assign(m: Muezzin, prayers: Set<Prayer>) {
        val ok = prayers.filter { m.canCall(it) }
        if (ok.isEmpty()) return
        update { s -> s.copy(muezzinFor = s.muezzinFor + ok.associateWith { m.id }) }
        ensureSelectedDownloaded()
    }

    fun setEnabled(p: Prayer, on: Boolean) = update { it.copy(enabled = it.enabled + (p to on)) }
    fun setMethod(m: CalcMethod) = update { it.copy(method = m) }
    fun setMadhab(m: AsrMadhab) = update { it.copy(madhab = m) }
    fun setPreAlert(min: Int) = update { it.copy(preAlertMinutes = min) }
    fun setSilent(on: Boolean) = update { it.copy(silent = on) }
    fun setVibrate(on: Boolean) = update { it.copy(vibrate = on) }
    fun setArabic(on: Boolean) = store.update { it.copy(arabic = on) }
    fun setLight(on: Boolean) = store.update { it.copy(light = on) }
    fun setAutoDownload(on: Boolean) {
        store.update { it.copy(autoDownload = on) }
        ensureSelectedDownloaded()
    }

    fun setVolume(v: Float) {
        store.update { it.copy(volume = v) }
        audio.setPreviewVolume(v)
        if (playback.value != null) {
            sendToService(Intent(getApplication(), AdhanService::class.java)
                .setAction(AdhanService.ACTION_SET_VOLUME).putExtra(AdhanService.EXTRA_VOLUME, v))
        }
    }

    /** Downloads the recordings chosen for the five prayers, for offline use. */
    fun ensureSelectedDownloaded() {
        val s = store.current
        if (!s.autoDownload) return
        Prayer.adhanPrayers.map { s.track(it) }.distinctBy { it.key }.forEach { audio.download(it) }
    }

    // ---------- Audio ----------

    fun togglePreview(t: Track) = audio.togglePreview(t, settings.value.volume)
    fun stopPreview() = audio.stopPreview()
    fun download(t: Track) = audio.download(t)
    fun deleteDownload(t: Track) = audio.delete(t)

    fun testAdhan(p: Prayer) {
        audio.stopPreview()
        val i = Intent(getApplication(), AdhanService::class.java)
            .setAction(AdhanService.ACTION_PLAY)
            .putExtra(AdhanService.EXTRA_PRAYER, p.name)
        runCatching { ContextCompat.startForegroundService(getApplication(), i) }
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
