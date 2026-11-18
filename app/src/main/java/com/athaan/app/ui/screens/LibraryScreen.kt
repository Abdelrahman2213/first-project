package com.athaan.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athaan.app.audio.DownloadState
import com.athaan.app.data.Muezzin
import com.athaan.app.data.Muezzins
import com.athaan.app.data.Region
import com.athaan.app.prayer.Prayer
import com.athaan.app.ui.AppViewModel
import com.athaan.app.ui.components.Avatar
import com.athaan.app.ui.components.CircleIconButton
import com.athaan.app.ui.components.Pill
import com.athaan.app.ui.components.ScreenTitle
import com.athaan.app.ui.components.T
import com.athaan.app.ui.theme.AC
import com.athaan.app.ui.theme.AIcons
import com.athaan.app.ui.theme.LocalArabic
import com.athaan.app.ui.theme.Plex
import com.athaan.app.ui.theme.tr

private enum class Filter { ALL, EGYPT, HARAMAIN, GULF, OTHER, OFFLINE }

/**
 * The muezzin library. With [pickFor] set, tapping a sheikh assigns him to that prayer and returns.
 */
@Composable
fun LibraryScreen(
    vm: AppViewModel,
    pickFor: Prayer?,
    onOpen: (Muezzin) -> Unit,
    onPicked: () -> Unit,
    onBack: (() -> Unit)?,
) {
    val s by vm.settings.collectAsState()
    val downloads by vm.downloads.collectAsState()
    val preview by vm.preview.collectAsState()
    val ar = LocalArabic.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(Filter.ALL) }

    val shown = Muezzins.all.filter { m ->
        val q = query.trim()
        val matchQ = q.isEmpty() || m.nameAr.contains(q) || m.nameEn.contains(q, ignoreCase = true)
        val matchF = when (filter) {
            Filter.ALL -> true
            Filter.EGYPT -> m.region == Region.EGYPT
            Filter.HARAMAIN -> m.region == Region.HARAMAIN
            Filter.GULF -> m.region == Region.GULF
            Filter.OTHER -> m.region == Region.OTHER
            Filter.OFFLINE -> downloads[m.id] == DownloadState.Done
        }
        matchQ && matchF
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (pickFor != null) {
                Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) CircleIconButton(AIcons.Back, tr("رجوع", "Back"), onBack)
                }
                ScreenTitle(
                    tr("اختر مؤذن ${pickFor.ar}", "Muezzin for ${pickFor.en}"),
                    tr("اضغط على الشيخ لاختياره", "Tap a sheikh to choose him"),
                )
            } else {
                ScreenTitle(tr("مكتبة المؤذنين", "Muezzin library"), tr("استمع، اختر، وحمّل للاستماع بدون إنترنت", "Listen, choose, and download for offline use"))
            }

            // Search
            Row(
                Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AC.Surface)
                    .border(1.dp, AC.Line2, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AIcons.Search, null, tint = AC.Muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) T(tr("ابحث باسم الشيخ", "Search by name"), 15.sp, color = AC.Muted)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = Plex, fontSize = 15.sp, color = AC.Text),
                        cursorBrush = SolidColor(AC.Gold),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Filters
            val chips = listOf(
                Filter.ALL to tr("الكل", "All"),
                Filter.EGYPT to (if (ar) Region.EGYPT.ar else Region.EGYPT.en),
                Filter.HARAMAIN to (if (ar) Region.HARAMAIN.ar else Region.HARAMAIN.en),
                Filter.GULF to (if (ar) Region.GULF.ar else Region.GULF.en),
                Filter.OTHER to (if (ar) Region.OTHER.ar else Region.OTHER.en),
                Filter.OFFLINE to tr("المحمّلة", "Downloaded"),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(chips) { (f, label) -> Pill(label, filter == f, { filter = f }) }
            }

            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = if (preview.muezzinId != null) 92.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (shown.isEmpty()) item {
                    T(tr("لا توجد نتائج", "No results"), 14.sp, color = AC.Muted, modifier = Modifier.padding(24.dp))
                }
                items(shown, key = { it.id }) { m ->
                    val usedFor = Prayer.adhanPrayers.filter { s.muezzinFor[it] == m.id }
                    MuezzinCard(
                        m = m,
                        dl = downloads[m.id] ?: DownloadState.None,
                        usedFor = usedFor,
                        playing = preview.muezzinId == m.id && preview.playing,
                        loading = preview.muezzinId == m.id && preview.loading,
                        selectedForPick = pickFor != null && s.muezzinFor[pickFor] == m.id,
                        onClick = {
                            if (pickFor != null) { vm.setMuezzin(pickFor, m.id); vm.stopPreview(); onPicked() } else onOpen(m)
                        },
                        onPlay = { vm.togglePreview(m) },
                    )
                }
            }
        }

        // Mini player
        val pm = preview.muezzinId?.let { Muezzins.byId(it) }
        if (pm != null) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(AC.Surface2)
                    .border(1.dp, AC.Gold.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(
                    if (preview.playing) AIcons.Pause else AIcons.Play,
                    tr("تشغيل/إيقاف", "Play/pause"), { vm.togglePreview(pm) },
                    size = 40.dp, iconSize = 16.dp, filled = true,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    T(tr("معاينة", "Preview"), 12.sp, color = AC.Muted)
                    T(pm.name(ar), 14.sp, FontWeight.SemiBold, maxLines = 1)
                }
                Box(
                    Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AC.Gold)
                        .clickable(role = Role.Button) {
                            if (pickFor != null) { vm.setMuezzin(pickFor, pm.id); vm.stopPreview(); onPicked() } else onOpen(pm)
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { T(tr("اختيار", "Choose"), 14.sp, FontWeight.Bold, AC.Ink) }
            }
        }
    }
}

@Composable
private fun MuezzinCard(
    m: Muezzin,
    dl: DownloadState,
    usedFor: List<Prayer>,
    playing: Boolean,
    loading: Boolean,
    selectedForPick: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
) {
    val ar = LocalArabic.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AC.Surface)
            .border(1.dp, if (selectedForPick) AC.Gold else AC.Line, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(m.initial, 52.dp, 20.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            T(m.name(ar), 15.sp, FontWeight.SemiBold, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val origin = m.origin(ar)
                if (origin.isNotEmpty()) T(origin, 12.sp, color = AC.Muted)
                when (dl) {
                    DownloadState.Done -> {
                        if (origin.isNotEmpty()) Spacer(Modifier.width(6.dp))
                        Icon(AIcons.Check, null, tint = AC.Mint, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        T(tr("محمّل", "Offline"), 12.sp, color = AC.Mint)
                    }
                    is DownloadState.Downloading -> {
                        if (origin.isNotEmpty()) Spacer(Modifier.width(6.dp))
                        T(tr("جارٍ التحميل ${(dl.progress * 100).toInt()}%", "Downloading ${(dl.progress * 100).toInt()}%"), 12.sp, color = AC.Gold)
                    }
                    else -> {}
                }
            }
            if (usedFor.isNotEmpty()) {
                val names = usedFor.joinToString(if (ar) "، " else ", ") { if (ar) it.ar else it.en }
                T(tr("مختار لـ$names", "Chosen for $names"), 11.sp, FontWeight.SemiBold, AC.Gold, maxLines = 1)
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (playing) AC.Gold else AC.Surface)
                .border(1.dp, if (playing) AC.Gold else AC.GoldLine, CircleShape)
                .clickable(role = Role.Button, onClickLabel = tr("معاينة", "Preview"), onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = AC.Gold, strokeWidth = 2.dp)
            else Icon(if (playing) AIcons.Pause else AIcons.Play, null, tint = if (playing) AC.Ink else AC.Gold, modifier = Modifier.size(16.dp))
        }
    }
}
