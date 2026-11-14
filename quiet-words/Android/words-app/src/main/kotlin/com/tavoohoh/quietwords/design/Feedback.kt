package com.tavoohoh.quietwords.design

import androidx.compose.runtime.staticCompositionLocalOf
import com.tavoohoh.quiet.ui.QuietHaptics
import com.tavoohoh.quiet.ui.QuietTones

/** What the game reacts to (iOS `FeedbackService`). */
enum class Feedback { LETTER, FOUND, INVALID, HINT, HIDDEN_WORD, WON }

/** Haptics and sound for play, honoring Settings › Sound and Haptics (port of iOS `FeedbackService`). */
class FeedbackService(
    private val tones: QuietTones?,
    private val sound: () -> Boolean,
    private val haptics: () -> Boolean,
) {
    /** `step`: letters in the stroke minus one; the stroke climbs a note per letter. */
    fun play(feedback: Feedback, view: QuietHaptics, step: Int = 0) {
        if (haptics()) view.play(feedback)
        if (!sound()) return
        val notes = when (feedback) {
            Feedback.LETTER -> listOf(SCALE[minOf(step, SCALE.size - 1)])
            Feedback.HINT -> listOf(523.25)
            Feedback.FOUND -> listOf(659.25, 783.99)
            Feedback.INVALID -> listOf(220.0)
            Feedback.HIDDEN_WORD -> listOf(523.25, 659.25, 783.99)
            Feedback.WON -> listOf(523.25, 659.25, 783.99, 1046.5)
        }
        tones?.play(notes)
    }

    companion object {
        /** C major pentatonic, like the rest of the family. */
        val SCALE = listOf(261.63, 293.66, 329.63, 392.0, 440.0, 523.25, 587.33, 659.25, 783.99, 880.0, 1046.5, 1174.66)
    }
}

/** The app's feedback (provided by MainActivity). */
val LocalFeedback = staticCompositionLocalOf { FeedbackService(null, { false }, { true }) }

fun QuietHaptics.play(feedback: Feedback) = play(
    when (feedback) {
        Feedback.LETTER, Feedback.HINT -> QuietHaptics.Event.SELECT
        Feedback.FOUND, Feedback.HIDDEN_WORD, Feedback.WON -> QuietHaptics.Event.SUCCESS
        Feedback.INVALID -> QuietHaptics.Event.INVALID
    },
)
