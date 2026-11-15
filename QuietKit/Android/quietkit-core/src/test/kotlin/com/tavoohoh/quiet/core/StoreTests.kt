package com.tavoohoh.quiet.core

import kotlinx.serialization.Serializable
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The family's local store and daily results (iOS: `SyncMergeTests`, `DailyTieBreakTests`), and the reminder texts. */
class StoreTests {
    @Serializable private data class Game(val score: Int)
    @Serializable private data class Profile(val best: Int = 0)

    private fun tempDir(): File = Files.createTempDirectory("quiet-store").toFile().also { it.deleteOnExit() }
    private fun store(dir: File?) = QuietStore(dir, Game.serializer(), Profile.serializer(), Profile())

    @Test fun betterDailyResultWins() {
        val started = DailyRecord(0)
        val played = DailyRecord(80, isFinished = true)
        val completed = DailyRecord(40, isFinished = true, isCompleted = true)
        assertEquals(played, played.better(started))
        assertEquals(completed, played.better(completed))
        assertEquals(60, completed.better(DailyRecord(60, isFinished = true, isCompleted = true)).score)
        val slow = DailyRecord(500, true, true, 300)
        val fast = DailyRecord(500, true, true, 200)
        assertEquals(fast, slow.better(fast))
        assertEquals(slow, DailyRecord(500, true, true).better(slow))
    }

    @Test fun keepBestAndMerge() {
        val s = store(null)
        assertFalse(s.keepBest("2026-10-20", 30, false, 50))
        assertTrue(s.keepBest("2026-10-20", 20, true, 60))
        assertFalse(s.keepBest("2026-10-20", 10, true, 30))
        assertEquals(DailyRecord(20, true, true, 60), s.record("2026-10-20"))
        s.keepBest("2026-10-21", 90, true, 10)
        s.mergeDailies(
            mapOf(
                "2026-10-20" to DailyRecord(70, true, true),
                "2026-10-21" to DailyRecord(10, true, false),
                "2026-10-22" to DailyRecord(55, true, true),
            ),
        )
        assertEquals(70, s.record("2026-10-20")!!.score)
        assertEquals(90, s.record("2026-10-21")!!.score)
        assertEquals(setOf("2026-10-20", "2026-10-21", "2026-10-22"), s.completedDays)
    }

    @Test fun everythingRoundTripsOnDisk() {
        val dir = tempDir()
        val s = store(dir)
        s.saveGame(Game(12), "classic")
        s.saveGame(Game(3), "day-2026-11-01")
        s.startAttempt("2026-11-01")
        s.keepBest("2026-11-02", 400, true, 90)
        s.saveProfile(Profile(500))
        val r = store(dir)
        assertEquals(Game(12), r.loadGame("classic"))
        assertEquals(setOf("2026-11-01"), r.slots("day-"))
        assertEquals(1, r.attempts("2026-11-01"))
        assertEquals(setOf("2026-11-02"), r.completedDays)
        assertEquals(Profile(500), r.loadProfile())
        r.deleteGame("day-2026-11-01")
        assertTrue(store(dir).slots("day-").isEmpty())
        r.deleteEverything()
        assertNull(store(dir).loadGame("classic"))
        assertEquals(Profile(), store(dir).loadProfile())
    }

    @Test fun aBrokenFileStartsEmpty() {
        val dir = tempDir()
        File(dir, "dailies.json").writeText("{ not json")
        assertTrue(store(dir).dailies.isEmpty())
    }

    @Test fun reminderTextsFollowStreakAndFriends() {
        val zone = ZoneId.systemDefault()
        val morning = LocalDate.now(zone).atTime(9, 0).atZone(zone).toInstant()
        fun reminder(streak: Int) = DailyReminderPlan.items(morning, false, streak, zone).first { it.kind == DailyReminderPlan.Kind.REMINDER }
        assertEquals(ReminderText.Waiting, ReminderText.of(reminder(1)))
        assertEquals(ReminderText.Streak(8), ReminderText.of(reminder(8)))
        assertEquals(ReminderText.OneFriend("Ana", 560), ReminderText.of(reminder(8), FriendsToday(1, "Ana", 560)))
        assertEquals(ReminderText.Friends(3, "Ana", 560), ReminderText.of(reminder(8), FriendsToday(3, "Ana", 560)))
        val risk = DailyReminderPlan.items(morning, false, 5, zone).first { it.kind == DailyReminderPlan.Kind.STREAK_AT_RISK }
        assertEquals(ReminderText.StreakAtRisk(5), ReminderText.of(risk))
    }
}
