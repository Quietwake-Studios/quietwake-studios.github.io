package com.tavoohoh.quiet.core

/**
 * What a game pushes to Play Games after a sign-in or a finished game (iOS `pushToGameCenter`),
 * by concept ID (the Game Center ID; the app's `games-ids.xml` maps it to Play Console's).
 * Local data is the source of truth: Play only mirrors it.
 */
data class PlayGamesPush(
    /** Standard achievements earned. */
    val unlocks: List<String>,
    /** Incremental achievements: steps so far (capped at the total); Play keeps the highest. */
    val steps: Map<String, Int>,
    val scores: List<Score>,
) {
    data class Score(val leaderboard: String, val value: Long, val tag: String? = null)
}

/** Debug `demo` leaderboards (iOS `DemoData.leaderboard`; names from the Quiet Numbers canvas). */
object LeaderboardDemo {
    private val names = listOf("juli", "tatita12", "vale", "guss", "juanca", "cori16", "eliasb", "mariadelsea", "aleja", "nova47")
    private val friends = setOf("vale", "guss", "juanca", "cori16")
    private val seconds = listOf(214, 236, 251, 268, 240, 290, 311, 284, 330, 356)

    /** Ten rows with these scores (best first); "guss" is the player. Daily rows carry a time. */
    fun entries(scores: List<Long>, daily: Boolean, friendsOnly: Boolean): List<LeaderboardEntry> {
        val rows = names.indices.filter { !friendsOnly || names[it] in friends }
        return rows.mapIndexed { rank, i ->
            LeaderboardEntry(names[i], rank + 1, names[i], scores[i], names[i] == "guss", if (daily) ScoreTag.daily("demo", seconds[i]) else "")
        }
    }

    /** "#N among friends" on the Daily screen. */
    const val FRIENDS_RANK = 2
}
