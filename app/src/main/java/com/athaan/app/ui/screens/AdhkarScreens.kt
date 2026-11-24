package com.athaan.app.ui.screens

import android.content.Context
import android.os.VibrationEffect
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athaan.app.data.Adhkar
import com.athaan.app.data.AdhkarSet
import com.athaan.app.data.CountStore
import com.athaan.app.ui.components.Pill
import com.athaan.app.ui.components.Segmented
import com.athaan.app.ui.components.T
import com.athaan.app.ui.components.pressClick
import com.athaan.app.ui.theme.AC
import com.athaan.app.ui.theme.AIcons
import com.athaan.app.ui.theme.Amiri
import com.athaan.app.ui.theme.LocalArabic
import com.athaan.app.ui.theme.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun longTick(context: Context) {
    runCatching { vibrator(context)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 90, 70, 140), -1)) }
}

/** A ring that fills as [progress] goes from 0 to 1, with content in the middle. */
@Composable
private fun CountRing(progress: Float, size: Dp, stroke: Dp, done: Boolean, content: @Composable () -> Unit) {
    val p by animateFloatAsState(progress, tween(260), label = "ring")
    val gold = AC.Gold; val mint = AC.Mint; val track = AC.Track
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(stroke / 2)) {
            drawCircle(track, style = Stroke(stroke.toPx()))
            drawArc(if (done) mint else gold, -90f, 360f * p, false, style = Stroke(stroke.toPx(), cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
private fun ConfirmReset(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AC.Surface2,
        title = { T(title, 18.sp, FontWeight.Bold) },
        text = { T(text, 14.sp, color = AC.TextDim, lineHeight = 22.sp) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onConfirm) { T(tr("ابدأ من الأول", "Start over"), 14.sp, FontWeight.Bold, AC.Gold) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { T(tr("إلغاء", "Cancel"), 14.sp, FontWeight.SemiBold, AC.TextDim) } },
    )
}

// ---------------------------------------------------------------- Adhkar

/**
 * Adhkar with a counter on every dhikr. Progress is saved as you go and starts fresh each
 * day; finishing a dhikr moves on to the next, and finishing the set records the day.
 */
@Composable
fun AdhkarScreen(initial: AdhkarSet, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { CountStore.get(context) }
    val scope = rememberCoroutineScope()
    var set by rememberSaveable { mutableStateOf(initial) }
    val list = Adhkar.of(set)
    val counts = remember(set) { mutableStateListOf<Int>().apply { addAll(store.adhkar(set, list.size).toList()) } }
    var streak by remember(set) { mutableIntStateOf(store.streak(set)) }
    var askReset by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val totalReps = list.sumOf { it.count }
    val doneReps = list.indices.sumOf { minOf(counts[it], list[it].count) }
    val doneItems = list.indices.count { counts[it] >= list[it].count }
    val allDone = doneItems == list.size

    fun save() = store.saveAdhkar(set, counts.toIntArray())

    fun count(i: Int) {
        val d = list[i]
        if (counts[i] >= d.count) return
        counts[i] = counts[i] + 1
        save()
        if (counts[i] >= d.count) {
            longTick(context)
            if (list.indices.all { counts[it] >= list[it].count }) {
                store.markCompleted(set)
                streak = store.streak(set)
                scope.launch { delay(350); listState.animateScrollToItem(list.size + 1) }
            } else {
                // Move on to the next dhikr that isn't finished yet.
                val next = (i + 1 until list.size).firstOrNull { counts[it] < list[it].count }
                    ?: (0 until i).firstOrNull { counts[it] < list[it].count }
                if (next != null) scope.launch { delay(380); listState.animateScrollToItem(next + 1) }
            }
        } else tick(context)
    }

    Column(Modifier.fillMaxSize()) {
        BackHeader(tr("الأذكار", "Adhkar"), tr("بيتحفظ اللي قريته، ويبدأ من جديد كل يوم", "Your progress is saved and starts fresh each day"), onBack)
        Segmented(
            AdhkarSet.values().map { it to tr(it.shortAr, it.shortEn) }, set, { set = it },
            Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
        )
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Summary: progress through the set, streak, start over.
            item(key = "summary_${set.name}") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(AC.Surface)
                        .border(1.dp, AC.GoldLine, RoundedCornerShape(24.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CountRing(if (totalReps == 0) 0f else doneReps.toFloat() / totalReps, 64.dp, 6.dp, allDone) {
                        T("${(100f * doneReps / totalReps.coerceAtLeast(1)).toInt()}%", 15.sp, FontWeight.Bold, if (allDone) AC.Mint else AC.Text)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        T(tr(set.ar, set.en), 17.sp, FontWeight.Bold)
                        T(tr("$doneItems من ${list.size} ذكر", "$doneItems of ${list.size} adhkar"), 13.sp, color = AC.TextDim)
                        if (streak > 0) T(
                            tr(if (streak == 1) "أتممتها النهارده" else "مداوم عليها $streak أيام", if (streak == 1) "Completed today" else "$streak-day streak"),
                            12.sp, FontWeight.SemiBold, AC.Gold, modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(AC.Surface2)
                            .pressClick(enabled = doneReps > 0, label = tr("ابدأ من الأول", "Start over")) { askReset = true },
                        contentAlignment = Alignment.Center,
                    ) { Icon(AIcons.Reset, tr("ابدأ من الأول", "Start over"), tint = if (doneReps > 0) AC.Gold else AC.Muted, modifier = Modifier.size(20.dp)) }
                }
            }

            itemsIndexed(list, key = { i, _ -> "${set.name}_$i" }) { i, d ->
                DhikrCard(
                    index = i + 1,
                    text = d.text, note = d.note, virtue = d.virtue,
                    count = counts[i], target = d.count,
                    onCount = { count(i) },
                    onUndo = { if (counts[i] > 0) { counts[i] = counts[i] - 1; save() } },
                    onReset = { counts[i] = 0; save() },
                )
            }

            item(key = "end_${set.name}") {
                AnimatedVisibility(allDone, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(AC.Mint.copy(alpha = 0.10f))
                            .border(1.dp, AC.Mint.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(AIcons.Check, null, tint = AC.Mint, modifier = Modifier.size(30.dp))
                        T(tr("أتممت ${set.ar}، تقبّل الله منك", "You finished the ${set.en.lowercase()}. May Allah accept it"), 16.sp, FontWeight.Bold,
                            align = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                        if (streak > 1) T(tr("مداوم عليها $streak أيام متتالية", "$streak days in a row"), 13.sp, color = AC.TextDim, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }

    if (askReset) {
        ConfirmReset(
            tr("تبدأ ${set.ar} من الأول؟", "Start the ${set.en.lowercase()} over?"),
            tr("هيتصفّر العدّ في كل الأذكار دي.", "All counts in this set go back to zero."),
            onConfirm = { for (i in counts.indices) counts[i] = 0; save(); askReset = false; scope.launch { listState.animateScrollToItem(0) } },
            onDismiss = { askReset = false },
        )
    }
}

@Composable
private fun DhikrCard(
    index: Int, text: String, note: String, virtue: String,
    count: Int, target: Int,
    onCount: () -> Unit, onUndo: () -> Unit, onReset: () -> Unit,
) {
    val done = count >= target
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(count) { if (count > 0) { pulse.snapTo(0.985f); pulse.animateTo(1f, spring(dampingRatio = 0.5f)) } }
    val border by animateColorAsState(if (done) AC.Mint.copy(alpha = 0.55f) else AC.Line, tween(300), label = "dkb")
    val textAlpha by animateFloatAsState(if (done) 0.6f else 1f, tween(300), label = "dka")
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }
            .clip(shape)
            .background(AC.Surface)
            .border(1.dp, border, shape)
            .pressClick(enabled = !done, role = Role.Button, label = tr("عدّ", "Count"), onClick = onCount)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(AC.GoldSoft), contentAlignment = Alignment.Center) {
                T("$index", 12.sp, FontWeight.Bold, AC.Gold)
            }
            Spacer(Modifier.width(10.dp))
            T(note, 13.sp, FontWeight.SemiBold, AC.Gold, maxLines = 1, modifier = Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(AC.Surface2).padding(horizontal = 10.dp, vertical = 4.dp)) {
                T(timesLabel(target, LocalArabic.current), 12.sp, FontWeight.SemiBold, AC.TextDim)
            }
        }
        androidx.compose.material3.Text(
            text, fontFamily = Amiri, fontSize = 19.sp, lineHeight = 36.sp, color = AC.Text.copy(alpha = textAlpha),
            modifier = Modifier.padding(top = 12.dp),
        )
        if (virtue.isNotEmpty()) {
            Row(Modifier.padding(top = 10.dp)) {
                Box(Modifier.padding(top = 7.dp).size(5.dp).clip(CircleShape).background(AC.Gold.copy(alpha = 0.6f)))
                Spacer(Modifier.width(8.dp))
                T(virtue, 12.sp, color = AC.Muted, lineHeight = 19.sp)
            }
        }
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            CountRing(count.toFloat() / target, 56.dp, 4.dp, done) {
                AnimatedContent(
                    targetState = done to count,
                    transitionSpec = { (fadeIn(tween(140)) + scaleIn(initialScale = 0.7f)) togetherWith (fadeOut(tween(90)) + scaleOut(targetScale = 1.3f)) },
                    label = "dkc",
                ) { (isDone, n) ->
                    if (isDone) Icon(AIcons.Check, tr("تم", "Done"), tint = AC.Mint, modifier = Modifier.size(24.dp))
                    else T("${target - n}", if (target - n >= 100) 15.sp else 18.sp, FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                T(
                    when {
                        done -> tr("تم بحمد الله", "Done")
                        count == 0 -> tr("اضغط على الكارت للعدّ", "Tap the card to count")
                        else -> tr("قرأت $count من $target", "$count of $target")
                    },
                    13.sp, FontWeight.SemiBold, if (done) AC.Mint else AC.TextDim,
                )
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(AC.Track)) {
                    val w by animateFloatAsState(count.toFloat() / target, tween(260), label = "dkw")
                    Box(Modifier.fillMaxWidth(w.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (done) AC.Mint else AC.Gold))
                }
            }
            if (count > 0) {
                Spacer(Modifier.width(8.dp))
                if (!done && target > 1) SmallIconButton(AIcons.Minus, tr("ناقص واحد", "Minus one"), onUndo)
                SmallIconButton(AIcons.Reset, tr("إعادة هذا الذكر", "Reset this dhikr"), onReset)
            }
        }
    }
}

/** "مرة واحدة", "مرتين", "3 مرات", "33 مرة". */
private fun timesLabel(n: Int, ar: Boolean): String = if (!ar) (if (n == 1) "Once" else "$n times") else when {
    n == 1 -> "مرة واحدة"
    n == 2 -> "مرتين"
    n in 3..10 -> "$n مرات"
    else -> "$n مرة"
}

@Composable
private fun SmallIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).pressClick(label = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = AC.TextDim, modifier = Modifier.size(19.dp)) }
}

// ---------------------------------------------------------------- Tasbih

/** A tasbih counter that remembers its count, today's total and each phrase's all-time total. */
@Composable
fun TasbihScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { CountStore.get(context) }
    var phrase by remember { mutableIntStateOf(store.tasbihPhrase.coerceIn(0, Adhkar.tasbih.lastIndex)) }
    var target by remember { mutableIntStateOf(store.tasbihTarget) }
    var count by remember(phrase) { mutableIntStateOf(store.tasbihCount(phrase)) }
    var total by remember(phrase) { mutableIntStateOf(store.tasbihTotal(phrase)) }
    var today by remember { mutableIntStateOf(store.tasbihToday()) }
    var askReset by remember { mutableStateOf(false) }
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(count) { if (count > 0) { pulse.snapTo(0.95f); pulse.animateTo(1f, spring(dampingRatio = 0.4f)) } }
    val inRound = if (target == 0) count else count % target
    val roundDone = target > 0 && count > 0 && count % target == 0

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.fillMaxWidth()) { BackHeader(tr("السبحة", "Tasbih"), tr("العدّ بيتحفظ حتى لو قفلت التطبيق", "Your count is kept even if you close the app"), onBack) }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(Adhkar.tasbih) { i, p -> Pill(p, phrase == i, { phrase = i; store.tasbihPhrase = i }) }
        }
        Segmented(listOf(33 to "33", 100 to "100", 1000 to "1000", 0 to "∞"), target, { target = it; store.tasbihTarget = it }, Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.weight(1f))
        AnimatedContent(phrase, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "phrase") {
            androidx.compose.material3.Text(Adhkar.tasbih[it], fontFamily = Amiri, fontSize = 26.sp, color = AC.Text, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
        }
        Spacer(Modifier.height(22.dp))
        Box(
            Modifier
                .size(250.dp)
                .graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }
                .clip(CircleShape)
                .background(AC.Surface)
                .border(1.dp, AC.GoldLine, CircleShape)
                .pressClick(role = Role.Button, label = tr("تسبيحة", "Count")) {
                    count++
                    total++
                    today++
                    store.addTasbih(phrase, count)
                    if (target > 0 && count % target == 0) longTick(context) else tick(context)
                },
            contentAlignment = Alignment.Center,
        ) {
            CountRing(if (target == 0) 0f else (if (roundDone) 1f else inRound.toFloat() / target), 250.dp, 10.dp, roundDone) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedContent(count, transitionSpec = { (fadeIn(tween(150)) + scaleIn(initialScale = 0.8f)) togetherWith (fadeOut(tween(100)) + scaleOut(targetScale = 1.2f)) }, label = "count") {
                        T("$it", 60.sp, FontWeight.Bold)
                    }
                    if (target > 0) T(
                        if (roundDone) tr("أتممت ${count / target} × $target", "${count / target} × $target done")
                        else tr("الدورة ${count / target + 1} · باقي ${target - inRound}", "Round ${count / target + 1} · ${target - inRound} left"),
                        13.sp, color = if (roundDone) AC.Mint else AC.Muted,
                    )
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            StatChip(tr("النهارده", "Today"), "$today")
            StatChip(tr("إجمالي الذكر ده", "All-time, this phrase"), "$total")
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionPill(AIcons.Minus, tr("ناقص واحد", "Minus one"), enabled = count > 0) {
                count--; total = (total - 1).coerceAtLeast(0); today = (today - 1).coerceAtLeast(0)
                store.undoTasbih(phrase, count)
            }
            ActionPill(AIcons.Reset, tr("تصفير", "Reset"), enabled = count > 0) { askReset = true }
        }
        Spacer(Modifier.weight(1f))
    }

    if (askReset) {
        ConfirmReset(
            tr("تصفير العدّاد؟", "Reset the counter?"),
            tr("العدّ الحالي هيرجع صفر. إجمالي النهارده والإجمالي الكلي مش هيتأثروا.", "The current count goes back to zero. Today's and all-time totals stay."),
            onConfirm = { count = 0; store.resetTasbih(phrase); askReset = false },
            onDismiss = { askReset = false },
        )
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AC.Surface)
            .border(1.dp, AC.Line, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        T(value, 18.sp, FontWeight.Bold)
        T(label, 11.sp, color = AC.Muted, maxLines = 1)
    }
}

@Composable
private fun ActionPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, enabled: Boolean, onClick: () -> Unit) {
    val fg by animateColorAsState(if (enabled) AC.Gold else AC.Muted, label = "ap")
    Row(
        Modifier
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .border(1.dp, AC.Line2, RoundedCornerShape(23.dp))
            .pressClick(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        T(text, 14.sp, FontWeight.SemiBold, fg)
    }
}
