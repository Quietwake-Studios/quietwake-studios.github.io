package com.tavoohoh.quiet.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The daily tie-break (iOS `GameCenterServiceTests`, ported) and the score tag. */
class LeaderboardTests {
    private fun e(id: String, rank: Int, score: Long, seconds: Int?, day: String = "2026-11-14") =
        LeaderboardEntry(id, rank, id, score, false, ScoreTag.daily(day, seconds))

    @Test fun tagRoundTrips() {
        val tag = ScoreTag.daily("2026-11-14", 125)
        assertEquals("2026-11-14.126", tag)
        assertEquals("2026-11-14", ScoreTag.day(tag))
        assertEquals(125, ScoreTag.seconds(tag))
        assertNull(ScoreTag.seconds(ScoreTag.daily("2026-11-14", null)))
        assertNull(ScoreTag.day(""))
        assertNull(ScoreTag.seconds("garbage"))
    }

    @Test fun tiesGoToTheFasterAndShareRanks() {
        val ranked = Leaderboards.rankedByScoreAndTime(
            listOf(e("a", 1, 500, 200), e("b", 2, 500, 150), e("c", 3, 500, null), e("d", 4, 400, 100), e("e", 5, 500, 150)),
        )
        assertEquals(listOf("b", "e", "a", "c", "d"), ranked.map { it.id })
        assertEquals(listOf(1, 1, 3, 4, 5), ranked.map { it.rank })
    }

    /** Play's daily span ends at midnight Pacific: yesterday's tagged results don't show today. */
    @Test fun todayKeepsOnlyTodaysResults() {
        val page = listOf(e("old", 1, 900, 100, day = "2026-11-13"), e("a", 2, 500, 200), e("b", 3, 500, 150))
        val today = Leaderboards.today(page, "2026-11-14")
        assertEquals(listOf("b", "a"), today.map { it.id })
        assertEquals(listOf(1, 2), today.map { it.rank })
    }
}
