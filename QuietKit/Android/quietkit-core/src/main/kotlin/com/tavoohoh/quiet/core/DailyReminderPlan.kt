package com.tavoohoh.quiet.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Which daily-challenge notifications to schedule (port of QuietCore). At most two a day:
 * the reminder at `reminderHour` on a day not completed yet, and "streak at risk" at
 * `streakHour` when a streak of `minimumStreak` or more would end that night.
 */
object DailyReminderPlan {
    enum class Kind(val rawValue: String) { REMINDER("reminder"), STREAK_AT_RISK("streakAtRisk") }

    data class Item(
        val kind: Kind,
        val dayKey: String,
        /** Local wall-clock time it fires (minutes and seconds are 0). */
        val time: LocalDateTime,
        /** The streak that ends at midnight if the day isn't completed (0: none at stake). */
        val streak: Int,
    ) {
        /** Stable per day and kind, so planning again replaces instead of duplicating. */
        val identifier: String get() = "daily.$dayKey.${kind.rawValue}"
    }

    const val REMINDER_HOUR = 19
    const val STREAK_HOUR = 21
    const val MINIMUM_STREAK = 3
    const val DAYS_AHEAD = 7

    fun items(
        now: Instant, todayCompleted: Boolean, streak: Int, zone: ZoneId = ZoneId.systemDefault(),
        days: Int = DAYS_AHEAD, reminderHour: Int = REMINDER_HOUR, streakHour: Int = STREAK_HOUR,
        minimumStreak: Int = MINIMUM_STREAK,
    ): List<Item> {
        val today = DailyKey.dayKey(now, zone)
        val items = mutableListOf<Item>()
        val first = if (todayCompleted) 1 else 0
        for (offset in first until days) {
            val day = DailyKey.dayKey(today, offset) ?: continue
            // Missing the first day breaks the streak, so later days have none at stake.
            val atStake = if (offset == first) streak else 0
            val date = DailyKey.date(day) ?: continue
            for ((kind, hour) in listOf(Kind.REMINDER to reminderHour, Kind.STREAK_AT_RISK to streakHour)) {
                if (kind == Kind.STREAK_AT_RISK && atStake < minimumStreak) continue
                val time = date.atTime(hour, 0)
                if (time.atZone(zone).toInstant() <= now) continue
                items += Item(kind, day, time, if (atStake < minimumStreak) 0 else atStake)
            }
        }
        return items
    }
}

/** Today's daily among friends, for the reminder's text and the "Friends today" card. */
data class FriendsToday(val count: Int, val leaderName: String?, val leaderScore: Int?)

/** One past daily, head to head: who did better (higher score, then less time). */
object DailyDuel {
    enum class Result { WON, LOST, TIED, NOT_BOTH }

    data class Entry(val score: Int, val seconds: Int?)

    /** `seconds` null: unknown time, which loses a tie on score. */
    fun result(mine: Entry?, theirs: Entry?): Result {
        if (mine == null || theirs == null) return Result.NOT_BOTH
        if (mine.score != theirs.score) return if (mine.score > theirs.score) Result.WON else Result.LOST
        val a = mine.seconds ?: Int.MAX_VALUE
        val b = theirs.seconds ?: Int.MAX_VALUE
        return if (a == b) Result.TIED else if (a < b) Result.WON else Result.LOST
    }
}
