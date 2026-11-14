package com.tavoohoh.quietnumbers.core

import com.tavoohoh.quiet.core.SeededRandom
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Builds a board from rows; `0` marks a cleared cell. */
fun makeBoard(vararg rows: List<Int>): Board {
    val flat = rows.flatMap { it }
    var board = Board.of(flat.map { if (it == 0) 1 else it })
    flat.forEachIndexed { i, v -> if (v == 0) board = board.cleared(i) }
    return board
}

/** Port of iOS `GameSessionTests`. */
class GameSessionTests {
    private fun session(board: Board, mode: GameMode = GameMode.Classic, plusRemaining: Int = GameSession.MAX_PLUS_USES, stage: Int = 1, score: Int = 0, goal: Int? = null) =
        GameSession.withBoard(board, mode, plusRemaining = plusRemaining, stage = stage, score = score, goal = goal)

    @Test fun adjacentPairScoresOne() {
        val s = session(makeBoard(listOf(4, 4, 1, 2, 1, 2, 1, 2, 1), listOf(2, 1, 2, 1, 2, 1, 2, 1, 2)))
        assertEquals(1, s.match(0, 1)?.points)
        assertEquals(1, s.score)
        assertTrue(s.board.cells[0].isCleared && s.board.cells[1].isCleared)
    }

    @Test fun pairAcrossClearedScoresFour() {
        val s = session(makeBoard(listOf(6, 0, 4, 1, 2, 1, 2, 1, 2), listOf(2, 1, 2, 1, 2, 1, 2, 1, 2)))
        assertEquals(4, s.match(0, 2)?.points)
    }

    @Test fun invalidMatchChangesNothing() {
        val s = session(makeBoard(listOf(2, 3, 1, 2, 1, 2, 1, 2, 1)))
        val before = s.copy()
        assertNull(s.match(0, 1))
        assertEquals(before, s)
    }

    @Test fun emptiedRowCollapsesAndScoresBonus() {
        val s = session(makeBoard(listOf(0, 0, 0, 0, 0, 0, 0, 3, 7), listOf(2, 1, 2, 1, 2, 1, 2, 1, 2)), plusRemaining = 2)
        val o = s.match(7, 8)
        assertEquals(listOf(0), o?.clearedRows)
        assertEquals(11, o?.points)
        assertEquals(1, s.board.rowCount)
        assertEquals(listOf(2, 1, 2, 1, 2, 1, 2, 1, 2), s.board.cells.map { it.value })
        assertEquals(2, s.plusRemaining)
    }

    @Test fun clearingTwoRowsAtOnce() {
        val s = session(makeBoard(listOf(0, 0, 0, 0, 0, 0, 0, 0, 5), listOf(5, 0, 0, 0, 0, 0, 0, 0, 0), listOf(2, 1, 2, 1, 2, 1, 2, 1, 2)), plusRemaining = 0)
        assertEquals(listOf(0, 1), s.match(8, 9)?.clearedRows)
        assertEquals(1, s.board.rowCount)
    }

    @Test fun middleRowCollapseKeepsColumns() {
        val s = session(makeBoard(listOf(1, 2, 1, 2, 1, 2, 1, 2, 1), listOf(0, 0, 0, 0, 0, 0, 0, 4, 6), listOf(9, 8, 7, 6, 5, 4, 3, 2, 1)))
        assertEquals(listOf(1), s.match(16, 17)?.clearedRows)
        assertEquals(listOf(1, 2, 1, 2, 1, 2, 1, 2, 1, 9, 8, 7, 6, 5, 4, 3, 2, 1), s.board.cells.map { it.value })
    }

    @Test fun plusCopiesRemainingNumbersInOrder() {
        val s = session(makeBoard(listOf(1, 0, 2, 0, 3, 4, 5, 6, 7)))
        assertTrue(s.addNumbers())
        assertEquals(GameSession.MAX_PLUS_USES - 1, s.plusRemaining)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 1, 2, 3, 4, 5, 6, 7), s.board.activeValues)
        assertEquals(s.board.cells.size, s.board.cells.map { it.id }.toSet().size)
    }

    @Test fun plusIsLimitedAndGameOver() {
        assertFalse(session(makeBoard(listOf(1, 2, 1, 2, 1, 2, 1, 2, 1)), plusRemaining = 0).addNumbers())
        val stuck = makeBoard(listOf(1, 2, 1, 2, 1, 2, 1, 2, 1))
        assertFalse(session(stuck, plusRemaining = 1).isOver)
        assertTrue(session(stuck, plusRemaining = 0).isOver)
        assertFalse(session(makeBoard(listOf(1, 1, 2)), plusRemaining = 0).isOver)
    }

    @Test fun clearingBoardAdvancesStage() {
        val s = session(makeBoard(listOf(0, 0, 0, 0, 0, 0, 0, 2, 8)), plusRemaining = 1)
        val o = s.match(7, 8)
        assertEquals(true, o?.boardCleared)
        assertEquals(1 + 10 + 150, o?.points)
        assertEquals(2, s.stage)
        assertEquals(GameSession.MAX_PLUS_USES, s.plusRemaining)
        assertEquals(BoardGenerator.initialCount(2), s.board.cells.size)
        assertFalse(s.canUndo)
    }

    @Test fun laterStagesAreHarder() {
        assertEquals(listOf(42, 44, 48, 50, 54, 54), (1..6).map(BoardGenerator::initialCount))
        assertEquals(listOf(5, 5, 4, 4, 3, 3), (1..6).map(GameSession::plusUses))
        assertEquals(listOf(3..5, 2..4, 2..3, 2..3, 2..3, 2..3), (1..6).map(BoardGenerator::startingPairs))
        assertEquals(listOf(8, 7, 6, 6, 5, 5), (1..6).map(BoardGenerator::typicalMoves))
        val s = session(makeBoard(listOf(0, 0, 0, 0, 0, 0, 0, 2, 8)), stage = 4)
        s.match(7, 8)
        assertEquals(5, s.stage)
        assertEquals(3, s.plusRemaining)
        assertEquals(54, s.board.cells.size)
        assertTrue(MatchRules.validPairs(s.board).size in BoardGenerator.startingPairs(5))
    }

    @Test fun hintsAndPlusAreCountedPerBoard() {
        val s = session(makeBoard(listOf(0, 0, 0, 0, 0, 2, 8, 1, 9)))
        s.useHint()
        s.addNumbers()
        assertTrue(s.hintsOnBoard == 1 && s.usedPlusOnBoard)
        while (s.stage == 1) {
            val pair = MatchRules.validPairs(s.board).firstOrNull() ?: break
            val o = s.match(pair.first, pair.second)
            if (o?.boardCleared == true) {
                assertEquals(1, o.hintsOnBoard)
                assertTrue(o.usedPlusOnBoard)
            }
        }
        assertEquals(2, s.stage)
        assertTrue(s.hintsOnBoard == 0 && !s.usedPlusOnBoard)
        assertEquals(1, s.hintsUsed)
    }

    @Test fun boardBonusScalesWithStage() {
        val s = session(makeBoard(listOf(0, 0, 0, 0, 0, 0, 0, 2, 8)), stage = 3)
        assertEquals(2 + 10 + 150 * 3, s.match(7, 8)?.points)
        assertEquals(4, s.stage)
    }

    @Test fun dailyWithoutGoalEndsAfterOneStage() {
        val s = session(makeBoard(listOf(0, 0, 0, 0, 0, 0, 0, 2, 8)), GameMode.Daily("2026-10-25"))
        assertEquals(true, s.match(7, 8)?.boardCleared)
        assertTrue(s.isCompleted && s.isOver && s.stage == 1)
    }

    @Test fun dailyGoesOnToNextStageUntilTheGoal() {
        val board = makeBoard(listOf(0, 0, 0, 0, 0, 0, 0, 2, 8))
        val s = session(board, GameMode.Daily("2026-10-27"), goal = 400)
        val cleared = s.match(7, 8)
        assertTrue(cleared?.boardCleared == true && cleared.reachedGoal == false)
        assertTrue(s.stage == 2 && !s.isCompleted && !s.isOver)
        val near = session(board, GameMode.Daily("2026-10-27"), score = 300, goal = 400)
        assertEquals(true, near.match(7, 8)?.reachedGoal)
        assertTrue(near.isCompleted && near.isOver && near.stage == 1)
    }

    @Test fun nothingIsPlayableAfterTheGoal() {
        val s = session(makeBoard(listOf(4, 4, 1, 2, 1, 2, 1, 2, 3, 7)), GameMode.Daily("2026-10-27"), score = 399, goal = 400)
        s.match(0, 1)
        assertTrue(s.isCompleted && s.isOver)
        assertNull(s.useHint())
        assertNull(s.match(8, 9))
        assertTrue(!s.addNumbers() && !s.canUndo)
        assertEquals(400, s.score)
    }

    @Test fun statsCountTheGameAndUndoTakesTheMoveBack() {
        val s = session(makeBoard(listOf(4, 4, 1, 2, 1, 2, 1, 2, 3), listOf(7, 5, 5, 6, 6, 1, 1, 2, 3)))
        s.match(0, 1)
        s.match(8, 9)
        s.addNumbers()
        assertEquals(Stats(pairs = 2, rowsCleared = 0, plusUsed = 1, longestStreak = 2), s.stats)
        s.undo()
        assertTrue(s.stats.plusUsed == 0 && s.stats.pairs == 2)
        s.match(10, 11)
        s.undo()
        assertEquals(2, s.stats.pairs)
    }

    @Test fun timeCountsOnlyFromTheFirstMoveUntilTheEnd() {
        val s = session(makeBoard(listOf(4, 4, 1, 2, 1, 2, 1, 2, 3, 7)), GameMode.Daily("2026-10-27"), goal = 2)
        s.addTime(30)
        assertEquals(0, s.elapsedSeconds)
        s.match(0, 1)
        s.addTime(12)
        assertEquals(12, s.elapsedSeconds)
        s.match(8, 9)
        assertTrue(s.isOver)
        s.addTime(5)
        assertEquals(12, s.elapsedSeconds)
    }

    @Test fun hintsCostEvenBelowZero() {
        val s = session(makeBoard(listOf(1, 2, 3, 3, 1, 2, 1, 2, 1)), score = 25)
        val (pair, cost) = assertNotNull(s.useHint())
        assertNotNull(MatchRules.pair(s.board, pair.first, pair.second))
        assertEquals(10, cost)
        assertEquals(15, s.score)
        assertEquals(1, s.hintsUsed)
        val low = session(makeBoard(listOf(1, 2, 3, 3, 1, 2, 1, 2, 1)), score = 2)
        low.useHint()
        assertEquals(-8, low.score)
        val cheap = GameSession.withValues(listOf(1, 2, 3, 3, 1, 2, 1, 2, 1), seed = 0uL, scoring = Scoring.CHEAP_HINTS)
        assertEquals(3, cheap.useHint()?.second)
        assertEquals(-3, cheap.score)
    }

    @Test fun hintIsFreeWithoutPairs() {
        val s = session(makeBoard(listOf(1, 2, 1, 2, 1, 2, 1, 2, 1)), score = 30)
        assertNull(s.useHint())
        assertEquals(30, s.score)
        assertEquals(0, s.hintsUsed)
    }

    @Test fun pairReportsGapAndRoute() {
        assertEquals(3, session(makeBoard(listOf(6, 0, 0, 0, 4, 1, 2, 1, 2))).match(0, 4)?.pair?.gap)
        assertEquals(Route.READING_ORDER, MatchRules.pair(makeBoard(listOf(2, 1, 2, 1, 2, 1, 2, 1, 7), listOf(3, 2, 1, 2, 1, 2, 1, 2, 1)), 8, 9)?.route)
        assertEquals(Route.LINE, MatchRules.pair(makeBoard(listOf(4, 4, 1, 2, 1, 2, 1, 2, 1)), 0, 1)?.route)
    }

    @Test fun streakCountsMatchesAndResetsOnMissOrHint() {
        val s = session(makeBoard(listOf(1, 1, 2, 2, 3, 3, 4, 4, 5), listOf(5, 6, 6, 7, 7, 8, 8, 9, 9)), score = 10)
        s.match(0, 1); s.match(2, 3)
        assertEquals(2, s.streak)
        s.registerMiss()
        assertEquals(0, s.streak)
        s.match(4, 5)
        assertEquals(1, s.streak)
        s.useHint()
        assertEquals(0, s.streak)
        s.addNumbers()
        s.match(6, 7)
        assertEquals(1, s.streak)
    }

    @Test fun undoTakesBackOneStep() {
        val s = session(makeBoard(listOf(0, 0, 0, 0, 0, 0, 0, 3, 7), listOf(4, 4, 2, 1, 2, 1, 2, 1, 2)))
        val before = s.board
        s.match(7, 8)
        assertTrue(s.canUndo)
        assertTrue(s.undo())
        assertEquals(before, s.board)
        assertEquals(0, s.score)
        assertFalse(s.canUndo)
        assertFalse(s.undo())
        val plus = session(makeBoard(listOf(1, 2, 1, 2, 1, 2, 1, 2, 1)))
        plus.addNumbers()
        assertTrue(plus.undo())
        assertEquals(9, plus.board.cells.size)
        assertEquals(GameSession.MAX_PLUS_USES, plus.plusRemaining)
    }

    @Test fun undoDoesNotRefundHintsOrRestoreBrokenStreaks() {
        val s = session(makeBoard(listOf(4, 4, 3, 3, 1, 2, 1, 2, 1)), score = 10)
        s.match(0, 1)
        assertTrue(s.score == 11 && s.streak == 1)
        s.useHint()
        assertEquals(1, s.score)
        s.undo()
        assertEquals(0, s.score)
        assertEquals(0, s.streak)
    }

    @Test fun noUndoOnceTheGameIsOver() {
        val s = session(makeBoard(listOf(1, 2, 1, 2, 1, 1, 3, 4, 3)), plusRemaining = 0)
        s.match(4, 5)
        assertTrue(s.isOver)
        assertFalse(s.canUndo)
    }

    @Test fun reportsDigitsWithNoneLeftAndMarksAdded() {
        assertEquals(listOf(3), session(makeBoard(listOf(3, 7, 7, 1, 2, 1, 2, 1, 2))).match(0, 1)?.clearedDigits)
        val s = session(makeBoard(listOf(1, 2, 1, 2, 1, 2, 1, 2, 1)))
        s.addNumbers()
        assertTrue(s.board.cells.take(9).none { it.isAdded })
        assertTrue(s.board.cells.drop(9).all { it.isAdded })
    }

    @Test fun sessionRoundTripsThroughJson() {
        val s = GameSession(seed = 42uL)
        s.addNumbers()
        s.useHint()
        val json = Json.encodeToString(GameSession.Saved.serializer(), s.saved())
        val back = GameSession.restore(Json.decodeFromString(GameSession.Saved.serializer(), json))
        assertEquals(s, back)
        // The generator continues where it was: the same next stage on both.
        assertEquals(s.copy().saved().randomState, back.saved().randomState)
    }

    @Test fun generatorRules() {
        for (seed in 0uL until 40uL) {
            val random = SeededRandom(seed)
            for (stage in 1..6) {
                val board = BoardGenerator.makeBoard(random, stage)
                assertEquals(BoardGenerator.initialCount(stage), board.cells.size)
                assertTrue(board.cells.all { it.value in 1..9 })
                assertTrue(MatchRules.validPairs(board).size in BoardGenerator.startingPairs(stage))
                assertTrue(BoardGenerator.isPairable(board.cells.map { it.value }))
            }
        }
        for (seed in 0uL until 50uL) {
            assertTrue(MatchRules.validPairs(Board.of(BoardGenerator.valuesWithoutPairs(54, SeededRandom(seed)))).isEmpty())
        }
        val random = SeededRandom(1uL)
        assertEquals(0, BoardGenerator.movesBeforePlus(makeBoard(listOf(1, 2, 1, 2, 1, 2, 1, 2, 1)), random = random))
        assertEquals(2, BoardGenerator.movesBeforePlus(makeBoard(listOf(3, 4, 4, 7, 1, 2, 1, 2, 1)), random = random))
        assertTrue(BoardGenerator.isPairable(listOf(5, 5, 1, 9, 2, 2)))
        assertFalse(BoardGenerator.isPairable(listOf(5, 5, 5, 1, 9, 2)))
        assertTrue(BoardGenerator.isPairable(emptyList()))
    }

    @Test fun dailyDeterminismAndGolden() {
        assertEquals(GameSession(seed = 1234uL).board, GameSession(seed = 1234uL).board)
        assertNotEquals(GameSession(seed = 1234uL).board, GameSession(seed = 1235uL).board)
        assertEquals(DailyChallenge.session("2026-10-25").board, DailyChallenge.session("2026-10-25").board)
        val golden = listOf(7, 5, 6, 5, 5, 9, 7, 5, 4, 9, 2, 1, 9, 8, 5, 2, 9, 2, 4, 3, 4, 5, 4, 3, 6, 3, 5, 9, 8, 9, 2, 9, 5, 9, 8, 1, 3, 4, 7, 6, 7, 8)
        assertEquals(golden, DailyChallenge.session("2026-10-25").board.cells.map { it.value })
        assertEquals(DailyChallenge.goal("2026-10-25"), DailyChallenge.session("2026-10-25").goal)
    }
}
