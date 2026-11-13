package com.tavoohoh.quietwords.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

/** One of the 8 straight directions a word can run. */
@Serializable
data class Direction(val dr: Int, val dc: Int) {
    val reversed: Direction get() = Direction(-dr, -dc)

    companion object {
        val EAST = Direction(0, 1)
        val SOUTH = Direction(1, 0)
        val SOUTH_EAST = Direction(1, 1)
        val NORTH_EAST = Direction(-1, 1)
        val WEST = Direction(0, -1)
        val NORTH = Direction(-1, 0)
        val NORTH_WEST = Direction(-1, -1)
        val SOUTH_WEST = Direction(1, -1)

        /** Fixed order: generation must not depend on anything but the seed. */
        val ALL = listOf(EAST, SOUTH, SOUTH_EAST, NORTH_EAST, WEST, NORTH, NORTH_WEST, SOUTH_WEST)
    }
}

/** Board size, directions, word groups and hidden word length of each level. */
@Serializable
enum class Level(val rawValue: String) {
    @SerialName("gentle") GENTLE("gentle"),
    @SerialName("medium") MEDIUM("medium"),
    @SerialName("hard") HARD("hard");

    val side: Int get() = when (this) { GENTLE -> 9; MEDIUM -> 11; HARD -> 12 }

    val directions: List<Direction> get() = when (this) {
        GENTLE -> listOf(Direction.EAST, Direction.SOUTH, Direction.SOUTH_EAST)
        MEDIUM -> listOf(Direction.EAST, Direction.SOUTH, Direction.SOUTH_EAST, Direction.NORTH_EAST)
        HARD -> Direction.ALL
    }

    val groups: List<WordGroup> get() = when (this) {
        GENTLE -> listOf(WordGroup.COMMON)
        MEDIUM -> listOf(WordGroup.COMMON, WordGroup.CRAFTED, WordGroup.REGIONAL)
        HARD -> listOf(WordGroup.COMMON, WordGroup.CRAFTED, WordGroup.RARE, WordGroup.REGIONAL)
    }

    /** Letters of the hidden word. Sunday's daily asks for a long one. */
    val hiddenLength: IntRange get() = when (this) { GENTLE -> 4..6; MEDIUM -> 5..7; HARD -> 6..9 }

    /** Words a board usually has (it depends on crossings). */
    val typicalWords: Int get() = when (this) { GENTLE -> 14; MEDIUM -> 17; HARD -> 19 }

    companion object {
        fun fromRaw(raw: String): Level? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** A word on the board: where it starts and where it runs. */
@Serializable
data class Placement(val word: Word, val row: Int, val column: Int, val direction: Direction) {
    fun cells(side: Int): List<Int> =
        (0 until word.count).map { (row + direction.dr * it) * side + column + direction.dc * it }
}

/** A finished board: every cell belongs to a word, except the cells of the hidden word. */
@Serializable
data class Board(
    val side: Int,
    val language: WordLanguage,
    /** One letter per cell, row by row. */
    val letters: List<String>,
    /** Sorted as shown in the list. */
    val placements: List<Placement>,
    /** Board form of the hidden word. */
    val hidden: String,
    /** Cells left over for the hidden word, in reading order. */
    val hiddenCells: List<Int>,
) {
    fun letter(row: Int, column: Int): String = letters[row * side + column]

    /** The placement index a straight stroke from `start` to `end` spells (either way round). */
    fun placement(start: Int, end: Int): Int? = placements.indexOfFirst { placement ->
        val cells = placement.cells(side)
        (cells.first() == start && cells.last() == end) || (cells.first() == end && cells.last() == start)
    }.takeIf { it >= 0 }

    /** The cells of a straight line in one of the 8 directions, or null if it isn't one. */
    fun line(start: Int, end: Int): List<Int>? {
        val r0 = start / side
        val c0 = start % side
        val r1 = end / side
        val c1 = end % side
        val dr = r1 - r0
        val dc = c1 - c0
        if (!(dr == 0 || dc == 0 || abs(dr) == abs(dc))) return null
        val steps = max(abs(dr), abs(dc))
        val sr = dr.sign
        val sc = dc.sign
        return (0..steps).map { (r0 + sr * it) * side + c0 + sc * it }
    }
}
