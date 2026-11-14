package com.tavoohoh.quietnumbers.core

import com.tavoohoh.quiet.core.SeededRandom
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Point values (from the brief; keep them configurable). */
@Serializable
data class Scoring(
    val adjacentPair: Int = 1,
    val acrossClearedPair: Int = 4,
    val rowCleared: Int = 10,
    val boardClearedPerStage: Int = 150,
    /** Hints are unlimited but cost points, even if that takes the score below zero. */
    val hintCost: Int = 10,
) {
    fun points(connection: Connection, stage: Int = 1): Int {
        val base = if (connection == Connection.ADJACENT) adjacentPair else acrossClearedPair
        return base + if (stage >= PAIR_BONUS_FROM_STAGE) 1 else 0
    }

    companion object {
        /** From this stage on, every pair is worth one more point. */
        const val PAIR_BONUS_FROM_STAGE = 3
        val STANDARD = Scoring()
        /** "In the Red"'s hidden reward, outside the scored daily challenge: hints cost 3. */
        val CHEAP_HINTS = Scoring(hintCost = 3)
    }
}

@Serializable
sealed interface GameMode {
    @Serializable @SerialName("classic") data object Classic : GameMode
    /** Stages like classic until the day's `goal` score. */
    @Serializable @SerialName("daily") data class Daily(val dayKey: String) : GameMode
}

/** What a successful match did, so the UI can animate and give feedback. */
data class MatchOutcome(
    val pair: MatchPair,
    val points: Int,
    /** The two numbers, in board order. */
    val values: List<Int>,
    /** Rows removed, as row indices before removal. */
    val clearedRows: List<Int>,
    /** Digits with no active cell left after this match. */
    val clearedDigits: List<Int>,
    val boardCleared: Boolean,
    /** Stage after this match (the next one if the board was cleared in classic). */
    val stage: Int,
    val hintsOnBoard: Int = 0,
    val usedPlusOnBoard: Boolean = false,
    /** This match took the score to the daily goal. */
    val reachedGoal: Boolean = false,
)

/** How the game went, for the end-of-game summary. Undo takes back what the move added. */
@Serializable
data class Stats(val pairs: Int = 0, val rowsCleared: Int = 0, val plusUsed: Int = 0, val longestStreak: Int = 0)

/**
 * A full game: board, score, stages and the + budget (port of iOS `GameSession`, a value type;
 * `copy()` gives an independent copy). Saved as `GameSession.Saved`.
 */
class GameSession private constructor(
    val mode: GameMode,
    val scoring: Scoring,
    /** Daily only: the score that completes it. */
    val goal: Int?,
    private var random: SeededRandom,
    board: Board?,
) {
    var board: Board = board ?: BoardGenerator.makeBoard(random)
        private set
    var score = 0
        private set
    var stage = 1
        private set
    var plusRemaining = MAX_PLUS_USES
        private set
    /** Hints used in the whole game. */
    var hintsUsed = 0
        private set
    /** Valid matches in a row. A miss or a hint resets it; + doesn't. */
    var streak = 0
        private set
    /** Daily only: the goal was reached (or, without a goal, the board was cleared). Ends the game. */
    var isCompleted = false
        private set
    private var hintsAtBoardStart = 0
    var stats = Stats()
        private set
    /** Seconds played: counted from the first move, while the game is on screen. */
    var elapsedSeconds = 0
        private set
    private var undoPoint: UndoPoint? = null

    @Serializable
    private data class UndoPoint(val board: Board, val score: Int, val plusRemaining: Int, val streak: Int, val stats: Stats)

    constructor(mode: GameMode = GameMode.Classic, seed: ULong, scoring: Scoring = Scoring.STANDARD, goal: Int? = null) :
        this(mode, scoring, goal, SeededRandom(seed), null)

    val hasValidPair: Boolean get() = MatchRules.hasValidPair(board)
    /** Hints used on the current board (for "No Help"). */
    val hintsOnBoard: Int get() = hintsUsed - hintsAtBoardStart
    /** + was used on the current board (for "Clean Slate"). */
    val usedPlusOnBoard: Boolean get() = plusRemaining < plusUses(stage)
    /** Anything was played: a match, a + or a hint. */
    val hasMoves: Boolean get() = score != 0 || stage > 1 || hintsUsed > 0 || usedPlusOnBoard
    /** No pairs and no + left, or a daily challenge that reached its goal. */
    val isOver: Boolean get() = isCompleted || (!hasValidPair && plusRemaining == 0)
    /** The last match or + can be taken back: one step, not across a cleared board, not once over. */
    val canUndo: Boolean get() = undoPoint != null && !isOver

    /** Adds play time (the game screen keeps the count and hands it in with each move). */
    fun addTime(seconds: Int) {
        if (seconds <= 0 || !hasMoves || isOver) return
        elapsedSeconds += seconds
    }

    fun matchCells(idA: Int, idB: Int): MatchOutcome? {
        val a = board.indexOfCell(idA) ?: return null
        val b = board.indexOfCell(idB) ?: return null
        return match(a, b)
    }

    fun match(a: Int, b: Int): MatchOutcome? {
        if (isOver) return null
        val pair = MatchRules.pair(board, a, b) ?: return null
        val undo = checkpoint()
        val values = listOf(board.cells[pair.first].value, board.cells[pair.second].value)
        var next = board.cleared(pair.first).cleared(pair.second)
        var points = scoring.points(pair.connection, stage)
        val (collapsed, rows) = next.removingClearedRows(setOf(next.row(pair.first), next.row(pair.second)))
        next = collapsed
        board = next
        points += rows.size * scoring.rowCleared
        val remaining = board.activeValues.toSet()
        val clearedDigits = values.toSet().filter { it !in remaining }.sorted()
        val boardCleared = board.isEmpty
        if (boardCleared) points += scoring.boardClearedPerStage * stage
        score += points
        streak += 1
        stats = stats.copy(pairs = stats.pairs + 1, rowsCleared = stats.rowsCleared + rows.size, longestStreak = maxOf(stats.longestStreak, streak))
        val reachedGoal = !isCompleted && goal != null && score >= goal
        if (reachedGoal) isCompleted = true
        undoPoint = if (boardCleared) null else undo
        val hintsOnBoard = hintsOnBoard
        val usedPlusOnBoard = usedPlusOnBoard
        // Reaching the goal ends the game: no next board, even if this one was cleared.
        if (boardCleared && !reachedGoal) advanceStage()
        return MatchOutcome(pair, points, values, rows, clearedDigits, boardCleared, stage, hintsOnBoard, usedPlusOnBoard, reachedGoal)
    }

    /** Copies every remaining number, in order, to the end of the board. */
    fun addNumbers(): Boolean {
        if (plusRemaining <= 0 || isOver) return false
        undoPoint = checkpoint()
        plusRemaining -= 1
        stats = stats.copy(plusUsed = stats.plusUsed + 1)
        board = board.appending(board.activeValues)
        return true
    }

    /** Returns a valid pair and charges `scoring.hintCost`. Free (and null) when there's no pair to show. */
    fun useHint(): Pair<MatchPair, Int>? {
        if (isOver) return null
        val pair = MatchRules.validPairs(board).firstOrNull() ?: return null
        val cost = scoring.hintCost
        score -= cost
        hintsUsed += 1
        streak = 0
        // Undoing later mustn't refund the hint or bring the streak back.
        undoPoint = undoPoint?.let { it.copy(score = it.score - cost, streak = 0) }
        return pair to cost
    }

    /** The player tried a pair that doesn't match. */
    fun registerMiss() {
        streak = 0
        undoPoint = undoPoint?.copy(streak = 0)
    }

    /** Takes back the last match or +. False if there's nothing to undo. */
    fun undo(): Boolean {
        if (!canUndo) return false
        val point = undoPoint ?: return false
        board = point.board
        score = point.score
        plusRemaining = point.plusRemaining
        streak = point.streak
        stats = point.stats
        undoPoint = null
        return true
    }

    private fun checkpoint() = UndoPoint(board, score, plusRemaining, streak, stats)

    private fun advanceStage() {
        if (mode is GameMode.Daily && goal == null) {
            isCompleted = true
            return
        }
        stage += 1
        plusRemaining = plusUses(stage)
        hintsAtBoardStart = hintsUsed
        board = BoardGenerator.makeBoard(random, stage)
    }

    /** An independent copy (Swift's GameSession is a value type). */
    fun copy(): GameSession = restore(saved())

    override fun equals(other: Any?) = other is GameSession && saved() == other.saved()
    override fun hashCode() = saved().hashCode()

    /** Everything a game needs to continue later (persistence, saved games). */
    @Serializable
    data class Saved(
        val mode: GameMode, val scoring: Scoring, val goal: Int?, val randomState: ULong, val board: Board,
        val score: Int, val stage: Int, val plusRemaining: Int, val hintsUsed: Int, val streak: Int,
        val isCompleted: Boolean, val hintsAtBoardStart: Int, val stats: Stats, val elapsedSeconds: Int,
        val undo: Undo? = null,
    ) {
        @Serializable
        data class Undo(val board: Board, val score: Int, val plusRemaining: Int, val streak: Int, val stats: Stats)
    }

    fun saved() = Saved(
        mode, scoring, goal, random.state, board, score, stage, plusRemaining, hintsUsed, streak, isCompleted,
        hintsAtBoardStart, stats, elapsedSeconds, undoPoint?.let { Saved.Undo(it.board, it.score, it.plusRemaining, it.streak, it.stats) },
    )

    companion object {
        /** + uses per board at stage 1 (the most). Clearing rows doesn't give any back. */
        const val MAX_PLUS_USES = 5

        /** Fewer + as stages go: 5, then 4 from stage 3, then 3 from stage 5. */
        fun plusUses(stage: Int): Int = when {
            stage <= 2 -> MAX_PLUS_USES
            stage <= 4 -> 4
            else -> 3
        }

        /** Starts from exact numbers instead of a generated board (tutorials, UI tests). Later stages come from `seed`. */
        fun withValues(values: List<Int>, mode: GameMode = GameMode.Classic, seed: ULong, scoring: Scoring = Scoring.STANDARD, goal: Int? = null) =
            GameSession(mode, seed, scoring, goal).also { it.board = Board.of(values) }

        /** Starts from a hand-made position (tests, store screenshots). */
        fun withBoard(
            board: Board, mode: GameMode = GameMode.Classic, seed: ULong = 0uL, plusRemaining: Int = MAX_PLUS_USES,
            stage: Int = 1, score: Int = 0, goal: Int? = null,
        ) = GameSession(mode, seed, goal = goal).also {
            it.board = board
            it.plusRemaining = plusRemaining
            it.stage = stage
            it.score = score
        }

        fun restore(s: Saved) = GameSession(s.mode, s.scoring, s.goal, SeededRandom(s.randomState), s.board).also {
            it.score = s.score
            it.stage = s.stage
            it.plusRemaining = s.plusRemaining
            it.hintsUsed = s.hintsUsed
            it.streak = s.streak
            it.isCompleted = s.isCompleted
            it.hintsAtBoardStart = s.hintsAtBoardStart
            it.stats = s.stats
            it.elapsedSeconds = s.elapsedSeconds
            it.undoPoint = s.undo?.let { u -> UndoPoint(u.board, u.score, u.plusRemaining, u.streak, u.stats) }
        }
    }
}
