package com.mixtape.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mixtape.player.data.Settings
import com.mixtape.player.playback.PlaybackState
import com.mixtape.player.playback.RepeatMode
import com.mixtape.player.ui.components.AlbumArt
import com.mixtape.player.ui.components.SegmentMeter
import com.mixtape.player.ui.components.SignalBars
import com.mixtape.player.ui.components.TacButtonShape
import com.mixtape.player.ui.components.TacLabel
import com.mixtape.player.ui.components.cornerBrackets
import com.mixtape.player.ui.components.formatTime
import com.mixtape.player.ui.theme.Tac

@Composable
fun NowPlayingScreen(
    state: PlaybackState,
    crossfadeMs: Int,
    onClose: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onCrossfadeSeconds: (Int) -> Unit,
    onJumpTo: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
) {
    val song = state.current
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Tac.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header strip
        item {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Close player", tint = Tac.Green) }
                    Text(
                        "PLAYBACK // CH-01",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        when {
                            state.crossfading -> "◆ XFADE "
                            state.isPlaying -> "● LIVE "
                            else -> "‖ HOLD "
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.isPlaying || state.crossfading) Tac.Amber else Tac.GreenMid,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Tac.GreenDim))
            }
        }

        // Cover in a targeting frame
        item {
            Box(
                Modifier
                    .padding(horizontal = 36.dp, vertical = 16.dp)
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .cornerBrackets(inset = 0.dp, length = 22.dp)
                    .padding(10.dp)
                    .border(1.dp, Tac.GreenFaint),
            ) {
                AlbumArt(song, Modifier.fillMaxSize(), sizePx = 1000, shape = MaterialTheme.shapes.extraSmall)
            }
        }

        // Title block + readouts
        item {
            Column(Modifier.padding(horizontal = 24.dp).fillMaxWidth()) {
                Text(
                    song?.title?.uppercase() ?: "NO SIGNAL",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song?.let { "${it.artist} — ${it.album}" } ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tac.GreenMid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    listOf(
                        "TRK %03d/%03d".format(state.index + 1, state.queue.size),
                        "SHUF " + if (state.shuffle) "ON" else "OFF",
                        "RPT " + state.repeat.name,
                        "XF %02ds".format(crossfadeMs / 1000),
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = Tac.GreenMid,
                    maxLines = 1,
                )
                Spacer(Modifier.height(10.dp))
                SignalBars(state.isPlaying, Modifier.fillMaxWidth().height(36.dp))
                Spacer(Modifier.height(10.dp))
                SeekBar(state.positionMs, state.durationMs, onSeek)
            }
        }

        // Transport controls
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlKey(Icons.Rounded.Shuffle, if (state.shuffle) "Shuffle on" else "Shuffle off", lit = state.shuffle, onClick = onToggleShuffle)
                ControlKey(Icons.Rounded.SkipPrevious, "Previous", onClick = onPrevious)
                ControlKey(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (state.isPlaying) "Pause" else "Play",
                    big = true,
                    filled = true,
                    onClick = onTogglePlay,
                )
                ControlKey(Icons.Rounded.SkipNext, "Next", onClick = onNext)
                ControlKey(
                    if (state.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    when (state.repeat) {
                        RepeatMode.OFF -> "Repeat off"
                        RepeatMode.ALL -> "Repeat all"
                        RepeatMode.ONE -> "Repeat one"
                    },
                    lit = state.repeat != RepeatMode.OFF,
                    onClick = onCycleRepeat,
                )
            }
        }

        item {
            CrossfadeControl(crossfadeMs, onCrossfadeSeconds, Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        }

        val upNext = state.upNext
        if (upNext.isNotEmpty()) {
            item { TacLabel("Up next", Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp), count = upNext.size) }
            itemsIndexed(upNext.take(100), key = { i, s -> "q${state.index + 1 + i}:${s.id}" }) { i, s ->
                val queueIndex = state.index + 1 + i
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onJumpTo(queueIndex) }
                        .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("%02d".format(i + 1), style = MaterialTheme.typography.labelMedium, color = Tac.GreenDim)
                    Spacer(Modifier.size(10.dp))
                    AlbumArt(s, Modifier.size(36.dp), sizePx = 108, shape = MaterialTheme.shapes.extraSmall)
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.artist, style = MaterialTheme.typography.bodySmall, color = Tac.GreenMid, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { onRemoveFromQueue(queueIndex) }) {
                        Icon(Icons.Rounded.Close, "Remove from queue", tint = Tac.GreenDim)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** A square, chamfered control key. */
@Composable
private fun ControlKey(
    icon: ImageVector,
    description: String,
    lit: Boolean = false,
    big: Boolean = false,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val size: Dp = if (big) 68.dp else 48.dp
    Box(
        Modifier
            .size(size)
            .border(1.dp, if (filled || lit) Tac.Green else Tac.GreenDim, TacButtonShape)
            .background(if (filled) Tac.Green else Tac.Black, TacButtonShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            description,
            tint = when {
                filled -> Tac.Black
                lit -> Tac.Green
                else -> Tac.GreenMid
            },
            modifier = Modifier.size(if (big) 36.dp else 24.dp),
        )
    }
}

@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shown = dragging ?: fraction
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        SegmentMeter(shown, Modifier.fillMaxWidth().height(10.dp))
        // Invisible slider on top so the meter can be dragged.
        Slider(
            value = shown,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * durationMs).toLong()) }
                dragging = null
            },
            enabled = durationMs > 0,
            colors = SliderDefaults.colors(
                thumbColor = Tac.Green,
                activeTrackColor = androidx.compose.ui.graphics.Color.Transparent,
                inactiveTrackColor = androidx.compose.ui.graphics.Color.Transparent,
                disabledThumbColor = androidx.compose.ui.graphics.Color.Transparent,
                disabledActiveTrackColor = androidx.compose.ui.graphics.Color.Transparent,
                disabledInactiveTrackColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
        )
    }
    Row(Modifier.fillMaxWidth()) {
        Text("T+" + formatTime((shown * durationMs).toLong()), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.weight(1f))
        Text(formatTime(durationMs), style = MaterialTheme.typography.labelMedium, color = Tac.GreenMid)
    }
}

/** 0–12 s crossfade; shown on the player so it's easy to tweak while listening. */
@Composable
fun CrossfadeControl(crossfadeMs: Int, onSeconds: (Int) -> Unit, modifier: Modifier = Modifier) {
    val max = Settings.MAX_CROSSFADE_MS / 1000
    var local by remember(crossfadeMs) { mutableStateOf(crossfadeMs / 1000f) }
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CROSSFADE", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(
                if (local.toInt() == 0) "[ OFF ]" else "[ %02d s ]".format(local.toInt()),
                style = MaterialTheme.typography.labelLarge,
                color = if (local.toInt() == 0) Tac.GreenMid else Tac.Amber,
            )
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onSeconds(local.toInt()) },
            valueRange = 0f..max.toFloat(),
            steps = max - 1,
            colors = SliderDefaults.colors(
                thumbColor = Tac.Green,
                activeTrackColor = Tac.Green,
                inactiveTrackColor = Tac.GreenFaint,
                activeTickColor = Tac.Black,
                inactiveTickColor = Tac.GreenDim,
            ),
        )
    }
}
