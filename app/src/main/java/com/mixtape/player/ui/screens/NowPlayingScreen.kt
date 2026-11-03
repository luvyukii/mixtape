package com.mixtape.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mixtape.player.data.Settings
import com.mixtape.player.playback.PlaybackState
import com.mixtape.player.playback.RepeatMode
import com.mixtape.player.ui.components.AlbumArt
import com.mixtape.player.ui.components.formatTime

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
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Close player") }
                Text(
                    if (state.crossfading) "Crossfading…" else "Now playing",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(48.dp))
            }
        }
        item {
            AlbumArt(
                song,
                Modifier
                    .padding(horizontal = 32.dp, vertical = 8.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f),
                sizePx = 1000,
                shape = RoundedCornerShape(20.dp),
            )
        }
        item {
            Column(Modifier.padding(horizontal = 24.dp).fillMaxWidth()) {
                Text(
                    song?.title ?: "Nothing playing",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song?.let { "${it.artist} · ${it.album}" } ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                SeekBar(state.positionMs, state.durationMs, onSeek)
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        if (state.shuffle) "Shuffle on" else "Shuffle off",
                        tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(36.dp))
                }
                FilledIconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(),
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (state.isPlaying) "Pause" else "Play",
                        Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(36.dp))
                }
                IconButton(onClick = onCycleRepeat) {
                    Icon(
                        if (state.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        when (state.repeat) {
                            RepeatMode.OFF -> "Repeat off"
                            RepeatMode.ALL -> "Repeat all"
                            RepeatMode.ONE -> "Repeat one"
                        },
                        tint = if (state.repeat != RepeatMode.OFF) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            CrossfadeControl(crossfadeMs, onCrossfadeSeconds, Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        }

        val upNext = state.upNext
        if (upNext.isNotEmpty()) {
            item { Column(Modifier.fillMaxWidth()) { SectionHeader("Up next · ${upNext.size}") } }
            itemsIndexed(upNext.take(100), key = { i, s -> "q${state.index + 1 + i}:${s.id}" }) { i, s ->
                val queueIndex = state.index + 1 + i
                ListItem(
                    modifier = Modifier.clickable { onJumpTo(queueIndex) },
                    headlineContent = { Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { AlbumArt(s, Modifier.size(40.dp), sizePx = 120) },
                    trailingContent = {
                        IconButton(onClick = { onRemoveFromQueue(queueIndex) }) {
                            Icon(Icons.Rounded.Close, "Remove from queue")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shown = dragging ?: fraction
    Slider(
        value = shown,
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            dragging?.let { onSeek((it * durationMs).toLong()) }
            dragging = null
        },
        enabled = durationMs > 0,
    )
    Row(Modifier.fillMaxWidth()) {
        Text(formatTime((shown * durationMs).toLong()), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.weight(1f))
        Text(formatTime(durationMs), style = MaterialTheme.typography.labelMedium)
    }
}

/** 0–12 s crossfade; shown on the player so it's easy to tweak while listening. */
@Composable
fun CrossfadeControl(crossfadeMs: Int, onSeconds: (Int) -> Unit, modifier: Modifier = Modifier) {
    val max = Settings.MAX_CROSSFADE_MS / 1000
    var local by remember(crossfadeMs) { mutableStateOf(crossfadeMs / 1000f) }
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Crossfade", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                if (local.toInt() == 0) "Off" else "${local.toInt()} s",
                style = MaterialTheme.typography.labelLarge,
                color = if (local.toInt() == 0) Color.Unspecified else MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onSeconds(local.toInt()) },
            valueRange = 0f..max.toFloat(),
            steps = max - 1,
        )
    }
}
