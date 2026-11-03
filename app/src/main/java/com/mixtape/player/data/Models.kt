package com.mixtape.player.data

import android.content.ContentUris
import android.net.Uri

private val ALBUM_ART_BASE: Uri = Uri.parse("content://media/external/audio/albumart")

data class Song(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    /** Artist with "feat. X" etc. stripped — what the library groups by. */
    val primaryArtist: String,
    val album: String,
    val albumId: Long,
    val albumArtist: String,
    val genre: String,
    val durationMs: Long,
    /** MediaStore track number; disc 2 track 3 is stored as 2003, so it sorts correctly. */
    val track: Int,
    val year: Int,
    /** Seconds since epoch. */
    val dateAdded: Long,
    /** Relative folder, e.g. "Music/Albums/Rumours/". */
    val folder: String,
) {
    val artUri: Uri get() = ContentUris.withAppendedId(ALBUM_ART_BASE, albumId)
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val year: Int,
    val songs: List<Song>,
)

/** A named bucket of songs: an artist, genre or folder. */
data class SongGroup(
    val key: String,
    val name: String,
    val subtitle: String,
    val songs: List<Song>,
)

data class Library(
    val songs: List<Song>,
    val albums: List<Album>,
    val artists: List<SongGroup>,
    val genres: List<SongGroup>,
    val folders: List<SongGroup>,
) {
    val byId: Map<Long, Song> = songs.associateBy { it.id }
    val albumsById: Map<Long, Album> = albums.associateBy { it.id }

    fun artist(key: String) = artists.firstOrNull { it.key == key }
    fun genre(key: String) = genres.firstOrNull { it.key == key }
    fun folder(key: String) = folders.firstOrNull { it.key == key }

    companion object {
        val Empty = Library(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}
