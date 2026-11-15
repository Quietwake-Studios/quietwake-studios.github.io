package com.tavoohoh.quietnumbers.core

import com.tavoohoh.quiet.core.DailyKey
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of iOS `AppModelTests` (daily rule, saving, achievements wiring, filled days, attempts). */
class NumbersPlayTests {
    private val today = DailyKey.dayKey(Instant.now())
    private fun day(offset: Int) = DailyKey.dayKey(today, offset)!!
    private fun makePlay(store: NumbersStore = NumbersStore(null), hour: Int = 12, cheap: () -> Boolean = { true }) =
        NumbersPlay(store, today = { today }, hour = { hour }, cheapHintsEnabled = cheap)

    /** What the app's GameModel does on a move: the session changes, the play hears the event, then saves. */
    private fun match(play: NumbersPlay, s: GameSession, a: Int, b: Int) {
        val outcome = s.match(a, b) ?: return
        play.record(ProgressEvent.Matched(outcome))
        play.sessionChanged(s)
    }

    private fun first(play: NumbersPlay, s: GameSession, index: Int = 0) {
        val pairs = MatchRules.validPairs(s.board)
        val p = pairs[minOf(index, pairs.size - 1)]
        match(play, s, p.first, p.second)
    }

    /** Completes `dayKey` on a board that reaches a goal of 2 in two moves, in `seconds`. */
    private fun complete(play: NumbersPlay, dayKey: String, seconds: Int = 0) {
        val s = GameSession.withBoard(Board.of(listOf(4, 4, 1, 2, 1, 2, 1, 2, 3, 7)), GameMode.Daily(dayKey), goal = 2)
        play.begin(s, GameKind.Daily(dayKey), play.hasPlayed(dayKey))
        match(play, s, 0, 1)
        s.addTime(seconds)
        match(play, s, 8, 9)
    }

    @Test fun firstDailyOfTheDayIsSavedRightAway() {
        val play = makePlay()
        assertFalse(play.hasPlayed(today))
        val started = play.playDaily(today)
        assertEquals(GameKind.Daily(today), started.kind)
        assertTrue(play.isDailyInProgress(today))
        assertFalse(started.isReplay)
        assertNull(play.record(today))
    }

    @Test fun dailyResumesWhileInProgressAndReplays() {
        val play = makePlay()
        val first = play.playDaily(today).session
        assertEquals(first, play.playDaily(today).session)
        play.store.finishDaily(today, 120, true)
        play.startOver(today)
        assertTrue(play.playDaily(today).isReplay)
        assertFalse(play.playDaily(day(-1)).isReplay)
    }

    @Test fun streakCountsCompletedDaysOnly() {
        val play = makePlay()
        play.store.finishDaily(day(-1), 50, true)
        assertEquals(1, play.currentStreak)
        play.store.finishDaily(today, 10, false)
        assertEquals(1, play.currentStreak)
        play.store.finishDaily(today, 90, true)
        assertEquals(2, play.currentStreak)
    }

    @Test fun classicGameIsSavedAndRestored() {
        val store = NumbersStore(null)
        val play = makePlay(store)
        val s = play.newClassic(7uL).session
        s.addNumbers()
        play.sessionChanged(s)
        assertTrue(play.hasClassicInProgress)
        assertEquals(s, makePlay(store).continueClassic()!!.session)
    }

    @Test fun unlocksAreAnnouncedAndPersisted() {
        val store = NumbersStore(null)
        val play = makePlay(store)
        play.record(ProgressEvent.GameFinished(null))
        assertEquals(listOf(Achievement.FIRST_STEP), play.pendingUnlocks)
        play.dismissUnlock(Achievement.FIRST_STEP)
        assertTrue(play.pendingUnlocks.isEmpty())
        assertEquals(setOf(Achievement.FIRST_STEP), makePlay(store).progress.unlocked)
    }

    @Test fun newGameKeepsTheAbandonedScoreAsBest() {
        val play = makePlay()
        val s = play.newClassic(3uL).session
        while (s.score < 5) {
            val p = MatchRules.validPairs(s.board).firstOrNull() ?: break
            s.match(p.first, p.second)
        }
        play.store.saveGame(s, NumbersStore.CLASSIC)
        play.newClassic(4uL)
        assertEquals(s.score, play.progress.bestScore)
        assertTrue(play.progress.bestScore > 0)
        assertEquals(0, play.progress.gamesFinished)
        assertEquals(1, play.progress.gamesPlayed)
    }

    @Test fun inTheRedMakesHintsCheaperOnlyInClassic() {
        var enabled = true
        val play = makePlay(cheap = { enabled })
        assertEquals(10, play.newClassic(1uL).session.scoring.hintCost)
        play.record(ProgressEvent.HintUsed(-10))
        assertEquals(3, play.newClassic(2uL).session.scoring.hintCost)
        assertEquals(10, play.playDaily(today).session.scoring.hintCost)
        assertEquals(10, play.playDaily(day(-2)).session.scoring.hintCost)
        enabled = false
        assertEquals(Scoring.STANDARD, play.classicScoring)
        assertEquals(10, play.newClassic(3uL).session.scoring.hintCost)
    }

    @Test fun clearingAPastDayMarksAndFillsIt() {
        val play = makePlay()
        val d = day(-5)
        val s = GameSession.withValues(listOf(4, 4), GameMode.Daily(d), seed = 0uL)
        play.begin(s, GameKind.Daily(d))
        match(play, s, 0, 1)
        assertTrue(s.isCompleted)
        assertEquals(setOf(d), play.progress.practicedDays)
        assertEquals(true, play.record(d)?.isCompleted)
        assertEquals(1, play.longestStreak)
    }

    @Test fun aPastDayIsSavedAndResumedAfterRelaunch() {
        val dir = Files.createTempDirectory("numbers").toFile().also(File::deleteOnExit)
        val play = makePlay(NumbersStore(dir))
        val d = day(-3)
        val s = play.playDaily(d).session
        first(play, s)
        assertEquals(setOf(d), play.daysInProgress)
        assertEquals(s, play.playDaily(d).session)
        val again = makePlay(NumbersStore(dir))
        assertEquals(setOf(d), again.daysInProgress)
        assertEquals(s, again.playDaily(d).session)
        assertNull(again.record(d))
    }

    @Test fun aFinishedAttemptIsDroppedAndOldOnesTooAfterTheWindow() {
        val play = makePlay()
        val d = day(-10)
        val s = GameSession.withValues(listOf(4, 4), GameMode.Daily(d), seed = 0uL)
        play.begin(s, GameKind.Daily(d))
        play.store.saveGame(s, NumbersStore.day(d))
        match(play, s, 0, 1)
        assertTrue(play.daysInProgress.isEmpty())
        val store = NumbersStore(null)
        store.saveGame(DailyChallenge.session(day(-40)), NumbersStore.day(day(-40)))
        store.saveGame(DailyChallenge.session(day(-20)), NumbersStore.day(day(-20)))
        makePlay(store)
        assertEquals(setOf(day(-20)), store.daysInProgress)
    }

    @Test fun reachingTheGoalCompletesTheDayOnce() {
        val play = makePlay(hour = 2)
        val s = GameSession.withBoard(Board.of(listOf(4, 4, 1, 2, 1, 2, 1, 2, 1, 3, 7, 2, 1, 2, 1, 2, 1, 2)), GameMode.Daily(today), score = 399, goal = 400)
        play.begin(s, GameKind.Daily(today))
        match(play, s, 0, 1)
        assertTrue(s.isCompleted && s.isOver)
        val r = play.record(today)!!
        assertTrue(r.isCompleted && r.isFinished)
        assertEquals(400, r.score)
        assertEquals(1, play.currentStreak)
        assertEquals(1, play.progress.dailiesCompleted)
        assertFalse(play.isDailyInProgress(today))
        assertTrue(Achievement.FIRST_STEP in play.unlockedThisGame)
        assertTrue(Achievement.NIGHT_OWL in play.unlockedThisGame)
        assertTrue(play.pendingUnlocks.isEmpty())
        // The first completion offers the reminders once; today is done for them.
        assertTrue(play.leftGame())
        assertFalse(play.leftGame())
        assertEquals(true to 1, play.reminderState())
    }

    @Test fun startingOverDealsTheDayAgain() {
        val play = makePlay()
        val d = day(-3)
        first(play, play.playDaily(d).session)
        assertTrue(d in play.daysInProgress)
        play.startOver(d)
        assertFalse(d in play.daysInProgress)
        assertEquals(0, play.playDaily(d).session.score)
    }

    @Test fun newBestShowsDuringTheGame() {
        val play = makePlay()
        val s = play.newClassic(9uL).session
        first(play, s)
        assertEquals(s.score, play.progress.bestScore)
        assertTrue(play.progress.bestScore > 0)
    }

    @Test fun resetErasesProgressGamesAndDailies() {
        val store = NumbersStore(null)
        val play = makePlay(store)
        play.record(ProgressEvent.GameFinished(300))
        play.newClassic(1uL)
        store.finishDaily("2026-10-20", 120, true)
        play.reset()
        assertEquals(PlayerProgress(), play.progress)
        assertFalse(play.hasClassicInProgress)
        assertTrue(store.dailies.isEmpty())
        assertEquals(PlayerProgress(), makePlay(store).progress)
    }

    @Test fun finishingADailyKeepsItsTime() {
        val play = makePlay()
        complete(play, today, seconds = 42)
        assertEquals(42, play.record(today)?.seconds)
        assertEquals(true, play.record(today)?.isFinished)
    }

    @Test fun completingAMissedDayFillsTheStreakOnce() {
        val play = makePlay()
        play.store.finishDaily(day(-2), 500, true)
        play.store.finishDaily(today, 450, true)
        assertEquals(1, play.currentStreak)
        complete(play, day(-1))
        assertEquals(true, play.record(day(-1))?.isCompleted)
        assertEquals(3, play.currentStreak)
        assertEquals(1, play.progress.dailiesCompleted)
        complete(play, day(-1))
        assertEquals(1, play.progress.dailiesCompleted)
    }

    @Test fun completingAFailedDayKeepsTheCompletedResult() {
        val play = makePlay()
        val d = day(-4)
        play.store.finishDaily(d, 310, false)
        complete(play, d)
        assertEquals(true, play.record(d)?.isCompleted)
        assertNotEquals(310, play.record(d)?.score)
    }

    /** Start, play, start over, play differently, leave, relaunch: the latest attempt continues (bug of 2026-11-02). */
    @Test fun startingOverKeepsTheLatestAttempt() {
        for (offset in listOf(0, -3)) {
            val dir = Files.createTempDirectory("numbers-latest").toFile().also(File::deleteOnExit)
            val play = makePlay(NumbersStore(dir))
            val d = day(offset)
            val one = play.playDaily(d).session
            first(play, one, 0)
            play.leftGame()
            play.startOver(d)
            val two = play.playDaily(d).session
            assertEquals(0, two.score)
            first(play, two, 1)
            first(play, two, 0)
            assertNotEquals(one, two)
            play.leftGame()
            val relaunched = makePlay(NumbersStore(dir))
            assertTrue(d in relaunched.daysInProgress)
            assertEquals(two, relaunched.playDaily(d).session)
        }
    }

    @Test fun todayReplaysWithoutLimitAndEveryAttemptCounts() {
        val play = makePlay()
        complete(play, today, 50)
        assertEquals(50, play.dailyToSubmit?.seconds)
        assertEquals(1, play.progress.dailiesCompleted)
        assertTrue(play.playDaily(today).isReplay)
        complete(play, today, 20)
        assertEquals(20, play.dailyToSubmit?.seconds)
        complete(play, today, 90)
        assertEquals(20, play.dailyToSubmit?.seconds)
        assertEquals(true, play.dailyToSubmit?.isCompleted)
        assertEquals(1, play.progress.dailiesCompleted)
    }

    @Test fun aPastDayKeepsItsBestAndSendsNothing() {
        val play = makePlay()
        val d = day(-2)
        play.store.finishDaily(d, 1, false, 30)
        complete(play, d, 40)
        assertEquals(true, play.record(d)?.isCompleted)
        assertEquals(40, play.record(d)?.seconds)
        complete(play, d, 60)
        assertEquals(40, play.record(d)?.seconds)
        assertNull(play.dailyToSubmit)
    }

    @Test fun onlyTheLastThirtyDaysArePlayable() {
        val play = makePlay()
        assertTrue(play.isPlayable(day(-30)))
        assertFalse(play.isPlayable(day(-31)))
        assertFalse(play.isPlayable(day(1)))
        assertNotNull(play.playDaily(day(-30)))
    }
}
