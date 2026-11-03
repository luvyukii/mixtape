package com.mixtape.player.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.mixtape.player.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "Tactical terminal" look: phosphor green on black, one amber accent for warnings,
 * monospace everything, chamfered corners instead of rounded ones.
 */
object Tac {
    val Black = Color(0xFF000000)
    val Panel = Color(0xFF050C07)        // panels sit just above pure black
    val PanelHi = Color(0xFF0A170C)      // pressed / selected rows
    val Green = Color(0xFF4DFF7C)        // primary phosphor
    val GreenMid = Color(0xFF2FA352)     // secondary text
    val GreenDim = Color(0xFF16572A)     // borders, inactive
    val GreenFaint = Color(0xFF0B2E14)   // tracks, dividers
    val Amber = Color(0xFFFFB627)        // alerts, "live" indicators
    val Red = Color(0xFFFF4D4D)

    /** Body text: a clean, squared-off digital terminal face. */
    val Mono = FontFamily(Font(R.font.share_tech_mono))
    /** Big readouts (clock, titles, timers): a CRT pixel face. */
    val Display = FontFamily(Font(R.font.vt323))
}

private val TacColors = darkColorScheme(
    primary = Tac.Green,
    onPrimary = Tac.Black,
    primaryContainer = Tac.GreenFaint,
    onPrimaryContainer = Tac.Green,
    secondary = Tac.GreenMid,
    onSecondary = Tac.Black,
    secondaryContainer = Tac.PanelHi,
    onSecondaryContainer = Tac.Green,
    tertiary = Tac.Amber,
    onTertiary = Tac.Black,
    background = Tac.Black,
    onBackground = Tac.Green,
    surface = Tac.Black,
    onSurface = Tac.Green,
    surfaceVariant = Tac.Panel,
    onSurfaceVariant = Tac.GreenMid,
    surfaceContainerLowest = Tac.Black,
    surfaceContainerLow = Tac.Panel,
    surfaceContainer = Tac.Panel,
    surfaceContainerHigh = Tac.PanelHi,
    surfaceContainerHighest = Tac.PanelHi,
    surfaceTint = Color.Transparent,
    inverseSurface = Tac.Green,
    inverseOnSurface = Tac.Black,
    outline = Tac.GreenDim,
    outlineVariant = Tac.GreenFaint,
    error = Tac.Red,
    onError = Tac.Black,
    scrim = Color(0xCC000000),
)

private fun mono(style: TextStyle, spacing: Float = 0.5f) =
    style.copy(fontFamily = Tac.Mono, letterSpacing = spacing.sp)

/** The pixel font runs small, so it's set larger to match the body text's weight on screen. */
private fun display(size: Int, spacing: Float = 1f) =
    TextStyle(fontFamily = Tac.Display, fontSize = size.sp, lineHeight = (size * 1.05).sp, letterSpacing = spacing.sp)

private val base = Typography()

private val TacType = Typography(
    displayLarge = display(64),
    displayMedium = display(52),
    displaySmall = display(42),
    headlineLarge = display(40),
    headlineMedium = display(34),
    headlineSmall = display(30, 1.5f),
    titleLarge = display(28, 1.5f),
    titleMedium = mono(base.titleMedium.copy(fontSize = 15.sp), 0.6f),
    titleSmall = mono(base.titleSmall.copy(fontSize = 12.sp), 2f),
    bodyLarge = mono(base.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp), 0.3f),
    bodyMedium = mono(base.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp), 0.3f),
    bodySmall = mono(base.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp), 0.4f),
    labelLarge = mono(base.labelLarge.copy(fontSize = 12.sp), 2f),
    labelMedium = mono(base.labelMedium.copy(fontSize = 11.sp), 1.6f),
    labelSmall = mono(base.labelSmall.copy(fontSize = 10.sp), 1.6f),
)

private val TacShapes = Shapes(
    extraSmall = CutCornerShape(2.dp),
    small = CutCornerShape(3.dp),
    medium = CutCornerShape(5.dp),
    large = CutCornerShape(6.dp),
    extraLarge = CutCornerShape(6.dp),
)

/** Settings that change how the theme draws (cover tint, scanlines). */
data class TacOptions(val tintArt: Boolean = true, val scanlines: Boolean = true)

val LocalTacOptions = staticCompositionLocalOf { TacOptions() }

@Composable
fun MixtapeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TacColors, typography = TacType, shapes = TacShapes, content = content)
}
