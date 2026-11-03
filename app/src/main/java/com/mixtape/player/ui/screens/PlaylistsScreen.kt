package com.mixtape.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mixtape.player.data.Playlist
import com.mixtape.player.data.PlaylistSource

@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    onOpen: (Playlist) -> Unit,
    onCreate: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item { SectionHeader("Playlists") }
            if (playlists.isEmpty()) {
                item {
                    Text(
                        "No playlists yet. Make one here, or import your likes from Spotify, YouTube or SoundCloud in Settings.",
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(playlists.sortedWith(compareBy<Playlist>({ it.source != PlaylistSource.LOCAL }, { it.name.lowercase() })), key = { it.id }) { p ->
                ListItem(
                    modifier = Modifier.clickable { onOpen(p) },
                    headlineContent = { Text(p.name) },
                    supportingContent = {
                        Text(if (p.source == PlaylistSource.LOCAL) "${p.entries.size} songs" else "${p.entries.size} likes · ${p.source.label}")
                    },
                    leadingContent = {
                        Icon(iconFor(p.source), null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    },
                )
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("New playlist") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }
    if (creating) {
        NameDialog(
            title = "New playlist",
            initial = "",
            onDismiss = { creating = false },
            onConfirm = { creating = false; onCreate(it) },
        )
    }
}

fun iconFor(source: PlaylistSource): ImageVector = when (source) {
    PlaylistSource.LOCAL -> Icons.AutoMirrored.Rounded.QueueMusic
    PlaylistSource.SPOTIFY -> Icons.Rounded.Favorite
    PlaylistSource.YOUTUBE -> Icons.Rounded.SmartDisplay
    PlaylistSource.SOUNDCLOUD -> Icons.Rounded.Cloud
    PlaylistSource.LIST -> Icons.Rounded.Description
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, placeholder = { Text("Name") })
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Picks a playlist to add songs to (or makes a new one). */
@Composable
fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPick: (Playlist) -> Unit,
    onCreate: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog("New playlist", "", onDismiss = { creating = false }, onConfirm = { onCreate(it) })
        return
    }
    val local = playlists.filter { it.source == PlaylistSource.LOCAL }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            LazyColumn {
                item {
                    ListItem(
                        modifier = Modifier.clickable { creating = true },
                        headlineContent = { Text("New playlist…") },
                        leadingContent = { Icon(Icons.Rounded.Add, null) },
                    )
                }
                items(local, key = { it.id }) { p ->
                    ListItem(
                        modifier = Modifier.clickable { onPick(p) },
                        headlineContent = { Text(p.name) },
                        supportingContent = { Text("${p.entries.size} songs") },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
