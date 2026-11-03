package com.mixtape.player.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixtape.player.ui.theme.Tac
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

val TacButtonShape = CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp)

/** Primary command: dark fill, bright hairline and text. */
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
        border = BorderStroke(1.dp, if (enabled) Tac.Bright.copy(alpha = 0.7f) else Tac.GreenDim),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF0B1F10),
            contentColor = Tac.Bright,
            disabledContainerColor = Tac.Black,
            disabledContentColor = Tac.GreenDim,
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        content = content,
    )
}

/** Secondary command: hairline only. */
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
        border = BorderStroke(1.dp, if (enabled) Tac.GreenDim else Tac.GreenFaint),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Tac.Green, disabledContentColor = Tac.GreenDim),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        content = content,
    )
}

/** A section: small label, a hairline, then content. No box around it. */
@Composable
fun TacPanel(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    glyph: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (glyph != null) {
                Icon(glyph, null, tint = Tac.GreenMid, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = Tac.GreenMid)
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Tac.GreenFaint)
            )
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                Text(trailing, style = MaterialTheme.typography.labelSmall, color = Tac.GreenDim)
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** List section label: "ARTISTS  12 ─────". */
@Composable
fun TacLabel(text: String, modifier: Modifier = Modifier, count: Int? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = Tac.GreenMid)
        if (count != null) {
            Text("  $count", style = MaterialTheme.typography.labelMedium, color = Tac.GreenDim)
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(Tac.GreenFaint)
        )
    }
}

/**
 * Screen texture drawn over everything: faint scanlines and a dark vignette.
 * Purely visual; touches pass through.
 */
fun Modifier.scanlines(enabled: Boolean): Modifier = if (!enabled) this else drawWithCache {
    val vignette = Brush.radialGradient(
        0.55f to Color.Transparent,
        1f to Color(0x8C000000),
        center = Offset(size.width / 2, size.height / 2),
        radius = maxOf(size.width, size.height) * 0.75f,
    )
    val step = 3.dp.toPx()
    val line = Color(0x0F000000)
    onDrawWithContent {
        drawContent()
        var y = 0f
        while (y < size.height) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += step
        }
        drawRect(vignette)
    }
}

/** Thin targeting brackets around something (album art, the visualizer). */
fun Modifier.cornerBrackets(color: Color = Tac.GreenDim, length: Dp = 10.dp, inset: Dp = 0.dp): Modifier =
    drawBehind {
        val l = length.toPx()
        val i = inset.toPx()
        val w = 1.dp.toPx()
        val r = size.width - i
        val b = size.height - i
        fun seg(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = w)
        seg(i, i, i + l, i); seg(i, i, i, i + l)
        seg(r, i, r - l, i); seg(r, i, r, i + l)
        seg(i, b, i + l, b); seg(i, b, i, b - l)
        seg(r, b, r - l, b); seg(r, b, r, b - l)
    }

/** Top status strip: wordmark with a slow blinking cursor, status, and a clock. */
@Composable
fun TacHeader(status: String, modifier: Modifier = Modifier, alert: Boolean = false) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(500)
        }
    }
    val cursorOn = now.second % 2 == 0
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "NEXUS",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp, letterSpacing = 5.sp),
                color = Tac.Green,
            )
            Box(
                Modifier
                    .padding(start = 3.dp)
                    .size(width = 6.dp, height = 12.dp)
                    .background(if (cursorOn) Tac.Bright else Color.Transparent)
            )
            Text(
                status.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (alert) Tac.Amber else Tac.GreenMid,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            )
            Text(
                now.format(DateTimeFormatter.ofPattern("HH:mm")),
                style = MaterialTheme.typography.labelMedium,
                color = Tac.GreenMid,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Tac.GreenFaint)
        )
    }
}

class TacTab(val label: String, val icon: ImageVector)

/** Bottom navigation: quiet cells; the active one gets a lit edge and bright label. */
@Composable
fun TacNavBar(tabs: List<TacTab>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(Tac.Black)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Tac.GreenFaint)
        )
        Row(Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { i, tab ->
                val active = i == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .width(28.dp)
                            .height(1.dp)
                            .background(if (active) Tac.Bright else Color.Transparent)
                    )
                    Spacer(Modifier.height(9.dp))
                    Icon(tab.icon, contentDescription = null, tint = if (active) Tac.Green else Tac.GreenDim, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tab.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) Tac.Green else Tac.GreenDim,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(9.dp))
                }
            }
        }
    }
}

/** Side navigation for landscape. */
@Composable
fun TacNavRail(tabs: List<TacTab>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxHeight().background(Tac.Black)) {
        Column(
            Modifier
                .width(76.dp)
                .fillMaxHeight()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            tabs.forEachIndexed { i, tab ->
                val active = i == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(i) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(40.dp)
                            .background(if (active) Tac.Bright else Color.Transparent)
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(tab.icon, null, tint = if (active) Tac.Green else Tac.GreenDim, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tab.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) Tac.Green else Tac.GreenDim,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(Tac.GreenFaint)
        )
    }
}

/** Square on/off switch. */
@Composable
fun TacSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val x by animateDpAsState(if (checked) 18.dp else 2.dp, label = "switch")
    Box(
        modifier
            .size(width = 34.dp, height = 18.dp)
            .border(1.dp, if (checked) Tac.Bright.copy(alpha = 0.6f) else Tac.GreenDim)
            .clickable { onCheckedChange(!checked) },
    ) {
        Box(
            Modifier
                .offset(x = x, y = 3.dp)
                .size(12.dp)
                .background(if (checked) Tac.Bright else Tac.GreenDim)
        )
    }
}

/** Search line: "›" prompt, placeholder, clear button, hairline underneath. */
@Composable
fun TacSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(44.dp)) {
            Text("›", style = MaterialTheme.typography.titleLarge, color = if (value.isEmpty()) Tac.GreenDim else Tac.Bright)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = Tac.GreenDim, maxLines = 1)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Tac.Green),
                    cursorBrush = SolidColor(Tac.Bright),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Rounded.Close, "Clear search", tint = Tac.GreenMid, modifier = Modifier.size(18.dp))
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (value.isEmpty()) Tac.GreenFaint else Tac.GreenDim)
        )
    }
}

/**
 * Text tabs with a thin underline on the active one. With [fill], the tabs split the full
 * width evenly; otherwise they keep their natural widths and scroll sideways if needed.
 */
@Composable
fun TacTabsRow(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (fill) Modifier else Modifier.horizontalScroll(rememberScrollState()))
            .padding(horizontal = 8.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val active = i == selected
            Column(
                (if (fill) Modifier.weight(1f) else Modifier)
                    .clickable { onSelect(i) }
                    .padding(horizontal = if (fill) 4.dp else 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) Tac.Green else Tac.GreenDim,
                    maxLines = 1,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                Box(
                    Modifier
                        .height(1.dp)
                        .width(if (active) 22.dp else 0.dp)
                        .background(Tac.Bright)
                )
            }
        }
    }
}

/** Snackbar in the house style: dark panel, hairline, amber action. */
@Composable
fun TacSnackbar(data: SnackbarData) {
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .border(1.dp, Tac.GreenDim, TacButtonShape)
            .background(Tac.Panel, TacButtonShape)
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            data.visuals.message,
            style = MaterialTheme.typography.bodySmall,
            color = Tac.Green,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp),
        )
        data.visuals.actionLabel?.let { label ->
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Tac.Amber,
                modifier = Modifier
                    .clickable { data.performAction() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
        if (data.visuals.withDismissAction) {
            IconButton(onClick = { data.dismiss() }) {
                Icon(Icons.Rounded.Close, "Dismiss", tint = Tac.GreenMid, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Segmented progress meter. */
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
            drawRect(color, Offset(i * (w + gap), 0f), Size(w, size.height))
        }
    }
}
