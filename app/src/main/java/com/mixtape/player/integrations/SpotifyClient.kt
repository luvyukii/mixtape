package com.mixtape.player.integrations

import android.net.Uri
import com.mixtape.player.data.OAuthTokens
import com.mixtape.player.data.Settings
import org.json.JSONObject
import java.io.IOException

/**
 * Reads your Spotify "Liked Songs" through the official Web API. Spotify audio can't be
 * downloaded, so likes become a playlist matched against music already on the phone.
 *
 * Setup: create an app at developer.spotify.com, add the redirect URI below, and paste
 * its Client ID into Settings. (Since February 2026 the app owner needs Spotify Premium.)
 */
class SpotifyClient(private val settings: Settings) {

    fun authorizeUrl(): Uri {
        val clientId = settings.accounts.value.spotifyClientId
        if (clientId.isBlank()) throw IOException("Add your Spotify Client ID first.")
        val verifier = Pkce.randomString()
        val state = Pkce.randomString(16)
        settings.savePendingAuth(Settings.SPOTIFY, verifier, state)
        return Uri.parse(AUTH_URL).buildUpon().params(
            "client_id" to clientId,
            "response_type" to "code",
            "redirect_uri" to REDIRECT_URI,
            "code_challenge_method" to "S256",
            "code_challenge" to Pkce.challenge(verifier),
            "state" to state,
            "scope" to SCOPES,
        ).build()
    }

    suspend fun completeSignIn(redirect: Uri) {
        redirect.getQueryParameter("error")?.let { throw IOException("Spotify sign-in failed: $it") }
        val (verifier, state) = settings.pendingAuth(Settings.SPOTIFY)
            ?: throw IOException("Spotify sign-in expired. Try again.")
        if (redirect.getQueryParameter("state") != state) throw IOException("Spotify sign-in didn't match. Try again.")
        val code = redirect.getQueryParameter("code") ?: throw IOException("Spotify didn't return a sign-in code.")
        settings.clearPendingAuth(Settings.SPOTIFY)

        val json = Http.postForm(
            TOKEN_URL,
            mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to REDIRECT_URI,
                "client_id" to settings.accounts.value.spotifyClientId,
                "code_verifier" to verifier,
            ),
        )
        saveTokens(JSONObject(json), previousRefresh = null)
    }

    fun signOut() = settings.clearTokens(Settings.SPOTIFY)

    /** Fetches every liked song, newest first. */
    suspend fun likedTracks(onProgress: (Int) -> Unit): List<ExternalTrack> {
        val out = ArrayList<ExternalTrack>()
        var pageSize = 50
        var next: String? = "$API/me/tracks?limit=$pageSize"
        while (next != null) {
            val body = try {
                Http.get(next, mapOf("Authorization" to "Bearer ${accessToken()}"))
            } catch (e: HttpError) {
                // Some app modes cap page sizes; fall back to small pages instead of failing.
                if (e.code == 400 && pageSize > 10) {
                    pageSize = 10
                    next = "$API/me/tracks?limit=$pageSize&offset=${out.size}"
                    continue
                }
                throw e
            }
            val page = JSONObject(body)
            val items = page.optJSONArray("items")
            for (i in 0 until (items?.length() ?: 0)) {
                val item = items!!.getJSONObject(i)
                // The field was renamed from "track" to "item" in some 2026 endpoints.
                val track = item.optJSONObject("track") ?: item.optJSONObject("item") ?: continue
                val artists = track.optJSONArray("artists")
                val artistNames = (0 until (artists?.length() ?: 0))
                    .mapNotNull { artists!!.getJSONObject(it).str("name") }
                out += ExternalTrack(
                    title = track.str("name") ?: continue,
                    artist = artistNames.joinToString(", "),
                    durationMs = track.optLong("duration_ms"),
                    id = track.str("id") ?: track.str("uri") ?: continue,
                    url = track.optJSONObject("external_urls")?.str("spotify"),
                )
            }
            onProgress(out.size)
            next = page.str("next")
        }
        return out
    }

    private suspend fun accessToken(): String {
        val tokens = settings.tokens(Settings.SPOTIFY)
            ?: throw IOException("Connect Spotify first.")
        if (!tokens.expired) return tokens.access
        val refresh = tokens.refresh ?: throw IOException("Spotify session expired. Connect again.")
        val json = try {
            Http.postForm(
                TOKEN_URL,
                mapOf(
                    "grant_type" to "refresh_token",
                    "refresh_token" to refresh,
                    "client_id" to settings.accounts.value.spotifyClientId,
                ),
            )
        } catch (e: HttpError) {
            signOut()
            throw IOException("Spotify session expired. Connect again.")
        }
        return saveTokens(JSONObject(json), previousRefresh = refresh).access
    }

    private fun saveTokens(json: JSONObject, previousRefresh: String?): OAuthTokens {
        val tokens = OAuthTokens(
            access = json.getString("access_token"),
            refresh = json.str("refresh_token") ?: previousRefresh,
            expiresAt = System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000,
        )
        settings.saveTokens(Settings.SPOTIFY, tokens)
        return tokens
    }

    companion object {
        const val REDIRECT_URI = "mixtape://spotify-callback"
        private const val AUTH_URL = "https://accounts.spotify.com/authorize"
        private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        private const val API = "https://api.spotify.com/v1"
        private const val SCOPES = "user-library-read"
    }
}
