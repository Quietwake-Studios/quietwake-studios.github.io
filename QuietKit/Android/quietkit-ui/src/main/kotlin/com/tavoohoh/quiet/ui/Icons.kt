package com.tavoohoh.quiet.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The few glyphs the kit's own components draw (stroked like SF Symbols, semibold). */
object QuietIcons {
    private fun icon(name: String, block: ImageVector.Builder.() -> ImageVector.Builder): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).block().build()

    private val stroke = SolidColor(Color.Black)

    /** chevron.backward */
    val Back: ImageVector by lazy {
        icon("Back") {
            path(stroke = stroke, strokeLineWidth = 2.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(15f, 4.5f); lineTo(7.5f, 12f); lineTo(15f, 19.5f)
            }
        }
    }

    /** ellipsis */
    val More: ImageVector by lazy {
        icon("More") {
            for (x in listOf(5f, 12f, 19f)) {
                path(fill = stroke) {
                    moveTo(x - 2f, 12f); arcTo(2f, 2f, 0f, true, true, x + 2f, 12f); arcTo(2f, 2f, 0f, true, true, x - 2f, 12f); close()
                }
            }
            this
        }
    }
}
