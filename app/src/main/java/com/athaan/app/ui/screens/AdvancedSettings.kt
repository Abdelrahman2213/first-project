package com.athaan.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athaan.app.prayer.Prayer
import com.athaan.app.prayer.TimeFmt
import com.athaan.app.ui.AppViewModel
import com.athaan.app.ui.components.ACard
import com.athaan.app.ui.components.GoldSwitch
import com.athaan.app.ui.components.ListRow
import com.athaan.app.ui.components.SectionLabel
import com.athaan.app.ui.components.T
import com.athaan.app.ui.components.pressClick
import com.athaan.app.ui.theme.AC
import com.athaan.app.ui.theme.LocalArabic
import com.athaan.app.ui.theme.tr
import java.util.Calendar

/** A round − / + button. */
@Composable
private fun StepButton(plus: Boolean, enabled: Boolean, label: String, onClick: () -> Unit) {
    val bg by animateColorAsState(if (enabled) AC.Gold.copy(alpha = 0.14f) else AC.Line, label = "step")
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(bg)
            .pressClick(enabled = enabled, label = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        T(if (plus) "+" else "−", 20.sp, FontWeight.SemiBold, if (enabled) AC.Gold else AC.Muted)
    }
}

/** − value + with a rolling value in the middle. */
@Composable
private fun Stepper(value: Int, min: Int, max: Int, step: Int, format: (Int) -> String, onChange: (Int) -> Unit) {
    val ar = LocalArabic.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton(false, value > min, if (ar) "إنقاص" else "Decrease") { onChange((value - step).coerceAtLeast(min)) }
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                val up = targetState > initialState
                (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith (slideOutVertically { if (up) -it else it } + fadeOut())
            },
            label = "stepper",
            modifier = Modifier.width(88.dp),
            contentAlignment = Alignment.Center,
        ) { v ->
            T(format(v), 14.sp, FontWeight.SemiBold, if (v == 0) AC.TextDim else AC.Text, align = TextAlign.Center, maxLines = 1,
                modifier = Modifier.fillMaxWidth())
        }
        StepButton(true, value < max, if (ar) "زيادة" else "Increase") { onChange((value + step).coerceAtMost(max)) }
    }
}

@Composable
fun TuningScreen(vm: AppViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    val ar = LocalArabic.current
    val now = remember { System.currentTimeMillis() }
    val today = vm.dayTimes(s, 0, now)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        BackHeader(
            tr("ضبط الدقائق والإقامة", "Adjustments & iqama"),
            tr("لو مسجدك بيأذّن قبل أو بعد الحساب بدقايق", "If your mosque calls a few minutes earlier or later"),
            onBack,
        )
        SectionLabel(tr("تقديم أو تأخير الأذان", "Shift the adhan time"))
        ACard(Modifier.padding(horizontal = 16.dp)) {
            Column {
                Prayer.values().forEachIndexed { i, p ->
                    val off = s.offsets[p] ?: 0
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            T(if (ar) p.ar else p.en, 15.sp, FontWeight.Medium)
                            T(TimeFmt.clock(today[p], ar, s.zone()), 12.sp, color = AC.Gold)
                        }
                        Stepper(off, -60, 60, 1, { if (it == 0) (if (ar) "بدون" else "None") else TimeFmt.signedMinutes(it, ar) }) { vm.setOffset(p, it) }
                    }
                    if (i < Prayer.values().size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(AC.Line))
                }
            }
        }
        T(tr("الوقت المكتوب تحت كل صلاة هو بعد التعديل. التعديل بيطبّق على الأذان والتنبيهات والويدجت.",
            "The time under each prayer already includes the shift. It applies to the adhan, reminders and widget."),
            12.sp, color = AC.Muted, lineHeight = 18.sp, modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 8.dp))

        SectionLabel(tr("الإقامة بعد الأذان", "Iqama after the adhan"))
        ACard(Modifier.padding(horizontal = 16.dp)) {
            Column {
                Prayer.adhanPrayers.forEachIndexed { i, p ->
                    val iq = s.iqama[p] ?: 0
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            T(prayerName(p, now, s.zone(), ar), 15.sp, FontWeight.Medium)
                            if (iq > 0) T(tr("تنبيه الإقامة ", "Iqama at ") + TimeFmt.clock(today[p] + iq * 60_000L, ar, s.zone()), 12.sp, color = AC.Gold)
                            else T(tr("بدون تنبيه", "No reminder"), 12.sp, color = AC.Muted)
                        }
                        Stepper(iq, 0, 60, 5, { if (it == 0) (if (ar) "بدون" else "Off") else (if (ar) "$it د" else "$it min") }) { vm.setIqama(p, it) }
                    }
                    if (i < Prayer.adhanPrayers.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(AC.Line))
                }
            }
        }
    }
}

/** Saturday-first week, as most Arab calendars show it. */
private val WEEK = listOf(Calendar.SATURDAY, Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
private fun dayName(d: Int, ar: Boolean) = when (d) {
    Calendar.SATURDAY -> if (ar) "السبت" else "Sat"
    Calendar.SUNDAY -> if (ar) "الأحد" else "Sun"
    Calendar.MONDAY -> if (ar) "الاثنين" else "Mon"
    Calendar.TUESDAY -> if (ar) "الثلاثاء" else "Tue"
    Calendar.WEDNESDAY -> if (ar) "الأربعاء" else "Wed"
    Calendar.THURSDAY -> if (ar) "الخميس" else "Thu"
    else -> if (ar) "الجمعة" else "Fri"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuietHoursScreen(vm: AppViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    val ar = LocalArabic.current
    val fmt: (Int) -> String = { TimeFmt.minuteOfDay(it, ar) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        BackHeader(
            tr("وقت الشغل", "Quiet hours"),
            tr("في الأوقات دي الأذان يوصلك إشعار بس من غير صوت", "During these hours the adhan arrives as a silent notification"),
            onBack,
        )
        Spacer(Modifier.height(14.dp))
        ACard(Modifier.padding(horizontal = 16.dp)) {
            ListRow(null, tr("تفعيل وقت الشغل", "Enable quiet hours"), null, divider = false) {
                GoldSwitch(s.quietEnabled) { vm.setQuiet(enabled = it) }
            }
        }
        SectionLabel(tr("الوقت", "Hours"))
        ACard(Modifier.padding(horizontal = 16.dp)) {
            Column {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(tr("من", "From"), 15.sp, FontWeight.Medium, modifier = Modifier.weight(1f))
                    Stepper(s.quietStart, 0, 24 * 60 - 15, 15, fmt) { vm.setQuiet(start = it) }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(AC.Line))
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(tr("إلى", "To"), 15.sp, FontWeight.Medium, modifier = Modifier.weight(1f))
                    Stepper(s.quietEnd, 0, 24 * 60 - 15, 15, fmt) { vm.setQuiet(end = it) }
                }
            }
        }
        SectionLabel(tr("الأيام", "Days"))
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WEEK.forEach { d ->
                val on = d in s.quietDays
                val bg by animateColorAsState(if (on) AC.Gold else AC.Surface, label = "day")
                val fg by animateColorAsState(if (on) AC.OnGold else AC.TextDim, label = "dayfg")
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(bg)
                        .border(1.dp, if (on) AC.Gold else AC.Line2, RoundedCornerShape(50))
                        .pressClick(role = Role.Checkbox) { vm.setQuiet(days = if (on) s.quietDays - d else s.quietDays + d) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) { T(dayName(d, ar), 13.sp, FontWeight.SemiBold, fg) }
            }
        }
        val summary = when {
            !s.quietEnabled -> tr("وقت الشغل مقفول دلوقتي.", "Quiet hours are off.")
            s.quietDays.isEmpty() -> tr("اختار يوم واحد على الأقل.", "Pick at least one day.")
            s.quietStart == s.quietEnd -> tr("وقت البداية زي وقت النهاية، فمش هيأثر على حاجة.", "Start and end are the same, so nothing is silenced.")
            else -> tr(
                "الأذان هيوصل إشعار صامت من ${fmt(s.quietStart)} لحد ${fmt(s.quietEnd)} في ${WEEK.filter { it in s.quietDays }.joinToString("، ") { dayName(it, true) }}.",
                "Adhans arrive silently from ${fmt(s.quietStart)} to ${fmt(s.quietEnd)} on ${WEEK.filter { it in s.quietDays }.joinToString(", ") { dayName(it, false) }}.",
            )
        }
        T(summary, 13.sp, color = AC.TextDim, lineHeight = 20.sp, modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 18.dp))
    }
}
