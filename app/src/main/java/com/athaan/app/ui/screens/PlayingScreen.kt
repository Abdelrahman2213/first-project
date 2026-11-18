package com.athaan.app.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athaan.app.data.Muezzins
import com.athaan.app.prayer.TimeFmt
import com.athaan.app.service.AdhanPlayback
import com.athaan.app.ui.AppViewModel
import com.athaan.app.ui.components.GoldButton
import com.athaan.app.ui.components.OutlineButton
import com.athaan.app.ui.components.T
import com.athaan.app.ui.theme.AC
import com.athaan.app.ui.theme.AIcons
import com.athaan.app.ui.theme.Amiri
import com.athaan.app.ui.theme.LocalArabic
import com.athaan.app.ui.theme.tr

@Composable
fun PlayingScreen(vm: AppViewModel, pb: AdhanPlayback) {
    val s by vm.settings.collectAsState()
    val now by vm.now.collectAsState()
    val ar = LocalArabic.current
    val m = Muezzins.byId(pb.muezzinId)
    val prayerTime = remember(s, pb.prayer, now / 60_000L) {
        // Today's time for this prayer (the adhan starts at it).
        vm.dayTimes(s, 0, now)[pb.prayer]
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(AC.InkDeep)
            .systemBarsPadding()
    ) {
        // Rings and star
        Canvas(Modifier.fillMaxWidth().height(520.dp).padding(top = 40.dp)) {
            val c = Offset(size.width / 2, size.height / 2)
            val k = size.width / 390f
            listOf(200f to 0.06f, 160f to 0.10f, 120f to 0.16f).forEach { (r, a) ->
                drawCircle(AC.Gold.copy(alpha = a), radius = r * k, center = c, style = Stroke(1.dp.toPx()))
            }
            val half = 62f * k
            val stroke = Stroke(1.2f.dp.toPx())
            val tl = Offset(c.x - half, c.y - half)
            drawRect(AC.Gold.copy(alpha = 0.55f), tl, Size(half * 2, half * 2), style = stroke)
            rotate(45f, c) { drawRect(AC.Gold.copy(alpha = 0.55f), tl, Size(half * 2, half * 2), style = stroke) }
        }

        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            T(tr("حان الآن موعد أذان", "It's time for"), 13.sp, color = AC.Muted, modifier = Modifier.padding(top = 24.dp))
            Spacer(Modifier.height(170.dp))
            T(if (ar) pb.prayer.ar else pb.prayer.en, 58.sp, color = AC.Gold, kufi = true, lineHeight = 66.sp)
            T(TimeFmt.clock(prayerTime, ar), 18.sp, FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            T((if (ar && !m.nameAr.startsWith("أذان")) "الشيخ " else "") + m.name(ar), 18.sp, FontWeight.Bold)
            T(s.place(ar) + " · " + (if (ar) s.method.ar else s.method.en), 13.sp, color = AC.Muted, align = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp))

            Column(Modifier.padding(horizontal = 45.dp, vertical = 16.dp)) {
                val frac = if (pb.durationMs > 0) pb.positionMs.toFloat() / pb.durationMs else 0f
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.10f))) {
                    Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(AC.Gold))
                }
                Row(Modifier.padding(top = 6.dp)) {
                    T(TimeFmt.mmss(pb.positionMs), 11.sp, color = AC.Muted)
                    Spacer(Modifier.weight(1f))
                    T(if (pb.durationMs > 0) TimeFmt.mmss(pb.durationMs) else "--:--", 11.sp, color = AC.Muted)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AIcons.Volume, null, tint = AC.Muted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Slider(
                        value = s.volume,
                        onValueChange = { vm.setVolume(it) },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = AC.Gold, activeTrackColor = AC.Gold, inactiveTrackColor = AC.Track),
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlineButton(
                    if (pb.muted) tr("إلغاء الكتم", "Unmute") else tr("كتم", "Mute"),
                    { vm.toggleMute() },
                    Modifier.weight(1f), icon = if (pb.muted) AIcons.Volume else AIcons.Mute, height = 56.dp,
                )
                GoldButton(tr("إيقاف الأذان", "Stop adhan"), { vm.stopAdhan() }, Modifier.weight(1f), icon = AIcons.Stop)
            }

            Column(
                Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 24.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF0F1728))
                    .border(1.dp, AC.Line, RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                T(tr("دعاء بعد الأذان", "Supplication after the adhan"), 11.sp, FontWeight.SemiBold, AC.Muted)
                androidx.compose.material3.Text(
                    "اللهم رب هذه الدعوة التامة والصلاة القائمة، آتِ محمدًا الوسيلة والفضيلة، وابعثه مقامًا محمودًا الذي وعدته",
                    fontFamily = Amiri, fontSize = 16.sp, lineHeight = 29.sp, color = AC.Text,
                )
            }
        }
    }
}
