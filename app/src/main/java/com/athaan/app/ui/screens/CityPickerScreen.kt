package com.athaan.app.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athaan.app.data.Cities
import com.athaan.app.data.City
import com.athaan.app.ui.components.CircleIconButton
import com.athaan.app.ui.components.ScreenTitle
import com.athaan.app.ui.components.T
import com.athaan.app.ui.theme.AC
import com.athaan.app.ui.theme.AIcons
import com.athaan.app.ui.theme.LocalArabic
import com.athaan.app.ui.theme.Plex
import com.athaan.app.ui.theme.tr

@Composable
fun CityPickerScreen(onPick: (City) -> Unit, onUseGps: () -> Unit, onBack: () -> Unit) {
    val ar = LocalArabic.current
    var q by remember { mutableStateOf("") }
    val list = Cities.all.filter {
        val t = q.trim()
        t.isEmpty() || it.ar.contains(t) || it.en.contains(t, true) || it.countryAr.contains(t) || it.countryEn.contains(t, true)
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            CircleIconButton(AIcons.Back, tr("رجوع", "Back"), onBack)
        }
        ScreenTitle(tr("اختر مدينتك", "Choose your city"), tr("مفيد لو مسافر أو الـGPS مقفول", "Handy when travelling or without GPS"))

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
                if (q.isEmpty()) T(tr("ابحث عن مدينة", "Search for a city"), 15.sp, color = AC.Muted)
                BasicTextField(
                    value = q, onValueChange = { q = it }, singleLine = true,
                    textStyle = TextStyle(fontFamily = Plex, fontSize = 15.sp, color = AC.Text),
                    cursorBrush = SolidColor(AC.Gold), modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(AC.GoldSoft)
                        .clickable(onClick = onUseGps)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AIcons.Locate, null, tint = AC.Gold, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    T(tr("استخدام موقعي الحالي", "Use my current location"), 15.sp, FontWeight.SemiBold, AC.Gold)
                }
                Spacer(Modifier.height(8.dp))
            }
            items(list) { c ->
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
                        T(if (ar) c.ar else c.en, 15.sp, FontWeight.Medium)
                        T(if (ar) c.countryAr else c.countryEn, 12.sp, color = AC.Muted)
                    }
                }
            }
        }
    }
}
