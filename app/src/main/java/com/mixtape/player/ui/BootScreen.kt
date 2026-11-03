package com.mixtape.player.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixtape.player.ui.components.pixelErase
import com.mixtape.player.ui.components.scanlines
import com.mixtape.player.ui.theme.Tac
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Startup sequence: status lines type out, a pixel X assembles, then the whole screen
 * breaks apart into pixels to reveal the app. Tap anywhere to skip.
 */
@Composable
fun BootScreen(trackCount: Int, onDone: () -> Unit) {
    val count by rememberUpdatedState(trackCount)
    val lines = remember {
        listOf(
            "NEXUS AUDIO TERMINAL",
            "> audio engine ........ OK",
            "> crossfade bus ....... OK",
            "> index library ....... %s",
            "> link ................ ESTABLISHED",
        )
    }
    var shownLines by remember { mutableIntStateOf(0) }
    var typed by remember { mutableIntStateOf(0) }
    var countText by remember { mutableStateOf("…") }
    val logo = remember { Animatable(0f) }
    val erase = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var finishing by remember { mutableStateOf(false) }
    val done by rememberUpdatedState(onDone)

    fun finish() {
        if (finishing) return
        finishing = true
        scope.launch {
            erase.animateTo(1f, tween(420, easing = LinearEasing))
            done()
        }
    }

    LaunchedEffect(Unit) {
        for (i in lines.indices) {
            if (i == 3) countText = if (count > 0) "%,d TRK".format(count) else "SCANNING"
            shownLines = i + 1
            val len = lines[i].length + 8
            typed = 0
            while (typed < len) {
                typed += 3
                delay(12)
            }
            delay(50)
        }
        logo.animateTo(1f, tween(420, easing = LinearEasing))
        delay(260)
        finish()
    }

    Box(
        Modifier
            .fillMaxSize()
            .pixelErase({ erase.value })
            .background(Tac.Black)
            .scanlines(true)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() }
            .padding(28.dp),
    ) {
        Column(Modifier.align(Alignment.TopStart).padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 until shownLines) {
                val full = lines[i].replace("%s", countText)
                val text = if (i == shownLines - 1) full.take(typed) else full
                Text(
                    text,
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        i == 0 -> Tac.Green
                        text.endsWith("OK") || text.endsWith("ESTABLISHED") || text.endsWith("TRK") -> Tac.GreenMid
                        else -> Tac.GreenDim
                    },
                )
            }
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            PixelX(progress = { logo.value }, modifier = Modifier.size(120.dp))
            Spacer(Modifier.height(22.dp))
            Text(
                "NEXUS",
                style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 10.sp),
                color = Tac.Green.copy(alpha = logo.value),
            )
        }
    }
}

/** An X built from square pixels that light up in random order as [progress] runs. */
@Composable
private fun PixelX(progress: () -> Float, modifier: Modifier) {
    Canvas(modifier) {
        val n = 15
        val cell = size.width / n
        val p = progress()
        for (y in 0 until n) for (x in 0 until n) {
            val onDiagonal = abs(x - y) <= 0 || abs(x + y - (n - 1)) <= 0
            val nearDiagonal = abs(x - y) == 1 || abs(x + y - (n - 1)) == 1
            if (!onDiagonal && !nearDiagonal) continue
            var h = x * 92_837_111 xor y * 689_287_499
            h = h xor (h ushr 13)
            val t = (h and 0x7fffffff) % 1000 / 1000f
            val d = p - t
            if (d <= 0f) continue
            val o = Offset(x * cell, y * cell)
            if (onDiagonal) {
                // Glow halo, then the bright pixel; fresh pixels flash brighter.
                drawRect(Tac.Bright.copy(alpha = 0.12f), o - Offset(cell * 0.5f, cell * 0.5f), Size(cell * 2, cell * 2))
                drawRect(if (d < 0.08f) Tac.Green else Tac.Bright, o, Size(cell * 0.9f, cell * 0.9f))
            } else {
                drawRect(Tac.Bright.copy(alpha = 0.16f), o, Size(cell * 0.9f, cell * 0.9f))
            }
        }
    }
}
