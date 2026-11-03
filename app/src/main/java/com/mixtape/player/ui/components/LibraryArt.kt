package com.mixtape.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mixtape.player.data.Library
import com.mixtape.player.ui.theme.Tac
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Runs 0 → 1 each time [key] changes, for the list scan-in. */
@Composable
fun rememberEntrance(key: Any?): Animatable<Float, *> {
    val a = remember { Animatable(0f) }
    LaunchedEffect(key) {
        a.snapTo(0f)
        a.animateTo(1f, tween(650, easing = LinearEasing))
    }
    return a
}

/**
 * Rows scan in one after another: each fades up and slides a few pixels from the left.
 * Only the first screenful is staggered; rows further down are already in place.
 */
fun Modifier.entrance(progress: Animatable<Float, *>, index: Int): Modifier = graphicsLayer {
    val i = min(index, 14)
    val local = ((progress.value * 1.9f) - i * 0.06f).coerceIn(0f, 1f)
    val eased = FastOutSlowInEasing.transform(local)
    alpha = eased
    translationX = (1f - eased) * 18.dp.toPx()
}

/** A tiny rotating radar scope. */
@Composable
fun ScopeGlyph(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "scope")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "a")
    val blip by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "b")
    Canvas(modifier) {
        val r = size.minDimension / 2
        val c = center
        drawCircle(Tac.GreenDim, r - 0.5f, c, style = Stroke(1f))
        drawCircle(Tac.GreenFaint, r * 0.5f, c, style = Stroke(1f))
        rotate(angle, c) {
            for (k in 0 until 10) {
                drawLine(
                    Tac.Bright.copy(alpha = (1f - k / 10f) * 0.5f),
                    c,
                    Offset(c.x + r * cos(-k * 0.08f), c.y + r * sin(-k * 0.08f)),
                    strokeWidth = 1.2f,
                )
            }
        }
        drawRect(Tac.Amber.copy(alpha = 0.3f + 0.7f * blip), Offset(c.x + r * 0.35f, c.y - r * 0.45f), Size(2.5f, 2.5f))
    }
}

/** Library status strip: live scope, counts and total running time. */
@Composable
fun LibraryHud(library: Library, modifier: Modifier = Modifier) {
    val totalMs = remember(library) { library.songs.sumOf { it.durationMs } }
    val hours = totalMs / 3_600_000
    val minutes = (totalMs / 60_000) % 60
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScopeGlyph(Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            "%,d TRK  ·  %,d ALB  ·  %,d ART  ·  %dH %02dM".format(
                library.songs.size, library.albums.size, library.artists.size, hours, minutes,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = Tac.GreenMid,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Empty-library art: a wireframe disc spinning above a perspective grid, with a scan line
 * sweeping down through it.
 */
@Composable
fun EmptyArt(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "empty")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "spin")
    val scan by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "scan")
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.62f
        // Perspective floor grid
        for (i in 0..8) {
            val y = horizon + (h - horizon) * (i / 8f) * (i / 8f)
            drawLine(Tac.GreenFaint, Offset(0f, y), Offset(w, y), 1f)
        }
        for (i in -6..6) {
            drawLine(Tac.GreenFaint, Offset(w / 2 + i * w * 0.03f, horizon), Offset(w / 2 + i * w * 0.16f, h), 1f)
        }
        // Wireframe disc: rings plus a spinning spoke pattern, squashed for depth.
        val c = Offset(w / 2, h * 0.36f)
        val r = min(w, h) * 0.26f
        for (k in 1..4) {
            val rr = r * k / 4
            drawOval(Tac.GreenDim, Offset(c.x - rr, c.y - rr * 0.42f), Size(rr * 2, rr * 0.84f), style = Stroke(1f))
        }
        for (s in 0 until 12) {
            val a = Math.toRadians((spin + s * 30).toDouble())
            drawLine(
                if (s == 0) Tac.Bright.copy(alpha = 0.7f) else Tac.GreenFaint,
                c,
                Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r * 0.42f).toFloat()),
                1f,
            )
        }
        drawOval(Tac.Green, Offset(c.x - 3f, c.y - 1.5f), Size(6f, 3f))
        // Scan line
        val sy = h * scan
        drawLine(Tac.Bright.copy(alpha = 0.22f), Offset(0f, sy), Offset(w, sy), 1.5f)
    }
}
