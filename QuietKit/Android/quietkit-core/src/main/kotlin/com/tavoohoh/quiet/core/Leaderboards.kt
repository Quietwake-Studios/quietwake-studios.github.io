package com.tavoohoh.quiet.core

/**
 * Leaderboard rows and the daily tie-break, shared by the family (port of the pure half of
 * QuietUI's `GameCenterService`). Google Play Games ranks by score only, so a daily score
 * carries its day and time in the score tag and the app reorders the page it loads.
 */
data class LeaderboardEntry(
    val id: String,
    val rank: Int,
    val name: String,
    val score: Long,
    val isLocalPlayer: Boolean,
    /** What the app sent with the score (`ScoreTag`), empty if nothing. */
    val tag: String = "",
)

/**
 * The tag a daily score carries: the day it belongs to and the seconds played + 1 (0 = unknown
 * time), like iOS's `context`. Play's daily boards reset at midnight Pacific, not at each
 * player's midnight, so the day also lets the app show only today's results. URL-safe, ≤ 64.
 */
object ScoreTag {
    fun daily(dayKey: String, seconds: Int?): String = "$dayKey.${seconds?.let { it + 1 } ?: 0}"

    /** Day key of a daily tag, or null. */
    fun day(tag: String): String? = tag.substringBefore('.', "").takeIf { DailyKey.date(it) != null }

    /** Time played that came with a daily score, if any. */
    fun seconds(tag: String): Int? = tag.substringAfter('.', "").toIntOrNull()?.takeIf { it > 0 }?.minus(1)

    /** The context-like value used for ordering: seconds + 1, or 0 when unknown. */
    internal fun context(tag: String): Int = seconds(tag)?.plus(1) ?: 0
}

object Leaderboards {
    /**
     * A daily board ends in near ties; Play ranks only by score, so the page is reordered:
     * higher score first, then less time; no time goes last among its score. Same score and
     * time share a rank (1, 2, 2, 4). Same rule as iOS's `rankedByScoreAndTime`.
     */
    fun rankedByScoreAndTime(entries: List<LeaderboardEntry>): List<LeaderboardEntry> {
        fun key(e: LeaderboardEntry): Pair<Long, Int> = -e.score to ScoreTag.context(e.tag).let { if (it == 0) Int.MAX_VALUE else it }
        val sorted = entries.withIndex().sortedWith(compareBy<IndexedValue<LeaderboardEntry>>({ key(it.value).first }, { key(it.value).second }, { it.index }))
            .map { it.value }
        val first = entries.minOfOrNull { it.rank } ?: 1
        val ranked = mutableListOf<LeaderboardEntry>()
        for ((index, entry) in sorted.withIndex()) {
            val previous = ranked.lastOrNull()
            val rank = if (previous != null && key(previous) == key(entry)) previous.rank else first + index
            ranked += entry.copy(rank = rank)
        }
        return ranked
    }

    /**
     * Today's page of a daily board: only results tagged with today's day (Play's own daily
     * span ends at midnight Pacific), ranked by score and time and renumbered from 1.
     */
    fun today(entries: List<LeaderboardEntry>, dayKey: String): List<LeaderboardEntry> {
        val todays = entries.filter { ScoreTag.day(it.tag) == dayKey }.mapIndexed { i, e -> e.copy(rank = i + 1) }
        return rankedByScoreAndTime(todays)
    }
}
