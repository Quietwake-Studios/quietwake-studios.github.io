package com.tavoohoh.quiet.core

/**
 * When to ask for a store review (family rule, 2026-11-13): once per install, when the summary
 * of the 3rd finished game (free or daily) is closed. Never mid-game, never over a fresh board
 * and never with a custom question first; the system dialog does the asking. If that summary is
 * closed with "New Game", the request waits for the next return to Home or the Daily screen.
 * "Reset progress" keeps this state.
 */
data class ReviewPrompt(
    /** Games whose summary was closed. */
    val finishedGames: Int = 0,
    /** The request was decided (made, or waiting in `pending`). */
    val asked: Boolean = false,
    /** Decided while going to a new board: ask on the next return to Home or the Daily screen. */
    val pending: Boolean = false,
) {
    /**
     * A summary was closed; `toNewGame` when it was closed with "New Game". Returns the new
     * state and whether to request the review now.
     */
    fun gameFinished(toNewGame: Boolean = false): Pair<ReviewPrompt, Boolean> {
        val count = finishedGames + 1
        val due = !asked && count >= GAMES
        return ReviewPrompt(count, asked || due, pending || (due && toNewGame)) to (due && !toNewGame)
    }

    /** Home or the Daily screen is showing: a deferred request goes now. */
    fun arrived(): Pair<ReviewPrompt, Boolean> = copy(pending = false) to pending

    companion object {
        const val GAMES = 3
    }
}
