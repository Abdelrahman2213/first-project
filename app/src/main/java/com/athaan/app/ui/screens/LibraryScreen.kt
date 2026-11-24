package com.athaan.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athaan.app.audio.DownloadState
import com.athaan.app.data.Muezzin
import com.athaan.app.data.Muezzins
import com.athaan.app.data.Places
import com.athaan.app.data.Region
import com.athaan.app.data.Track
import com.athaan.app.prayer.Prayer
import com.athaan.app.ui.AppViewModel
import com.athaan.app.ui.components.Avatar
import com.athaan.app.ui.components.CircleIconButton
import com.athaan.app.ui.components.Pill
import com.athaan.app.ui.components.PlayButton
import com.athaan.app.ui.components.ScreenTitle
import com.athaan.app.ui.components.T
import com.athaan.app.ui.components.pressClick
import com.athaan.app.ui.theme.AC
import com.athaan.app.ui.theme.AIcons
import com.athaan.app.ui.theme.LocalArabic
import com.athaan.app.ui.theme.Plex
import com.athaan.app.ui.theme.tr

enum class Filter { ALL, FAJR, EGYPT, HARAMAIN, GULF, OTHER, CUSTOM, OFFLINE }

/**
 * The library's scroll position, search text and filter. Kept by the caller (not inside the
 * screen) so opening a sheikh's page and coming back returns to exactly the same spot.
 */
class LibraryUiState {
    val list = androidx.compose.foundation.lazy.LazyListState()
    val chips = androidx.compose.foundation.lazy.LazyListState()
    var query by mutableStateOf("")
    var filter by mutableStateOf(Filter.ALL)
}

/**
 * The muezzin library. With [pickFor] set, only muezzins who can call that prayer are shown
 * (for Fajr: those with a Fajr adhan) and tapping one assigns him and returns.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    vm: AppViewModel,
    pickFor: Prayer?,
    onOpen: (Muezzin) -> Unit,
    onPicked: () -> Unit,
    onBack: (() -> Unit)?,
    onAdd: () -> Unit,
    ui: LibraryUiState = remember { LibraryUiState() },
) {
    val s by vm.settings.collectAsState()
    val downloads by vm.downloads.collectAsState()
    val preview by vm.preview.collectAsState()
    val ar = LocalArabic.current
    var query by ui::query
    var filter by ui::filter

    // The recording each card previews: the one that will actually be used.
    fun trackOf(m: Muezzin): Track = if (pickFor != null) m.trackFor(pickFor) else m.defaultTrack

    val q = Places.normalize(query)
    val shown = Muezzins.all.filter { m ->
        val matchPick = pickFor == null || m.canCall(pickFor)
        val matchQ = q.isEmpty() || Places.normalize(m.nameAr).contains(q) || Places.normalize(m.nameEn).contains(q)
        val matchF = when (filter) {
            Filter.ALL -> true
            Filter.FAJR -> m.hasFajr
            Filter.EGYPT -> m.region == Region.EGYPT
            Filter.HARAMAIN -> m.region == Region.HARAMAIN
            Filter.GULF -> m.region == Region.GULF
            Filter.OTHER -> m.region == Region.OTHER
            Filter.CUSTOM -> m.region == Region.CUSTOM
            Filter.OFFLINE -> downloads[trackOf(m).key] == DownloadState.Done
        }
        matchPick && matchQ && matchF
    }.let { l ->
        // While choosing, the current choice comes first.
        if (pickFor == null) l else { val cur = s.muezzin(pickFor).id; l.sortedByDescending { it.id == cur } }
    }
    val pickTotal = if (pickFor == null) 0 else Muezzins.all.count { it.canCall(pickFor) }

    fun choose(m: Muezzin) {
        if (pickFor != null) { vm.setMuezzin(pickFor, m.id); vm.stopPreview(); onPicked() } else onOpen(m)
    }

    Box(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.fillMaxSize()) {
            if (pickFor != null) {
                Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) CircleIconButton(AIcons.Back, tr("رجوع", "Back"), onBack)
                }
                ScreenTitle(
                    tr("أذان ${pickFor.ar}", "${pickFor.en} adhan"),
                    if (pickFor == Prayer.FAJR) tr("$pickTotal تسجيل لأذان الفجر · اسمع واختار", "$pickTotal Fajr recordings · listen and choose")
                    else tr("$pickTotal تسجيل للأذان العادي · اسمع واختار", "$pickTotal regular adhan recordings · listen and choose"),
                )
                Row(
                    Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AC.GoldSoft)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (pickFor == Prayer.FAJR) AIcons.MoonStar else AIcons.Mosque, null, tint = AC.Gold, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    T(
                        if (pickFor == Prayer.FAJR) tr("كل التسجيلات هنا أذان فجر، فيها «الصلاة خير من النوم».", "Every recording here is a Fajr adhan, with “prayer is better than sleep”.")
                        else tr("هنا الأذان العادي بس. تسجيلات الفجر مش ظاهرة لأنها مخصوصة لصلاة الفجر.", "Regular adhan only. Fajr recordings are kept for Fajr."),
                        12.sp, color = AC.TextDim, lineHeight = 18.sp,
                    )
                }
            } else {
                ScreenTitle(tr("مكتبة المؤذنين", "Muezzin library"), tr("${Muezzins.all.size} مؤذن · استمع، اختر، وحمّل للاستماع بدون إنترنت", "${Muezzins.all.size} muezzins · listen, choose, download for offline"))
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
            val chips = buildList {
                add(Filter.ALL to tr("الكل", "All"))
                if (pickFor == null) add(Filter.FAJR to tr("أذان الفجر", "Fajr adhan"))
                add(Filter.EGYPT to (if (ar) Region.EGYPT.ar else Region.EGYPT.en))
                add(Filter.HARAMAIN to (if (ar) Region.HARAMAIN.ar else Region.HARAMAIN.en))
                add(Filter.GULF to (if (ar) Region.GULF.ar else Region.GULF.en))
                add(Filter.OTHER to (if (ar) Region.OTHER.ar else Region.OTHER.en))
                if (Muezzins.all.any { it.isCustom }) add(Filter.CUSTOM to (if (ar) Region.CUSTOM.ar else Region.CUSTOM.en))
                add(Filter.OFFLINE to tr("المحمّلة", "Downloaded"))
            }
            LazyRow(
                state = ui.chips,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(chips, key = { it.first.name }) { (f, label) -> Pill(label, filter == f, { filter = f }) }
            }

            LazyColumn(
                Modifier.weight(1f),
                state = ui.list,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = if (preview.key != null) 92.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "add") {
                    Row(
                        Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(AC.GoldSoft)
                            .border(1.dp, AC.GoldLine, RoundedCornerShape(20.dp))
                            .pressClick(onClick = onAdd)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(AC.Gold), contentAlignment = Alignment.Center) {
                            T("+", 24.sp, FontWeight.Bold, AC.OnGold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            T(tr("إضافة صوت مؤذن", "Add a muezzin"), 15.sp, FontWeight.Bold)
                            T(tr("شيخ مش موجود أو نسخة تانية، من ملف على موبايلك", "A missing sheikh or another version, from a file on your phone"), 12.sp, color = AC.Muted, maxLines = 2)
                        }
                    }
                }
                if (shown.isEmpty()) item(key = "empty") {
                    T(tr("لا توجد نتائج", "No results"), 14.sp, color = AC.Muted, modifier = Modifier.padding(24.dp).animateItem())
                }
                items(shown, key = { it.id }) { m ->
                    val t = trackOf(m)
                    // Which prayers already use this same recording.
                    val usedFor = Prayer.adhanPrayers.filter { p ->
                        s.muezzin(p).id == m.id && (pickFor == null || (p == Prayer.FAJR) == (pickFor == Prayer.FAJR))
                    }
                    MuezzinCard(
                        m = m,
                        modifier = Modifier.animateItem(),
                        dl = downloads[t.key] ?: DownloadState.None,
                        usedFor = usedFor,
                        playing = preview.key == t.key && preview.playing,
                        loading = preview.key == t.key && preview.loading,
                        selectedForPick = pickFor != null && s.muezzin(pickFor).id == m.id,
                        pick = pickFor,
                        onClick = { choose(m) },
                        onPlay = { vm.togglePreview(t) },
                    )
                }
            }
        }

        // Mini player
        val pm = preview.key?.let { k -> Muezzins.find(k.removeSuffix("_fajr")) }
        val pmKey = if (pm != null) preview.key else null
        AnimatedContent(
            targetState = pmKey,
            modifier = Modifier.align(Alignment.BottomCenter),
            transitionSpec = {
                (slideInVertically(tween(300)) { it } + fadeIn()) togetherWith (slideOutVertically(tween(250)) { it } + fadeOut())
            },
            contentKey = { it?.removeSuffix("_fajr") },
            label = "miniPlayer",
        ) { key ->
            val m = key?.let { Muezzins.find(it.removeSuffix("_fajr")) } ?: return@AnimatedContent
            val isFajr = key.endsWith("_fajr")
            Row(
                Modifier
                    .padding(12.dp)
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(AC.Surface2)
                    .border(1.dp, AC.Gold.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayButton(preview.playing, preview.loading, { vm.togglePreview(Track(m, isFajr)) }, size = 40.dp, label = tr("تشغيل/إيقاف", "Play/pause"))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    T(
                        when {
                            preview.error -> tr("تعذّر التشغيل، تأكد من الإنترنت", "Couldn't play, check internet")
                            isFajr -> tr("معاينة · أذان الفجر", "Preview · Fajr adhan")
                            pickFor != null -> tr("معاينة · الأذان العادي", "Preview · regular adhan")
                            else -> tr("معاينة", "Preview")
                        },
                        12.sp, color = if (preview.error) AC.Danger else AC.Muted, maxLines = 1,
                    )
                    T(m.name(ar), 14.sp, FontWeight.SemiBold, maxLines = 1)
                }
                Box(
                    Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AC.Gold)
                        .pressClick { choose(m) }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { T(tr("اختيار", "Choose"), 14.sp, FontWeight.Bold, AC.OnGold) }
            }
        }
    }
}

@Composable
private fun MuezzinCard(
    m: Muezzin,
    modifier: Modifier,
    dl: DownloadState,
    usedFor: List<Prayer>,
    playing: Boolean,
    loading: Boolean,
    selectedForPick: Boolean,
    pick: Prayer? = null,
    onClick: () -> Unit,
    onPlay: () -> Unit,
) {
    val ar = LocalArabic.current
    val shape = RoundedCornerShape(20.dp)
    val border by animateColorAsState(if (selectedForPick || playing) AC.Gold else AC.Line, tween(250), label = "cardBr")
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AC.Surface)
            .border(1.dp, border, shape)
            .pressClick(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(m.initial, 52.dp, 20.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            T(m.name(ar), 15.sp, FontWeight.SemiBold, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val origin = m.origin(ar)
                if (origin.isNotEmpty()) T(origin, 12.sp, color = AC.Muted, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                when (dl) {
                    DownloadState.Done -> {
                        if (origin.isNotEmpty()) Spacer(Modifier.width(6.dp))
                        Icon(AIcons.Check, null, tint = AC.Mint, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        T(tr("محمّل", "Offline"), 12.sp, color = AC.Mint)
                    }
                    is DownloadState.Downloading -> {
                        if (origin.isNotEmpty()) Spacer(Modifier.width(6.dp))
                        T("${(dl.progress * 100).toInt()}%", 12.sp, color = AC.Gold)
                    }
                    else -> {}
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    selectedForPick -> {
                        Icon(AIcons.Check, null, tint = AC.Gold, modifier = Modifier.size(14.dp))
                        T(tr("اختيارك الحالي", "Current choice"), 11.sp, FontWeight.Bold, AC.Gold, maxLines = 1)
                    }
                    pick == Prayer.FAJR -> Badge(tr("فيه «الصلاة خير من النوم»", "With “better than sleep”"))
                    pick != null -> {}
                    m.hasFajr -> Badge(if (m.hasRegular) tr("+ أذان الفجر", "+ Fajr adhan") else tr("أذان الفجر فقط", "Fajr only"))
                }
                if (usedFor.isNotEmpty() && !selectedForPick) {
                    val names = usedFor.joinToString(if (ar) "، " else ", ") { if (ar) it.ar else it.en }
                    T(tr("مختار لـ$names", "For $names"), 11.sp, FontWeight.SemiBold, AC.Gold, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        PlayButton(playing, loading, onPlay, filled = false, label = tr("معاينة", "Preview"))
    }
}

@Composable
private fun Badge(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AC.GoldSoft)
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) { T(text, 10.sp, FontWeight.SemiBold, AC.Gold, maxLines = 1) }
}
