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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixtape.player.R

/**
 * Dark terminal look: muted phosphor green on near-black, with the bright green kept for
 * the few things that are "live" (what's playing, the active tab, focus). One dull amber
 * for warnings. Hairlines instead of boxes.
 */
object Tac {
    val Black = Color(0xFF020302)      // background
    val Panel = Color(0xFF070A07)      // raised surfaces (menus, dialogs, mini player)
    val PanelHi = Color(0xFF0D140E)    // selected / pressed rows
    val Bright = Color(0xFF3DFF6E)     // live accents only
    val Green = Color(0xFF74E08E)      // primary text
    val GreenMid = Color(0xFF3F7D50)   // secondary text, icons
    val GreenDim = Color(0xFF1F3626)   // borders, inactive controls
    val GreenFaint = Color(0xFF111B13) // hairlines, tracks
    val Amber = Color(0xFFD9A441)
    val Red = Color(0xFFE05A4F)

    /** Body text: IBM Plex Mono, a clean, serious terminal face. */
    val Body = FontFamily(
        Font(R.font.plex_mono_regular, FontWeight.Normal),
        Font(R.font.plex_mono_medium, FontWeight.Medium),
        Font(R.font.plex_mono_medium, FontWeight.SemiBold),
        Font(R.font.plex_mono_medium, FontWeight.Bold),
    )

    /** Labels, titles and readouts: Share Tech Mono, a squared-off digital face. */
    val Mono = FontFamily(Font(R.font.share_tech_mono))
}

private val TacColors = darkColorScheme(
    primary = Tac.Bright,
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
    surfaceContainerHigh = Tac.Panel,
    surfaceContainerHighest = Tac.PanelHi,
    surfaceTint = Color.Transparent,
    inverseSurface = Tac.Panel,
    inverseOnSurface = Tac.Green,
    inversePrimary = Tac.Amber,
    outline = Tac.GreenDim,
    outlineVariant = Tac.GreenFaint,
    error = Tac.Red,
    onError = Tac.Black,
    scrim = Color(0xD9000000),
)

private fun label(size: Int, spacing: Float) =
    TextStyle(fontFamily = Tac.Mono, fontSize = size.sp, lineHeight = (size + 4).sp, letterSpacing = spacing.sp)

private fun title(size: Int, spacing: Float = 0.4f) =
    TextStyle(fontFamily = Tac.Mono, fontSize = size.sp, lineHeight = (size * 1.2).sp, letterSpacing = spacing.sp)

private fun body(size: Int, line: Int, spacing: Float = 0f) =
    TextStyle(fontFamily = Tac.Body, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp)

private val TacType = Typography(
    displayLarge = title(48),
    displayMedium = title(40),
    displaySmall = title(34),
    headlineLarge = title(30),
    headlineMedium = title(26),
    headlineSmall = title(23),
    titleLarge = title(19),
    titleMedium = body(15, 21),
    titleSmall = label(12, 1.6f),
    bodyLarge = body(14, 20),
    bodyMedium = body(13, 18),
    bodySmall = body(11, 15, 0.1f),
    labelLarge = label(12, 1.8f),
    labelMedium = label(11, 1.6f),
    labelSmall = label(10, 1.4f),
)

private val TacShapes = Shapes(
    extraSmall = CutCornerShape(1.dp),
    small = CutCornerShape(2.dp),
    medium = CutCornerShape(3.dp),
    large = CutCornerShape(4.dp),
    extraLarge = CutCornerShape(4.dp),
)

/** Settings that change how the theme draws (cover tint, grain). */
data class TacOptions(val tintArt: Boolean = true, val scanlines: Boolean = true)

val LocalTacOptions = staticCompositionLocalOf { TacOptions() }

@Composable
fun MixtapeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TacColors, typography = TacType, shapes = TacShapes, content = content)
}
