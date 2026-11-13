package com.tavoohoh.quietwords.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import com.tavoohoh.quietwords.core.Board
import com.tavoohoh.quietwords.core.GameSession
import com.tavoohoh.quietwords.design.Feedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The game on screen (port of iOS `GameModel`): wraps `GameSession` with what the board shows
 * around it (the stroke under the finger, the first tapped letter, the flash of a found word,
 * the reveal). Every rule stays in the session.
 */
class GameModel(private val session: GameSession, val isTutorial: Boolean = false) {
    /** A straight stroke from one letter to another, snapped to the 8 directions. */
    data class Stroke(val start: Int, val end: Int)

    /** What Compose reads: a copy of the session after every change. */
    var state: GameSession by mutableStateOf(session.copy(), neverEqualPolicy())
        private set
    /** The stroke while dragging. */
    var stroke: Stroke? by mutableStateOf(null)
        private set
    /** Tap mode: the first letter, waiting for the last. */
    var firstTap: Int? by mutableStateOf(null)
        private set
    /** The word just found, lit in the accent for a moment. */
    var flashing: Int? by mutableStateOf(null)
        private set
    /** Bumped on a wrong guess (the slots shake). */
    var guessShake by mutableIntStateOf(0)
        private set
    /** End of game: how many leftover cells are lit so far, in reading order. */
    var revealed by mutableIntStateOf(0)
        private set
    /** The summary can come up (after the reveal). */
    var showsSummary by mutableStateOf(false)
        private set

    /** Haptics (set by the screen). */
    var onFeedback: (Feedback) -> Unit = {}
    /** Something that happened (later: achievements, album, saving). */
    var onEvent: (GameSession.Event) -> Unit = {}
    /** Reduce Motion (set by the screen): no letter-by-letter reveal. */
    var reduceMotion = false

    /** Tutorial: only this word can be found (null = any). */
    var allowedWord: Int? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        if (session.isWon) {
            revealed = session.board.hiddenCells.size
            showsSummary = true
        }
    }

    val board: Board get() = session.board

    // Dragging

    /** The finger moved: a new snapped stroke. A soft tick per letter it grows or shrinks. */
    fun drag(to: Stroke) {
        if (session.isWon || to == stroke) return
        val length = board.line(to.start, to.end)?.size ?: 1
        val previous = stroke?.let { board.line(it.start, it.end)?.size } ?: 0
        stroke = to
        // A touch that hasn't moved may be the second tap: keep the first letter until it does.
        if (to.start != to.end) firstTap = null
        if (length != previous) onFeedback(Feedback.LETTER)
    }

    /** The finger lifted. A stroke of one letter is a tap. */
    fun endDrag() {
        val stroke = stroke ?: return
        this.stroke = null
        if (stroke.start == stroke.end) return tap(stroke.start)
        commit(stroke.start, stroke.end)
    }

    fun cancelDrag() {
        stroke = null
    }

    // Tapping (and TalkBack)

    /** First letter, then last. A second letter out of line becomes the new first. */
    fun tap(cell: Int) {
        if (session.isWon) return
        val first = firstTap
        if (first == null) {
            firstTap = cell
            return onFeedback(Feedback.LETTER)
        }
        if (first == cell) {
            firstTap = null
            return
        }
        if (board.line(first, cell) == null) {
            firstTap = cell
            return onFeedback(Feedback.LETTER)
        }
        firstTap = null
        commit(first, cell)
    }

    // Words

    private fun commit(start: Int, end: Int) {
        val allowed = allowedWord
        if (allowed != null && board.placement(start, end) != allowed) return onFeedback(Feedback.INVALID)
        val events = session.select(start, end) ?: return
        handle(events)
    }

    fun requestHint() {
        val event = session.hint() ?: return
        onFeedback(Feedback.HINT)
        handle(listOf(event))
    }

    /** True if it was the hidden word. */
    fun guess(text: String): Boolean {
        val event = session.guess(text) ?: return false
        handle(listOf(event))
        return event is GameSession.Event.GuessRight
    }

    /** One second of play (the game screen ticks it while the app is in front). */
    fun tick() {
        if (session.isWon) return
        session.tick()
        state = session.copy()
    }

    fun dismissSummary() {
        showsSummary = false
    }

    fun dispose() = scope.cancel()

    // Private

    private fun handle(events: List<GameSession.Event>) {
        for (event in events) {
            onEvent(event)
            when (event) {
                is GameSession.Event.Found -> {
                    onFeedback(Feedback.FOUND)
                    flash(event.index)
                }
                is GameSession.Event.AlreadyFound -> flash(event.index)
                is GameSession.Event.GuessRight -> onFeedback(Feedback.HIDDEN_WORD)
                is GameSession.Event.GuessWrong -> {
                    onFeedback(Feedback.INVALID)
                    guessShake += 1
                }
                is GameSession.Event.Won -> {
                    onFeedback(Feedback.WON)
                    reveal()
                }
                GameSession.Event.Miss, is GameSession.Event.Hint -> Unit
            }
        }
        state = session.copy()
    }

    private fun flash(index: Int) {
        flashing = index
        scope.launch {
            delay(350)
            if (flashing == index) flashing = null
        }
    }

    /** The leftover letters light up one by one in reading order, then the summary. */
    private fun reveal() {
        val count = board.hiddenCells.size
        val reduce = reduceMotion
        scope.launch {
            delay(450)
            if (reduce) {
                revealed = count
            } else {
                for (step in 1..maxOf(count, 1)) {
                    revealed = step
                    delay(80)
                }
            }
            delay(if (reduce) 300 else 900)
            showsSummary = true
        }
    }
}
