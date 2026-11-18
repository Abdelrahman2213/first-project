package com.athaan.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athaan.app.data.Muezzins
import com.athaan.app.prayer.HijriDate
import com.athaan.app.prayer.Prayer
import com.athaan.app.prayer.TimeFmt
import com.athaan.app.ui.AppViewModel
import com.athaan.app.ui.components.Avatar
import com.athaan.app.ui.components.CircleIconButton
import com.athaan.app.ui.components.StarPattern
import com.athaan.app.ui.components.T
import com.athaan.app.ui.theme.AC
import com.athaan.app.ui.theme.AIcons
import com.athaan.app.ui.theme.LocalArabic
import com.athaan.app.ui.theme.tr

@Composable
fun HomeScreen(vm: AppViewModel, onLocation: () -> Unit, onAdhanSettings: () -> Unit, onPrayer: (Prayer) -> Unit) {
    val s by vm.settings.collectAsState()
    val now by vm.now.collectAsState()
    val preview by vm.preview.collectAsState()
    val ar = LocalArabic.current

    // Recompute times once a minute, not every second.
    val minuteKey = now / 60_000L
    val today = remember(s, minuteKey) { vm.dayTimes(s, 0, now) }
    val next = remember(s, minuteKey) { vm.nextInfo(s, now) }
    val nextMuezzin = Muezzins.byId(s.muezzinFor[next.prayer])
    val progress = ((now - next.prevAt).toFloat() / (next.at - next.prevAt).coerceAtLeast(1)).coerceIn(0f, 1f)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        // Top bar: location (start), app name (center), adhan settings (end)
        Box(Modifier.fillMaxWidth().height(56.dp).padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
            Row(
                Modifier
                    .align(Alignment.CenterStart)
                    .widthIn(max = 150.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(AC.Surface)
                    .border(1.dp, AC.Line2, RoundedCornerShape(22.dp))
                    .clickable(role = Role.Button, onClick = onLocation)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AIcons.Pin, null, tint = AC.Gold, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                T(s.place(ar), 14.sp, FontWeight.Medium, maxLines = 1)
            }
            T(tr("أذان", "Athaan"), 22.sp, color = AC.Gold, kufi = true, modifier = Modifier.align(Alignment.Center))
            Box(Modifier.align(Alignment.CenterEnd)) {
                CircleIconButton(AIcons.Bell, tr("إعدادات الأذان", "Adhan settings"), onAdhanSettings)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 14.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            T(HijriDate.hijriLine(now, ar), 15.sp, FontWeight.SemiBold, modifier = Modifier.weight(1f))
            T(HijriDate.gregorianShort(now, ar), 13.sp, color = AC.Muted)
        }

        // Hero card
        Box(
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(AC.Surface)
                .border(1.dp, AC.Gold.copy(alpha = 0.22f), RoundedCornerShape(28.dp))
        ) {
            // Pattern on the "end" half of the card
            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            Box(Modifier.matchParentSize(), contentAlignment = if (rtl) Alignment.CenterStart else Alignment.CenterEnd) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(0.58f)) {
                    StarPattern(Modifier.matchParentSize())
                }
            }
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 22.dp)) {
                T(tr("الصلاة القادمة", "Next prayer"), 13.sp, FontWeight.Medium, AC.Muted)
                Row(verticalAlignment = Alignment.Bottom) {
                    T(if (ar) next.prayer.ar else next.prayer.en, 46.sp, kufi = true, lineHeight = 56.sp)
                    Spacer(Modifier.width(12.dp))
                    T(TimeFmt.clock(next.at, ar), 17.sp, FontWeight.SemiBold, AC.Gold, Modifier.padding(bottom = 10.dp))
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    T(tr("باقي", "in"), 13.sp, color = AC.Muted, modifier = Modifier.padding(bottom = 6.dp))
                    Spacer(Modifier.width(8.dp))
                    T(TimeFmt.countdown(next.at - now), 30.sp, FontWeight.SemiBold)
                }
                Box(
                    Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Box(Modifier.fillMaxWidth(progress).height(4.dp).clip(RoundedCornerShape(2.dp)).background(AC.Gold))
                }
                Box(Modifier.padding(top = 18.dp).fillMaxWidth().height(1.dp).background(AC.Line))
                Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(nextMuezzin.initial, 36.dp, 14.sp, ring = AC.Gold)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        T(tr("يؤذّن لها", "Called by"), 12.sp, color = AC.Muted)
                        T(nextMuezzin.name(ar), 14.sp, FontWeight.SemiBold, maxLines = 1)
                    }
                    val isPlaying = preview.muezzinId == nextMuezzin.id && preview.playing
                    CircleIconButton(
                        if (isPlaying) AIcons.Pause else AIcons.Play,
                        if (isPlaying) tr("إيقاف المعاينة", "Stop preview") else tr("استمع لمعاينة الأذان", "Preview adhan"),
                        onClick = { vm.togglePreview(nextMuezzin) },
                        filled = true, iconSize = 18.dp,
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            T(tr("مواقيت اليوم", "Today's times"), 15.sp, FontWeight.SemiBold, modifier = Modifier.weight(1f))
            T(if (ar) s.method.ar else s.method.en, 12.sp, color = AC.Muted, maxLines = 1)
        }

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Prayer.values().forEach { p ->
                val t = today[p]
                val isNext = p == next.prayer && t == next.at
                val sub = when {
                    !p.hasAdhan -> tr("بدون أذان", "No adhan")
                    s.enabled[p] != true -> tr("الأذان متوقف", "Adhan off")
                    else -> Muezzins.byId(s.muezzinFor[p]).name(ar) + if (isNext) tr(" · التالية", " · next") else ""
                }
                PrayerRow(
                    p = p, time = TimeFmt.clock(t, ar), subtitle = sub, highlighted = isNext,
                    onClick = if (p.hasAdhan) ({ onPrayer(p) }) else null,
                )
            }
        }
    }
}

@Composable
private fun PrayerRow(p: Prayer, time: String, subtitle: String, highlighted: Boolean, onClick: (() -> Unit)?) {
    val ar = LocalArabic.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (highlighted) 56.dp else 52.dp)
            .clip(shape)
            .then(if (highlighted) Modifier.background(AC.GoldSoft).border(1.dp, AC.Gold.copy(alpha = 0.28f), shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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
