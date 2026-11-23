package com.athaan.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athaan.app.ui.AppViewModel
import com.athaan.app.ui.LocStatus
import com.athaan.app.ui.components.GoldButton
import com.athaan.app.ui.components.LanguageToggle
import com.athaan.app.ui.components.OutlineButton
import com.athaan.app.ui.components.ArchLattice
import com.athaan.app.ui.components.Fade
import com.athaan.app.ui.components.T
import com.athaan.app.ui.components.ThemeToggle
import com.athaan.app.ui.theme.AC
import com.athaan.app.ui.theme.AIcons
import com.athaan.app.ui.theme.LocalArabic
import com.athaan.app.ui.theme.tr

@Composable
fun OnboardingScreen(vm: AppViewModel, onAllow: () -> Unit, onManual: () -> Unit, onOpenAppSettings: () -> Unit) {
    val s by vm.settings.collectAsState()
    val status by vm.locStatus.collectAsState()
    val ar = LocalArabic.current

    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val loop = rememberInfiniteTransition(label = "onb")
    val float by loop.animateFloat(-4f, 4f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float")
    val halo by loop.animateFloat(1f, 1.12f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "halo")

    fun stage(i: Int) = ((enter.value * 1.8f) - i * 0.18f).coerceIn(0f, 1f)

    Box(Modifier.fillMaxSize()) {
        ArchLattice(Modifier.fillMaxWidth().height(460.dp), tile = 56.dp, alpha = 0.10f, fade = Fade.TO_BOTTOM)
        Box(Modifier.fillMaxWidth().height(460.dp).background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to AC.Ink)))

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            // Language + theme, so a non-Arabic speaker can switch right away.
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                LanguageToggle(ar) { vm.setArabic(!ar) }
                Spacer(Modifier.weight(1f))
                ThemeToggle(s.light) { vm.setLight(!s.light) }
            }
            Spacer(Modifier.height(48.dp))
            Box(Modifier.size(168.dp).graphicsLayer { alpha = stage(0) }, contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { val sc = halo * (0.7f + 0.3f * stage(0)); scaleX = sc; scaleY = sc }
                        .border(1.dp, AC.Gold.copy(alpha = 0.25f), CircleShape),
                )
                Box(
                    Modifier
                        .graphicsLayer { translationY = float.dp.toPx() }
                        .size(124.dp)
                        .clip(CircleShape)
                        .background(AC.Surface)
                        .border(1.dp, AC.Gold.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(AIcons.Mosque, null, tint = AC.Gold, modifier = Modifier.size(64.dp)) }
            }
            Column(
                Modifier.padding(bottom = 160.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier.graphicsLayer { alpha = stage(1); translationY = (1f - stage(1)) * 20.dp.toPx() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    T("أذان", 40.sp, color = AC.Gold, kufi = true, lineHeight = 54.sp, modifier = Modifier.padding(top = 28.dp))
                    T("ATHAAN", 13.sp, color = AC.Muted)
                }
                Column(
                    Modifier.graphicsLayer { alpha = stage(2); translationY = (1f - stage(2)) * 20.dp.toPx() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    T(
                        tr("نحتاج موقعك لنحسب\nمواقيت الصلاة بدقة", "We need your location to\ncalculate prayer times"),
                        24.sp, FontWeight.Bold, align = TextAlign.Center, lineHeight = 36.sp,
                        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 36.dp),
                    )
                    AnimatedContent(status, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) }, label = "status") { st ->
                        val error = locStatusError(st)
                        val (msg, err) = when {
                            error != null -> error to true
                            st == LocStatus.LOCATING -> tr("جارٍ تحديد موقعك…", "Finding your location…") to false
                            else -> tr("الموقع يُستخدم على جهازك فقط لتحديد مدينتك ومواقيت الصلاة.", "Your location stays on your phone and is only used for prayer times.") to false
                        }
                        T(
                            msg, 14.sp, color = if (err) AC.Danger else AC.Muted, align = TextAlign.Center, lineHeight = 24.sp,
                            modifier = Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 12.dp),
                        )
                    }
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.25f to AC.Ink))
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp, top = 24.dp)
                .graphicsLayer { alpha = stage(3); translationY = (1f - stage(3)) * 30.dp.toPx() },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (status == LocStatus.LOCATING) {
                Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AC.Gold)
                }
            } else if (status == LocStatus.DENIED) {
                GoldButton(tr("فتح إعدادات التطبيق", "Open app settings"), onOpenAppSettings, Modifier.fillMaxWidth(), icon = AIcons.Sliders)
            } else {
                GoldButton(
                    if (status == LocStatus.IDLE) tr("السماح بالوصول للموقع", "Allow location access") else tr("حاول مرة أخرى", "Try again"),
                    onAllow, Modifier.fillMaxWidth(), icon = AIcons.Pin,
                )
            }
            OutlineButton(tr("اختيار الدولة والمدينة يدويًا", "Choose country and city"), onManual, Modifier.fillMaxWidth())
        }
    }
}

/** Short explanation for a failed location lookup, or null when there is nothing to report. */
@Composable
fun locStatusError(st: LocStatus): String? = when (st) {
    LocStatus.DENIED -> tr("لم يتم السماح بالوصول للموقع. اسمح به من إعدادات التطبيق، أو اختر مدينتك يدويًا.", "Location access wasn't allowed. Allow it in the app settings, or choose your city.")
    LocStatus.SERVICES_OFF -> tr("خدمة الموقع مقفولة في الموبايل. شغّلها وحاول مرة أخرى، أو اختر مدينتك يدويًا.", "Location is turned off on the phone. Turn it on and try again, or choose your city.")
    LocStatus.NOT_FOUND -> tr("لم نتمكن من تحديد مكانك الآن. جرّب مرة أخرى قرب نافذة، أو اختر مدينتك يدويًا.", "Couldn't find your position right now. Try again near a window, or choose your city.")
    else -> null
}
