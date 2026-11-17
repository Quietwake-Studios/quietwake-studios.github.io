package com.tavoohoh.quietnumbers.core

import com.tavoohoh.quiet.core.DailyRecord
import com.tavoohoh.quiet.core.PlayGamesPush
import com.tavoohoh.quiet.core.ScoreTag
import com.tavoohoh.quiet.core.Streaks
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The three leaderboards of iOS's Game Center (`AppStore/game-center.json`), same concept IDs;
 * the Play Console IDs are in the app's `games-ids.xml`.
 */
enum class Leaderboard(val conceptID: String) {
    /** Today's best result (time breaks ties through the score tag). */
    DAILY("daily_challenge"),
    /** Best classic score. */
    BEST_SCORE("best_score"),
    /** Longest run of completed daily challenges. */
    LONGEST_STREAK("longest_streak");

    val isDaily: Boolean get() = this == DAILY
}

/** The Game Center / Play Games ID of each achievement (iOS `Achievement.gameCenterID`). */
val Achievement.conceptID: String
    get() = when (this) {
        Achievement.FIRST_STEP -> "first_step"
        Achievement.CLEAN_SLATE -> "clean_slate"
        Achievement.NO_HELP -> "no_help"
        Achievement.CONSISTENCY -> "consistency"
        Achievement.HABIT -> "habit"
        Achievement.SWEEPER -> "sweeper"
        Achievement.STAGE_FIVE -> "stage_five"
        Achievement.CENTENNIAL -> "centennial"
        Achievement.NIGHT_OWL -> "night_owl"
        Achievement.MONOTONE -> "monotone"
        Achievement.IN_THE_RED -> "in_the_red"
    }

/** Game Center's points (620 in all; Play wants multiples of 5 and at most 1000 per game). */
val Achievement.points: Int
    get() = when (this) {
        Achievement.FIRST_STEP -> 10
        Achievement.CLEAN_SLATE -> 30
        Achievement.NO_HELP -> 50
        Achievement.CONSISTENCY -> 80
        Achievement.HABIT -> 100
        Achievement.SWEEPER -> 60
        Achievement.STAGE_FIVE -> 70
        Achievement.CENTENNIAL -> 100
        Achievement.NIGHT_OWL -> 40
        Achievement.MONOTONE -> 60
        Achievement.IN_THE_RED -> 20
    }

/** Hidden on Game Center until earned (also in Play Console's initial state). */
val Achievement.isHiddenOnline: Boolean
    get() = this == Achievement.NIGHT_OWL || this == Achievement.MONOTONE || this == Achievement.IN_THE_RED

/** Counted achievements are incremental on Play with the iOS target as total, so Play shows the same progress as Game Center's percent. */
val Achievement.isIncremental: Boolean get() = target > 1

/**
 * What to push to Play Games after a sign-in or a finished game (port of iOS
 * `AppModel.pushToGameCenter`): local data is the source of truth, Play only mirrors it.
 */
object PlayGamesPlan {
    fun of(progress: PlayerProgress, dailies: Map<String, DailyRecord>, todayKey: String): PlayGamesPush {
        val unlocks = Achievement.entries.filter { !it.isIncremental && it in progress.unlocked }.map { it.conceptID }
        val steps = Achievement.entries.filter { it.isIncremental }.associate { a ->
            a.conceptID to (if (a in progress.unlocked) a.target else minOf(progress.count(a), a.target))
        }.filterValues { it > 0 }
        val scores = mutableListOf<PlayGamesPush.Score>()
        // Today's best after every attempt, even at 0 or below (iOS `dailyToSubmit`); the tag
        // carries the day (Play's daily span isn't the player's day) and the time for the tie-break.
        dailies[todayKey]?.takeIf { it.isFinished }?.let {
            scores += PlayGamesPush.Score(Leaderboard.DAILY.conceptID, it.score.toLong(), ScoreTag.daily(todayKey, it.seconds))
        }
        if (progress.bestScore > 0) scores += PlayGamesPush.Score(Leaderboard.BEST_SCORE.conceptID, progress.bestScore.toLong())
        val longest = Streaks.longest(dailies.filterValues { it.isCompleted }.keys)
        if (longest > 0) scores += PlayGamesPush.Score(Leaderboard.LONGEST_STREAK.conceptID, longest.toLong())
        return PlayGamesPush(unlocks, steps, scores)
    }
}

/**
 * Progress and daily results shared across the player's devices (Play Games saved game; iOS's
 * `Snapshot` in iCloud). The merge never loses progress: union of achievements, the highest of
 * each counter, the better result of each day. The classic game in progress isn't synced.
 */
@Serializable
data class CloudSnapshot(val progress: PlayerProgress = PlayerProgress(), val dailies: Map<String, DailyRecord> = emptyMap()) {
    fun merged(other: CloudSnapshot): CloudSnapshot {
        val dailies = this.dailies.toMutableMap()
        for ((key, record) in other.dailies) {
            val local = dailies[key]
            dailies[key] = if (local == null) record else local.better(record).copy(attempts = maxOf(local.attempts, record.attempts))
        }
        return CloudSnapshot(progress.merged(other.progress), dailies)
    }

    fun encode(): ByteArray = FORMAT.encodeToString(serializer(), this).toByteArray()

    companion object {
        /** The saved game's name in Play Games. */
        const val NAME = "progress"
        private val FORMAT = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun decode(bytes: ByteArray): CloudSnapshot? =
            runCatching { FORMAT.decodeFromString(serializer(), bytes.decodeToString()) }.getOrNull()

        /** Two copies of the saved game into one (either may be unreadable). */
        fun mergeBytes(a: ByteArray, b: ByteArray): ByteArray {
            val x = decode(a)
            val y = decode(b)
            return (if (x != null && y != null) x.merged(y) else x ?: y ?: CloudSnapshot()).encode()
        }
    }
}
