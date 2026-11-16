package com.tavoohoh.quietnumbers.core

/**
 * The tutorial (port of iOS `TutorialScript`): a hand-made board where each move sets up the
 * next. Cell ids are the board's starting indices (rows of 9). `TutorialScriptTests` plays it
 * through a real `GameSession` to check every step is legal and leaves no pairs for +. The
 * texts live in the app (`tutorial_step_N_title`/`_text`).
 */
object TutorialScript {
    sealed interface Move {
        /** Match these two cells. */
        data class Pair(val a: Int, val b: Int) : Move
        /** Tap +. */
        data object AddNumbers : Move
    }

    data class Step(val id: Int, val move: Move)

    /** Row 0 has no pairs once the 8 is gone, and neither does what's left of row 2, so the last step really is "out of pairs". */
    val values = listOf(
        1, 2, 3, 8, 4, 1, 2, 3, 5,
        7, 5, 5, 4, 2, 3, 6, 6, 9,
        1, 4, 5, 6,
    )

    val steps = listOf(
        Step(1, Move.Pair(10, 11)),
        Step(2, Move.Pair(12, 21)),
        Step(3, Move.Pair(13, 3)),
        Step(4, Move.Pair(9, 14)),
        Step(5, Move.Pair(17, 18)),
        Step(6, Move.Pair(15, 16)),
        Step(7, Move.AddNumbers),
    )

    fun session(): GameSession = GameSession.withValues(values, seed = 0uL)

    /** Whether `step` has been played on `session`. */
    fun isDone(step: Step, session: GameSession): Boolean = when (val move = step.move) {
        is Move.Pair -> listOf(move.a, move.b).all { id -> session.board.indexOfCell(id)?.let { session.board.cells[it].isCleared } ?: true }
        Move.AddNumbers -> session.plusRemaining < GameSession.MAX_PLUS_USES
    }
}
