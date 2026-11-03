package com.mixtape.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mixtape.player.data.Library
import com.mixtape.player.data.Playlist
import com.mixtape.player.data.PlaylistEntry
import com.mixtape.player.data.PlaylistSource
import com.mixtape.player.data.Song
import com.mixtape.player.data.TrackMatcher
import com.mixtape.player.ui.components.SongActions
import com.mixtape.player.ui.components.SongRow

private data class Resolved(val position: Int, val entry: PlaylistEntry, val song: Song?)

@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    library: Library,
    matcher: TrackMatcher.Index,
    currentSongId: Long?,
    actions: SongActions,
    busy: Boolean,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onRemove: (Int) -> Unit,
    onOpenUrl: (String) -> Unit,
    onDownload: (List<PlaylistEntry>) -> Unit,
) {
    val resolved = remember(playlist, library, matcher) {
        playlist.entries.mapIndexed { i, e ->
            val song = e.songId?.let { library.byId[it] } ?: matcher.find(e.title, e.artist, e.durationMs)
            Resolved(i, e, song)
        }
    }
    val playable = remember(resolved) { resolved.mapNotNull { it.song } }
    val missingDownloadable = remember(resolved) {
        resolved.filter { it.song == null && it.entry.downloadable }.map { it.entry }
    }
    val isLocal = playlist.source == PlaylistSource.LOCAL
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val subtitle = if (isLocal) "${playlist.entries.size} songs"
    else "${playable.size} of ${playlist.entries.size} on this phone · ${playlist.source.label}"

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            CollectionHeader(playlist.name, subtitle, playable.firstOrNull(), onBack) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Playlist options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (isLocal) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                onClick = { menu = false; renaming = true },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete playlist") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                }
            }
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = { onPlay(playable, 0) }, enabled = playable.isNotEmpty()) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Play")
                }
                FilledTonalButton(onClick = { onShuffle(playable) }, enabled = playable.isNotEmpty()) {
                    Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Shuffle")
                }
            }
            if (playlist.source == PlaylistSource.SOUNDCLOUD && missingDownloadable.isNotEmpty()) {
                OutlinedButton(
                    onClick = { onDownload(missingDownloadable) },
                    enabled = !busy,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Download ${missingDownloadable.size} the artists allow")
                }
            }
            if (!isLocal && playable.size < playlist.entries.size) {
                Text(
                    "Greyed-out tracks aren't on this phone yet. Add the files and they'll match automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        itemsIndexed(resolved, key = { _, r -> r.position }) { _, r ->
            val song = r.song
            if (song != null) {
                SongRow(
                    song, song.id == currentSongId, actions,
                    onClick = { onPlay(playable, playable.indexOf(song).coerceAtLeast(0)) },
                    onRemove = if (isLocal) ({ onRemove(r.position) }) else null,
                )
            } else {
                MissingRow(r.entry, playlist.source, busy, onOpenUrl, onDownload, onRemove = if (isLocal) ({ onRemove(r.position) }) else null)
            }
        }
    }

    if (renaming) {
        NameDialog("Rename playlist", playlist.name, onDismiss = { renaming = false }, onConfirm = { renaming = false; onRename(it) })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${playlist.name}”?") },
            text = { Text("Your music files stay on the phone; only the playlist is removed.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MissingRow(
    entry: PlaylistEntry,
    source: PlaylistSource,
    busy: Boolean,
    onOpenUrl: (String) -> Unit,
    onDownload: (List<PlaylistEntry>) -> Unit,
    onRemove: (() -> Unit)?,
) {
    ListItem(
        modifier = Modifier.alpha(0.55f),
        headlineContent = { Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(entry.artist.ifBlank { "Unknown artist" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Row {
                if (source == PlaylistSource.SOUNDCLOUD && entry.downloadable) {
                    IconButton(onClick = { onDownload(listOf(entry)) }, enabled = !busy) {
                        Icon(Icons.Rounded.Download, "Download ${entry.title}")
                    }
                }
                entry.externalUrl?.let { url ->
                    IconButton(onClick = { onOpenUrl(url) }) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open in ${source.label}")
                    }
                }
                if (onRemove != null) {
                    IconButton(onClick = onRemove) { Icon(Icons.Rounded.Delete, "Remove") }
                }
            }
        },
    )
}
