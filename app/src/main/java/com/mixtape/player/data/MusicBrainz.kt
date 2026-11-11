package com.mixtape.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One possible identity for a track, from MusicBrainz. */
data class LookupMatch(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val year: Int,
    val track: Int,
    val durationMs: Long,
    val releaseId: String,
    val releaseGroupId: String?,
    val score: Int,
)

/**
 * Looks up track info in MusicBrainz (an open music encyclopedia) and cover pictures in the
 * Cover Art Archive. Both are free and need no account. MusicBrainz asks apps to send at
 * most about one request per second, so calls are spaced out.
 */
object MusicBrainz {

    private const val USER_AGENT = "NEXUS/1.0 (Android music player)"
    private const val API = "https://musicbrainz.org/ws/2"
    private val gate = Mutex()
    private var lastCall = 0L

    suspend fun search(title: String, artist: String): List<LookupMatch> {
        val t = title.trim()
        if (t.isEmpty()) return emptyList()
        val query = buildString {
            append("recording:\"").append(escape(t)).append('"')
            if (artist.isNotBlank()) append(" AND artist:\"").append(escape(artist.trim())).append('"')
        }
        val url = "$API/recording?fmt=json&limit=15&query=" + URLEncoder.encode(query, "UTF-8")
        val json = JSONObject(String(get(url, json = true) ?: throw IOException("No response from MusicBrainz")))
        val recordings = json.optJSONArray("recordings") ?: return emptyList()

        val out = ArrayList<LookupMatch>()
        for (i in 0 until recordings.length()) {
            val r = recordings.getJSONObject(i)
            val credits = r.optJSONArray("artist-credit")
            val artistName = buildString {
                for (k in 0 until (credits?.length() ?: 0)) {
                    val c = credits!!.getJSONObject(k)
                    append(c.optString("name"))
                    append(c.optString("joinphrase"))
                }
            }.trim()
            val releases = r.optJSONArray("releases") ?: continue
            // A recording appears on many releases; prefer albums over singles and compilations.
            val picks = (0 until releases.length()).map { releases.getJSONObject(it) }
                .sortedBy { rel ->
                    val g = rel.optJSONObject("release-group")
                    val type = g?.optString("primary-type").orEmpty()
                    val secondary = g?.optJSONArray("secondary-types")?.length() ?: 0
                    when {
                        type == "Album" && secondary == 0 -> 0
                        type == "Album" -> 2
                        type == "EP" -> 1
                        else -> 3
                    }
                }
                .take(2)
            for (rel in picks) {
                val media = rel.optJSONArray("media")?.optJSONObject(0)
                val trackNo = media?.optJSONArray("track")?.optJSONObject(0)?.optString("number")?.toIntOrNull() ?: 0
                val disc = media?.optInt("position", 1) ?: 1
                val relArtist = rel.optJSONArray("artist-credit")?.let { ac ->
                    (0 until ac.length()).joinToString("") { ac.getJSONObject(it).optString("name") + ac.getJSONObject(it).optString("joinphrase") }
                }?.trim().orEmpty()
                out += LookupMatch(
                    title = r.optString("title"),
                    artist = artistName,
                    album = rel.optString("title"),
                    albumArtist = relArtist.ifBlank { artistName },
                    year = rel.optString("date").take(4).toIntOrNull() ?: 0,
                    track = if (trackNo > 0) (if (disc > 1) disc * 1000 + trackNo else trackNo) else 0,
                    durationMs = r.optLong("length"),
                    releaseId = rel.optString("id"),
                    releaseGroupId = rel.optJSONObject("release-group")?.optString("id")?.takeIf { it.isNotBlank() },
                    score = r.optInt("score"),
                )
            }
        }
        return out
            .distinctBy { Triple(it.title.lowercase(), it.artist.lowercase(), it.album.lowercase()) }
            .take(12)
    }

    /** The front cover for a match (falls back to the album's other editions), or null. */
    suspend fun cover(match: LookupMatch, size: Int): ByteArray? {
        get("https://coverartarchive.org/release/${match.releaseId}/front-$size", json = false)?.let { return it }
        val group = match.releaseGroupId ?: return null
        return get("https://coverartarchive.org/release-group/$group/front-$size", json = false)
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    /** GET with redirects; null for "not found". JSON calls to MusicBrainz are spaced 1.1 s apart. */
    private suspend fun get(url: String, json: Boolean): ByteArray? = withContext(Dispatchers.IO) {
        if (json) {
            gate.withLock {
                val wait = lastCall + 1100 - System.currentTimeMillis()
                if (wait > 0) kotlinx.coroutines.delay(wait)
                lastCall = System.currentTimeMillis()
            }
        }
        var target = url
        repeat(5) {
            val conn = (URL(target).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 12_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", USER_AGENT)
                if (json) setRequestProperty("Accept", "application/json")
            }
            try {
                when (val code = conn.responseCode) {
                    in 200..299 -> return@withContext conn.inputStream.use { it.readBytes() }
                    in 300..399 -> {
                        target = URL(URL(target), conn.getHeaderField("Location") ?: return@withContext null).toString()
                    }
                    404 -> return@withContext null
                    503 -> throw IOException("MusicBrainz is busy. Try again in a moment.")
                    else -> throw IOException("Lookup failed (HTTP $code)")
                }
            } finally {
                conn.disconnect()
            }
        }
        null
    }
}
