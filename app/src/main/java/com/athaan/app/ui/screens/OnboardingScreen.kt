package com.athaan.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athaan.app.ui.AppViewModel
import com.athaan.app.ui.components.GoldButton
import com.athaan.app.ui.components.OutlineButton
import com.athaan.app.ui.components.StarPattern
import com.athaan.app.ui.components.T
import com.athaan.app.ui.theme.AC
import com.athaan.app.ui.theme.AIcons
import com.athaan.app.ui.theme.tr

@Composable
fun OnboardingScreen(vm: AppViewModel, onManual: () -> Unit) {
    val locating by vm.locating.collectAsState()
    val error by vm.locationError.collectAsState()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) vm.detectLocation() else onManual()
    }
    fun request() {
        val perms = mutableListOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms += Manifest.permission.POST_NOTIFICATIONS
        launcher.launch(perms.toTypedArray())
    }

    Box(Modifier.fillMaxSize()) {
        StarPattern(Modifier.fillMaxWidth().height(460.dp), tile = 52.dp, alpha = 0.09f)
        Box(Modifier.fillMaxWidth().height(460.dp).background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to AC.Ink)))

        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(110.dp))
            Box(Modifier.size(168.dp).border(1.dp, AC.Gold.copy(alpha = 0.25f), CircleShape), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(124.dp).clip(CircleShape).background(AC.Surface).border(1.dp, AC.Gold.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(AIcons.Mosque, null, tint = AC.Gold, modifier = Modifier.size(64.dp)) }
            }
            T("أذان", 40.sp, color = AC.Gold, kufi = true, modifier = Modifier.padding(top = 28.dp))
            T("ATHAAN", 13.sp, color = AC.Muted)
            T(
                tr("نحتاج موقعك لنحسب\nمواقيت الصلاة بدقة", "We need your location to\ncalculate prayer times"),
                24.sp, FontWeight.Bold, align = TextAlign.Center, lineHeight = 36.sp,
                modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 36.dp),
            )
            T(
                if (error) tr("تعذّر تحديد موقعك. تأكد إن خدمة الموقع شغالة أو اختر مدينتك يدويًا.", "Couldn't get your location. Turn on location services or choose your city.")
                else tr("الموقع يُستخدم على جهازك فقط لتحديد مدينتك ومواقيت الصلاة.", "Your location stays on your phone and is only used for prayer times."),
                14.sp, color = if (error) AC.Danger else AC.Muted, align = TextAlign.Center, lineHeight = 24.sp,
                modifier = Modifier.padding(start = 40.dp, end = 40.dp, top = 12.dp),
            )
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (locating) {
                    Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AC.Gold)
                    }
                } else {
                    GoldButton(tr("السماح بالوصول للموقع", "Allow location access"), { request() }, Modifier.fillMaxWidth(), icon = AIcons.Pin)
                }
                OutlineButton(tr("اختيار المدينة يدويًا", "Choose city manually"), onManual, Modifier.fillMaxWidth())
            }
        }
    }
}
