package com.athaan.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.athaan.app.art.Motif
import com.athaan.app.art.MotifKind
import com.athaan.app.art.Motifs
import com.athaan.app.data.HeroPattern
import com.athaan.app.ui.theme.AC

fun HeroPattern.motif(): MotifKind? = when (this) {
    HeroPattern.MOSQUE -> MotifKind.MOSQUE
    HeroPattern.MIHRAB -> MotifKind.MIHRAB
    HeroPattern.CRESCENT -> MotifKind.CRESCENT
    HeroPattern.ARABESQUE -> MotifKind.ARABESQUE
    HeroPattern.MASHRABIYA -> MotifKind.LATTICE
    HeroPattern.NONE -> null
}

/** Draws a [Motif]: gold "strap" outlines (a gold band with the card colour down its middle), fine lines and soft fills. */
fun DrawScope.drawMotif(m: Motif, deco: Color, card: Color, alpha: Float = 1f, strap: Float = 3.6f) {
    val d = density
    m.fills.forEach { drawPath(it.asComposePath(), deco.copy(alpha = 0.10f * alpha)) }
    m.bold.forEach {
        val p = it.asComposePath()
        drawPath(p, deco.copy(alpha = 0.62f * alpha), style = Stroke(strap * d, join = StrokeJoin.Round, cap = StrokeCap.Round))
        drawPath(p, card, style = Stroke((strap - 2.2f) * d, join = StrokeJoin.Round, cap = StrokeCap.Round))
    }
    m.thin.forEach {
        drawPath(it.asComposePath(), deco.copy(alpha = 0.5f * alpha), style = Stroke(1.1f * d, join = StrokeJoin.Round, cap = StrokeCap.Round))
    }
    m.solid.forEach { drawPath(it.asComposePath(), deco.copy(alpha = 0.7f * alpha)) }
}

/**
 * The ornament in the top corner of the "next prayer" card (the end side). It is drawn inside
 * this composable's bounds and fades out towards the text.
 */
@Composable
fun HeroOrnament(pattern: HeroPattern, modifier: Modifier, card: Color = AC.Surface, fadeFromCorner: Boolean = true) {
    val deco = AC.Deco
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val kind = pattern.motif()
        if (kind == null) {
            val c = Offset(if (rtl) 0f else size.width, 0f)
            drawCircle(Brush.radialGradient(listOf(deco.copy(alpha = 0.22f), Color.Transparent), c, size.maxDimension), radius = size.maxDimension, center = c)
            return@Canvas
        }
        val m = Motifs.build(kind, size.width, size.height)
        drawMotif(m, deco, card)
        // Fade towards the text: strongest at the card's corner, or around the centre for previews.
        val c = if (fadeFromCorner) Offset(if (rtl) 0f else size.width, 0f) else Offset(size.width / 2, size.height / 2)
        val r = if (fadeFromCorner) size.maxDimension * 1.25f else size.maxDimension * 0.75f
        drawRect(
            Brush.radialGradient(0f to Color.Black, 0.55f to Color.Black.copy(alpha = 0.85f), 1f to Color.Transparent, center = c, radius = r),
            blendMode = BlendMode.DstIn,
        )
    }
}

enum class Fade { NONE, TO_START, TO_BOTTOM }

/** A background grille of pointed arches (mashrabiya), fading away so it never competes with the text. */
@Composable
fun ArchLattice(modifier: Modifier, tile: Dp = 44.dp, alpha: Float = 0.11f, fade: Fade = Fade.NONE) {
    val gold = AC.Gold
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier) {
        val t = tile.toPx()
        val m = Motifs.lattice(size.width, size.height, t)
        val strong = gold.copy(alpha = alpha * 1.6f)
        val clear = gold.copy(alpha = 0f)
        val brush = when (fade) {
            Fade.NONE -> androidx.compose.ui.graphics.SolidColor(gold.copy(alpha = alpha * 1.3f))
            Fade.TO_START -> Brush.horizontalGradient(if (rtl) listOf(strong, clear) else listOf(clear, strong), startX = 0f, endX = size.width)
            Fade.TO_BOTTOM -> Brush.verticalGradient(listOf(strong, clear), startY = 0f, endY = size.height)
        }
        m.thin.forEach { drawPath(it.asComposePath(), brush, style = Stroke(1.dp.toPx())) }
    }
}

/** A gently breathing mihrab arch that frames the prayer name on the adhan screen. */
@Composable
fun MihrabFrame(modifier: Modifier, breath: Float, alpha: Float = 0.55f) {
    val gold = AC.Gold
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val grow = 1f + breath * 0.03f
        val pw = w * 0.62f * grow; val ph = h * 0.9f * grow
        val l = (w - pw) / 2; val top = (h - ph) / 2
        listOf(0f to alpha, 0.07f to alpha * 0.55f).forEach { (inset, a) ->
            val p = android.graphics.Path()
            val il = l + pw * inset; val ir = l + pw * (1 - inset)
            Motifs.arch(p, il, ir, top + ph * (0.45f + inset), top + ph * inset * 1.4f, top + ph)
            drawPath(p.asComposePath(), gold.copy(alpha = a), style = Stroke(1.2.dp.toPx(), join = StrokeJoin.Round))
        }
        val lamp = Motifs.crescentPath(w / 2, top + ph * 0.13f, 7.dp.toPx())
        drawPath(lamp.asComposePath(), gold.copy(alpha = alpha))
    }
}
