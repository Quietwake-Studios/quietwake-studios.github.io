package com.tavoohoh.quietnumbers.core

import com.tavoohoh.quiet.core.DailyKey
import com.tavoohoh.quiet.core.SeededRandom

/** The daily board: the same for every player on a day key, on iOS and Android. Dates and seeds: `DailyKey`. */
object DailyChallenge {
    /** The day's score to reach, 400–700 in steps of 50. */
    val GOALS: List<Int> = (400..700 step 50).toList()

    /** From its own stream of the day's seed, so it doesn't change the boards. */
    fun goal(dayKey: String): Int {
        val random = SeededRandom(DailyKey.seed(dayKey) xor 0x60A15C0EuL)
        return GOALS[random.nextInt(0, GOALS.size - 1)]
    }

    /** Every player gets the same boards: later stages come from the same generator, which only advances when a board is made. */
    fun session(dayKey: String, scoring: Scoring = Scoring.STANDARD): GameSession =
        GameSession(GameMode.Daily(dayKey), DailyKey.seed(dayKey), scoring, goal(dayKey))
}
