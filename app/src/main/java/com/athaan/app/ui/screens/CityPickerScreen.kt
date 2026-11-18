package com.athaan.app.ui.screens

import android.content.Context
import android.telephony.TelephonyManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athaan.app.data.City
import com.athaan.app.data.Country
import com.athaan.app.data.Places
import com.athaan.app.ui.LocStatus
import com.athaan.app.ui.components.CircleIconButton
import com.athaan.app.ui.components.LanguageToggle
import com.athaan.app.ui.components.T
import com.athaan.app.ui.theme.AC
import com.athaan.app.ui.theme.AIcons
import com.athaan.app.ui.theme.LocalArabic
import com.athaan.app.ui.theme.Plex
import com.athaan.app.ui.theme.tr
import java.text.Collator
import java.util.Locale

/**
 * Manual location: pick a country, then a city in it. Works offline from bundled data.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CityPickerScreen(
    onPick: (City) -> Unit,
    onUseGps: () -> Unit,
    onBack: () -> Unit,
    status: LocStatus,
    onToggleLanguage: () -> Unit,
) {
    val locating = status == LocStatus.LOCATING
    val locError = locStatusError(status)
    val ar = LocalArabic.current
    val context = LocalContext.current
    var countryCode by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = countryCode != null) { countryCode = null }

    val countries by produceState<List<Country>?>(null) { value = Places.countries(context) }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(AIcons.Back, tr("رجوع", "Back"), { if (countryCode != null) countryCode = null else onBack() })
            Spacer(Modifier.weight(1f))
            LanguageToggle(ar, onToggleLanguage)
        }

        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        AnimatedContent(
            targetState = countryCode,
            transitionSpec = {
                val forward = targetState != null
                val dir = if (forward xor rtl) 1 else -1
                (slideInHorizontally(tween(320)) { it * dir / 3 } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(260)) { -it * dir / 3 } + fadeOut(tween(200)))
            },
            label = "picker",
        ) { code ->
            val list = countries
            when {
                list == null -> Loading()
                code == null -> CountryStep(list, ar, context, locating, locError, onUseGps) { countryCode = it.code }
                else -> {
                    val country = list.firstOrNull { it.code == code } ?: Country(code, code, 0)
                    CityStep(country, ar, onPick)
                }
            }
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AC.Gold) }
}

@Composable
private fun SearchBox(value: String, onChange: (String) -> Unit, hint: String) {
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
            if (value.isEmpty()) T(hint, 15.sp, color = AC.Muted)
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(fontFamily = Plex, fontSize = 15.sp, color = AC.Text),
                cursorBrush = SolidColor(AC.Gold), modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun deviceCountry(context: Context): String? {
    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    val fromSim = runCatching { tm?.networkCountryIso?.takeIf { it.isNotBlank() } ?: tm?.simCountryIso }.getOrNull()
    return (fromSim?.takeIf { it.isNotBlank() } ?: Locale.getDefault().country.takeIf { it.isNotBlank() })?.uppercase()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CountryStep(all: List<Country>, ar: Boolean, context: Context, locating: Boolean, locError: String?, onUseGps: () -> Unit, onSelect: (Country) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    val sorted = remember(all, ar) {
        val coll = Collator.getInstance(if (ar) Locale("ar") else Locale.ENGLISH)
        all.sortedWith(compareBy(coll) { it.name(ar) })
    }
    val home = remember(all) { deviceCountry(context)?.let { c -> all.firstOrNull { it.code == c } } }
    val nq = Places.normalize(q)
    val shown = if (nq.isEmpty()) sorted else sorted.filter {
        Places.normalize(it.name(true)).contains(nq) || Places.normalize(it.name(false)).contains(nq) || it.code.equals(q.trim(), true)
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp)) {
            T(tr("اختر دولتك", "Choose your country"), 30.sp, kufi = true, lineHeight = 42.sp)
            T(tr("بعدها تختار مدينتك · مفيد لو مسافر أو الـGPS مقفول", "Then pick your city · handy when travelling or without GPS"), 13.sp, color = AC.Muted)
        }
        SearchBox(q, { q = it }, tr("ابحث عن دولة", "Search for a country"))
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            if (nq.isEmpty()) {
                item(key = "gps") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(AC.GoldSoft)
                            .clickable(enabled = !locating, onClick = onUseGps)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (locating) CircularProgressIndicator(Modifier.size(20.dp), color = AC.Gold, strokeWidth = 2.dp)
                        else Icon(AIcons.Locate, null, tint = AC.Gold, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        T(if (locating) tr("جارٍ تحديد موقعك…", "Finding your location…") else tr("استخدام موقعي الحالي", "Use my current location"), 15.sp, FontWeight.SemiBold, AC.Gold)
                    }
                    if (locError != null && !locating) {
                        T(locError, 12.sp, color = AC.Danger, lineHeight = 18.sp, modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (home != null) {
                    item(key = "homeLabel") { T(tr("دولتك", "Your country"), 12.sp, FontWeight.SemiBold, AC.Muted, Modifier.padding(start = 14.dp, top = 6.dp, bottom = 2.dp)) }
                    item(key = "home") { CountryRow(home, ar, Modifier.animateItem()) { onSelect(home) } }
                    item(key = "allLabel") { T(tr("كل الدول", "All countries"), 12.sp, FontWeight.SemiBold, AC.Muted, Modifier.padding(start = 14.dp, top = 10.dp, bottom = 2.dp)) }
                }
            }
            if (shown.isEmpty()) item(key = "none") { T(tr("لا توجد نتائج", "No results"), 14.sp, color = AC.Muted, modifier = Modifier.padding(14.dp)) }
            items(shown, key = { it.code }) { c -> CountryRow(c, ar, Modifier.animateItem()) { onSelect(c) } }
        }
    }
}

@Composable
private fun CountryRow(c: Country, ar: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(flag(c.code), 22.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            T(c.name(ar), 15.sp, FontWeight.Medium, maxLines = 1)
            T(tr("${c.cityCount} مدينة", "${c.cityCount} cities"), 12.sp, color = AC.Muted)
        }
        Icon(AIcons.Forward, null, tint = AC.Muted, modifier = Modifier.size(18.dp))
    }
}

/** Regional-indicator flag emoji for an ISO country code. */
private fun flag(code: String): String =
    if (code.length != 2) "" else code.uppercase().map { Character.toChars(0x1F1E6 + (it - 'A')) }.joinToString("") { String(it) }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CityStep(country: Country, ar: Boolean, onPick: (City) -> Unit) {
    val context = LocalContext.current
    var q by rememberSaveable(country.code) { mutableStateOf("") }
    val cities by produceState<List<City>?>(null, country.code) { value = Places.cities(context, country.code) }
    val nq = Places.normalize(q)
    val list = cities
    val shown = remember(list, nq) {
        when {
            list == null -> emptyList()
            nq.isEmpty() -> list
            else -> list.filter { Places.normalize(it.nameAr).contains(nq) || Places.normalize(it.nameEn).contains(nq) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            T(flag(country.code), 28.sp)
            Spacer(Modifier.width(10.dp))
            Column {
                T(tr("اختر مدينتك", "Choose your city"), 30.sp, kufi = true, lineHeight = 42.sp)
                T(country.name(ar), 13.sp, color = AC.Muted)
            }
        }
        SearchBox(q, { q = it }, tr("ابحث عن مدينة أو قرية", "Search for a city or town"))
        if (list == null) { Loading(); return@Column }
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            if (shown.isEmpty()) item(key = "none") { T(tr("لا توجد نتائج", "No results"), 14.sp, color = AC.Muted, modifier = Modifier.padding(14.dp)) }
            items(shown) { c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onPick(c) }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AIcons.Pin, null, tint = AC.Muted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        T(c.name(ar), 15.sp, FontWeight.Medium, maxLines = 1)
                        val other = if (ar) c.nameEn else c.nameAr
                        if (other.isNotEmpty() && other != c.name(ar)) T(other, 12.sp, color = AC.Muted, maxLines = 1)
                    }
                }
            }
        }
    }
}
