package com.tavoohoh.quietwords.core

import com.tavoohoh.quiet.core.ScoreTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What goes to Play Games, and the saved game's merge. */
class PlayGamesTests {
    private val today = "2026-11-14"

    @Test fun idsMatchGameCenter() {
        assertEquals(
            listOf("qw_first_puzzle", "qw_words_100", "qw_words_1000", "qw_words_10000", "qw_theme_complete", "qw_hidden_early",
                "qw_daily_7", "qw_daily_30", "qw_hard_fast", "qw_bilingual", "qw_in_the_red"),
            Achievement.entries.map { it.conceptID },
        )
        assertEquals(listOf("qw_daily_en", "qw_daily_es", "qw_best_hard_en", "qw_best_hard_es", "qw_longest_streak"), Leaderboard.entries.map { it.conceptID })
        assertEquals(setOf(Achievement.WORDS_100, Achievement.WORDS_1000, Achievement.WORDS_10000, Achievement.HABIT, Achievement.CONSISTENCY),
            Achievement.entries.filter { it.isIncremental }.toSet())
    }

    @Test fun nothingYetMeansNothingToSend() {
        val plan = PlayGamesPlan.of(PlayerProgress(), emptyMap(), today)
        assertTrue(plan.unlocks.isEmpty() && plan.steps.isEmpty() && plan.scores.isEmpty())
    }

    @Test fun planMirrorsIOS() {
        val progress = PlayerProgress(
            unlocked = setOf(Achievement.FIRST_PUZZLE, Achievement.WORDS_100, Achievement.BILINGUAL),
            wordsFound = 412, bestStreak = 3, bestScores = mapOf("hard-es" to 1310),
        )
        val dailies = mapOf(
            "$today#en" to DailyRecord(742, isFinished = true, isCompleted = true, seconds = 251, attempts = 2),
            "$today#es" to DailyRecord(0, isFinished = false, isCompleted = false, seconds = null, attempts = 1),
            "2026-11-13#es" to DailyRecord(600, isFinished = true, isCompleted = true, seconds = 300, attempts = 1),
            "2026-11-12#en" to DailyRecord(-20, isFinished = true, isCompleted = true, seconds = 400, attempts = 1),
        )
        val plan = PlayGamesPlan.of(progress, dailies, today)
        assertEquals(listOf(Achievement.FIRST_PUZZLE, Achievement.BILINGUAL), plan.unlocks)
        assertEquals(100, plan.steps[Achievement.WORDS_100])
        assertEquals(412, plan.steps[Achievement.WORDS_1000])
        assertEquals(3, plan.steps[Achievement.HABIT])
        assertNull(plan.steps[Achievement.THEME_COMPLETE])
        assertEquals(
            listOf(
                PlayGamesPlan.Score(Leaderboard.DAILY_EN, 742, ScoreTag.daily(today, 251)),
                PlayGamesPlan.Score(Leaderboard.BEST_HARD_ES, 1310),
                PlayGamesPlan.Score(Leaderboard.LONGEST_STREAK, 3),
            ),
            plan.scores,
        )
    }

    @Test fun snapshotMergeNeverLosesProgress() {
        val a = CloudSnapshot(
            Profile(PlayerProgress(unlocked = setOf(Achievement.FIRST_PUZZLE), wordsFound = 50), Album().add(listOf("CORAL"), "ocean", WordLanguage.EN).first),
            mapOf("$today#en" to DailyRecord(500, isFinished = true, isCompleted = true, seconds = 200, attempts = 1)),
        )
        val b = CloudSnapshot(
            Profile(PlayerProgress(unlocked = setOf(Achievement.HABIT), wordsFound = 80), Album().add(listOf("TIDE"), "ocean", WordLanguage.EN).first),
            mapOf("$today#en" to DailyRecord(500, isFinished = true, isCompleted = true, seconds = 150, attempts = 3), "$today#es" to DailyRecord(300, isFinished = true, isCompleted = true, seconds = 90, attempts = 1)),
        )
        val m = CloudSnapshot.decode(a.merged(b).encode())!!
        assertEquals(setOf(Achievement.FIRST_PUZZLE, Achievement.HABIT), m.profile.progress.unlocked)
        assertEquals(80, m.profile.progress.wordsFound)
        assertEquals(setOf("CORAL", "TIDE"), m.profile.album.words["ocean/en"])
        assertEquals(DailyRecord(500, isFinished = true, isCompleted = true, seconds = 150, attempts = 3), m.dailies["$today#en"])
        assertEquals(2, m.dailies.size)
        assertNull(CloudSnapshot.decode("{broken".toByteArray()))
    }

    /** A second device's saved game: its streak days and progress join this one's. */
    @Test fun mergingTheCloudKeepsBoth() {
        val play = WordsPlay(WordsStore(null), today = { today })
        val started = play.newFree(FreeGame.session("ocean", Level.GENTLE, WordLanguage.EN, "cloud")!!)
        val s = started.session
        for (p in s.board.placements) p.cells(s.board.side).let { play.update(s, s.select(it.first(), it.last())!!) }
        val remote = CloudSnapshot(
            Profile(PlayerProgress(unlocked = setOf(Achievement.HABIT), wordsFound = 5000, bestStreak = 7)),
            mapOf("2026-11-13#es" to DailyRecord(600, isFinished = true, isCompleted = true, seconds = 300, attempts = 1), "$today#en" to DailyRecord(700, isFinished = true, isCompleted = true, seconds = 250, attempts = 1)),
        )
        play.mergeCloud(remote)
        assertEquals(5000, play.progress.wordsFound)
        assertTrue(Achievement.FIRST_PUZZLE in play.progress.unlocked && Achievement.HABIT in play.progress.unlocked)
        assertEquals(2, play.currentStreak)
        val plan = play.playGamesPlan()
        assertTrue(plan.scores.any { it.leaderboard == Leaderboard.DAILY_EN && it.value == 700L })
        assertEquals(1000, plan.steps[Achievement.WORDS_1000])
        assertEquals(play.cloudSnapshot().dailies.keys, setOf("2026-11-13#es", "$today#en"))
    }
}
