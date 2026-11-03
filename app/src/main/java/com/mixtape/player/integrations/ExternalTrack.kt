package com.mixtape.player.integrations

import com.mixtape.player.data.PlaylistEntry

/** A liked track from a streaming service. */
data class ExternalTrack(
    val title: String,
    val artist: String,
    val durationMs: Long,
    val id: String,
    val url: String?,
    val downloadable: Boolean = false,
) {
    fun toEntry() = PlaylistEntry(
        title = title,
        artist = artist,
        durationMs = durationMs,
        externalId = id,
        externalUrl = url,
        downloadable = downloadable,
    )
}
