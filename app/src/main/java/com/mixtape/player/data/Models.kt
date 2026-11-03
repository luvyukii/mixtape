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
    /** Android's album id, for cover art; stays put even if the album name is edited. */
    val artId: Long = albumId,
) {
    val artUri: Uri get() = ContentUris.withAppendedId(ALBUM_ART_BASE, artId)

    /** This song with your edits applied. An edited album gets its own grouping id. */
    fun withEdit(e: SongEdit): Song {
        val newArtist = e.artist ?: artist
        val newAlbum = e.album ?: album
        val newAlbumArtist = e.albumArtist ?: albumArtist
        val regroup = e.album != null || e.albumArtist != null
        return copy(
            title = e.title ?: title,
            artist = newArtist,
            primaryArtist = LibraryOrganizer.primaryArtist(newArtist),
            album = newAlbum,
            albumArtist = newAlbumArtist,
            genre = e.genre ?: genre,
            year = e.year ?: year,
            track = e.track ?: track,
            albumId = if (regroup) {
                -1L - ("${newAlbum.lowercase()}|${newAlbumArtist.lowercase()}".hashCode().toLong() and 0x7fffffffL)
            } else albumId,
            artId = artId,
        )
    }
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
