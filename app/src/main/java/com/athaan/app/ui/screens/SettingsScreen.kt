package com.athaan.app.ui.screens

import android.content.Intent
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
fun SettingsScreen(vm: AppViewModel, onPickCity: () -> Unit, onUseLocation: () -> Unit) {
    val s by vm.settings.collectAsState()
    val loc by vm.locStatus.collectAsState()
    val locating = loc == com.athaan.app.ui.LocStatus.LOCATING
    val ar = LocalArabic.current
    val context = LocalContext.current
    var showMethod by remember { mutableStateOf(false) }
    var showMadhab by remember { mutableStateOf(false) }
    var canExact by remember { mutableStateOf(AdhanScheduler.canExact(context)) }

    LifecycleResumeEffect(Unit) {
        canExact = AdhanScheduler.canExact(context)
        if (canExact) AdhanScheduler.reschedule(context)
        onPauseOrDispose { }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
                                context.startActivity(
                                    Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM", Uri.parse("package:${context.packageName}"))
                                )
                            }
                        },
                        divider = false,
                    ) { Icon(AIcons.Forward, null, tint = AC.Muted, modifier = Modifier.size(18.dp)) }
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
                ListRow(
                    AIcons.Locate, tr("تحديث موقعي الحالي", "Use my current location"), locStatusError(loc),
                    onClick = onUseLocation,
                ) {
                    if (locating) CircularProgressIndicator(Modifier.size(18.dp), color = AC.Gold, strokeWidth = 2.dp)
                }
                ListRow(
                    AIcons.Clock, tr("طريقة حساب المواقيت", "Calculation method"),
                    if (ar) s.method.ar else s.method.en, onClick = { showMethod = true },
                ) { Icon(AIcons.Forward, null, tint = AC.Muted, modifier = Modifier.size(18.dp)) }
                ListRow(
                    AIcons.Afternoon, tr("وقت العصر", "Asr time"),
                    if (ar) s.madhab.ar else s.madhab.en, onClick = { showMadhab = true }, divider = false,
                ) { Icon(AIcons.Forward, null, tint = AC.Muted, modifier = Modifier.size(18.dp)) }
            }
        }

        SectionLabel(tr("التنبيه قبل الصلاة", "Reminder before prayer"))
        Segmented(
            listOf(0 to tr("بدون", "Off"), 5 to tr("5 د", "5 min"), 10 to tr("10 د", "10 min"), 15 to tr("15 د", "15 min")),
            s.preAlertMinutes, { vm.setPreAlert(it) }, Modifier.padding(horizontal = 16.dp),
        )

        SectionLabel(tr("الصوت والتشغيل", "Sound & playback"))
        ACard(Modifier.padding(horizontal = 16.dp)) {
            Column {
                ListRow(null, tr("العمل بدون إنترنت", "Work offline"), tr("تحميل أذان كل صلاة على الجهاز", "Download each prayer's adhan to the phone")) {
                    GoldSwitch(s.autoDownload) { vm.setAutoDownload(it) }
                }
                ListRow(null, tr("الوضع الصامت", "Silent mode"), tr("إشعار فقط بدون صوت الأذان", "Notification only, no adhan sound")) {
                    GoldSwitch(s.silent) { vm.setSilent(it) }
                }
                ListRow(null, tr("الاهتزاز", "Vibration"), tr("مع بداية الأذان", "When the adhan starts")) {
                    GoldSwitch(s.vibrate) { vm.setVibrate(it) }
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AIcons.Volume, null, tint = AC.Gold, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    T(tr("مستوى الصوت", "Volume"), 15.sp, FontWeight.Medium)
                    Spacer(Modifier.width(12.dp))
                    GoldSlider(s.volume, { vm.setVolume(it) }, Modifier.weight(1f))
                }
                Box(Modifier.fillMaxWidth().heightIn(min = 1.dp, max = 1.dp).background(AC.Line))
                ListRow(AIcons.Play, tr("تجربة الأذان الآن", "Test the adhan now"), tr("يشغّل أذان الصلاة القادمة", "Plays the next prayer's adhan"),
                    onClick = { vm.testAdhan(vm.nextInfo(s, System.currentTimeMillis()).prayer) })
                ListRow(AIcons.MoonStar, tr("تجربة أذان الفجر", "Test the Fajr adhan"), s.muezzin(com.athaan.app.prayer.Prayer.FAJR).name(ar),
                    onClick = { vm.testAdhan(com.athaan.app.prayer.Prayer.FAJR) }, divider = false)
            }
        }

        SectionLabel(tr("تشغيل في الخلفية", "Background"))
        ACard(Modifier.padding(horizontal = 16.dp)) {
            ListRow(
                AIcons.Battery, tr("استثناء من توفير البطارية", "Battery optimisation"),
                tr("على بعض الهواتف لازم تستثني التطبيق عشان الأذان ميتأخرش", "Some phones need this so the adhan isn't delayed"),
                onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } },
                divider = false,
            ) { Icon(AIcons.Forward, null, tint = AC.Muted, modifier = Modifier.size(18.dp)) }
        }

        SectionLabel(tr("المظهر", "Appearance"))
        Segmented(listOf(false to tr("ليلي", "Dark"), true to tr("فاتح", "Light")), s.light, { vm.setLight(it) }, Modifier.padding(horizontal = 16.dp))

        SectionLabel(tr("اللغة", "Language"))
        Segmented(listOf(true to "العربية", false to "English"), s.arabic, { vm.setArabic(it) }, Modifier.padding(horizontal = 16.dp))

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
