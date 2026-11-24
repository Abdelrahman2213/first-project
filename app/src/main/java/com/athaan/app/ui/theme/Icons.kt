package com.athaan.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.athaan.app.prayer.Prayer

/** Line icons drawn from the design's SVG paths (24×24, 1.7 stroke). */
object AIcons {
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

    private fun icon(name: String, vararg paths: String, fill: Boolean = false, width: Float = 1.7f, mirror: Boolean = false, size: Float = 24f) =
        ImageVector.Builder(
            name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = size, viewportHeight = size, autoMirror = mirror,
        ).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    fill = if (fill) SolidColor(Color.Black) else null,
                    stroke = if (fill) null else SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Pin = icon("pin", "M12 21s-7-6.2-7-11.5A7 7 0 0 1 19 9.5C19 14.8 12 21 12 21z", circle(12f, 9.5f, 2.5f))
    val Bell = icon("bell", "M6 16v-5a6 6 0 0 1 12 0v5l1.5 2h-15L6 16z", "M10 20a2 2 0 0 0 4 0")
    val Play = icon("play", "M8 5.5v13l11-6.5-11-6.5z", fill = true)
    val Pause = icon("pause", "M6.5 5h4v14h-4z", "M13.5 5h4v14h-4z", fill = true)
    val Stop = icon("stop", "M6 6h12v12H6z", fill = true)
    val Home = icon("home", "M4 11l8-7 8 7v9h-5v-6H9v6H4z")
    val Mic = icon("mic", "M12 3a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3z", "M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21")
    val Clock = icon("clock", circle(12f, 12f, 8.5f), "M12 7.5V12l3 2")
    val Sliders = icon("sliders", "M4 7h10M18 7h2M4 17h4M12 17h8", circle(16f, 7f, 2f), circle(10f, 17f, 2f))
    val Search = icon("search", circle(11f, 11f, 6.5f), "M20 20l-4.2-4.2")
    val Download = icon("download", "M12 4v11M7 10l5 5 5-5M5 20h14")
    val Check = icon("check", "M5 12.5l4.5 4.5L19 7.5", width = 2.2f)
    val Reset = icon("reset", "M4.5 12a7.5 7.5 0 1 0 2.2-5.3", "M4.5 4.5v3.2h3.2")
    val Minus = icon("minus", "M6 12h12", width = 2f)
    val Back = icon("back", "M15 6l-6 6 6 6", width = 1.8f, mirror = true)
    val Forward = icon("forward", "M9 6l6 6-6 6", width = 1.8f, mirror = true)
    val Moon = icon("moon", "M19 14.5A7.5 7.5 0 0 1 9.5 5a7.5 7.5 0 1 0 9.5 9.5z")
    val MoonStar = icon("moonStar", "M19 14.5A7.5 7.5 0 0 1 9.5 5a7.5 7.5 0 1 0 9.5 9.5z", circle(17.5f, 5.5f, 0.9f))
    val Sun = icon("sun", circle(12f, 12f, 4f), "M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M5.6 18.4L7 17M17 7l1.4-1.4")
    val Sunrise = icon("sunrise", "M4 18h16M7 18a5 5 0 0 1 10 0M12 6v3M5.5 11l1.5 1M18.5 11l-1.5 1")
    val Afternoon = icon("asr", circle(12f, 13f, 3.5f), "M12 5v2M4.5 13h2M17.5 13h2M6.7 7.7l1.4 1.4M15.9 9.1l1.4-1.4M4 19h16")
    val Sunset = icon("sunset", "M4 17h16M7.5 17a4.5 4.5 0 0 1 9 0M12 21v-2M9 9l3-3 3 3")
    val Volume = icon("volume", "M4 9.5h3.5L12 6v12l-4.5-3.5H4z", "M15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11")
    val Mute = icon("mute", "M4 9.5h3.5L12 6v12l-4.5-3.5H4z", "M16 9.5l5 5M21 9.5l-5 5")
    val Trash = icon("trash", "M5 7h14M10 7V5h4v2M7 7l1 13h8l1-13")
    val Locate = icon("locate", circle(12f, 12f, 7f), circle(12f, 12f, 2f), "M12 2v3M12 19v3M2 12h3M19 12h3")
    val Globe = icon("globe", circle(12f, 12f, 8.5f), "M3.5 12h17M12 3.5c2.5 2.6 2.5 14.4 0 17M12 3.5c-2.5 2.6-2.5 14.4 0 17")
    val Battery = icon("battery", "M4 8h14v8H4z", "M20 11v2")
    val Alarm = icon("alarm", circle(12f, 13f, 7.5f), "M12 9.5V13l2.5 1.5M4 5l3-2M20 5l-3-2")
    val Mosque = icon(
        "mosque",
        "M24 54V30c0-6 8-8 8-16 0 8 8 10 8 16v24", "M18 54h28", "M28 54v-8a4 4 0 0 1 8 0v8",
        "M32 10v-4", "M14 54V38l4-4 4 4v16M42 54V38l4-4 4 4v16",
        width = 2f, size = 64f,
    )

    val Compass = icon("compass", circle(12f, 12f, 8.5f), "M15.5 8.5l-2 5-5 2 2-5z")
    val Book = icon("book", "M4 5.5A2 2 0 0 1 6 3.5h5v16H6a2 2 0 0 0-2 2z", "M20 5.5a2 2 0 0 0-2-2h-5v16h5a2 2 0 0 1 2 2z")
    val Beads = icon("beads", circle(12f, 5f, 2f), circle(17.5f, 8.5f, 2f), circle(18f, 14.5f, 2f), circle(6.5f, 8.5f, 2f), circle(6f, 14.5f, 2f), "M12 17v4M10 21h4")
    val Calendar = icon("calendar", "M4 6.5h16v13.5H4z", "M4 10.5h16M8.5 4v4M15.5 4v4")
    val Grid = icon("grid", "M4 4h6.5v6.5H4zM13.5 4H20v6.5h-6.5zM4 13.5h6.5V20H4zM13.5 13.5H20V20h-6.5z")
    val Palette = icon("palette", "M12 3.5a8.5 8.5 0 1 0 0 17c1.2 0 1.8-.9 1.4-1.9-.5-1.2.2-2.4 1.5-2.4H17a3.5 3.5 0 0 0 3.5-3.5c0-5-3.8-9.2-8.5-9.2z", circle(8f, 11f, 1.2f), circle(11f, 7.5f, 1.2f), circle(15.5f, 8.5f, 1.2f))
    val Backup = icon("backup", "M12 15V4M8 8l4-4 4 4M4 15v3.5A1.5 1.5 0 0 0 5.5 20h13a1.5 1.5 0 0 0 1.5-1.5V15")
    val Tune = icon("tune", "M6 4v16M12 4v16M18 4v16", circle(6f, 15f, 2f), circle(12f, 8f, 2f), circle(18f, 13f, 2f))
    val Briefcase = icon("briefcase", "M3.5 8h17v11h-17z", "M8.5 8V5.5h7V8M3.5 13h17")

    fun forPrayer(p: Prayer): ImageVector = when (p) {
        Prayer.FAJR -> Moon
        Prayer.SUNRISE -> Sunrise
        Prayer.DHUHR -> Sun
        Prayer.ASR -> Afternoon
        Prayer.MAGHRIB -> Sunset
        Prayer.ISHA -> MoonStar
    }
}
