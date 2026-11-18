package com.athaan.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athaan.app.audio.DownloadState
import com.athaan.app.data.Muezzin
import com.athaan.app.prayer.Prayer
import com.athaan.app.prayer.TimeFmt
import com.athaan.app.ui.AppViewModel
import com.athaan.app.ui.components.Avatar
import com.athaan.app.ui.components.CircleIconButton
import com.athaan.app.ui.components.GoldButton
import com.athaan.app.ui.components.Pill
import com.athaan.app.ui.components.StarPattern
import com.athaan.app.ui.components.T
import com.athaan.app.ui.theme.AC
import com.athaan.app.ui.theme.AIcons
import com.athaan.app.ui.theme.LocalArabic
import com.athaan.app.ui.theme.tr

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuezzinScreen(vm: AppViewModel, m: Muezzin, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    val downloads by vm.downloads.collectAsState()
    val preview by vm.preview.collectAsState()
    val ar = LocalArabic.current
    val dl = downloads[m.id] ?: DownloadState.None

    val assigned = Prayer.adhanPrayers.filter { s.muezzinFor[it] == m.id }.toSet()
    var selected by remember(m.id) { mutableStateOf(assigned) }
    val isThis = preview.muezzinId == m.id

    Box(Modifier.fillMaxSize()) {
        StarPattern(Modifier.fillMaxWidth().height(300.dp), tile = 48.dp, alpha = 0.10f)
        Box(
            Modifier.fillMaxWidth().height(300.dp).background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0.35f to Color.Transparent, 1f to AC.Ink
                )
            )
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                CircleIconButton(AIcons.Back, tr("رجوع", "Back"), onBack)
            }
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(132.dp).border(1.dp, AC.Gold.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Avatar(m.initial, 112.dp, 46.sp, ring = AC.Gold, ringWidth = 2.dp) }
                T((if (ar) "الشيخ " else "").takeIf { !m.nameAr.startsWith("أذان") }.orEmpty() + m.name(ar),
                    24.sp, FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp))
                val origin = listOf(m.origin(ar), tr("أذان كامل", "Full adhan")).filter { it.isNotEmpty() }.distinct()
                T(origin.joinToString(" · "), 13.sp, color = AC.Muted)
            }

            // Preview card
            Row(
                Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 22.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(AC.Surface)
                    .border(1.dp, AC.Line, RoundedCornerShape(22.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val playing = isThis && preview.playing
                CircleIconButton(
                    if (playing) AIcons.Pause else AIcons.Play,
                    if (playing) tr("إيقاف المعاينة", "Pause") else tr("تشغيل المعاينة", "Play"),
                    { vm.togglePreview(m) }, size = 52.dp, iconSize = 18.dp, filled = true,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    if (isThis && preview.loading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 18.dp), color = AC.Gold, trackColor = AC.Track)
                    } else {
                        val frac = if (isThis && preview.durationMs > 0) preview.positionMs.toFloat() / preview.durationMs else 0f
                        Slider(
                            value = frac,
                            onValueChange = { if (isThis) vm.audio.seekPreview(it) },
                            enabled = isThis,
                            colors = SliderDefaults.colors(
                                thumbColor = AC.Gold, activeTrackColor = AC.Gold, inactiveTrackColor = AC.Track,
                                disabledThumbColor = AC.Muted, disabledInactiveTrackColor = AC.Track,
                            ),
                        )
                    }
                    Row {
                        T(if (isThis) TimeFmt.mmss(preview.positionMs) else "0:00", 11.sp, color = AC.Muted)
                        Spacer(Modifier.weight(1f))
                        T(if (isThis && preview.durationMs > 0) TimeFmt.mmss(preview.durationMs) else "--:--", 11.sp, color = AC.Muted)
                    }
                }
            }

            // Download state
            val dlShape = RoundedCornerShape(18.dp)
            Row(
                Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp)
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(dlShape)
                    .background(if (dl == DownloadState.Done) AC.Mint.copy(alpha = 0.08f) else AC.Surface)
                    .border(1.dp, if (dl == DownloadState.Done) AC.Mint.copy(alpha = 0.25f) else AC.Line, dlShape)
                    .clickable(enabled = dl == DownloadState.None || dl == DownloadState.Failed, role = Role.Button) { vm.download(m) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AIcons.Download, null, tint = if (dl == DownloadState.Done) AC.Mint else AC.Gold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                when (dl) {
                    DownloadState.Done -> {
                        T(tr("محمّل على جهازك", "Saved on your phone"), 14.sp, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = tr("حذف", "Delete")) { vm.deleteDownload(m) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(AIcons.Trash, tr("حذف التحميل", "Delete download"), tint = AC.Muted, modifier = Modifier.size(18.dp)) }
                    }
                    is DownloadState.Downloading -> {
                        Column(Modifier.weight(1f)) {
                            T(tr("جارٍ التحميل…", "Downloading…"), 14.sp, FontWeight.SemiBold)
                            LinearProgressIndicator(
                                progress = { dl.progress },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                color = AC.Gold, trackColor = AC.Track,
                            )
                        }
                    }
                    DownloadState.Failed -> T(tr("فشل التحميل · اضغط للمحاولة مرة أخرى", "Download failed · tap to retry"), 14.sp, FontWeight.SemiBold, AC.Danger, Modifier.weight(1f))
                    DownloadState.None -> T(tr("تحميل للعمل بدون إنترنت", "Download for offline use"), 14.sp, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                }
            }

            T(tr("اختره ليؤذن في", "Use him for"), 15.sp, FontWeight.SemiBold, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 10.dp))
            FlowRow(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Prayer.adhanPrayers.forEach { p ->
                    val on = p in selected
                    Pill(if (ar) p.ar else p.en, on, { selected = if (on) selected - p else selected + p }, leading = AIcons.Check)
                }
            }
            if (m.fajrOnly) {
                T(tr("هذا التسجيل مخصص لأذان الفجر", "This recording is a Fajr adhan"), 12.sp, color = AC.Muted, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp))
            }

            Spacer(Modifier.height(28.dp))
            val newOnes = selected - assigned
            val label = when {
                selected.isEmpty() -> tr("اختر صلاة واحدة على الأقل", "Pick at least one prayer")
                newOnes.isEmpty() -> tr("محفوظ", "Saved")
                selected.size == 5 -> tr("حفظ لكل الصلوات", "Save for all prayers")
                else -> tr("حفظ الاختيار (${selected.size})", "Save (${selected.size})")
            }
            GoldButton(
                label,
                onClick = { vm.assign(m, selected); onBack() },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp).fillMaxWidth(),
                enabled = newOnes.isNotEmpty(),
            )
        }
    }
}
