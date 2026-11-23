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
import com.athaan.app.art.Motif
import com.athaan.app.art.Motifs

/** Pictures drawn for notifications: the mosque ornament, in the app's ink and gold (no stars). */
object NotificationArt {
    private const val GOLD = 0xFFD6B370.toInt()
    private const val CARD = 0xFF131E35.toInt()
    private const val INK_TOP = 0xFF0E1628.toInt()
    private const val INK_BOTTOM = 0xFF1C2B4B.toInt()

    private var cornerCache: Pair<Pair<Int, Boolean>, Bitmap>? = null
    private var artCache: Bitmap? = null

    private fun Canvas.drawMotif(m: Motif, scale: Float, alpha: Float) {
        val a = { k: Float -> (255 * k * alpha).toInt().coerceIn(0, 255) }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD; this.alpha = a(0.10f) }
        m.fills.forEach { drawPath(it, fill) }
        val strap = 3.6f * scale
        val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GOLD; this.alpha = a(0.62f); style = Paint.Style.STROKE; strokeWidth = strap
            strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        }
        val core = Paint(outer).apply { color = CARD; this.alpha = 255; strokeWidth = strap - 2.2f * scale }
        m.bold.forEach { drawPath(it, outer); drawPath(it, core) }
        val thin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GOLD; this.alpha = a(0.5f); style = Paint.Style.STROKE; strokeWidth = 1.1f * scale
            strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        }
        m.thin.forEach { drawPath(it, thin) }
        val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD; this.alpha = a(0.7f) }
        m.solid.forEach { drawPath(it, solid) }
    }

    /**
     * The mosque for the card's top corner (the end side: left in Arabic), faded towards the
     * text and clipped to the card's rounded corner.
     */
    fun corner(context: Context, rtl: Boolean): Bitmap {
        val d = context.resources.displayMetrics.density
        val size = (132 * d).toInt().coerceAtLeast(64)
        cornerCache?.let { (k, b) -> if (k == size to rtl) return b }
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val box = size * 0.86f
        c.save()
        c.translate(if (rtl) size * 0.04f else size - box - size * 0.04f, size * 0.06f)
        c.drawMotif(Motifs.mosque(box, box), d * 0.8f, 0.55f)
        c.restore()
        val cx = if (rtl) 0f else size.toFloat()
        val fade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, 0f, size * 1.25f, intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), fade)
        // Follow the card's 20dp rounded corner.
        val r = 20 * d
        val mask = Path().apply {
            addRoundRect(RectF(if (rtl) 0f else -r, 0f, if (rtl) size + r else size.toFloat(), size + r), r, r, Path.Direction.CW)
        }
        val clip = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(clip).apply { clipPath(mask); drawBitmap(b, 0f, 0f, null) }
        b.recycle()
        cornerCache = (size to rtl) to clip
        return clip
    }

    /** Artwork for the playing adhan (media player, lock screen): a mosque under a crescent, on ink. */
    fun adhanArt(): Bitmap {
        artCache?.let { return it }
        val s = 640
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawRect(0f, 0f, s.toFloat(), s.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, s.toFloat(), s.toFloat(), INK_TOP, INK_BOTTOM, Shader.TileMode.CLAMP)
        })
        c.drawCircle(s / 2f, s * 0.55f, s * 0.46f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(s / 2f, s * 0.55f, s * 0.46f, intArrayOf(0x3AD6B370, 0x00D6B370), null, Shader.TileMode.CLAMP)
        })
        val box = s * 0.62f
        c.save()
        c.translate((s - box) / 2, s * 0.24f)
        c.drawMotif(Motifs.mosque(box, box), 2.4f, 0.85f)
        c.restore()
        artCache = b
        return b
    }
}
