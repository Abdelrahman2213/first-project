package com.athaan.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import com.athaan.app.data.HeroPattern
import com.athaan.app.ui.theme.AC
import kotlin.math.cos
import kotlin.math.sin

private fun pt(c: Offset, r: Float, deg: Double) =
    Offset(c.x + r * cos(Math.toRadians(deg)).toFloat(), c.y + r * sin(Math.toRadians(deg)).toFloat())

/** Outline of an n-pointed star (alternating outer and inner radius). */
fun starOutline(c: Offset, outer: Float, inner: Float, n: Int, rot: Double = 0.0) = Path().apply {
    for (i in 0 until 2 * n) {
        val p = pt(c, if (i % 2 == 0) outer else inner, rot + i * 180.0 / n)
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

/** Star polygon {n/k}: every k-th point on a circle joined up (the classic Islamic star lines). */
fun starPolygon(c: Offset, r: Float, n: Int, k: Int, rot: Double = 0.0) = Path().apply {
    val g = gcd(n, k)
    for (s in 0 until g) {
        var i = s
        val p0 = pt(c, r, rot + 360.0 * i / n)
        moveTo(p0.x, p0.y)
        repeat(n / g) {
            i = (i + k) % n
            val p = pt(c, r, rot + 360.0 * i / n)
            lineTo(p.x, p.y)
        }
        close()
    }
}

private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

/** Interlaced "strap" line: a gold band with the card colour running down its middle. */
private fun DrawScope.strap(path: Path, deco: Color, card: Color, width: Float, alpha: Float) {
    drawPath(path, deco.copy(alpha = alpha), style = Stroke(width, join = StrokeJoin.Round))
    drawPath(path, card, style = Stroke((width - 2.2f * density).coerceAtLeast(0.5f), join = StrokeJoin.Round))
}

private fun circlePath(c: Offset, r: Float) = Path().apply {
    addOval(androidx.compose.ui.geometry.Rect(c.x - r, c.y - r, c.x + r, c.y + r))
}

/**
 * The ornament in the corner of the "next prayer" card. Drawn centred in this composable's
 * bounds and faded out towards its edges, so it never competes with the text.
 */
@Composable
fun HeroOrnament(pattern: HeroPattern, modifier: Modifier, rotation: Float = 0f) {
    val deco = AC.Deco
    val card = AC.Surface
    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val c = Offset(size.width / 2, size.height / 2)
        val R = size.minDimension / 2
        val d = density
        rotate(rotation, c) {
            when (pattern) {
                HeroPattern.SHAMSA -> {
                    val inner = R * 0.52f
                    for (i in 0 until 32) {
                        val a = i * 360.0 / 32
                        drawLine(deco.copy(alpha = 0.55f), pt(c, inner * 1.05f, a), pt(c, inner * (if (i % 2 == 0) 1.75f else 1.5f), a), strokeWidth = 1.2f * d)
                    }
                    val lobes = 16
                    val lobe = Path().apply {
                        for (i in 0 until lobes) {
                            val a0 = i * 360.0 / lobes
                            val a1 = (i + 1) * 360.0 / lobes
                            val p0 = pt(c, inner, a0)
                            val mid = pt(c, inner * 1.16f, (a0 + a1) / 2)
                            val p1 = pt(c, inner, a1)
                            if (i == 0) moveTo(p0.x, p0.y)
                            quadraticBezierTo(mid.x, mid.y, p1.x, p1.y)
                        }
                        close()
                    }
                    drawPath(lobe, deco.copy(alpha = 0.12f))
                    drawPath(lobe, deco.copy(alpha = 0.7f), style = Stroke(1.4f * d))
                    drawCircle(deco.copy(alpha = 0.45f), radius = inner * 0.8f, center = c, style = Stroke(1f * d))
                    strap(starOutline(c, inner * 0.74f, inner * 0.74f * 0.62f, 8, 22.5), deco, card, 4f * d, 0.6f)
                    drawCircle(deco.copy(alpha = 0.6f), radius = inner * 0.16f, center = c)
                }
                HeroPattern.RUB_EL_HIZB -> {
                    strap(starOutline(c, R * 0.72f, R * 0.72f * 0.765f, 8, 22.5), deco, card, 5f * d, 0.6f)
                    strap(starOutline(c, R * 0.46f, R * 0.46f * 0.765f, 8, 22.5), deco, card, 3.6f * d, 0.35f)
                    strap(circlePath(c, R * 0.34f), deco, card, 4.2f * d, 0.6f)
                    drawCircle(deco.copy(alpha = 0.7f), radius = R * 0.08f, center = c)
                }
                HeroPattern.ALHAMBRA -> {
                    val t = 56f * d
                    var y = c.y - t * 4
                    while (y <= c.y + t * 4) {
                        var x = c.x - t * 4
                        while (x <= c.x + t * 4) {
                            strap(starOutline(Offset(x, y), t * 0.5f, t * 0.5f * 0.765f, 8), deco, card, 4.4f * d, 0.6f)
                            x += t
                        }
                        y += t
                    }
                }
                HeroPattern.MAMLUK -> {
                    strap(starPolygon(c, R * 0.86f, 10, 3, -90.0), deco, card, 4.6f * d, 0.58f)
                    strap(circlePath(c, R * 0.86f), deco, card, 3.2f * d, 0.3f)
                    drawCircle(deco.copy(alpha = 0.5f), radius = R * 0.34f, center = c, style = Stroke(1.2f * d))
                    drawPath(starOutline(c, R * 0.23f, R * 0.14f, 10, -90.0), deco.copy(alpha = 0.35f))
                }
                HeroPattern.ROSETTE -> {
                    listOf(1f, 0.74f, 0.5f, 0.28f).forEachIndexed { i, k ->
                        val r = R * k
                        drawPath(starOutline(c, r, r * 0.77f, 8, i * 11.25), deco.copy(alpha = 0.5f), style = Stroke(1f * d))
                        drawCircle(deco.copy(alpha = 0.4f), radius = r * 0.86f, center = c, style = Stroke(1f * d))
                    }
                    drawCircle(deco.copy(alpha = 0.45f), radius = R * 0.08f, center = c)
                }
                HeroPattern.NONE -> {
                    drawCircle(Brush.radialGradient(listOf(deco.copy(alpha = 0.28f), Color.Transparent), c, R), radius = R, center = c)
                }
            }
        }
        // Fade everything out towards the edges.
        drawRect(
            Brush.radialGradient(
                0f to Color.Black, 0.5f to Color.Black.copy(alpha = 0.8f), 1f to Color.Transparent,
                center = c, radius = R,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
}
