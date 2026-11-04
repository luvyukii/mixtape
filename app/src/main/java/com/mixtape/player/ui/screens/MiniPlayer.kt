package com.mixtape.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mixtape.player.playback.PlaybackState
import com.mixtape.player.ui.components.AlbumArt
import com.mixtape.player.ui.components.formatTime
import com.mixtape.player.ui.components.swipeVertical
import com.mixtape.player.ui.theme.Tac

@Composable
fun MiniPlayer(state: PlaybackState, onOpen: () -> Unit, onTogglePlay: () -> Unit, onNext: () -> Unit) {
    val song = state.current ?: return
    val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    Column(
        Modifier
            .fillMaxWidth()
            .background(Tac.Panel)
            .swipeVertical(down = false, threshold = 30.dp, onSwipe = onOpen)
            .clickable(onClick = onOpen)
    ) {
        // Progress as a hairline along the top edge
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Tac.GreenFaint)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(1.dp)
                    .background(Tac.Bright)
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlbumArt(song, Modifier.size(38.dp), sizePx = 114, shape = MaterialTheme.shapes.extraSmall)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tac.Green,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${song.artist}  ·  ${formatTime(state.positionMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Tac.GreenMid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onTogglePlay) {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (state.isPlaying) "Pause" else "Play",
                    tint = Tac.Green,
                )
            }
            IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, "Next", tint = Tac.GreenMid) }
        }
    }
}
