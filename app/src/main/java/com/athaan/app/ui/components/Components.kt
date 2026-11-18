package com.athaan.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athaan.app.ui.theme.AC
import com.athaan.app.ui.theme.AIcons
import com.athaan.app.ui.theme.Kufi
import com.athaan.app.ui.theme.Plex
import com.athaan.app.ui.theme.tr

@Composable
fun T(
    text: String,
    size: TextUnit = 14.sp,
    weight: FontWeight = FontWeight.Normal,
    color: Color = AC.Text,
    modifier: Modifier = Modifier,
    kufi: Boolean = false,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = size,
        fontWeight = if (kufi) FontWeight.SemiBold else weight,
        fontFamily = if (kufi) Kufi else Plex,
        color = color,
        textAlign = align,
        maxLines = maxLines,
        lineHeight = lineHeight,
        overflow = overflow,
    )
}

/** Eight-point star tile pattern used behind hero areas. */
@Composable
fun StarPattern(modifier: Modifier, tile: Dp = 44.dp, alpha: Float = 0.11f) {
    Canvas(modifier) {
        val t = tile.toPx()
        val s = t / 2f
        val stroke = Stroke(width = 1.dp.toPx())
        val color = AC.Gold.copy(alpha = alpha)
        var y = 0f
        while (y < size.height) {
            var x = 0f
            while (x < size.width) {
                val c = Offset(x + t / 2, y + t / 2)
                val tl = Offset(c.x - s / 2, c.y - s / 2)
                drawRect(color, topLeft = tl, size = Size(s, s), style = stroke)
                rotate(45f, pivot = c) { drawRect(color, topLeft = tl, size = Size(s, s), style = stroke) }
                x += t
            }
            y += t
        }
    }
}

@Composable
fun Avatar(initial: String, size: Dp, fontSize: TextUnit, ring: Color = AC.GoldLine, ringWidth: Dp = 1.5.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(AC.Avatar)
            .border(ringWidth, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        T(initial, size = fontSize, color = AC.Gold, kufi = true)
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    filled: Boolean = false,
    tint: Color = if (filled) AC.Ink else AC.Text,
    outline: Color = AC.Line2,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) AC.Gold else AC.Surface)
            .then(if (filled) Modifier else Modifier.border(1.dp, outline, CircleShape))
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun GoldButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, height: Dp = 56.dp, enabled: Boolean = true) {
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) AC.Gold else AC.Track)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = AC.Ink, modifier = Modifier.size(20.dp))
            Box(Modifier.size(10.dp))
        }
        T(text, 16.sp, FontWeight.Bold, if (enabled) AC.Ink else AC.Muted)
    }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, height: Dp = 52.dp, color: Color = AC.Text, border: Color = AC.Line2) {
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, border, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Box(Modifier.size(8.dp))
        }
        T(text, 15.sp, FontWeight.SemiBold, color)
    }
}

@Composable
fun SmallOutlineButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, AC.GoldLine, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { T(text, 13.sp, FontWeight.SemiBold, AC.Gold) }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, leading: ImageVector? = null) {
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) AC.Gold else AC.Surface)
            .border(1.dp, if (selected) AC.Gold else AC.Line2, RoundedCornerShape(20.dp))
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null && selected) {
            Icon(leading, null, tint = AC.Ink, modifier = Modifier.size(16.dp))
            Box(Modifier.size(6.dp))
        }
        T(text, 13.sp, if (selected) FontWeight.Bold else FontWeight.Medium, if (selected) AC.Ink else AC.Text)
    }
}

/** Segmented control, e.g. pre-alert minutes or language. */
@Composable
fun <V> Segmented(options: List<Pair<V, String>>, selected: V, onSelect: (V) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AC.Surface)
            .border(1.dp, AC.Line, RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (v, label) ->
            val on = v == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) AC.Gold else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(v) },
                contentAlignment = Alignment.Center,
            ) { T(label, 14.sp, if (on) FontWeight.Bold else FontWeight.Medium, if (on) AC.Ink else AC.Text) }
        }
    }
}

@Composable
fun GoldSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = AC.Ink,
            checkedTrackColor = AC.Gold,
            checkedBorderColor = AC.Gold,
            uncheckedThumbColor = AC.Muted,
            uncheckedTrackColor = AC.Track,
            uncheckedBorderColor = AC.Track,
        ),
    )
}

@Composable
fun ACard(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(0.dp), border: Color = AC.Line, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(AC.Surface)
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .padding(padding)
    ) { content() }
}

@Composable
fun SectionLabel(text: String) {
    T(text, 12.sp, FontWeight.SemiBold, AC.Muted, Modifier.padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 6.dp))
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp)) {
        T(title, 30.sp, kufi = true)
        if (subtitle != null) T(subtitle, 13.sp, color = AC.Muted)
    }
}

/** Settings-style row with optional trailing content. */
@Composable
fun ListRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    divider: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, tint = AC.Gold, modifier = Modifier.size(20.dp))
                Box(Modifier.size(12.dp))
            }
            Column(Modifier.weight(1f)) {
                T(title, 15.sp, FontWeight.Medium)
                if (subtitle != null) T(subtitle, 12.sp, color = AC.Muted)
            }
            trailing()
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(AC.Line))
    }
}

enum class Tab { HOME, LIBRARY, ADHAN, SETTINGS }

@Composable
fun BottomNav(current: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.background(AC.Nav)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(AC.Line))
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(70.dp)
                .padding(horizontal = 8.dp),
        ) {
            val items = listOf(
                Triple(Tab.HOME, AIcons.Home, tr("الرئيسية", "Home")),
                Triple(Tab.LIBRARY, AIcons.Mic, tr("المؤذنين", "Muezzins")),
                Triple(Tab.ADHAN, AIcons.Clock, tr("الأذان", "Adhan")),
                Triple(Tab.SETTINGS, AIcons.Sliders, tr("الإعدادات", "Settings")),
            )
            items.forEach { (tab, icon, label) ->
                val on = tab == current
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .height(70.dp)
                        .clickable(role = Role.Tab) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(icon, null, tint = if (on) AC.Gold else AC.Muted, modifier = Modifier.size(22.dp))
                    Box(Modifier.size(4.dp))
                    T(label, 11.sp, if (on) FontWeight.SemiBold else FontWeight.Medium, if (on) AC.Gold else AC.Muted)
                }
            }
        }
    }
}
