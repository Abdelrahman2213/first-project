package com.athaan.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.athaan.app.data.Muezzins
import com.athaan.app.location.LocationHelper
import com.athaan.app.prayer.Prayer
import com.athaan.app.schedule.AdhanScheduler
import com.athaan.app.ui.AppViewModel
import com.athaan.app.ui.components.BottomNav
import com.athaan.app.ui.components.Tab
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

/** Screens pushed on top of the tabs. */
sealed interface Route {
    data class Muezzin(val id: String) : Route
    data class Pick(val prayer: Prayer) : Route
    data object CityPicker : Route
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
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
        setContent {
            val s by vm.settings.collectAsState()
            AthaanTheme(arabic = s.arabic) {
                AthaanRoot(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
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
private fun AthaanRoot(vm: AppViewModel) {
    val s by vm.settings.collectAsState()
    val playback by vm.playback.collectAsState()
    val context = LocalContext.current

    var tab by remember { mutableStateOf(Tab.HOME) }
    val stack = remember { mutableStateListOf<Route>() }
    var onboardingManual by remember { mutableStateOf(false) }

    val gpsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) vm.detectLocation { ok -> if (ok && stack.lastOrNull() == Route.CityPicker) stack.removeAt(stack.lastIndex) }
    }
    fun useGps() {
        if (LocationHelper.hasPermission(context)) {
            vm.detectLocation { ok -> if (ok && stack.lastOrNull() == Route.CityPicker) stack.removeAt(stack.lastIndex) }
        } else {
            gpsLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    // Ask for notifications on Android 13+ once the app is set up.
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(s.hasLocation) {
        if (s.hasLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(enabled = stack.isNotEmpty() || tab != Tab.HOME || onboardingManual) {
        when {
            stack.isNotEmpty() -> stack.removeAt(stack.lastIndex)
            onboardingManual -> onboardingManual = false
            else -> tab = Tab.HOME
        }
    }

    Box(Modifier.fillMaxSize().background(AC.Ink)) {
        val pb = playback
        when {
            pb != null -> PlayingScreen(vm, pb)

            !s.hasLocation -> Box(Modifier.statusBarsPadding()) {
                if (onboardingManual) {
                    CityPickerScreen(
                        onPick = { vm.setCity(it); onboardingManual = false },
                        onUseGps = { useGps() },
                        onBack = { onboardingManual = false },
                    )
                } else {
                    OnboardingScreen(vm, onManual = { onboardingManual = true })
                }
            }

            stack.isNotEmpty() -> Box(Modifier.statusBarsPadding()) {
                when (val r = stack.last()) {
                    is Route.Muezzin -> MuezzinScreen(vm, Muezzins.byId(r.id), onBack = { stack.removeAt(stack.lastIndex) })
                    is Route.Pick -> LibraryScreen(
                        vm, pickFor = r.prayer,
                        onOpen = { stack.add(Route.Muezzin(it.id)) },
                        onPicked = { stack.removeAt(stack.lastIndex) },
                        onBack = { stack.removeAt(stack.lastIndex) },
                    )
                    Route.CityPicker -> CityPickerScreen(
                        onPick = { vm.setCity(it); stack.removeAt(stack.lastIndex) },
                        onUseGps = { useGps() },
                        onBack = { stack.removeAt(stack.lastIndex) },
                    )
                }
            }

            else -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).statusBarsPadding()) {
                    when (tab) {
                        Tab.HOME -> HomeScreen(
                            vm,
                            onLocation = { stack.add(Route.CityPicker) },
                            onAdhanSettings = { tab = Tab.ADHAN },
                            onPrayer = { stack.add(Route.Pick(it)) },
                        )
                        Tab.LIBRARY -> LibraryScreen(
                            vm, pickFor = null,
                            onOpen = { stack.add(Route.Muezzin(it.id)) },
                            onPicked = {},
                            onBack = null,
                        )
                        Tab.ADHAN -> PerPrayerScreen(vm, onChange = { stack.add(Route.Pick(it)) })
                        Tab.SETTINGS -> SettingsScreen(vm, onPickCity = { stack.add(Route.CityPicker) })
                    }
                }
                BottomNav(tab) { tab = it; vm.stopPreview() }
            }
        }
    }
}
