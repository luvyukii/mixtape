package com.mixtape.player.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

/** Info you've edited for a song. A null field means "keep what the file says". */
data class SongEdit(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val year: Int? = null,
    val track: Int? = null,
) {
    fun mergedWith(newer: SongEdit) = SongEdit(
        title = newer.title ?: title,
        artist = newer.artist ?: artist,
        album = newer.album ?: album,
        albumArtist = newer.albumArtist ?: albumArtist,
        genre = newer.genre ?: genre,
        year = newer.year ?: year,
        track = newer.track ?: track,
    )
}

/**
 * Keeps your edits inside the app, so they show up instantly and work for every file
 * (even ones Android won't let apps change). Edits are also written into the files when possible.
 */
class MetadataStore(context: Context) {

    private val file = File(context.filesDir, "song_edits.json")
    private val _edits = MutableStateFlow(load())
    val edits: StateFlow<Map<Long, SongEdit>> = _edits.asStateFlow()

    @Synchronized
    fun apply(ids: Collection<Long>, edit: SongEdit) {
        val next = _edits.value.toMutableMap()
        ids.forEach { id -> next[id] = (next[id] ?: SongEdit()).mergedWith(edit) }
        _edits.value = next
        save(next)
    }

    /** Applies a different edit to each song, with one save. */
    @Synchronized
    fun applyEach(edits: Map<Long, SongEdit>) {
        val next = _edits.value.toMutableMap()
        edits.forEach { (id, edit) -> next[id] = (next[id] ?: SongEdit()).mergedWith(edit) }
        _edits.value = next
        save(next)
    }

    @Synchronized
    fun forget(ids: Collection<Long>) {
        val next = _edits.value - ids.toSet()
        _edits.value = next
        save(next)
    }

    private fun load(): Map<Long, SongEdit> = try {
        if (!file.exists()) emptyMap() else {
            val o = JSONObject(file.readText())
            o.keys().asSequence().mapNotNull { key ->
                val e = o.getJSONObject(key)
                key.toLongOrNull()?.let {
                    it to SongEdit(
                        title = e.optStringOrNull("title"),
                        artist = e.optStringOrNull("artist"),
                        album = e.optStringOrNull("album"),
                        albumArtist = e.optStringOrNull("albumArtist"),
                        genre = e.optStringOrNull("genre"),
                        year = if (e.has("year")) e.getInt("year") else null,
                        track = if (e.has("track")) e.getInt("track") else null,
                    )
                }
            }.toMap()
        }
    } catch (e: Exception) {
        Log.e("MetadataStore", "Couldn't read edits", e)
        emptyMap()
    }

    private fun save(map: Map<Long, SongEdit>) {
        val o = JSONObject()
        map.forEach { (id, e) ->
            o.put(id.toString(), JSONObject().apply {
                e.title?.let { put("title", it) }
                e.artist?.let { put("artist", it) }
                e.album?.let { put("album", it) }
                e.albumArtist?.let { put("albumArtist", it) }
                e.genre?.let { put("genre", it) }
                e.year?.let { put("year", it) }
                e.track?.let { put("track", it) }
            })
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(file)) file.writeText(o.toString())
    }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (has(name) && !isNull(name)) getString(name) else null
}
