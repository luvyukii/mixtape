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
import com.mixtape.player.data.LookupMatch
import com.mixtape.player.data.MusicBrainz
import com.mixtape.player.data.TagWriter
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
    val globe = settings.globe
    fun setGlobe(on: Boolean) = settings.setGlobe(on)
    val bootAnim = settings.bootAnim
    fun setBootAnim(on: Boolean) = settings.setBootAnim(on)
    val scanlines = settings.scanlines
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
    fun saveSongInfo(
        songs: List<Song>,
        edit: SongEdit,
        writeToFile: Boolean,
        renumber: Boolean = false,
        artwork: LookupMatch? = null,
    ) {
        if (songs.isEmpty()) return
        if (artwork != null) {
            // Fetch the chosen cover first, keep a copy in the app, then save everything together.
            runTask("Fetching artwork…") {
                val bytes = MusicBrainz.cover(artwork, 500) ?: MusicBrainz.cover(artwork, 250)
                val path = bytes?.let { b ->
                    withContext(Dispatchers.IO) {
                        val dir = java.io.File(app.filesDir, "art").apply { mkdirs() }
                        java.io.File(dir, "${songs.first().id}_${System.currentTimeMillis()}.jpg")
                            .apply { writeBytes(b) }.absolutePath
                    }
                }
                if (path == null) say("No artwork found for that match; saved the rest")
                saveSongInfo(songs, if (path != null) edit.copy(artwork = path) else edit, writeToFile, renumber, null)
            }
            return
        }
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
