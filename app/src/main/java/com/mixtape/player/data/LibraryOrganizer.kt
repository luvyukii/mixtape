package com.mixtape.player.data

/**
 * Turns a flat list of songs into albums, artists, genres and folders.
 * Grouping is case-insensitive and ignores a leading "The ", so "the beatles",
 * "The Beatles" and "Beatles" all land in one artist.
 */
object LibraryOrganizer {

    const val UNKNOWN_ARTIST = "Unknown artist"
    /** Songs with no album tag have a blank album, which is never shown. */
    const val UNKNOWN_ALBUM = ""
    const val UNKNOWN_GENRE = "Unknown genre"

    private val featSplit = Regex(
        """\s+[(\[]?(?:feat\.?|ft\.?|featuring)\s+.*$|\s*;\s*.*$|\s+/\s+.*$""",
        RegexOption.IGNORE_CASE,
    )

    fun clean(raw: String?, fallback: String): String {
        val v = raw?.trim().orEmpty()
        return if (v.isEmpty() || v.equals("<unknown>", ignoreCase = true)) fallback else v
    }

    fun primaryArtist(artist: String): String =
        artist.replace(featSplit, "").trim().ifEmpty { artist }

    fun groupKey(name: String): String =
        name.lowercase().trim().removePrefix("the ").trim()

    fun organize(songs: List<Song>): Library {
        val byTitle = songs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        val albumOrder = compareBy<Song>({ it.track }, { it.title.lowercase() })

        val albums = songs.filter { it.album.isNotBlank() }.groupBy { it.albumId }.map { (id, list) ->
            Album(
                id = id,
                title = list.first().album,
                artist = albumArtistFor(list),
                year = list.maxOf { it.year },
                songs = list.sortedWith(albumOrder),
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

        val artists = groupNamed(songs) { it.primaryArtist }.map { g ->
            val albumCount = g.songs.filter { it.album.isNotBlank() }.map { it.albumId }.distinct().size
            g.copy(subtitle = "${plural(g.songs.size, "song")} · ${plural(albumCount, "album")}")
        }
        val genres = groupNamed(songs) { it.genre }.map { g ->
            g.copy(subtitle = plural(g.songs.size, "song"))
        }

        val folders = songs.groupBy { it.folder }.map { (path, list) ->
            val name = path.trimEnd('/').substringAfterLast('/').ifEmpty { "Internal storage" }
            SongGroup(
                key = path,
                name = name,
                subtitle = "${plural(list.size, "song")} · /${path.trimEnd('/')}",
                songs = list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }),
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        return Library(byTitle, albums, artists, genres, folders)
    }

    private fun albumArtistFor(list: List<Song>): String {
        list.firstOrNull { it.albumArtist.isNotBlank() }?.let { return it.albumArtist }
        val distinct = list.map { groupKey(it.primaryArtist) }.distinct()
        return if (distinct.size > 2) "Various artists" else list.first().primaryArtist
    }

    private fun groupNamed(songs: List<Song>, name: (Song) -> String): List<SongGroup> =
        songs.groupBy { groupKey(name(it)) }.map { (key, list) ->
            // Show the most common spelling of the name.
            val display = list.groupingBy(name).eachCount().maxByOrNull { it.value }!!.key
            SongGroup(
                key = key,
                name = display,
                subtitle = "",
                songs = list.sortedWith(
                    compareBy<Song>({ it.album.lowercase() }, { it.track }, { it.title.lowercase() })
                ),
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    private fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"
}
