package com.mixtape.player.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class PlaylistSource(val label: String) {
    LOCAL("Playlist"),
    SPOTIFY("Spotify"),
    YOUTUBE("YouTube"),
    SOUNDCLOUD("SoundCloud"),
}

/**
 * One track in a playlist. Local playlists point at a song id; imported likes only
 * know title/artist and get matched against the library each time they're shown,
 * so newly added files fill in automatically.
 */
data class PlaylistEntry(
    val songId: Long? = null,
    val title: String,
    val artist: String,
    val durationMs: Long = 0,
    val externalId: String? = null,
    val externalUrl: String? = null,
    val downloadable: Boolean = false,
)

data class Playlist(
    val id: String,
    val name: String,
    val source: PlaylistSource,
    val entries: List<PlaylistEntry>,
    val updatedAt: Long,
)

class PlaylistStore(context: Context) {

    private val file = File(context.filesDir, "playlists.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    private val _playlists = MutableStateFlow(load())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    fun get(id: String): Playlist? = _playlists.value.firstOrNull { it.id == id }

    fun create(name: String, songs: List<Song> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        mutate { it + Playlist(id, name.trim().ifEmpty { "New playlist" }, PlaylistSource.LOCAL, songs.map(::entryFor), now()) }
        return id
    }

    fun rename(id: String, name: String) = update(id) { it.copy(name = name.trim().ifEmpty { it.name }) }

    fun delete(id: String) = mutate { list -> list.filterNot { it.id == id } }

    fun addSongs(id: String, songs: List<Song>) = update(id) { it.copy(entries = it.entries + songs.map(::entryFor)) }

    fun removeAt(id: String, position: Int) = update(id) { p ->
        p.copy(entries = p.entries.filterIndexed { i, _ -> i != position })
    }

    /** Replaces the previous import from the same service. */
    fun upsertImported(source: PlaylistSource, name: String, entries: List<PlaylistEntry>) {
        val id = "import-${source.name.lowercase()}"
        mutate { list ->
            val updated = Playlist(id, name, source, entries, now())
            if (list.any { it.id == id }) list.map { if (it.id == id) updated else it } else list + updated
        }
    }

    private fun update(id: String, change: (Playlist) -> Playlist) = mutate { list ->
        list.map { if (it.id == id) change(it).copy(updatedAt = now()) else it }
    }

    private fun mutate(change: (List<Playlist>) -> List<Playlist>) {
        val next = synchronized(this) {
            change(_playlists.value).also { _playlists.value = it }
        }
        scope.launch { writeLock.withLock { save(next) } }
    }

    private fun entryFor(song: Song) = PlaylistEntry(
        songId = song.id, title = song.title, artist = song.artist, durationMs = song.durationMs
    )

    private fun now() = System.currentTimeMillis()

    // ---- JSON persistence -------------------------------------------------------------

    private fun load(): List<Playlist> = try {
        if (!file.exists()) emptyList() else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val entries = o.getJSONArray("entries")
                Playlist(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    source = runCatching { PlaylistSource.valueOf(o.getString("source")) }
                        .getOrDefault(PlaylistSource.LOCAL),
                    entries = (0 until entries.length()).map { j -> entries.getJSONObject(j).toEntry() },
                    updatedAt = o.optLong("updatedAt"),
                )
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Couldn't read playlists", e)
        emptyList()
    }

    private fun save(list: List<Playlist>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("source", p.source.name)
                put("updatedAt", p.updatedAt)
                put("entries", JSONArray().apply { p.entries.forEach { put(it.toJson()) } })
            })
        }
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(arr.toString())
            tmp.delete()
        }
    }

    private fun PlaylistEntry.toJson() = JSONObject().apply {
        songId?.let { put("songId", it) }
        put("title", title)
        put("artist", artist)
        put("durationMs", durationMs)
        externalId?.let { put("externalId", it) }
        externalUrl?.let { put("externalUrl", it) }
        put("downloadable", downloadable)
    }

    private fun JSONObject.toEntry() = PlaylistEntry(
        songId = if (has("songId")) getLong("songId") else null,
        title = optString("title"),
        artist = optString("artist"),
        durationMs = optLong("durationMs"),
        externalId = if (has("externalId")) getString("externalId") else null,
        externalUrl = if (has("externalUrl")) getString("externalUrl") else null,
        downloadable = optBoolean("downloadable"),
    )

    companion object {
        private const val TAG = "PlaylistStore"
    }
}
