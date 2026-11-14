package com.tavoohoh.quiet.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The store review is asked once, closing the 3rd finished game's summary. */
class ReviewPromptTests {
    private fun finish(state: ReviewPrompt, times: Int): Pair<ReviewPrompt, List<Boolean>> {
        var s = state
        val asks = mutableListOf<Boolean>()
        repeat(times) { s.gameFinished().let { (next, ask) -> s = next; asks += ask } }
        return s to asks
    }

    @Test fun asksOnTheThirdOnly() {
        val (state, asks) = finish(ReviewPrompt(), 3)
        assertEquals(listOf(false, false, true), asks)
        assertTrue(state.asked)
        assertEquals(3, state.finishedGames)
    }

    @Test fun neverTwice() {
        val (_, asks) = finish(ReviewPrompt(), 20)
        assertEquals(1, asks.count { it })
    }

    /** "Reset progress" doesn't touch it: the saved state keeps `asked`, so nothing re-arms. */
    @Test fun resetDoesNotRearm() {
        val (afterThree, _) = finish(ReviewPrompt(), 3)
        val (_, asks) = finish(afterThree, 5)
        assertFalse(asks.any { it })
    }

    /** Closed with "New Game": counted, but the dialog waits for Home or the Daily screen. */
    @Test fun newGameDefersToTheNextReturn() {
        val (two, _) = finish(ReviewPrompt(), 2)
        val (third, now) = two.gameFinished(toNewGame = true)
        assertFalse(now)
        assertEquals(3, third.finishedGames)
        assertTrue(third.pending)
        // Another game on the fresh board doesn't ask either; the return does, once.
        val (fourth, again) = third.gameFinished(toNewGame = true)
        assertFalse(again)
        val (back, ask) = fourth.arrived()
        assertTrue(ask)
        assertFalse(back.arrived().second)
        assertFalse(finish(back, 5).second.any { it })
    }

    @Test fun arrivingWithNothingPendingAsksNothing() {
        assertFalse(ReviewPrompt().arrived().second)
        assertFalse(finish(ReviewPrompt(), 3).first.arrived().second)
    }
}
