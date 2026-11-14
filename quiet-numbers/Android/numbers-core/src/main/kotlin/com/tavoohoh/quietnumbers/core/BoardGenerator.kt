package com.tavoohoh.quietnumbers.core

import com.tavoohoh.quiet.core.SeededRandom

/** Port of iOS `BoardGenerator`: the same calls on `SeededRandom`, in the same order, so the same boards. */
object BoardGenerator {
    /** Stage 1 (and every daily challenge). */
    val INITIAL_COUNT get() = initialCount(1)

    /** Bigger boards as stages go (42 → 54 numbers by stage 5). Always even: `isPairable` needs it. */
    fun initialCount(stage: Int): Int = listOf(42, 44, 48, 50, 54)[stage.coerceIn(1, 5) - 1]

    /** Only a few pairs to start with: 3–5, then 2–4, then 2–3 from stage 3. */
    fun startingPairs(stage: Int): IntRange = when {
        stage <= 1 -> 3..5
        stage == 2 -> 2..4
        else -> 2..3
    }

    /** A typical player (random order) gets at least this many matches before needing +. */
    fun typicalMoves(stage: Int): Int = when {
        stage <= 1 -> 8
        stage == 2 -> 7
        stage <= 4 -> 6
        else -> 5
    }

    /**
     * A fresh board for `stage` with `startingPairs` valid pairs (at least one a sum) that
     * opens up to at least `typicalMoves`, where every number could find a partner.
     */
    fun makeBoard(random: SeededRandom, stage: Int = 1): Board {
        val count = initialCount(stage)
        val range = startingPairs(stage)
        while (true) {
            val values = valuesWithoutPairs(count, random)
            repeat(random.nextInt(range)) { plantPair(values, random) }
            if (!isPairable(values)) continue
            val board = Board.of(values)
            val pairs = MatchRules.validPairs(board)
            if (pairs.size !in range || pairs.none { isSum(it, board) }) continue
            if (movesBeforePlus(board, random = random) >= typicalMoves(stage)) return board
        }
    }

    /** Median matches over a few random playouts until no pair is left (no +). */
    internal fun movesBeforePlus(board: Board, playouts: Int = 15, random: SeededRandom): Int {
        val results = mutableListOf<Int>()
        repeat(playouts) {
            var current = board
            var moves = 0
            while (!current.isEmpty) {
                val pairs = MatchRules.validPairs(current)
                if (pairs.isEmpty()) break
                val pair = pairs[random.nextInt(0, pairs.size - 1)]
                current = current.cleared(pair.first).cleared(pair.second)
                current = current.removingClearedRows(setOf(current.row(pair.first), current.row(pair.second))).first
                moves += 1
            }
            results += moves
        }
        return results.sorted()[playouts / 2]
    }

    /** In reading order, each number avoids the families of the neighbours already placed. */
    internal fun valuesWithoutPairs(count: Int, random: SeededRandom): MutableList<Int> {
        val values = ArrayList<Int>(count)
        for (index in 0 until count) {
            val taken = earlierNeighbours(index).map { family(values[it]) }
            val allowed = (1..9).filter { family(it) !in taken }
            values += allowed[random.nextInt(0, allowed.size - 1)]
        }
        return values
    }

    /** Makes one cell pair with one of its earlier neighbours: two times in three the complement, else the same number. */
    internal fun plantPair(values: MutableList<Int>, random: SeededRandom) {
        val index = random.nextInt(1, values.size - 1)
        val neighbours = earlierNeighbours(index)
        val partner = values[neighbours[random.nextInt(0, neighbours.size - 1)]]
        values[index] = if (random.nextInt(0, 2) == 0) partner else 10 - partner
    }

    /** Two different numbers that add up to 10 (not two 5s). */
    internal fun isSum(pair: MatchPair, board: Board) = board.cells[pair.first].value != board.cells[pair.second].value

    private fun earlierNeighbours(index: Int): List<Int> {
        val columns = Board.COLUMNS
        val column = index % columns
        val result = mutableListOf<Int>()
        if (index >= 1) result += index - 1
        if (index < columns) return result
        result += index - columns
        if (column > 0) result += index - columns - 1
        if (column < columns - 1) result += index - columns + 1
        return result
    }

    private fun family(value: Int) = minOf(value, 10 - value)

    /** Every family ({1, 9}, {2, 8}, {3, 7}, {4, 6}, {5}) has an even count. Necessary, not sufficient. */
    fun isPairable(values: List<Int>): Boolean {
        val odd = HashSet<Int>()
        for (value in values) {
            val f = family(value)
            if (!odd.remove(f)) odd.add(f)
        }
        return odd.isEmpty()
    }
}
