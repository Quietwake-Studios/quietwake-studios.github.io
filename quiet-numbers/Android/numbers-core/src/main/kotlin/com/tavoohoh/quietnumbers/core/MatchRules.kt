package com.tavoohoh.quietnumbers.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How two cells reach each other. Drives scoring. */
@Serializable
enum class Connection {
    /** Neighbours on the grid, or consecutive in reading order. */
    @SerialName("adjacent") ADJACENT,
    /** Only cleared cells between them. */
    @SerialName("acrossCleared") ACROSS_CLEARED,
}

/** The path between two cells. */
@Serializable
enum class Route(val rawValue: String) {
    /** Horizontal, vertical or diagonal. */
    @SerialName("line") LINE("line"),
    /** Reading order across a row break: end of one row to the start of the next. */
    @SerialName("readingOrder") READING_ORDER("readingOrder"),
}

/** A matchable pair, as board indices with `first < second`. */
@Serializable
data class MatchPair(val first: Int, val second: Int, val gap: Int, val route: Route = Route.LINE) {
    val connection: Connection get() = if (gap == 0) Connection.ADJACENT else Connection.ACROSS_CLEARED

    companion object {
        fun of(a: Int, b: Int, gap: Int, route: Route = Route.LINE) = MatchPair(minOf(a, b), maxOf(a, b), gap, route)
    }
}

/** Port of iOS `MatchRules`. Same search order, so the same first pair (hints) on both platforms. */
object MatchRules {
    /** Equal numbers or numbers that add up to 10. */
    fun valuesMatch(a: Int, b: Int): Boolean = a == b || a + b == 10

    private data class Reach(val index: Int, val gap: Int, val route: Route)

    /** The shortest path between two active cells (fewest cleared cells between, straight lines first), or null. Ignores values. */
    fun path(board: Board, a: Int, b: Int): Pair<Int, Route>? {
        if (a == b) return null
        val from = minOf(a, b)
        val to = maxOf(a, b)
        return reachableCells(board, from).filter { it.index == to }
            .minWithOrNull(compareBy<Reach>({ it.gap }, { if (it.route == Route.LINE) 0 else 1 }))
            ?.let { it.gap to it.route }
    }

    /** A valid pair for the two cells, or null. */
    fun pair(board: Board, a: Int, b: Int): MatchPair? {
        val cells = board.cells
        if (a !in cells.indices || b !in cells.indices) return null
        if (cells[a].isCleared || cells[b].isCleared) return null
        if (!valuesMatch(cells[a].value, cells[b].value)) return null
        val (gap, route) = path(board, a, b) ?: return null
        return MatchPair.of(a, b, gap, route)
    }

    /** Every valid pair on the board, in reading order of the first cell. */
    fun validPairs(board: Board): List<MatchPair> {
        val pairs = mutableListOf<MatchPair>()
        val seen = HashSet<Long>()
        for (index in board.cells.indices) {
            if (board.cells[index].isCleared) continue
            for (reach in reachableCells(board, index)) {
                if (!seen.add(index.toLong() * 1_000_003L + reach.index)) continue
                pair(board, index, reach.index)?.let(pairs::add)
            }
        }
        return pairs
    }

    fun hasValidPair(board: Board): Boolean {
        for (index in board.cells.indices) {
            if (board.cells[index].isCleared) continue
            for (reach in reachableCells(board, index)) {
                if (valuesMatch(board.cells[index].value, board.cells[reach.index].value)) return true
            }
        }
        return false
    }

    /**
     * First active cell after `index` in each forward direction: reading order, down,
     * down-left and down-right. Backward directions are covered by symmetry.
     */
    private fun reachableCells(board: Board, index: Int): List<Reach> {
        val cells = board.cells
        val columns = Board.COLUMNS
        val result = mutableListOf<Reach>()
        var next = index + 1
        while (next < cells.size && cells[next].isCleared) next += 1
        if (next < cells.size) {
            val route = if (board.row(next) == board.row(index)) Route.LINE else Route.READING_ORDER
            result += Reach(next, next - index - 1, route)
        }
        val startColumn = board.column(index)
        for (step in intArrayOf(0, -1, 1)) {
            var row = board.row(index) + 1
            var column = startColumn + step
            var gap = 0
            while (column in 0 until columns) {
                val target = row * columns + column
                if (target >= cells.size) break
                if (!cells[target].isCleared) {
                    result += Reach(target, gap, Route.LINE)
                    break
                }
                gap += 1
                row += 1
                column += step
            }
        }
        return result
    }
}
