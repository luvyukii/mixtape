package com.mixtape.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mixtape.player.ui.theme.Tac

/**
 * When a cell flips during a pixel transition: mostly random, with a top-to-bottom bias
 * so it reads like a scan sweeping the screen rather than plain noise.
 */
private fun threshold(x: Int, y: Int, rows: Int): Float {
    var h = x * 73_856_093 xor y * 19_349_663
    h = h xor (h ushr 13)
    h *= 0x5bd1e995
    h = h xor (h ushr 15)
    val r = (h and 0x7fffffff) % 10_000 / 10_000f
    return 0.7f * r + 0.3f * (y.toFloat() / rows.coerceAtLeast(1))
}

private inline fun DrawScope.forEachCell(cell: Float, block: (x: Int, y: Int, t: Float, left: Float, top: Float) -> Unit) {
    val cols = (size.width / cell).toInt() + 1
    val rows = (size.height / cell).toInt() + 1
    for (y in 0 until rows) for (x in 0 until cols) {
        block(x, y, threshold(x, y, rows), x * cell, y * cell)
    }
}

/**
 * Covers content with black pixels as [progress] goes 0 → 1. Cells right at the flip
 * point glow green for an instant, like phosphor catching.
 */
fun Modifier.pixelCover(progress: () -> Float, cell: Dp = 12.dp, color: Color = Tac.Black): Modifier =
    drawWithContent {
        drawContent()
        val p = progress()
        if (p <= 0f) return@drawWithContent
        if (p >= 1f) {
            drawRect(color)
            return@drawWithContent
        }
        val c = cell.toPx()
        val sz = Size(c, c)
        forEachCell(c) { _, _, t, left, top ->
            val d = p - t
            when {
                d > 0.09f -> drawRect(color, Offset(left, top), sz)
                d > 0f -> drawRect(Tac.GreenDim, Offset(left, top), sz)
                d > -0.04f -> drawRect(Tac.Bright.copy(alpha = 0.18f), Offset(left, top), sz)
            }
        }
    }

/**
 * Erases the content pixel by pixel as [progress] goes 0 → 1, showing whatever is behind it.
 * Used for the boot screen breaking apart to reveal the app.
 */
fun Modifier.pixelErase(progress: () -> Float, cell: Dp = 14.dp): Modifier =
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        val p = progress()
        if (p <= 0f) return@drawWithContent
        val c = cell.toPx()
        val sz = Size(c, c)
        forEachCell(c) { _, _, t, left, top ->
            val d = p - t
            when {
                d > 0.06f -> drawRect(Color.Black, Offset(left, top), sz, blendMode = BlendMode.Clear)
                d > 0f -> drawRect(Tac.Bright.copy(alpha = 0.6f), Offset(left, top), sz)
            }
        }
    }

/** Fast pixel dissolve-in whenever [key] changes (for small in-screen tabs). */
@Composable
fun PixelReveal(key: Any?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val cover = remember { Animatable(0f) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(key) {
        if (first[0]) {
            first[0] = false
            return@LaunchedEffect
        }
        cover.snapTo(1f)
        cover.animateTo(0f, tween(220, easing = LinearEasing))
    }
    Box(modifier.pixelCover({ cover.value }, cell = 10.dp)) { content() }
}
