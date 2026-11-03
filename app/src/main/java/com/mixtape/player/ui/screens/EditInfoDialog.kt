package com.mixtape.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mixtape.player.data.Song
import com.mixtape.player.data.SongEdit

/**
 * Edit a song's info, or (with several songs) the shared album info.
 * Only fields you actually change are saved.
 */
@Composable
fun EditInfoDialog(
    songs: List<Song>,
    onDismiss: () -> Unit,
    onSave: (SongEdit, writeToFile: Boolean) -> Unit,
) {
    val first = songs.first()
    val single = songs.size == 1
    var title by rememberSaveable { mutableStateOf(first.title) }
    var artist by rememberSaveable { mutableStateOf(first.artist) }
    var album by rememberSaveable { mutableStateOf(first.album) }
    var albumArtist by rememberSaveable { mutableStateOf(first.albumArtist.ifBlank { first.artist.takeIf { !single }.orEmpty() }) }
    var genre by rememberSaveable { mutableStateOf(first.genre.takeUnless { it.startsWith("Unknown") }.orEmpty()) }
    var year by rememberSaveable { mutableStateOf(first.year.takeIf { it > 0 }?.toString().orEmpty()) }
    var track by rememberSaveable { mutableStateOf((first.track % 1000).takeIf { it > 0 }?.toString().orEmpty()) }
    var writeToFile by rememberSaveable { mutableStateOf(true) }

    fun changed(new: String, old: String) = new.trim().takeIf { it.isNotEmpty() && it != old }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (single) "Edit song info" else "Edit album (${songs.size} songs)") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (single) {
                    Field("Title", title) { title = it }
                    Field("Artist", artist) { artist = it }
                }
                Field("Album", album) { album = it }
                Field("Album artist", albumArtist) { albumArtist = it }
                Field("Genre", genre) { genre = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Year", year, Modifier.weight(1f), number = true) { year = it.filter(Char::isDigit).take(4) }
                    if (single) {
                        Field("Track #", track, Modifier.weight(1f), number = true) { track = it.filter(Char::isDigit).take(3) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = writeToFile, onCheckedChange = { writeToFile = it })
                    Text(
                        "Also save into the file, so other apps see it",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val edit = SongEdit(
                    title = if (single) changed(title, first.title) else null,
                    artist = if (single) changed(artist, first.artist) else null,
                    album = changed(album, first.album),
                    albumArtist = changed(albumArtist, first.albumArtist),
                    genre = changed(genre, first.genre),
                    year = year.toIntOrNull()?.takeIf { it != first.year && it > 0 },
                    track = if (single) {
                        track.toIntOrNull()?.takeIf { it > 0 && it != first.track % 1000 }
                            ?.let { (first.track / 1000) * 1000 + it } // keep the disc number
                    } else null,
                )
                onSave(edit, writeToFile)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Field(
    label: String,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    number: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number)
        else KeyboardOptions(capitalization = KeyboardCapitalization.Words),
    )
}
