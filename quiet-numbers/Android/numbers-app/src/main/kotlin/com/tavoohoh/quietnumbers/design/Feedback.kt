package com.tavoohoh.quietnumbers.design

import androidx.compose.runtime.staticCompositionLocalOf
import com.tavoohoh.quiet.ui.QuietHaptics
import com.tavoohoh.quiet.ui.QuietTones
import com.tavoohoh.quietnumbers.game.GameFeedback

/** The game's haptics and tones on the kit's `QuietHaptics` and `QuietTones` (port of iOS `FeedbackService`). */
class FeedbackService(private val tones: QuietTones?, private val sound: () -> Boolean = { true }, private val haptics: () -> Boolean = { true }) {
    fun play(event: GameFeedback, view: QuietHaptics) {
        if (haptics()) {
            view.play(
                when (event) {
                    GameFeedback.Select -> QuietHaptics.Event.SELECT
                    is GameFeedback.Match -> if (event.longShot) QuietHaptics.Event.SUCCESS else QuietHaptics.Event.PLACE
                    GameFeedback.Invalid -> QuietHaptics.Event.INVALID
                    GameFeedback.RowCleared, GameFeedback.BoardCleared -> QuietHaptics.Event.SUCCESS
                },
            )
        }
        if (sound()) tones?.play(notes(event))
    }

    companion object {
        /** Frequencies (Hz) played one after another, on a C major pentatonic scale. */
        fun notes(event: GameFeedback): List<Double> = when (event) {
            GameFeedback.Select -> listOf(523.25)
            is GameFeedback.Match -> if (event.longShot) listOf(659.25, 987.77) else listOf(659.25)
            GameFeedback.Invalid -> listOf(220.0)
            GameFeedback.RowCleared -> listOf(523.25, 659.25, 783.99)
            GameFeedback.BoardCleared -> listOf(523.25, 659.25, 783.99, 1046.5)
        }
    }
}

val LocalFeedback = staticCompositionLocalOf { FeedbackService(null) }
