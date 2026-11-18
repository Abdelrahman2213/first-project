package com.athaan.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import com.athaan.app.R

object AC {
    val Ink = Color(0xFF0B1220)
    val InkDeep = Color(0xFF070C16)
    val Nav = Color(0xFF0E1628)
    val Surface = Color(0xFF131C2E)
    val Surface2 = Color(0xFF1A2538)
    val Avatar = Color(0xFF22304A)
    val Track = Color(0xFF2A3550)
    val Text = Color(0xFFF3EFE6)
    val TextDim = Color(0xFFB8BFCC)
    val Muted = Color(0xFF9AA3B2)
    val Gold = Color(0xFFD6B370)
    val GoldSoft = Color(0x1AD6B370)
    val GoldLine = Color(0x47D6B370)
    val Mint = Color(0xFF7FC8A9)
    val Line = Color(0x0FFFFFFF)
    val Line2 = Color(0x1FFFFFFF)
    val Danger = Color(0xFFE7A27A)
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
fun AthaanTheme(arabic: Boolean, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = AC.Gold,
        onPrimary = AC.Ink,
        background = AC.Ink,
        onBackground = AC.Text,
        surface = AC.Surface,
        onSurface = AC.Text,
        surfaceVariant = AC.Surface2,
        onSurfaceVariant = AC.Muted,
        secondary = AC.Mint,
        outline = AC.Line2,
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

val BaseText = TextStyle(fontFamily = Plex, color = AC.Text)
