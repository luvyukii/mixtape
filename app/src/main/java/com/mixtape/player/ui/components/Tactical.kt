package com.mixtape.player.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mixtape.player.ui.theme.Tac
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.sin

val TacButtonShape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp)

/** Solid green command button. */
@Composable
fun TacButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = TacButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Tac.Green,
            contentColor = Tac.Black,
            disabledContainerColor = Tac.GreenFaint,
            disabledContentColor = Tac.GreenDim,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        content = content,
    )
}

/** Outlined secondary button. */
@Composable
fun TacOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = TacButtonShape,
        border = BorderStroke(1.dp, if (enabled) Tac.Green else Tac.GreenDim),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Tac.Green, disabledContentColor = Tac.GreenDim),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        content = content,
    )
}

/** Bordered panel with an inverted title tab, e.g. ▌PLAYBACK. */
@Composable
fun TacPanel(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .border(1.dp, Tac.GreenDim, MaterialTheme.shapes.medium)
            .background(Tac.Panel, MaterialTheme.shapes.medium)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                " ${title.uppercase()} ",
                color = Tac.Black,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Tac.Green).padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Tac.GreenDim)
            )
            if (trailing != null) {
                Text(
                    " $trailing ",
                    style = MaterialTheme.typography.labelSmall,
                    color = Tac.GreenMid,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/** Section label like "// ARTISTS ───────". */
@Composable
fun TacLabel(text: String, modifier: Modifier = Modifier, count: Int? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "// ${text.uppercase()}",
            style = MaterialTheme.typography.labelLarge,
            color = Tac.Green,
            fontWeight = FontWeight.Bold,
        )
        if (count != null) {
            Text(" [$count]", style = MaterialTheme.typography.labelMedium, color = Tac.GreenMid)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(Tac.GreenFaint)
        )
    }
}

/** CRT scanlines drawn over everything (purely visual; touches pass through). */
fun Modifier.scanlines(enabled: Boolean): Modifier = if (!enabled) this else drawWithContent {
    drawContent()
    val step = 3.dp.toPx()
    var y = 0f
    val line = Color(0x24000000)
    while (y < size.height) {
        drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
    // Soft darkening at the edges, like a tube screen.
    drawRect(
        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
            0f to Color(0x33000000), 0.08f to Color.Transparent, 0.92f to Color.Transparent, 1f to Color(0x33000000),
        ),
    )
}

/** Targeting-style corner brackets around something (album art, the player). */
fun Modifier.cornerBrackets(color: Color = Tac.Green, length: Dp = 14.dp, inset: Dp = 0.dp): Modifier =
    drawWithContent {
        drawContent()
        val l = length.toPx()
        val i = inset.toPx()
        val w = 2.dp.toPx()
        val r = size.width - i
        val b = size.height - i
        fun seg(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = w)
        seg(i, i, i + l, i); seg(i, i, i, i + l)
        seg(r, i, r - l, i); seg(r, i, r, i + l)
        seg(i, b, i + l, b); seg(i, b, i, b - l)
        seg(r, b, r - l, b); seg(r, b, r, b - l)
    }

/** Top status strip: unit name with a blinking cursor, status text and a live clock. */
@Composable
fun TacHeader(status: String, modifier: Modifier = Modifier, alert: Boolean = false) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(500)
        }
    }
    val cursorOn = now.nano < 500_000_000
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "NEXUS" + if (cursorOn) "_" else " ",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Tac.Green,
            )
            Text(
                "  // ${status.uppercase()}",
                style = MaterialTheme.typography.labelMedium,
                color = if (alert) Tac.Amber else Tac.GreenMid,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                now.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                style = MaterialTheme.typography.labelMedium,
                color = Tac.GreenMid,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Tac.GreenDim)
        )
    }
}

class TacTab(val label: String, val icon: ImageVector)

/** Bottom navigation: square cells, the active one inverted. */
@Composable
fun TacNavBar(tabs: List<TacTab>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(Tac.Black)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Tac.GreenDim)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tabs.forEachIndexed { i, tab ->
                val active = i == selected
                Column(
                    Modifier
                        .weight(1f)
                        .border(1.dp, if (active) Tac.Green else Tac.GreenFaint, TacButtonShape)
                        .background(if (active) Tac.Green else Tac.Black, TacButtonShape)
                        .clickable { onSelect(i) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(tab.icon, contentDescription = null, tint = if (active) Tac.Black else Tac.GreenMid, modifier = Modifier.size(20.dp))
                    Text(
                        tab.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) Tac.Black else Tac.GreenMid,
                        fontWeight = if (active) FontWeight.Bold else null,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * A decorative "signal monitor": bars that move while music plays and settle when paused.
 * It's an animation, not a real audio analyzer.
 */
@Composable
fun SignalBars(active: Boolean, modifier: Modifier = Modifier, bars: Int = 28) {
    val transition = rememberInfiniteTransition(label = "signal")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(400_000, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    Canvas(modifier) {
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (bars - 1)) / bars
        val cell = 3.dp.toPx()
        for (i in 0 until bars) {
            val level = if (!active) 0.08f else {
                val a = sin(t * 7f + i * 0.55f) * 0.5f + 0.5f
                val b = sin(t * 17f + i * 1.7f) * 0.5f + 0.5f
                (0.15f + 0.85f * (a * 0.6f + b * 0.4f) * (1f - abs(i - bars / 2f) / bars)).coerceIn(0.05f, 1f)
            }
            val h = size.height * level
            // Draw as stacked cells for the segmented-LED look.
            var y = size.height
            val x = i * (w + gap)
            while (y > size.height - h) {
                val top = (y - cell).coerceAtLeast(size.height - h)
                val hot = y < size.height * 0.25f
                drawRect(if (hot) Tac.Amber else Tac.Green, Offset(x, top), androidx.compose.ui.geometry.Size(w, y - top - 1f))
                y -= cell + 1f
            }
        }
        drawLine(Tac.GreenDim, Offset(0f, size.height), Offset(size.width, size.height), 1f)
    }
}

/** Segmented progress meter: ▮▮▮▮▮▯▯▯ */
@Composable
fun SegmentMeter(fraction: Float, modifier: Modifier = Modifier, segments: Int = 40) {
    Canvas(modifier) {
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (segments - 1)) / segments
        val lit = (fraction.coerceIn(0f, 1f) * segments)
        for (i in 0 until segments) {
            val color = when {
                i < lit.toInt() -> Tac.Green
                i < lit -> Tac.GreenMid
                else -> Tac.GreenFaint
            }
            drawRect(color, Offset(i * (w + gap), 0f), androidx.compose.ui.geometry.Size(w, size.height))
        }
    }
}
