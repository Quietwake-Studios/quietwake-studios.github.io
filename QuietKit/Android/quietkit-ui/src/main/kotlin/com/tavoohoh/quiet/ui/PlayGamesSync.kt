package com.tavoohoh.quiet.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.tavoohoh.quiet.core.PlayGamesPush
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A game's Play Games plumbing (iOS `syncGameCenter` / `pushToGameCenter`): concept IDs to Play
 * Console IDs (`achievement_<id>` / `leaderboard_<id>` in the app's `games-ids.xml`, `TODO_…`
 * until the game exists; while the project ID isn't a number Play Games never starts), the
 * saved game (pulled and merged on sign-in, pushed after each game; deleted after a reset, on
 * the next sign-in if signed out) and the push of achievements and scores.
 */
class PlayGamesSync(
    private val context: Context,
    val games: PlayGamesService,
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope,
    /** What's pushed now (achievements and scores, by concept ID). */
    private val plan: () -> PlayGamesPush,
    /** The saved game: this device's copy, how two copies merge, and how a merged copy is applied here. */
    private val local: () -> ByteArray,
    private val merge: (ByteArray, ByteArray) -> ByteArray,
    private val apply: (ByteArray) -> Unit,
    private val snapshotName: String = "progress",
) {
    private fun id(prefix: String, conceptID: String): String {
        val res = context.resources.getIdentifier("${prefix}_$conceptID", "string", context.packageName)
        return if (res == 0) "" else context.getString(res)
    }

    fun achievementID(conceptID: String) = id("achievement", conceptID)
    fun leaderboardID(conceptID: String) = id("leaderboard", conceptID)

    private val pendingReset: Boolean get() = prefs.getBoolean(KEY_PENDING_CLOUD_RESET, false)

    /** On sign-in: pull what other devices saved, merge it into local data, then push everything. */
    fun sync() {
        if (!games.isAuthenticated) return
        scope.launch {
            runCatching {
                if (pendingReset) {
                    if (games.deleteSnapshot(snapshotName)) prefs.edit { remove(KEY_PENDING_CLOUD_RESET) }
                } else {
                    games.loadSnapshot(snapshotName, merge)?.let(apply)
                }
            }
            push()
        }
    }

    /** Achievements, scores and the saved game. */
    fun push() {
        if (!games.isAuthenticated) return
        val plan = plan()
        for (a in plan.unlocks) games.unlock(achievementID(a))
        for ((a, steps) in plan.steps) games.setSteps(achievementID(a), steps)
        for (score in plan.scores) games.submit(leaderboardID(score.leaderboard), score.value, score.tag)
        if (pendingReset) return
        val snapshot = local()
        scope.launch { runCatching { games.saveSnapshot(snapshotName, snapshot) } }
    }

    /** Reset Progress: the saved game goes too; signed out, on the next sign-in (Play can't reset achievements or scores). */
    fun resetCloud() {
        prefs.edit { putBoolean(KEY_PENDING_CLOUD_RESET, true) }
        sync()
    }

    companion object {
        /** The same key Quiet Words used before this moved to the kit. */
        const val KEY_PENDING_CLOUD_RESET = "pendingCloudReset"
    }
}
