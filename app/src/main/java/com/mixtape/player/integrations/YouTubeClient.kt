package com.mixtape.player.integrations

import org.json.JSONObject

/**
 * Reads videos you've liked on YouTube (YouTube Data API v3) and turns them into
 * artist/title pairs for matching. Sign-in happens in [com.mixtape.player.ui.YouTubeAuth].
 */
class YouTubeClient {

    suspend fun likedVideos(accessToken: String, musicOnly: Boolean, onProgress: (Int) -> Unit): List<ExternalTrack> {
        val out = ArrayList<ExternalTrack>()
        var pageToken: String? = null
        do {
            val url = buildString {
                append("$API/videos?myRating=like&part=snippet,contentDetails&maxResults=50")
                pageToken?.let { append("&pageToken=").append(it) }
            }
            val page = JSONObject(Http.get(url, mapOf("Authorization" to "Bearer $accessToken")))
            val items = page.optJSONArray("items")
            for (i in 0 until (items?.length() ?: 0)) {
                val v = items!!.getJSONObject(i)
                val snippet = v.optJSONObject("snippet") ?: continue
                if (musicOnly && snippet.str("categoryId") != MUSIC_CATEGORY) continue
                val id = v.str("id") ?: continue
                val (artist, title) = splitTitle(snippet.str("title") ?: continue, snippet.str("channelTitle").orEmpty())
                out += ExternalTrack(
                    title = title,
                    artist = artist,
                    durationMs = parseDuration(v.optJSONObject("contentDetails")?.str("duration")),
                    id = id,
                    url = "https://music.youtube.com/watch?v=$id",
                )
            }
            onProgress(out.size)
            pageToken = page.str("nextPageToken")
        } while (pageToken != null)
        return out
    }

    companion object {
        private const val API = "https://www.googleapis.com/youtube/v3"
        private const val MUSIC_CATEGORY = "10"
        const val SCOPE = "https://www.googleapis.com/auth/youtube.readonly"

        private val channelNoise = Regex("""\s*-\s*Topic$|VEVO$|\s+Official$""", RegexOption.IGNORE_CASE)

        /** "Artist - Song (Official Video)" → ("Artist", "Song (Official Video)"). */
        fun splitTitle(videoTitle: String, channel: String): Pair<String, String> {
            val parts = videoTitle.split(" - ", " – ", " — ", limit = 2)
            return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                parts[0].trim() to parts[1].trim()
            } else {
                channel.replace(channelNoise, "").trim() to videoTitle.trim()
            }
        }

        private val isoDuration = Regex("""PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?""")

        fun parseDuration(iso: String?): Long {
            val m = iso?.let { isoDuration.matchEntire(it) } ?: return 0
            val (h, min, s) = m.destructured
            return ((h.toLongOrNull() ?: 0) * 3600 + (min.toLongOrNull() ?: 0) * 60 + (s.toLongOrNull() ?: 0)) * 1000
        }
    }
}
