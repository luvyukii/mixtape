package com.mixtape.player.data

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import org.json.JSONObject
import java.io.File
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
            _allSongs.value = fixFolderAlbums(query())
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
            MediaStore.Audio.Media.DATE_MODIFIED,
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
            val modified = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
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
                    modified = c.getLong(modified),
                )
            }
        }
        return songs
    }

    // ---- folder names posing as albums -------------------------------------------------

    /**
     * When a file has no album tag, Android fills in its folder's name as the album. For songs
     * whose album equals their folder name, read the file's own tags; if it really has no album,
     * file it under "Unknown album" instead. Results are cached so each file is checked once.
     */
    private fun fixFolderAlbums(songs: List<Song>): List<Song> {
        val cache = loadTagCache()
        var dirty = false
        val out = songs.map { s ->
            val folderName = s.folder.trimEnd('/').substringAfterLast('/')
            if (folderName.isEmpty() || !s.album.equals(folderName, ignoreCase = true)) return@map s
            val key = "${s.id}:${s.modified}"
            val tag = cache[key] ?: readAlbumTag(s)?.also {
                cache[key] = it
                dirty = true
            } ?: return@map s // couldn't read the file; leave it as it was
            if (tag.isBlank()) {
                s.copy(album = LibraryOrganizer.UNKNOWN_ALBUM, albumId = UNKNOWN_ALBUM_ID)
            } else s
        }
        if (dirty) saveTagCache(cache)
        return out
    }

    /** The album tag stored in the file ("" if none), or null if the file couldn't be read. */
    private fun readAlbumTag(s: Song): String? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(appContext, s.uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim().orEmpty()
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    private val tagCacheFile get() = File(appContext.filesDir, "album_tags.json")

    private fun loadTagCache(): MutableMap<String, String> = try {
        val o = JSONObject(tagCacheFile.readText())
        o.keys().asSequence().associateWith { o.getString(it) }.toMutableMap()
    } catch (e: Exception) {
        HashMap()
    }

    private fun saveTagCache(map: Map<String, String>) {
        runCatching { tagCacheFile.writeText(JSONObject(map).toString()) }
    }

    companion object {
        /** All songs with no album tag are grouped together. */
        const val UNKNOWN_ALBUM_ID = -2L
        private const val TAG = "LibraryRepository"
        private const val MIN_DURATION_MS = 15_000
    }
}
