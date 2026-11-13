package com.tavoohoh.quiet.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Daily challenges: the same content for every player on a given day key (port of QuietCore). */
object DailyKey {
    /** Past days that can still be replayed (unscored). */
    const val REPLAY_WINDOW = 30
    /** Local hour when the next day's challenge starts: midnight. */
    const val ROLLOVER_HOUR = 0

    /** `yyyy-MM-dd` of the challenge on at `instant`: the local day, moved to the next one from `rolloverHour` on. */
    fun dayKey(instant: Instant, zone: ZoneId = ZoneId.systemDefault(), rolloverHour: Int = ROLLOVER_HOUR): String {
        val local = instant.atZone(zone)
        var day = local.toLocalDate()
        if (rolloverHour > 0 && local.hour >= rolloverHour) day = day.plusDays(1)
        return key(day)
    }

    /** When the challenge after the one on at `instant` starts. */
    fun nextRollover(after: Instant, zone: ZoneId = ZoneId.systemDefault(), rolloverHour: Int = ROLLOVER_HOUR): Instant {
        val local = after.atZone(zone)
        val today = ZonedDateTime.of(local.toLocalDate(), LocalTime.of(rolloverHour, 0), zone)
        return if (today.toInstant() > after) today.toInstant() else today.plusDays(1).toInstant()
    }

    /**
     * The calendar day of a `yyyy-MM-dd` key, or null if malformed. Lenient like Foundation's
     * calendar (month 13 or day 32 roll over), so both platforms agree on odd keys.
     */
    fun date(forDayKey: String): LocalDate? {
        val parts = forDayKey.split("-").filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
        if (parts.size != 3) return null
        return runCatching {
            LocalDate.of(parts[0], 1, 1).plusMonths(parts[1] - 1L).plusDays(parts[2] - 1L)
        }.getOrNull()
    }

    /** The key `days` days after (or before, if negative) `key`. */
    fun dayKey(key: String, addingDays: Int): String? = date(key)?.plusDays(addingDays.toLong())?.let(::key)

    /** 1 = Monday … 7 = Sunday. */
    fun weekday(ofDayKey: String): Int? = date(ofDayKey)?.dayOfWeek?.value

    /** Whole days from `from` to `to` (negative if `to` is earlier). */
    fun daysBetween(from: String, to: String): Int? {
        val a = date(from) ?: return null
        val b = date(to) ?: return null
        return ChronoUnit.DAYS.between(a, b).toInt()
    }

    fun seed(forDayKey: String): ULong = FNV1a.hash(forDayKey)

    fun key(day: LocalDate): String = String.format(Locale.ROOT, "%04d-%02d-%02d", day.year, day.monthValue, day.dayOfMonth)
}

/** Consecutive completed daily challenges. */
object Streaks {
    /** Streak still alive on `today`: it ends today, or yesterday if today isn't done yet. */
    fun current(completed: Set<String>, today: String): Int {
        var day: String? = if (today in completed) today else DailyKey.dayKey(today, -1)
        var count = 0
        while (day != null && day in completed) {
            count += 1
            day = DailyKey.dayKey(day, -1)
        }
        return count
    }

    fun longest(completed: Set<String>): Int {
        var longest = 0
        for (key in completed) {
            val previous = DailyKey.dayKey(key, -1)
            if (previous != null && previous in completed) continue
            var length = 0
            var day: String? = key
            while (day != null && day in completed) {
                length += 1
                day = DailyKey.dayKey(day, 1)
            }
            longest = maxOf(longest, length)
        }
        return longest
    }
}
