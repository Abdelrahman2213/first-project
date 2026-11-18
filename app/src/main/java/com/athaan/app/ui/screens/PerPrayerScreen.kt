package com.athaan.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athaan.app.audio.DownloadState
import com.athaan.app.prayer.Prayer
import com.athaan.app.prayer.TimeFmt
import com.athaan.app.ui.AppViewModel
import com.athaan.app.ui.components.Avatar
import com.athaan.app.ui.components.GoldSwitch
import com.athaan.app.ui.components.ScreenTitle
import com.athaan.app.ui.components.SmallOutlineButton
import com.athaan.app.ui.components.T
import com.athaan.app.ui.theme.AC
import com.athaan.app.ui.theme.AIcons
import com.athaan.app.ui.theme.LocalArabic
import com.athaan.app.ui.theme.tr

@Composable
fun PerPrayerScreen(vm: AppViewModel, onChange: (Prayer) -> Unit) {
    val s by vm.settings.collectAsState()
    val now by vm.now.collectAsState()
    val downloads by vm.downloads.collectAsState()
    val ar = LocalArabic.current
    val today = remember(s, now / 60_000L) { vm.dayTimes(s, 0, now) }
    val tz = s.zone()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        ScreenTitle(tr("إعدادات الأذان", "Adhan settings"), tr("اختر المؤذن المفضل لكل صلاة", "Choose a muezzin for each prayer"))
        Spacer(Modifier.height(18.dp))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Prayer.adhanPrayers.forEach { p ->
                val on = s.enabled[p] == true
                val m = s.muezzin(p)
                val track = m.trackFor(p)
                val dl = downloads[track.key] ?: DownloadState.None
                val shape = RoundedCornerShape(22.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(AC.Surface)
                        .border(1.dp, AC.Line, shape)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(AC.GoldSoft),
                            contentAlignment = Alignment.Center,
                        ) { Icon(AIcons.forPrayer(p), null, tint = AC.Gold, modifier = Modifier.size(20.dp)) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            T(if (ar) p.ar else p.en, 16.sp, FontWeight.Bold)
                            T(TimeFmt.clock(today[p], ar, tz) + if (p == Prayer.FAJR) tr(" · أذان الفجر", " · Fajr adhan") else "", 12.sp, color = AC.Muted)
                        }
                        GoldSwitch(on) { vm.setEnabled(p, it) }
                    }
                    Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(1.dp).background(AC.Line))
                    val dim by androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0.5f, label = "dim")
                    Row(
                        Modifier.padding(top = 10.dp).alpha(dim),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(m.initial, 32.dp, 13.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            T(m.title(ar), 14.sp, FontWeight.SemiBold, maxLines = 1)
                            when (dl) {
                                DownloadState.Done -> T(tr("محمّل · يعمل بدون إنترنت", "Downloaded · works offline"), 11.sp, color = AC.Mint)
                                is DownloadState.Downloading -> T(tr("جارٍ التحميل ${(dl.progress * 100).toInt()}%", "Downloading ${(dl.progress * 100).toInt()}%"), 11.sp, color = AC.Gold)
                                DownloadState.Failed -> Row(
                                    Modifier.clickable(role = Role.Button) { vm.download(track) },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) { T(tr("فشل التحميل · إعادة المحاولة", "Download failed · retry"), 11.sp, color = AC.Danger) }
                                DownloadState.None -> Row(
                                    Modifier.clickable(role = Role.Button) { vm.download(track) },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(AIcons.Download, null, tint = AC.Muted, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp))
                                    T(tr("أونلاين · اضغط للتحميل", "Online · tap to download"), 11.sp, color = AC.Muted)
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        SmallOutlineButton(tr("تغيير", "Change")) { onChange(p) }
                    }
                }
            }
        }
    }
}
