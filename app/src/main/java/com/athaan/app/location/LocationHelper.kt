package com.athaan.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

object LocationHelper {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Best available location: a fresh fix (up to 15 s), else the last known one. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context): Location? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        val freshProvider = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER }
            ?: return last

        val fresh = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    lm.getCurrentLocation(freshProvider, null, ContextCompat.getMainExecutor(context)) { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            if (cont.isActive) cont.resume(location)
                        }
                    }
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(freshProvider, listener, Looper.getMainLooper())
                    cont.invokeOnCancellation { lm.removeUpdates(listener) }
                }
            }
        }
        return fresh ?: last
    }

    /** City/country name for the coordinates, or null if offline. Returns (arabic, english). */
    suspend fun placeName(context: Context, lat: Double, lng: Double): Pair<String, String>? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        fun name(locale: Locale): String? = runCatching {
            @Suppress("DEPRECATION")
            val a = Geocoder(context, locale).getFromLocation(lat, lng, 1)?.firstOrNull() ?: return@runCatching null
            val city = a.locality ?: a.subAdminArea ?: a.adminArea
            listOfNotNull(city, a.countryName).distinct().joinToString(if (locale.language == "ar") "، " else ", ")
                .ifBlank { null }
        }.getOrNull()
        val ar = name(Locale("ar")) ?: return@withContext null
        val en = name(Locale.ENGLISH) ?: ar
        ar to en
    }
}
