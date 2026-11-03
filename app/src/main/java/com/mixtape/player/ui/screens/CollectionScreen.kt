package com.mixtape.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mixtape.player.data.Song
import com.mixtape.player.ui.components.AlbumArt
import com.mixtape.player.ui.components.SongActions
import com.mixtape.player.ui.components.SongRow

/** An album, artist, genre or folder: header with Play/Shuffle, then its songs. */
@Composable
fun CollectionScreen(
    title: String,
    subtitle: String,
    songs: List<Song>,
    currentSongId: Long?,
    actions: SongActions,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    showTrackArtist: Boolean = true,
    headerActions: @Composable () -> Unit = {},
    extraItems: LazyListScope.() -> Unit = {},
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            CollectionHeader(title, subtitle, songs.firstOrNull(), onBack, headerActions)
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = { onPlay(songs, 0) }, enabled = songs.isNotEmpty()) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Play")
                }
                FilledTonalButton(onClick = { onShuffle(songs) }, enabled = songs.isNotEmpty()) {
                    Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Shuffle")
                }
            }
        }
        itemsIndexed(songs, key = { i, s -> "$i:${s.id}" }) { i, song ->
            SongRow(
                song, song.id == currentSongId, actions,
                onClick = { onPlay(songs, i) },
                supporting = if (showTrackArtist) "${song.artist} · ${song.album}" else song.album,
            )
        }
        extraItems()
    }
}

@Composable
fun CollectionHeader(
    title: String,
    subtitle: String,
    artSong: Song?,
    onBack: () -> Unit,
    headerActions: @Composable () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Spacer(Modifier.weight(1f))
            headerActions()
        }
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlbumArt(artSong, Modifier.size(112.dp), sizePx = 336)
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
