package com.mixtape.player.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App preferences and service sign-ins, stored privately on the device. */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("mixtape", Context.MODE_PRIVATE)

    private val _crossfadeMs = MutableStateFlow(prefs.getInt(K_CROSSFADE, 6_000))
    val crossfadeMs: StateFlow<Int> = _crossfadeMs.asStateFlow()

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

    private val _bootAnim = MutableStateFlow(prefs.getBoolean(K_BOOT, true))
    val bootAnim: StateFlow<Boolean> = _bootAnim.asStateFlow()
    fun setBootAnim(on: Boolean) { prefs.edit { putBoolean(K_BOOT, on) }; _bootAnim.value = on }

    private val _smartShuffle = MutableStateFlow(prefs.getBoolean(K_SMART_SHUFFLE, true))
    val smartShuffle: StateFlow<Boolean> = _smartShuffle.asStateFlow()

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

    companion object {
        const val MAX_CROSSFADE_MS = 12_000
        const val MAX_BOOST_DB = 10
        private const val K_BOOST = "audio.boostDb"
        private const val K_BOOT = "ui.bootAnim"
        private const val K_HIRES = "audio.hiRes"

        private const val K_CROSSFADE = "crossfade_ms"
        private const val K_SMART_SHUFFLE = "smart_shuffle"
        private const val K_HIDDEN = "library.hidden"
        private const val K_SCANLINES = "ui.scanlines"
    }
}
