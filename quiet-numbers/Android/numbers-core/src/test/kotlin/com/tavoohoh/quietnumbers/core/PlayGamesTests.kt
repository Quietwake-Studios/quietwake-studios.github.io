package com.tavoohoh.quietnumbers.core

import com.tavoohoh.quiet.core.DailyKey
import com.tavoohoh.quiet.core.DailyRecord
import com.tavoohoh.quiet.core.ScoreTag
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Play Games mirrors iOS's Game Center: same IDs and points, what's pushed, and the saved game's merge. */
class PlayGamesTests {
    private val today = DailyKey.dayKey(Instant.parse("2026-11-14T15:00:00Z"))
    private fun day(offset: Int) = DailyKey.dayKey(today, offset)!!

    @Test fun sameIDsAndPointsAsGameCenter() {
        assertEquals(
            listOf("first_step", "clean_slate", "no_help", "consistency", "habit", "sweeper", "stage_five", "centennial", "night_owl", "monotone", "in_the_red"),
            Achievement.entries.map { it.conceptID },
        )
        assertEquals(620, Achievement.entries.sumOf { it.points })
        assertTrue(Achievement.entries.all { it.points % 5 == 0 })
        assertEquals(listOf("daily_challenge", "best_score", "longest_streak"), Leaderboard.entries.map { it.conceptID })
        assertEquals(setOf(Achievement.NIGHT_OWL, Achievement.MONOTONE, Achievement.IN_THE_RED), Achievement.entries.filter { it.isHiddenOnline }.toSet())
    }

    @Test fun countedAchievementsAreIncrementalWithTheirProgress() {
        val progress = PlayerProgress(unlocked = setOf(Achievement.FIRST_STEP, Achievement.CONSISTENCY), bestStreak = 9, rowsCleared = 42, highestStage = 3)
        val push = PlayGamesPlan.of(progress, emptyMap(), today)
        assertEquals(listOf("first_step"), push.unlocks)
        assertEquals(7, push.steps["consistency"])
        assertEquals(9, push.steps["habit"])
        assertEquals(42, push.steps["sweeper"])
        assertEquals(3, push.steps["stage_five"])
        assertNull(push.steps["centennial"])
    }

    /** Today's best after every attempt, even below zero, with its day and time; past days never; classic boards only real records. */
    @Test fun scoresMirrorWhatIOSSends() {
        val dailies = mapOf(
            today to DailyRecord(score = -7, isFinished = true, isCompleted = false, seconds = 95),
            day(-1) to DailyRecord(score = 600, isFinished = true, isCompleted = true),
            day(-2) to DailyRecord(score = 550, isFinished = true, isCompleted = true),
        )
        val push = PlayGamesPlan.of(PlayerProgress(bestScore = 0), dailies, today)
        val daily = push.scores.single { it.leaderboard == "daily_challenge" }
        assertEquals(-7L, daily.value)
        assertEquals(today, ScoreTag.day(daily.tag!!))
        assertEquals(95, ScoreTag.seconds(daily.tag!!))
        assertFalse(push.scores.any { it.leaderboard == "best_score" })
        assertEquals(2L, push.scores.single { it.leaderboard == "longest_streak" }.value)
        // Started but not finished: nothing for today.
        val started = PlayGamesPlan.of(PlayerProgress(bestScore = 900), mapOf(today to DailyRecord(score = 0)), today)
        assertEquals(listOf("best_score"), started.scores.map { it.leaderboard })
    }

    @Test fun theSavedGameMergesWithoutLosingProgress() {
        val a = CloudSnapshot(
            PlayerProgress(unlocked = setOf(Achievement.FIRST_STEP), bestScore = 900, rowsCleared = 10),
            mapOf(day(-1) to DailyRecord(score = 400, isFinished = true, isCompleted = false, attempts = 2)),
        )
        val b = CloudSnapshot(
            PlayerProgress(unlocked = setOf(Achievement.NO_HELP), bestScore = 700, rowsCleared = 30),
            mapOf(day(-1) to DailyRecord(score = 300, isFinished = true, isCompleted = true, attempts = 1), day(-2) to DailyRecord(score = 50, isFinished = true)),
        )
        val merged = CloudSnapshot.decode(CloudSnapshot.mergeBytes(a.encode(), b.encode()))!!
        assertEquals(setOf(Achievement.FIRST_STEP, Achievement.NO_HELP), merged.progress.unlocked)
        assertEquals(900, merged.progress.bestScore)
        assertEquals(30, merged.progress.rowsCleared)
        assertTrue(merged.dailies[day(-1)]!!.isCompleted)
        assertEquals(2, merged.dailies[day(-1)]!!.attempts)
        assertEquals(50, merged.dailies[day(-2)]!!.score)
        // An unreadable copy loses nothing.
        assertEquals(a, CloudSnapshot.decode(CloudSnapshot.mergeBytes(a.encode(), "nope".toByteArray())))
    }

    @Test fun mergingTheCloudFillsTheStreakHere() {
        val play = NumbersPlay(NumbersStore(null), today = { today })
        play.mergeCloud(CloudSnapshot(PlayerProgress(bestScore = 1200), mapOf(day(-1) to DailyRecord(score = 600, isFinished = true, isCompleted = true))))
        assertEquals(1200, play.progress.bestScore)
        assertEquals(1, play.currentStreak)
        assertEquals(1200L, play.playGamesPush().scores.single { it.leaderboard == "best_score" }.value)
    }
}
