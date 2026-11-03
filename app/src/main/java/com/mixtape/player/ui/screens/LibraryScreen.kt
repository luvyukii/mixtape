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

private enum class LibraryTab(val label: String) { Songs("Songs"), Albums("Albums"), Artists("Artists"), Genres("Genres"), Folders("Folders") }
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
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search songs, artists, albums") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Clear, "Clear search") }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
        )
        if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())

        when {
            library.songs.isEmpty() && !scanning -> EmptyLibrary(onAddFiles, onRescan)
            query.isNotBlank() -> SearchResults(library, query.trim(), currentSongId, actions, onOpen, onPlay)
            else -> {
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 16.dp) {
                    LibraryTab.entries.forEachIndexed { i, t ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t.label) })
                    }
                }
                when (LibraryTab.entries[tab]) {
                    LibraryTab.Songs -> SongsTab(library.songs, currentSongId, actions, onPlay, onShuffle)
                    LibraryTab.Albums -> AlbumsGrid(library.albums) { onOpen(Detail.AlbumD(it.id)) }
                    LibraryTab.Artists -> GroupList(library.artists, circleArt = true) { onOpen(Detail.ArtistD(it.key)) }
                    LibraryTab.Genres -> GroupList(library.genres) { onOpen(Detail.GenreD(it.key)) }
                    LibraryTab.Folders -> GroupList(library.folders, folderIcon = true) { onOpen(Detail.FolderD(it.key)) }
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
            SongSort.Album -> songs.sortedWith(compareBy<Song>({ it.album.lowercase() }, { it.track }))
        }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TacOutlinedButton(onClick = { onShuffle(songs) }) {
                    Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Shuffle all ${songs.size}")
                }
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, "Sort") }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SongSort.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(if (s == sort) "✓  ${s.label}" else "    ${s.label}") },
                                onClick = { sort = s; sortMenu = false },
                            )
                        }
                    }
                }
            }
        }
        itemsIndexed(sorted, key = { _, s -> s.id }) { i, song ->
            SongRow(song, song.id == currentSongId, actions, onClick = { onPlay(sorted, i) })
        }
    }
}

@Composable
private fun AlbumsGrid(albums: List<Album>, onClick: (Album) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(albums, key = { it.id }) { album ->
            Column(Modifier.clickable { onClick(album) }) {
                AlbumArt(album.songs.firstOrNull(), Modifier.fillMaxWidth().aspectRatio(1f), sizePx = 400)
                Text(
                    album.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
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
    LazyColumn(Modifier.fillMaxSize()) {
        items(groups, key = { it.key }) { g ->
            ListItem(
                modifier = Modifier.clickable { onClick(g) },
                headlineContent = { Text(g.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(g.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = {
                    if (folderIcon) {
                        Icon(Icons.Rounded.Folder, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    } else {
                        AlbumArt(
                            g.songs.firstOrNull(),
                            Modifier.size(48.dp),
                            sizePx = 144,
                            shape = if (circleArt) CircleShape else MaterialTheme.shapes.small,
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
                    leadingContent = { AlbumArt(g.songs.firstOrNull(), Modifier.size(48.dp), sizePx = 144, shape = CircleShape) },
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
        Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("No music found yet", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Songs saved anywhere on your phone show up here automatically. You can also copy files in.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        TacButton(onClick = onAddFiles) { Text("Add music files") }
        Spacer(Modifier.height(8.dp))
        TacOutlinedButton(onClick = onRescan) { Text("Scan again") }
    }
}
