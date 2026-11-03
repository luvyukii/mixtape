package com.mixtape.player.ui.screens

import com.mixtape.player.ui.components.TacButton
import com.mixtape.player.ui.components.TacOutlinedButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mixtape.player.data.Album
import com.mixtape.player.data.Library
import com.mixtape.player.data.Song
import com.mixtape.player.data.SongGroup
import com.mixtape.player.ui.Detail
import com.mixtape.player.ui.components.AlbumArt
import com.mixtape.player.ui.components.SongActions
import com.mixtape.player.ui.components.SongRow
import com.mixtape.player.ui.components.TacSearchField
import com.mixtape.player.ui.components.TacTabsRow
import com.mixtape.player.ui.components.PixelReveal
import com.mixtape.player.ui.components.EmptyArt
import com.mixtape.player.ui.components.LibraryHud
import com.mixtape.player.ui.components.cornerBrackets
import com.mixtape.player.ui.components.entrance
import com.mixtape.player.ui.components.rememberEntrance
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.ui.unit.sp
import com.mixtape.player.ui.theme.Tac
import androidx.compose.runtime.SideEffect

private enum class LibraryTab(val label: String) { Songs("Songs"), Albums("Albums"), Artists("Artists"), Genres("Genres") }
private enum class SongSort(val label: String) { Title("Title"), Artist("Artist"), Recent("Recently added"), Album("Album") }

@Composable
fun LibraryScreen(
    library: Library,
    scanning: Boolean,
    currentSongId: Long?,
    actions: SongActions,
    onOpen: (Detail) -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onAddFiles: () -> Unit,
    onRescan: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TacSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "search songs, artists, albums",
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp),
        )
        if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth().height(1.dp), color = Tac.Bright, trackColor = Tac.GreenFaint)

        when {
            library.songs.isEmpty() && !scanning -> EmptyLibrary(onAddFiles, onRescan)
            query.isNotBlank() -> SearchResults(library, query.trim(), currentSongId, actions, onOpen, onPlay)
            else -> {
                LibraryHud(library)
                TacTabsRow(
                    labels = LibraryTab.entries.map { it.label },
                    selected = tab,
                    onSelect = { tab = it },
                    modifier = Modifier.padding(top = 4.dp),
                    fill = true,
                )
                PixelReveal(tab, Modifier.weight(1f)) {
                when (LibraryTab.entries[tab.coerceIn(0, LibraryTab.entries.lastIndex)]) {
                    LibraryTab.Songs -> SongsTab(library.songs, currentSongId, actions, onPlay, onShuffle)
                    LibraryTab.Albums -> AlbumsGrid(library.albums) { onOpen(Detail.AlbumD(it.id)) }
                    LibraryTab.Artists -> GroupList(library.artists, circleArt = true) { onOpen(Detail.ArtistD(it.key)) }
                    LibraryTab.Genres -> GroupList(library.genres) { onOpen(Detail.GenreD(it.key)) }
                }
                }
            }
        }
    }
}

@Composable
private fun SongsTab(
    songs: List<Song>,
    currentSongId: Long?,
    actions: SongActions,
    onPlay: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
) {
    var sort by rememberSaveable { mutableStateOf(SongSort.Title) }
    var sortMenu by remember { mutableStateOf(false) }
    val sorted = remember(songs, sort) {
        when (sort) {
            SongSort.Title -> songs
            SongSort.Artist -> songs.sortedWith(compareBy<Song>({ it.primaryArtist.lowercase() }, { it.album.lowercase() }, { it.track }))
            SongSort.Recent -> songs.sortedByDescending { it.dateAdded }
            SongSort.Album -> songs.sortedWith(compareBy<Song>({ it.album.isBlank() }, { it.album.lowercase() }, { it.track }))
        }
    }
    SideEffect { actions.selection.pool = sorted }
    val enter = rememberEntrance(sort)
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TacOutlinedButton(onClick = { onShuffle(songs) }) {
                    Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Shuffle all ${songs.size}")
                }
                Spacer(Modifier.weight(1f))
                Box {
                    Text(
                        "SORT · ${sort.label.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Tac.GreenMid,
                        modifier = Modifier
                            .clickable { sortMenu = true }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SongSort.entries.forEach { s ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        s.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (s == sort) Tac.Bright else Tac.Green,
                                    )
                                },
                                onClick = { sort = s; sortMenu = false },
                            )
                        }
                    }
                }
            }
        }
        itemsIndexed(sorted, key = { _, s -> s.id }) { i, song ->
            SongRow(
                song, song.id == currentSongId, actions,
                onClick = { onPlay(sorted, i) },
                modifier = Modifier.entrance(enter, i),
            )
        }
    }
}

@Composable
private fun AlbumsGrid(albums: List<Album>, onClick: (Album) -> Unit) {
    val enter = rememberEntrance(Unit)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        gridItemsIndexed(albums, key = { _, a -> a.id }) { i, album ->
            Column(Modifier.entrance(enter, i).clickable { onClick(album) }) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .cornerBrackets(length = 8.dp)
                        .padding(4.dp)
                ) {
                    AlbumArt(album.songs.firstOrNull(), Modifier.fillMaxSize(), sizePx = 400, shape = MaterialTheme.shapes.extraSmall)
                }
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        album.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Tac.Green,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text("%03d".format(i + 1), style = MaterialTheme.typography.labelSmall, color = Tac.GreenDim)
                }
                Text(
                    album.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun GroupList(
    groups: List<SongGroup>,
    circleArt: Boolean = false,
    folderIcon: Boolean = false,
    onClick: (SongGroup) -> Unit,
) {
    val enter = rememberEntrance(Unit)
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(groups, key = { _, g -> g.key }) { i, g ->
            ListItem(
                modifier = Modifier.entrance(enter, i).clickable { onClick(g) },
                headlineContent = { Text(g.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(g.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = {
                    if (folderIcon) {
                        Icon(Icons.Rounded.Folder, null, Modifier.size(28.dp), tint = Tac.GreenMid)
                    } else {
                        AlbumArt(
                            g.songs.firstOrNull(),
                            Modifier.size(48.dp),
                            sizePx = 144,
                            shape = MaterialTheme.shapes.extraSmall,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun SearchResults(
    library: Library,
    query: String,
    currentSongId: Long?,
    actions: SongActions,
    onOpen: (Detail) -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
) {
    val q = query.lowercase()
    val songs = remember(library, q) {
        library.songs.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q)
        }
    }
    val artists = remember(library, q) { library.artists.filter { it.name.lowercase().contains(q) }.take(8) }
    val albums = remember(library, q) {
        library.albums.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }.take(8)
    }

    SideEffect { actions.selection.pool = songs }
    LazyColumn(Modifier.fillMaxSize()) {
        if (songs.isEmpty() && artists.isEmpty() && albums.isEmpty()) {
            item {
                Text(
                    "Nothing matches “$query”",
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (artists.isNotEmpty()) {
            item { SectionHeader("Artists") }
            items(artists, key = { "artist:${it.key}" }) { g ->
                ListItem(
                    modifier = Modifier.clickable { onOpen(Detail.ArtistD(g.key)) },
                    headlineContent = { Text(g.name) },
                    supportingContent = { Text(g.subtitle) },
                    leadingContent = { AlbumArt(g.songs.firstOrNull(), Modifier.size(48.dp), sizePx = 144, shape = MaterialTheme.shapes.extraSmall) },
                )
            }
        }
        if (albums.isNotEmpty()) {
            item { SectionHeader("Albums") }
            items(albums, key = { "album:${it.id}" }) { a ->
                ListItem(
                    modifier = Modifier.clickable { onOpen(Detail.AlbumD(a.id)) },
                    headlineContent = { Text(a.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(a.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { AlbumArt(a.songs.firstOrNull(), Modifier.size(48.dp), sizePx = 144) },
                )
            }
        }
        if (songs.isNotEmpty()) {
            item { SectionHeader("Songs") }
            itemsIndexed(songs.take(200), key = { _, s -> "song:${s.id}" }) { i, song ->
                SongRow(song, song.id == currentSongId, actions, onClick = { onPlay(songs, i) })
            }
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    com.mixtape.player.ui.components.TacLabel(
        text,
        Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun EmptyLibrary(onAddFiles: () -> Unit, onRescan: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyArt(Modifier.fillMaxWidth().height(200.dp))
        Spacer(Modifier.height(20.dp))
        Text("NO SIGNAL", style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 4.sp), color = Tac.Green)
        Spacer(Modifier.height(8.dp))
        Text(
            "Songs saved anywhere on your phone show up here automatically. You can also copy files in.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        TacButton(onClick = onAddFiles) { Text("ADD MUSIC FILES") }
        Spacer(Modifier.height(8.dp))
        TacOutlinedButton(onClick = onRescan) { Text("SCAN AGAIN") }
    }
}
