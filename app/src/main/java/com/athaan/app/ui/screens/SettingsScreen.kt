package com.athaan.app.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.Color
import com.athaan.app.data.AdhanLength
import com.athaan.app.data.HeroPattern
import com.athaan.app.data.LightPalette
import com.athaan.app.prayer.TimeFmt
import com.athaan.app.ui.components.GoldSlider
import com.athaan.app.ui.components.HeroOrnament
import com.athaan.app.ui.components.pressClick
import com.athaan.app.ui.theme.lightColors
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.athaan.app.prayer.AsrMadhab
import com.athaan.app.prayer.CalcMethod
import com.athaan.app.schedule.AdhanScheduler
import com.athaan.app.ui.AppViewModel
import com.athaan.app.ui.components.ACard
import com.athaan.app.ui.components.GoldSwitch
import com.athaan.app.ui.components.ListRow
import com.athaan.app.ui.components.ScreenTitle
import com.athaan.app.ui.components.SectionLabel
import com.athaan.app.ui.components.Segmented
import com.athaan.app.ui.components.GoldSlider
import com.athaan.app.ui.components.T
import com.athaan.app.ui.theme.AC
import com.athaan.app.ui.theme.AIcons
import com.athaan.app.ui.theme.LocalArabic
import com.athaan.app.ui.theme.tr

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onPickCity: () -> Unit,
    onUseLocation: () -> Unit,
    onTuning: () -> Unit,
    onQuiet: () -> Unit,
    scroll: androidx.compose.foundation.ScrollState = rememberScrollState(),
) {
    val s by vm.settings.collectAsState()
    val loc by vm.locStatus.collectAsState()
    val locating = loc == com.athaan.app.ui.LocStatus.LOCATING
    val ar = LocalArabic.current
    val context = LocalContext.current
    var showMethod by remember { mutableStateOf(false) }
    var showMadhab by remember { mutableStateOf(false) }
    var canExact by remember { mutableStateOf(AdhanScheduler.canExact(context)) }
    var backupMsg by remember { mutableStateOf<String?>(null) }
    val msgExported = tr("تم حفظ نسخة الإعدادات", "Settings saved")
    val msgImported = tr("تم استرجاع الإعدادات", "Settings restored")
    val msgFailed = tr("تعذّر قراءة أو كتابة الملف", "Couldn't read or write the file")

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportSettings(uri) { ok -> backupMsg = if (ok) msgExported else msgFailed }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importSettings(uri) { ok -> backupMsg = if (ok) msgImported else msgFailed }
    }

    LifecycleResumeEffect(Unit) {
        canExact = AdhanScheduler.canExact(context)
        if (canExact) AdhanScheduler.reschedule(context)
        onPauseOrDispose { }
    }

    @Composable
    fun Chevron() = Icon(AIcons.Forward, null, tint = AC.Muted, modifier = Modifier.size(18.dp))

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(bottom = 24.dp)
    ) {
        ScreenTitle(tr("الإعدادات", "Settings"))

        if (!canExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp)) {
                ACard(border = AC.Danger.copy(alpha = 0.5f)) {
                    ListRow(
                        AIcons.Alarm,
                        tr("اسمح بالمنبّهات الدقيقة", "Allow exact alarms"),
                        tr("مطلوب ليشتغل الأذان في وقته بالظبط", "Needed so the adhan plays on time"),
                        onClick = {
                            runCatching {
                                context.startActivity(Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM", Uri.parse("package:${context.packageName}")))
                            }
                        },
                        divider = false,
                    ) { Chevron() }
                }
            }
        }

        SectionLabel(tr("الموقع والحساب", "Location & calculation"))
        ACard(Modifier.padding(horizontal = 16.dp)) {
            Column {
                ListRow(
                    AIcons.Pin, tr("الموقع", "Location"),
                    (if (s.manualLocation) tr("يدوي · ", "Manual · ") else tr("تلقائي · ", "Automatic · ")) + s.place(ar),
                    onClick = onPickCity,
                ) { T(tr("تغيير", "Change"), 13.sp, FontWeight.SemiBold, AC.Gold) }
                ListRow(AIcons.Locate, tr("تحديث موقعي الحالي", "Use my current location"), locStatusError(loc), onClick = onUseLocation) {
                    if (locating) CircularProgressIndicator(Modifier.size(18.dp), color = AC.Gold, strokeWidth = 2.dp)
                }
                ListRow(AIcons.Clock, tr("طريقة حساب المواقيت", "Calculation method"), if (ar) s.method.ar else s.method.en, onClick = { showMethod = true }) { Chevron() }
                ListRow(AIcons.Afternoon, tr("وقت العصر", "Asr time"), if (ar) s.madhab.ar else s.madhab.en, onClick = { showMadhab = true }) { Chevron() }
                val changed = s.offsets.values.count { it != 0 } + s.iqama.values.count { it > 0 }
                ListRow(AIcons.Tune, tr("ضبط الدقائق والإقامة", "Minute adjustments & iqama"),
                    if (changed == 0) tr("طابق مواقيت مسجدك، وحدد وقت الإقامة لكل صلاة", "Match your mosque, set the iqama for each prayer")
                    else tr("$changed تعديل", "$changed changes"),
                    onClick = onTuning, divider = false) { Chevron() }
            }
        }

        SectionLabel(tr("الأذان", "Adhan"))
        ACard(Modifier.padding(horizontal = 16.dp)) {
            Column {
                Column(Modifier.padding(16.dp)) {
                    T(tr("طول الأذان", "Adhan length"), 15.sp, FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
                    Segmented(AdhanLength.values().map { it to tr(it.ar, it.en) }, s.adhanLength, { vm.setAdhanLength(it) })
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(AC.Line))
                ListRow(null, tr("الصوت يعلى بالتدريج", "Gentle fade-in"), tr("يبدأ هادي ويعلى خلال 6 ثواني", "Starts softly and rises over 6 seconds")) {
                    GoldSwitch(s.fadeIn) { vm.setFadeIn(it) }
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(AIcons.Volume, null, tint = AC.Gold, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    T(tr("مستوى الصوت", "Volume"), 15.sp, FontWeight.Medium)
                    Spacer(Modifier.width(12.dp))
                    GoldSlider(s.volume, { vm.setVolume(it) }, Modifier.weight(1f))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(AC.Line))
                ListRow(null, tr("الوضع الصامت", "Silent mode"), tr("إشعار فقط بدون صوت الأذان", "Notification only, no adhan sound")) {
                    GoldSwitch(s.silent) { vm.setSilent(it) }
                }
                ListRow(null, tr("الاهتزاز", "Vibration"), tr("مع بداية الأذان", "When the adhan starts")) { GoldSwitch(s.vibrate) { vm.setVibrate(it) } }
                ListRow(null, tr("العمل بدون إنترنت", "Work offline"), tr("تحميل أذان كل صلاة على الجهاز", "Download each prayer's adhan to the phone")) {
                    GoldSwitch(s.autoDownload) { vm.setAutoDownload(it) }
                }
                ListRow(AIcons.Briefcase, tr("وقت الشغل", "Quiet hours"),
                    if (s.quietEnabled) tr("مفعّل · ${TimeFmt.minuteOfDay(s.quietStart, true)} إلى ${TimeFmt.minuteOfDay(s.quietEnd, true)}", "On · ${TimeFmt.minuteOfDay(s.quietStart, false)} to ${TimeFmt.minuteOfDay(s.quietEnd, false)}")
                    else tr("إشعار بدون صوت في أوقات تحددها", "Notification only during set hours"),
                    onClick = onQuiet) { Chevron() }
                ListRow(AIcons.Play, tr("تجربة الأذان الآن", "Test the adhan now"), tr("يشغّل أذان الصلاة القادمة", "Plays the next prayer's adhan"),
                    onClick = { vm.testAdhan(vm.nextInfo(s, System.currentTimeMillis()).prayer) })
                ListRow(AIcons.MoonStar, tr("تجربة أذان الفجر", "Test the Fajr adhan"), s.muezzin(com.athaan.app.prayer.Prayer.FAJR).name(ar),
                    onClick = { vm.testAdhan(com.athaan.app.prayer.Prayer.FAJR) }, divider = false)
            }
        }

        SectionLabel(tr("إيقاف الأذان أثناء تشغيله", "Pausing a playing adhan"))
        ACard(Modifier.padding(horizontal = 16.dp)) {
            Column {
                ListRow(null, tr("زراير الصوت", "Volume keys"), tr("الضغط على تعلية أو توطية الصوت يوقف الأذان", "Pressing volume up or down pauses it")) {
                    GoldSwitch(s.volumeKeysPause) { vm.setVolumeKeysPause(it) }
                }
                ListRow(null, tr("زرار القفل", "Power key"), tr("قفل الشاشة يوقف الأذان", "Turning the screen off pauses it")) {
                    GoldSwitch(s.screenOffPause) { vm.setScreenOffPause(it) }
                }
                ListRow(null, tr("قلب الموبايل على وشه", "Turn face down"), tr("يوقف الأذان", "Pauses it"), divider = false) {
                    GoldSwitch(s.flipToSilence) { vm.setFlipToSilence(it) }
                }
            }
        }
        T(tr("بعد الإيقاف تقدر تكمّل الأذان من الإشعار أو من التطبيق، ولو مرّت 10 دقايق بيتقفل لوحده.", "After pausing you can resume from the notification or the app; it ends by itself after 10 minutes."),
            12.sp, color = AC.Muted, lineHeight = 18.sp, modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 8.dp))

        SectionLabel(tr("التنبيهات", "Reminders"))
        ACard(Modifier.padding(horizontal = 16.dp)) {
            Column {
                Column(Modifier.padding(16.dp)) {
                    T(tr("قبل الأذان", "Before the adhan"), 15.sp, FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
                    Segmented(listOf(0 to tr("بدون", "Off"), 5 to tr("5 د", "5 min"), 10 to tr("10 د", "10 min"), 15 to tr("15 د", "15 min")), s.preAlertMinutes, { vm.setPreAlert(it) })
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(AC.Line))
                Column(Modifier.padding(16.dp)) {
                    T(tr("صلاة الجمعة", "Jumu'ah"), 15.sp, FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
                    Segmented(listOf(0 to tr("بدون", "Off"), 30 to tr("قبلها بنص ساعة", "30 min"), 60 to tr("قبلها بساعة", "1 hour")), s.fridayMinutes, { vm.setFridayMinutes(it) })
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(AC.Line))
                Column(Modifier.padding(16.dp)) {
                    T(tr("السحور في رمضان", "Suhoor in Ramadan"), 15.sp, FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
                    Segmented(listOf(0 to tr("بدون", "Off"), 30 to tr("30 د", "30 min"), 45 to tr("45 د", "45 min"), 60 to tr("ساعة", "1 hour")), s.suhoorMinutes, { vm.setSuhoor(it) })
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(AC.Line))
                ListRow(null, tr("سورة الكهف يوم الجمعة", "Surat al-Kahf on Friday"), tr("تذكير الساعة 9:30 الصبح", "Reminder at 9:30 AM")) { GoldSwitch(s.kahfReminder) { vm.setKahf(it) } }
                ListRow(null, tr("أذكار الصباح والمساء", "Morning & evening adhkar"), tr("بعد الفجر والعصر", "After Fajr and Asr")) { GoldSwitch(s.adhkarReminders) { vm.setAdhkarReminders(it) } }
                ListRow(null, tr("إشعار ثابت بالعدّاد", "Always-on countdown"), tr("الصلاة القادمة والوقت الباقي في الإشعارات", "Next prayer and time left in the notification shade"), divider = false) {
                    GoldSwitch(s.persistentCountdown) { vm.setPersistentCountdown(it) }
                }
            }
        }

        SectionLabel(tr("المظهر", "Appearance"))
        Segmented(listOf(false to tr("ليلي", "Dark"), true to tr("فاتح", "Light")), s.light, { vm.setLight(it) }, Modifier.padding(horizontal = 16.dp))
        T(tr("ألوان الوضع الفاتح", "Light theme colours"), 13.sp, FontWeight.Medium, AC.TextDim, modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 14.dp, bottom = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(LightPalette.values().toList()) { p ->
                val lc = lightColors(p)
                val on = s.palette == p
                val br by animateColorAsState(if (on) AC.Gold else AC.Line2, label = "pal")
                Column(
                    Modifier
                        .width(112.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(if (on) 2.dp else 1.dp, br, RoundedCornerShape(18.dp))
                        .pressClick(role = Role.RadioButton) { vm.setPalette(p); if (!s.light) vm.setLight(true) }
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp)).background(Color(lc.bg))) {
                        Box(Modifier.align(Alignment.Center).width(70.dp).height(30.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)) {
                            Box(Modifier.align(Alignment.CenterStart).padding(start = 8.dp).size(12.dp).clip(CircleShape).background(Color(lc.accent)))
                            Box(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).width(28.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(lc.deco).copy(alpha = 0.6f)))
                        }
                    }
                    T(tr(p.ar, p.en), 11.sp, if (on) FontWeight.Bold else FontWeight.Medium, if (on) AC.Gold else AC.Text, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        T(tr("زخرفة كارت الصلاة", "Prayer card ornament"), 13.sp, FontWeight.Medium, AC.TextDim, modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(HeroPattern.values().toList()) { p ->
                val on = s.pattern == p
                val br by animateColorAsState(if (on) AC.Gold else AC.Line2, label = "pat")
                Column(
                    Modifier
                        .width(112.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(AC.Surface)
                        .border(if (on) 2.dp else 1.dp, br, RoundedCornerShape(18.dp))
                        .pressClick(role = Role.RadioButton) { vm.setPattern(p) }
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        HeroOrnament(p, Modifier.size(96.dp))
                    }
                    T(tr(p.ar, p.en), 11.sp, if (on) FontWeight.Bold else FontWeight.Medium, if (on) AC.Gold else AC.Text, maxLines = 2,
                        align = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        SectionLabel(tr("اللغة", "Language"))
        Segmented(listOf(true to "العربية", false to "English"), s.arabic, { vm.setArabic(it) }, Modifier.padding(horizontal = 16.dp))

        SectionLabel(tr("نسخة احتياطية", "Backup"))
        ACard(Modifier.padding(horizontal = 16.dp)) {
            Column {
                ListRow(AIcons.Backup, tr("حفظ الإعدادات في ملف", "Save settings to a file"), tr("تقدر ترجعها على أي موبايل", "Restore them on any phone"),
                    onClick = { runCatching { exportLauncher.launch("athaan-settings.json") } }) { Chevron() }
                ListRow(AIcons.Download, tr("استرجاع الإعدادات من ملف", "Restore settings from a file"), backupMsg,
                    onClick = { runCatching { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) } }, divider = false) { Chevron() }
            }
        }

        SectionLabel(tr("تشغيل في الخلفية", "Background"))
        ACard(Modifier.padding(horizontal = 16.dp)) {
            ListRow(
                AIcons.Battery, tr("استثناء من توفير البطارية", "Battery optimisation"),
                tr("على بعض الهواتف لازم تستثني التطبيق عشان الأذان ميتأخرش", "Some phones need this so the adhan isn't delayed"),
                onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } },
                divider = false,
            ) { Chevron() }
        }

        T(
            tr("تسجيلات الأذان من مستودعات عامة على GitHub · بيانات المدن من GeoNames (CC BY 4.0) · الخطوط برخصة OFL",
                "Adhan recordings from public GitHub repositories · City data from GeoNames (CC BY 4.0) · Fonts under the OFL"),
            11.sp, color = AC.Muted, modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 24.dp),
        )
    }

    if (showMethod) {
        ChoiceDialog(
            title = tr("طريقة حساب المواقيت", "Calculation method"),
            options = CalcMethod.values().map { it to if (ar) it.ar else it.en },
            selected = s.method,
            onSelect = { vm.setMethod(it); showMethod = false },
            onDismiss = { showMethod = false },
        )
    }
    if (showMadhab) {
        ChoiceDialog(
            title = tr("وقت العصر", "Asr time"),
            options = AsrMadhab.values().map { it to if (ar) it.ar else it.en },
            selected = s.madhab,
            onSelect = { vm.setMadhab(it); showMadhab = false },
            onDismiss = { showMadhab = false },
        )
    }
}

@Composable
private fun <V> ChoiceDialog(title: String, options: List<Pair<V, String>>, selected: V, onSelect: (V) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AC.Surface2,
        title = { T(title, 18.sp, FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (v, label) ->
                    val on = v == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.RadioButton) { onSelect(v) }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(20.dp).clip(CircleShape).background(if (on) AC.Gold else AC.Track),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Icon(AIcons.Check, null, tint = AC.OnGold, modifier = Modifier.size(14.dp)) }
                        Spacer(Modifier.width(12.dp))
                        T(label, 15.sp, if (on) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { T(tr("إغلاق", "Close"), 14.sp, FontWeight.SemiBold, AC.Gold) }
        },
    )
}
