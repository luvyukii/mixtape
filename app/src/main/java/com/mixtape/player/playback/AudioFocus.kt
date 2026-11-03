package com.mixtape.player.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * One audio-focus request shared by both crossfade players. If each player asked for
 * focus itself, the second one would knock the first one out mid-fade.
 */
class AudioFocus(context: Context, private val onChange: (Int) -> Unit) {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var held = false

    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS) held = false
            onChange(change)
        }
        .build()

    fun request(): Boolean {
        if (!held) held = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return held
    }

    fun abandon() {
        if (held) {
            audioManager.abandonAudioFocusRequest(request)
            held = false
        }
    }
}
