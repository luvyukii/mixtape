package com.mixtape.player.ui.components

import android.graphics.Bitmap
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mixtape.player.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
}
private val missing: MutableSet<String> = Collections.synchronizedSet(HashSet())

/** Cover art read from the song file itself, cached per album. */
@Composable
fun AlbumArt(
    song: Song?,
    modifier: Modifier = Modifier,
    sizePx: Int = 192,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    val context = LocalContext.current
    val key = song?.let { "${it.albumId}:$sizePx" }
    val bitmap by produceState<Bitmap?>(initialValue = key?.let { cache.get(it) }, key) {
        if (song == null || key == null || value != null || key in missing) return@produceState
        val loaded = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.loadThumbnail(song.uri, Size(sizePx, sizePx), null) }.getOrNull()
        }
        if (loaded != null) cache.put(key, loaded) else missing += key
        value = loaded
    }

    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize(0.45f),
            )
        }
    }
}
