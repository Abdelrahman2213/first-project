package com.athaan.app.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import com.athaan.app.R

/** 0 = dark (night) theme, 1 = light (ivory) theme; animated between the two. */
object ThemeAnim {
    var t by mutableFloatStateOf(0f)
}

private fun c(dark: Long, light: Long): Color = lerp(Color(dark), Color(light), ThemeAnim.t)
private fun a(dark: Long, light: Long, alphaDark: Float, alphaLight: Float): Color =
    lerp(Color(dark).copy(alpha = alphaDark), Color(light).copy(alpha = alphaLight), ThemeAnim.t)

/** App colours. Every value follows the theme animation, so reading it in a composable is enough. */
object AC {
    val Ink: Color get() = c(0xFF0B1220, 0xFFF6F1E7)
    val InkDeep: Color get() = c(0xFF070C16, 0xFFEFE7D8)
    val Nav: Color get() = c(0xFF0E1628, 0xFFFBF8F2)
    val Surface: Color get() = c(0xFF131C2E, 0xFFFFFFFF)
    val Surface2: Color get() = c(0xFF1A2538, 0xFFFFFFFF)
    val Avatar: Color get() = c(0xFF22304A, 0xFFF3E9D6)
    val Track: Color get() = c(0xFF2A3550, 0xFFE2DACB)
    val Text: Color get() = c(0xFFF3EFE6, 0xFF1A2030)
    val TextDim: Color get() = c(0xFFB8BFCC, 0xFF3B4456)
    val Muted: Color get() = c(0xFF9AA3B2, 0xFF636B7D)
    val Gold: Color get() = c(0xFFD6B370, 0xFF85601F)
    val OnGold: Color get() = c(0xFF0B1220, 0xFFFFFFFF)
    val GoldSoft: Color get() = Gold.copy(alpha = 0.10f)
    val GoldLine: Color get() = Gold.copy(alpha = 0.30f)
    val Mint: Color get() = c(0xFF7FC8A9, 0xFF2C7A5A)
    val Line: Color get() = a(0xFFFFFFFF, 0xFF1A2030, 0.06f, 0.08f)
    val Line2: Color get() = a(0xFFFFFFFF, 0xFF1A2030, 0.12f, 0.13f)
    val Danger: Color get() = c(0xFFE7A27A, 0xFFA84B22)
    val Shadow: Color get() = a(0xFF000000, 0xFF5A4520, 0.45f, 0.18f)
}

val Plex = FontFamily(
    Font(R.font.plex_regular, FontWeight.Normal),
    Font(R.font.plex_medium, FontWeight.Medium),
    Font(R.font.plex_semibold, FontWeight.SemiBold),
    Font(R.font.plex_bold, FontWeight.Bold),
)
val Kufi = FontFamily(Font(R.font.reem_kufi_semibold, FontWeight.SemiBold))
val Amiri = FontFamily(Font(R.font.amiri_regular, FontWeight.Normal))

val LocalArabic = staticCompositionLocalOf { true }

/** Picks the Arabic or English string for the current language. */
@Composable
@ReadOnlyComposable
fun tr(ar: String, en: String): String = if (LocalArabic.current) ar else en

@Composable
fun AthaanTheme(arabic: Boolean, light: Boolean, content: @Composable () -> Unit) {
    // Animate between the palettes. MainActivity sets ThemeAnim.t before the first frame,
    // so the app opens in the saved theme without a flash.
    val anim = remember { Animatable(ThemeAnim.t) }
    LaunchedEffect(light) {
        anim.animateTo(if (light) 1f else 0f, tween(550, easing = FastOutSlowInEasing)) {
            ThemeAnim.t = value
        }
    }
    val base = if (light) lightColorScheme() else darkColorScheme()
    val scheme = base.copy(
        primary = AC.Gold,
        onPrimary = AC.OnGold,
        background = AC.Ink,
        onBackground = AC.Text,
        surface = AC.Surface2,
        onSurface = AC.Text,
        surfaceVariant = AC.Surface,
        onSurfaceVariant = AC.Muted,
        secondary = AC.Mint,
        outline = AC.Line2,
        surfaceContainerHigh = AC.Surface2,
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography.let { t ->
            t.copy(
                bodyLarge = t.bodyLarge.copy(fontFamily = Plex),
                bodyMedium = t.bodyMedium.copy(fontFamily = Plex),
                bodySmall = t.bodySmall.copy(fontFamily = Plex),
                titleLarge = t.titleLarge.copy(fontFamily = Plex),
                titleMedium = t.titleMedium.copy(fontFamily = Plex),
                titleSmall = t.titleSmall.copy(fontFamily = Plex),
                labelLarge = t.labelLarge.copy(fontFamily = Plex),
                labelMedium = t.labelMedium.copy(fontFamily = Plex),
                labelSmall = t.labelSmall.copy(fontFamily = Plex),
                headlineSmall = t.headlineSmall.copy(fontFamily = Plex),
            )
        },
    ) {
        CompositionLocalProvider(
            LocalArabic provides arabic,
            LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr,
            content = content,
        )
    }
}

val BaseText = TextStyle(fontFamily = Plex)
