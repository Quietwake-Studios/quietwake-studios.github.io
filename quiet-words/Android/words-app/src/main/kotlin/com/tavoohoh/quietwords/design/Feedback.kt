package com.tavoohoh.quietwords.design

import com.tavoohoh.quiet.ui.QuietHaptics

/** What the game reacts to (iOS `FeedbackService`). Tones come in a later phase; haptics only. */
enum class Feedback { LETTER, FOUND, INVALID, HINT, HIDDEN_WORD, WON }

fun QuietHaptics.play(feedback: Feedback) = play(
    when (feedback) {
        Feedback.LETTER, Feedback.HINT -> QuietHaptics.Event.SELECT
        Feedback.FOUND, Feedback.HIDDEN_WORD, Feedback.WON -> QuietHaptics.Event.SUCCESS
        Feedback.INVALID -> QuietHaptics.Event.INVALID
    },
)
