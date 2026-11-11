package com.mixtape.player.playback

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Remembers which songs were played recently (newest last), so shuffles can hold back
 * tracks you've just heard. Saved to a small file so it survives restarts.
 */
class PlayHistory(context: Context) {

    private val file = File(context.filesDir, "play_history.txt")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ids = ArrayDeque<Long>()

    init {
        runCatching {
            if (file.exists()) file.readLines().mapNotNullTo(ids) { it.toLongOrNull() }
        }
    }

    @Synchronized
    fun record(id: Long) {
        if (ids.lastOrNull() == id) return
        ids.remove(id)
        ids.addLast(id)
        while (ids.size > MAX) ids.removeFirst()
        val snapshot = ids.toList()
        scope.launch { runCatching { file.writeText(snapshot.joinToString("\n")) } }
    }

    /** How recently each song was played: 0 = the last one, 1 = the one before, and so on. */
    @Synchronized
    fun recency(): Map<Long, Int> {
        val out = HashMap<Long, Int>(ids.size)
        ids.forEachIndexed { i, id -> out[id] = ids.size - 1 - i }
        return out
    }

    companion object {
        private const val MAX = 3000
    }
}
