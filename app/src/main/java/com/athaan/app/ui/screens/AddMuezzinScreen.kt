package com.athaan.app.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athaan.app.data.CustomMuezzins
import com.athaan.app.data.Muezzin
import com.athaan.app.ui.AppViewModel
import com.athaan.app.ui.components.CircleIconButton
import com.athaan.app.ui.components.GoldButton
import com.athaan.app.ui.components.ScreenTitle
import com.athaan.app.ui.components.T
import com.athaan.app.ui.components.pressClick
import com.athaan.app.ui.theme.AC
import com.athaan.app.ui.theme.AIcons
import com.athaan.app.ui.theme.Plex
import com.athaan.app.ui.theme.tr

/** Lets the user add a muezzin (or another version of one) from audio files on the phone. */
@Composable
fun AddMuezzinScreen(vm: AppViewModel, prefillName: String?, onBack: () -> Unit, onAdded: (Muezzin) -> Unit) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(prefillName ?: "") }
    var note by rememberSaveable { mutableStateOf("") }
    var regular by rememberSaveable { mutableStateOf<String?>(null) }
    var fajr by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val pickRegular = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { regular = uri.toString(); error = null } }
    val pickFajr = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { fajr = uri.toString(); error = null } }

    val msgNoFile = tr("اختر ملف صوت واحد على الأقل", "Pick at least one audio file")
    val msgNotAudio = tr("الملف ده مش ملف صوت صالح، اختر ملف تاني", "That file isn't a playable audio file, pick another")
    val msgFailed = tr("لم نتمكن من حفظ الملف، حاول مرة أخرى", "Couldn't save the file, try again")

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            CircleIconButton(AIcons.Back, tr("رجوع", "Back"), onBack)
        }
        ScreenTitle(
            tr("إضافة صوت مؤذن", "Add a muezzin"),
            tr("أضف شيخ مش موجود، أو نسخة تانية لشيخ تحبه، من ملف صوت على موبايلك", "Add a sheikh who isn't listed, or another version of one, from an audio file on your phone"),
        )

        Field(tr("اسم المؤذن", "Muezzin name"), name, { name = it; error = null }, tr("مثلًا: الشيخ محمد رفعت (نسختي)", "e.g. Mohamed Refaat (my copy)"))
        Field(tr("ملاحظة (اختياري)", "Note (optional)"), note, { note = it }, tr("مثلًا: مسجد الحي", "e.g. my local mosque"))

        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FileCard(
                title = tr("الأذان العادي", "Regular adhan"),
                hint = tr("للظهر والعصر والمغرب والعشاء", "For Dhuhr, Asr, Maghrib and Isha"),
                fileName = regular?.let { displayName(context, Uri.parse(it)) },
                onPick = { pickRegular.launch(arrayOf("audio/*")) },
                onClear = { regular = null },
            )
            FileCard(
                title = tr("أذان الفجر", "Fajr adhan"),
                hint = tr("اختياري · التسجيل اللي فيه «الصلاة خير من النوم»", "Optional · the one with “prayer is better than sleep”"),
                fileName = fajr?.let { displayName(context, Uri.parse(it)) },
                onPick = { pickFajr.launch(arrayOf("audio/*")) },
                onClear = { fajr = null },
            )
        }

        AnimatedVisibility(error != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            T(error ?: "", 13.sp, color = AC.Danger, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp))
        }

        Spacer(Modifier.height(24.dp))
        Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            if (saving) {
                Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AC.Gold) }
            } else {
                GoldButton(
                    tr("حفظ وإضافة للمكتبة", "Save to library"),
                    onClick = {
                        if (regular == null && fajr == null) { error = msgNoFile; return@GoldButton }
                        saving = true
                        vm.addCustom(name, note, regular?.let(Uri::parse), fajr?.let(Uri::parse)) { r ->
                            saving = false
                            when (r) {
                                is CustomMuezzins.AddResult.Added -> onAdded(r.muezzin)
                                CustomMuezzins.AddResult.NoFile -> error = msgNoFile
                                CustomMuezzins.AddResult.NotAudio -> error = msgNotAudio
                                CustomMuezzins.AddResult.Failed -> error = msgFailed
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = AIcons.Check,
                    enabled = name.isNotBlank() && (regular != null || fajr != null),
                )
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, hint: String) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
        T(label, 13.sp, FontWeight.SemiBold, AC.Muted, Modifier.padding(start = 8.dp, bottom = 6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AC.Surface)
                .border(1.dp, AC.Line2, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) T(hint, 15.sp, color = AC.Muted, maxLines = 1)
            BasicTextField(
                value = value, onValueChange = { if (it.length <= 60) onChange(it) }, singleLine = true,
                textStyle = TextStyle(fontFamily = Plex, fontSize = 15.sp, color = AC.Text),
                cursorBrush = SolidColor(AC.Gold), modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FileCard(title: String, hint: String, fileName: String?, onPick: () -> Unit, onClear: () -> Unit) {
    val picked = fileName != null
    val border by animateColorAsState(if (picked) AC.GoldLine else AC.Line, label = "fileBr")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AC.Surface)
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .pressClick(onClick = onPick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(if (picked) AC.Mint.copy(alpha = 0.12f) else AC.GoldSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(if (picked) AIcons.Check else AIcons.Mic, null, tint = if (picked) AC.Mint else AC.Gold, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            T(title, 15.sp, FontWeight.Bold)
            T(fileName ?: hint, 12.sp, color = if (picked) AC.Mint else AC.Muted, maxLines = 1)
        }
        if (picked) {
            CircleIconButton(AIcons.Trash, tr("إزالة الملف", "Remove file"), onClear, size = 40.dp, iconSize = 18.dp, tint = AC.Muted)
        } else {
            T(tr("اختيار ملف", "Choose file"), 13.sp, FontWeight.SemiBold, AC.Gold)
        }
    }
}

private fun displayName(context: Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull() ?: uri.lastPathSegment ?: "audio"
