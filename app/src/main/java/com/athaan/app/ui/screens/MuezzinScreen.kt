package com.athaan.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athaan.app.audio.DownloadState
import com.athaan.app.audio.PreviewState
import com.athaan.app.data.Muezzin
import com.athaan.app.data.Track
import com.athaan.app.prayer.Prayer
import com.athaan.app.prayer.TimeFmt
import com.athaan.app.ui.AppViewModel
import com.athaan.app.ui.components.Avatar
import com.athaan.app.ui.components.CircleIconButton
import com.athaan.app.ui.components.GoldButton
import com.athaan.app.ui.components.OutlineButton
import com.athaan.app.ui.components.Pill
import com.athaan.app.ui.components.PlayButton
import com.athaan.app.ui.components.ArchLattice
import com.athaan.app.ui.components.Fade
import com.athaan.app.ui.components.GoldSlider
import com.athaan.app.ui.components.T
import com.athaan.app.ui.components.pressClick
import com.athaan.app.ui.theme.AC
import com.athaan.app.ui.theme.AIcons
import com.athaan.app.ui.theme.LocalArabic
import com.athaan.app.ui.theme.tr

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuezzinScreen(vm: AppViewModel, m: Muezzin, onBack: () -> Unit, onAddVersion: (String) -> Unit) {
    val s by vm.settings.collectAsState()
    val downloads by vm.downloads.collectAsState()
    val preview by vm.preview.collectAsState()
    val ar = LocalArabic.current

    val assigned = Prayer.adhanPrayers.filter { s.muezzin(it).id == m.id }.toSet()
    var selected by remember(m.id) { mutableStateOf(assigned) }
    var confirmDelete by remember { mutableStateOf(false) }

    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
    val breathe = rememberInfiniteTransition(label = "ring")
    val ring by breathe.animateFloat(1f, 1.08f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "ringScale")

    Box(Modifier.fillMaxSize()) {
        ArchLattice(Modifier.fillMaxWidth().height(300.dp), tile = 52.dp, alpha = 0.10f, fade = Fade.TO_BOTTOM)
        Box(Modifier.fillMaxWidth().height(300.dp).background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to AC.Ink)))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                CircleIconButton(AIcons.Back, tr("رجوع", "Back"), onBack)
            }
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp).graphicsLayer {
                    alpha = enter.value
                    val sc = 0.85f + 0.15f * enter.value
                    scaleX = sc; scaleY = sc
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(132.dp)
                        .graphicsLayer { scaleX = ring; scaleY = ring }
                        .border(1.dp, AC.Gold.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Avatar(m.initial, 112.dp, 46.sp, ring = AC.Gold, ringWidth = 2.dp) }
                T(m.title(ar), 24.sp, FontWeight.Bold, align = TextAlign.Center, modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp))
                if (m.origin(ar).isNotEmpty()) T(m.origin(ar), 13.sp, color = AC.Muted)
            }

            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp).graphicsLayer {
                    alpha = enter.value; translationY = (1f - enter.value) * 30.dp.toPx()
                },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (m.hasRegular) RecordingCard(vm, Track(m, false), tr("الأذان", "Adhan"), tr("للظهر والعصر والمغرب والعشاء", "For Dhuhr, Asr, Maghrib and Isha"), preview, downloads[Track(m, false).key] ?: DownloadState.None)
                if (m.hasFajr) RecordingCard(vm, Track(m, true), tr("أذان الفجر", "Fajr adhan"), tr("فيه «الصلاة خير من النوم»", "Includes “prayer is better than sleep”"), preview, downloads[Track(m, true).key] ?: DownloadState.None)
            }

            T(tr("اختره ليؤذن في", "Use him for"), 15.sp, FontWeight.SemiBold, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 10.dp))
            FlowRow(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Prayer.adhanPrayers.forEach { p ->
                    val can = m.canCall(p)
                    val on = p in selected && can
                    Pill(if (ar) p.ar else p.en, on, { selected = if (on) selected - p else selected + p }, leading = AIcons.Check, enabled = can)
                }
            }
            if (!m.hasFajr) {
                T(tr("لا يوجد له تسجيل لأذان الفجر، لذلك لا يمكن اختياره للفجر", "No Fajr recording, so he can't be chosen for Fajr"), 12.sp, color = AC.Muted, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp))
            } else if (!m.hasRegular) {
                T(tr("هذا تسجيل لأذان الفجر فقط", "This is a Fajr-only recording"), 12.sp, color = AC.Muted, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp))
            }

            // Add another version of this sheikh, or delete a recording the user added.
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp)) {
                if (m.isCustom) {
                    OutlineButton(tr("حذف هذا الصوت", "Delete this recording"), { confirmDelete = true }, Modifier.fillMaxWidth(), icon = AIcons.Trash, color = AC.Danger, border = AC.Danger.copy(alpha = 0.4f))
                } else {
                    OutlineButton(tr("إضافة نسخة أخرى بصوته من ملف", "Add another version from a file"), { onAddVersion(m.name(ar) + if (ar) " (نسخة أخرى)" else " (another version)") }, Modifier.fillMaxWidth(), icon = AIcons.Mic)
                }
            }

            Spacer(Modifier.height(20.dp))
            val valid = selected.filter { m.canCall(it) }.toSet()
            val newOnes = valid - assigned
            val label = when {
                valid.isEmpty() -> tr("اختر صلاة واحدة على الأقل", "Pick at least one prayer")
                newOnes.isEmpty() -> tr("محفوظ", "Saved")
                valid.size == 5 -> tr("حفظ لكل الصلوات", "Save for all prayers")
                else -> tr("حفظ الاختيار (${valid.size})", "Save (${valid.size})")
            }
            GoldButton(
                label,
                onClick = { vm.assign(m, valid); onBack() },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp).fillMaxWidth(),
                enabled = newOnes.isNotEmpty(),
            )
        }
    }
    if (confirmDelete) {
        DeleteDialog(m.name(ar), onConfirm = { confirmDelete = false; vm.removeCustom(m); onBack() }, onDismiss = { confirmDelete = false })
    }
}

@Composable
private fun DeleteDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AC.Surface2,
        title = { T(tr("حذف الصوت؟", "Delete recording?"), 18.sp, FontWeight.Bold) },
        text = { T(tr("سيتم حذف «$name» من المكتبة ومن موبايلك. الصلوات اللي كانت بصوته هترجع للمؤذن الافتراضي.", "“$name” will be removed from the library and your phone. Prayers that used it go back to the default muezzin."), 14.sp, color = AC.TextDim, lineHeight = 22.sp) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onConfirm) { T(tr("حذف", "Delete"), 14.sp, FontWeight.Bold, AC.Danger) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { T(tr("إلغاء", "Cancel"), 14.sp, FontWeight.SemiBold, AC.Gold) } },
    )
}

@Composable
private fun RecordingCard(vm: AppViewModel, t: Track, title: String, subtitle: String, preview: PreviewState, dl: DownloadState) {
    val isThis = preview.key == t.key
    val playing = isThis && preview.playing
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(AC.Surface)
            .border(1.dp, if (isThis) AC.GoldLine else AC.Line, RoundedCornerShape(22.dp))
            .animateContentSize()
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayButton(playing, isThis && preview.loading, { vm.togglePreview(t) }, size = 48.dp, label = title)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                T(title, 15.sp, FontWeight.Bold)
                T(if (isThis && preview.error) tr("تعذّر التشغيل، تأكد من الإنترنت", "Couldn't play, check internet") else subtitle,
                    12.sp, color = if (isThis && preview.error) AC.Danger else AC.Muted, maxLines = 2)
            }
            Spacer(Modifier.width(8.dp))
            DownloadChip(dl, onDownload = { vm.download(t) }, onDelete = { vm.deleteDownload(t) })
        }
        AnimatedVisibility(isThis && !preview.loading && !preview.error, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 6.dp)) {
                val frac = if (preview.durationMs > 0) preview.positionMs.toFloat() / preview.durationMs else 0f
                GoldSlider(frac.coerceIn(0f, 1f), { vm.audio.seekPreview(it) })
                Row {
                    T(TimeFmt.mmss(preview.positionMs), 11.sp, color = AC.Muted)
                    Spacer(Modifier.weight(1f))
                    T(if (preview.durationMs > 0) TimeFmt.mmss(preview.durationMs) else "--:--", 11.sp, color = AC.Muted)
                }
            }
        }
        if (dl is DownloadState.Downloading) {
            LinearProgressIndicator(
                progress = { dl.progress },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                color = AC.Gold, trackColor = AC.Track,
            )
        }
    }
}

@Composable
private fun DownloadChip(dl: DownloadState, onDownload: () -> Unit, onDelete: () -> Unit) {
    val (icon, color, label, action) = when (dl) {
        DownloadState.Done -> Quad(AIcons.Check, AC.Mint, tr("محمّل على جهازك", "Saved on your phone"), {})
        is DownloadState.Downloading -> Quad(AIcons.Download, AC.Gold, tr("جارٍ التحميل", "Downloading"), {})
        DownloadState.Failed -> Quad(AIcons.Download, AC.Danger, tr("إعادة التحميل", "Retry download"), onDownload)
        DownloadState.None -> Quad(AIcons.Download, AC.Gold, tr("تحميل", "Download"), onDownload)
    }
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.10f))
            .pressClick(enabled = dl == DownloadState.None || dl == DownloadState.Failed, label = label, onClick = action),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = color, modifier = Modifier.size(20.dp))
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
