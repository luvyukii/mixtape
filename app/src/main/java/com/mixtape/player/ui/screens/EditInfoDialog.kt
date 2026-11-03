package com.mixtape.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.mixtape.player.ui.components.TacSwitch
import com.mixtape.player.ui.theme.Tac

/**
 * Edit one song, or many at once. With several songs, a field the songs disagree on starts
 * empty ("mixed") and is left alone unless you type something. Only changed fields are saved.
 */
@Composable
fun EditInfoDialog(
    songs: List<Song>,
    onDismiss: () -> Unit,
    onSave: (edit: SongEdit, writeToFile: Boolean, renumber: Boolean) -> Unit,
) {
    val single = songs.size == 1
    fun common(get: (Song) -> String): String? = songs.map(get).distinct().singleOrNull()

    val cArtist = common { it.artist }
    val cAlbum = common { it.album }
    val cAlbumArtist = common { it.albumArtist }
    val cGenre = common { it.genre.takeUnless { g -> g.startsWith("Unknown") }.orEmpty() }
    val cYear = common { it.year.takeIf { y -> y > 0 }?.toString().orEmpty() }

    var title by rememberSaveable { mutableStateOf(songs.first().title) }
    var artist by rememberSaveable { mutableStateOf(cArtist.orEmpty()) }
    var album by rememberSaveable { mutableStateOf(cAlbum.orEmpty()) }
    var albumArtist by rememberSaveable { mutableStateOf(cAlbumArtist.orEmpty()) }
    var genre by rememberSaveable { mutableStateOf(cGenre.orEmpty()) }
    var year by rememberSaveable { mutableStateOf(cYear.orEmpty()) }
    var track by rememberSaveable { mutableStateOf((songs.first().track % 1000).takeIf { it > 0 }?.toString().orEmpty()) }
    var renumber by rememberSaveable { mutableStateOf(false) }
    var writeToFile by rememberSaveable { mutableStateOf(true) }

    /** The new value if it was changed; blank means "keep" for mixed fields. */
    fun changed(new: String, old: String?) = new.trim().takeIf { it.isNotEmpty() && it != old }

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
                if (single) Field("Title", title, mixed = false) { title = it }
                Field("Artist", artist, mixed = cArtist == null) { artist = it }
                Field("Album", album, mixed = cAlbum == null) { album = it }
                Field("Album artist", albumArtist, mixed = cAlbumArtist == null) { albumArtist = it }
                Field("Genre", genre, mixed = cGenre == null) { genre = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Year", year, mixed = cYear == null, modifier = Modifier.weight(1f), number = true) {
                        year = it.filter(Char::isDigit).take(4)
                    }
                    if (single) {
                        Field("Track", track, mixed = false, modifier = Modifier.weight(1f), number = true) {
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
                val first = songs.first()
                val edit = SongEdit(
                    title = if (single) changed(title, first.title) else null,
                    artist = changed(artist, cArtist),
                    album = changed(album, cAlbum),
                    albumArtist = changed(albumArtist, cAlbumArtist),
                    genre = changed(genre, cGenre),
                    year = year.toIntOrNull()?.takeIf { it > 0 && it.toString() != cYear },
                    track = if (single) {
                        track.toIntOrNull()?.takeIf { it > 0 && it != first.track % 1000 }
                            ?.let { (first.track / 1000) * 1000 + it } // keep the disc number
                    } else null,
                )
                onSave(edit, writeToFile, renumber && !single)
            }) { Text("SAVE", color = Tac.Bright) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = Tac.GreenMid) } },
    )
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
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label.uppercase(), style = MaterialTheme.typography.labelSmall) },
        placeholder = if (mixed) ({ Text("mixed — leave blank to keep", style = MaterialTheme.typography.bodySmall, color = Tac.GreenDim) }) else null,
        singleLine = true,
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = MaterialTheme.shapes.extraSmall,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Tac.Bright.copy(alpha = 0.6f),
            unfocusedBorderColor = Tac.GreenDim,
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
