package com.tavoohoh.quietnumbers.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Port of iOS `AchievementsTests`. */
class AchievementsTests {
    private fun outcome(values: List<Int> = listOf(3, 7), rows: List<Int> = emptyList(), boardCleared: Boolean = false, stage: Int = 1, hintsOnBoard: Int = 0, usedPlusOnBoard: Boolean = false) =
        MatchOutcome(MatchPair.of(0, 1, 0), 1, values, rows, emptyList(), boardCleared, stage, hintsOnBoard, usedPlusOnBoard)

    private class P(var p: PlayerProgress = PlayerProgress()) {
        fun record(e: ProgressEvent): List<Achievement> = p.record(e).let { (n, u) -> p = n; u }
    }

    @Test fun firstStep() {
        val p = P()
        assertEquals(listOf(Achievement.FIRST_STEP), p.record(ProgressEvent.GameFinished(null)))
        assertTrue(p.record(ProgressEvent.GameFinished(null)).isEmpty())
        assertEquals(listOf(Achievement.FIRST_STEP), P().record(ProgressEvent.Matched(outcome(boardCleared = true, stage = 2, hintsOnBoard = 1, usedPlusOnBoard = true))))
    }

    @Test fun bestScoreAndMerge() {
        val p = P()
        p.record(ProgressEvent.GameFinished(320)); p.record(ProgressEvent.GameFinished(150)); p.record(ProgressEvent.GameFinished(null))
        assertEquals(320, p.p.bestScore)
        val phone = P()
        phone.record(ProgressEvent.GameFinished(500))
        phone.record(ProgressEvent.Matched(outcome(rows = listOf(0, 1, 2))))
        val mac = P()
        mac.record(ProgressEvent.Matched(outcome(boardCleared = true, stage = 2)))
        mac.record(ProgressEvent.GameFinished(900))
        mac.record(ProgressEvent.ClassicAbandoned(100, true))
        val merged = phone.p.merged(mac.p)
        assertEquals(setOf(Achievement.FIRST_STEP, Achievement.CLEAN_SLATE, Achievement.NO_HELP), merged.unlocked)
        assertEquals(900, merged.bestScore)
        assertEquals(3, merged.rowsCleared)
        assertEquals(2, merged.gamesPlayed)
        assertEquals(merged.copy(unlocked = merged.unlocked.toSet()), mac.p.merged(phone.p).let { it.copy(unlocked = it.unlocked.toSet(), equalPairsRun = merged.equalPairsRun) })
    }

    @Test fun cleanSlateNoHelpInTheRed() {
        val p = P()
        p.record(ProgressEvent.Matched(outcome(boardCleared = true, stage = 2, hintsOnBoard = 1, usedPlusOnBoard = true)))
        assertEquals(listOf(Achievement.CLEAN_SLATE), p.record(ProgressEvent.Matched(outcome(boardCleared = true, stage = 3, hintsOnBoard = 1))))
        val q = P()
        q.record(ProgressEvent.GameFinished(40))
        assertEquals(listOf(Achievement.NO_HELP), q.record(ProgressEvent.Matched(outcome(boardCleared = true, stage = 2, usedPlusOnBoard = true))))
        val r = P()
        assertTrue(r.record(ProgressEvent.HintUsed(0)).isEmpty())
        assertEquals(listOf(Achievement.IN_THE_RED), r.record(ProgressEvent.HintUsed(-3)))
    }

    @Test fun abandonedAndLiveScores() {
        val p = P()
        p.record(ProgressEvent.ClassicAbandoned(210, true)); p.record(ProgressEvent.ClassicAbandoned(0, false))
        assertEquals(210, p.p.bestScore); assertEquals(0, p.p.gamesFinished); assertEquals(1, p.p.gamesPlayed)
        val q = P()
        q.record(ProgressEvent.ClassicScore(120)); q.record(ProgressEvent.ClassicScore(80))
        assertEquals(120, q.p.bestScore); assertEquals(0, q.p.gamesPlayed)
    }

    @Test fun practiceMarksTheDayAndNothingElse() {
        val p = P()
        assertTrue(p.record(ProgressEvent.PracticeCompleted("2026-10-01")).isEmpty())
        assertEquals(setOf("2026-10-01"), p.p.practicedDays)
        assertTrue(p.p.dailiesCompleted == 0 && p.p.bestStreak == 0)
        val o = P(); o.record(ProgressEvent.PracticeCompleted("2026-10-02"))
        assertEquals(setOf("2026-10-01", "2026-10-02"), p.p.merged(o.p).practicedDays)
    }

    @Test fun countedAchievements() {
        val p = P()
        repeat(49) { p.record(ProgressEvent.Matched(outcome(rows = listOf(0, 1)))) }
        assertEquals(0.98, p.p.progress(Achievement.SWEEPER))
        assertEquals(listOf(Achievement.SWEEPER), p.record(ProgressEvent.Matched(outcome(rows = listOf(3, 4)))))
        val s = P()
        assertTrue(s.record(ProgressEvent.Matched(outcome(stage = 4))).isEmpty())
        assertEquals(listOf(Achievement.STAGE_FIVE), s.record(ProgressEvent.Matched(outcome(stage = 5))))
        val m = P()
        repeat(9) { m.record(ProgressEvent.Matched(outcome(values = listOf(4, 4)))) }
        m.record(ProgressEvent.Matched(outcome(values = listOf(3, 7))))
        repeat(9) { m.record(ProgressEvent.Matched(outcome(values = listOf(6, 6)))) }
        assertFalse(Achievement.MONOTONE in m.p.unlocked)
        assertEquals(listOf(Achievement.MONOTONE), m.record(ProgressEvent.Matched(outcome(values = listOf(6, 6)))))
    }

    @Test fun undoTakesCountersBack() {
        val p = P()
        val match = outcome(values = listOf(4, 4), rows = listOf(0))
        p.record(ProgressEvent.Matched(match)); p.record(ProgressEvent.Undone(match))
        assertEquals(0, p.p.rowsCleared); assertEquals(0, p.p.equalPairsRun)
    }

    @Test fun streakAndNightOwl() {
        val p = P()
        assertEquals(setOf(Achievement.CONSISTENCY, Achievement.NIGHT_OWL), p.record(ProgressEvent.DailyCompleted(7, 2)).toSet())
        assertEquals(listOf(Achievement.HABIT), p.record(ProgressEvent.DailyCompleted(30, 12)))
        assertEquals(0.02, p.p.progress(Achievement.CENTENNIAL))
    }
}
