package com.mixtape.player.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import com.mixtape.player.ui.theme.Tac
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A wireframe globe turning slowly: meridians and parallels on a tilted sphere, with the
 * far side drawn fainter. Meant to sit faded in the background.
 */
@Composable
fun GlobeBackdrop(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "globe")
    val spin by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(48_000, easing = LinearEasing)), label = "spin")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "pulse")
    Canvas(modifier) { drawGlobe(spin, pulse) }
}

private fun DrawScope.drawGlobe(spin: Float, pulse: Float) {
    val r = size.minDimension / 2 * 0.92f
    val c = center
    val tilt = 0.38f // radians, so the poles lean toward the viewer
    val ct = cos(tilt)
    val st = sin(tilt)

    /** Projects a point on the sphere; returns screen offset and depth (z > 0 is the near side). */
    fun project(lat: Float, lon: Float): Triple<Float, Float, Float> {
        val x = cos(lat) * sin(lon + spin)
        val y = sin(lat)
        val z = cos(lat) * cos(lon + spin)
        val y2 = y * ct - z * st
        val z2 = y * st + z * ct
        return Triple(c.x + x * r, c.y - y2 * r, z2)
    }

    fun seg(a: Triple<Float, Float, Float>, b: Triple<Float, Float, Float>, base: Color) {
        val near = (a.third + b.third) / 2 > 0f
        drawLine(
            base.copy(alpha = if (near) base.alpha else base.alpha * 0.3f),
            Offset(a.first, a.second),
            Offset(b.first, b.second),
            strokeWidth = if (near) 1.3f else 1f,
        )
    }

    // Outline
    drawCircle(Tac.Green.copy(alpha = 0.55f), r, c, style = Stroke(1.3f))

    // Meridians (every 20°)
    val steps = 36
    for (m in 0 until 9) {
        val lon = (m * 20f) * (PI / 180).toFloat()
        var prev = project(-(PI / 2).toFloat(), lon)
        for (i in 1..steps) {
            val lat = (-PI / 2 + PI * i / steps).toFloat()
            val p = project(lat, lon)
            seg(prev, p, Tac.Green.copy(alpha = 0.7f))
            prev = p
        }
        // the opposite half of the same great circle
        prev = project(-(PI / 2).toFloat(), lon + PI.toFloat())
        for (i in 1..steps) {
            val lat = (-PI / 2 + PI * i / steps).toFloat()
            val p = project(lat, lon + PI.toFloat())
            seg(prev, p, Tac.Green.copy(alpha = 0.7f))
            prev = p
        }
    }

    // Parallels (every 30°), the equator brighter
    for (d in -60..60 step 30) {
        val lat = d * (PI / 180).toFloat()
        var prev = project(lat, 0f)
        for (i in 1..48) {
            val lon = (2 * PI * i / 48).toFloat()
            val p = project(lat, lon)
            seg(prev, p, if (d == 0) Tac.Bright.copy(alpha = 0.8f) else Tac.Green.copy(alpha = 0.6f))
            prev = p
        }
    }

    // A few "nodes" on the surface; the ones facing us blink amber.
    val nodes = listOf(0.45f to 0.6f, -0.3f to 2.1f, 0.9f to 3.9f, -0.7f to 5.0f, 0.1f to 1.2f)
    nodes.forEachIndexed { i, (lat, lon) ->
        val p = project(lat, lon)
        if (p.third > 0f) {
            val a = if (i % 2 == 0) 0.4f + 0.6f * pulse else 1f - 0.6f * pulse
            drawRect(Tac.Amber.copy(alpha = a), Offset(p.first - 2.5f, p.second - 2.5f), Size(5f, 5f))
        }
    }
}

/** Calls [onSwipe] after a deliberate vertical drag: [down] for closing, up for opening. */
fun Modifier.swipeVertical(down: Boolean, threshold: androidx.compose.ui.unit.Dp, onSwipe: () -> Unit): Modifier =
    pointerInput(down) {
        var total = 0f
        var fired = false
        detectVerticalDragGestures(
            onDragStart = { total = 0f; fired = false },
            onVerticalDrag = { change, dy ->
                total += dy
                val passed = if (down) total > threshold.toPx() else total < -threshold.toPx()
                if (passed && !fired) {
                    fired = true
                    onSwipe()
                }
                change.consume()
            },
        )
    }

/** A green scanline sweeping from top to bottom, with a soft trail behind it. */
@Composable
fun ScanSweep(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "sweep")
    val y by t.animateFloat(-0.1f, 1.1f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "y")
    Canvas(modifier) {
        val ly = size.height * y
        val trail = size.height * 0.18f
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                1f to Tac.Bright.copy(alpha = 0.07f),
                startY = ly - trail,
                endY = ly,
            ),
            topLeft = Offset(0f, ly - trail),
            size = Size(size.width, trail),
        )
        drawLine(Tac.Bright.copy(alpha = 0.28f), Offset(0f, ly), Offset(size.width, ly), strokeWidth = 1.5f)
    }
}

/**
 * Old-CRT power effect for [progress] 0 → 1: a bright line flashes across the middle and
 * widens, then opens out vertically into the full screen. Running it backwards collapses
 * the screen to a line, then a dot.
 */
fun Modifier.crtPower(progress: () -> Float): Modifier = this
    .graphicsLayer {
        val p = progress().coerceIn(0f, 1f)
        val split = 0.3f
        if (p < split) {
            scaleX = (p / split).coerceAtLeast(0.02f)
            scaleY = 0.004f
        } else {
            val q = (p - split) / (1f - split)
            scaleX = 1f
            scaleY = (q * q * (3 - 2 * q)).coerceAtLeast(0.004f) // smoothstep
        }
        transformOrigin = TransformOrigin.Center
        alpha = if (p <= 0f) 0f else 1f
    }
    .drawWithContent {
        drawContent()
        val p = progress().coerceIn(0f, 1f)
        // Phosphor flash: strong while it's a thin line, fading as the picture opens up.
        val flash = when {
            p < 0.3f -> 1f
            else -> (1f - (p - 0.3f) / 0.45f).coerceIn(0f, 1f)
        }
        if (flash > 0f) drawRect(Tac.Bright.copy(alpha = flash * 0.85f))
    }
