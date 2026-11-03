package com.mixtape.player.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OAuthTokens(val access: String, val refresh: String?, val expiresAt: Long) {
    val expired: Boolean get() = System.currentTimeMillis() > expiresAt - 60_000
}

data class AccountsState(
    val spotifyClientId: String = "",
    val spotifyConnected: Boolean = false,
    val soundCloudClientId: String = "",
    val soundCloudClientSecret: String = "",
    val soundCloudConnected: Boolean = false,
    val youTubeMusicOnly: Boolean = true,
)

/** App preferences and service sign-ins, stored privately on the device. */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("mixtape", Context.MODE_PRIVATE)

    private val _crossfadeMs = MutableStateFlow(prefs.getInt(K_CROSSFADE, 6_000))
    val crossfadeMs: StateFlow<Int> = _crossfadeMs.asStateFlow()

    private val _tintArt = MutableStateFlow(prefs.getBoolean(K_TINT_ART, true))
    val tintArt: StateFlow<Boolean> = _tintArt.asStateFlow()
    fun setTintArt(on: Boolean) { prefs.edit { putBoolean(K_TINT_ART, on) }; _tintArt.value = on }

    private val _scanlines = MutableStateFlow(prefs.getBoolean(K_SCANLINES, true))
    val scanlines: StateFlow<Boolean> = _scanlines.asStateFlow()
    fun setScanlines(on: Boolean) { prefs.edit { putBoolean(K_SCANLINES, on) }; _scanlines.value = on }

    /** Volume boost in dB (0 = off), applied with a limiter so it doesn't clip. */
    private val _boostDb = MutableStateFlow(prefs.getInt(K_BOOST, 0))
    val boostDb: StateFlow<Int> = _boostDb.asStateFlow()
    fun setBoostDb(db: Int) {
        val v = db.coerceIn(0, MAX_BOOST_DB)
        prefs.edit { putInt(K_BOOST, v) }
        _boostDb.value = v
    }

    /** Send hi-res (24-bit+) audio to Android as-is instead of 16-bit. Applies on next launch. */
    private val _hiRes = MutableStateFlow(prefs.getBoolean(K_HIRES, false))
    val hiRes: StateFlow<Boolean> = _hiRes.asStateFlow()
    fun setHiRes(on: Boolean) { prefs.edit { putBoolean(K_HIRES, on) }; _hiRes.value = on }

    private val _smartShuffle = MutableStateFlow(prefs.getBoolean(K_SMART_SHUFFLE, true))
    val smartShuffle: StateFlow<Boolean> = _smartShuffle.asStateFlow()

    private val _accounts = MutableStateFlow(readAccounts())
    val accounts: StateFlow<AccountsState> = _accounts.asStateFlow()

    /** Songs hidden from the library (the files stay on the phone). */
    private val _hiddenIds = MutableStateFlow(
        prefs.getStringSet(K_HIDDEN, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    )
    val hiddenIds: StateFlow<Set<Long>> = _hiddenIds.asStateFlow()

    fun hide(ids: Collection<Long>) = setHidden(_hiddenIds.value + ids)
    fun unhide(ids: Collection<Long>) = setHidden(_hiddenIds.value - ids.toSet())
    fun unhideAll() = setHidden(emptySet())

    private fun setHidden(ids: Set<Long>) {
        prefs.edit { putStringSet(K_HIDDEN, ids.map { it.toString() }.toSet()) }
        _hiddenIds.value = ids
    }

    fun setCrossfadeMs(ms: Int) {
        val v = ms.coerceIn(0, MAX_CROSSFADE_MS)
        prefs.edit { putInt(K_CROSSFADE, v) }
        _crossfadeMs.value = v
    }

    fun setSmartShuffle(on: Boolean) {
        prefs.edit { putBoolean(K_SMART_SHUFFLE, on) }
        _smartShuffle.value = on
    }

    fun setSpotifyClientId(v: String) = editAccounts { putString(K_SPOTIFY_CLIENT, v.trim()) }
    fun setSoundCloudClientId(v: String) = editAccounts { putString(K_SC_CLIENT, v.trim()) }
    fun setSoundCloudClientSecret(v: String) = editAccounts { putString(K_SC_SECRET, v.trim()) }
    fun setYouTubeMusicOnly(v: Boolean) = editAccounts { putBoolean(K_YT_MUSIC_ONLY, v) }

    // ---- tokens -----------------------------------------------------------------------

    fun tokens(provider: String): OAuthTokens? {
        val access = prefs.getString("$provider.access", null) ?: return null
        return OAuthTokens(
            access = access,
            refresh = prefs.getString("$provider.refresh", null),
            expiresAt = prefs.getLong("$provider.expiresAt", 0),
        )
    }

    fun saveTokens(provider: String, tokens: OAuthTokens) = editAccounts {
        putString("$provider.access", tokens.access)
        putString("$provider.refresh", tokens.refresh)
        putLong("$provider.expiresAt", tokens.expiresAt)
    }

    fun clearTokens(provider: String) = editAccounts {
        remove("$provider.access")
        remove("$provider.refresh")
        remove("$provider.expiresAt")
    }

    /** Remembers the PKCE verifier and state while the browser sign-in is open. */
    fun savePendingAuth(provider: String, verifier: String, state: String) = prefs.edit {
        putString("$provider.verifier", verifier)
        putString("$provider.state", state)
    }

    fun pendingAuth(provider: String): Pair<String, String>? {
        val verifier = prefs.getString("$provider.verifier", null) ?: return null
        val state = prefs.getString("$provider.state", null) ?: return null
        return verifier to state
    }

    fun clearPendingAuth(provider: String) = prefs.edit {
        remove("$provider.verifier")
        remove("$provider.state")
    }

    private fun editAccounts(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit(commit = true, action = block)
        _accounts.value = readAccounts()
    }

    private fun readAccounts() = AccountsState(
        spotifyClientId = prefs.getString(K_SPOTIFY_CLIENT, "").orEmpty(),
        spotifyConnected = prefs.contains("$SPOTIFY.access"),
        soundCloudClientId = prefs.getString(K_SC_CLIENT, "").orEmpty(),
        soundCloudClientSecret = prefs.getString(K_SC_SECRET, "").orEmpty(),
        soundCloudConnected = prefs.contains("$SOUNDCLOUD.access"),
        youTubeMusicOnly = prefs.getBoolean(K_YT_MUSIC_ONLY, true),
    )

    companion object {
        const val SPOTIFY = "spotify"
        const val SOUNDCLOUD = "soundcloud"
        const val MAX_CROSSFADE_MS = 12_000
        const val MAX_BOOST_DB = 10
        private const val K_BOOST = "audio.boostDb"
        private const val K_HIRES = "audio.hiRes"

        private const val K_CROSSFADE = "crossfade_ms"
        private const val K_SMART_SHUFFLE = "smart_shuffle"
        private const val K_SPOTIFY_CLIENT = "spotify.clientId"
        private const val K_SC_CLIENT = "soundcloud.clientId"
        private const val K_SC_SECRET = "soundcloud.clientSecret"
        private const val K_YT_MUSIC_ONLY = "youtube.musicOnly"
        private const val K_HIDDEN = "library.hidden"
        private const val K_TINT_ART = "ui.tintArt"
        private const val K_SCANLINES = "ui.scanlines"
    }
}
