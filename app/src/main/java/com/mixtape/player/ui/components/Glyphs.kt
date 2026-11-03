package com.mixtape.player.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

typealias Glyph = ImageVector

/**
 * Line-art icons drawn for NEXUS on a 24-unit grid: thin square-capped strokes,
 * so they sit with the terminal type instead of looking like stock icons.
 */
object Glyphs {

    private fun glyph(name: String, stroke: Float = 1.5f, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = stroke,
            strokeLineCap = StrokeCap.Square,
            strokeLineJoin = StrokeJoin.Miter,
            pathBuilder = block,
        ).build()

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, true, true, r * 2, 0f)
        arcToRelative(r, r, 0f, true, true, -r * 2, 0f)
        close()
    }

    private fun PathBuilder.rect(l: Float, t: Float, r: Float, b: Float) {
        moveTo(l, t); lineTo(r, t); lineTo(r, b); lineTo(l, b); close()
    }

    /** Library: a crate of records, one leaning out. */
    val Library: Glyph by lazy {
        glyph("Library") {
            moveTo(5f, 5f); lineTo(5f, 19f)
            moveTo(9f, 5f); lineTo(9f, 19f)
            moveTo(13f, 5f); lineTo(13f, 19f)
            moveTo(16.5f, 6f); lineTo(20f, 18.5f)
        }
    }

    /** Lists: three lines with square bullets. */
    val Lists: Glyph by lazy {
        glyph("Lists") {
            rect(4f, 6f, 6f, 8f); moveTo(9f, 7f); lineTo(20f, 7f)
            rect(4f, 11f, 6f, 13f); moveTo(9f, 12f); lineTo(20f, 12f)
            rect(4f, 16f, 6f, 18f); moveTo(9f, 17f); lineTo(16f, 17f)
        }
    }

    /** Browse: a radar scope with a sweep. */
    val Browse: Glyph by lazy {
        glyph("Browse") {
            circle(12f, 12f, 8f)
            moveTo(12f, 2.5f); lineTo(12f, 4.5f)
            moveTo(12f, 19.5f); lineTo(12f, 21.5f)
            moveTo(2.5f, 12f); lineTo(4.5f, 12f)
            moveTo(19.5f, 12f); lineTo(21.5f, 12f)
            moveTo(12f, 12f); lineTo(16.5f, 7.5f)
        }
    }

    /** Config: two faders. */
    val Config: Glyph by lazy {
        glyph("Config") {
            moveTo(4f, 8f); lineTo(20f, 8f)
            moveTo(4f, 16f); lineTo(20f, 16f)
            rect(7.5f, 5.5f, 11.5f, 10.5f)
            rect(13.5f, 13.5f, 17.5f, 18.5f)
        }
    }

    /** Web: a wireframe globe. */
    val Web: Glyph by lazy {
        glyph("Web") {
            circle(12f, 12f, 8.5f)
            moveTo(3.5f, 12f); lineTo(20.5f, 12f)
            moveTo(12f, 3.5f)
            arcToRelative(4.5f, 8.5f, 0f, false, true, 0f, 17f)
            arcToRelative(4.5f, 8.5f, 0f, false, true, 0f, -17f)
        }
    }

    /** Playback: a signal trace. */
    val Wave: Glyph by lazy {
        glyph("Wave") {
            moveTo(2.5f, 12f); lineTo(6f, 12f); lineTo(8f, 7f); lineTo(11f, 17f)
            lineTo(14f, 5f); lineTo(17f, 15f); lineTo(18.5f, 12f); lineTo(21.5f, 12f)
        }
    }

    /** Display: a monitor. */
    val Screen: Glyph by lazy {
        glyph("Screen") {
            rect(3f, 5f, 21f, 16f)
            moveTo(9f, 19.5f); lineTo(15f, 19.5f)
            moveTo(12f, 16f); lineTo(12f, 19.5f)
        }
    }

    /** A disc with its spindle hole. */
    val Disc: Glyph by lazy {
        glyph("Disc") {
            circle(12f, 12f, 8.5f)
            circle(12f, 12f, 2f)
            moveTo(12f, 6f)
            arcToRelative(6f, 6f, 0f, false, true, 5.2f, 3f)
        }
    }

    /** Small targeting cross for decorations. */
    val Cross: Glyph by lazy {
        glyph("Cross", stroke = 1.2f) {
            moveTo(12f, 4f); lineTo(12f, 9f)
            moveTo(12f, 15f); lineTo(12f, 20f)
            moveTo(4f, 12f); lineTo(9f, 12f)
            moveTo(15f, 12f); lineTo(20f, 12f)
        }
    }
}
