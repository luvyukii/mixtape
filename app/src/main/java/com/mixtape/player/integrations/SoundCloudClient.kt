package com.mixtape.player.integrations

import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.mixtape.player.data.ImportManager
import com.mixtape.player.data.OAuthTokens
import com.mixtape.player.data.PlaylistEntry
import com.mixtape.player.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * SoundCloud likes and downloads through the official API. Only tracks whose artist has
 * turned on downloads ("downloadable") can be saved; everything else stays a link.
 *
 * Setup: register an app at soundcloud.com/you/apps, add the redirect URI below, and paste
 * its Client ID and Client Secret into Settings. SoundCloud requires the secret for sign-in.
 */
class SoundCloudClient(private val settings: Settings) {

    fun authorizeUrl(): Uri {
        val acct = settings.accounts.value
        if (acct.soundCloudClientId.isBlank() || acct.soundCloudClientSecret.isBlank()) {
            throw IOException("Add your SoundCloud Client ID and Secret first.")
        }
        val verifier = Pkce.randomString()
        val state = Pkce.randomString(16)
        settings.savePendingAuth(Settings.SOUNDCLOUD, verifier, state)
        return Uri.parse(AUTH_URL).buildUpon().params(
            "client_id" to acct.soundCloudClientId,
            "redirect_uri" to REDIRECT_URI,
            "response_type" to "code",
            "code_challenge" to Pkce.challenge(verifier),
            "code_challenge_method" to "S256",
            "state" to state,
            "display" to "popup", // mobile-friendly sign-in page
        ).build()
    }

    suspend fun completeSignIn(redirect: Uri) {
        redirect.getQueryParameter("error")?.let { throw IOException("SoundCloud sign-in failed: $it") }
        val (verifier, state) = settings.pendingAuth(Settings.SOUNDCLOUD)
            ?: throw IOException("SoundCloud sign-in expired. Try again.")
        if (redirect.getQueryParameter("state") != state) throw IOException("SoundCloud sign-in didn't match. Try again.")
        val code = redirect.getQueryParameter("code") ?: throw IOException("SoundCloud didn't return a sign-in code.")
        settings.clearPendingAuth(Settings.SOUNDCLOUD)

        val acct = settings.accounts.value
        val json = Http.postForm(
            TOKEN_URL,
            mapOf(
                "grant_type" to "authorization_code",
                "client_id" to acct.soundCloudClientId,
                "client_secret" to acct.soundCloudClientSecret,
                "redirect_uri" to REDIRECT_URI,
                "code_verifier" to verifier,
                "code" to code,
            ),
        )
        saveTokens(JSONObject(json), previousRefresh = null)
    }

    fun signOut() = settings.clearTokens(Settings.SOUNDCLOUD)

    suspend fun likedTracks(onProgress: (Int) -> Unit): List<ExternalTrack> {
        val out = ArrayList<ExternalTrack>()
        var next: String? = "$API/me/likes/tracks?limit=50&linked_partitioning=true"
        while (next != null) {
            val body = Http.get(next, authHeader())
            val trimmed = body.trimStart()
            val collection: JSONArray
            if (trimmed.startsWith("[")) {
                collection = JSONArray(trimmed)
                next = null
            } else {
                val page = JSONObject(trimmed)
                collection = page.optJSONArray("collection") ?: JSONArray()
                next = page.str("next_href")
            }
            for (i in 0 until collection.length()) {
                val t = collection.getJSONObject(i)
                val id = t.str("urn") ?: t.optLong("id").takeIf { it > 0 }?.toString() ?: continue
                out += ExternalTrack(
                    title = t.str("title") ?: continue,
                    artist = t.optJSONObject("user")?.str("username") ?: "",
                    durationMs = t.optLong("duration"),
                    id = id,
                    url = t.str("permalink_url"),
                    downloadable = t.optBoolean("downloadable"),
                )
            }
            onProgress(out.size)
        }
        return out
    }

    /**
     * Saves the artist-provided download into Music/Mixtape/SoundCloud.
     * Fails cleanly for tracks without downloads enabled.
     */
    suspend fun download(entry: PlaylistEntry, importer: ImportManager): Uri = withContext(Dispatchers.IO) {
        if (!entry.downloadable) throw IOException("The artist hasn't enabled downloads for \"${entry.title}\".")
        val id = entry.externalId ?: throw IOException("Missing SoundCloud track id.")
        val headers = authHeader()

        // Step 1: ask for the download; SoundCloud answers with a redirect to the file.
        val first = open("$API/tracks/${Uri.encode(id)}/download", headers, followRedirects = false)
        val fileConn: HttpURLConnection = when (first.responseCode) {
            in 300..399 -> {
                val location = first.getHeaderField("Location") ?: throw IOException("Download link missing.")
                first.disconnect()
                open(location, emptyMap(), followRedirects = true)
            }
            in 200..299 -> {
                if (first.contentType?.contains("json") == true) {
                    val json = JSONObject(first.inputStream.bufferedReader().use { it.readText() })
                    first.disconnect()
                    val location = json.str("redirectUri") ?: json.str("location")
                        ?: throw IOException("Download link missing.")
                    open(location, emptyMap(), followRedirects = true)
                } else first
            }
            401 -> throw IOException("SoundCloud session expired. Connect again.")
            403, 404 -> throw IOException("\"${entry.title}\" isn't available to download.")
            else -> throw IOException("Download failed (HTTP ${first.responseCode}).")
        }

        try {
            if (fileConn.responseCode !in 200..299) throw IOException("Download failed (HTTP ${fileConn.responseCode}).")
            val ext = extensionFor(fileConn)
            val name = ImportManager.safeName("${entry.artist} - ${entry.title}.$ext")
            val mime = ImportManager.audioMime(name, fileConn.contentType?.substringBefore(';'))
            importer.saveAudio(
                displayName = name,
                mime = mime,
                relativePath = "Music/Mixtape/SoundCloud/",
                extra = {
                    put(MediaStore.Audio.Media.TITLE, entry.title)
                    put(MediaStore.Audio.Media.ARTIST, entry.artist)
                },
            ) { out -> fileConn.inputStream.use { it.copyTo(out) } }
        } finally {
            fileConn.disconnect()
        }
    }

    private fun open(url: String, headers: Map<String, String>, followRedirects: Boolean): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = followRedirects
            connectTimeout = 15_000
            readTimeout = 60_000
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }

    private fun extensionFor(conn: HttpURLConnection): String {
        conn.getHeaderField("Content-Disposition")
            ?.let { Regex("""filename\*?=(?:UTF-8'')?"?([^";]+)"?""").find(it)?.groupValues?.get(1) }
            ?.substringAfterLast('.', "")?.lowercase()
            ?.takeIf { it.length in 2..5 }
            ?.let { return it }
        val mime = conn.contentType?.substringBefore(';')?.trim()
        return when (mime) {
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
            "audio/flac", "audio/x-flac" -> "flac"
            "audio/aiff", "audio/x-aiff" -> "aiff"
            "audio/mp4", "audio/x-m4a", "audio/aac" -> "m4a"
            "audio/ogg" -> "ogg"
            else -> mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "mp3"
        }
    }

    private suspend fun authHeader(): Map<String, String> = mapOf("Authorization" to "OAuth ${accessToken()}")

    private suspend fun accessToken(): String {
        val tokens = settings.tokens(Settings.SOUNDCLOUD) ?: throw IOException("Connect SoundCloud first.")
        if (!tokens.expired) return tokens.access
        val refresh = tokens.refresh ?: throw IOException("SoundCloud session expired. Connect again.")
        val acct = settings.accounts.value
        val json = try {
            Http.postForm(
                TOKEN_URL,
                mapOf(
                    "grant_type" to "refresh_token",
                    "client_id" to acct.soundCloudClientId,
                    "client_secret" to acct.soundCloudClientSecret,
                    "refresh_token" to refresh,
                ),
            )
        } catch (e: HttpError) {
            signOut()
            throw IOException("SoundCloud session expired. Connect again.")
        }
        return saveTokens(JSONObject(json), previousRefresh = null).access
    }

    private fun saveTokens(json: JSONObject, previousRefresh: String?): OAuthTokens {
        val tokens = OAuthTokens(
            access = json.getString("access_token"),
            // SoundCloud refresh tokens are single-use, so always keep the newest one.
            refresh = json.str("refresh_token") ?: previousRefresh,
            expiresAt = System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000,
        )
        settings.saveTokens(Settings.SOUNDCLOUD, tokens)
        return tokens
    }

    companion object {
        const val REDIRECT_URI = "mixtape://soundcloud-callback"
        private const val AUTH_URL = "https://secure.soundcloud.com/authorize"
        private const val TOKEN_URL = "https://secure.soundcloud.com/oauth/token"
        private const val API = "https://api.soundcloud.com"
    }
}
