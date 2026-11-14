package com.tavoohoh.quietnumbers.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Port of iOS `MatchRulesTests`: the 3 connections. */
class MatchRulesTests {
    private fun conn(board: Board, a: Int, b: Int) = MatchRules.pair(board, a, b)?.connection

    @Test fun values() {
        for ((a, b, e) in listOf(Triple(4, 4, true), Triple(3, 7, true), Triple(5, 5, true), Triple(1, 9, true), Triple(2, 3, false), Triple(6, 6, true), Triple(8, 3, false))) {
            assertEquals(e, MatchRules.valuesMatch(a, b))
        }
    }

    @Test fun adjacent() {
        assertEquals(Connection.ADJACENT, conn(makeBoard(listOf(4, 4, 1, 2, 3, 5, 6, 8, 2)), 0, 1))
        assertEquals(Connection.ADJACENT, conn(makeBoard(listOf(3, 1, 2, 1, 2, 1, 2, 1, 2), listOf(7, 2, 1, 2, 1, 2, 1, 2, 1)), 0, 9))
        val d = makeBoard(listOf(1, 5, 1, 2, 1, 2, 1, 2, 1), listOf(5, 2, 5, 1, 2, 1, 2, 1, 2))
        assertEquals(Connection.ADJACENT, conn(d, 1, 9))
        assertEquals(Connection.ADJACENT, conn(d, 1, 11))
        assertNull(MatchRules.pair(makeBoard(listOf(2, 3, 1, 2, 1, 2, 1, 2, 1)), 0, 1))
        val c = makeBoard(listOf(0, 4, 4, 1, 2, 1, 2, 1, 2))
        assertNull(MatchRules.pair(c, 0, 1))
        assertNull(MatchRules.pair(c, 1, 1))
    }

    @Test fun acrossCleared() {
        assertEquals(Connection.ACROSS_CLEARED, conn(makeBoard(listOf(6, 0, 0, 4, 1, 2, 1, 2, 1)), 0, 3))
        assertEquals(Connection.ACROSS_CLEARED, conn(makeBoard(listOf(8, 1, 2, 1, 2, 1, 2, 1, 2), listOf(0, 2, 1, 2, 1, 2, 1, 2, 1), listOf(2, 1, 2, 1, 2, 1, 2, 1, 2)), 0, 18))
        assertEquals(Connection.ACROSS_CLEARED, conn(makeBoard(listOf(9, 2, 1, 2, 1, 2, 1, 2, 1), listOf(1, 0, 2, 1, 2, 1, 2, 1, 2), listOf(2, 1, 9, 2, 1, 2, 1, 2, 1)), 0, 20))
        assertNull(MatchRules.pair(makeBoard(listOf(8, 1, 2, 1, 2, 1, 2, 1, 2), listOf(3, 2, 1, 2, 1, 2, 1, 2, 1), listOf(2, 1, 2, 1, 2, 1, 2, 1, 2)), 0, 18))
        assertNull(MatchRules.pair(makeBoard(listOf(1, 2, 1, 2, 1, 2, 1, 3, 4), listOf(2, 1, 2, 1, 2, 1, 2, 1, 2), listOf(4, 3, 1, 2, 1, 2, 1, 2, 1)), 8, 18))
    }

    @Test fun readingOrder() {
        assertEquals(Connection.ADJACENT, conn(makeBoard(listOf(2, 1, 2, 1, 2, 1, 2, 1, 7), listOf(3, 2, 1, 2, 1, 2, 1, 2, 1)), 8, 9))
        assertEquals(Connection.ACROSS_CLEARED, conn(makeBoard(listOf(2, 1, 2, 1, 2, 1, 6, 0, 0), listOf(0, 0, 4, 2, 1, 2, 1, 2, 1)), 6, 11))
        val pairs = MatchRules.validPairs(makeBoard(listOf(5, 5, 5, 2, 1, 2, 1, 2, 1)))
        assertEquals(setOf(listOf(0, 1), listOf(1, 2)), pairs.map { listOf(it.first, it.second) }.toSet())
        assertEquals(2, pairs.size)
    }
}
