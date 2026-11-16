package com.tavoohoh.quietnumbers.design

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import com.tavoohoh.quietnumbers.core.ClearEffect
import com.tavoohoh.quietnumbers.core.NumberFont
import com.tavoohoh.quietnumbers.core.TileStyle

/** The player's cosmetics for the board (iOS `Appearance`); the colors are the kit's theme. */
data class BoardLooks(
    val tileStyle: TileStyle = TileStyle.PLAIN,
    val numberFont: NumberFont = NumberFont.ROUNDED,
    val clearEffect: ClearEffect = ClearEffect.OUTLINE,
    /** Settings › Larger numbers: cells 52 dp tall, numbers of 32. */
    val largerNumbers: Boolean = false,
) {
    /** iOS SF Rounded and New York: the system sans and serif. */
    val family: FontFamily get() = if (numberFont == NumberFont.SERIF) FontFamily.Serif else FontFamily.SansSerif
}

val LocalBoardLooks = staticCompositionLocalOf { BoardLooks() }
