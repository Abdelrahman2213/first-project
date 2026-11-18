package com.athaan.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.athaan.app.data.Muezzins
import com.athaan.app.location.LocationHelper
import com.athaan.app.prayer.Prayer
import com.athaan.app.schedule.AdhanScheduler
import com.athaan.app.ui.AppViewModel
import com.athaan.app.ui.LocStatus
import com.athaan.app.ui.components.BottomNav
import com.athaan.app.ui.components.Tab
import com.athaan.app.ui.screens.AddMuezzinScreen
import com.athaan.app.ui.screens.CityPickerScreen
import com.athaan.app.ui.screens.HomeScreen
import com.athaan.app.ui.screens.LibraryScreen
import com.athaan.app.ui.screens.MuezzinScreen
import com.athaan.app.ui.screens.OnboardingScreen
import com.athaan.app.ui.screens.PerPrayerScreen
import com.athaan.app.ui.screens.PlayingScreen
import com.athaan.app.ui.screens.SettingsScreen
import com.athaan.app.ui.theme.AC
import com.athaan.app.ui.theme.AthaanTheme
import com.athaan.app.ui.theme.ThemeAnim
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch

/** Screens pushed on top of the tabs. */
sealed interface Route {
    data class Muezzin(val id: String) : Route
    data class Pick(val prayer: Prayer) : Route
    data object CityPicker : Route
    data class AddCustom(val prefill: String?) : Route
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    /** Debug-build-only hooks used by the automated UI test (see .github/workflows). */
    val debugRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeAnim.t = if (vm.settings.value.light) 1f else 0f
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        // While the adhan plays, the stop screen may show over the lock screen.
        lifecycleScope.launch {
            vm.playback.collect { pb ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    setShowWhenLocked(pb != null)
                    setTurnScreenOn(pb != null)
                }
            }
        }
        handleDebug(intent)
        setContent {
            val s by vm.settings.collectAsState()
            AthaanTheme(arabic = s.arabic, light = s.light) {
                val view = LocalView.current
                SideEffect {
                    val c = WindowCompat.getInsetsController(window, view)
                    c.isAppearanceLightStatusBars = s.light
                    c.isAppearanceLightNavigationBars = s.light
                }
                AthaanRoot(vm, debugRoute)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDebug(intent)
    }

    private fun handleDebug(intent: Intent?) {
        if (!BuildConfig.DEBUG || intent == null) return
        intent.getStringExtra("debug_lang")?.let { vm.setArabic(it == "ar") }
        intent.getStringExtra("debug_theme")?.let { vm.setLight(it == "light") }
        intent.getStringExtra("debug_play")?.let { p -> runCatching { vm.testAdhan(Prayer.valueOf(p)) } }
        intent.getStringExtra("debug_stop")?.let { vm.stopAdhan() }
        intent.getStringExtra("debug_route")?.let { debugRoute.value = it }
        intent.getStringExtra("debug_add_custom")?.let { name ->
            lifecycleScope.launch {
                val repo = com.athaan.app.audio.AudioRepository.get(this@MainActivity)
                val src = com.athaan.app.data.Track(Muezzins.byId("husary"), false)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { if (!repo.isDownloaded(src)) repo.downloadBlocking(src) }
                val m = com.athaan.app.data.CustomMuezzins.addFromFile(this@MainActivity, name, repo.localFile(src), false)
                if (m != null) vm.setMuezzin(Prayer.DHUHR, m.id)
                Log.i("AthaanDebug", "custom added ${m?.id}")
            }
        }
        Log.i("AthaanDebug", "handled debug intent ${intent.extras?.keySet()}")
    }

    override fun onResume() {
        super.onResume()
        AdhanScheduler.reschedule(this)
    }

    companion object {
        const val EXTRA_OPEN_PLAYING = "open_playing"
    }
}

@Composable
private fun AthaanRoot(vm: AppViewModel, debugRoute: androidx.compose.runtime.MutableState<String?>) {
    val s by vm.settings.collectAsState()
    val playback by vm.playback.collectAsState()
    val loc by vm.locStatus.collectAsState()
    val context = LocalContext.current
    val activity = context as Activity

    var tab by remember { mutableStateOf(Tab.HOME) }
    val stack = remember { mutableStateListOf<Route>() }
    var onboardingManual by remember { mutableStateOf(false) }

    fun closeCityPicker() {
        if (stack.lastOrNull() == Route.CityPicker) stack.removeAt(stack.lastIndex)
        onboardingManual = false
    }

    // ---------- Location: permission → turn on location → find position ----------
    fun detect() = vm.detectLocation { st -> if (st == LocStatus.IDLE) closeCityPicker() }

    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK || LocationHelper.servicesEnabled(context)) detect()
    }
    fun ensureServicesThenDetect() {
        if (LocationHelper.servicesEnabled(context)) { detect(); return }
        // Ask Google Play services to show the one-tap "turn on location" dialog.
        val req = LocationSettingsRequest.Builder()
            .addLocationRequest(LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10_000).build())
            .setAlwaysShow(true)
            .build()
        LocationServices.getSettingsClient(activity).checkLocationSettings(req)
            .addOnSuccessListener { detect() }
            .addOnFailureListener { e ->
                if (e is ResolvableApiException) {
                    runCatching { settingsLauncher.launch(IntentSenderRequest.Builder(e.resolution).build()) }
                        .onFailure { detect() }
                } else {
                    detect() // reports SERVICES_OFF with instructions
                }
            }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) ensureServicesThenDetect() else vm.setLocDenied()
    }
    fun requestLocation() {
        vm.clearLocStatus()
        if (LocationHelper.hasPermission(context)) ensureServicesThenDetect()
        else permLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    fun openAppSettings() {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }
        vm.clearLocStatus()
    }

    // Ask for notifications on Android 13+ once the app is set up.
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(s.hasLocation) {
        if (s.hasLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Debug-build hooks for the automated screenshot test.
    LaunchedEffect(debugRoute.value) {
        val r = debugRoute.value ?: return@LaunchedEffect
        debugRoute.value = null
        stack.clear()
        when {
            r == "home" -> tab = Tab.HOME
            r == "library" -> tab = Tab.LIBRARY
            r == "adhan" -> tab = Tab.ADHAN
            r == "settings" -> tab = Tab.SETTINGS
            r == "city" -> stack.add(Route.CityPicker)
            r == "add" -> stack.add(Route.AddCustom(null))
            r.startsWith("pick:") -> runCatching { stack.add(Route.Pick(Prayer.valueOf(r.removePrefix("pick:")))) }
            r.startsWith("muezzin:") -> stack.add(Route.Muezzin(r.removePrefix("muezzin:")))
        }
    }

    BackHandler(enabled = stack.isNotEmpty() || tab != Tab.HOME || onboardingManual) {
        when {
            stack.isNotEmpty() -> stack.removeAt(stack.lastIndex)
            onboardingManual -> onboardingManual = false
            else -> tab = Tab.HOME
        }
    }

    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val screenKey: Any = when {
        playback != null -> "playing"
        !s.hasLocation -> if (onboardingManual) "onbCity" else "onb"
        stack.isNotEmpty() -> stack.size to stack.last()
        else -> "tabs"
    }

    Box(Modifier.fillMaxSize().background(AC.Ink)) {
        AnimatedContent(
            targetState = screenKey,
            transitionSpec = {
                fun depth(k: Any?): Int = when (k) {
                    is Pair<*, *> -> k.first as? Int ?: 0
                    "onbCity" -> 1
                    else -> 0
                }
                val pushing = depth(targetState) > depth(initialState)
                when {
                    targetState == "playing" -> (fadeIn(tween(400)) + scaleIn(initialScale = 1.06f, animationSpec = tween(400))) togetherWith fadeOut(tween(250))
                    initialState == "playing" -> fadeIn(tween(350)) togetherWith (fadeOut(tween(300)) + scaleOut(targetScale = 1.06f, animationSpec = tween(300)))
                    else -> {
                        val dir = (if (pushing) 1 else -1) * (if (rtl) -1 else 1)
                        (slideInHorizontally(tween(300)) { it * dir / 10 } + fadeIn(tween(300))) togetherWith
                            (slideOutHorizontally(tween(260)) { -it * dir / 10 } + fadeOut(tween(200)))
                    }
                }
            },
            label = "screens",
        ) { key ->
            when {
                key == "playing" -> playback?.let { PlayingScreen(vm, it) } ?: Box(Modifier.fillMaxSize())

                key == "onb" || key == "onbCity" -> Box(Modifier.systemBarsPadding()) {
                    if (key == "onbCity") {
                        CityPickerScreen(
                            onPick = { vm.setCity(it); onboardingManual = false },
                            onUseGps = { requestLocation() },
                            onBack = { onboardingManual = false },
                            status = loc,
                            onToggleLanguage = { vm.setArabic(!s.arabic) },
                        )
                    } else {
                        OnboardingScreen(
                            vm,
                            onAllow = { requestLocation() },
                            onManual = { onboardingManual = true },
                            onOpenAppSettings = { openAppSettings() },
                        )
                    }
                }

                key is Pair<*, *> -> Box(Modifier.systemBarsPadding()) {
                    when (val r = key.second as Route) {
                        is Route.Muezzin -> MuezzinScreen(
                            vm, Muezzins.byId(r.id),
                            onBack = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) },
                            onAddVersion = { stack.add(Route.AddCustom(it)) },
                        )
                        is Route.AddCustom -> AddMuezzinScreen(
                            vm, r.prefill,
                            onBack = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) },
                            onAdded = { m -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex); stack.add(Route.Muezzin(m.id)) },
                        )
                        is Route.Pick -> LibraryScreen(
                            vm, pickFor = r.prayer,
                            onOpen = { stack.add(Route.Muezzin(it.id)) },
                            onPicked = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) },
                            onBack = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) },
                            onAdd = { stack.add(Route.AddCustom(null)) },
                        )
                        Route.CityPicker -> CityPickerScreen(
                            onPick = { vm.setCity(it); closeCityPicker() },
                            onUseGps = { requestLocation() },
                            onBack = { closeCityPicker() },
                            status = loc,
                            onToggleLanguage = { vm.setArabic(!s.arabic) },
                        )
                    }
                }

                else -> Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).statusBarsPadding()) {
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = {
                                // Tabs simply cross-fade: nothing moves, so the screen feels steady.
                                fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(160))
                            },
                            label = "tabs",
                        ) { t ->
                            when (t) {
                                Tab.HOME -> HomeScreen(
                                    vm,
                                    onLocation = { stack.add(Route.CityPicker) },
                                    onPrayer = { stack.add(Route.Pick(it)) },
                                )
                                Tab.LIBRARY -> LibraryScreen(
                                    vm, pickFor = null,
                                    onOpen = { stack.add(Route.Muezzin(it.id)) },
                                    onPicked = {},
                                    onBack = null,
                                    onAdd = { stack.add(Route.AddCustom(null)) },
                                )
                                Tab.ADHAN -> PerPrayerScreen(vm, onChange = { stack.add(Route.Pick(it)) })
                                Tab.SETTINGS -> SettingsScreen(
                                    vm,
                                    onPickCity = { stack.add(Route.CityPicker) },
                                    onUseLocation = { requestLocation() },
                                )
                            }
                        }
                    }
                    BottomNav(tab) { tab = it; vm.stopPreview() }
                }
            }
        }
    }
}
