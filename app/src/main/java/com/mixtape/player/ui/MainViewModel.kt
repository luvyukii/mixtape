package com.mixtape.player.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mixtape.player.MixtapeApp
import com.mixtape.player.data.PlaylistEntry
import com.mixtape.player.data.PlaylistSource
import com.mixtape.player.data.Song
import com.mixtape.player.data.SongEdit
import com.mixtape.player.data.TagWriter
import com.mixtape.player.data.TrackListParser
import com.mixtape.player.data.UnsupportedFormatException
import android.app.RecoverableSecurityException
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.mixtape.player.data.TrackMatcher
import com.mixtape.player.integrations.ExternalTrack
import com.mixtape.player.integrations.HttpError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A snackbar message, optionally with a button like "Undo". */
class UiMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MixtapeApp
    val engine = app.engine
    val settings = app.settings

    val library = app.library.library
    val scanning = app.library.scanning
    val playlists = app.playlists.playlists
    val playback = engine.state
    val accounts = settings.accounts
    val crossfadeMs = settings.crossfadeMs
    val smartShuffle = settings.smartShuffle
    val boostDb = settings.boostDb
    val hiRes = settings.hiRes
    fun setBoostDb(db: Int) = settings.setBoostDb(db)
    fun setHiRes(on: Boolean) {
        settings.setHiRes(on)
        say(if (on) "Hi-res output turns on next time NEXUS starts" else "Hi-res output turns off next time NEXUS starts")
    }
    fun moveInQueue(from: Int, to: Int) = engine.moveInQueue(from, to)
    val tintArt = settings.tintArt
    val scanlines = settings.scanlines
    fun setTintArt(on: Boolean) = settings.setTintArt(on)
    fun setScanlines(on: Boolean) = settings.setScanlines(on)

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    /** Text of the long-running task in progress, or null. */
    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    val hiddenIds = settings.hiddenIds

    /** Songs waiting on Android's delete confirmation; survives screen rotation. */
    var pendingDelete: List<Song> = emptyList()

    fun showMessage(text: String) = say(text)

    // ---- editing song info -----------------------------------------------------------------

    /** Android's "Allow NEXUS to modify this file?" prompt, for the Activity to show. */
    private val _writeRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val writeRequests: SharedFlow<IntentSender> = _writeRequests.asSharedFlow()

    private var pendingTagWrite: List<Pair<Song, SongEdit>>? = null
    /** Edits for songs that were playing; written into the file once they stop. */
    private val deferredWrites = LinkedHashMap<Long, Pair<Song, SongEdit>>()

    init {
        viewModelScope.launch {
            engine.state.map { it.current?.id }.distinctUntilChanged().collect { flushDeferredWrites() }
        }
    }

    /**
     * Saves edited info. It shows in NEXUS right away; with [writeToFile] it's also
     * written into the files so other apps see it.
     */
    fun saveSongInfo(songs: List<Song>, edit: SongEdit, writeToFile: Boolean, renumber: Boolean = false) {
        if (songs.isEmpty()) return
        // With renumbering, each song gets its own track number in the order they were picked.
        val items = songs.mapIndexed { i, s ->
            s to if (renumber) edit.copy(track = (s.track / 1000) * 1000 + i + 1) else edit
        }
        app.metadata.applyEach(items.associate { (s, e) -> s.id to e })
        if (writeToFile) writeTags(items, askPermission = true)
        else say(if (songs.size == 1) "Saved" else "Saved ${songs.size} songs")
    }

    fun onWritePermissionResult(granted: Boolean) {
        val items = pendingTagWrite ?: return
        pendingTagWrite = null
        if (granted) writeTags(items, askPermission = false)
        else say("Saved in NEXUS. The file itself wasn't changed.")
    }

    private fun writeTags(items: List<Pair<Song, SongEdit>>, askPermission: Boolean) {
        viewModelScope.launch {
            val playingId = engine.state.value.current?.id
            val needPermission = ArrayList<Pair<Song, SongEdit>>()
            val showProgress = items.size > 3 && _busy.value == null
            if (showProgress) _busy.value = "Writing tags…"
            var recoverable: RecoverableSecurityException? = null
            var written = 0
            var deferred = 0
            var unsupported = 0
            var failed = 0
            withContext(Dispatchers.IO) {
                for ((n, item) in items.withIndex()) {
                    val (song, edit) = item
                    if (showProgress && n % 5 == 0) _busy.value = "Writing tags ${n + 1}/${items.size}"
                    try {
                        if (song.id == playingId) {
                            // Rewriting a file mid-playback would glitch it; check access now, write later.
                            app.contentResolver.openFileDescriptor(song.uri, "rw")?.close()
                            synchronized(deferredWrites) { deferredWrites[song.id] = song to edit }
                            deferred++
                        } else {
                            TagWriter.write(app, song, edit)
                            written++
                        }
                    } catch (e: RecoverableSecurityException) {
                        needPermission += item
                        if (recoverable == null) recoverable = e
                    } catch (e: SecurityException) {
                        needPermission += item
                    } catch (e: UnsupportedFormatException) {
                        unsupported++
                    } catch (e: Exception) {
                        android.util.Log.w("MainViewModel", "Tag write failed", e)
                        failed++
                    }
                }
            }
            if (showProgress) _busy.value = null
            if (needPermission.isNotEmpty() && askPermission) {
                val sender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    MediaStore.createWriteRequest(app.contentResolver, needPermission.map { it.first.uri }).intentSender
                } else recoverable?.userAction?.actionIntent?.intentSender
                if (sender != null) {
                    pendingTagWrite = needPermission
                    _writeRequests.tryEmit(sender)
                    return@launch
                }
            }
            failed += needPermission.size
            if (written > 0) app.library.refresh()
            say(
                buildString {
                    append(if (items.size == 1) "Saved" else "Saved ${items.size} songs")
                    if (deferred > 0) append(". The playing song's file updates when it finishes")
                    if (unsupported > 0) append(". $unsupported file type(s) can't hold tags, so those changes are in NEXUS only")
                    if (failed > 0) append(". $failed file(s) couldn't be changed; edits are in NEXUS only")
                }
            )
        }
    }

    private fun flushDeferredWrites() {
        val playingId = engine.state.value.current?.id
        val ready = synchronized(deferredWrites) {
            deferredWrites.values.filter { it.first.id != playingId }.also { list ->
                list.forEach { deferredWrites.remove(it.first.id) }
            }
        }
        if (ready.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            ready.forEach { (song, edit) -> runCatching { TagWriter.write(app, song, edit) } }
            app.library.refresh()
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _hasPermission.value = granted
        if (granted) app.library.start()
    }

    // ---- playback ---------------------------------------------------------------------------

    fun play(songs: List<Song>, index: Int) = engine.playQueue(songs, index, shuffled = engine.state.value.shuffle)
    fun shuffle(songs: List<Song>) = engine.playQueue(songs, startIndex = null, shuffled = true)
    fun togglePlay() = engine.togglePlay()
    fun next() = engine.next()
    fun previous() = engine.previous()
    fun seekTo(ms: Long) = engine.seekTo(ms)
    fun toggleShuffle() = engine.setShuffle(!engine.state.value.shuffle)
    fun cycleRepeat() = engine.cycleRepeat()
    fun jumpTo(queueIndex: Int) = engine.skipTo(queueIndex)
    fun removeFromQueue(queueIndex: Int) = engine.removeFromQueue(queueIndex)
    fun playNext(song: Song) {
        engine.playNext(listOf(song))
        say("“${song.title}” plays next")
    }
    fun addToQueue(song: Song) {
        engine.addToQueue(listOf(song))
        say("Added “${song.title}” to the queue")
    }

    fun setCrossfadeSeconds(seconds: Int) = settings.setCrossfadeMs(seconds * 1000)
    fun setSmartShuffle(on: Boolean) = settings.setSmartShuffle(on)

    // ---- library & playlists -----------------------------------------------------------------

    fun rescan() = app.library.refresh()

    fun importFiles(uris: List<Uri>) = runTask("Adding ${uris.size} file(s)…") {
        val n = app.importer.importFiles(uris)
        app.library.refresh()
        say(if (n == 0) "Those files are already in your library" else "Added $n file(s) to Music/Nexus")
    }

    fun importFolder(tree: Uri) = runTask("Copying folder…") {
        val n = app.importer.importFolder(tree) { copied -> _busy.value = "Copying folder… $copied" }
        app.library.refresh()
        say(if (n == 0) "No new music found in that folder" else "Added $n file(s) to Music/Nexus")
    }

    fun createPlaylist(name: String, songs: List<Song> = emptyList()) {
        app.playlists.create(name, songs)
        say("Created “$name”")
    }

    fun addToPlaylist(playlistId: String, songs: List<Song>) {
        app.playlists.addSongs(playlistId, songs)
        say("Added to “${app.playlists.get(playlistId)?.name}”")
    }

    fun hideSongs(songs: List<Song>) {
        if (songs.isEmpty()) return
        val ids = songs.map { it.id }
        settings.hide(ids)
        _messages.tryEmit(UiMessage("Hid ${songs.size} songs", "Undo") { settings.unhide(ids) })
    }

    fun playNextAll(songs: List<Song>) {
        engine.playNext(songs)
        say("${songs.size} songs play next")
    }

    fun queueAll(songs: List<Song>) {
        engine.addToQueue(songs)
        say("Added ${songs.size} songs to the queue")
    }

    /** Hides a song from the library without touching the file. */
    fun hideSong(song: Song) {
        settings.hide(listOf(song.id))
        _messages.tryEmit(UiMessage("Hid “${song.title}”", "Undo") { settings.unhide(listOf(song.id)) })
    }

    fun unhideAll() {
        val n = settings.hiddenIds.value.size
        settings.unhideAll()
        say(if (n == 1) "1 song is back in your library" else "$n songs are back in your library")
    }

    /** Called after Android confirms the files were deleted. */
    fun onSongsDeleted(songs: List<Song>) {
        val ids = songs.map { it.id }.toSet()
        engine.removeSongs(ids)
        settings.unhide(ids)
        app.library.refresh()
        say(if (songs.size == 1) "Deleted “${songs[0].title}”" else "Deleted ${songs.size} songs")
    }

    fun renamePlaylist(id: String, name: String) = app.playlists.rename(id, name)
    fun deletePlaylist(id: String) = app.playlists.delete(id)
    fun removeFromPlaylist(id: String, position: Int) = app.playlists.removeAt(id, position)

    // ---- streaming services ----------------------------------------------------------------

    fun setSpotifyClientId(v: String) = settings.setSpotifyClientId(v)
    fun setSoundCloudClientId(v: String) = settings.setSoundCloudClientId(v)
    fun setSoundCloudClientSecret(v: String) = settings.setSoundCloudClientSecret(v)
    fun setYouTubeMusicOnly(v: Boolean) = settings.setYouTubeMusicOnly(v)

    /** Returns the sign-in page to open, or null (with a message) if setup is missing. */
    fun spotifySignInUrl(): Uri? = try {
        app.spotify.authorizeUrl()
    } catch (e: Exception) {
        say(e.message ?: "Couldn't start Spotify sign-in"); null
    }

    fun soundCloudSignInUrl(): Uri? = try {
        app.soundCloud.authorizeUrl()
    } catch (e: Exception) {
        say(e.message ?: "Couldn't start SoundCloud sign-in"); null
    }

    fun disconnectSpotify() = app.spotify.signOut()
    fun disconnectSoundCloud() = app.soundCloud.signOut()

    fun handleSignInRedirect(uri: Uri) = when (uri.host) {
        "spotify-callback" -> runTask("Connecting Spotify…") {
            app.spotify.completeSignIn(uri)
            say("Spotify connected")
        }
        "soundcloud-callback" -> runTask("Connecting SoundCloud…") {
            app.soundCloud.completeSignIn(uri)
            say("SoundCloud connected")
        }
        else -> Unit
    }

    fun importSpotifyLikes() = runTask("Importing Spotify likes…") {
        val tracks = app.spotify.likedTracks { n -> _busy.value = "Importing Spotify likes… $n" }
        saveImport(PlaylistSource.SPOTIFY, "Spotify likes", tracks)
    }

    fun importYouTubeLikes(token: suspend () -> String) = runTask("Signing in to Google…") {
        val accessToken = token()
        _busy.value = "Importing YouTube likes…"
        val tracks = try {
            app.youTube.likedVideos(accessToken, accounts.value.youTubeMusicOnly) { n ->
                _busy.value = "Importing YouTube likes… $n"
            }
        } catch (e: HttpError) {
            throw java.io.IOException(
                when {
                    "accessNotConfigured" in e.body || "SERVICE_DISABLED" in e.body ->
                        "YouTube Data API v3 isn't turned on in your Google Cloud project. Enable it, wait a minute, and try again."
                    e.code == 401 || e.code == 403 ->
                        "Google refused access (HTTP ${e.code}). Make sure your account is added as a test user on the OAuth consent screen."
                    else -> "YouTube request failed (HTTP ${e.code})."
                },
                e,
            )
        }
        saveImport(PlaylistSource.YOUTUBE, "YouTube likes", tracks)
    }

    fun importSoundCloudLikes() = runTask("Importing SoundCloud likes…") {
        val tracks = app.soundCloud.likedTracks { n -> _busy.value = "Importing SoundCloud likes… $n" }
        saveImport(PlaylistSource.SOUNDCLOUD, "SoundCloud likes", tracks)
        val downloadable = tracks.count { it.downloadable }
        if (downloadable > 0) say("$downloadable of your SoundCloud likes can be downloaded — open the playlist to save them")
    }

    fun downloadFromSoundCloud(entries: List<PlaylistEntry>) = runTask("Downloading…") {
        val wanted = entries.filter { it.downloadable }
        var ok = 0
        var failed = 0
        wanted.forEachIndexed { i, entry ->
            _busy.value = "Downloading ${i + 1} of ${wanted.size}: ${entry.title}"
            try {
                app.soundCloud.download(entry, app.importer)
                ok++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                if (wanted.size == 1) throw e
            }
        }
        app.library.refresh()
        say(
            when {
                failed == 0 -> "Downloaded $ok track(s) to Music/Nexus/SoundCloud"
                ok == 0 -> "Couldn't download those tracks"
                else -> "Downloaded $ok, $failed failed"
            }
        )
    }

    /** Imports a song list from an exported file (Spotify data export, CSV, text). */
    fun importLikesFile(uri: Uri) = runTask("Reading file…") {
        val resolver = app.contentResolver
        val name = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "Imported list"
        val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: throw java.io.IOException("Couldn't open that file")
        val parsed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            TrackListParser.parseFile(name, bytes)
        }
        saveParsed(parsed)
    }

    /** Remembers a song found while browsing, so you can find or buy it later. */
    fun saveToWishlist(title: String, artist: String, url: String, source: PlaylistSource) {
        app.playlists.appendTo(
            "wishlist", "Wishlist", PlaylistSource.LIST,
            PlaylistEntry(title = title, artist = artist, externalUrl = url.ifBlank { null }),
        )
        say("Saved “$title” to your Wishlist in Playlists")
    }

    /** Imports a pasted list, one "Artist - Title" per line. */
    fun importPastedList(name: String, text: String) {
        val entries = TrackListParser.parseLines(text)
        if (entries.isEmpty()) {
            say("That list is empty")
            return
        }
        saveParsed(TrackListParser.Result(name.ifBlank { "Pasted list" }, PlaylistSource.LIST, entries))
    }

    private fun saveParsed(parsed: TrackListParser.Result) {
        if (parsed.source == PlaylistSource.SPOTIFY) {
            app.playlists.upsertImported(PlaylistSource.SPOTIFY, parsed.name, parsed.entries)
        } else {
            app.playlists.createImported(parsed.name, parsed.source, parsed.entries)
        }
        val index = TrackMatcher.Index(library.value.songs)
        val onDevice = parsed.entries.count { index.find(it.title, it.artist, it.durationMs) != null }
        say("Imported “${parsed.name}”: ${parsed.entries.size} songs, $onDevice already on your phone. It's in Playlists.")
    }

    private fun saveImport(source: PlaylistSource, name: String, tracks: List<ExternalTrack>) {
        app.playlists.upsertImported(source, name, tracks.map { it.toEntry() })
        val index = TrackMatcher.Index(library.value.songs)
        val onDevice = tracks.count { index.find(it.title, it.artist, it.durationMs) != null }
        say("Imported ${tracks.size} ${source.label} likes — $onDevice already on your phone")
    }

    // ---- helpers ------------------------------------------------------------------------------

    private fun runTask(label: String, block: suspend () -> Unit) {
        if (_busy.value != null) {
            say("Wait for “${_busy.value}” to finish")
            return
        }
        _busy.value = label
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpError) {
                say(
                    when (e.code) {
                        401 -> "Your sign-in expired. Disconnect and connect again."
                        403 -> "Access denied. Check your developer app settings (see README)."
                        429 -> "Too many requests. Try again in a minute."
                        else -> "Request failed (HTTP ${e.code})"
                    }
                )
            } catch (e: Exception) {
                say(e.message ?: "Something went wrong")
            } finally {
                _busy.value = null
            }
        }
    }

    private fun say(message: String) {
        _messages.tryEmit(UiMessage(message))
    }
}
