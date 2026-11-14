package com.tavoohoh.quietnumbers.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.tavoohoh.quietnumbers.core.Board
import com.tavoohoh.quietnumbers.core.Cell
import com.tavoohoh.quietnumbers.core.GameSession
import com.tavoohoh.quietnumbers.core.MatchOutcome
import com.tavoohoh.quietnumbers.core.MatchPair
import com.tavoohoh.quietnumbers.core.ProgressEvent
import com.tavoohoh.quietnumbers.core.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A score delta shown briefly next to the score ("+4", "-3"). */
data class ScoreChange(val id: Int, val amount: Int)

/** Points from a match: "+4" rises from the middle of the pair and fades. `center` in cell units. */
data class FloatingPoints(val id: Int, val amount: Int, val center: Offset, val delayMillis: Long)

/** Short encouragement after a notable move, as a toast next to the score. */
data class Praise(val id: Int, val kind: Kind, val value: Int = 0, val delayMillis: Long = 0) {
    enum class Kind { GOAL_REACHED, NO_PAIRS, TRIPLE_ROW, DOUBLE_ROW, LONG_SHOT, NO_MORE, IN_A_ROW }
}

/** A matched number bursting into 8 rays (accent for a long shot). `center` in cell units. */
data class Pop(val id: Int, val center: Offset, val isLongShot: Boolean)

/** The line drawn between a matched pair, in cell units (1 = one cell side). */
data class MatchLine(val id: Int, val tier: Tier, val segments: List<Pair<Offset, Offset>>) {
    enum class Tier { SUBTLE, MEDIUM, DRAMATIC }
}

/** Game feedback events (haptics and tones, iOS `FeedbackService.Event`). */
sealed interface GameFeedback {
    data object Select : GameFeedback
    data class Match(val longShot: Boolean) : GameFeedback
    data object Invalid : GameFeedback
    data object RowCleared : GameFeedback
    data object BoardCleared : GameFeedback
}

/**
 * UI state around a `GameSession` (port of iOS `GameModel`): selection, hints, shakes,
 * feedback and the match sequence (line → ghosts → row effect → collapse).
 */
class GameModel(session: GameSession) {
    /** The session (Compose reads a copy after each change, like a Swift value type). */
    private val live: GameSession = session
    var session: GameSession by mutableStateOf(session.copy(), neverEqualPolicy())
        private set
    /** What the board shows. Trails `session.board` while a match plays out. */
    var board: Board by mutableStateOf(session.board)
        private set
    var selectedID: Int? by mutableStateOf(null)
        private set
    var hintIDs: Set<Int> by mutableStateOf(emptySet())
        private set
    /** The pair being connected; looks selected until it turns into ghosts. */
    var connectingIDs: Set<Int> by mutableStateOf(emptySet())
        private set
    var matchLine: MatchLine? by mutableStateOf(null)
        private set
    /** Rows (by first cell id) playing their clear effect before collapsing. */
    var flashingRows: Set<Int> by mutableStateOf(emptySet())
        private set
    /** Bumped per cell on an invalid pair; drives the shake. */
    val shakes = mutableStateMapOf<Int, Int>()
    /** The two cells of an invalid pair, ringed in the accent for a moment. */
    var missIDs: Set<Int> by mutableStateOf(emptySet())
        private set
    /** First cell added by the last +, so new numbers come in one by one. */
    var addedFromID: Int? by mutableStateOf(null)
        private set
    /** Stage reached after clearing a board, shown briefly. */
    var stageBanner: Int? by mutableStateOf(null)
    /** Bumped on every +, so the board scrolls to the new numbers. */
    var additions by mutableIntStateOf(0)
        private set
    var scoreChange: ScoreChange? by mutableStateOf(null)
        private set
    var floatingPoints: FloatingPoints? by mutableStateOf(null)
        private set
    var pops: List<Pop> by mutableStateOf(emptyList())
        private set
    /** Points of the match playing out; the score shown counts them in after the line. */
    private var pendingPoints by mutableIntStateOf(0)
    var praise: Praise? by mutableStateOf(null)
        private set
    /** Bumped when a hint finds nothing and + is the way out; bounces the + button. */
    var plusNudges by mutableIntStateOf(0)
        private set
    /** Set by the screen: skips the line and shortens the sequence. */
    var reduceMotion = false
    /** Haptics and tones (set by the screen). */
    var onFeedback: (GameFeedback) -> Unit = {}
    /** Gameplay events for achievements (Phase 4). */
    var onEvent: (ProgressEvent) -> Unit = {}
    /** After every change to the session (saving, Phase 3). */
    var onChange: (GameSession) -> Unit = {}

    private var feedbackID = 0
    private var uncommittedSeconds = 0
    private var sequence: Job? = null
    private var missJob: Job? = null
    /** The last move if it was a match, so undoing it can take its progress back. */
    private var lastOutcome: MatchOutcome? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val canAddNumbers: Boolean get() = session.plusRemaining > 0 && !session.isOver
    val canUseHint: Boolean get() = !session.isOver
    val canUndo: Boolean get() = session.canUndo
    /** Stuck until the player taps +. */
    val needsNumbers: Boolean get() = !session.isOver && !session.hasValidPair
    val displayedScore: Int get() = session.score - pendingPoints

    fun tap(cell: Cell) {
        finishSequence()
        commitTime()
        if (live.isOver) return
        val index = live.board.indexOfCell(cell.id) ?: return
        if (live.board.cells[index].isCleared) return
        val selected = selectedID
        if (selected == null) return select(cell.id)
        if (selected == cell.id) {
            selectedID = null
            return
        }
        val before = live.board
        val stage = live.stage
        val outcome = live.matchCells(selected, cell.id) ?: return miss(selected, cell.id)
        selectedID = null
        hintIDs = emptySet()
        val effectsDelay = if (reduceMotion) 0L else DRAW_MILLIS
        if (reduceMotion) {
            showScoreChange(outcome.points)
        } else {
            feedbackID += 1
            pendingPoints = outcome.points
            val a = center(outcome.pair.first)
            val b = center(outcome.pair.second)
            floatingPoints = FloatingPoints(feedbackID, outcome.points, Offset((a.x + b.x) / 2, (a.y + b.y) / 2), effectsDelay)
        }
        onFeedback(
            when {
                outcome.boardCleared -> GameFeedback.BoardCleared
                outcome.clearedRows.isEmpty() -> GameFeedback.Match(isLongShot(outcome.pair))
                else -> GameFeedback.RowCleared
            },
        )
        onEvent(ProgressEvent.Matched(outcome))
        lastOutcome = outcome
        if (outcome.reachedGoal) {
            feedbackID += 1
            praise = Praise(feedbackID, Praise.Kind.GOAL_REACHED, delayMillis = effectsDelay)
        }
        if (live.stage != stage) {
            stageBanner = live.stage
        } else if (!outcome.reachedGoal) {
            praiseFor(outcome)?.let { (kind, value) ->
                feedbackID += 1
                praise = Praise(feedbackID, kind, value, effectsDelay)
            }
        }
        publish()
        play(outcome, before)
    }

    fun addNumbers() {
        finishSequence()
        commitTime()
        val firstNew = live.board.cells.size
        if (!live.addNumbers()) return
        lastOutcome = null
        addedFromID = live.board.cells.getOrNull(firstNew)?.id
        selectedID = null
        hintIDs = emptySet()
        additions += 1
        onFeedback(GameFeedback.Select)
        publish()
        board = live.board
    }

    /** Drag started on a number: it becomes the selection, replacing any other. */
    fun beginDrag(cell: Cell) {
        finishSequence()
        if (live.isOver || selectedID == cell.id) return
        val index = live.board.indexOfCell(cell.id) ?: return
        if (live.board.cells[index].isCleared) return
        select(cell.id)
    }

    /** Highlights a valid pair, for a few points. */
    fun showHint() {
        finishSequence()
        commitTime()
        val hint = live.useHint()
        if (hint == null) {
            if (canAddNumbers) {
                plusNudges += 1
                feedbackID += 1
                praise = Praise(feedbackID, Praise.Kind.NO_PAIRS)
                onFeedback(GameFeedback.Select)
            }
            return
        }
        val cells = live.board.cells
        hintIDs = setOf(cells[hint.first.first].id, cells[hint.first.second].id)
        selectedID = null
        showScoreChange(-hint.second)
        onFeedback(GameFeedback.Select)
        onEvent(ProgressEvent.HintUsed(live.score))
        publish()
    }

    /** Takes back the last match or + (one step). */
    fun undo() {
        finishSequence()
        commitTime()
        val before = live.score
        if (!live.undo()) return
        lastOutcome?.let { onEvent(ProgressEvent.Undone(it)) }
        lastOutcome = null
        selectedID = null
        hintIDs = emptySet()
        if (live.score != before) showScoreChange(live.score - before)
        onFeedback(GameFeedback.Select)
        publish()
        board = live.board
    }

    /** One second on screen with the app in front (the screen ticks it); handed in with the next move. */
    fun tick() {
        uncommittedSeconds += 1
    }

    /** Hands the seconds played to the session. Returns whether that changed it. */
    fun commitTime(): Boolean {
        val before = live.elapsedSeconds
        live.addTime(uncommittedSeconds)
        uncommittedSeconds = 0
        val changed = live.elapsedSeconds != before
        if (changed) publish()
        return changed
    }

    fun dismissScoreChange(change: ScoreChange) {
        if (scoreChange == change) scoreChange = null
    }

    fun dismissFloatingPoints(shown: FloatingPoints) {
        if (floatingPoints == shown) floatingPoints = null
    }

    fun dismissPraise(shown: Praise) {
        if (praise == shown) praise = null
    }

    fun dispose() = scope.cancel()

    // Match sequence

    /** Line → pops and ghosts → row effect → collapse. The session is already updated; only `board` and the effects trail behind. */
    private fun play(outcome: MatchOutcome, before: Board) {
        val pair = outcome.pair
        val ghosted = before.clearing(listOf(pair.first, pair.second))
        val rowIDs = outcome.clearedRows.map { before.cells[it * Board.COLUMNS].id }.toSet()
        val line = makeLine(pair)
        board = before
        connectingIDs = setOf(before.cells[pair.first].id, before.cells[pair.second].id)
        matchLine = if (reduceMotion) null else line
        sequence = scope.launch {
            if (!reduceMotion) delay(DRAW_MILLIS)
            board = ghosted
            connectingIDs = emptySet()
            matchLine = null
            pendingPoints = 0
            if (!reduceMotion) {
                val long = isLongShot(pair)
                pops = listOf(pair.first, pair.second).map { index ->
                    feedbackID += 1
                    Pop(feedbackID, center(index), long)
                }
                delay(POP_MILLIS)
                pops = emptyList()
            }
            if (rowIDs.isNotEmpty()) {
                flashingRows = rowIDs
                delay(ROW_EFFECT_MILLIS)
            }
            finishSequence()
        }
    }

    /** Jumps to the end of any running match sequence. */
    private fun finishSequence() {
        sequence?.cancel()
        sequence = null
        board = live.board
        connectingIDs = emptySet()
        flashingRows = emptySet()
        matchLine = null
        pops = emptyList()
        pendingPoints = 0
    }

    private fun makeLine(pair: MatchPair): MatchLine {
        val a = center(pair.first)
        val b = center(pair.second)
        val segments = when (pair.route) {
            // End of one row to the start of the next: grow in from both edges.
            Route.READING_ORDER -> listOf(
                Offset(Board.COLUMNS.toFloat(), a.y) to Offset(a.x + LINE_INSET, a.y),
                Offset(0f, b.y) to Offset(b.x - LINE_INSET, b.y),
            )
            // Grow out from the middle towards both numbers.
            Route.LINE -> {
                val mid = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
                fun stop(p: Offset): Offset {
                    val d = p - mid
                    val length = maxOf(d.getDistance(), 1e-6f)
                    val keep = maxOf(length - LINE_INSET, 0f) / length
                    return mid + d * keep
                }
                listOf(mid to stop(a), mid to stop(b))
            }
        }
        feedbackID += 1
        return MatchLine(feedbackID, tier(pair), segments)
    }

    private fun praiseFor(outcome: MatchOutcome): Pair<Praise.Kind, Int>? {
        when {
            outcome.clearedRows.size >= 3 -> return Praise.Kind.TRIPLE_ROW to 0
            outcome.clearedRows.size == 2 -> return Praise.Kind.DOUBLE_ROW to 0
        }
        if (isLongShot(outcome.pair)) return Praise.Kind.LONG_SHOT to 0
        val digit = outcome.clearedDigits.firstOrNull()
        if (digit != null && !outcome.boardCleared) return Praise.Kind.NO_MORE to digit
        val streak = live.streak
        if (streak > 0 && streak % STREAK_STEP == 0) return Praise.Kind.IN_A_ROW to streak
        return null
    }

    /** Not a pair: both rings turn to the accent and shake, then the tapped number stays selected. */
    private fun miss(firstID: Int, secondID: Int) {
        shakes[firstID] = (shakes[firstID] ?: 0) + 1
        shakes[secondID] = (shakes[secondID] ?: 0) + 1
        missIDs = setOf(firstID, secondID)
        selectedID = secondID
        live.registerMiss()
        onEvent(ProgressEvent.Missed)
        onFeedback(GameFeedback.Invalid)
        publish()
        missJob?.cancel()
        missJob = scope.launch {
            delay(240)
            missIDs = emptySet()
        }
    }

    private fun select(id: Int) {
        selectedID = id
        onFeedback(GameFeedback.Select)
    }

    private fun showScoreChange(amount: Int) {
        feedbackID += 1
        scoreChange = ScoreChange(feedbackID, amount)
    }

    private fun publish() {
        session = live.copy()
        onChange(session)
    }

    companion object {
        /** Cleared cells between a pair for it to count as a long shot. */
        const val LONG_SHOT_GAP = 4
        /** Every this many matches in a row earns a praise. */
        const val STREAK_STEP = 5
        const val DRAW_MILLIS = 120L
        const val POP_MILLIS = 260L
        const val ROW_EFFECT_MILLIS = 150L
        /** Distance from a cell's center where the line stops, so it doesn't cover the number. */
        const val LINE_INSET = 0.3f

        fun center(index: Int) = Offset((index % Board.COLUMNS) + 0.5f, (index / Board.COLUMNS) + 0.5f)

        fun isLongShot(pair: MatchPair) = pair.gap >= LONG_SHOT_GAP

        fun tier(pair: MatchPair) = when {
            pair.gap == 0 -> MatchLine.Tier.SUBTLE
            pair.gap < LONG_SHOT_GAP -> MatchLine.Tier.MEDIUM
            else -> MatchLine.Tier.DRAMATIC
        }
    }
}
