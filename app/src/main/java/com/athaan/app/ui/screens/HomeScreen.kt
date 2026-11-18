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
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.athaan.app.ui.components.StarPattern
import com.athaan.app.ui.components.T
import com.athaan.app.ui.components.ThemeToggle
import com.athaan.app.ui.components.pressClick
import com.athaan.app.ui.theme.AC
import com.athaan.app.ui.theme.AIcons
import com.athaan.app.ui.theme.LocalArabic
import com.athaan.app.ui.theme.tr

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
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }

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

        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 14.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            T(HijriDate.hijriLine(now, ar, tz), 15.sp, FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
            T(HijriDate.gregorianShort(now, ar, tz), 13.sp, color = AC.Muted)
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
            // Slowly turning star pattern on the "end" half of the card
            val spin = rememberInfiniteTransition(label = "heroSpin")
            val rot by spin.animateFloat(0f, 90f, infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart), label = "rot")
            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            Box(Modifier.matchParentSize(), contentAlignment = if (rtl) Alignment.CenterStart else Alignment.CenterEnd) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(0.58f)) {
                    StarPattern(Modifier.matchParentSize(), rotation = rot)
                }
            }
            Column(Modifier.padding(22.dp)) {
                T(tr("الصلاة القادمة", "Next prayer"), 13.sp, FontWeight.Medium, AC.Muted)
                AnimatedContent(
                    targetState = next.prayer,
                    transitionSpec = {
                        (slideInVertically(tween(450)) { it / 2 } + fadeIn(tween(450))) togetherWith
                            (slideOutVertically(tween(300)) { -it / 2 } + fadeOut(tween(250)))
                    },
                    label = "nextPrayer",
                ) { p ->
                    Row(verticalAlignment = Alignment.Bottom) {
                        T(if (ar) p.ar else p.en, 46.sp, kufi = true, lineHeight = 60.sp)
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
                    else -> s.muezzin(p).name(ar) + if (isNext) tr(" · التالية", " · next") else ""
                }
                // Staggered entrance per row
                val local = ((enter.value * 1.6f) - i * 0.12f).coerceIn(0f, 1f)
                Box(Modifier.graphicsLayer { alpha = local; translationY = (1f - local) * 24.dp.toPx() }) {
                    PrayerRow(
                        p = p, time = TimeFmt.clock(t, ar, tz), subtitle = sub, highlighted = isNext,
                        onClick = if (p.hasAdhan) ({ onPrayer(p) }) else null,
                    )
                }
            }
        }
    }
}

/** Text whose changing characters roll up, like a split-flap clock. */
@Composable
private fun RollingText(text: String, size: TextUnit) {
    // Digits always read left-to-right, even in the Arabic layout.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Row {
        text.forEachIndexed { i, ch ->
            AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    (slideInVertically(tween(260)) { -it } + fadeIn(tween(260))) togetherWith
                        (slideOutVertically(tween(260)) { it } + fadeOut(tween(200)))
                },
                label = "digit$i",
            ) { c -> T(c.toString(), size, FontWeight.SemiBold) }
        }
    } }
}

@Composable
private fun PrayerRow(p: Prayer, time: String, subtitle: String, highlighted: Boolean, onClick: (() -> Unit)?) {
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
            T(if (ar) p.ar else p.en, 15.sp, if (highlighted) FontWeight.Bold else FontWeight.SemiBold, if (highlighted) AC.Text else AC.TextDim)
            T(subtitle, 11.sp, color = if (highlighted) AC.Gold else AC.Muted, maxLines = 1)
        }
        T(time, 15.sp, if (highlighted) FontWeight.Bold else FontWeight.Normal, if (highlighted) AC.Gold else AC.TextDim)
    }
}
