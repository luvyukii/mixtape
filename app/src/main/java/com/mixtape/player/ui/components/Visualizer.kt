package com.mixtape.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.mixtape.player.playback.AudioAnalyzer
import com.mixtape.player.ui.theme.Tac
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class VisMode(val label: String) { Spectrum("SPECTRUM"), Scope("OSCILLOSCOPE"), Radar("RADAR") }

/**
 * Smooths the analyzer's raw numbers for drawing: bars jump up fast and fall slowly,
 * peaks hang for a moment, and old waveforms are kept for a phosphor afterglow.
 */
private class VisState {
    val bands = FloatArray(AudioAnalyzer.BANDS)
    val peaks = FloatArray(AudioAnalyzer.BANDS)
    val trails = ArrayDeque<FloatArray>()
    var level = 0f
    var lastWave: FloatArray? = null

    fun update(a: AudioAnalyzer) {
        val fresh = System.nanoTime() - a.updatedAtNanos < 200_000_000L
        val target = if (fresh) a.bands else null
        for (i in bands.indices) {
            val t = target?.get(i) ?: 0f
            bands[i] = if (t > bands[i]) bands[i] + (t - bands[i]) * 0.65f else bands[i] * 0.88f
            peaks[i] = max(peaks[i] - 0.008f, bands[i])
        }
        level = if (fresh) level + (a.level - level) * 0.4f else level * 0.9f
        val w = if (fresh) a.wave else null
        if (w != null && w !== lastWave) {
            lastWave = w
            trails.addFirst(w)
            while (trails.size > 5) trails.removeLast()
        } else if (!fresh && trails.isNotEmpty()) {
            trails.removeLast()
        }
    }
}

@Composable
fun AudioVisualizer(analyzer: AudioAnalyzer, mode: VisMode, modifier: Modifier = Modifier) {
    val vis = remember { VisState() }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(analyzer) {
        while (true) {
            withFrameNanos { t ->
                vis.update(analyzer)
                frame = t
            }
        }
    }
    Canvas(modifier) {
        val t = frame / 1_000_000_000.0 // reading frame redraws every frame
        when (mode) {
            VisMode.Spectrum -> drawSpectrum(vis)
            VisMode.Scope -> drawScope(vis)
            VisMode.Radar -> drawRadar(vis, t)
        }
    }
}

private fun DrawScope.grid(columns: Int, rows: Int) {
    for (c in 1 until columns) {
        val x = size.width * c / columns
        drawLine(Tac.GreenFaint, Offset(x, 0f), Offset(x, size.height), 1f)
    }
    for (r in 1 until rows) {
        val y = size.height * r / rows
        drawLine(Tac.GreenFaint, Offset(0f, y), Offset(size.width, y), 1f)
    }
}

/** Segmented LED columns with amber tops and floating peak markers. */
private fun DrawScope.drawSpectrum(v: VisState) {
    grid(8, 4)
    val n = v.bands.size
    val gap = 2.dp.toPx()
    val w = (size.width - gap * (n - 1)) / n
    val cell = 4.dp.toPx()
    val cellGap = 1.5f
    for (i in 0 until n) {
        val x = i * (w + gap)
        val h = v.bands[i] * size.height
        var y = size.height
        while (y > size.height - h) {
            val top = max(y - cell, size.height - h)
            val frac = 1f - top / size.height
            val color = when {
                frac > 0.85f -> Tac.Amber
                frac > 0.6f -> Tac.Green
                else -> Tac.GreenMid
            }
            drawRect(color, Offset(x, top), Size(w, y - top - cellGap))
            y -= cell + cellGap
        }
        val py = size.height - v.peaks[i] * size.height
        if (v.peaks[i] > 0.02f) drawRect(Tac.Amber, Offset(x, py - 2f), Size(w, 2.5f))
    }
}

/** Oscilloscope trace with fading afterimages, plus a level readout bar. */
private fun DrawScope.drawScope(v: VisState) {
    grid(10, 6)
    val mid = size.height / 2
    drawLine(Tac.GreenDim, Offset(0f, mid), Offset(size.width, mid), 1f)
    v.trails.forEachIndexed { age, wave ->
        val alpha = 1f - age / 5f
        val path = Path()
        wave.forEachIndexed { i, s ->
            val x = size.width * i / (wave.size - 1)
            val y = mid - s * size.height * 0.9f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (age == 0) {
            // Soft glow under the live trace.
            drawPath(path, Tac.Green.copy(alpha = 0.18f), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
        }
        drawPath(path, Tac.Green.copy(alpha = alpha * if (age == 0) 1f else 0.45f), style = Stroke(if (age == 0) 2.dp.toPx() else 1.dp.toPx()))
    }
    // Level bar along the bottom edge
    val lw = size.width * v.level
    drawRect(if (v.level > 0.85f) Tac.Amber else Tac.Green, Offset(0f, size.height - 3.dp.toPx()), Size(lw, 3.dp.toPx()))
}

/** Radar: range rings, a rotating sweep, and the spectrum plotted around the circle. */
private fun DrawScope.drawRadar(v: VisState, t: Double) {
    val c = center
    val r = min(size.width, size.height) / 2 * 0.92f
    for (ring in 1..4) drawCircle(Tac.GreenFaint, r * ring / 4, c, style = Stroke(1f))
    drawLine(Tac.GreenFaint, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1f)
    drawLine(Tac.GreenFaint, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1f)

    // Spectrum mirrored around the full circle as a closed shape.
    val n = v.bands.size
    val path = Path()
    val total = n * 2
    for (k in 0..total) {
        val i = if (k < n) k else (total - k).coerceIn(0, n - 1)
        val ang = (k.toDouble() / total) * 2 * PI - PI / 2
        val rad = r * (0.18f + 0.78f * v.bands[i])
        val p = Offset(c.x + (cos(ang) * rad).toFloat(), c.y + (sin(ang) * rad).toFloat())
        if (k == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, Tac.Green.copy(alpha = 0.12f))
    drawPath(path, Tac.Green, style = Stroke(1.5.dp.toPx()))

    // Sweep with a fading tail; speed picks up with loudness.
    val sweep = (t * (1.2 + v.level * 2.5)) % (2 * PI)
    for (k in 0 until 24) {
        val a = sweep - k * 0.035
        val end = Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat())
        drawLine(Tac.Green.copy(alpha = (1f - k / 24f) * 0.5f), c, end, if (k == 0) 2.5f else 2f)
    }

    // Blips: loud bands light up as amber contacts at their position on the ring.
    for (i in 0 until n) {
        if (v.peaks[i] < 0.72f) continue
        val ang = (i.toDouble() / (n * 2)) * 2 * PI - PI / 2
        val rad = r * (0.18f + 0.78f * v.peaks[i])
        drawRect(
            Tac.Amber,
            Offset(c.x + (cos(ang) * rad).toFloat() - 3f, c.y + (sin(ang) * rad).toFloat() - 3f),
            Size(6f, 6f),
        )
    }
    // Pulsing core
    drawCircle(Tac.Green.copy(alpha = 0.25f + v.level * 0.6f), r * (0.06f + v.level * 0.08f), c)
    drawCircle(Color.Black, r * 0.03f, c)
}
