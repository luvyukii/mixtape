package com.mixtape.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mixtape.player.data.Song
import com.mixtape.player.data.TrackMatcher
import com.mixtape.player.ui.components.SongActions
import com.mixtape.player.ui.screens.AddToPlaylistDialog
import com.mixtape.player.ui.screens.CollectionScreen
import com.mixtape.player.ui.screens.EditInfoDialog
import com.mixtape.player.ui.screens.BrowseScreen
import com.mixtape.player.ui.screens.BrowseState
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.IconButton
import com.mixtape.player.ui.screens.LibraryScreen
import com.mixtape.player.ui.screens.MiniPlayer
import com.mixtape.player.ui.screens.NowPlayingScreen
import com.mixtape.player.ui.screens.PlaylistDetailScreen
import com.mixtape.player.ui.screens.PlaylistsScreen
import com.mixtape.player.ui.screens.SettingsActions
import com.mixtape.player.ui.screens.SettingsScreen

/** Screens you can drill into; kept on a simple back stack. */
sealed interface Detail {
    data class AlbumD(val id: Long) : Detail
    data class ArtistD(val key: String) : Detail
    data class GenreD(val key: String) : Detail
    data class FolderD(val key: String) : Detail
    data class PlaylistD(val id: String) : Detail
}

private enum class Tab(val label: String) { Library("Library"), Playlists("Playlists"), Browse("Browse"), Settings("Settings") }

/** Things that need the Activity (pickers, browser, Google sign-in). */
class PlatformActions(
    val openUrl: (String) -> Unit,
    val pickFiles: () -> Unit,
    val pickFolder: () -> Unit,
    val requestPermission: () -> Unit,
    val importYouTube: () -> Unit,
    val deleteSongs: (List<Song>) -> Unit,
    val pickLikesFile: () -> Unit,
)

@Composable
fun MixtapeRoot(vm: MainViewModel, platform: PlatformActions) {
    val hasPermission by vm.hasPermission.collectAsStateWithLifecycle()
    if (!hasPermission) {
        PermissionScreen(platform.requestPermission)
        return
    }

    val library by vm.library.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val crossfadeMs by vm.crossfadeMs.collectAsStateWithLifecycle()
    val smartShuffle by vm.smartShuffle.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val hiddenIds by vm.hiddenIds.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.Library) }
    val stack = remember { mutableStateListOf<Detail>() }
    var nowPlayingOpen by rememberSaveable { mutableStateOf(false) }
    var addToPlaylist by remember { mutableStateOf<List<Song>?>(null) }
    var confirmDelete by remember { mutableStateOf<Song?>(null) }
    var editing by remember { mutableStateOf<List<Song>?>(null) }
    var longMessage by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val browse = remember { BrowseState() }
    val matcher = remember(library) { TrackMatcher.Index(library.songs) }
    val currentId = playback.current?.id

    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            // Long explanations (like setup errors) get a dialog so they aren't cut off.
            if (msg.actionLabel == null && msg.text.length > 110) {
                longMessage = msg.text
                return@collect
            }
            val result = snackbar.showSnackbar(
                message = msg.text,
                actionLabel = msg.actionLabel,
                withDismissAction = msg.actionLabel == null && msg.text.length > 60,
                duration = if (msg.actionLabel != null || msg.text.length > 60) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) msg.action?.invoke()
        }
    }

    BackHandler(enabled = nowPlayingOpen || stack.isNotEmpty()) {
        if (nowPlayingOpen) nowPlayingOpen = false else stack.removeAt(stack.lastIndex)
    }

    val songActions = remember(vm) {
        SongActions(
            playNext = vm::playNext,
            addToQueue = vm::addToQueue,
            addToPlaylist = { addToPlaylist = listOf(it) },
            hide = vm::hideSong,
            edit = { editing = listOf(it) },
            delete = { confirmDelete = it },
        )
    }
    val play: (List<Song>, Int) -> Unit = { songs, i -> vm.play(songs, i); nowPlayingOpen = false }
    val shuffle: (List<Song>) -> Unit = { songs -> vm.shuffle(songs) }
    val back: () -> Unit = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column {
                    MiniPlayer(playback, onOpen = { nowPlayingOpen = true }, onTogglePlay = vm::togglePlay, onNext = vm::next)
                    NavigationBar {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = {
                                    tab = t
                                    stack.clear()
                                },
                                icon = {
                                    Icon(
                                        when (t) {
                                            Tab.Library -> Icons.Rounded.LibraryMusic
                                            Tab.Playlists -> Icons.AutoMirrored.Rounded.QueueMusic
                                            Tab.Browse -> Icons.Rounded.Explore
                                            Tab.Settings -> Icons.Rounded.Settings
                                        },
                                        contentDescription = null,
                                    )
                                },
                                label = { Text(t.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                busy?.let { label ->
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(label, style = MaterialTheme.typography.bodySmall)
                            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    when (val detail = stack.lastOrNull()) {
                        null -> when (tab) {
                            Tab.Library -> LibraryScreen(
                                library, scanning, currentId, songActions,
                                onOpen = { stack.add(it) },
                                onPlay = play,
                                onShuffle = shuffle,
                                onAddFiles = platform.pickFiles,
                                onRescan = vm::rescan,
                            )
                            Tab.Playlists -> PlaylistsScreen(
                                playlists,
                                onOpen = { stack.add(Detail.PlaylistD(it.id)) },
                                onCreate = { vm.createPlaylist(it) },
                            )
                            Tab.Browse -> BrowseScreen(
                                state = browse,
                                onMessage = vm::showMessage,
                                onSaveToWishlist = vm::saveToWishlist,
                            )
                            Tab.Settings -> SettingsScreen(
                                library, crossfadeMs, smartShuffle, accounts, hiddenIds.size, busy != null,
                                SettingsActions(
                                    onImportFile = platform.pickLikesFile,
                                    onPasteList = vm::importPastedList,
                                    onUnhideAll = vm::unhideAll,
                                    onCrossfadeSeconds = vm::setCrossfadeSeconds,
                                    onSmartShuffle = vm::setSmartShuffle,
                                    onAddFiles = platform.pickFiles,
                                    onAddFolder = platform.pickFolder,
                                    onRescan = vm::rescan,
                                    onSpotifyClientId = vm::setSpotifyClientId,
                                    onSpotifyConnect = { vm.spotifySignInUrl()?.let { platform.openUrl(it.toString()) } },
                                    onSpotifyDisconnect = vm::disconnectSpotify,
                                    onSpotifyImport = vm::importSpotifyLikes,
                                    onYouTubeMusicOnly = vm::setYouTubeMusicOnly,
                                    onYouTubeImport = platform.importYouTube,
                                    onSoundCloudClientId = vm::setSoundCloudClientId,
                                    onSoundCloudSecret = vm::setSoundCloudClientSecret,
                                    onSoundCloudConnect = { vm.soundCloudSignInUrl()?.let { platform.openUrl(it.toString()) } },
                                    onSoundCloudDisconnect = vm::disconnectSoundCloud,
                                    onSoundCloudImport = vm::importSoundCloudLikes,
                                ),
                            )
                        }

                        is Detail.AlbumD -> library.albumsById[detail.id]?.let { album ->
                            CollectionScreen(
                                title = album.title,
                                subtitle = listOfNotNull(album.artist, album.year.takeIf { it > 0 }?.toString(), "${album.songs.size} songs")
                                    .joinToString(" · "),
                                songs = album.songs, currentSongId = currentId, actions = songActions,
                                onBack = back, onPlay = play, onShuffle = shuffle, showTrackArtist = false,
                                headerActions = {
                                    IconButton(onClick = { editing = album.songs }) {
                                        Icon(Icons.Rounded.Edit, "Edit album info")
                                    }
                                },
                            )
                        } ?: Gone(back)

                        is Detail.ArtistD -> library.artist(detail.key)?.let { g ->
                            CollectionScreen(g.name, g.subtitle, g.songs, currentId, songActions, back, play, shuffle)
                        } ?: Gone(back)

                        is Detail.GenreD -> library.genre(detail.key)?.let { g ->
                            CollectionScreen(g.name, g.subtitle, g.songs, currentId, songActions, back, play, shuffle)
                        } ?: Gone(back)

                        is Detail.FolderD -> library.folder(detail.key)?.let { g ->
                            CollectionScreen(g.name, g.subtitle, g.songs, currentId, songActions, back, play, shuffle)
                        } ?: Gone(back)

                        is Detail.PlaylistD -> playlists.firstOrNull { it.id == detail.id }?.let { p ->
                            PlaylistDetailScreen(
                                playlist = p,
                                library = library,
                                matcher = matcher,
                                currentSongId = currentId,
                                actions = songActions,
                                busy = busy != null,
                                onBack = back,
                                onPlay = play,
                                onShuffle = shuffle,
                                onRename = { vm.renamePlaylist(p.id, it) },
                                onDelete = { back(); vm.deletePlaylist(p.id) },
                                onRemove = { vm.removeFromPlaylist(p.id, it) },
                                onOpenUrl = platform.openUrl,
                                onDownload = vm::downloadFromSoundCloud,
                            )
                        } ?: Gone(back)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = nowPlayingOpen && playback.current != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            Surface(Modifier.fillMaxSize()) {
                NowPlayingScreen(
                    state = playback,
                    crossfadeMs = crossfadeMs,
                    onClose = { nowPlayingOpen = false },
                    onTogglePlay = vm::togglePlay,
                    onNext = vm::next,
                    onPrevious = vm::previous,
                    onSeek = vm::seekTo,
                    onToggleShuffle = vm::toggleShuffle,
                    onCycleRepeat = vm::cycleRepeat,
                    onCrossfadeSeconds = vm::setCrossfadeSeconds,
                    onJumpTo = vm::jumpTo,
                    onRemoveFromQueue = vm::removeFromQueue,
                )
            }
        }
    }

    editing?.let { songs ->
        // Edit using the songs as Android sees them, so "changed" compares against the file.
        EditInfoDialog(
            songs = songs,
            onDismiss = { editing = null },
            onSave = { edit, toFile -> editing = null; vm.saveSongInfo(songs, edit, toFile) },
        )
    }

    longMessage?.let { text ->
        AlertDialog(
            onDismissRequest = { longMessage = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { longMessage = null }) { Text("OK") } },
        )
    }

    confirmDelete?.let { song ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete “${song.title}”?") },
            text = { Text("This permanently deletes the music file from your phone. To just stop seeing it, use “Hide from library” instead.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; platform.deleteSongs(listOf(song)) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }

    addToPlaylist?.let { songs ->
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { addToPlaylist = null },
            onPick = { p -> vm.addToPlaylist(p.id, songs); addToPlaylist = null },
            onCreate = { name -> vm.createPlaylist(name, songs); addToPlaylist = null },
        )
    }
}

/** Shown for a moment if an album/playlist disappeared (e.g. files deleted). */
@Composable
private fun Gone(onBack: () -> Unit) {
    LaunchedEffect(Unit) { onBack() }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Let Mixtape see your music", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "Mixtape needs access to audio files on your phone to build your library. Nothing leaves your device.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Row { Button(onClick = onRequest) { Text("Allow access") } }
        }
    }
}
