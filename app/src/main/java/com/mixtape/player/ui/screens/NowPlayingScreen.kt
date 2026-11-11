package com.mixtape.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mixtape.player.data.Settings
import com.mixtape.player.data.Song
import com.mixtape.player.playback.AudioAnalyzer
import com.mixtape.player.playback.PlaybackState
import com.mixtape.player.playback.RepeatMode
import com.mixtape.player.ui.components.AlbumArt
import com.mixtape.player.ui.components.AudioVisualizer
import com.mixtape.player.ui.components.TacButtonShape
import com.mixtape.player.ui.components.TacLabel
import com.mixtape.player.ui.components.VisMode
import com.mixtape.player.ui.components.cornerBrackets
import com.mixtape.player.ui.components.swipeVertical
import com.mixtape.player.ui.components.formatTime
import com.mixtape.player.ui.theme.Tac

class NowPlayingActions(
    val onClose: () -> Unit,
    val onTogglePlay: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onToggleShuffle: () -> Unit,
    val onCycleRepeat: () -> Unit,
    val onCrossfadeSeconds: (Int) -> Unit,
    val onBoostDb: (Int) -> Unit,
    val onJumpTo: (Int) -> Unit,
    val onRemoveFromQueue: (Int) -> Unit,
    val onMoveInQueue: (Int, Int) -> Unit,
    val onOpenArtist: (Song) -> Unit = {},
    val onOpenAlbum: (Song) -> Unit = {},
)

/** Which Up-next row is being dragged, and how far it's moved from its slot. */
@Stable
private class QueueDrag {
    var index by mutableStateOf<Int?>(null)
    var offset by mutableFloatStateOf(0f)
}

private val QueueRowHeight = 56.dp

@Composable
fun NowPlayingScreen(
    state: PlaybackState,
    analyzer: AudioAnalyzer,
    crossfadeMs: Int,
    boostDb: Int,
    actions: NowPlayingActions,
) {
    // 0 = cover art, then each visualizer mode. Tap the frame to cycle.
    var view by rememberSaveable { mutableIntStateOf(0) }
    val drag = remember { QueueDrag() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val landscape = maxWidth > maxHeight
        val paneMax = maxHeight
        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxHeight()
                        .widthIn(max = paneMax)
                        .padding(start = 8.dp, top = 4.dp, bottom = 12.dp),
                ) {
                    TopStrip(state, actions.onClose)
                    ArtFrame(
                        state, analyzer, view, { view = it },
                        Modifier
                            .swipeVertical(down = true, threshold = 80.dp, onSwipe = actions.onClose)
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                            .fillMaxHeight()
                            .aspectRatio(1f, matchHeightConstraintsFirst = true)
                            .align(Alignment.CenterHorizontally),
                    )
                }
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Tac.GreenFaint)
                )
                LazyColumn(Modifier.weight(1f).fillMaxHeight()) {
                    item { Spacer(Modifier.height(16.dp)) }
                    item { InfoBlock(state, crossfadeMs, analyzer, view, actions) }
                    item { Transport(state, actions) }
                    item { Tuning(crossfadeMs, boostDb, actions) }
                    queueItems(state, actions, drag)
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                item { TopStrip(state, actions.onClose) }
                item {
                    ArtFrame(
                        state, analyzer, view, { view = it },
                        Modifier
                            .swipeVertical(down = true, threshold = 80.dp, onSwipe = actions.onClose)
                            .padding(horizontal = 40.dp, vertical = 20.dp)
                            .widthIn(max = 420.dp)
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    )
                }
                item { InfoBlock(state, crossfadeMs, analyzer, view, actions) }
                item { Transport(state, actions) }
                item { Tuning(crossfadeMs, boostDb, actions) }
                queueItems(state, actions, drag)
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun TopStrip(state: PlaybackState, onClose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .swipeVertical(down = true, threshold = 60.dp, onSwipe = onClose)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Close player", tint = Tac.GreenMid) }
        Text(
            "NOW PLAYING",
            style = MaterialTheme.typography.labelMedium,
            color = Tac.GreenMid,
            modifier = Modifier.weight(1f),
        )
        Text(
            when {
                state.crossfading -> "XFADE"
                state.isPlaying -> "LIVE"
                else -> "HOLD"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (state.isPlaying || state.crossfading) Tac.Amber else Tac.GreenDim,
            modifier = Modifier.padding(end = 16.dp),
        )
    }
}

@Composable
private fun ArtFrame(
    state: PlaybackState,
    analyzer: AudioAnalyzer,
    view: Int,
    onView: (Int) -> Unit,
    modifier: Modifier,
) {
    val visMode = VisMode.entries.getOrNull(view - 1)
    Box(
        modifier
            .cornerBrackets(length = 12.dp)
            .clickable { onView((view + 1) % (VisMode.entries.size + 1)) }
            .padding(8.dp),
    ) {
        if (visMode == null) {
            AlbumArt(state.current, Modifier.fillMaxSize(), sizePx = 1000, shape = MaterialTheme.shapes.extraSmall)
        } else {
            AudioVisualizer(
                analyzer,
                visMode,
                Modifier
                    .fillMaxSize()
                    .border(1.dp, Tac.GreenFaint)
                    .padding(10.dp)
                    .graphicsLayer(),
            )
        }
        Text(
            (visMode?.label ?: "COVER") + "  ›",
            style = MaterialTheme.typography.labelSmall,
            color = Tac.GreenMid,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(Color(0xCC000000))
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun InfoBlock(state: PlaybackState, crossfadeMs: Int, analyzer: AudioAnalyzer, view: Int, actions: NowPlayingActions) {
    val song = state.current
    Column(Modifier.padding(horizontal = 28.dp).fillMaxWidth()) {
        Text(
            song?.title ?: "No signal",
            style = MaterialTheme.typography.headlineSmall,
            color = Tac.Green,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        // Artist and album are links: tap to open their pages.
        if (song != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tac.Green,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable { actions.onOpenArtist(song) }
                        .padding(vertical = 4.dp),
                )
                if (song.album.isNotBlank()) {
                    Text("  ·  ", style = MaterialTheme.typography.bodyMedium, color = Tac.GreenDim)
                    Text(
                        song.album,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Tac.GreenMid,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable { actions.onOpenAlbum(song) }
                            .padding(vertical = 4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            listOf(
                "TRK %03d/%03d".format(state.index + 1, state.queue.size),
                "SHF " + if (state.shuffle) "ON" else "OFF",
                "RPT " + state.repeat.name,
                "XF %02dS".format(crossfadeMs / 1000),
            ).joinToString("   "),
            style = MaterialTheme.typography.labelSmall,
            color = Tac.GreenDim,
            maxLines = 1,
        )
        Spacer(Modifier.height(10.dp))
        // A slim live trace (the spectrum when the big frame isn't already showing it).
        AudioVisualizer(
            analyzer,
            if (view == 1) VisMode.Scope else VisMode.Spectrum,
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .graphicsLayer { alpha = 0.7f },
        )
    }
}

@Composable
private fun Transport(state: PlaybackState, actions: NowPlayingActions) {
    Column(Modifier.padding(horizontal = 28.dp)) {
        SeekBarControl(state, actions.onSeek)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Key(Icons.Rounded.Shuffle, if (state.shuffle) "Shuffle on" else "Shuffle off", lit = state.shuffle, onClick = actions.onToggleShuffle)
            Key(Icons.Rounded.SkipPrevious, "Previous", onClick = actions.onPrevious)
            Key(
                if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (state.isPlaying) "Pause" else "Play",
                big = true,
                onClick = actions.onTogglePlay,
            )
            Key(Icons.Rounded.SkipNext, "Next", onClick = actions.onNext)
            Key(
                if (state.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                when (state.repeat) {
                    RepeatMode.OFF -> "Repeat off"
                    RepeatMode.ALL -> "Repeat all"
                    RepeatMode.ONE -> "Repeat one"
                },
                lit = state.repeat != RepeatMode.OFF,
                onClick = actions.onCycleRepeat,
            )
        }
    }
}

@Composable
private fun Tuning(crossfadeMs: Int, boostDb: Int, actions: NowPlayingActions) {
    Column(Modifier.padding(horizontal = 28.dp, vertical = 4.dp)) {
        CrossfadeControl(crossfadeMs, actions.onCrossfadeSeconds)
        BoostControl(boostDb, actions.onBoostDb)
    }
}

private fun LazyListScope.queueItems(state: PlaybackState, actions: NowPlayingActions, drag: QueueDrag) {
    val upNext = state.upNext.take(200)
    if (upNext.isEmpty()) return
    item {
        TacLabel("Up next", Modifier.padding(start = 28.dp, end = 28.dp, top = 22.dp, bottom = 6.dp), count = state.upNext.size)
    }
    // Stable keys (song id + which copy) so a row keeps its identity while it's dragged.
    val seen = HashMap<Long, Int>()
    val keys = upNext.map { s -> val n = (seen[s.id] ?: 0) + 1; seen[s.id] = n; "${s.id}#$n" }
    val first = state.index + 1
    val last = state.index + upNext.size
    itemsIndexed(upNext, key = { i, _ -> keys[i] }) { i, s ->
        val queueIndex = first + i
        val dragging = drag.index == queueIndex
        QueueRow(
            song = s,
            number = i + 1,
            queueIndex = queueIndex,
            firstIndex = first,
            lastIndex = last,
            drag = drag,
            actions = actions,
            modifier = (if (dragging) Modifier else Modifier.animateItem())
                .zIndex(if (dragging) 1f else 0f)
                .graphicsLayer { translationY = if (dragging) drag.offset else 0f }
                .background(if (dragging) Tac.PanelHi else Color.Transparent),
        )
    }
}

@Composable
private fun QueueRow(
    song: Song,
    number: Int,
    queueIndex: Int,
    firstIndex: Int,
    lastIndex: Int,
    drag: QueueDrag,
    actions: NowPlayingActions,
    modifier: Modifier,
) {
    val rowPx = with(LocalDensity.current) { QueueRowHeight.toPx() }
    val current by rememberUpdatedState(queueIndex)
    val first by rememberUpdatedState(firstIndex)
    val last by rememberUpdatedState(lastIndex)
    val move by rememberUpdatedState(actions.onMoveInQueue)
    Row(
        modifier
            .fillMaxWidth()
            .height(QueueRowHeight)
            .clickable { actions.onJumpTo(queueIndex) }
            .padding(start = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Drag handle
        Box(
            Modifier
                .size(width = 36.dp, height = QueueRowHeight)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { drag.index = current; drag.offset = 0f },
                        onDragEnd = { drag.index = null; drag.offset = 0f },
                        onDragCancel = { drag.index = null; drag.offset = 0f },
                        onDrag = { change, amount ->
                            change.consume()
                            val at = drag.index ?: return@detectDragGestures
                            drag.offset += amount.y
                            if (drag.offset > rowPx / 2 && at < last) {
                                move(at, at + 1)
                                drag.index = at + 1
                                drag.offset -= rowPx
                            } else if (drag.offset < -rowPx / 2 && at > first) {
                                move(at, at - 1)
                                drag.index = at - 1
                                drag.offset += rowPx
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.DragHandle, "Drag to reorder", tint = Tac.GreenDim, modifier = Modifier.size(18.dp))
        }
        Text("%02d".format(number), style = MaterialTheme.typography.labelSmall, color = Tac.GreenDim)
        Spacer(Modifier.width(12.dp))
        AlbumArt(song, Modifier.size(34.dp), sizePx = 102, shape = MaterialTheme.shapes.extraSmall)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyMedium, color = Tac.Green, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.bodySmall, color = Tac.GreenMid, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { actions.onRemoveFromQueue(queueIndex) }) {
            Icon(Icons.Rounded.Close, "Remove from queue", tint = Tac.GreenDim, modifier = Modifier.size(16.dp))
        }
    }
}

/** A plain control key; the play key gets a hairline square. */
@Composable
private fun Key(
    icon: ImageVector,
    description: String,
    lit: Boolean = false,
    big: Boolean = false,
    onClick: () -> Unit,
) {
    val size: Dp = if (big) 64.dp else 48.dp
    Box(
        Modifier
            .size(size)
            .then(if (big) Modifier.border(1.dp, Tac.Bright.copy(alpha = 0.55f), TacButtonShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            description,
            tint = when {
                big -> Tac.Bright
                lit -> Tac.Green
                else -> Tac.GreenMid
            },
            modifier = Modifier.size(if (big) 30.dp else 22.dp),
        )
        if (lit && !big) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .size(3.dp)
                    .background(Tac.Bright)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekBarControl(state: PlaybackState, onSeek: (Long) -> Unit) {
    val durationMs = state.durationMs
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = if (durationMs > 0) (state.positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shown = dragging ?: fraction
    Slider(
        value = shown,
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            dragging?.let { onSeek((it * durationMs).toLong()) }
            dragging = null
        },
        enabled = durationMs > 0,
        thumb = {
            Box(
                Modifier
                    .size(width = 3.dp, height = 14.dp)
                    .background(Tac.Bright)
            )
        },
        track = { st ->
            Box(Modifier.fillMaxWidth().height(14.dp), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Tac.GreenDim))
                Box(Modifier.fillMaxWidth(st.value).height(1.dp).background(Tac.Green))
            }
        },
        modifier = Modifier.height(24.dp),
    )
    Row(Modifier.fillMaxWidth()) {
        Text(formatTime((shown * durationMs).toLong()), style = MaterialTheme.typography.labelMedium, color = Tac.Green)
        Spacer(Modifier.weight(1f))
        Text("-" + formatTime(((1 - shown) * durationMs).toLong()), style = MaterialTheme.typography.labelMedium, color = Tac.GreenDim)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TacSlider(value: Float, onValueChange: (Float) -> Unit, onFinished: () -> Unit, max: Int) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onFinished,
        valueRange = 0f..max.toFloat(),
        steps = max - 1,
        colors = SliderDefaults.colors(
            thumbColor = Tac.Green,
            activeTrackColor = Tac.GreenMid,
            inactiveTrackColor = Tac.GreenFaint,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
        thumb = {
            Box(
                Modifier
                    .size(width = 3.dp, height = 16.dp)
                    .background(Tac.Green)
            )
        },
        track = { st ->
            val f = (st.value - st.valueRange.start) / (st.valueRange.endInclusive - st.valueRange.start)
            Box(Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Tac.GreenFaint))
                Box(Modifier.fillMaxWidth(f).height(1.dp).background(Tac.GreenMid))
            }
        },
        modifier = Modifier.height(28.dp),
    )
}

/** 0–12 s crossfade. */
@Composable
fun CrossfadeControl(crossfadeMs: Int, onSeconds: (Int) -> Unit, modifier: Modifier = Modifier) {
    val max = Settings.MAX_CROSSFADE_MS / 1000
    var local by remember(crossfadeMs) { mutableFloatStateOf(crossfadeMs / 1000f) }
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CROSSFADE", style = MaterialTheme.typography.labelMedium, color = Tac.GreenMid, modifier = Modifier.weight(1f))
            Text(
                if (local.toInt() == 0) "OFF" else "%d S".format(local.toInt()),
                style = MaterialTheme.typography.labelMedium,
                color = if (local.toInt() == 0) Tac.GreenDim else Tac.Green,
            )
        }
        TacSlider(local, { local = it }, { onSeconds(local.toInt()) }, max)
    }
}

/** 0–10 dB boost through a limiter. */
@Composable
fun BoostControl(boostDb: Int, onDb: (Int) -> Unit, modifier: Modifier = Modifier) {
    val max = Settings.MAX_BOOST_DB
    var local by remember(boostDb) { mutableFloatStateOf(boostDb.toFloat()) }
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("VOLUME BOOST", style = MaterialTheme.typography.labelMedium, color = Tac.GreenMid, modifier = Modifier.weight(1f))
            Text(
                if (local.toInt() == 0) "OFF" else "+%d DB".format(local.toInt()),
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    local.toInt() == 0 -> Tac.GreenDim
                    local.toInt() >= 7 -> Tac.Amber
                    else -> Tac.Green
                },
            )
        }
        TacSlider(local, { local = it }, { onDb(local.toInt()) }, max)
    }
}
