package com.tavoohoh.quiet.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Text styles with the iOS text style sizes (they scale with the system font size).
 * SF Pro can't ship on Android: the family uses the system sans (Roboto) there.
 */
object QuietType {
    private val sans = FontFamily.SansSerif
    private fun style(size: Int, weight: FontWeight = FontWeight.Normal, line: Float = 1.25f) =
        TextStyle(fontFamily = sans, fontSize = size.sp, fontWeight = weight, lineHeight = (size * line).sp)

    val largeTitle = style(34, FontWeight.Black, 1.15f)
    val title = style(28, FontWeight.Black, 1.15f)
    val title2 = style(22, FontWeight.Bold)
    val title3 = style(20, FontWeight.Black)
    val headline = style(17, FontWeight.SemiBold)
    val body = style(17)
    val subheadline = style(15)
    val footnote = style(13)
    val caption = style(12)

    /** Tabular digits, so numbers don't jump. */
    const val TABULAR = "tnum"

    /** The game's score (heavy, tabular). */
    fun score(size: TextUnit = 44.sp) = TextStyle(
        fontFamily = sans, fontSize = size, fontWeight = FontWeight.Black, fontFeatureSettings = TABULAR,
    )

    /** Values in stat tiles. */
    val stat = style(22, FontWeight.Black).copy(fontFeatureSettings = TABULAR)

    val material = Typography(
        displayLarge = largeTitle, headlineLarge = title, headlineMedium = title2, headlineSmall = title3,
        titleLarge = title3, titleMedium = headline, bodyLarge = body, bodyMedium = subheadline, bodySmall = footnote,
        labelLarge = headline, labelMedium = footnote, labelSmall = caption,
    )
}

object QuietMotion {
    /** The family's default: quick with a little overshoot (iOS `.snappy(0.2, extraBounce: 0.12)`). */
    fun <T> quiet(): AnimationSpec<T> = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow * 2.5f)
}
