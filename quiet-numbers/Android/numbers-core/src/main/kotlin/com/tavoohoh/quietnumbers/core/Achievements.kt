package com.tavoohoh.quietnumbers.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Achievements. Each one unlocks a cosmetic reward; none affect play (port of iOS `Achievement`). */
@Serializable
enum class Achievement(val rawValue: String) {
    /** Clear a first board or finish a first game. */
    @SerialName("firstStep") FIRST_STEP("firstStep"),
    /** Clear a board without using +. */
    @SerialName("cleanSlate") CLEAN_SLATE("cleanSlate"),
    /** Clear a board without using any hint (+ is fine). */
    @SerialName("noHelp") NO_HELP("noHelp"),
    /** 7-day daily streak. */
    @SerialName("consistency") CONSISTENCY("consistency"),
    /** 30-day daily streak. */
    @SerialName("habit") HABIT("habit"),
    /** Clear 100 rows in total. */
    @SerialName("sweeper") SWEEPER("sweeper"),
    /** Reach stage 5 in one game. */
    @SerialName("stageFive") STAGE_FIVE("stageFive"),
    /** Complete 100 daily challenges. */
    @SerialName("centennial") CENTENNIAL("centennial"),
    /** Complete a daily between 00:00 and 04:00 local time. */
    @SerialName("nightOwl") NIGHT_OWL("nightOwl"),
    /** 10 pairs of equal numbers in a row. */
    @SerialName("monotone") MONOTONE("monotone"),
    /** Take the score below zero with hints. */
    @SerialName("inTheRed") IN_THE_RED("inTheRed");

    /** Amount needed for counted achievements; 1 for one-off ones. */
    val target: Int
        get() = when (this) {
            CONSISTENCY -> 7
            HABIT -> 30
            SWEEPER -> 100
            STAGE_FIVE -> 5
            CENTENNIAL -> 100
            MONOTONE -> 10
            FIRST_STEP, CLEAN_SLATE, NO_HELP, NIGHT_OWL, IN_THE_RED -> 1
        }

    val isHidden: Boolean get() = this == IN_THE_RED
}

/** Something that happened in play, fed to `PlayerProgress`. */
sealed interface ProgressEvent {
    data class Matched(val outcome: MatchOutcome) : ProgressEvent
    data object Missed : ProgressEvent
    data class HintUsed(val scoreAfter: Int) : ProgressEvent
    /** The player undid this match; its counters are taken back (unlocks stay). */
    data class Undone(val outcome: MatchOutcome) : ProgressEvent
    /** A game reached its end. `classicScore` is set for classic games. */
    data class GameFinished(val classicScore: Int?) : ProgressEvent
    /** A classic game replaced before it ended: its score still counts as a best; played if anything was played. */
    data class ClassicAbandoned(val score: Int, val played: Boolean) : ProgressEvent
    /** The classic game on screen reached this score: the best updates right away. */
    data class ClassicScore(val score: Int) : ProgressEvent
    data class DailyCompleted(val currentStreak: Int, val localHour: Int) : ProgressEvent
    /** A past day completed: shown on the calendar. */
    data class PracticeCompleted(val dayKey: String) : ProgressEvent
}

/** Lifetime counters and unlocked achievements. Stored locally; works without Play Games. */
@Serializable
data class PlayerProgress(
    val unlocked: Set<Achievement> = emptySet(),
    val gamesFinished: Int = 0,
    val boardsCleared: Int = 0,
    val rowsCleared: Int = 0,
    val highestStage: Int = 1,
    val dailiesCompleted: Int = 0,
    val bestStreak: Int = 0,
    /** Equal-number pairs in a row, in the current game. */
    val equalPairsRun: Int = 0,
    val bestEqualPairsRun: Int = 0,
    /** Best final score in classic mode. */
    val bestScore: Int = 0,
    val gamesPlayed: Int = 0,
    /** Past days completed (`yyyy-MM-dd`). */
    val practicedDays: Set<String> = emptySet(),
) {
    /** Applies an event; returns the new progress and the achievements it unlocked. */
    fun record(event: ProgressEvent): Pair<PlayerProgress, List<Achievement>> {
        var p = this
        val hits = mutableListOf<Achievement>()
        when (event) {
            is ProgressEvent.Matched -> {
                val o = event.outcome
                p = p.copy(rowsCleared = p.rowsCleared + o.clearedRows.size, highestStage = maxOf(p.highestStage, o.stage))
                p = if (o.values[0] == o.values[1]) {
                    val run = p.equalPairsRun + 1
                    p.copy(equalPairsRun = run, bestEqualPairsRun = maxOf(p.bestEqualPairsRun, run))
                } else {
                    p.copy(equalPairsRun = 0)
                }
                if (o.boardCleared) {
                    p = p.copy(boardsCleared = p.boardsCleared + 1)
                    if (o.hintsOnBoard == 0) hits += Achievement.NO_HELP
                    if (!o.usedPlusOnBoard) hits += Achievement.CLEAN_SLATE
                }
            }
            ProgressEvent.Missed -> Unit
            is ProgressEvent.HintUsed -> if (event.scoreAfter < 0) hits += Achievement.IN_THE_RED
            is ProgressEvent.Undone -> {
                val o = event.outcome
                p = p.copy(rowsCleared = maxOf(0, p.rowsCleared - o.clearedRows.size))
                if (o.values[0] == o.values[1]) p = p.copy(equalPairsRun = maxOf(0, p.equalPairsRun - 1))
            }
            is ProgressEvent.GameFinished -> {
                p = p.copy(gamesPlayed = p.gamesPlayed + 1, gamesFinished = p.gamesFinished + 1, equalPairsRun = 0)
                event.classicScore?.let { p = p.copy(bestScore = maxOf(p.bestScore, it)) }
            }
            is ProgressEvent.ClassicAbandoned ->
                p = p.copy(gamesPlayed = p.gamesPlayed + if (event.played) 1 else 0, equalPairsRun = 0, bestScore = maxOf(p.bestScore, event.score))
            is ProgressEvent.ClassicScore -> p = p.copy(bestScore = maxOf(p.bestScore, event.score))
            is ProgressEvent.DailyCompleted -> {
                p = p.copy(dailiesCompleted = p.dailiesCompleted + 1, bestStreak = maxOf(p.bestStreak, event.currentStreak))
                if (event.localHour in 0 until 4) hits += Achievement.NIGHT_OWL
            }
            is ProgressEvent.PracticeCompleted -> p = p.copy(practicedDays = p.practicedDays + event.dayKey)
        }
        hits += Achievement.entries.filter { p.count(it) >= it.target }
        val unlocked = LinkedHashSet(p.unlocked)
        val new = hits.filter { unlocked.add(it) }
        return p.copy(unlocked = unlocked) to new
    }

    /** Combines progress from two devices: every counter keeps its highest value, achievements are the union. */
    fun merged(other: PlayerProgress) = copy(
        unlocked = unlocked + other.unlocked,
        gamesFinished = maxOf(gamesFinished, other.gamesFinished),
        boardsCleared = maxOf(boardsCleared, other.boardsCleared),
        rowsCleared = maxOf(rowsCleared, other.rowsCleared),
        highestStage = maxOf(highestStage, other.highestStage),
        dailiesCompleted = maxOf(dailiesCompleted, other.dailiesCompleted),
        bestStreak = maxOf(bestStreak, other.bestStreak),
        bestEqualPairsRun = maxOf(bestEqualPairsRun, other.bestEqualPairsRun),
        bestScore = maxOf(bestScore, other.bestScore),
        gamesPlayed = maxOf(gamesPlayed, other.gamesPlayed),
        practicedDays = practicedDays + other.practicedDays,
    )

    /** Progress towards an achievement, 0..1. */
    fun progress(achievement: Achievement): Double =
        if (achievement in unlocked) 1.0 else minOf(count(achievement).toDouble() / achievement.target, 1.0)

    /** Current count for counted achievements; one-offs report 0 until unlocked. */
    fun count(achievement: Achievement): Int = when (achievement) {
        Achievement.FIRST_STEP -> gamesFinished + boardsCleared
        Achievement.CONSISTENCY, Achievement.HABIT -> bestStreak
        Achievement.SWEEPER -> rowsCleared
        Achievement.STAGE_FIVE -> highestStage
        Achievement.CENTENNIAL -> dailiesCompleted
        Achievement.MONOTONE -> bestEqualPairsRun
        Achievement.CLEAN_SLATE, Achievement.NO_HELP, Achievement.NIGHT_OWL, Achievement.IN_THE_RED -> 0
    }

    companion object {
        /** Starts with achievements already unlocked (QA and screenshots). */
        fun withUnlocked(unlocked: Set<Achievement>) = PlayerProgress(unlocked = unlocked)
    }
}
