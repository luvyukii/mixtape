package com.mixtape.player.data

import java.text.Normalizer

/**
 * Cleans up the messy names that downloaded tracks tend to have, so they can be looked up:
 *  - video junk:      "Song (Official Music Video) [HD]"      → "Song"
 *  - site tags:       "y2mate.com - Artist - Song_320kbps"     → "Artist - Song"
 *  - channel names:   "ArtistVEVO", "Artist - Topic"          → "Artist"
 *  - featured guests: "Song (feat. Someone)"                   → "Song"
 *  - artist in title: "Artist - Song" with no artist tag       → title "Song", artist "Artist"
 * Brackets that mark a different version (Remix, Live, Acoustic, …) are kept.
 */
object TitleCleaner {

    /** A title/artist pair to try searching for. */
    data class Guess(val title: String, val artist: String)

    private val noise = listOf(
        "official music video", "official video", "official audio", "official lyric video", "official visualizer",
        "official hd video", "music video", "lyric video", "lyrics video", "with lyrics", "lyrics", "lyric",
        "audio only", "official", "audio", "video", "visualizer", "visualiser", "hd", "hq", "4k", "1080p", "720p",
        "explicit", "clean version", "free download", "download", "out now", "full song", "color coded",
        "prod.", "prod by", "produced by", "remastered", "remaster", "mv", "m/v", "kbps", "320", "128",
    )

    /** Any noise phrase as a whole word ("hd" matches "(HD)" but not "(Shadow Remix)"). */
    private val noiseWord by lazy {
        Regex(noise.joinToString("|", "(?<![a-z0-9])(?:", ")(?![a-z0-9])") { Regex.escape(it) })
    }

    private val bracket = Regex("""\s*[(\[{【]([^)\]}】]*)[)\]}】]""")
    // "feat. Someone" up to a closing bracket or an " - " separator (so "A ft. B - Song" keeps "Song").
    private val feat = Regex("""\s*[(\[]?\s*\b(?:feat\.?|ft\.?|featuring)\s+(?:(?!\s[-–|]\s)[^)\]])*[)\]]?""", RegexOption.IGNORE_CASE)
    private val sites = Regex(
        """(?i)[\[(]?\s*(?:www\.)?(?:y2mate|ytmp3|yt1s|savefrom|snaptube|mp3juices?|flvto|spotidown|spotifydown|spotifymate|""" +
            """spotdl|soundcloud\s*downloader|scdownloader|sclouddownloader|klickaud|loader\.to|onlymp3|320ytmp3|notube|""" +
            """tubidy|mp3download|ssyoutube|savetube|cnvmp3|ezmp3|downloader)[\w.\-]*\s*[\])]?(?:\s*[-–_|]\s*)?"""
    )
    /** A bracket group that names a website or downloader, e.g. "[SPOTIFY-DOWNLOADER.COM]". */
    private val siteBracket = Regex("""(?i)\s*[\[(][^\])]*(?:\.com|\.net|\.org|\.to|download|convert|mp3|kbps)[^\])]*[\])]""")
    private val urls = Regex("""(?i)\b(?:https?://)?(?:www\.)?[a-z0-9-]+\.(?:com|net|org|io|to|cc|me)\b""")
    private val bitrate = Regex("""(?i)[_\s(\[]*\d{2,3}\s*kbps[)\]]*""")
    private val youtubeId = Regex("""\s*[\[(]?[A-Za-z0-9_-]{11}[\])]?$""")
    private val separators = listOf(" - ", " – ", " — ", " | ", " ~ ")

    private val unknownArtists = setOf("", "<unknown>", "unknown", "unknown artist", "various artists", "va")

    fun cleanTitle(raw: String): String {
        var s = raw.replace('_', ' ')
        s = siteBracket.replace(s, " ")
        s = sites.replace(s, " ")
        s = urls.replace(s, " ")
        s = bitrate.replace(s, " ")
        s = feat.replace(s, " ")
        s = bracket.replace(s) { m ->
            val inner = m.groupValues[1].lowercase().trim()
            if (inner.isEmpty() || noiseWord.containsMatchIn(inner)) " " else m.value
        }
        // A trailing 11-character YouTube id like "dQw4w9WgXcQ" (needs a digit or mixed case to count).
        youtubeId.find(s)?.let { m ->
            val id = m.value.trim('[', ']', '(', ')', ' ')
            if (id.any(Char::isDigit) && id.any(Char::isUpperCase) && id.any(Char::isLowerCase)) s = s.removeRange(m.range)
        }
        s = s.replace(Regex("""\s+"""), " ").trim()
        // Drop " | Official Music Video"-style segments that are nothing but noise.
        val parts = s.split(" - ", " | ", " – ", " — ").map { it.trim() }
        if (parts.size > 1) {
            val kept = parts.filter { part ->
                noiseWord.replace(part.lowercase(), " ").replace(Regex("""[^a-z0-9]+"""), "").isNotEmpty()
            }
            if (kept.isNotEmpty()) s = kept.joinToString(" - ")
        }
        return s.trim().trim('-', '–', '—', '|', '.', ' ')
    }

    fun cleanArtist(raw: String): String {
        var s = raw.trim().removePrefix("@")
        s = s.replace(Regex("""(?i)\s*-\s*topic$"""), "")
        s = s.replace(Regex("""(?i)\s*vevo$"""), "")
        s = s.replace(Regex("""(?i)\s*(official|officiel)$"""), "")
        s = feat.replace(s, "")
        // "TaylorSwift" → "Taylor Swift" (only when there are no spaces at all)
        if (!s.contains(' ') && Regex("""[a-z][A-Z]""").containsMatchIn(s)) {
            s = s.replace(Regex("""(?<=[a-z])(?=[A-Z])"""), " ")
        }
        s = s.trim()
        return if (s.lowercase() in unknownArtists) "" else s
    }

    /** Title/artist pairs to try, best guess first. */
    fun guesses(rawTitle: String, rawArtist: String): List<Guess> {
        val title = cleanTitle(rawTitle)
        val artist = cleanArtist(rawArtist)
        val out = ArrayList<Guess>()
        val sep = separators.firstOrNull { title.contains(it) }
        if (sep != null) {
            val left = title.substringBefore(sep).trim()
            val right = cleanTitle(title.substringAfter(sep))
            val leftArtist = cleanArtist(left)
            when {
                // "Artist - Song" where the tag artist is the same person, or missing / a channel name
                artist.isEmpty() || similarity(leftArtist, artist) > 0.6 -> {
                    out += Guess(right, leftArtist)
                    if (artist.isNotEmpty() && similarity(leftArtist, artist) <= 0.6) out += Guess(right, artist)
                }
                else -> {
                    out += Guess(right, leftArtist)
                    out += Guess(title, artist)
                }
            }
            out += Guess(left, cleanArtist(right)) // in case it's "Song - Artist"
        } else {
            out += Guess(title, artist)
        }
        return out.filter { it.title.isNotBlank() }.distinct()
    }

    // ---- fuzzy comparison -------------------------------------------------------------

    private val diacritics = Regex("""\p{Mn}+""")

    fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(diacritics, "")
            .replace("&", " and ")
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()

    /** 0..1 similarity using shared letter pairs (forgiving of small spelling differences). */
    fun similarity(a: String, b: String): Float {
        val x = normalize(a).replace(" ", "")
        val y = normalize(b).replace(" ", "")
        if (x.isEmpty() || y.isEmpty()) return 0f
        if (x == y) return 1f
        if (x.length < 2 || y.length < 2) return 0f
        val pairs = HashMap<String, Int>()
        for (i in 0 until x.length - 1) pairs.merge(x.substring(i, i + 2), 1, Int::plus)
        var shared = 0
        for (i in 0 until y.length - 1) {
            val p = y.substring(i, i + 2)
            val n = pairs[p] ?: 0
            if (n > 0) { shared++; pairs[p] = n - 1 }
        }
        val dice = 2f * shared / (x.length - 1 + y.length - 1)
        // One name containing the other ("Song" vs "Song Radio Edit") is a strong hint.
        val contains = if (x.contains(y) || y.contains(x)) 0.85f else 0f
        return maxOf(dice, contains)
    }
}
