package com.tavoohoh.quiet.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** Haptic feedback through the view (it follows the system's touch feedback setting). Port of QuietUI's `HapticsService`. */
class QuietHaptics(private val view: View) {
    enum class Event {
        SELECT,
        /** A piece lands. */
        PLACE,
        SUCCESS,
        INVALID,
    }

    fun play(event: Event) {
        val constant = when (event) {
            Event.SELECT -> if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK
            Event.PLACE -> HapticFeedbackConstants.CONTEXT_CLICK
            Event.SUCCESS -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
            Event.INVALID -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberHaptics(): QuietHaptics {
    val view = LocalView.current
    return remember(view) { QuietHaptics(view) }
}
