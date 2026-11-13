package com.tavoohoh.quietwords.core

import com.tavoohoh.quiet.core.FNV1a
import com.tavoohoh.quiet.core.SeededRandom

/**
 * Builds dense boards (port of QuietWordsCore's `Generator`, step by step: every call to the
 * PRNG happens in the same order as in Swift, so a key gives the same board on iOS and Android).
 * Words cover every cell except the letters of the hidden word, which fill the leftover cells
 * in reading order.
 *
 * Board letters are only A–Z and a precomposed Ñ, so a `Char` is the same as Swift's `Character`.
 */
object Generator {
    const val MAX_ATTEMPTS = 600

    /** Tries `"<key>#0"`, `"<key>#1"`… until a board passes every check. */
    fun board(
        key: String, pool: List<Word>, hidden: List<String>, language: WordLanguage, level: Level,
        longHidden: Boolean = false, blocklist: List<String>, maxAttempts: Int = MAX_ATTEMPTS,
    ): Board? {
        var lengths = level.hiddenLength
        if (longHidden) lengths = maxOf(lengths.first, lengths.last - 2)..lengths.last
        val hiddenOptions = hidden.filter { it.characterCount in lengths && WordText.isBoardText(it, language) }
        if (hiddenOptions.isEmpty()) return null
        val words = eligible(pool, level, language)
        val block = blocklist.map { it.toCharArray() }
        for (attempt in 0 until maxAttempts) {
            val rng = SeededRandom(FNV1a.hash("$key#$attempt"))
            build(words, hiddenOptions, language, level, block, rng)?.let { return it }
        }
        return null
    }

    /** Words that can go on a board of this level: right group and length, board letters, no palindromes, one copy each. */
    fun eligible(pool: List<Word>, level: Level, language: WordLanguage): List<Word> {
        val seen = HashSet<String>()
        return pool.filter { word ->
            word.group in level.groups && word.count in 3..level.side &&
                WordText.isBoardText(word.letters, language) &&
                word.letters.reversed() != word.letters &&
                seen.add(word.letters)
        }
    }

    private class Placed(val word: Word, val letters: CharArray, val row: Int, val column: Int, val direction: Direction) {
        val text = String(letters)
    }

    private const val EMPTY = '\u0000'

    private fun build(
        words: List<Word>, hiddenOptions: List<String>, language: WordLanguage, level: Level,
        block: List<CharArray>, rng: SeededRandom,
    ): Board? {
        val side = level.side
        val directions = level.directions
        val hiddenLengths = hiddenOptions.map { it.length }.toSet()
        val minHidden = hiddenLengths.min()
        val grid = CharArray(side * side) { EMPTY }
        var free = side * side
        val placed = mutableListOf<Placed>()
        val directionUse = IntArray(directions.size)

        // Longest first, with jitter so boards vary. Both sorts are stable.
        val order = shuffled(words, rng)
            .map { it to it.count + rng.nextInt(0, 3) }
            .sortedByDescending { it.second }
            .map { it.first }

        val bestPer = Array(directions.size) { ArrayList<Long>() }
        val scorePer = IntArray(directions.size)
        for (word in order) {
            if (free in hiddenLengths) break
            if (word.count >= side - 1 && placed.count { it.letters.size >= side - 1 } >= 1) continue
            val letters = word.letters.toCharArray()
            val reversed = word.letters.reversed()
            // A word inside another ("MAR" in "MARINO") would be found twice.
            if (placed.any { it.text.contains(word.letters) || word.letters.contains(it.text) || it.text.contains(reversed) }) continue

            for (d in directions.indices) {
                bestPer[d].clear()
                scorePer[d] = Int.MIN_VALUE
            }
            for (row in 0 until side) {
                for (column in 0 until side) {
                    for ((d, direction) in directions.withIndex()) {
                        val endRow = row + direction.dr * (letters.size - 1)
                        val endColumn = column + direction.dc * (letters.size - 1)
                        if (endRow !in 0 until side || endColumn !in 0 until side) continue
                        var overlap = 0
                        var fits = true
                        for (i in letters.indices) {
                            val existing = grid[(row + direction.dr * i) * side + column + direction.dc * i]
                            if (existing != EMPTY) {
                                if (existing != letters[i]) { fits = false; break }
                                overlap += 1
                            }
                        }
                        if (!fits) continue
                        val newCells = letters.size - overlap
                        val remaining = free - newCells
                        if (newCells <= 0 || remaining < minHidden) continue
                        var score = overlap
                        if (remaining in hiddenLengths) score += 1_000
                        val spot = (row.toLong() shl 32) or column.toLong()
                        if (score > scorePer[d]) {
                            scorePer[d] = score
                            bestPer[d].clear()
                            bestPer[d].add(spot)
                        } else if (score == scorePer[d]) {
                            bestPer[d].add(spot)
                        }
                    }
                }
            }
            val usable = directions.indices.filter { bestPer[it].isNotEmpty() }
            if (usable.isEmpty()) continue
            val finishing = usable.filter { scorePer[it] >= 1_000 }
            val candidatesPool = finishing.ifEmpty { usable }
            val fewest = candidatesPool.minOf { directionUse[it] }
            val candidates = candidatesPool.filter { directionUse[it] == fewest }
            // Swift's `max(by:)` keeps the first of equal maxima, like `maxBy`.
            val d = candidates.maxBy { scorePer[it] }
            val spots = bestPer[d]
            val spot = spots[rng.nextInt(0, spots.size - 1)]
            // Swift then picks from a one-element list: it still draws from the PRNG.
            rng.nextInt(0, 0)
            val row = (spot shr 32).toInt()
            val column = (spot and 0xFFFFFFFFL).toInt()
            val direction = directions[d]
            for (i in letters.indices) {
                val cell = (row + direction.dr * i) * side + column + direction.dc * i
                if (grid[cell] == EMPTY) free -= 1
                grid[cell] = letters[i]
            }
            directionUse[d] += 1
            placed += Placed(word, letters, row, column, direction)
        }

        if (free !in hiddenLengths) return null
        if (level == Level.HARD && placed.count { it.word.group != WordGroup.COMMON } < 3) return null
        val placedLetters = placed.map { it.text }.toSet()
        val options = hiddenOptions.filter { it.length == free && it !in placedLetters }
        if (options.isEmpty()) return null
        val hidden = options[rng.nextInt(0, options.size - 1)]

        // The hidden word in the leftover cells, in reading order.
        val hiddenCells = mutableListOf<Int>()
        var next = 0
        for (cell in grid.indices) {
            if (grid[cell] == EMPTY) {
                grid[cell] = hidden[next++]
                hiddenCells += cell
            }
        }
        if (!valid(grid, side, placed, block)) return null

        val placements = placed
            .map { Placement(it.word, it.row, it.column, it.direction) }
            .sortedWith { a, b ->
                val byLetters = compareScalars(a.word.letters, b.word.letters)
                if (byLetters != 0) byLetters else compareScalars(a.word.text, b.word.text)
            }
        return Board(side, language, grid.map { it.toString() }, placements, hidden, hiddenCells)
    }

    /** Every word reads exactly once; no blocked word reads anywhere unless it's inside a placed word along the same line. */
    private fun valid(letters: CharArray, side: Int, placed: List<Placed>, block: List<CharArray>): Boolean {
        for (word in placed) if (occurrences(word.letters, letters, side).size != 1) return false
        val lines = placed.map { p ->
            p.letters.indices.map { (p.row + p.direction.dr * it) * side + p.column + p.direction.dc * it }.toSet() to p.direction
        }
        for (blocked in block) {
            for (hit in occurrences(blocked, letters, side)) {
                val cells = hit.cells.toSet()
                val inside = lines.any { (lineCells, direction) ->
                    (direction == hit.direction || direction == hit.direction.reversed) && lineCells.containsAll(cells)
                }
                if (!inside) return false
            }
        }
        return true
    }

    data class Hit(val cells: List<Int>, val direction: Direction)

    fun occurrences(word: CharArray, letters: CharArray, side: Int): List<Hit> {
        if (word.isEmpty()) return emptyList()
        val first = word[0]
        val hits = mutableListOf<Hit>()
        for (start in letters.indices) {
            if (letters[start] != first) continue
            val row = start / side
            val column = start % side
            for (direction in Direction.ALL) {
                val endRow = row + direction.dr * (word.size - 1)
                val endColumn = column + direction.dc * (word.size - 1)
                if (endRow !in 0 until side || endColumn !in 0 until side) continue
                val cells = ArrayList<Int>(word.size)
                var match = true
                for (i in word.indices) {
                    val cell = (row + direction.dr * i) * side + column + direction.dc * i
                    if (letters[cell] != word[i]) { match = false; break }
                    cells += cell
                }
                if (match) hits += Hit(cells, direction)
            }
        }
        return hits
    }

    /** Fisher–Yates with our own generator. */
    fun <T> shuffled(items: List<T>, rng: SeededRandom): List<T> {
        val out = items.toMutableList()
        if (out.size <= 1) return out
        for (i in out.size - 1 downTo 1) {
            val j = rng.nextInt(0, i)
            val tmp = out[i]
            out[i] = out[j]
            out[j] = tmp
        }
        return out
    }

    /** Swift's `String <`: by Unicode scalars of the NFC form (not UTF-16 units). */
    internal fun compareScalars(a: String, b: String): Int {
        val x = java.text.Normalizer.normalize(a, java.text.Normalizer.Form.NFC).codePoints().toArray()
        val y = java.text.Normalizer.normalize(b, java.text.Normalizer.Form.NFC).codePoints().toArray()
        for (i in 0 until minOf(x.size, y.size)) if (x[i] != y[i]) return x[i].compareTo(y[i])
        return x.size.compareTo(y.size)
    }
}
