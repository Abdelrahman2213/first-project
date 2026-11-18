package com.athaan.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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

/** Click with a soft press-down scale. */
fun Modifier.pressClick(enabled: Boolean = true, role: Role = Role.Button, label: String? = null, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), label = "press")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), enabled = enabled, role = role, onClickLabel = label, onClick = onClick)
}

/** Path of an eight-point star (khatam) centred on [c]. */
private fun starPath(c: Offset, outer: Float, inner: Float, rot: Float): androidx.compose.ui.graphics.Path {
    val p = androidx.compose.ui.graphics.Path()
    for (i in 0 until 16) {
        val r = if (i % 2 == 0) outer else inner
        val a = Math.toRadians((i * 22.5 + rot).toDouble())
        val x = c.x + r * kotlin.math.cos(a).toFloat()
        val y = c.y + r * kotlin.math.sin(a).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

enum class Fade { NONE, TO_START, TO_BOTTOM }

/**
 * Islamic "star and cross" tessellation: eight-point stars whose tips meet, leaving
 * crosses between them. Fades out towards the text so it never competes with it.
 */
@Composable
fun StarPattern(modifier: Modifier, tile: Dp = 44.dp, alpha: Float = 0.11f, rotation: Float = 0f, fade: Fade = Fade.NONE) {
    val gold = AC.Gold
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier) {
        val t = tile.toPx()
        val strong = gold.copy(alpha = alpha * 1.6f)
        val clear = gold.copy(alpha = 0f)
        val brush = when (fade) {
            Fade.NONE -> androidx.compose.ui.graphics.SolidColor(gold.copy(alpha = alpha * 1.3f))
            Fade.TO_START -> androidx.compose.ui.graphics.Brush.horizontalGradient(
                if (rtl) listOf(strong, clear) else listOf(clear, strong), startX = 0f, endX = size.width,
            )
            Fade.TO_BOTTOM -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(strong, clear), startY = 0f, endY = size.height)
        }
        val stroke = Stroke(width = 1.dp.toPx())
        var y = 0f
        while (y < size.height + t) {
            var x = 0f
            while (x < size.width + t) {
                val c = Offset(x, y)
                drawPath(starPath(c, t * 0.5f, t * 0.30f, rotation), brush, style = stroke)
                drawCircle(brush, radius = t * 0.12f, center = c, style = stroke)
                x += t
            }
            y += t
        }
    }
}

/** A large eight-fold rosette, drawn in fine gold lines that fade away from its centre. */
@Composable
fun Rosette(modifier: Modifier, rotation: Float = 0f, alpha: Float = 0.5f) {
    val gold = AC.Gold
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val R = size.minDimension / 2
        val brush = androidx.compose.ui.graphics.Brush.radialGradient(
            0f to gold.copy(alpha = alpha), 0.75f to gold.copy(alpha = alpha * 0.45f), 1f to gold.copy(alpha = 0f),
            center = c, radius = R,
        )
        val thin = Stroke(width = 1.dp.toPx())
        listOf(1f, 0.74f, 0.5f, 0.28f).forEachIndexed { i, k ->
            val rot = rotation * (if (i % 2 == 0) 1f else -1f) + i * 11.25f
            drawPath(starPath(c, R * k, R * k * 0.77f, rot), brush, style = thin)
            drawCircle(brush, radius = R * k * 0.86f, center = c, style = thin)
        }
        for (i in 0 until 8) {
            val a = Math.toRadians((i * 45 + rotation).toDouble())
            val d = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
            drawLine(brush, c + d * (R * 0.28f), c + d * (R * 0.86f), strokeWidth = 1.dp.toPx())
        }
        drawCircle(brush, radius = R * 0.08f, center = c)
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
        T(initial, size = fontSize, weight = FontWeight.SemiBold, color = AC.Gold)
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
    tint: Color = if (filled) AC.OnGold else AC.Text,
    outline: Color = AC.Line2,
    enabled: Boolean = true,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) AC.Gold else AC.Surface)
            .then(if (filled) Modifier else Modifier.border(1.dp, outline, CircleShape))
            .pressClick(enabled = enabled, label = contentDescription, onClick = onClick)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Play/pause button whose icon morphs with a small scale+fade. */
@Composable
fun PlayButton(playing: Boolean, loading: Boolean, onClick: () -> Unit, size: Dp = 44.dp, filled: Boolean = true, label: String) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled || playing) AC.Gold else AC.Surface)
            .then(if (filled || playing) Modifier else Modifier.border(1.dp, AC.GoldLine, CircleShape))
            .pressClick(label = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (filled || playing) AC.OnGold else AC.Gold
        AnimatedContent(
            targetState = when { loading -> 2; playing -> 1; else -> 0 },
            transitionSpec = { (fadeIn(tween(180)) + scaleIn(initialScale = 0.6f)) togetherWith (fadeOut(tween(120)) + scaleOut(targetScale = 0.6f)) },
            label = "play",
        ) { st ->
            when (st) {
                2 -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(size * 0.4f), color = tint, strokeWidth = 2.dp)
                1 -> Icon(AIcons.Pause, null, tint = tint, modifier = Modifier.size(size * 0.38f))
                else -> Icon(AIcons.Play, null, tint = tint, modifier = Modifier.size(size * 0.38f))
            }
        }
    }
}

@Composable
fun GoldButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, height: Dp = 56.dp, enabled: Boolean = true) {
    val bg by animateColorAsState(if (enabled) AC.Gold else AC.Track, label = "gb")
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .pressClick(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = AC.OnGold, modifier = Modifier.size(20.dp))
            Box(Modifier.size(10.dp))
        }
        T(text, 16.sp, FontWeight.Bold, if (enabled) AC.OnGold else AC.Muted, maxLines = 1)
    }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, height: Dp = 52.dp, color: Color = AC.Text, border: Color = AC.Line2) {
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, border, RoundedCornerShape(18.dp))
            .pressClick(onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Box(Modifier.size(8.dp))
        }
        T(text, 15.sp, FontWeight.SemiBold, color, maxLines = 1)
    }
}

@Composable
fun SmallOutlineButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, AC.GoldLine, RoundedCornerShape(14.dp))
            .pressClick(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { T(text, 13.sp, FontWeight.SemiBold, AC.Gold) }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, leading: ImageVector? = null, enabled: Boolean = true) {
    val bg by animateColorAsState(if (selected) AC.Gold else AC.Surface, tween(220), label = "pillBg")
    val fg by animateColorAsState(if (selected) AC.OnGold else if (enabled) AC.Text else AC.Muted, tween(220), label = "pillFg")
    val br by animateColorAsState(if (selected) AC.Gold else AC.Line2, tween(220), label = "pillBr")
    Row(
        modifier
            .height(40.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, br, RoundedCornerShape(20.dp))
            .pressClick(enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null && selected) {
            Icon(leading, null, tint = fg, modifier = Modifier.size(16.dp))
            Box(Modifier.size(6.dp))
        }
        T(text, 13.sp, if (selected) FontWeight.Bold else FontWeight.Medium, fg, maxLines = 1)
    }
}

/** Segmented control with a sliding gold indicator. */
@Composable
fun <V> Segmented(options: List<Pair<V, String>>, selected: V, onSelect: (V) -> Unit, modifier: Modifier = Modifier) {
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AC.Surface)
            .border(1.dp, AC.Line, RoundedCornerShape(16.dp))
            .padding(4.dp)
    ) {
        val gap = 4.dp
        val itemW = (maxWidth - gap * (options.size - 1)) / options.size
        // Modifier.offset already mirrors in right-to-left layouts.
        val x by animateDpAsState((itemW + gap) * index, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "seg")
        Box(
            Modifier
                .offset(x = x)
                .align(Alignment.CenterStart)
                .width(itemW)
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AC.Gold)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            options.forEachIndexed { i, (v, label) ->
                val on = i == index
                val fg by animateColorAsState(if (on) AC.OnGold else AC.Text, label = "segFg")
                Box(
                    Modifier
                        .width(itemW)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.RadioButton) { onSelect(v) },
                    contentAlignment = Alignment.Center,
                ) { T(label, 14.sp, if (on) FontWeight.Bold else FontWeight.Medium, fg, maxLines = 1) }
            }
        }
    }
}

/** Slim slider: a thin gold track and a round thumb (no Material 3 "gap" style). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GoldSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    val dragged by source.collectIsPressedAsState()
    val thumb by animateDpAsState(if (dragged) 22.dp else 18.dp, label = "thumb")
    androidx.compose.material3.Slider(
        value = value.coerceIn(0f, 1f),
        onValueChange = onChange,
        modifier = modifier.height(36.dp),
        enabled = enabled,
        interactionSource = source,
        thumb = {
            Box(Modifier.size(thumb).clip(CircleShape).background(if (enabled) AC.Gold else AC.Muted))
        },
        track = { st ->
            val f = ((st.value - st.valueRange.start) / (st.valueRange.endInclusive - st.valueRange.start)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(AC.Track)) {
                Box(Modifier.fillMaxWidth(f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (enabled) AC.Gold else AC.Muted))
            }
        },
    )
}

@Composable
fun GoldSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = AC.OnGold,
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
        T(title, 30.sp, kufi = true, lineHeight = 42.sp)
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

/** Sun/moon button that spins and swaps its icon when the theme changes. */
@Composable
fun ThemeToggle(light: Boolean, onToggle: () -> Unit) {
    val turn by animateFloatAsState(if (light) 360f else 0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow), label = "themeTurn")
    val label = if (light) tr("الوضع الليلي", "Dark mode") else tr("الوضع الفاتح", "Light mode")
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(AC.Surface)
            .border(1.dp, AC.Line2, CircleShape)
            .pressClick(label = label, onClick = onToggle)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = light,
            transitionSpec = { (fadeIn(tween(260)) + scaleIn(initialScale = 0.4f)) togetherWith (fadeOut(tween(200)) + scaleOut(targetScale = 0.4f)) },
            label = "themeIcon",
        ) { isLight ->
            Icon(
                if (isLight) AIcons.MoonStar else AIcons.Sun, null,
                tint = AC.Gold,
                modifier = Modifier.size(21.dp).rotate(turn),
            )
        }
    }
}

/** "English" / "العربية" chip for switching the app language. */
@Composable
fun LanguageToggle(arabic: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(AC.Surface)
            .border(1.dp, AC.Line2, RoundedCornerShape(22.dp))
            .pressClick(onClick = onToggle)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AIcons.Globe, null, tint = AC.Gold, modifier = Modifier.size(18.dp))
        Box(Modifier.size(8.dp))
        AnimatedContent(arabic, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) }, label = "lang") { ar ->
            T(if (ar) "English" else "العربية", 14.sp, FontWeight.SemiBold)
        }
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
                val tint by animateColorAsState(if (on) AC.Gold else AC.Muted, tween(250), label = "navTint")
                val pill by animateFloatAsState(if (on) 1f else 0f, tween(260), label = "navPill")
                val source = remember { MutableInteractionSource() }
                val pressed by source.collectIsPressedAsState()
                val press by animateFloatAsState(if (pressed) 0.9f else 1f, tween(120), label = "navPress")
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(interactionSource = source, indication = null, role = Role.Tab, onClickLabel = label) { if (!on) onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .graphicsLayer { scaleX = press; scaleY = press }
                            .width(56.dp)
                            .height(30.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .graphicsLayer { scaleX = 0.6f + 0.4f * pill; alpha = pill }
                                .fillMaxWidth()
                                .height(30.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(AC.GoldSoft)
                        )
                        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
                    }
                    Box(Modifier.size(4.dp))
                    T(label, 11.sp, if (on) FontWeight.SemiBold else FontWeight.Medium, tint, maxLines = 1)
                }
            }
        }
    }
}
