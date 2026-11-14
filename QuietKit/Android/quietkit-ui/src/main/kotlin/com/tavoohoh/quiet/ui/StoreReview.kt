package com.tavoohoh.quiet.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import com.google.android.play.core.review.ReviewManagerFactory
import com.tavoohoh.quiet.core.ReviewPrompt

/**
 * Store reviews (family rule, 2026-11-13). The automatic prompt follows `ReviewPrompt`
 * (quietkit-core) and uses Google Play's In-App Review API; its state lives in its own
 * preferences, which "Reset progress" never clears. `openRatePage` is the Settings row.
 */
class StoreReview(context: Context, prefsName: String = "storeReview") {
    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    var state: ReviewPrompt
        get() = ReviewPrompt(prefs.getInt(FINISHED, 0), prefs.getBoolean(ASKED, false), prefs.getBoolean(PENDING, false))
        private set(value) = prefs.edit {
            putInt(FINISHED, value.finishedGames)
            putBoolean(ASKED, value.asked)
            putBoolean(PENDING, value.pending)
        }

    /**
     * A finished game's summary was closed (`toNewGame`: with "New Game", which defers the
     * request). Returns true when the review should be requested now; `request` shows it.
     */
    fun gameFinished(toNewGame: Boolean = false): Boolean {
        val (next, ask) = state.gameFinished(toNewGame)
        state = next
        return ask
    }

    /** Home or the Daily screen is showing: true if a deferred request should go now. */
    fun arrived(): Boolean {
        val (next, ask) = state.arrived()
        if (ask) state = next
        return ask
    }

    /** The system's review dialog (Google Play decides whether it actually appears). */
    fun request(activity: Activity) {
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful) manager.launchReviewFlow(activity, task.result)
        }
    }

    companion object {
        private const val FINISHED = "finishedGames"
        private const val ASKED = "asked"
        private const val PENDING = "pending"

        /** The store page to write a review: Play Store app, or the web if there isn't one. */
        fun openRatePage(context: Context, packageName: String = context.packageName) {
            val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(market)
            } catch (_: ActivityNotFoundException) {
                val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(web) }
            }
        }
    }
}
