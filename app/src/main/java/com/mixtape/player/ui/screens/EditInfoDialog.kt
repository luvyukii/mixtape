package com.mixtape.player.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mixtape.player.data.LookupMatch
import com.mixtape.player.data.MusicBrainz
import com.mixtape.player.data.Song
import com.mixtape.player.data.SongEdit
import com.mixtape.player.ui.components.TacOutlinedButton
import com.mixtape.player.ui.components.TacSwitch
import com.mixtape.player.ui.components.formatTime
import com.mixtape.player.ui.theme.Tac
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Edit one song, or many at once.
 * - A field the songs disagree on starts empty ("mixed") and is kept unless you type something.
 * - Emptying a field that had a value clears it; the ✕ on a field clears it even when mixed.
 * - For a single song, AUTO-FILL ONLINE looks the track up and fills everything, artwork included.
 */
@Composable
fun EditInfoDialog(
    songs: List<Song>,
    onDismiss: () -> Unit,
    onSave: (edit: SongEdit, writeToFile: Boolean, renumber: Boolean, artwork: LookupMatch?) -> Unit,
) {
    val single = songs.size == 1
    val first = songs.first()
    fun common(get: (Song) -> String): String? = songs.map(get).distinct().singleOrNull()

    val cArtist = common { it.artist }
    val cAlbum = common { it.album }
    val cAlbumArtist = common { it.albumArtist }
    val cGenre = common { it.genre }
    val cYear = common { it.year.takeIf { y -> y > 0 }?.toString().orEmpty() }

    var title by rememberSaveable { mutableStateOf(first.title) }
    var artist by rememberSaveable { mutableStateOf(cArtist.orEmpty()) }
    var album by rememberSaveable { mutableStateOf(cAlbum.orEmpty()) }
    var albumArtist by rememberSaveable { mutableStateOf(cAlbumArtist.orEmpty()) }
    var genre by rememberSaveable { mutableStateOf(cGenre.orEmpty()) }
    var year by rememberSaveable { mutableStateOf(cYear.orEmpty()) }
    var track by rememberSaveable { mutableStateOf((first.track % 1000).takeIf { it > 0 }?.toString().orEmpty()) }
    // Fields explicitly cleared with ✕ (matters when the songs had mixed values).
    var cleared by remember { mutableStateOf(setOf<String>()) }
    var renumber by rememberSaveable { mutableStateOf(false) }
    var writeToFile by rememberSaveable { mutableStateOf(true) }

    // Online lookup
    var lookupOpen by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var lookupError by remember { mutableStateOf<String?>(null) }
    var matches by remember { mutableStateOf<List<LookupMatch>>(emptyList()) }
    var artwork by remember { mutableStateOf<LookupMatch?>(null) }
    val scope = rememberCoroutineScope()

    fun runLookup() {
        lookupOpen = true
        searching = true
        lookupError = null
        matches = emptyList()
        // If the tags are bare, try "Artist - Title" from the title text.
        val (t, a) = if (artist.isBlank() && title.contains(" - ")) {
            title.substringAfter(" - ") to title.substringBefore(" - ")
        } else title to artist
        scope.launch {
            try {
                matches = MusicBrainz.search(t, a)
                    .sortedBy { m -> if (first.durationMs > 0 && m.durationMs > 0) abs(m.durationMs - first.durationMs) / 4000 else 5 }
                if (matches.isEmpty()) lookupError = "No matches. Try fixing the title or artist above, then search again."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lookupError = e.message ?: "Lookup failed. Check your connection."
            } finally {
                searching = false
            }
        }
    }

    fun apply(m: LookupMatch) {
        title = m.title
        artist = m.artist
        album = m.album
        albumArtist = m.albumArtist
        if (m.year > 0) year = m.year.toString()
        if (m.track > 0) track = (m.track % 1000).toString()
        cleared = emptySet()
        artwork = m
        lookupOpen = false
    }

    /** New text value; "" means clear; null means keep. */
    fun field(key: String, new: String, old: String?): String? {
        val v = new.trim()
        return when {
            v.isNotEmpty() -> v.takeIf { it != old }
            key in cleared -> ""
            !old.isNullOrBlank() -> "" // had a value, now emptied
            else -> null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tac.Panel,
        title = {
            Text(
                if (single) "EDIT TRACK" else "EDIT ${songs.size} TRACKS",
                style = MaterialTheme.typography.labelLarge,
                color = Tac.Green,
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (single) {
                    TacOutlinedButton(onClick = { runLookup() }, enabled = !searching, modifier = Modifier.fillMaxWidth()) {
                        Text(if (lookupOpen) "SEARCH AGAIN" else "AUTO-FILL ONLINE")
                    }
                    if (lookupOpen) {
                        LookupPanel(first, searching, lookupError, matches, onPick = ::apply, onClose = { lookupOpen = false })
                    }
                    artwork?.let { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CoverThumb(m, 44)
                            Spacer(Modifier.width(10.dp))
                            Text("New artwork will be saved", style = MaterialTheme.typography.bodySmall, color = Tac.Green, modifier = Modifier.weight(1f))
                            IconButton(onClick = { artwork = null }) {
                                Icon(Icons.Rounded.Close, "Don't change artwork", tint = Tac.GreenMid, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Field("Title", title, mixed = false, clearable = false) { title = it }
                }
                Field("Artist", artist, mixed = cArtist == null, clearable = false) { artist = it }
                Field("Album", album, mixed = cAlbum == null, cleared = "album" in cleared,
                    onClear = { album = ""; cleared = cleared + "album" }) { album = it; cleared = cleared - "album" }
                Field("Album artist", albumArtist, mixed = cAlbumArtist == null, cleared = "albumArtist" in cleared,
                    onClear = { albumArtist = ""; cleared = cleared + "albumArtist" }) { albumArtist = it; cleared = cleared - "albumArtist" }
                Field("Genre", genre, mixed = cGenre == null, cleared = "genre" in cleared,
                    onClear = { genre = ""; cleared = cleared + "genre" }) { genre = it; cleared = cleared - "genre" }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(
                        "Year", year, mixed = cYear == null, modifier = Modifier.weight(1f), number = true,
                        cleared = "year" in cleared, onClear = { year = ""; cleared = cleared + "year" },
                    ) { year = it.filter(Char::isDigit).take(4); cleared = cleared - "year" }
                    if (single) {
                        Field("Track", track, mixed = false, modifier = Modifier.weight(1f), number = true, clearable = false) {
                            track = it.filter(Char::isDigit).take(3)
                        }
                    }
                }
                if (!single) {
                    Option("Number tracks 1–${songs.size} in the order you picked them", renumber) { renumber = it }
                }
                Option("Also write into the files", writeToFile) { writeToFile = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val yearValue = field("year", year, cYear)
                val edit = SongEdit(
                    title = if (single) title.trim().takeIf { it.isNotEmpty() && it != first.title } else null,
                    artist = artist.trim().takeIf { it.isNotEmpty() && it != cArtist },
                    album = field("album", album, cAlbum),
                    albumArtist = field("albumArtist", albumArtist, cAlbumArtist),
                    genre = field("genre", genre, cGenre),
                    year = yearValue?.let { it.toIntOrNull() ?: 0 },
                    track = if (single) {
                        track.toIntOrNull()?.takeIf { it > 0 && it != first.track % 1000 }
                            ?.let { (first.track / 1000) * 1000 + it } // keep the disc number
                    } else null,
                )
                onSave(edit, writeToFile, renumber && !single, if (single) artwork else null)
            }) { Text("SAVE", color = Tac.Bright) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = Tac.GreenMid) } },
    )
}

@Composable
private fun LookupPanel(
    song: Song,
    searching: Boolean,
    error: String?,
    matches: List<LookupMatch>,
    onPick: (LookupMatch) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Tac.GreenDim, MaterialTheme.shapes.extraSmall)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("MATCHES", style = MaterialTheme.typography.labelSmall, color = Tac.GreenMid, modifier = Modifier.weight(1f))
            if (song.durationMs > 0) {
                Text("YOURS ${formatTime(song.durationMs)}", style = MaterialTheme.typography.labelSmall, color = Tac.GreenDim)
            }
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.Close, "Close matches", tint = Tac.GreenMid, modifier = Modifier.size(14.dp))
            }
        }
        if (searching) LinearProgressIndicator(Modifier.fillMaxWidth().height(1.dp), color = Tac.Bright, trackColor = Tac.GreenFaint)
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Tac.Amber) }
        matches.forEach { m ->
            val close = song.durationMs > 0 && m.durationMs > 0 && abs(m.durationMs - song.durationMs) <= 3000
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(m) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverThumb(m, 40)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.title, style = MaterialTheme.typography.bodySmall, color = Tac.Green, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(m.artist, m.album, m.year.takeIf { it > 0 }?.toString()).filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = Tac.GreenMid,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (m.durationMs > 0) {
                    Text(
                        formatTime(m.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (close) Tac.Bright else Tac.GreenDim,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

/** Small cover preview fetched from the Cover Art Archive. */
@Composable
private fun CoverThumb(m: LookupMatch, sizeDp: Int) {
    val image by produceState<ImageBitmap?>(null, m.releaseId) {
        value = runCatching {
            MusicBrainz.cover(m, 250)?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        }.getOrNull()
    }
    Box(
        Modifier
            .size(sizeDp.dp)
            .background(Tac.Black)
            .border(1.dp, Tac.GreenFaint),
        contentAlignment = Alignment.Center,
    ) {
        val img = image
        if (img != null) Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.size(sizeDp.dp))
        else Text("—", style = MaterialTheme.typography.labelSmall, color = Tac.GreenDim)
    }
}

@Composable
private fun Option(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = Tac.GreenMid, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        TacSwitch(checked, onChange)
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    mixed: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    number: Boolean = false,
    clearable: Boolean = true,
    cleared: Boolean = false,
    onClear: () -> Unit = {},
    onChange: (String) -> Unit,
) {
    val hint = when {
        cleared -> "will be cleared"
        mixed -> "mixed — leave blank to keep"
        else -> null
    }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label.uppercase(), style = MaterialTheme.typography.labelSmall) },
        placeholder = hint?.let { h ->
            { Text(h, style = MaterialTheme.typography.bodySmall, color = if (cleared) Tac.Amber else Tac.GreenDim) }
        },
        trailingIcon = if (clearable && (value.isNotEmpty() || (mixed && !cleared))) {
            {
                IconButton(onClick = onClear) {
                    Icon(Icons.Rounded.Close, "Clear $label", tint = Tac.GreenDim, modifier = Modifier.size(16.dp))
                }
            }
        } else null,
        singleLine = true,
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = MaterialTheme.shapes.extraSmall,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Tac.Bright.copy(alpha = 0.6f),
            unfocusedBorderColor = if (cleared) Tac.Amber.copy(alpha = 0.5f) else Tac.GreenDim,
            focusedLabelColor = Tac.Green,
            unfocusedLabelColor = Tac.GreenMid,
            cursorColor = Tac.Bright,
            focusedTextColor = Tac.Green,
            unfocusedTextColor = Tac.Green,
        ),
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number)
        else KeyboardOptions(capitalization = KeyboardCapitalization.Words),
    )
}
