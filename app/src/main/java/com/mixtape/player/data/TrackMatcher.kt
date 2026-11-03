package com.mixtape.player.data

import java.text.Normalizer
import kotlin.math.abs

/**
 * Matches a track from Spotify/YouTube/SoundCloud ("Artist", "Title (Remastered 2011)")
 * to a song file on the phone, ignoring case, accents, brackets, "feat." credits and
 * common suffixes like "Official Video".
 */
object TrackMatcher {

    private val brackets = Regex("""[(\[{][^)\]}]*[)\]}]""")
    private val featTail = Regex("""\s+(?:feat\.?|ft\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE)
    private val suffixTail = Regex(
        """\s+-\s+(?:\d{4}\s+)?(?:remaster(?:ed)?|radio edit|single version|album version|live|mono|stereo|original mix|official).*$""",
        RegexOption.IGNORE_CASE,
    )
    private val nonAlnum = Regex("""[^a-z0-9]+""")
    private val diacritics = Regex("""\p{Mn}+""")

    private fun simplify(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(diacritics, "")
            .replace("&", " and ")
            .replace(nonAlnum, " ")
            .trim()

    fun normTitle(title: String): String =
        simplify(title.replace(brackets, " ").replace(featTail, "").replace(suffixTail, ""))

    fun normArtist(artist: String): String =
        simplify(LibraryOrganizer.primaryArtist(artist)).removePrefix("the ").trim()

    class Index(songs: List<Song>) {
        private val byTitle: Map<String, List<Song>> = songs.groupBy { normTitle(it.title) }

        fun find(title: String, artist: String, durationMs: Long = 0): Song? {
            val candidates = byTitle[normTitle(title)] ?: return null
            val wanted = normArtist(artist)
            val artistMatches = candidates.filter {
                val have = normArtist(it.artist)
                have.isNotEmpty() && wanted.isNotEmpty() && (have == wanted || have.contains(wanted) || wanted.contains(have))
            }
            if (artistMatches.isNotEmpty()) {
                return artistMatches.minByOrNull { if (durationMs > 0) abs(it.durationMs - durationMs) else 0L }
            }
            // Same title, unknown/odd artist tag: accept only when the lengths agree.
            if (durationMs > 0) {
                return candidates.firstOrNull { abs(it.durationMs - durationMs) <= 3_000 }
            }
            return null
        }
    }
}
