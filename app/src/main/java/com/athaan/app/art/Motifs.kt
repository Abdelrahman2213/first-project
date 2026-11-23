package com.athaan.app.art

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Islamic ornaments without stars: a mosque with dome and minarets, a mihrab with its lamp,
 * a crescent, an arabesque vine and a lattice of arches. Each is built as plain paths inside a
 * box, so the app (Compose) and the notifications (android.graphics) draw exactly the same shapes.
 */
class Motif(
    /** Main outlines, drawn as a double "strap" line. */
    val bold: List<Path> = emptyList(),
    /** Fine details, drawn as a single thin line. */
    val thin: List<Path> = emptyList(),
    /** Shapes filled with a faint wash of gold. */
    val fills: List<Path> = emptyList(),
    /** Small solid accents. */
    val solid: List<Path> = emptyList(),
)

enum class MotifKind { MOSQUE, MIHRAB, CRESCENT, ARABESQUE, LATTICE }

object Motifs {

    fun build(kind: MotifKind, w: Float, h: Float): Motif = when (kind) {
        MotifKind.MOSQUE -> mosque(w, h)
        MotifKind.MIHRAB -> mihrab(w, h)
        MotifKind.CRESCENT -> crescent(w, h)
        MotifKind.ARABESQUE -> arabesque(w, h)
        MotifKind.LATTICE -> lattice(w, h, min(w, h) / 4.2f)
    }

    /** A pointed (four-centred) arch from [l] to [r], springing at [spring] and peaking at [top]. */
    fun arch(p: Path, l: Float, r: Float, spring: Float, top: Float, base: Float? = null) {
        val cx = (l + r) / 2
        val w = r - l
        val hh = spring - top
        if (base != null) { p.moveTo(l, base); p.lineTo(l, spring) } else p.moveTo(l, spring)
        p.cubicTo(l, spring - hh * 0.55f, cx - w * 0.16f, top + hh * 0.2f, cx, top)
        p.cubicTo(cx + w * 0.16f, top + hh * 0.2f, r, spring - hh * 0.55f, r, spring)
        if (base != null) p.lineTo(r, base)
    }

    /** A small crescent centred at ([x], [y]). */
    fun crescentPath(x: Float, y: Float, r: Float, dx: Float = 0.42f, dy: Float = -0.18f, cut: Float = 0.82f): Path {
        val outer = Path().apply { addCircle(x, y, r, Path.Direction.CW) }
        val inner = Path().apply { addCircle(x + r * dx, y + r * dy, r * cut, Path.Direction.CW) }
        return Path().apply { op(outer, inner, Path.Op.DIFFERENCE) }
    }

    private fun rect(l: Float, t: Float, r: Float, b: Float) = Path().apply { addRect(RectF(l, t, r, b), Path.Direction.CW) }

    /** A mosque: onion dome with a crescent finial, an arched door and windows, and two minarets. */
    fun mosque(w: Float, h: Float): Motif {
        val u = min(w, h) / 100f
        val cx = w / 2
        val oy = (h - 100 * u) / 2
        fun y(v: Float) = oy + v * u

        val dome = Path().apply {
            moveTo(cx - 23 * u, y(56f))
            cubicTo(cx - 28 * u, y(43f), cx - 14 * u, y(36f), cx - 5 * u, y(28f))
            cubicTo(cx - 2 * u, y(25f), cx - 0.8f * u, y(22.5f), cx, y(20f))
            cubicTo(cx + 0.8f * u, y(22.5f), cx + 2 * u, y(25f), cx + 5 * u, y(28f))
            cubicTo(cx + 14 * u, y(36f), cx + 28 * u, y(43f), cx + 23 * u, y(56f))
            close()
        }
        val body = rect(cx - 33 * u, y(61f), cx + 33 * u, y(94f))
        val drum = rect(cx - 25.5f * u, y(56f), cx + 25.5f * u, y(61f))
        val door = Path().apply { arch(this, cx - 7 * u, cx + 7 * u, y(80f), y(68f), y(94f)) }
        val windows = Path().apply {
            for (s in listOf(-1, 1)) {
                val x = cx + s * 20 * u
                arch(this, x - 4 * u, x + 4 * u, y(82f), y(73f), y(88f)); close()
            }
            for (s in listOf(-1, 1)) {
                val x = cx + s * 12 * u
                arch(this, x - 2.5f * u, x + 2.5f * u, y(59.5f), y(57.2f)); // drum openings
            }
        }
        val minarets = Path()
        val minaretDetails = Path()
        val solids = mutableListOf<Path>()
        for (s in listOf(-1, 1)) {
            val mx = cx + s * 42 * u
            minarets.addRect(RectF(mx - 3 * u, y(34f), mx + 3 * u, y(94f)), Path.Direction.CW)
            minaretDetails.addRect(RectF(mx - 5 * u, y(46f), mx + 5 * u, y(49f)), Path.Direction.CW)
            minaretDetails.addRect(RectF(mx - 4.2f * u, y(66f), mx + 4.2f * u, y(68.5f)), Path.Direction.CW)
            minaretDetails.moveTo(mx - 3 * u, y(34f))
            minaretDetails.quadTo(mx - 3.4f * u, y(26f), mx, y(21f))
            minaretDetails.quadTo(mx + 3.4f * u, y(26f), mx + 3 * u, y(34f))
            minaretDetails.moveTo(mx, y(21f)); minaretDetails.lineTo(mx, y(17.5f))
            solids += crescentPath(mx, y(15.8f), 1.6f * u)
        }
        val finial = Path().apply { moveTo(cx, y(20f)); lineTo(cx, y(12.5f)) }
        solids += crescentPath(cx, y(9.6f), 3f * u)
        val ground = Path().apply { moveTo(cx - 50 * u, y(94f)); lineTo(cx + 50 * u, y(94f)) }
        val halo = Path().apply { addArc(RectF(cx - 36 * u, y(10f), cx + 36 * u, y(82f)), 200f, 140f) }
        return Motif(
            bold = listOf(dome, body, minarets),
            thin = listOf(drum, door, windows, minaretDetails, finial, ground, halo),
            fills = listOf(dome),
            solid = solids,
        )
    }

    /** A mihrab: nested pointed arches on slim columns, with a hanging lamp. */
    fun mihrab(w: Float, h: Float): Motif {
        val cx = w / 2
        val base = h * 0.97f
        val outer = Path(); val inner = Path(); val thin = Path()
        val widths = listOf(0.84f, 0.66f, 0.48f)
        widths.forEachIndexed { i, k ->
            val half = w * k / 2
            val spring = h * (0.44f + i * 0.04f)
            val top = h * (0.04f + i * 0.1f)
            arch(if (i == 0) outer else if (i == 1) inner else thin, cx - half, cx + half, spring, top, base)
        }
        // Column capitals on the outer arch.
        val half0 = w * widths[0] / 2
        for (s in listOf(-1, 1)) {
            val x = cx + s * half0
            thin.addRect(RectF(x - w * 0.025f, h * 0.44f, x + w * 0.025f, h * 0.465f), Path.Direction.CW)
        }
        // The lamp: a chain, a flared glass with a band, and a small drop.
        val lampTop = h * 0.40f
        val chain = Path().apply { moveTo(cx, h * 0.24f + h * 0.02f); lineTo(cx, lampTop) }
        val lw = w * 0.15f
        val lamp = Path().apply {
            moveTo(cx - lw * 0.35f, lampTop)
            lineTo(cx + lw * 0.35f, lampTop)
            cubicTo(cx + lw * 0.4f, lampTop + h * 0.05f, cx + lw, lampTop + h * 0.08f, cx + lw * 0.85f, lampTop + h * 0.14f)
            cubicTo(cx + lw * 0.7f, lampTop + h * 0.19f, cx + lw * 0.3f, lampTop + h * 0.2f, cx, lampTop + h * 0.205f)
            cubicTo(cx - lw * 0.3f, lampTop + h * 0.2f, cx - lw * 0.7f, lampTop + h * 0.19f, cx - lw * 0.85f, lampTop + h * 0.14f)
            cubicTo(cx - lw, lampTop + h * 0.08f, cx - lw * 0.4f, lampTop + h * 0.05f, cx - lw * 0.35f, lampTop)
            close()
        }
        val band = Path().apply { moveTo(cx - lw * 0.82f, lampTop + h * 0.105f); lineTo(cx + lw * 0.82f, lampTop + h * 0.105f) }
        val drop = Path().apply { addCircle(cx, lampTop + h * 0.235f, w * 0.014f, Path.Direction.CW) }
        val sill = Path().apply { moveTo(cx - w * 0.47f, base); lineTo(cx + w * 0.47f, base) }
        return Motif(
            bold = listOf(outer, inner),
            thin = listOf(thin, chain, band, sill),
            fills = listOf(lamp),
            solid = listOf(drop),
        )
    }

    /** A large crescent with a halo of fine arcs and a string of beads. */
    fun crescent(w: Float, h: Float): Motif {
        val s = min(w, h)
        val x = w * 0.5f; val y = h * 0.5f
        val moon = crescentPath(x, y, s * 0.34f, dx = 0.3f, dy = -0.16f, cut = 0.9f)
        val halo = Path().apply {
            for (k in listOf(0.42f, 0.48f)) addArc(RectF(x - s * k, y - s * k, x + s * k, y + s * k), 95f, 170f)
        }
        val beads = Path().apply {
            val r = s * 0.45f
            var a = 100.0
            while (a <= 260.0) {
                addCircle(x + r * cos(Math.toRadians(a)).toFloat(), y + r * sin(Math.toRadians(a)).toFloat(), s * 0.008f, Path.Direction.CW)
                a += 10.0
            }
        }
        return Motif(bold = listOf(moon), thin = listOf(halo), fills = listOf(moon), solid = listOf(beads))
    }

    /** Islimi: a heart-shaped palmette, two scrolling vines curling outward, leaves and a tulip bud. */
    fun arabesque(w: Float, h: Float): Motif {
        val u = min(w, h) / 100f
        val ox = (w - 100 * u) / 2
        val oy = (h - 100 * u) / 2
        fun X(v: Float, mirror: Boolean) = ox + (if (mirror) 100 - v else v) * u
        fun Y(v: Float) = oy + v * u
        val vines = Path(); val details = Path(); val leaves = Path(); val buds = Path()
        for (m in listOf(false, true)) {
            // The scroll: up and out from the base, then curling inward.
            vines.moveTo(X(50f, m), Y(94f))
            vines.cubicTo(X(30f, m), Y(90f), X(8f, m), Y(72f), X(12f, m), Y(50f))
            vines.cubicTo(X(15f, m), Y(33f), X(34f, m), Y(28f), X(40f, m), Y(40f))
            vines.cubicTo(X(45f, m), Y(50f), X(36f, m), Y(58f), X(29f, m), Y(53f))
            vines.cubicTo(X(24f, m), Y(49f), X(28f, m), Y(42f), X(33f, m), Y(45f))
            // An inner tendril with a small curl.
            details.moveTo(X(30f, m), Y(86f))
            details.cubicTo(X(30f, m), Y(76f), X(38f, m), Y(70f), X(44f, m), Y(74f))
            details.cubicTo(X(48f, m), Y(77f), X(45f, m), Y(82f), X(41f, m), Y(80f))
            // Side petal of the tulip.
            buds.moveTo(X(50f, m), Y(44f))
            buds.cubicTo(X(40f, m), Y(40f), X(37f, m), Y(28f), X(43f, m), Y(20f))
            buds.cubicTo(X(45f, m), Y(30f), X(48f, m), Y(36f), X(50f, m), Y(44f))
        }
        // Centre petal and stem.
        buds.moveTo(X(50f, false), Y(44f))
        buds.cubicTo(X(45f, false), Y(34f), X(46f, false), Y(20f), X(50f, false), Y(11f))
        buds.cubicTo(X(54f, false), Y(20f), X(55f, false), Y(34f), X(50f, false), Y(44f))
        buds.close()
        details.moveTo(X(50f, false), Y(94f)); details.lineTo(X(50f, false), Y(44f))
        // Leaves along the vines.
        fun leaf(x: Float, y: Float, deg: Float, len: Float, mirror: Boolean) {
            val l = Path().apply {
                moveTo(0f, 0f)
                quadTo(len * 0.5f, -len * 0.38f, len, 0f)
                quadTo(len * 0.5f, len * 0.38f, 0f, 0f)
                close()
            }
            l.transform(Matrix().apply { postRotate(if (mirror) 180f - deg else deg); postTranslate(X(x, mirror), Y(y)) })
            leaves.addPath(l)
        }
        for (m in listOf(false, true)) {
            leaf(19f, 79f, 200f, 11f * u, m)
            leaf(10f, 62f, 250f, 10f * u, m)
            leaf(22f, 34f, 300f, 9f * u, m)
            leaf(42f, 88f, 250f, 8f * u, m)
        }
        val base = Path().apply { addCircle(X(50f, false), Y(94f), 1.6f * u, Path.Direction.CW) }
        return Motif(bold = listOf(vines), thin = listOf(details, buds, leaves), fills = listOf(leaves, buds), solid = listOf(base))
    }

    /** A mashrabiya-like grille: rows of pointed arches. */
    fun lattice(w: Float, h: Float, tile: Float): Motif {
        val thin = Path()
        val cellH = tile * 1.3f
        var y = -cellH * 0.3f
        var row = 0
        while (y < h + cellH) {
            var x = if (row % 2 == 0) 0f else -tile / 2
            while (x < w + tile) {
                arch(thin, x + tile * 0.12f, x + tile * 0.88f, y + cellH * 0.55f, y + cellH * 0.1f, y + cellH * 0.95f)
                thin.close()
                x += tile
            }
            y += cellH
            row++
        }
        return Motif(thin = listOf(thin))
    }
}
