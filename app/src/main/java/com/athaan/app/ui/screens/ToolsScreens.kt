package com.athaan.app.ui.screens

import android.content.Context
import android.content.Intent
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athaan.app.data.Adhkar
import com.athaan.app.data.AdhkarSet
import com.athaan.app.prayer.HijriDate
import com.athaan.app.prayer.Prayer
import com.athaan.app.prayer.PrayerSchedule
import com.athaan.app.prayer.Qibla
import com.athaan.app.prayer.TimeFmt
import com.athaan.app.ui.AppViewModel
import com.athaan.app.ui.components.CircleIconButton
import com.athaan.app.ui.components.Pill
import com.athaan.app.ui.components.ScreenTitle
import com.athaan.app.ui.components.Segmented
import com.athaan.app.ui.components.T
import com.athaan.app.ui.components.pressClick
import com.athaan.app.ui.theme.AC
import com.athaan.app.ui.theme.AIcons
import com.athaan.app.ui.theme.Amiri
import com.athaan.app.ui.theme.LocalArabic
import com.athaan.app.ui.theme.tr
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Tool { QIBLA, ADHKAR, TASBIH, MONTH, HIJRI, MOSQUE }

// ---------------------------------------------------------------- hub

@Composable
fun ToolsScreen(onOpen: (Tool) -> Unit) {
    val context = LocalContext.current
    val items = listOf(
        Triple(Tool.QIBLA, AIcons.Compass, tr("اتجاه القبلة", "Qibla") to tr("بوصلة نحو الكعبة", "Compass to the Kaaba")),
        Triple(Tool.ADHKAR, AIcons.Book, tr("الأذكار", "Adhkar") to tr("الصباح والمساء وبعد الصلاة", "Morning, evening, after prayer")),
        Triple(Tool.TASBIH, AIcons.Beads, tr("السبحة", "Tasbih") to tr("عدّاد تسبيح", "Dhikr counter")),
        Triple(Tool.MONTH, AIcons.Calendar, tr("مواقيت الشهر", "Monthly times") to tr("جدول الصلوات لكل يوم", "Prayer times for each day")),
        Triple(Tool.HIJRI, AIcons.MoonStar, tr("التقويم الهجري", "Hijri calendar") to tr("المناسبات ورمضان والأيام البيض", "Ramadan, Eids and more")),
        Triple(Tool.MOSQUE, AIcons.Mosque, tr("أقرب مسجد", "Nearest mosque") to tr("يفتح الخريطة", "Opens your maps app")),
    )
    Column(Modifier.fillMaxSize()) {
        ScreenTitle(tr("الأدوات", "Tools"), tr("كل ما يساعدك على الصلاة في مكان واحد", "Everything for your prayer in one place"))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items) { (tool, icon, labels) ->
                Column(
                    Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(AC.Surface)
                        .border(1.dp, AC.Line, RoundedCornerShape(22.dp))
                        .pressClick { if (tool == Tool.MOSQUE) openMosques(context) else onOpen(tool) }
                        .padding(16.dp)
                        .heightIn(min = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(AC.GoldSoft), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = AC.Gold, modifier = Modifier.size(if (icon == AIcons.Mosque) 28.dp else 22.dp))
                    }
                    T(labels.first, 16.sp, FontWeight.Bold)
                    T(labels.second, 12.sp, color = AC.Muted, lineHeight = 18.sp)
                }
            }
        }
    }
}

private fun openMosques(context: Context) {
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode("مسجد")))
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/" + Uri.encode("مسجد")))
    runCatching { context.startActivity(geo.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { runCatching { context.startActivity(web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

@Composable
internal fun BackHeader(title: String, subtitle: String?, onBack: () -> Unit) {
    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) { CircleIconButton(AIcons.Back, tr("رجوع", "Back"), onBack) }
    ScreenTitle(title, subtitle)
}

// ---------------------------------------------------------------- Qibla

@Composable
fun QiblaScreen(vm: AppViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    val context = LocalContext.current
    val qibla = remember(s.lat, s.lng) { Qibla.bearing(s.lat, s.lng) }
    val km = remember(s.lat, s.lng) { Qibla.distanceKm(s.lat, s.lng) }
    var heading by remember { mutableFloatStateOf(Float.NaN) }
    var accuracyLow by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val decl = GeomagneticField(s.lat.toFloat(), s.lng.toFloat(), 0f, System.currentTimeMillis()).declination
        val rot = FloatArray(9); val ori = FloatArray(3)
        var smooth = Float.NaN
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, ori)
                val az = ((Math.toDegrees(ori[0].toDouble()).toFloat() + decl) + 360f) % 360f
                smooth = if (smooth.isNaN()) az else {
                    var d = az - smooth
                    if (d > 180) d -= 360f; if (d < -180) d += 360f
                    (smooth + d * 0.15f + 360f) % 360f
                }
                heading = smooth
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) { accuracyLow = a <= SensorManager.SENSOR_STATUS_ACCURACY_LOW }
        }
        if (sm != null && sensor != null) sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(l) }
    }

    val hasCompass = !heading.isNaN()
    val dialRot = if (hasCompass) -heading else 0f
    val anim by animateFloatAsState(dialRot, spring(stiffness = 120f), label = "dial")
    val diff = if (hasCompass) { var d = (qibla - heading).toFloat(); if (d > 180) d -= 360f; if (d < -180) d += 360f; d } else 999f
    val aligned = abs(diff) < 4f
    val ring by animateColorAsState(if (aligned) AC.Mint else AC.GoldLine, label = "ring")

    LaunchedEffect(aligned) { if (aligned) tick(context) }

    Column(Modifier.fillMaxSize()) {
        BackHeader(tr("اتجاه القبلة", "Qibla"), s.place(LocalArabic.current), onBack)
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 40.dp).aspectRatio(1f), contentAlignment = Alignment.Center) {
            val gold = AC.Gold; val line = AC.Line2; val text = AC.Muted; val mint = AC.Mint; val deco = AC.Deco
            Canvas(Modifier.fillMaxSize().rotate(anim)) {
                val c = Offset(size.width / 2, size.height / 2)
                val r = size.minDimension / 2
                drawCircle(ring, r - 2.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                for (i in 0 until 72) {
                    val a = Math.toRadians(i * 5.0 - 90)
                    val long = i % 18 == 0
                    val p1 = Offset(c.x + (r - 10.dp.toPx()) * Math.cos(a).toFloat(), c.y + (r - 10.dp.toPx()) * Math.sin(a).toFloat())
                    val p2 = Offset(c.x + (r - (if (long) 26 else 18).dp.toPx()) * Math.cos(a).toFloat(), c.y + (r - (if (long) 26 else 18).dp.toPx()) * Math.sin(a).toFloat())
                    drawLine(if (long) gold else line, p1, p2, strokeWidth = (if (long) 2 else 1).dp.toPx())
                }
                drawCircle(deco.copy(alpha = 0.22f), r * 0.42f, c, style = Stroke(1.5.dp.toPx()))
                drawCircle(deco.copy(alpha = 0.14f), r * 0.36f, c, style = Stroke(1.dp.toPx()))
                // Kaaba marker at the qibla bearing
                rotate(qibla.toFloat(), c) {
                    val tip = Offset(c.x, c.y - r + 34.dp.toPx())
                    drawLine(if (aligned) mint else gold, c, tip, strokeWidth = 3.dp.toPx())
                    val k = 14.dp.toPx()
                    drawRect(if (aligned) mint else gold, Offset(tip.x - k / 2, tip.y - k - 4.dp.toPx()), androidx.compose.ui.geometry.Size(k, k))
                }
                drawCircle(gold, 6.dp.toPx(), c)
            }
            // Fixed pointer at the top (the phone's direction)
            Canvas(Modifier.fillMaxSize()) {
                val p = Path().apply {
                    moveTo(size.width / 2, 0f); lineTo(size.width / 2 - 10.dp.toPx(), 18.dp.toPx()); lineTo(size.width / 2 + 10.dp.toPx(), 18.dp.toPx()); close()
                }
                drawPath(p, text)
            }
        }
        Spacer(Modifier.height(28.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(aligned, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "aligned") { ok ->
                T(
                    when {
                        !hasCompass -> tr("لا توجد بوصلة في هذا الجهاز", "This phone has no compass")
                        ok -> tr("أنت متجه إلى القبلة", "You are facing the Qibla")
                        else -> tr("لف الموبايل حتى يصل رمز الكعبة للسهم", "Turn until the Kaaba reaches the arrow")
                    },
                    18.sp, FontWeight.Bold, if (ok) AC.Mint else AC.Text, align = TextAlign.Center,
                )
            }
            T(tr("القبلة ${qibla.roundToInt()}° من الشمال · ${"%,d".format(km.roundToInt())} كم إلى مكة", "Qibla ${qibla.roundToInt()}° from north · ${"%,d".format(km.roundToInt())} km to Makkah"),
                13.sp, color = AC.Muted, align = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            if (accuracyLow) T(tr("البوصلة تحتاج معايرة: حرّك الموبايل على شكل رقم 8", "Calibrate: move the phone in a figure 8"),
                12.sp, color = AC.Danger, align = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
            T(tr("ابعد الموبايل عن المعادن والمغناطيس لدقة أفضل", "Keep away from metal and magnets"), 12.sp, color = AC.Muted, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

private fun vibrator(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

private fun tick(context: Context) {
    runCatching { vibrator(context)?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)) }
}

// ---------------------------------------------------------------- Adhkar

@Composable
fun AdhkarScreen(initial: AdhkarSet, onBack: () -> Unit) {
    val context = LocalContext.current
    var set by rememberSaveable { mutableStateOf(initial) }
    val counts = remember(set) { mutableStateMapOf<Int, Int>() }
    val list = Adhkar.of(set)
    val listState = rememberLazyListState()
    val done = list.indices.count { (counts[it] ?: 0) >= list[it].count }

    Column(Modifier.fillMaxSize()) {
        BackHeader(tr("الأذكار", "Adhkar"), tr("اضغط على الذكر لعدّه · $done من ${list.size}", "Tap a dhikr to count it · $done of ${list.size}"), onBack)
        Segmented(
            AdhkarSet.values().map { it to tr(it.ar, it.en) }, set, { set = it },
            Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
        )
        LazyColumn(state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(list, key = { i, _ -> "${set.name}$i" }) { i, d ->
                val n = counts[i] ?: 0
                val finished = n >= d.count
                val border by animateColorAsState(if (finished) AC.Mint.copy(alpha = 0.5f) else AC.Line, label = "dk")
                val fade by animateFloatAsState(if (finished) 0.55f else 1f, label = "dkf")
                Column(
                    Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .graphicsLayer { alpha = fade }
                        .clip(RoundedCornerShape(20.dp))
                        .background(AC.Surface)
                        .border(1.dp, border, RoundedCornerShape(20.dp))
                        .pressClick {
                            if (!finished) {
                                counts[i] = n + 1
                                tick(context)
                            }
                        }
                        .padding(16.dp),
                ) {
                    if (d.note.isNotEmpty()) T(d.note, 12.sp, FontWeight.SemiBold, AC.Gold, modifier = Modifier.padding(bottom = 6.dp))
                    androidx.compose.material3.Text(d.text, fontFamily = Amiri, fontSize = 18.sp, lineHeight = 34.sp, color = AC.Text)
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.clip(RoundedCornerShape(12.dp)).background(if (finished) AC.Mint.copy(alpha = 0.15f) else AC.GoldSoft).padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            T(if (finished) tr("تم ✓", "Done ✓") else "${d.count - n}", 14.sp, FontWeight.Bold, if (finished) AC.Mint else AC.Gold)
                        }
                        Spacer(Modifier.width(10.dp))
                        T(tr("التكرار: ${d.count}", "Repeat: ${d.count}"), 12.sp, color = AC.Muted)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Tasbih

@Composable
fun TasbihScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var phrase by rememberSaveable { mutableIntStateOf(0) }
    var target by rememberSaveable { mutableIntStateOf(33) }
    var count by rememberSaveable { mutableIntStateOf(0) }
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(count) { if (count > 0) { pulse.snapTo(0.94f); pulse.animateTo(1f, spring(dampingRatio = 0.4f)) } }
    val progress by animateFloatAsState(if (target == 0) 0f else (count % target).toFloat() / target, label = "tp")

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.fillMaxWidth()) { BackHeader(tr("السبحة", "Tasbih"), null, onBack) }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(Adhkar.tasbih) { i, p -> Pill(p, phrase == i, { phrase = i; count = 0 }) }
        }
        Segmented(listOf(33 to "33", 100 to "100", 0 to "∞"), target, { target = it; count = 0 }, Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.weight(1f))
        AnimatedContent(phrase, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "phrase") {
            androidx.compose.material3.Text(Adhkar.tasbih[it], fontFamily = Amiri, fontSize = 24.sp, color = AC.Text, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
        }
        Spacer(Modifier.height(20.dp))
        val gold = AC.Gold; val track = AC.Track
        Box(
            Modifier
                .size(240.dp)
                .graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }
                .clip(CircleShape)
                .background(AC.Surface)
                .pressClick(role = Role.Button, label = tr("تسبيحة", "Count")) {
                    count++
                    if (target > 0 && count % target == 0) longTick(context) else tick(context)
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                drawCircle(track, style = Stroke(8.dp.toPx()))
                drawArc(gold, -90f, 360f * progress, false, style = Stroke(8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedContent(count, transitionSpec = { (fadeIn(tween(150)) + scaleIn(initialScale = 0.8f)) togetherWith (fadeOut(tween(100)) + scaleOut(targetScale = 1.2f)) }, label = "count") {
                    T("$it", 56.sp, FontWeight.Bold)
                }
                if (target > 0) T(tr("الدورة ${count / target + 1}", "Round ${count / target + 1}"), 13.sp, color = AC.Muted)
            }
        }
        Spacer(Modifier.height(20.dp))
        T(tr("إعادة العدّ", "Reset"), 14.sp, FontWeight.SemiBold, AC.Gold, modifier = Modifier.clip(RoundedCornerShape(14.dp)).pressClick { count = 0 }.padding(horizontal = 18.dp, vertical = 12.dp))
        Spacer(Modifier.weight(1f))
    }
}

private fun longTick(context: Context) {
    runCatching { vibrator(context)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 120, 80, 120), -1)) }
}

// ---------------------------------------------------------------- Month timetable

@Composable
fun MonthTimesScreen(vm: AppViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    val ar = LocalArabic.current
    val tz = s.zone()
    var monthShift by rememberSaveable { mutableIntStateOf(0) }
    val now = System.currentTimeMillis()
    val first = remember(monthShift, tz) {
        Calendar.getInstance(tz).apply {
            timeInMillis = now; set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 12); add(Calendar.MONTH, monthShift)
        }
    }
    val days = first.getActualMaximum(Calendar.DAY_OF_MONTH)
    val todayKey = Calendar.getInstance(tz).let { "${it.get(Calendar.YEAR)}-${it.get(Calendar.DAY_OF_YEAR)}" }
    val rows = remember(s, monthShift) {
        (0 until days).map { d ->
            val c = (first.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, d) }
            Triple(c, PrayerSchedule.day(s, 0, c.timeInMillis), "${c.get(Calendar.YEAR)}-${c.get(Calendar.DAY_OF_YEAR)}")
        }
    }
    val monthName = HijriDate.gregorianShort(first.timeInMillis, ar, tz).substringAfter(" ") + " " + first.get(Calendar.YEAR)
    val listState = rememberLazyListState()
    LaunchedEffect(monthShift) { if (monthShift == 0) listState.scrollToItem((Calendar.getInstance(tz).get(Calendar.DAY_OF_MONTH) - 3).coerceAtLeast(0)) }

    Column(Modifier.fillMaxSize()) {
        BackHeader(tr("مواقيت الشهر", "Monthly times"), s.place(ar), onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(AIcons.Forward, tr("الشهر السابق", "Previous month"), { monthShift-- }, size = 40.dp, iconSize = 18.dp)
            AnimatedContent(monthName, transitionSpec = { fadeIn() togetherWith fadeOut() }, modifier = Modifier.weight(1f), label = "month") {
                T(it, 18.sp, FontWeight.Bold, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            CircleIconButton(AIcons.Back, tr("الشهر التالي", "Next month"), { monthShift++ }, size = 40.dp, iconSize = 18.dp)
        }
        val cols = Prayer.values().toList()
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 6.dp)) {
            T(tr("اليوم", "Day"), 11.sp, FontWeight.SemiBold, AC.Muted, Modifier.width(52.dp))
            cols.forEach { T(if (ar) it.ar else it.en, 11.sp, FontWeight.SemiBold, AC.Muted, Modifier.weight(1f), align = TextAlign.Center, maxLines = 1) }
        }
        LazyColumn(state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            items(rows) { (c, dt, key) ->
                val today = key == todayKey
                val friday = c.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (today) AC.GoldSoft else if (friday) AC.Line else AC.Ink.copy(alpha = 0f))
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T("${c.get(Calendar.DAY_OF_MONTH)}", 14.sp, if (today) FontWeight.Bold else FontWeight.Medium, if (today) AC.Gold else AC.Text, Modifier.width(52.dp), align = TextAlign.Center)
                    cols.forEach { p ->
                        T(TimeFmt.clock(dt[p], ar, tz).substringBefore(" "), 13.sp, if (today) FontWeight.SemiBold else FontWeight.Normal,
                            if (today) AC.Text else AC.TextDim, Modifier.weight(1f), align = TextAlign.Center, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Hijri calendar & occasions

private data class Occasion(val ar: String, val en: String, val month: Int, val day: Int, val days: Int = 1)

private val OCCASIONS = listOf(
    Occasion("رأس السنة الهجرية", "Islamic New Year", 0, 1),
    Occasion("يوم عاشوراء", "Day of Ashura", 0, 10),
    Occasion("بداية شهر رمضان", "Start of Ramadan", 8, 1),
    Occasion("العشر الأواخر من رمضان", "Last ten nights of Ramadan", 8, 21),
    Occasion("عيد الفطر", "Eid al-Fitr", 9, 1),
    Occasion("بداية العشر من ذي الحجة", "First ten days of Dhul-Hijjah", 11, 1),
    Occasion("يوم عرفة", "Day of Arafah", 11, 9),
    Occasion("عيد الأضحى", "Eid al-Adha", 11, 10),
)

@Composable
fun HijriCalendarScreen(vm: AppViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    val ar = LocalArabic.current
    val tz = s.zone()
    val now = System.currentTimeMillis()

    // Walk the next 400 days once and note where each Hijri date falls.
    val upcoming = remember(tz.id) {
        val found = mutableListOf<Pair<Occasion, Long>>()
        val white = mutableListOf<Long>()
        val c = Calendar.getInstance(tz).apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, 12) }
        for (i in 0 until 400) {
            val h = PrayerSchedule.hijri(c.timeInMillis, tz) ?: break
            OCCASIONS.firstOrNull { it.month == h.second && it.day == h.third }?.let { o ->
                if (found.none { it.first == o }) found += o to c.timeInMillis
            }
            if (h.third == 13 && white.size < 1) white += c.timeInMillis
            c.add(Calendar.DAY_OF_YEAR, 1)
        }
        found.sortedBy { it.second } to white.firstOrNull()
    }
    val (events, whiteDays) = upcoming
    val today = PrayerSchedule.hijri(now, tz)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackHeader(tr("التقويم الهجري", "Hijri calendar"), HijriDate.hijriLine(now, ar, tz), onBack) }
        item {
            Column(
                Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(AC.Surface).border(1.dp, AC.GoldLine, RoundedCornerShape(24.dp)).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                T("${today?.third ?: ""}", 56.sp, kufi = true, color = AC.Gold, lineHeight = 66.sp)
                T(HijriDate.hijriDate(now, ar, tz).substringAfter(" "), 18.sp, FontWeight.Bold)
                T(HijriDate.weekdayGregorian(now, ar, tz), 13.sp, color = AC.Muted)
                if (today?.second == 8) T(tr("رمضان كريم", "Ramadan Kareem"), 14.sp, FontWeight.SemiBold, AC.Gold, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item { T(tr("المناسبات القادمة", "Upcoming occasions"), 15.sp, FontWeight.SemiBold, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp)) }
        val all = (events.map { Triple(if (ar) it.first.ar else it.first.en, it.second, false) } +
            listOfNotNull(whiteDays?.let { Triple(if (ar) "الأيام البيض (13 و14 و15)" else "White days (13–15)", it, true) })).sortedBy { it.second }
        items(all) { (name, at, _) ->
            val daysLeft = ((at - now) / 86_400_000L).toInt()
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AC.Surface).border(1.dp, AC.Line, RoundedCornerShape(18.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    T(name, 15.sp, FontWeight.SemiBold)
                    T(HijriDate.hijriDate(at, ar, tz) + " · " + HijriDate.weekdayGregorian(at, ar, tz), 12.sp, color = AC.Muted)
                }
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(AC.GoldSoft).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    T(if (daysLeft <= 0) tr("اليوم", "Today") else tr("بعد $daysLeft يوم", "in $daysLeft d"), 12.sp, FontWeight.Bold, AC.Gold)
                }
            }
        }
        item {
            T(tr("التواريخ حسب تقويم أم القرى، وقد تختلف يومًا عن رؤية الهلال في بلدك.", "Dates follow the Umm al-Qura calendar and may differ by a day from local moon sighting."),
                12.sp, color = AC.Muted, modifier = Modifier.padding(24.dp), lineHeight = 18.sp)
        }
    }
}
