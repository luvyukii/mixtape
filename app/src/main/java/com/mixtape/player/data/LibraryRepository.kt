package com.mixtape.player.data

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reads every music file Android knows about (MediaStore) and keeps the
 * organized library up to date as files are added or removed.
 */
class LibraryRepository(
    context: Context,
    private val settings: Settings,
    private val metadata: MetadataStore,
) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scanLock = Mutex()

    private val _library = MutableStateFlow(Library.Empty)
    val library: StateFlow<Library> = _library.asStateFlow()

    /** Every song found, including hidden ones. */
    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    val allSongs: StateFlow<List<Song>> = _allSongs.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private var started = false
    private var pendingRescan: Job? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            // MediaStore fires many changes in a burst; wait for it to settle.
            pendingRescan?.cancel()
            pendingRescan = scope.launch {
                delay(1500)
                scan()
            }
        }
    }

    init {
        // Re-sort the library whenever songs are hidden or unhidden.
        scope.launch { settings.hiddenIds.collect { publish() } }
        // ...and whenever song info is edited.
        scope.launch { metadata.edits.collect { publish() } }
    }

    private fun publish() {
        val hidden = settings.hiddenIds.value
        val edits = metadata.edits.value
        _library.value = LibraryOrganizer.organize(
            _allSongs.value.filter { it.id !in hidden }.map { song -> edits[song.id]?.let { song.withEdit(it) } ?: song }
        )
    }

    /** Call once the audio permission is granted. */
    fun start() {
        if (!started) {
            started = true
            appContext.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer
            )
        }
        refresh()
    }

    fun refresh() {
        scope.launch { scan() }
    }

    private suspend fun scan() = scanLock.withLock {
        _scanning.value = true
        try {
            _allSongs.value = query()
            publish()
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to read music yet", e)
        } finally {
            _scanning.value = false
        }
    }

    private fun query(): List<Song> {
        val columns = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )
        val hasGenre = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        if (hasGenre) columns += MediaStore.Audio.Media.GENRE

        // Skip ringtones/notification sounds and anything shorter than 15 s.
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val args = arrayOf(MIN_DURATION_MS.toString())

        val songs = ArrayList<Song>()
        appContext.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            columns.toTypedArray(), selection, args, null
        )?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val albumArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST)
            val duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val track = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val year = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val added = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val path = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            val name = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val genre = if (hasGenre) c.getColumnIndex(MediaStore.Audio.Media.GENRE) else -1

            while (c.moveToNext()) {
                val songId = c.getLong(id)
                val fileName = c.getString(name).orEmpty()
                val artistName = LibraryOrganizer.clean(c.getString(artist), LibraryOrganizer.UNKNOWN_ARTIST)
                songs += Song(
                    id = songId,
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId),
                    title = LibraryOrganizer.clean(c.getString(title), fileName.substringBeforeLast('.')),
                    artist = artistName,
                    primaryArtist = LibraryOrganizer.primaryArtist(artistName),
                    album = LibraryOrganizer.clean(c.getString(album), LibraryOrganizer.UNKNOWN_ALBUM),
                    albumId = c.getLong(albumId),
                    albumArtist = LibraryOrganizer.clean(c.getString(albumArtist), ""),
                    genre = if (genre >= 0) {
                        LibraryOrganizer.clean(c.getString(genre), LibraryOrganizer.UNKNOWN_GENRE)
                    } else LibraryOrganizer.UNKNOWN_GENRE,
                    durationMs = c.getLong(duration),
                    track = c.getInt(track),
                    year = c.getInt(year),
                    dateAdded = c.getLong(added),
                    folder = c.getString(path).orEmpty(),
                )
            }
        }
        return songs
    }

    companion object {
        private const val TAG = "LibraryRepository"
        private const val MIN_DURATION_MS = 15_000
    }
}
