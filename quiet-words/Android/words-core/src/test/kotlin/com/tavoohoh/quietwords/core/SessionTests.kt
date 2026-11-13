package com.tavoohoh.quietwords.core

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class SessionTests {
    private fun session(level: Level = Level.MEDIUM): GameSession {
        val words = ThemeLibrary.shared.theme("ocean")!!.words(WordLanguage.EN)!!
        val board = Generator.board("session", words.all, words.hidden, WordLanguage.EN, level,
            blocklist = ThemeLibrary.shared.blocklist.getValue(WordLanguage.EN))!!
        return GameSession(GameSession.Mode.Free, "ocean", level, board)
    }

    private fun ends(s: GameSession, i: Int): Pair<Int, Int> = s.board.placements[i].cells(s.board.side).let { it.first() to it.last() }

    @Test fun findsAWordEitherWay() {
        val s = session()
        val (a, b) = ends(s, 0)
        val points = s.board.placements[0].word.count * 10
        assertEquals(listOf(GameSession.Event.Found(0, points)), s.select(b, a))
        assertEquals(listOf(GameSession.Event.AlreadyFound(0)), s.select(a, b))
        assertEquals(points, s.score)
    }

    @Test fun missesAndBadLines() {
        val s = session()
        val side = s.board.side
        assertNull(s.select(0, 2 * side + 1))
        assertNull(s.select(0, 0))
        val (a, b) = ends(s, 0)
        val shorter = s.board.line(a, b)!!.dropLast(1).last()
        assertEquals(listOf(GameSession.Event.Miss), s.select(a, shorter))
        assertTrue(s.found.isEmpty())
    }

    @Test fun hintsCostEvenBelowZero() {
        val s = session()
        val hint = s.hint() as? GameSession.Event.Hint ?: fail("no hint")
        assertEquals(s.board.placements[hint.index].cells(s.board.side).first(), hint.cell)
        assertEquals(listOf(hint.cell), s.hintCells)
        assertEquals(-30, s.score)
        val (a, b) = ends(s, hint.index)
        s.select(a, b)
        assertTrue(s.hintCells.isEmpty())
        assertEquals(s.board.placements[hint.index].word.count * 10 - 30, s.score)
    }

    @Test fun threeGuessesForTheHiddenWord() {
        val s = session()
        assertEquals(GameSession.Event.GuessWrong(2), s.guess("nope"))
        assertEquals(GameSession.Event.GuessRight(100), s.guess(s.board.hidden.lowercase()))
        assertEquals(GameSession.HiddenState.GUESSED, s.hiddenState)
        assertNull(s.guess("again"))
        val t = session()
        for (left in listOf(2, 1, 0)) assertEquals(GameSession.Event.GuessWrong(left), t.guess("x"))
        assertFalse(t.canGuess)
    }

    @Test fun lastWordRevealsAndScores() {
        val s = session(Level.GENTLE)
        repeat(60) { s.tick() }
        var last: List<GameSession.Event> = emptyList()
        for (i in s.board.placements.indices) {
            val (a, b) = ends(s, i)
            last = s.select(a, b)!!
        }
        assertTrue(s.isWon && s.hiddenState == GameSession.HiddenState.REVEALED)
        val words = s.board.placements.sumOf { it.word.count * 10 }
        val bonus = words * 50 * 120 / (180 * 100)
        assertEquals(bonus, s.timeBonus)
        assertEquals(words + bonus + 30, s.score)
        val summary = (last.last() as? GameSession.Event.Won)?.summary ?: fail("no win")
        assertTrue(summary.score == s.score && !summary.hiddenGuessed && summary.foundLetters.size == s.board.placements.size)
        s.tick()
        assertEquals(60, s.elapsedSeconds)
    }

    @Test fun roundTripsThroughJson() {
        val s = session()
        val (a, b) = ends(s, 1)
        s.select(a, b)
        s.hint()
        val json = Json.encodeToString(GameSession.serializer(), s)
        assertEquals(s, Json.decodeFromString(GameSession.serializer(), json))
        val daily = GameSession(GameSession.Mode.Daily("2026-11-13"), "ocean", s.level, s.board)
        assertEquals(daily, Json.decodeFromString(GameSession.serializer(), Json.encodeToString(GameSession.serializer(), daily)))
    }

    @Test fun cheapHints() {
        val base = session()
        val s = GameSession(GameSession.Mode.Free, base.themeID, base.level, base.board, Scoring.CHEAP_HINTS)
        s.hint()
        s.hint()
        assertEquals(-20, s.score)
        val decoded = Json.decodeFromString(GameSession.serializer(), Json.encodeToString(GameSession.serializer(), s))
        assertTrue(decoded.scoring == Scoring.CHEAP_HINTS && decoded.score == -20)
        assertEquals(30, Scoring.STANDARD.hint)
    }

    @Test fun copyIsIndependent() {
        val s = session()
        val c = s.copy()
        s.hint()
        assertEquals(0, c.hintsUsed)
        assertEquals(1, s.hintsUsed)
    }
}
