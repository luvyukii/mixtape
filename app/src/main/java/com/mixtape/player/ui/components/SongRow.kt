package com.mixtape.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mixtape.player.data.Song

/** What the ⋮ menu on a song can do; shared by every list in the app. */
class SongActions(
    val playNext: (Song) -> Unit,
    val addToQueue: (Song) -> Unit,
    val addToPlaylist: (Song) -> Unit,
    val hide: (Song) -> Unit,
    val edit: (Song) -> Unit,
    val delete: (Song) -> Unit,
)

@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    actions: SongActions,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String = "${song.artist} · ${song.album}",
    onRemove: (() -> Unit)? = null,
    removeLabel: String = "Remove from playlist",
) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        colors = if (isCurrent) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else ListItemDefaults.colors(),
        headlineContent = {
            Text(
                if (isCurrent) "▶ ${song.title}" else song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isCurrent) FontWeight.SemiBold else null,
            )
        },
        supportingContent = { Text(supporting, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { AlbumArt(song, Modifier.size(48.dp), sizePx = 144) },
        trailingContent = { SongMenu(song, actions, onRemove, removeLabel) },
    )
}

@Composable
private fun SongMenu(song: Song, actions: SongActions, onRemove: (() -> Unit)?, removeLabel: String) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More options for ${song.title}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Play next") },
                leadingIcon = { Icon(Icons.Rounded.SkipNext, null) },
                onClick = { open = false; actions.playNext(song) },
            )
            DropdownMenuItem(
                text = { Text("Add to queue") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
                onClick = { open = false; actions.addToQueue(song) },
            )
            DropdownMenuItem(
                text = { Text("Add to playlist…") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                onClick = { open = false; actions.addToPlaylist(song) },
            )
            DropdownMenuItem(
                text = { Text("Edit info…") },
                leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                onClick = { open = false; actions.edit(song) },
            )
            DropdownMenuItem(
                text = { Text("Hide from library") },
                leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null) },
                onClick = { open = false; actions.hide(song) },
            )
            DropdownMenuItem(
                text = { Text("Delete from phone…") },
                leadingIcon = { Icon(Icons.Rounded.DeleteForever, null) },
                onClick = { open = false; actions.delete(song) },
            )
            if (onRemove != null) {
                DropdownMenuItem(
                    text = { Text(removeLabel) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                    onClick = { open = false; onRemove() },
                )
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
