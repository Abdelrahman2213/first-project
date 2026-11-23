package com.athaan.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Pictures drawn for notifications: the Shamsa ornament, in the app's ink and gold. */
object NotificationArt {
    private const val GOLD = 0xFFD6B370.toInt()
    private const val INK_TOP = 0xFF0E1628.toInt()
    private const val INK_BOTTOM = 0xFF1C2B4B.toInt()

    private var cornerCache: Pair<Pair<Int, Boolean>, Bitmap>? = null
    private var artCache: Bitmap? = null

    /** The Shamsa (sun medallion): rays, a ring, an eight-point star and a rosette. */
    private fun shamsa(c: Canvas, cx: Float, cy: Float, r: Float, alpha: Int, stroke: Float, squares: Boolean = true) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GOLD; this.alpha = alpha; style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND
        }
        // Rays, long and short in turn.
        for (i in 0 until 48) {
            val a = Math.toRadians(i * 7.5).toFloat()
            val r0 = r * 0.74f
            val r1 = if (i % 2 == 0) r else r * 0.88f
            c.drawLine(cx + r0 * cos(a), cy + r0 * sin(a), cx + r1 * cos(a), cy + r1 * sin(a), p)
        }
        c.drawCircle(cx, cy, r * 0.70f, p)
        c.drawCircle(cx, cy, r * 0.665f, p.apply { this.alpha = alpha / 2 })
        p.alpha = alpha
        // Eight-point star made of two squares.
        fun square(rot: Double, rr: Float) {
            val path = Path()
            for (k in 0 until 4) {
                val a = Math.toRadians(rot + k * 90.0)
                val x = cx + rr * cos(a).toFloat(); val y = cy + rr * sin(a).toFloat()
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close(); c.drawPath(path, p)
        }
        if (squares) { square(0.0, r * 0.6f); square(45.0, r * 0.6f) }
        // Inner interlaced star.
        val star = Path()
        for (k in 0 until 16) {
            val a = Math.toRadians(k * 22.5 - 90)
            val rr = if (k % 2 == 0) r * 0.42f else r * 0.27f
            val x = cx + rr * cos(a).toFloat(); val y = cy + rr * sin(a).toFloat()
            if (k == 0) star.moveTo(x, y) else star.lineTo(x, y)
        }
        star.close(); c.drawPath(star, p)
        c.drawCircle(cx, cy, r * 0.18f, p)
        c.drawCircle(cx, cy, r * 0.07f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD; this.alpha = alpha })
    }

    /**
     * The ornament for the card's top corner (the end side: left in Arabic): a quarter of the
     * medallion, faded towards the text and clipped to the card's rounded corner.
     */
    fun corner(context: Context, rtl: Boolean): Bitmap {
        val d = context.resources.displayMetrics.density
        val size = (132 * d).toInt().coerceAtLeast(64)
        cornerCache?.let { (k, b) -> if (k == size to rtl) return b }
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val cx = if (rtl) 0f else size.toFloat()
        // A sun rising from the corner: rays and rings, with the star small at the very corner.
        shamsa(c, cx, 0f, size * 0.92f, 105, 1.1f * d, squares = false)
        // Fade away from the corner.
        val fade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, 0f, size * 1.0f, intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), fade)
        // Follow the card's 20dp rounded corner.
        val r = 20 * d
        val mask = Path().apply {
            addRoundRect(RectF(if (rtl) 0f else -r, 0f, if (rtl) size + r else size.toFloat(), size + r), r, r, Path.Direction.CW)
        }
        val clip = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(clip).apply {
            clipPath(mask)
            drawBitmap(b, 0f, 0f, null)
        }
        b.recycle()
        cornerCache = (size to rtl) to clip
        return clip
    }

    /** Artwork for the playing adhan (media player, lock screen): the full medallion on ink. */
    fun adhanArt(): Bitmap {
        artCache?.let { return it }
        val s = 640
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawRect(0f, 0f, s.toFloat(), s.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, s.toFloat(), s.toFloat(), INK_TOP, INK_BOTTOM, Shader.TileMode.CLAMP)
        })
        c.drawCircle(s / 2f, s / 2f, s * 0.48f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(s / 2f, s / 2f, s * 0.48f, intArrayOf(0x40D6B370, 0x00D6B370), null, Shader.TileMode.CLAMP)
        })
        shamsa(c, s / 2f, s / 2f, min(s, s) * 0.40f, 125, 2.4f)
        // Darken the middle band where the player writes the title.
        c.drawRect(0f, 0f, s.toFloat(), s.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, s.toFloat(), intArrayOf(0x330B1220, 0x660B1220, 0x330B1220), null, Shader.TileMode.CLAMP)
        })
        artCache = b
        return b
    }
}
