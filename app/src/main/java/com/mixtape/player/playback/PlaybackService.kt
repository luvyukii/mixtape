package com.mixtape.player.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.mixtape.player.MixtapeApp
import com.mixtape.player.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Keeps music playing in the background and provides the notification, lock-screen,
 * headphone-button and Bluetooth controls. It follows whichever of the two crossfade
 * players is currently audible.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val engine = (application as MixtapeApp).engine
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val s = MediaSession.Builder(this, EnginePlayer(engine.activePlayer.value, engine))
            .setSessionActivity(openApp)
            .build()
        session = s

        scope.launch {
            engine.activePlayer.drop(1).collect { player ->
                s.player = EnginePlayer(player, engine)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val engine = (application as MixtapeApp).engine
        if (!engine.isPlaying) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        // The players belong to the app-wide engine, so only the session is released.
        session?.release()
        session = null
        super.onDestroy()
    }
}

/**
 * Wraps the audible player so system controls go through the crossfade engine: next/previous
 * use the app's queue, and play/pause covers both players during a fade.
 */
private class EnginePlayer(player: Player, private val engine: CrossfadeEngine) : ForwardingPlayer(player) {

    private val extraCommands = intArrayOf(
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        Player.COMMAND_PLAY_PAUSE,
    )

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon().addAll(*extraCommands).build()

    override fun isCommandAvailable(command: Int): Boolean =
        command in extraCommands || super.isCommandAvailable(command)

    override fun play() = engine.play()
    override fun pause() = engine.pause()
    override fun setPlayWhenReady(playWhenReady: Boolean) = if (playWhenReady) engine.play() else engine.pause()
    override fun seekToNext() = engine.next()
    override fun seekToNextMediaItem() = engine.next()
    override fun seekToPrevious() = engine.previous()
    override fun seekToPreviousMediaItem() = engine.previous()
    override fun seekTo(positionMs: Long) = engine.seekTo(positionMs)
    override fun hasNextMediaItem(): Boolean = engine.state.value.upNext.isNotEmpty() ||
        engine.state.value.repeat != RepeatMode.OFF
    override fun hasPreviousMediaItem(): Boolean = true
}
