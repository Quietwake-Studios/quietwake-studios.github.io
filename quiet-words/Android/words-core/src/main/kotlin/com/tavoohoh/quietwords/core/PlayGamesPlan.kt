package com.tavoohoh.quietwords.core

import com.tavoohoh.quiet.core.ScoreTag
import com.tavoohoh.quiet.core.Streaks
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Leaderboards: one daily per language (comparing times across lists isn't fair), the best
 * Hard score per language and one streak. Same concepts and IDs as iOS's Game Center; the
 * Play Console IDs are in the app's `games-ids.xml`.
 */
enum class Leaderboard(val conceptID: String) {
    DAILY_EN("qw_daily_en"), DAILY_ES("qw_daily_es"),
    BEST_HARD_EN("qw_best_hard_en"), BEST_HARD_ES("qw_best_hard_es"),
    LONGEST_STREAK("qw_longest_streak");

    val isDaily: Boolean get() = this == DAILY_EN || this == DAILY_ES

    companion object {
        fun daily(language: WordLanguage) = if (language == WordLanguage.EN) DAILY_EN else DAILY_ES
        fun bestHard(language: WordLanguage) = if (language == WordLanguage.EN) BEST_HARD_EN else BEST_HARD_ES
    }
}

/** The Game Center / Play Games ID of each achievement (iOS `Achievement.gameCenterID`). */
val Achievement.conceptID: String
    get() = when (this) {
        Achievement.FIRST_PUZZLE -> "qw_first_puzzle"
        Achievement.WORDS_100 -> "qw_words_100"
        Achievement.WORDS_1000 -> "qw_words_1000"
        Achievement.WORDS_10000 -> "qw_words_10000"
        Achievement.THEME_COMPLETE -> "qw_theme_complete"
        Achievement.IN_PLAIN_SIGHT -> "qw_hidden_early"
        Achievement.HABIT -> "qw_daily_7"
        Achievement.CONSISTENCY -> "qw_daily_30"
        Achievement.STEADY_HAND -> "qw_hard_fast"
        Achievement.BILINGUAL -> "qw_bilingual"
        Achievement.IN_THE_RED -> "qw_in_the_red"
    }

/**
 * Play Games achievements are standard (unlock) or incremental (steps out of a total); the
 * counted ones are incremental with the iOS target as total, so Play shows the same progress.
 */
val Achievement.isIncremental: Boolean get() = target > 1

/**
 * What to push to Play Games after a sign-in or a finished game (port of iOS
 * `AppModel.pushToGameCenter`): local data is the source of truth, Play only mirrors it.
 */
data class PlayGamesPlan(
    /** Standard achievements earned. */
    val unlocks: List<Achievement>,
    /** Incremental achievements: steps so far (capped at the total); Play keeps the highest. */
    val steps: Map<Achievement, Int>,
    val scores: List<Score>,
) {
    data class Score(val leaderboard: Leaderboard, val value: Long, val tag: String? = null)

    /** By concept ID, for QuietKit's `PlayGamesSync`. */
    fun push() = com.tavoohoh.quiet.core.PlayGamesPush(
        unlocks.map { it.conceptID },
        steps.mapKeys { it.key.conceptID },
        scores.map { com.tavoohoh.quiet.core.PlayGamesPush.Score(it.leaderboard.conceptID, it.value, it.tag) },
    )

    companion object {
        fun of(progress: PlayerProgress, dailies: Map<String, DailyRecord>, todayKey: String): PlayGamesPlan {
            val unlocks = Achievement.entries.filter { !it.isIncremental && it in progress.unlocked }
            val steps = Achievement.entries.filter { it.isIncremental }.associateWith { a ->
                if (a in progress.unlocked) a.target else (progress.progress(a) * a.target).toInt()
            }.filterValues { it > 0 }
            val scores = mutableListOf<Score>()
            for (language in WordLanguage.entries) {
                val best = progress.bestScore(Level.HARD, language)
                if (best > 0) scores += Score(Leaderboard.bestHard(language), best.toLong())
                // Today's best in each language, after every attempt (0 or negative too): Play
                // keeps the best per span; the tag carries the day and the time for the tie-break.
                val today = dailies[DailyID(todayKey, language).key]
                if (today != null && today.isCompleted) {
                    scores += Score(Leaderboard.daily(language), today.score.toLong(), ScoreTag.daily(todayKey, today.seconds))
                }
            }
            val completed = dailies.filterValues { it.isCompleted }.keys.mapNotNull { DailyID.fromKey(it)?.dayKey }.toSet()
            val longest = Streaks.longest(completed)
            if (longest > 0) scores += Score(Leaderboard.LONGEST_STREAK, longest.toLong())
            return PlayGamesPlan(unlocks, steps, scores)
        }
    }
}

/**
 * Progress, album and daily results shared across the player's devices (Play Games saved
 * game; iOS's `Snapshot` in iCloud). The merge never loses progress.
 */
@Serializable
data class CloudSnapshot(val profile: Profile = Profile(), val dailies: Map<String, DailyRecord> = emptyMap()) {
    fun merged(other: CloudSnapshot): CloudSnapshot {
        val dailies = this.dailies.toMutableMap()
        for ((key, record) in other.dailies) {
            val local = dailies[key]
            dailies[key] = if (local == null) record else local.better(record).copy(attempts = maxOf(local.attempts, record.attempts))
        }
        return CloudSnapshot(profile.merged(other.profile), dailies)
    }

    fun encode(): ByteArray = FORMAT.encodeToString(serializer(), this).toByteArray()

    companion object {
        /** The saved game's name in Play Games. */
        const val NAME = "progress"
        private val FORMAT = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun decode(bytes: ByteArray): CloudSnapshot? =
            runCatching { FORMAT.decodeFromString(serializer(), bytes.decodeToString()) }.getOrNull()
    }
}
