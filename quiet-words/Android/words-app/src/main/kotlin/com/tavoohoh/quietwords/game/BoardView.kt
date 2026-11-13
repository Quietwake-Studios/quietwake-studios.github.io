package com.tavoohoh.quietwords.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quietwords.LocalUiTesting
import com.tavoohoh.quietwords.R
import com.tavoohoh.quietwords.core.GameSession
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Cell math for a square board of `side` letters drawn `size` pixels wide (port of iOS `BoardGeometry`). */
data class BoardGeometry(val side: Int, val size: Float) {
    val cell: Float get() = size / side

    fun center(index: Int): Offset = Offset(((index % side) + 0.5f) * cell, ((index / side) + 0.5f) * cell)

    /** The cell under a point, clamped to the board. */
    fun index(point: Offset): Int {
        val column = (point.x / cell).toInt().coerceIn(0, side - 1)
        val row = (point.y / cell).toInt().coerceIn(0, side - 1)
        return row * side + column
    }

    /**
     * The stroke from `start` towards the finger: the nearest of the 8 directions (45°
     * sectors), as many letters as the finger has gone, never off the board.
     */
    fun snap(start: Int, point: Offset): Int {
        val origin = center(start)
        val dx = point.x - origin.x
        val dy = point.y - origin.y
        val distance = sqrt(dx * dx + dy * dy)
        if (distance <= cell * 0.4f) return start
        val sector = (atan2(dy, dx) / (Math.PI.toFloat() / 4)).roundToInt()
        val angle = sector * Math.PI / 4
        val dc = cos(angle).roundToInt()
        val dr = sin(angle).roundToInt()
        val step = cell * (if (dr != 0 && dc != 0) sqrt(2f) else 1f)
        var steps = (distance / step).roundToInt()
        val row = start / side
        val column = start % side
        while (steps > 0) {
            val r = row + dr * steps
            val c = column + dc * steps
            if (r in 0 until side && c in 0 until side) return r * side + c
            steps -= 1
        }
        return start
    }
}

/** UI tests (debug `uiTesting`): "start" / "end" on the first word not found yet. TalkBack ignores it. */
val AnswerMark = SemanticsPropertyKey<String>("AnswerMark")
var SemanticsPropertyReceiver.answerMark by AnswerMark

/** The marker a found word stays in. Only the Highlighter for now (the others are rewards, Phase 4). */
private enum class Ink { FOUND, FLASH, STROKE }

/**
 * The letter grid with the marker layer under it: found words in the highlighter, the stroke
 * under the finger, hints and, at the end, the leftover letters of the hidden word.
 */
@Composable
fun BoardView(model: GameModel, size: Dp, modifier: Modifier = Modifier) {
    val session = model.state
    val board = session.board
    val colors = QuietTheme.colors
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }
    val geometry = BoardGeometry(board.side, sizePx)
    val cellDp = size / board.side
    val uiTesting = LocalUiTesting.current
    val boardLabel = stringResource(R.string.board)
    val stroke = model.stroke
    val firstTap = model.firstTap
    val flashing = model.flashing
    val revealed = model.revealed
    val hiddenLit = board.hiddenCells.take(revealed).toSet()
    val foundCells = session.foundCells
    val answer = if (uiTesting) nextAnswer(session) else null
    val fontSize = with(density) { (geometry.cell * 0.5f).toSp() }
    val letterStyle = TextStyle(fontSize = fontSize, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)

    Box(
        modifier
            .size(size)
            // Drags near the screen edges are the board's, not the back gesture's.
            .systemGestureExclusion()
            .semantics { contentDescription = boardLabel }
            .testTag("board")
            .pointerInput(model, sizePx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = geometry.index(down.position)
                    model.drag(GameModel.Stroke(start, start))
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) {
                            model.cancelDrag()
                            break
                        }
                        if (!change.pressed) {
                            change.consume()
                            model.endDrag()
                            break
                        }
                        model.drag(GameModel.Stroke(start, geometry.snap(start, change.position)))
                        change.consume()
                    }
                }
            },
    ) {
        Canvas(Modifier.size(size)) {
            val dark = colors.isDark
            fun ink(cells: List<Int>, ink: Ink) = capsule(geometry, cells) { rect, radius ->
                when (ink) {
                    Ink.STROKE -> {
                        drawRoundRect(colors.primary.copy(alpha = 0.3f), rect.first, rect.second, radius)
                        drawRoundRect(colors.primary, rect.first, rect.second, radius, style = Stroke(2 * density.density))
                    }
                    Ink.FLASH -> drawRoundRect(colors.accent.copy(alpha = 0.26f), rect.first, rect.second, radius)
                    Ink.FOUND -> drawRoundRect(colors.primary.copy(alpha = if (dark) 0.24f else 0.18f), rect.first, rect.second, radius)
                }
            }
            for (index in session.found) {
                ink(board.placements[index].cells(board.side), if (index == flashing) Ink.FLASH else Ink.FOUND)
            }
            if (flashing != null && flashing !in session.found) ink(board.placements[flashing].cells(board.side), Ink.FLASH)
            if (stroke != null) board.line(stroke.start, stroke.end)?.let { ink(it, Ink.STROKE) }
            if (firstTap != null) {
                val c = geometry.center(firstTap)
                val r = geometry.cell * 0.4f
                drawCircle(colors.primary.copy(alpha = 0.14f), r, c)
                drawCircle(colors.primary, r, c, style = Stroke(2 * density.density))
            }
            for (cell in session.hintCells) {
                val d = density.density
                drawCircle(
                    colors.accent, geometry.cell * 0.44f, geometry.center(cell),
                    style = Stroke(2 * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * d, 3 * d))),
                )
            }
            for (cell in hiddenLit) {
                val inset = geometry.cell * 0.08f
                val side = geometry.cell - 2 * inset
                val c = geometry.center(cell)
                drawRoundRect(
                    colors.accent.copy(alpha = 0.16f), Offset(c.x - side / 2, c.y - side / 2), Size(side, side),
                    CornerRadius(side * 0.24f),
                )
            }
        }
        for (index in board.letters.indices) {
            val row = index / board.side + 1
            val column = index % board.side + 1
            val letter = board.letters[index]
            val description = when {
                firstTap == index -> stringResource(R.string.cell_first, letter, row, column)
                index in foundCells -> stringResource(R.string.cell_found, letter, row, column)
                else -> stringResource(R.string.cell, letter, row, column)
            }
            val mark = when (index) {
                answer?.first -> "start"
                answer?.second -> "end"
                else -> ""
            }
            val center = geometry.center(index)
            Box(
                Modifier
                    .offset { IntOffset((center.x - geometry.cell / 2).roundToInt(), (center.y - geometry.cell / 2).roundToInt()) }
                    .size(cellDp)
                    .clearAndSetSemantics {
                        contentDescription = description
                        role = Role.Button
                        selected = firstTap == index
                        testTag = "cell-$index"
                        if (uiTesting) answerMark = mark
                        onClick { model.tap(index); true }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(letter, style = letterStyle, color = if (index in hiddenLit) colors.accent else colors.ink)
            }
        }
    }
}

/** First and last cell of the first word not found yet. */
private fun nextAnswer(session: GameSession): Pair<Int, Int>? {
    val next = session.board.placements.indices.firstOrNull { !session.isFound(it) } ?: return null
    val cells = session.board.placements[next].cells(session.board.side)
    return cells.first() to cells.last()
}

/** A capsule from the first cell's center to the last's, rotated along the word. */
private fun DrawScope.capsule(
    geometry: BoardGeometry, cells: List<Int>,
    draw: DrawScope.(rect: Pair<Offset, Size>, radius: CornerRadius) -> Unit,
) {
    if (cells.isEmpty()) return
    val a = geometry.center(cells.first())
    val b = geometry.center(cells.last())
    val height = geometry.cell * 0.8f
    val length = hypot(b.x - a.x, b.y - a.y) + height
    val mid = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
    rotateRad(atan2(b.y - a.y, b.x - a.x), pivot = mid) {
        draw(Offset(mid.x - length / 2, mid.y - height / 2) to Size(length, height), CornerRadius(height / 2))
    }
}
