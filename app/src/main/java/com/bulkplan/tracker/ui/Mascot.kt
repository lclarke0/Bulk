package com.bulkplan.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val Shell = Color(0xFFE9EDF5)
private val ShellShade = Color(0xFFB8C1D3)
private val Visor = Color(0xFF0B1220)
private val Glow = Color(0xFF38BDF8)

/** The coach mascot: a friendly robot with a dumbbell badge, giving a thumbs-up. Drawn in code. */
@Composable
fun CoachBot(size: Dp = 150.dp, thumbsUp: Boolean = true) {
    Canvas(Modifier.size(size)) { drawBot(thumbsUp) }
}

/** Just the robot's head, for chat avatars. */
@Composable
fun CoachAvatar(size: Dp = 40.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width
        drawCircle(Color(0xFF1E2A44), s / 2)
        drawCircle(Glow.copy(alpha = .5f), s / 2, style = Stroke(s * .04f))
        drawHead(Offset(s * .5f, s * .54f), s * .62f)
    }
}

private fun DrawScope.drawHead(c: Offset, w: Float) {
    val h = w * .82f
    // ears
    drawCircle(ShellShade, w * .11f, Offset(c.x - w * .5f, c.y))
    drawCircle(ShellShade, w * .11f, Offset(c.x + w * .5f, c.y))
    // antenna
    drawLine(Glow, Offset(c.x + w * .18f, c.y - h * .5f), Offset(c.x + w * .3f, c.y - h * .82f), w * .05f, StrokeCap.Round)
    drawCircle(Glow, w * .05f, Offset(c.x + w * .31f, c.y - h * .84f))
    // shell
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White, Shell, ShellShade), startY = c.y - h / 2, endY = c.y + h / 2),
        Offset(c.x - w / 2, c.y - h / 2), Size(w, h), CornerRadius(w * .32f),
    )
    // visor
    val vw = w * .78f; val vh = h * .62f
    drawRoundRect(Visor, Offset(c.x - vw / 2, c.y - vh / 2 + h * .02f), Size(vw, vh), CornerRadius(vw * .3f))
    // happy eyes (upturned arcs) and smile
    val eye = vw * .2f
    listOf(-1f, 1f).forEach { side ->
        drawArc(Glow, 200f, 140f, false, Offset(c.x + side * vw * .2f - eye / 2, c.y - vh * .12f), Size(eye, eye * .9f),
            style = Stroke(w * .055f, cap = StrokeCap.Round))
    }
    drawArc(Glow, 20f, 140f, false, Offset(c.x - vw * .14f, c.y + vh * .02f), Size(vw * .28f, vh * .3f), style = Stroke(w * .045f, cap = StrokeCap.Round))
}

private fun DrawScope.drawBot(thumbsUp: Boolean) {
    val s = size.width
    // soft blue glow behind
    drawCircle(Brush.radialGradient(listOf(Glow.copy(alpha = .28f), Color.Transparent), center = Offset(s * .45f, s * .5f), radius = s * .5f), s * .5f, Offset(s * .45f, s * .5f))
    // body
    val bw = s * .42f; val bh = s * .3f; val bx = s * .45f - bw / 2; val by = s * .6f
    drawRoundRect(Brush.verticalGradient(listOf(Color.White, Shell, ShellShade), startY = by, endY = by + bh), Offset(bx, by), Size(bw, bh), CornerRadius(s * .08f))
    // dumbbell badge
    val cx = s * .45f; val cy = by + bh * .48f
    drawLine(Visor, Offset(cx - bw * .22f, cy), Offset(cx + bw * .22f, cy), s * .025f, StrokeCap.Round)
    listOf(-1f, 1f).forEach { d ->
        drawRoundRect(Visor, Offset(cx + d * bw * .22f - s * .022f, cy - s * .045f), Size(s * .044f, s * .09f), CornerRadius(s * .012f))
        drawRoundRect(Visor, Offset(cx + d * bw * .3f - s * .016f, cy - s * .03f), Size(s * .032f, s * .06f), CornerRadius(s * .01f))
    }
    // left arm resting
    drawRoundRect(ShellShade, Offset(bx - s * .07f, by + s * .03f), Size(s * .08f, bh * .7f), CornerRadius(s * .04f))
    // right arm: raised fist with thumb up
    if (thumbsUp) {
        val ax = bx + bw + s * .02f; val ay = by + s * .05f
        rotate(-25f, Offset(ax, ay + s * .05f)) {
            drawRoundRect(ShellShade, Offset(ax - s * .04f, ay - s * .05f), Size(s * .08f, s * .16f), CornerRadius(s * .04f))
        }
        val fx = ax + s * .08f; val fy = ay - s * .1f
        drawRoundRect(Brush.verticalGradient(listOf(Color.White, Shell, ShellShade), startY = fy, endY = fy + s * .12f),
            Offset(fx - s * .07f, fy), Size(s * .14f, s * .12f), CornerRadius(s * .04f))
        drawRoundRect(Shell, Offset(fx - s * .035f, fy - s * .085f), Size(s * .055f, s * .11f), CornerRadius(s * .028f))
        listOf(.03f, .06f, .09f).forEach { k -> drawLine(ShellShade, Offset(fx + s * .02f, fy + s * k), Offset(fx + s * .065f, fy + s * k), s * .008f) }
    }
    // head
    drawHead(Offset(s * .45f, s * .36f), s * .5f)
    // little motion ticks by the antenna
    drawLine(Glow, Offset(s * .74f, s * .06f), Offset(s * .79f, s * .0f), s * .02f, StrokeCap.Round)
    drawLine(Glow, Offset(s * .79f, s * .1f), Offset(s * .86f, s * .07f), s * .02f, StrokeCap.Round)
}
