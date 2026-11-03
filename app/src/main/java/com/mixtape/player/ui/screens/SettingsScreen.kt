package com.mixtape.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mixtape.player.data.Library
import com.mixtape.player.ui.components.Glyph
import com.mixtape.player.ui.components.Glyphs
import com.mixtape.player.ui.components.TacOutlinedButton
import com.mixtape.player.ui.components.TacPanel
import com.mixtape.player.ui.components.TacSwitch
import com.mixtape.player.ui.theme.Tac

class SettingsActions(
    val onBoostDb: (Int) -> Unit,
    val onHiRes: (Boolean) -> Unit,
    val onUnhideAll: () -> Unit,
    val onCrossfadeSeconds: (Int) -> Unit,
    val onSmartShuffle: (Boolean) -> Unit,
    val onBootAnim: (Boolean) -> Unit,
    val onScanlines: (Boolean) -> Unit,
    val onAddFiles: () -> Unit,
    val onAddFolder: () -> Unit,
    val onRescan: () -> Unit,
)

@Composable
fun SettingsScreen(
    library: Library,
    crossfadeMs: Int,
    smartShuffle: Boolean,
    scanlines: Boolean,
    hiddenCount: Int,
    busy: Boolean,
    boostDb: Int,
    hiRes: Boolean,
    bootAnim: Boolean,
    actions: SettingsActions,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(30.dp),
        ) {
            Section("Playback", Glyphs.Wave) {
                CrossfadeControl(crossfadeMs, actions.onCrossfadeSeconds)
                BoostControl(boostDb, actions.onBoostDb)
                Hint("Boost raises quiet tracks through a limiter, so loud parts don't distort. Go easy on headphones.")
                SwitchRow(
                    "Hi-res output",
                    "Play 24-bit files at full resolution instead of 16-bit. The visualizer pauses on those files. Applies after restarting NEXUS.",
                    hiRes,
                    actions.onHiRes,
                )
                SwitchRow("Smart shuffle", "Avoid playing the same artist twice in a row", smartShuffle, actions.onSmartShuffle)
            }

            Section("Display", Glyphs.Screen) {
                SwitchRow("Startup animation", "Boot sequence when NEXUS opens (tap to skip it)", bootAnim, actions.onBootAnim)
                SwitchRow("CRT scanlines", "Faint lines across the screen and a dark vignette", scanlines, actions.onScanlines)
            }

            Section("Library", Glyphs.Disc) {
                Text(
                    "${library.songs.size} songs · ${library.albums.size} albums · ${library.artists.size} artists",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tac.Green,
                )
                Hint("Music anywhere on your phone is found and sorted automatically. Adding files copies them into Music/Nexus.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TacOutlinedButton(onClick = actions.onAddFiles, enabled = !busy) { Text("ADD FILES") }
                    TacOutlinedButton(onClick = actions.onAddFolder, enabled = !busy) { Text("ADD FOLDER") }
                }
                TacOutlinedButton(onClick = actions.onRescan) { Text("RESCAN") }
                if (hiddenCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (hiddenCount == 1) "1 hidden song" else "$hiddenCount hidden songs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Tac.GreenMid,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = actions.onUnhideAll) { Text("SHOW ALL", color = Tac.Green) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, glyph: Glyph, content: @Composable () -> Unit) {
    TacPanel(title, glyph = glyph) { content() }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Tac.GreenMid)
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Tac.Green)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Tac.GreenMid)
        }
        Spacer(Modifier.width(16.dp))
        TacSwitch(checked = checked, onCheckedChange = onChange)
    }
}
