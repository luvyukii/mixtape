package com.mixtape.player.playback

import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.mixtape.player.data.Settings
import com.mixtape.player.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class RepeatMode { OFF, ALL, ONE }

data class PlaybackState(
    val queue: List<Song> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val crossfading: Boolean = false,
) {
    val current: Song? get() = queue.getOrNull(index)
    val upNext: List<Song> get() = if (index < 0) emptyList() else queue.drop(index + 1)
}

/**
 * Plays a queue with real crossfades using two ExoPlayers. Near the end of a song the
 * idle player loads the next one, starts it silent, and the two are blended with an
 * equal-power curve (so the mix doesn't dip in loudness halfway through).
 *
 * All methods must be called on the main thread.
 */
@OptIn(UnstableApi::class)
class CrossfadeEngine(context: Context, private val settings: Settings) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val focus = AudioFocus(appContext, ::onFocusChange)

    private val players: Array<ExoPlayer> = Array(2) { createPlayer() }
    private var activeSlot = 0
    private val active: ExoPlayer get() = players[activeSlot]
    private val standby: ExoPlayer get() = players[1 - activeSlot]

    private var original: List<Song> = emptyList()
    private var queue: List<Song> = emptyList()
    private var index = -1
    /** Which queue position is loaded (silently) in the standby player, or -1. */
    private var standbyIndex = -1
    private var shuffle = false
    private var repeat = RepeatMode.OFF

    private var fadeJob: Job? = null
    private var tickJob: Job? = null
    private var duck = 1f
    private var resumeOnFocusGain = false

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /** The player currently heard; the media session/notification follows it. */
    private val _activePlayer = MutableStateFlow<Player>(players[0])
    val activePlayer: StateFlow<Player> = _activePlayer.asStateFlow()

    val isPlaying: Boolean get() = active.playWhenReady && active.playbackState != Player.STATE_ENDED

    init {
        players.forEach { p -> p.addListener(listenerFor(p)) }
    }

    private fun createPlayer(): ExoPlayer =
        ExoPlayer.Builder(appContext)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ false, // handled once, below, for both players
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

    private fun listenerFor(p: ExoPlayer) = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (p === active && playbackState == Player.STATE_ENDED && fadeJob == null) onTrackEnded()
            publish()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (p === active) {
                if (isPlaying) ensureTicker()
                publish()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "Playback error", error)
            if (p === active) {
                val next = nextIndex(userInitiated = true)
                if (next != null && next != index) loadIntoActive(next, play = true) else pause()
            } else if (fadeJob == null) {
                resetStandby()
            }
        }
    }

    // ---- public controls ----------------------------------------------------------------

    /**
     * Starts playing [songs]. With [shuffled], [startIndex] picks the first song
     * (or null for a random one) and the rest are shuffled.
     */
    fun playQueue(songs: List<Song>, startIndex: Int? = 0, shuffled: Boolean = false) {
        if (songs.isEmpty()) return
        original = songs
        shuffle = shuffled
        if (shuffled) {
            val first = startIndex?.let { songs.getOrNull(it) }
            queue = if (first != null) listOf(first) + smartShuffle(songs.filterIndexed { i, _ -> i != startIndex })
            else smartShuffle(songs)
            loadIntoActive(0, play = true)
        } else {
            queue = songs
            loadIntoActive((startIndex ?: 0).coerceIn(songs.indices), play = true)
        }
    }

    fun play() {
        if (index < 0) return
        if (!focus.request()) return
        resumeOnFocusGain = false
        when (active.playbackState) {
            Player.STATE_IDLE -> active.prepare()
            Player.STATE_ENDED -> active.seekTo(0)
            else -> Unit
        }
        active.play()
        if (fadeJob != null) standby.play()
        ensureTicker()
        publish()
    }

    fun pause() {
        active.pause()
        standby.pause()
        publish()
    }

    fun togglePlay() = if (isPlaying) pause() else play()

    fun next() {
        val n = nextIndex(userInitiated = true) ?: return
        skipTo(n)
    }

    fun previous() {
        if (active.currentPosition > 3_000) {
            seekTo(0)
            return
        }
        val p = when {
            index > 0 -> index - 1
            repeat == RepeatMode.ALL && queue.isNotEmpty() -> queue.lastIndex
            else -> null
        }
        if (p == null) seekTo(0) else skipTo(p)
    }

    /** Jumps to a queue position, with a short fade if music is playing. */
    fun skipTo(position: Int) {
        if (position !in queue.indices) return
        val wasPlaying = isPlaying
        val fade = min(settings.crossfadeMs.value, SKIP_FADE_MS).toLong()
        if (wasPlaying && fade > 0 && fadeJob == null) {
            loadStandby(position)
            crossfade(fade)
        } else {
            loadIntoActive(position, play = wasPlaying)
        }
    }

    fun seekTo(positionMs: Long) {
        if (fadeJob != null) cancelFade()
        active.seekTo(positionMs.coerceAtLeast(0))
        publish()
    }

    fun setShuffle(on: Boolean) {
        shuffle = on
        val current = queue.getOrNull(index)
        if (current != null) {
            if (on) {
                queue = listOf(current) + smartShuffle(original.filter { it.id != current.id })
                index = 0
            } else {
                queue = original
                index = original.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
            }
            resetStandby()
        }
        publish()
    }

    fun cycleRepeat() {
        repeat = RepeatMode.entries[(repeat.ordinal + 1) % RepeatMode.entries.size]
        resetStandby()
        publish()
    }

    fun playNext(songs: List<Song>) {
        if (index < 0) return playQueue(songs)
        queue = queue.toMutableList().apply { addAll(index + 1, songs) }
        val current = queue[index]
        val at = original.indexOfFirst { it.id == current.id }
        original = original.toMutableList().apply { addAll(if (at >= 0) at + 1 else size, songs) }
        resetStandby()
        publish()
    }

    fun addToQueue(songs: List<Song>) {
        if (index < 0) return playQueue(songs)
        queue = queue + songs
        original = original + songs
        resetStandby()
        publish()
    }

    fun removeFromQueue(position: Int) {
        if (position !in queue.indices || position == index) return
        val removed = queue[position]
        queue = queue.filterIndexed { i, _ -> i != position }
        if (position < index) index--
        // Drop one copy from the unshuffled order too.
        val o = original.indexOfFirst { it.id == removed.id }
        if (o >= 0) original = original.filterIndexed { i, _ -> i != o }
        resetStandby()
        publish()
    }

    // ---- loading & crossfading -------------------------------------------------------------

    private fun loadIntoActive(position: Int, play: Boolean) {
        cancelFade()
        resetStandby()
        index = position
        active.setMediaItem(queue[position].toMediaItem())
        active.prepare()
        active.volume = duck
        if (play) play() else {
            active.pause()
            publish()
        }
    }

    private fun loadStandby(position: Int) {
        val p = standby
        p.setMediaItem(queue[position].toMediaItem())
        p.volume = 0f
        p.playWhenReady = false
        p.prepare()
        standbyIndex = position
    }

    private fun resetStandby() {
        if (fadeJob != null) return // standby is the song fading out; leave it alone
        standbyIndex = -1
        standby.stop()
        standby.clearMediaItems()
    }

    /** Swaps players and blends them over [fadeMs], driven by the new song's position. */
    private fun crossfade(fadeMs: Long) {
        if (standbyIndex < 0) return
        val outgoing = active
        val incoming = standby
        activeSlot = 1 - activeSlot
        index = standbyIndex
        standbyIndex = -1

        incoming.volume = 0f
        incoming.play()
        _activePlayer.value = incoming

        fadeJob = scope.launch {
            while (isActive) {
                val t = (incoming.currentPosition.toFloat() / fadeMs).coerceIn(0f, 1f)
                incoming.volume = sin(t * PI / 2).toFloat() * duck
                outgoing.volume = cos(t * PI / 2).toFloat() * duck
                if (t >= 1f) break
                // While paused mid-fade, check back slowly instead of spinning.
                delay(if (incoming.playWhenReady) FADE_STEP_MS else TICK_MS)
            }
            fadeJob = null
            outgoing.stop()
            outgoing.clearMediaItems()
            incoming.volume = duck
            publish()
        }
        ensureTicker()
        publish()
    }

    /** Stops a fade instantly: the new song keeps playing at full volume. */
    private fun cancelFade() {
        val job = fadeJob ?: return
        job.cancel()
        fadeJob = null
        standby.stop()
        standby.clearMediaItems()
        standbyIndex = -1
        active.volume = duck
    }

    private fun onTrackEnded() {
        if (repeat == RepeatMode.ONE) {
            active.seekTo(0)
            active.play()
            return
        }
        val n = nextIndex(userInitiated = false)
        when {
            n == null -> { // end of the queue
                active.pause()
                active.seekTo(0)
                focus.abandon()
                publish()
            }
            n == standbyIndex -> { // preloaded: switch with no gap
                val old = active
                activeSlot = 1 - activeSlot
                index = n
                standbyIndex = -1
                active.volume = duck
                active.play()
                old.stop()
                old.clearMediaItems()
                _activePlayer.value = active
                ensureTicker()
                publish()
            }
            else -> loadIntoActive(n, play = true)
        }
    }

    private fun nextIndex(userInitiated: Boolean): Int? {
        if (queue.isEmpty() || index < 0) return null
        if (repeat == RepeatMode.ONE && !userInitiated) return index
        return when {
            index + 1 < queue.size -> index + 1
            repeat != RepeatMode.OFF -> 0
            else -> null
        }
    }

    /** Runs a few times a second while playing: preloads the next song and starts fades. */
    private fun ensureTicker() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            while (isActive) {
                tick()
                publish()
                if (!isPlaying && fadeJob == null) break
                delay(TICK_MS)
            }
        }
    }

    private fun tick() {
        if (fadeJob != null || repeat == RepeatMode.ONE || !active.isPlaying) return
        val duration = active.duration
        if (duration == C.TIME_UNSET || duration <= 0) return
        val next = nextIndex(userInitiated = false) ?: return
        val remaining = duration - active.currentPosition
        // Never fade for more than a third of either song.
        val fade = min(settings.crossfadeMs.value.toLong(), duration / 3)

        if (remaining <= fade + PRELOAD_MS && standbyIndex != next) loadStandby(next)
        if (fade > 0 && remaining <= fade && standbyIndex == next &&
            standby.playbackState == Player.STATE_READY
        ) {
            crossfade(remaining.coerceAtLeast(MIN_FADE_MS))
        }
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                duck = 1f
                if (fadeJob == null) active.volume = duck
                if (resumeOnFocusGain) play()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (isPlaying) {
                    pause()
                    resumeOnFocusGain = true
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                duck = DUCK_VOLUME
                if (fadeJob == null) active.volume = duck
            }
        }
    }

    /** Shuffles, then nudges songs apart so the same artist rarely plays twice in a row. */
    private fun smartShuffle(songs: List<Song>): List<Song> {
        val out = songs.shuffled().toMutableList()
        if (!settings.smartShuffle.value) return out
        fun key(s: Song) = s.primaryArtist.lowercase()
        for (i in 1 until out.size) {
            if (key(out[i]) != key(out[i - 1])) continue
            val swap = (i + 1 until out.size).firstOrNull { key(out[it]) != key(out[i - 1]) } ?: continue
            out[i] = out[swap].also { out[swap] = out[i] }
        }
        return out
    }

    private fun publish() {
        val duration = active.duration
        _state.value = PlaybackState(
            queue = queue,
            index = index,
            isPlaying = isPlaying,
            positionMs = active.currentPosition.coerceAtLeast(0),
            durationMs = if (duration == C.TIME_UNSET) queue.getOrNull(index)?.durationMs ?: 0 else duration,
            shuffle = shuffle,
            repeat = repeat,
            crossfading = fadeJob != null,
        )
    }

    companion object {
        private const val TAG = "CrossfadeEngine"
        private const val TICK_MS = 200L
        private const val FADE_STEP_MS = 30L
        private const val PRELOAD_MS = 4_000L
        private const val MIN_FADE_MS = 400L
        private const val SKIP_FADE_MS = 1_000
        private const val DUCK_VOLUME = 0.25f
    }
}

fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artUri)
                .build()
        )
        .build()
