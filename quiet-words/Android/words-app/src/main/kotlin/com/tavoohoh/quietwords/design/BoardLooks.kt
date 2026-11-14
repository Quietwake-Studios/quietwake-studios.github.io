package com.tavoohoh.quietwords.design

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.tavoohoh.quietwords.core.BoardFont
import com.tavoohoh.quietwords.core.Marker

/** What the board looks like from the player's choices (iOS `Appearance`): marker, letters and "Larger letters". */
data class BoardLooks(
    val marker: Marker = Marker.HIGHLIGHTER,
    val boardFont: BoardFont = BoardFont.ROUNDED,
    val largerLetters: Boolean = false,
)

val LocalBoardLooks = staticCompositionLocalOf { BoardLooks() }

/** Rounded is the system sans (SF Rounded can't ship on Android); serif and mono as on iOS. */
val BoardFont.family: FontFamily
    get() = when (this) {
        BoardFont.ROUNDED -> FontFamily.SansSerif
        BoardFont.SERIF -> FontFamily.Serif
        BoardFont.MONO -> FontFamily.Monospace
    }

val BoardFont.weight: FontWeight get() = if (this == BoardFont.ROUNDED) FontWeight.SemiBold else FontWeight.Medium
