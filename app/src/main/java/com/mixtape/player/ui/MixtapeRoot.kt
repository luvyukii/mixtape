package com.mixtape.player.ui

import com.mixtape.player.ui.components.TacButton
import com.mixtape.player.ui.components.TacOutlinedButton
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import com.mixtape.player.ui.components.TacHeader
import com.mixtape.player.ui.components.TacNavRail
import com.mixtape.player.ui.components.TacSnackbar
import com.mixtape.player.ui.components.SelectionState
import com.mixtape.player.ui.components.MenuItem
import com.mixtape.player.ui.screens.NowPlayingActions
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import com.mixtape.player.ui.components.TacNavBar
import com.mixtape.player.ui.components.TacTab
import com.mixtape.player.ui.components.scanlines
import com.mixtape.player.ui.theme.LocalTacOptions
import com.mixtape.player.ui.theme.Tac
import com.mixtape.player.ui.theme.TacOptions
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

private enum class Tab(val label: String) { Library("Library"), Playlists("Lists"), Browse("Browse"), Settings("Config") }

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
    val tintArt by vm.tintArt.collectAsStateWithLifecycle()
    val scanlines by vm.scanlines.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.Library) }
    val stack = remember { mutableStateListOf<Detail>() }
    var nowPlayingOpen by rememberSaveable { mutableStateOf(false) }
    var addToPlaylist by remember { mutableStateOf<List<Song>?>(null) }
    var confirmDelete by remember { mutableStateOf<List<Song>?>(null) }
    val selection = remember { SelectionState() }
    val boostDb by vm.boostDb.collectAsStateWithLifecycle()
    val hiRes by vm.hiRes.collectAsStateWithLifecycle()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
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

    BackHandler(enabled = selection.active || nowPlayingOpen || stack.isNotEmpty()) {
        when {
            nowPlayingOpen -> nowPlayingOpen = false
            selection.active -> selection.clear()
            else -> stack.removeAt(stack.lastIndex)
        }
    }

    val songActions = remember(vm) {
        SongActions(
            playNext = vm::playNext,
            addToQueue = vm::addToQueue,
            addToPlaylist = { addToPlaylist = listOf(it) },
            hide = vm::hideSong,
            edit = { editing = listOf(it) },
            delete = { confirmDelete = listOf(it) },
            selection = selection,
        )
    }
    val play: (List<Song>, Int) -> Unit = { songs, i -> vm.play(songs, i); nowPlayingOpen = false }
    val shuffle: (List<Song>) -> Unit = { songs -> vm.shuffle(songs) }
    val back: () -> Unit = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    val headerStatus = when {
        busy != null -> busy!!
        playback.current != null && playback.isPlaying ->
            "▶ PLAYING ${playback.index + 1}/${playback.queue.size}" + if (playback.crossfading) " · XFADE" else ""
        playback.current != null -> "‖ PAUSED ${playback.index + 1}/${playback.queue.size}"
        scanning -> "SCANNING STORAGE…"
        else -> "${library.songs.size} TRACKS INDEXED"
    }
    val selectTab: (Int) -> Unit = { i ->
        tab = Tab.entries[i]
        stack.clear()
        selection.clear()
    }
    val navTabs = remember {
        listOf(
            TacTab(Tab.Library.label, Icons.Rounded.LibraryMusic),
            TacTab(Tab.Playlists.label, Icons.AutoMirrored.Rounded.QueueMusic),
            TacTab(Tab.Browse.label, Icons.Rounded.Explore),
            TacTab(Tab.Settings.label, Icons.Rounded.Settings),
        )
    }

    CompositionLocalProvider(LocalTacOptions provides TacOptions(tintArt = tintArt, scanlines = scanlines)) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Tac.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .scanlines(scanlines)
    ) {
        Scaffold(
            containerColor = Tac.Black,
            snackbarHost = { SnackbarHost(snackbar) { TacSnackbar(it) } },
            topBar = {
                if (selection.active) {
                    SelectionBar(
                        count = selection.count,
                        modifier = Modifier.statusBarsPadding(),
                        onClose = selection::clear,
                        onSelectAll = { selection.selectAll() },
                        onEdit = { editing = selection.songs },
                        onAddToPlaylist = { addToPlaylist = selection.songs },
                        onPlayNext = { vm.playNextAll(selection.songs); selection.clear() },
                        onQueue = { vm.queueAll(selection.songs); selection.clear() },
                        onHide = { vm.hideSongs(selection.songs); selection.clear() },
                        onDelete = { confirmDelete = selection.songs },
                    )
                } else {
                    TacHeader(headerStatus, Modifier.statusBarsPadding(), alert = busy != null)
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                Column(Modifier.navigationBarsPadding()) {
                    MiniPlayer(playback, onOpen = { nowPlayingOpen = true }, onTogglePlay = vm::togglePlay, onNext = vm::next)
                    if (!landscape) TacNavBar(navTabs, selected = tab.ordinal, onSelect = selectTab)
                }
            },
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                if (busy != null) {
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth().height(2.dp),
                        color = Tac.Amber,
                        trackColor = Tac.GreenFaint,
                    )
                }
                Row(Modifier.weight(1f)) {
                if (landscape) TacNavRail(navTabs, selected = tab.ordinal, onSelect = selectTab)
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
                                library, crossfadeMs, smartShuffle, tintArt, scanlines, accounts, hiddenIds.size, busy != null,
                                boostDb, hiRes,
                                SettingsActions(
                                    onBoostDb = vm::setBoostDb,
                                    onHiRes = vm::setHiRes,
                                    onImportFile = platform.pickLikesFile,
                                    onPasteList = vm::importPastedList,
                                    onUnhideAll = vm::unhideAll,
                                    onCrossfadeSeconds = vm::setCrossfadeSeconds,
                                    onSmartShuffle = vm::setSmartShuffle,
                                    onTintArt = vm::setTintArt,
                                    onScanlines = vm::setScanlines,
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
        }

        AnimatedVisibility(
            visible = nowPlayingOpen && playback.current != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            Surface(Modifier.fillMaxSize().scanlines(scanlines), color = Tac.Black) {
                NowPlayingScreen(
                    state = playback,
                    analyzer = vm.engine.analyzer,
                    crossfadeMs = crossfadeMs,
                    boostDb = boostDb,
                    actions = remember(vm) {
                        NowPlayingActions(
                            onClose = { nowPlayingOpen = false },
                            onTogglePlay = vm::togglePlay,
                            onNext = vm::next,
                            onPrevious = vm::previous,
                            onSeek = vm::seekTo,
                            onToggleShuffle = vm::toggleShuffle,
                            onCycleRepeat = vm::cycleRepeat,
                            onCrossfadeSeconds = vm::setCrossfadeSeconds,
                            onBoostDb = vm::setBoostDb,
                            onJumpTo = vm::jumpTo,
                            onRemoveFromQueue = vm::removeFromQueue,
                            onMoveInQueue = vm::moveInQueue,
                        )
                    },
                )
            }
        }
    }

    }
    editing?.let { songs ->
        // Edit using the songs as Android sees them, so "changed" compares against the file.
        EditInfoDialog(
            songs = songs,
            onDismiss = { editing = null },
            onSave = { edit, toFile, renumber ->
                editing = null
                selection.clear()
                vm.saveSongInfo(songs, edit, toFile, renumber)
            },
        )
    }

    longMessage?.let { text ->
        AlertDialog(
            onDismissRequest = { longMessage = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { longMessage = null }) { Text("OK") } },
        )
    }

    confirmDelete?.let { songs ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = Tac.Panel,
            title = {
                Text(
                    if (songs.size == 1) "Delete “${songs[0].title}”?" else "Delete ${songs.size} songs?",
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    "This permanently deletes the music files from your phone. To just stop seeing them, use Hide instead.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tac.GreenMid,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    selection.clear()
                    platform.deleteSongs(songs)
                }) { Text("DELETE", color = Tac.Amber) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("CANCEL", color = Tac.GreenMid) } },
        )
    }

    addToPlaylist?.let { songs ->
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { addToPlaylist = null },
            onPick = { p -> vm.addToPlaylist(p.id, songs); addToPlaylist = null; selection.clear() },
            onCreate = { name -> vm.createPlaylist(name, songs); addToPlaylist = null; selection.clear() },
        )
    }
}

/** Replaces the header while songs are selected. */
@Composable
private fun SelectionBar(
    count: Int,
    modifier: Modifier,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onEdit: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onQueue: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().background(Tac.Panel)) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Cancel selection", tint = Tac.GreenMid) }
            Text(
                "$count SELECTED",
                style = MaterialTheme.typography.labelLarge,
                color = Tac.Bright,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSelectAll) { Icon(Icons.Rounded.SelectAll, "Select all", tint = Tac.Green) }
            IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "Edit info", tint = Tac.Green) }
            IconButton(onClick = onAddToPlaylist) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist", tint = Tac.Green) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More actions", tint = Tac.Green) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    MenuItem("Play next", Icons.Rounded.SkipNext) { menu = false; onPlayNext() }
                    MenuItem("Add to queue", Icons.AutoMirrored.Rounded.QueueMusic) { menu = false; onQueue() }
                    MenuItem("Hide from library", Icons.Rounded.VisibilityOff) { menu = false; onHide() }
                    MenuItem("Delete from phone", Icons.Rounded.DeleteForever) { menu = false; onDelete() }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Tac.Bright.copy(alpha = 0.35f)))
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
            Text("Let NEXUS see your music", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "NEXUS needs access to audio files on your phone to build your library. Nothing leaves your device.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Row { TacButton(onClick = onRequest) { Text("Allow access") } }
        }
    }
}
