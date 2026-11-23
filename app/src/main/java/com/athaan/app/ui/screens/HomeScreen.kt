package com.athaan.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athaan.app.prayer.HijriDate
import com.athaan.app.prayer.Prayer
import com.athaan.app.prayer.TimeFmt
import com.athaan.app.ui.AppViewModel
import com.athaan.app.ui.components.Avatar
import com.athaan.app.ui.components.PlayButton
import com.athaan.app.ui.components.HeroOrnament
import com.athaan.app.prayer.PrayerSchedule
import com.athaan.app.ui.components.T
import com.athaan.app.ui.components.ThemeToggle
import com.athaan.app.ui.components.pressClick
import com.athaan.app.ui.theme.AC
import com.athaan.app.ui.theme.AIcons
import com.athaan.app.ui.theme.LocalArabic
import com.athaan.app.ui.theme.tr

private var homeIntroPlayed = false

/** "الجمعة" for Dhuhr on a Friday, otherwise the prayer's name. */
fun prayerName(p: Prayer, at: Long, tz: java.util.TimeZone, ar: Boolean): String =
    if (p == Prayer.DHUHR && PrayerSchedule.isFriday(at, tz)) (if (ar) "الجمعة" else "Jumu'ah") else (if (ar) p.ar else p.en)

@Composable
fun HomeScreen(vm: AppViewModel, onLocation: () -> Unit, onPrayer: (Prayer) -> Unit) {
    val s by vm.settings.collectAsState()
    val now by vm.now.collectAsState()
    val preview by vm.preview.collectAsState()
    val ar = LocalArabic.current
    val tz = s.zone()

    // Recompute times once a minute, not every second.
    val minuteKey = now / 60_000L
    val today = remember(s, minuteKey) { vm.dayTimes(s, 0, now) }
    val next = remember(s, minuteKey) { vm.nextInfo(s, now) }
    val nextMuezzin = s.muezzin(next.prayer)
    val nextTrack = nextMuezzin.trackFor(next.prayer)
    val rawProgress = ((now - next.prevAt).toFloat() / (next.at - next.prevAt).coerceAtLeast(1)).coerceIn(0f, 1f)
    val progress by animateFloatAsState(rawProgress, tween(900, easing = FastOutSlowInEasing), label = "progress")

    // Entrance: hero rises in, rows follow one by one.
    // Played once per app launch, so switching tabs back to Home stays steady.
    val enter = remember { Animatable(if (homeIntroPlayed) 1f else 0f) }
    LaunchedEffect(Unit) {
        homeIntroPlayed = true
        enter.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        // Top bar: location (start), app name (center), theme switch (end)
        Box(Modifier.fillMaxWidth().height(56.dp).padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
            Row(
                Modifier
                    .align(Alignment.CenterStart)
                    .widthIn(max = 150.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(AC.Surface)
                    .border(1.dp, AC.Line2, RoundedCornerShape(22.dp))
                    .pressClick(label = tr("تغيير الموقع", "Change location"), onClick = onLocation)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AIcons.Pin, null, tint = AC.Gold, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                T(s.place(ar), 14.sp, FontWeight.Medium, maxLines = 1)
            }
            T(tr("أذان", "Athaan"), 22.sp, color = AC.Gold, kufi = true, modifier = Modifier.align(Alignment.Center))
            Box(Modifier.align(Alignment.CenterEnd)) {
                ThemeToggle(s.light) { vm.setLight(!s.light) }
            }
        }

        // Weekday and Gregorian date, then the Hijri date — two calm lines that never collide.
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 14.dp)) {
            T(HijriDate.weekdayGregorian(now, ar, tz), 13.sp, color = AC.Muted, maxLines = 1)
            T(HijriDate.hijriDate(now, ar, tz), 16.sp, FontWeight.SemiBold, maxLines = 1)
        }

        // Hero card
        Box(
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = 14.dp)
                .graphicsLayer {
                    alpha = enter.value
                    translationY = (1f - enter.value) * 40.dp.toPx()
                    val sc = 0.96f + 0.04f * enter.value
                    scaleX = sc; scaleY = sc
                }
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(AC.Surface)
                .border(1.dp, AC.Gold.copy(alpha = 0.22f), RoundedCornerShape(28.dp))
        ) {
            // An Islamic ornament (no stars) in the card's far top corner, away from the text, glowing softly.
            val glow = rememberInfiniteTransition(label = "heroGlow")
            val shine by glow.animateFloat(0.82f, 1f, infiniteRepeatable(tween(3800, easing = LinearEasing), RepeatMode.Reverse), label = "shine")
            Box(Modifier.matchParentSize()) {
                HeroOrnament(
                    s.pattern,
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = 10.dp)
                        .size(176.dp)
                        .graphicsLayer { alpha = enter.value * shine },
                )
            }
            Column(Modifier.padding(22.dp)) {
                val ramadan = PrayerSchedule.isRamadan(next.at, tz)
                T(
                    when {
                        ramadan && next.prayer == Prayer.MAGHRIB -> tr("الصلاة القادمة · موعد الإفطار", "Next · Iftar")
                        ramadan && next.prayer == Prayer.FAJR -> tr("الصلاة القادمة · الإمساك", "Next · Suhoor ends")
                        else -> tr("الصلاة القادمة", "Next prayer")
                    },
                    13.sp, FontWeight.Medium, AC.Muted,
                )
                AnimatedContent(
                    targetState = next.prayer,
                    transitionSpec = {
                        (slideInVertically(tween(450)) { it / 2 } + fadeIn(tween(450))) togetherWith
                            (slideOutVertically(tween(300)) { -it / 2 } + fadeOut(tween(250)))
                    },
                    label = "nextPrayer",
                ) { p ->
                    Row(verticalAlignment = Alignment.Bottom) {
                        T(prayerName(p, next.at, tz, ar), 46.sp, kufi = true, lineHeight = 60.sp)
                        Spacer(Modifier.width(12.dp))
                        T(TimeFmt.clock(next.at, ar, tz), 17.sp, FontWeight.SemiBold, AC.Gold, Modifier.padding(bottom = 12.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(tr("باقي", "in"), 13.sp, color = AC.Muted)
                    Spacer(Modifier.width(8.dp))
                    RollingText(TimeFmt.countdown(next.at - now), 30.sp)
                }
                Box(
                    Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(AC.Line2)
                ) {
                    Box(Modifier.fillMaxWidth(progress * enter.value).height(4.dp).clip(RoundedCornerShape(2.dp)).background(AC.Gold))
                }
                Box(Modifier.padding(top = 18.dp).fillMaxWidth().height(1.dp).background(AC.Line))
                Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(nextMuezzin.initial, 36.dp, 14.sp, ring = AC.Gold)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        T(
                            if (nextTrack.fajr) tr("يؤذّن لها · أذان الفجر", "Called by · Fajr adhan") else tr("يؤذّن لها", "Called by"),
                            12.sp, color = AC.Muted, maxLines = 1,
                        )
                        T(nextMuezzin.title(ar), 14.sp, FontWeight.SemiBold, maxLines = 1)
                    }
                    PlayButton(
                        playing = preview.key == nextTrack.key && preview.playing,
                        loading = preview.key == nextTrack.key && preview.loading,
                        onClick = { vm.togglePreview(nextTrack) },
                        label = tr("استمع لمعاينة الأذان", "Preview adhan"),
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            T(tr("مواقيت اليوم", "Today's times"), 15.sp, FontWeight.SemiBold, modifier = Modifier.weight(1f))
            T(if (ar) s.method.ar else s.method.en, 12.sp, color = AC.Muted, maxLines = 1, modifier = Modifier.padding(start = 12.dp))
        }

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Prayer.values().forEachIndexed { i, p ->
                val t = today[p]
                val isNext = p == next.prayer && t == next.at
                val sub = when {
                    !p.hasAdhan -> tr("بدون أذان", "No adhan")
                    s.enabled[p] != true -> tr("الأذان متوقف", "Adhan off")
                    else -> s.muezzin(p).name(ar) +
                        ((s.iqama[p] ?: 0).takeIf { it > 0 }?.let { tr(" · الإقامة بعد $it د", " · iqama +$it min") } ?: "") +
                        if (isNext) tr(" · التالية", " · next") else ""
                }
                // Staggered entrance per row
                val local = ((enter.value * 1.6f) - i * 0.12f).coerceIn(0f, 1f)
                Box(Modifier.graphicsLayer { alpha = local; translationY = (1f - local) * 24.dp.toPx() }) {
                    PrayerRow(
                        p = p, name = prayerName(p, t, tz, ar), time = TimeFmt.clock(t, ar, tz), subtitle = sub, highlighted = isNext,
                        onClick = if (p.hasAdhan) ({ onPrayer(p) }) else null,
                    )
                }
            }
        }
    }
}

/** Countdown whose changing digits roll, each in a fixed-width cell so nothing shifts. */
@Composable
private fun RollingText(text: String, size: TextUnit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val digitW = with(density) { (size * 0.62f).toDp() }
    val colonW = with(density) { (size * 0.32f).toDp() }
    // Digits always read left-to-right, even in the Arabic layout.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            text.forEachIndexed { i, ch ->
                val w = if (ch.isDigit()) digitW else colonW
                Box(Modifier.width(w).clipToBounds(), contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = ch,
                        transitionSpec = {
                            (slideInVertically(tween(280)) { -it } + fadeIn(tween(280))) togetherWith
                                (slideOutVertically(tween(280)) { it } + fadeOut(tween(220)))
                        },
                        contentAlignment = Alignment.Center,
                        label = "digit$i",
                    ) { c ->
                        // No ellipsis and no wrapping: a single glyph must always be drawn in full.
                        androidx.compose.material3.Text(
                            c.toString(), fontSize = size, fontWeight = FontWeight.SemiBold, fontFamily = com.athaan.app.ui.theme.Plex,
                            color = AC.Text, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrayerRow(p: Prayer, name: String, time: String, subtitle: String, highlighted: Boolean, onClick: (() -> Unit)?) {
    val ar = LocalArabic.current
    val shape = RoundedCornerShape(16.dp)
    val bg by animateColorAsState(if (highlighted) AC.GoldSoft else AC.Ink.copy(alpha = 0f), tween(500), label = "rowBg")
    val border by animateColorAsState(if (highlighted) AC.Gold.copy(alpha = 0.28f) else AC.Ink.copy(alpha = 0f), tween(500), label = "rowBr")
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (highlighted) 56.dp else 52.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .then(if (onClick != null) Modifier.pressClick(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AIcons.forPrayer(p), null, tint = if (highlighted) AC.Gold else AC.Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            T(name, 15.sp, if (highlighted) FontWeight.Bold else FontWeight.SemiBold, if (highlighted) AC.Text else AC.TextDim)
            T(subtitle, 11.sp, color = if (highlighted) AC.Gold else AC.Muted, maxLines = 1)
        }
        T(time, 15.sp, if (highlighted) FontWeight.Bold else FontWeight.Normal, if (highlighted) AC.Gold else AC.TextDim)
    }
}
