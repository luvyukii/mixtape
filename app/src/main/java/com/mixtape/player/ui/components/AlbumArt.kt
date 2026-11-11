package com.mixtape.player.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/** Loads a picture file, shrunk to about [size] pixels so lists stay light. */
private fun decodeScaled(path: String, size: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()

/** Cover art: the one picked in NEXUS if any, otherwise the picture embedded in the song file. */
@Composable
fun AlbumArt(
    song: Song?,
    modifier: Modifier = Modifier,
    sizePx: Int = 192,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    val context = LocalContext.current
    val key = song?.let { "${it.id}:${it.modified}:${it.artOverride}:$sizePx" }
    // Keyed on the song itself: songs Android lumps into one "album" can carry different
    // pictures, so each file shows its own embedded art.
    var bitmap by remember(key) { mutableStateOf(key?.let { cache.get(it) }) }

    LaunchedEffect(key) {
        if (song == null || key == null || bitmap != null || key in missing) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            val override = song.artOverride
            if (override != null) decodeScaled(override, sizePx)
            else runCatching { context.contentResolver.loadThumbnail(song.uri, Size(sizePx, sizePx), null) }.getOrNull()
        }
        if (loaded != null) cache.put(key, loaded) else missing += key
        bitmap = loaded
    }

    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
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
