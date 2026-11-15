package com.tavoohoh.quietwords.core

import com.tavoohoh.quiet.core.DailyKey
import com.tavoohoh.quiet.core.DailyReminderPlan
import com.tavoohoh.quiet.core.FriendsToday
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import com.tavoohoh.quiet.core.ReminderText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The daily reminders as this app uses them (port of iOS `AppTests/ReminderTests`; the plan and the texts are tested in quietkit-core). */
class ReminderTests {
    private val zone = ZoneId.systemDefault()

    private fun todaysReminder(streak: Int): DailyReminderPlan.Item {
        val morning = LocalDate.now(zone).atTime(9, 0).atZone(zone).toInstant()
        return DailyReminderPlan.items(morning, todayCompleted = false, streak = streak, zone = zone).first { it.kind == DailyReminderPlan.Kind.REMINDER }
    }

    /**
     * Finishing today's challenge (in either language) moves the reminders to tomorrow and
     * offers them once, back from the game (the app asks only if never offered).
     */
    @Test fun aCompletedDailyOffersTheReminderOnce() {
        val today = DailyKey.dayKey(Instant.now())
        val play = WordsPlay(WordsStore(null), today = { today })
        var offered = false
        fun leave(session: GameSession?): Boolean = play.leftGame(session) && !offered
        assertFalse(play.reminderState().first)
        val started = play.playDaily(today, WordLanguage.ES)!!
        val s = started.session
        for (p in s.board.placements) {
            val cells = p.cells(s.board.side)
            play.update(s, s.select(cells.first(), cells.last())!!)
        }
        assertTrue(play.reminderState().first)
        assertTrue(leave(s))
        offered = true
        // The other language the same day: the day was already done, nothing to offer.
        val es = play.playDaily(today, WordLanguage.EN)!!.session
        for (p in es.board.placements) {
            val cells = p.cells(es.board.side)
            play.update(es, es.select(cells.first(), cells.last())!!)
        }
        assertFalse(play.leftGame(es))
        // Today done: the plan starts tomorrow.
        val plan = DailyReminderPlan.items(Instant.now(), play.reminderState().first, play.reminderState().second)
        assertTrue(plan.none { it.dayKey == today })
    }
}
