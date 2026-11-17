package com.bulkplan.tracker.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The app's own icon set, drawn on a 24-unit grid.
 * Line icons are white strokes (tinted by Icon); colour icons keep their colours (use tint = Color.Unspecified).
 */
object Ico {
    private class P(val d: String, val stroke: Color? = Color.White, val fill: Color? = null, val w: Float = 2f)

    private fun build(name: String, vararg paths: P): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { p ->
                addPath(
                    pathData = addPathNodes(p.d),
                    fill = p.fill?.let { SolidColor(it) },
                    stroke = p.stroke?.let { SolidColor(it) },
                    strokeLineWidth = if (p.stroke != null) p.w else 0f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun line(name: String, d: String, w: Float = 2f) = build(name, P(d, w = w))

    // ---- line icons ----
    val Calendar = line("calendar", "M4 7a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z M4 10h16 M8 3v4 M16 3v4 M9 15l2 2 4-4")
    val Fork = line("fork", "M7 3v7 M4.5 3v4.5a2.5 2.5 0 0 0 5 0V3 M7 10v11 M17 21V3c-2.2 1.3-3.5 4-3.5 8h3.5")
    val Chart = line("chart", "M5 20v-7 M12 20V5 M19 20v-11 M3 20h18")
    val Dumbbell = line("dumbbell", "M6.5 6.5v11 M17.5 6.5v11 M3.5 9v6 M20.5 9v6 M6.5 12h11")
    val Chat = line("chat", "M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z M8.5 12h.01 M12 12h.01 M15.5 12h.01")
    val Check = line("check", "M5 12.5l4.5 4.5L19 7.5", 2.5f)
    val Down = line("down", "M6 9l6 6 6-6")
    val Up = line("up", "M6 15l6-6 6 6")
    val Right = line("right", "M9 6l6 6-6 6")
    val Left = line("left", "M15 6l-6 6 6 6")
    val Plus = line("plus", "M12 5v14 M5 12h14", 2.4f)
    val Filter = line("filter", "M4 7h9 M17 7h3 M4 17h3 M11 17h9 M15 5v4 M9 15v4")
    val Note = line("note", "M7 3h7l4 4v14H7z M14 3v4h4 M10 12h5 M10 16h5")
    val Timer = line("timer", "M12 9v4.5l2.5 1.5 M9.5 2.5h5 M12 21a8 8 0 1 0 0-16 8 8 0 0 0 0 16z")
    val Camera = line("camera", "M4 8h3l2-3h6l2 3h3v11H4z M12 16.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z")
    val Barcode = line("barcode", "M4 5v14 M7.5 5v14 M10.5 5v14 M14 5v14 M16.5 5v14 M20 5v14")
    val Search = line("search", "M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14z M20.5 20.5l-4.4-4.4")
    val Sparkle = line("sparkle", "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z M18.5 16l.7 2 2 .7-2 .7-.7 2-.7-2-2-.7 2-.7z")
    val Bookmark = line("bookmark", "M6.5 3.5h11v17l-5.5-3.8-5.5 3.8z")
    val Trash = line("trash", "M4 7h16 M9.5 7V4h5v3 M6 7l1 13h10l1-13")
    val Close = line("close", "M6 6l12 12 M18 6L6 18")
    val Edit = line("edit", "M4 20h4L19 9l-4-4L4 16z M13 7l4 4")
    val Clipboard = line("clipboard", "M9 3.5h6v3H9z M7 5H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-1 M9 14l2 2 4-4")
    val Bulb = line("bulb", "M9 18h6 M10 21h4 M12 3a6 6 0 0 0-4 10.5c.8.8 1 1.5 1 2.5h6c0-1 .2-1.7 1-2.5A6 6 0 0 0 12 3z")
    val Person = line("person", "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M4.5 21a7.5 7.5 0 0 1 15 0")
    val Sync = line("sync", "M20 12a8 8 0 0 1-14.2 5 M4 12a8 8 0 0 1 14.2-5 M18.5 3v4h-4 M5.5 21v-4h4")
    val Heart = line("heart", "M12 20s-7.5-4.6-7.5-10A4.3 4.3 0 0 1 12 7.4 4.3 4.3 0 0 1 19.5 10c0 5.4-7.5 10-7.5 10z")

    // ---- colour icons ----
    val Send = build("send", P("M3 11.4L21 3l-8.4 18-2.2-7.4z", stroke = null, fill = Color.White))

    val Sunrise = build("sunrise",
        P("M6.5 17a5.5 5.5 0 0 1 11 0z", stroke = null, fill = Color(0xFFFBBF24)),
        P("M2.5 17h19 M12 5.5v2.5 M5.3 9.3l1.6 1.6 M18.7 9.3l-1.6 1.6 M2.5 13.5h2 M19.5 13.5h2 M8 20.5h8", stroke = Color(0xFFFBBF24)))
    val Sun = build("sun",
        P("M12 7.5a4.5 4.5 0 1 0 0 9 4.5 4.5 0 1 0 0-9z", stroke = null, fill = Color(0xFFFBBF24)),
        P("M12 2v2 M12 20v2 M4.9 4.9l1.4 1.4 M17.7 17.7l1.4 1.4 M2 12h2 M20 12h2 M4.9 19.1l1.4-1.4 M17.7 6.3l1.4-1.4", stroke = Color(0xFFFBBF24)))
    val Moon = build("moon", P("M19.5 14.5A8 8 0 0 1 9.5 4.5 8 8 0 1 0 19.5 14.5z", stroke = null, fill = Color(0xFFB9B4F0)))
    val Cookie = build("cookie",
        P("M12 3a9 9 0 1 0 9 9 3 3 0 0 1-3.5-3A3 3 0 0 1 14 5.5 3 3 0 0 1 12 3z", stroke = null, fill = Color(0xFFF59E0B)),
        P("M8 10.5h.01 M9.5 15.5h.01 M14 13.5h.01 M12.5 18h.01", stroke = Color(0xFF7C3E06), w = 2.6f))
    val Flame = build("flame", P("M12 2.5c1.2 3.2 5.5 5.4 5.5 10.5a5.5 5.5 0 0 1-11 0c0-2.3 1.2-4 2.3-5 0 2.2 1 3.3 2.2 3.3 0-3.3-1.1-5.4 1-8.8z", stroke = null, fill = Color(0xFFFB923C)))
    val Arm = build("arm", P("M5 20c-.6-4.5.6-8.3 3.4-10.4L10 5.2c.3-.9 1.2-1.4 2.1-1.1l1.8.6c.8.3 1.2 1.1 1 1.9L14.2 9c2.7-.6 5.3 1 5.8 3.8.6 3.5-1.6 6.4-5.3 7.2z", stroke = null, fill = Color(0xFF60A5FA)))
    val Wheat = build("wheat",
        P("M12 21V8", stroke = Color(0xFFFBBF24)),
        P("M12 8C9.8 7 8.8 5 9 2.8c2 .9 3 2.8 3 5.2z M12 8c2.2-1 3.2-3 3-5.2-2 .9-3 2.8-3 5.2z M12 13c-2.2-1-3.2-3-3-5.2 2 .9 3 2.8 3 5.2z M12 13c2.2-1 3.2-3 3-5.2-2 .9-3 2.8-3 5.2z M12 18c-2.2-1-3.2-3-3-5.2 2 .9 3 2.8 3 5.2z M12 18c2.2-1 3.2-3 3-5.2-2 .9-3 2.8-3 5.2z", stroke = null, fill = Color(0xFFFBBF24)))
    val Drop = build("drop", P("M12 2.5c3.3 4.3 6.5 8 6.5 11.8a6.5 6.5 0 0 1-13 0C5.5 10.5 8.7 6.8 12 2.5z", stroke = null, fill = Color(0xFFEF4444)))
    val SkinDrop = build("skin", P("M12 2.5c3.3 4.3 6.5 8 6.5 11.8a6.5 6.5 0 0 1-13 0C5.5 10.5 8.7 6.8 12 2.5z", stroke = null, fill = Color(0xFF5EEAD4)),
        P("M9 15a3 3 0 0 0 3 3", stroke = Color(0xFF0F766E), w = 1.6f))
    val Pill = build("pill",
        P("M10.6 3.6a5 5 0 0 1 7.1 7.1L14 14.4 6.9 7.3z", stroke = null, fill = Color(0xFFF472B6)),
        P("M6.9 7.3L14 14.4l-3.6 3.7a5 5 0 0 1-7.1-7.1z", stroke = null, fill = Color(0xFFE5E7EB)))
    val Bolt = build("bolt", P("M13.5 2L4.5 13.5h6.5l-1 8.5 9-11.5h-6.5z", stroke = null, fill = Color(0xFFFACC15)))
    val Barbell = build("barbell",
        P("M6.5 6v12 M17.5 6v12 M3.5 9v6 M20.5 9v6", stroke = Color(0xFF60A5FA), w = 2.6f),
        P("M6.5 12h11", stroke = Color(0xFF93C5FD)))
    val TaskIcon = build("task",
        P("M9 3.5h6v3H9z M7 5H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-1", stroke = Color(0xFF94A3B8)),
        P("M9 14l2 2 4-4", stroke = Color(0xFF4ADE80)))
    val Coach = build("coach", P("M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z", stroke = null, fill = Color(0xFF3B82F6)),
        P("M8.5 12h.01 M12 12h.01 M15.5 12h.01", stroke = Color.White, w = 2.4f))
    val Scale = build("scale", P("M5 4h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z", stroke = Color(0xFF94A3B8)),
        P("M8.5 9.5a5 5 0 0 1 7 0 M12 9.5l1.5-2", stroke = Color(0xFF60A5FA)))
}
