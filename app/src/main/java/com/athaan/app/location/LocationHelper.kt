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
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

sealed interface LocateResult {
    data class Found(val location: Location) : LocateResult
    data object NoPermission : LocateResult
    data object ServicesOff : LocateResult
    data object NotFound : LocateResult
}

data class PlaceName(val ar: String, val en: String, val countryCode: String?)

object LocationHelper {
    private const val TAG = "AthaanLocation"

    fun hasPermission(context: Context): Boolean = hasCoarse(context) || hasFine(context)

    private fun hasFine(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasCoarse(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun servicesEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) runCatching { lm.isLocationEnabled }.getOrDefault(true)
        else listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .any { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
    }

    private fun hasPlayServices(context: Context) = runCatching {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }.getOrDefault(false)

    /**
     * Finds the phone's location: Google's fused provider first (fast and works indoors),
     * then Android's own providers, then the last known fix.
     */
    suspend fun locate(context: Context): LocateResult {
        if (!hasPermission(context)) return LocateResult.NoPermission
        if (!servicesEnabled(context)) return LocateResult.ServicesOff

        if (hasPlayServices(context)) {
            fusedLocation(context)?.let { return LocateResult.Found(it) }
        }
        platformLocation(context)?.let { return LocateResult.Found(it) }
        return LocateResult.NotFound
    }

    @SuppressLint("MissingPermission")
    private suspend fun fusedLocation(context: Context): Location? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val priority = if (hasFine(context)) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        // A recent fix is good enough for prayer times.
        runCatching { client.lastLocation.await() }.getOrNull()
            ?.takeIf { System.currentTimeMillis() - it.time < 30 * 60_000L }
            ?.let { return it }
        val cts = CancellationTokenSource()
        val fresh = withTimeoutOrNull(25_000) {
            runCatching {
                client.getCurrentLocation(
                    CurrentLocationRequest.Builder()
                        .setPriority(priority)
                        .setDurationMillis(25_000)
                        .setMaxUpdateAgeMillis(10 * 60_000L)
                        .build(),
                    cts.token,
                ).await(cts)
            }.onFailure { Log.w(TAG, "fused failed", it) }.getOrNull()
        }
        if (fresh == null) cts.cancel()
        return fresh ?: runCatching { client.lastLocation.await() }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    private suspend fun platformLocation(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            if (hasFine(context)) add(LocationManager.GPS_PROVIDER)
        }.filter { p -> runCatching { lm.isProviderEnabled(p) }.getOrDefault(false) }

        for (provider in candidates) {
            val loc = withTimeoutOrNull(20_000) {
                runCatching { currentFrom(context, lm, provider) }
                    .onFailure { Log.w(TAG, "provider $provider failed", it) }
                    .getOrNull()
            }
            if (loc != null) return loc
        }
        val all = candidates + LocationManager.PASSIVE_PROVIDER
        return all.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentFrom(context: Context, lm: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = android.os.CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        lm.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                cont.invokeOnCancellation { lm.removeUpdates(listener) }
            }
        }

    /** City and country for the coordinates via the phone's geocoder; null when offline. */
    suspend fun placeName(context: Context, lat: Double, lng: Double): PlaceName? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        fun lookup(locale: Locale): android.location.Address? = runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, locale).getFromLocation(lat, lng, 1)?.firstOrNull()
        }.getOrNull()

        fun label(a: android.location.Address?, ar: Boolean): String? {
            a ?: return null
            val city = a.locality ?: a.subAdminArea ?: a.adminArea
            return listOfNotNull(city, a.countryName).distinct()
                .joinToString(if (ar) "، " else ", ").ifBlank { null }
        }
        withTimeoutOrNull(10_000) {
            val arA = lookup(Locale("ar"))
            val enA = lookup(Locale.ENGLISH)
            val ar = label(arA, true) ?: label(enA, false) ?: return@withTimeoutOrNull null
            PlaceName(ar, label(enA, false) ?: ar, (enA ?: arA)?.countryCode)
        }
    }
}
