package com.mixtape.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mixtape.player.data.AccountsState
import com.mixtape.player.data.Library
import com.mixtape.player.integrations.SoundCloudClient
import com.mixtape.player.integrations.SpotifyClient

class SettingsActions(
    val onImportFile: () -> Unit,
    val onPasteList: (name: String, text: String) -> Unit,
    val onUnhideAll: () -> Unit,
    val onCrossfadeSeconds: (Int) -> Unit,
    val onSmartShuffle: (Boolean) -> Unit,
    val onAddFiles: () -> Unit,
    val onAddFolder: () -> Unit,
    val onRescan: () -> Unit,
    val onSpotifyClientId: (String) -> Unit,
    val onSpotifyConnect: () -> Unit,
    val onSpotifyDisconnect: () -> Unit,
    val onSpotifyImport: () -> Unit,
    val onYouTubeMusicOnly: (Boolean) -> Unit,
    val onYouTubeImport: () -> Unit,
    val onSoundCloudClientId: (String) -> Unit,
    val onSoundCloudSecret: (String) -> Unit,
    val onSoundCloudConnect: () -> Unit,
    val onSoundCloudDisconnect: () -> Unit,
    val onSoundCloudImport: () -> Unit,
)

@Composable
fun SettingsScreen(
    library: Library,
    crossfadeMs: Int,
    smartShuffle: Boolean,
    accounts: AccountsState,
    hiddenCount: Int,
    busy: Boolean,
    actions: SettingsActions,
) {
    var pasting by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Section("Playback") {
            CrossfadeControl(crossfadeMs, actions.onCrossfadeSeconds)
            SwitchRow(
                "Smart shuffle",
                "Avoid playing the same artist twice in a row",
                smartShuffle,
                actions.onSmartShuffle,
            )
        }

        Section("Library") {
            Text(
                "${library.songs.size} songs · ${library.albums.size} albums · ${library.artists.size} artists",
                style = MaterialTheme.typography.bodyMedium,
            )
            Hint("Music anywhere on your phone is found and sorted automatically. Adding files copies them into Music/Mixtape.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = actions.onAddFiles) { Text("Add files") }
                FilledTonalButton(onClick = actions.onAddFolder) { Text("Add a folder") }
            }
            OutlinedButton(onClick = actions.onRescan) { Text("Rescan library") }
            if (hiddenCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (hiddenCount == 1) "1 hidden song" else "$hiddenCount hidden songs",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = actions.onUnhideAll) { Text("Show all again") }
                }
            }
        }

        Section("Import likes for free") {
            Hint("No developer account or subscription needed. Import a file you've exported, or paste a list. Songs you already have are matched automatically.")
            Hint("• Spotify: spotify.com → Account → Privacy settings → Download your data → \"Account data\". You'll get an email with a .zip in a few days; import the zip here.")
            Hint("• YouTube Music: takeout.google.com → select only \"YouTube and YouTube Music\" → import \"music library songs.csv\".")
            Hint("• Any service: export a CSV with a free tool like Exportify or TuneMyMusic, or paste lines like \"Artist - Title\".")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = actions.onImportFile, enabled = !busy) { Text("Import a file") }
                FilledTonalButton(onClick = { pasting = true }) { Text("Paste a list") }
            }
        }

        Section("Spotify") {
            Hint("Imports your Liked Songs as a playlist matched to music on your phone. Spotify songs themselves can't be downloaded.")
            CredentialField("Client ID", accounts.spotifyClientId, actions.onSpotifyClientId)
            Hint("Redirect URI to register: ${SpotifyClient.REDIRECT_URI}", mono = true)
            ConnectRow(
                connected = accounts.spotifyConnected,
                canConnect = accounts.spotifyClientId.isNotBlank(),
                busy = busy,
                onConnect = actions.onSpotifyConnect,
                onDisconnect = actions.onSpotifyDisconnect,
                onImport = actions.onSpotifyImport,
            )
        }

        Section("YouTube") {
            Hint("Imports videos you've liked and matches them to your files. Needs a one-time Google Cloud setup (see README).")
            SwitchRow("Music videos only", "Skip liked videos that aren't music", accounts.youTubeMusicOnly, actions.onYouTubeMusicOnly)
            Button(onClick = actions.onYouTubeImport, enabled = !busy) { Text("Sign in & import likes") }
        }

        Section("SoundCloud") {
            Hint("Imports your likes. Tracks whose artist allows downloads can be saved to your phone from the playlist.")
            CredentialField("Client ID", accounts.soundCloudClientId, actions.onSoundCloudClientId)
            CredentialField("Client secret", accounts.soundCloudClientSecret, actions.onSoundCloudSecret, secret = true)
            Hint("Redirect URI to register: ${SoundCloudClient.REDIRECT_URI}", mono = true)
            ConnectRow(
                connected = accounts.soundCloudConnected,
                canConnect = accounts.soundCloudClientId.isNotBlank() && accounts.soundCloudClientSecret.isNotBlank(),
                busy = busy,
                onConnect = actions.onSoundCloudConnect,
                onDisconnect = actions.onSoundCloudDisconnect,
                onImport = actions.onSoundCloudImport,
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (pasting) {
        PasteListDialog(
            onDismiss = { pasting = false },
            onImport = { name, text -> pasting = false; actions.onPasteList(name, text) },
        )
    }
}

@Composable
private fun PasteListDialog(onDismiss: () -> Unit, onImport: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste a song list") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Playlist name") }, singleLine = true)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("One per line: Artist - Title") },
                    minLines = 6,
                    maxLines = 12,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onImport(name, text) }, enabled = text.isNotBlank()) { Text("Import") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun Hint(text: String, mono: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = if (mono) FontFamily.Monospace else null,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CredentialField(label: String, value: String, onChange: (String) -> Unit, secret: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ConnectRow(
    connected: Boolean,
    canConnect: Boolean,
    busy: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onImport: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (connected) {
            Button(onClick = onImport, enabled = !busy) { Text("Import likes") }
            OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
        } else {
            Button(onClick = onConnect, enabled = canConnect && !busy) { Text("Connect") }
        }
    }
}
