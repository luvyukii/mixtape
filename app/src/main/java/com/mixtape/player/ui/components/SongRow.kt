package com.mixtape.player.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mixtape.player.data.Song
import com.mixtape.player.ui.theme.Tac

/**
 * Songs picked for a bulk action. Long-press a row to start; tap rows to add or remove.
 * Keeps the order songs were picked in (used when renumbering tracks).
 */
@Stable
class SelectionState {
    private val ids = mutableStateMapOf<Long, Song>()
    private val order = mutableStateListOf<Song>()

    /** The songs on the screen being looked at, for "Select all". */
    var pool: List<Song> = emptyList()

    val active: Boolean get() = order.isNotEmpty()
    val count: Int get() = order.size
    val songs: List<Song> get() = order.toList()

    fun isSelected(song: Song) = ids.containsKey(song.id)

    fun toggle(song: Song) {
        if (ids.remove(song.id) != null) order.removeAll { it.id == song.id }
        else {
            ids[song.id] = song
            order.add(song)
        }
    }

    fun selectAll(songs: List<Song> = pool) {
        songs.forEach { s ->
            if (!ids.containsKey(s.id)) {
                ids[s.id] = s
                order.add(s)
            }
        }
    }

    fun clear() {
        ids.clear()
        order.clear()
    }
}

/** What the ⋮ menu on a song can do; shared by every list in the app. */
class SongActions(
    val playNext: (Song) -> Unit,
    val addToQueue: (Song) -> Unit,
    val addToPlaylist: (Song) -> Unit,
    val hide: (Song) -> Unit,
    val edit: (Song) -> Unit,
    val delete: (Song) -> Unit,
    val selection: SelectionState = SelectionState(),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    actions: SongActions,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String = song.artistAndAlbum,
    onRemove: (() -> Unit)? = null,
    removeLabel: String = "Remove from playlist",
) {
    val sel = actions.selection
    val selecting = sel.active
    val selected = sel.isSelected(song)
    Row(
        modifier
            .fillMaxWidth()
            .background(if (selected) Tac.PanelHi else Color.Transparent)
            .drawBehind {
                // A lit edge marks the song that's playing.
                if (isCurrent) drawRect(Tac.Bright, Offset.Zero, Size(2.dp.toPx(), size.height))
            }
            .combinedClickable(
                onClick = { if (selecting) sel.toggle(song) else onClick() },
                onLongClick = { sel.toggle(song) },
            )
            .padding(start = 16.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp)) {
            AlbumArt(song, Modifier.fillMaxSize(), sizePx = 126, shape = MaterialTheme.shapes.extraSmall)
            if (selecting) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xB3000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(18.dp)
                            .border(1.dp, if (selected) Tac.Bright else Tac.GreenMid)
                            .background(if (selected) Tac.Bright else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Rounded.Check, null, tint = Tac.Black, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) Tac.Bright else Tac.Green,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = Tac.GreenMid,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            formatTime(song.durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = Tac.GreenDim,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        if (selecting) Spacer(Modifier.width(48.dp)) else SongMenu(song, actions, onRemove, removeLabel)
    }
}

@Composable
private fun SongMenu(song: Song, actions: SongActions, onRemove: (() -> Unit)?, removeLabel: String) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More options for ${song.title}", tint = Tac.GreenDim)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuItem("Play next", Icons.Rounded.SkipNext) { open = false; actions.playNext(song) }
            MenuItem("Add to queue", Icons.AutoMirrored.Rounded.QueueMusic) { open = false; actions.addToQueue(song) }
            MenuItem("Add to playlist", Icons.AutoMirrored.Rounded.PlaylistAdd) { open = false; actions.addToPlaylist(song) }
            MenuItem("Edit info", Icons.Rounded.Edit) { open = false; actions.edit(song) }
            MenuItem("Select", Icons.Rounded.Checklist) { open = false; actions.selection.toggle(song) }
            MenuItem("Hide from library", Icons.Rounded.VisibilityOff) { open = false; actions.hide(song) }
            MenuItem("Delete from phone", Icons.Rounded.DeleteForever) { open = false; actions.delete(song) }
            if (onRemove != null) MenuItem(removeLabel, Icons.Rounded.Delete) { open = false; onRemove() }
        }
    }
}

@Composable
fun MenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyMedium, color = Tac.Green) },
        leadingIcon = { Icon(icon, null, tint = Tac.GreenMid, modifier = Modifier.size(18.dp)) },
        onClick = onClick,
    )
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
