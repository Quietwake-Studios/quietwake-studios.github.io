package com.tavoohoh.quietwords.core

import com.tavoohoh.quiet.core.DailyKey
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The family's daily rule, with one board and one leaderboard per language and one streak
 * (port of iOS `AppTests/DailyRuleTests`). `WordsPlay` is what the app's `AppModel` wraps.
 */
class DailyRuleTests {
    private val today = DailyKey.dayKey(Instant.now())

    private fun makePlay(store: WordsStore = WordsStore(null), cheapHints: () -> Boolean = { true }) =
        WordsPlay(store, today = { today }, cheapHintsEnabled = cheapHints)

    /** What the app's GameModel does on a move: the session changes, then the play hears the events. */
    private fun move(play: WordsPlay, session: GameSession, events: List<GameSession.Event>?) {
        play.update(session, events ?: return)
    }

    private fun findWord(play: WordsPlay, session: GameSession, index: Int) {
        val cells = session.board.placements[index].cells(session.board.side)
        move(play, session, session.select(cells.first(), cells.last()))
    }

    /** Finds every word of the game. */
    private fun win(play: WordsPlay, started: Started) {
        val session = started.session
        for (i in session.board.placements.indices) if (!session.isFound(i)) findWord(play, session, i)
        assertTrue(session.isWon)
    }

    private fun hint(play: WordsPlay, session: GameSession) = move(play, session, listOfNotNull(session.hint()))

    @Test fun todayCountsOnceForBothLanguages() {
        val play = makePlay()
        win(play, play.playDaily(today, WordLanguage.EN)!!)
        assertTrue(play.isCompleted(today, WordLanguage.EN))
        assertEquals(1, play.currentStreak)
        assertEquals(1, play.progress.dailiesCompleted)
        win(play, play.playDaily(today, WordLanguage.ES)!!)
        assertTrue(play.isCompleted(today, WordLanguage.ES))
        assertEquals(1, play.currentStreak)
        assertEquals(1, play.progress.dailiesCompleted)
        assertTrue(Achievement.BILINGUAL in play.progress.unlocked)
        assertEquals(setOf(WordLanguage.EN, WordLanguage.ES), play.doneLanguages(today))
    }

    @Test fun eachLanguageHasItsOwnBoard() {
        val play = makePlay()
        val en = play.playDaily(today, WordLanguage.EN)!!.session.board
        val es = play.playDaily(today, WordLanguage.ES)!!.session.board
        assertEquals(WordLanguage.EN, en.language)
        assertEquals(WordLanguage.ES, es.language)
        assertNotEquals(en.letters, es.letters)
    }

    @Test fun leavingKeepsTheLatestAttempt() {
        val store = WordsStore(null)
        val play = makePlay(store)
        val first = play.playDaily(today, WordLanguage.ES)!!
        findWord(play, first.session, 0)
        play.leftGame(first.session)
        assertTrue(play.isInProgress(today, WordLanguage.ES))
        // Coming back resumes it as it was.
        assertEquals(1, play.playDaily(today, WordLanguage.ES)!!.session.found.size)
        // Start over: the next attempt starts clean and replaces the saved one.
        play.startOver(today, WordLanguage.ES)
        val second = play.playDaily(today, WordLanguage.ES)!!
        assertTrue(second.session.found.isEmpty())
        assertEquals(GameKind.Daily(DailyID(today, WordLanguage.ES), isReplay = true), second.kind)
        // Restarting from the game's menu is a new attempt too, and replaces it.
        findWord(play, second.session, 0)
        play.restart(second.session, second.kind)
        val reopened = makePlay(store)
        assertTrue(reopened.playDaily(today, WordLanguage.ES)!!.session.found.isEmpty())
    }

    @Test fun aPastDayFillsTheStreak() {
        val play = makePlay()
        val yesterday = DailyKey.dayKey(today, -1)!!
        win(play, play.playDaily(today, WordLanguage.EN)!!)
        val past = play.playDaily(yesterday, WordLanguage.ES)!!
        assertEquals(GameKind.Daily(DailyID(yesterday, WordLanguage.ES), isReplay = false), past.kind)
        win(play, past)
        assertEquals(2, play.currentStreak)
        assertTrue(yesterday in play.progress.lateDays)
        // A second completion of the same past day doesn't count again.
        val replay = play.playDaily(yesterday, WordLanguage.ES)!!
        assertEquals(true, (replay.kind as GameKind.Daily).isReplay)
        win(play, replay)
        assertEquals(2, play.progress.dailiesCompleted)
    }

    @Test fun onlyTheLastThirtyDaysArePlayable() {
        val play = makePlay()
        assertTrue(play.isPlayable(DailyKey.dayKey(today, -DailyKey.REPLAY_WINDOW)!!))
        assertFalse(play.isPlayable(DailyKey.dayKey(today, -DailyKey.REPLAY_WINDOW - 1)!!))
        assertFalse(play.isPlayable(DailyKey.dayKey(today, 1)!!))
        assertNull(play.playDaily(DailyKey.dayKey(today, -40)!!, WordLanguage.EN))
    }

    @Test fun bestOfTheDayStays() {
        val play = makePlay()
        win(play, play.playDaily(today, WordLanguage.EN)!!)
        val best = play.store.record(DailyID(today, WordLanguage.EN))!!.score
        val again = play.playDaily(today, WordLanguage.EN)!!
        hint(play, again.session)
        hint(play, again.session)
        win(play, again)
        assertEquals(best, play.store.record(DailyID(today, WordLanguage.EN))!!.score)
        assertEquals(2, play.store.attempts(DailyID(today, WordLanguage.EN)))
    }

    @Test fun freeGameContinuesAfterRelaunch() {
        val store = WordsStore(null)
        val play = makePlay(store)
        val started = play.newFree(FreeGame.session("space", Level.MEDIUM, WordLanguage.EN, "free-test")!!)
        findWord(play, started.session, 0)
        play.leftGame(started.session)
        val reopened = makePlay(store)
        assertEquals(1, reopened.freeInProgress?.found?.size)
        assertEquals("space", reopened.freeInProgress?.themeID)
    }

    @Test fun winningFillsTheAlbum() {
        val play = makePlay()
        val started = play.newFree(FreeGame.session("botany", Level.GENTLE, WordLanguage.EN, "free-album")!!)
        val count = started.session.board.placements.size
        win(play, started)
        val botany = play.library.theme("botany")!!
        assertEquals(count, play.profile.album.count(botany, WordLanguage.EN).found)
        assertEquals(count, play.newAlbumWords(started.session))
        assertEquals(count, play.progress.wordsFound)
        assertTrue(Achievement.FIRST_PUZZLE in play.unlockedThisGame)
        assertNull(play.freeInProgress)
    }

    @Test fun resetErasesProgress() {
        val store = WordsStore(null)
        val play = makePlay(store)
        win(play, play.playDaily(today, WordLanguage.EN)!!)
        val free = play.newFree(FreeGame.session("ocean", Level.GENTLE, WordLanguage.EN, "reset")!!)
        findWord(play, free.session, 0)
        play.reset()
        assertEquals(0, play.currentStreak)
        assertNull(play.freeInProgress)
        assertEquals(PlayerProgress(), play.progress)
        assertTrue(store.dailies.isEmpty() && store.dailiesInProgress.isEmpty())
        assertNull(makePlay(store).freeInProgress)
    }

    /** "In the Red": hints cost 10 in free play only (unless turned off), never in a daily. */
    @Test fun inTheRedMakesFreeHintsCheaper() {
        var enabled = true
        val play = makePlay(cheapHints = { enabled })
        val free = play.newFree(FreeGame.session("ocean", Level.GENTLE, WordLanguage.EN, "red", scoring = play.freeScoring)!!)
        assertEquals(Scoring.STANDARD, free.session.scoring)
        hint(play, free.session)
        assertEquals(-30, free.session.score)
        assertTrue(Achievement.IN_THE_RED in play.progress.unlocked)
        assertEquals(Scoring.CHEAP_HINTS, play.freeScoring)
        // The game in progress keeps its price; the next one is cheaper.
        hint(play, free.session)
        assertEquals(-60, free.session.score)
        val next = play.newFree(FreeGame.session("ocean", Level.GENTLE, WordLanguage.EN, "red2", scoring = play.freeScoring)!!)
        hint(play, next.session)
        assertEquals(-10, next.session.score)
        // Dailies, today's or past, always cost the normal price.
        val daily = play.playDaily(today, WordLanguage.EN)!!
        hint(play, daily.session)
        assertEquals(Scoring.STANDARD, daily.session.scoring)
        assertEquals(-30, daily.session.score)
        assertEquals(Scoring.STANDARD, play.playDaily(DailyKey.dayKey(today, -3)!!, WordLanguage.ES)!!.session.scoring)
        assertEquals(Scoring.STANDARD, play.restart(daily.session, daily.kind).session.scoring)
        enabled = false
        assertEquals(Scoring.STANDARD, play.freeScoring)
    }
}

/** Saving to disk and reading it back (the app's store is files under its own folder). */
class StoreTests {
    private val today = DailyKey.dayKey(Instant.now())

    private fun tempDir(): File = Files.createTempDirectory("words-store").toFile().also { it.deleteOnExit() }

    @Test fun everythingRoundTrips() {
        val dir = tempDir()
        val store = WordsStore(dir)
        val session = DailyChallenge.session(today, WordLanguage.ES)!!
        val cells = session.board.placements[1].cells(session.board.side)
        session.select(cells.last(), cells.first())
        session.hint()
        repeat(42) { session.tick() }
        val id = DailyID(today, WordLanguage.ES)
        store.startAttempt(id)
        store.saveGame(session, Slot.Daily(id))
        store.recordWin(DailyID("2026-11-01", WordLanguage.EN), 640, 200)
        val (progress, _) = PlayerProgress().record(ProgressEvent.WordsFound(12))
        store.saveProfile(Profile(progress, Album().add(listOf("CORAL"), "ocean", WordLanguage.EN).first))

        val reopened = WordsStore(dir)
        assertEquals(session, reopened.loadGame(Slot.Daily(id)))
        assertEquals(setOf(id), reopened.dailiesInProgress)
        assertEquals(setOf(today), reopened.daysInProgress)
        assertEquals(DailyRecord(640, true, 200, 0), reopened.record(DailyID("2026-11-01", WordLanguage.EN)))
        assertEquals(1, reopened.attempts(id))
        assertEquals(setOf("2026-11-01"), reopened.completedDays)
        assertEquals(12, reopened.loadProfile().progress.wordsFound)
        assertEquals(mapOf("ocean/en" to setOf("CORAL")), reopened.loadProfile().album.words)
    }

    /** The bug found in Quiet Numbers (2026-11-02), across a relaunch: never an earlier attempt. */
    @Test fun startOverKeepsTheLatestAttemptAfterRelaunch() {
        val dir = tempDir()
        val play = WordsPlay(WordsStore(dir), today = { today })
        val first = play.playDaily(today, WordLanguage.EN)!!
        for (i in 0..1) {
            val cells = first.session.board.placements[i].cells(first.session.board.side)
            play.update(first.session, first.session.select(cells.first(), cells.last())!!)
        }
        play.leftGame(first.session)
        assertEquals(2, WordsPlay(WordsStore(dir), today = { today }).playDaily(today, WordLanguage.EN)!!.session.found.size)

        val again = WordsPlay(WordsStore(dir), today = { today })
        again.startOver(today, WordLanguage.EN)
        val second = again.playDaily(today, WordLanguage.EN)!!
        val cells = second.session.board.placements[3].cells(second.session.board.side)
        again.update(second.session, second.session.select(cells.first(), cells.last())!!)
        again.leftGame(second.session)

        val third = WordsPlay(WordsStore(dir), today = { today }).playDaily(today, WordLanguage.EN)!!
        assertEquals(listOf(3), third.session.found)
        assertTrue((third.kind as GameKind.Daily).isReplay)
    }

    @Test fun oldDailyGamesAreDropped() {
        val dir = tempDir()
        val store = WordsStore(dir)
        val old = DailyKey.dayKey(today, -31)!!
        val session = DailyChallenge.session(old, WordLanguage.EN)!!
        store.saveGame(session, Slot.Daily(DailyID(old, WordLanguage.EN)))
        WordsPlay(store, today = { today })
        assertTrue(WordsStore(dir).dailiesInProgress.isEmpty())
    }

    @Test fun aBrokenFileStartsEmpty() {
        val dir = tempDir()
        File(dir, "dailies.json").writeText("{ not json")
        assertTrue(WordsStore(dir).dailies.isEmpty())
    }

    @Test fun mergeKeepsTheBetterResult() {
        val store = WordsStore(null)
        val id = DailyID("2026-11-02", WordLanguage.ES)
        store.recordWin(id, 500, 120)
        store.mergeDailies(mapOf(id.key to DailyRecord(500, true, 100), "2026-11-03#en" to DailyRecord(300, true, 90)))
        assertEquals(100, store.record(id)!!.seconds)
        assertEquals(setOf("2026-11-02", "2026-11-03"), store.completedDays)
    }
}
