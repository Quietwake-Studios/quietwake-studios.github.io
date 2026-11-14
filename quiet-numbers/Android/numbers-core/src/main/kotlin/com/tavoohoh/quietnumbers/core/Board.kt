package com.tavoohoh.quietnumbers.core

import kotlinx.serialization.Serializable

/** A single number on the board. Cleared cells stay as "ghosts" until their row empties. */
@Serializable
data class Cell(
    val id: Int,
    val value: Int,
    val isCleared: Boolean = false,
    /** Added with +, as opposed to the stage's original numbers. */
    val isAdded: Boolean = false,
)

/**
 * The grid, stored as a reading-order strip of cells `COLUMNS` wide; the last row may be
 * partial (port of iOS `Board`, a value type: every change returns or makes a new list).
 */
@Serializable
data class Board(
    val cells: List<Cell>,
    /** Next id to hand out, so ids stay unique for the whole session (Compose keys). */
    private val nextID: Int = (cells.maxOfOrNull { it.id } ?: -1) + 1,
) {
    val rowCount: Int get() = (cells.size + COLUMNS - 1) / COLUMNS
    val isEmpty: Boolean get() = cells.all { it.isCleared }
    val activeValues: List<Int> get() = cells.filter { !it.isCleared }.map { it.value }

    fun row(index: Int): Int = index / COLUMNS
    fun column(index: Int): Int = index % COLUMNS
    fun indexOfCell(id: Int): Int? = cells.indexOfFirst { it.id == id }.takeIf { it >= 0 }

    fun isRowCleared(row: Int): Boolean = rowRange(row).all { cells[it].isCleared }

    /** A copy with the given cells cleared but every row kept, so the UI can show a match before its rows collapse. */
    fun clearing(indices: List<Int>): Board = indices.fold(this) { b, i -> b.cleared(i) }

    internal fun cleared(index: Int): Board = copy(cells = cells.toMutableList().also { it[index] = it[index].copy(isCleared = true) })

    /** Appends `values` as new cells at the end of the strip. */
    internal fun appending(values: List<Int>): Board =
        Board(cells + values.mapIndexed { i, v -> Cell(nextID + i, v, isAdded = true) }, nextID + values.size)

    /** Removes the given rows (if fully cleared) and shifts the board up; returns the board and the rows removed. */
    internal fun removingClearedRows(rows: Set<Int>): Pair<Board, List<Int>> {
        val removable = rows.filter { it < rowCount && isRowCleared(it) }.sortedDescending()
        val list = cells.toMutableList()
        for (row in removable) {
            val range = rowRange(row)
            list.subList(range.first, range.last + 1).clear()
        }
        return Board(list, nextID) to removable.sorted()
    }

    private fun rowRange(row: Int): IntRange {
        val start = row * COLUMNS
        return start until minOf(start + COLUMNS, cells.size)
    }

    companion object {
        const val COLUMNS = 9

        fun of(values: List<Int>) = Board(values.mapIndexed { i, v -> Cell(i, v) }, values.size)
    }
}
