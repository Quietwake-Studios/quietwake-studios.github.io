package com.tavoohoh.quiet.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of QuietKit/Tests/QuietCoreTests. */
class RandomTests {
    @Test fun splitMix64IsStable() {
        val random = SeededRandom(0uL)
        assertEquals(0xE220A8397B1DCDAFuL, random.next())
        assertEquals(0x6E789E6AA1B965F4uL, random.next())
    }

    @Test fun fnv1aIsStable() {
        assertEquals(0xCBF29CE484222325uL, FNV1a.hash(""))
        assertEquals(0xAF63DC4C8601EC8CuL, FNV1a.hash("a"))
    }

    @Test fun boundedValuesStayInRange() {
        val random = SeededRandom(7uL)
        repeat(10_000) { assertTrue(random.nextInt(1, 9) in 1..9) }
    }
}

class DailyKeyTests {
    private fun date(iso: String) = Instant.parse(iso)
    private val bogota = ZoneId.of("America/Bogota")
    private val madrid = ZoneId.of("Europe/Madrid")

    @Test fun keyArithmeticCrossesMonthsAndYears() {
        assertEquals("2026-11-01", DailyKey.dayKey("2026-10-31", 1))
        assertEquals("2026-12-31", DailyKey.dayKey("2027-01-01", -1))
        assertEquals("2028-02-29", DailyKey.dayKey("2028-02-28", 1))
        assertNull(DailyKey.dayKey("bad", 1))
    }

    @Test fun rollsOverAtLocalMidnight() {
        assertEquals("2026-10-25", DailyKey.dayKey(date("2026-10-26T04:59:00Z"), bogota))
        assertEquals("2026-10-26", DailyKey.dayKey(date("2026-10-26T05:00:00Z"), bogota))
        assertEquals("2026-10-26", DailyKey.dayKey(date("2026-10-25T23:30:00Z"), madrid))
    }

    @Test fun nextRolloverIsNextMidnight() {
        assertEquals(date("2026-10-26T05:00:00Z"), DailyKey.nextRollover(date("2026-10-25T15:00:00Z"), bogota))
        assertEquals(date("2026-10-27T05:00:00Z"), DailyKey.nextRollover(date("2026-10-26T05:00:00Z"), bogota))
    }

    @Test fun dateRoundTripsThroughKey() {
        val day = DailyKey.date("2026-10-25")!!
        assertEquals("2026-10-25", DailyKey.dayKey(day.atStartOfDay(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC")))
    }

    @Test fun theEveningBelongsToTheSameDay() {
        assertEquals("2026-10-25", DailyKey.dayKey(date("2026-10-26T00:30:00Z"), bogota))
    }

    @Test fun rolloverHoldsAcrossDaylightSaving() {
        assertEquals("2026-10-25", DailyKey.dayKey(date("2026-10-25T22:59:00Z"), madrid))
        assertEquals("2026-10-26", DailyKey.dayKey(date("2026-10-25T23:00:00Z"), madrid))
    }

    @Test fun anEveningRolloverStillWorks() {
        assertEquals("2026-10-25", DailyKey.dayKey(date("2026-10-25T23:59:00Z"), bogota, rolloverHour = 19))
        assertEquals("2026-10-26", DailyKey.dayKey(date("2026-10-26T00:00:00Z"), bogota, rolloverHour = 19))
        assertEquals(date("2026-10-26T00:00:00Z"), DailyKey.nextRollover(date("2026-10-25T15:00:00Z"), bogota, rolloverHour = 19))
    }

    @Test fun weekdayIsMondayFirst() {
        assertEquals(1, DailyKey.weekday("2026-10-26"))
        assertEquals(2, DailyKey.weekday("2026-10-27"))
        assertEquals(7, DailyKey.weekday("2026-11-01"))
    }

    @Test fun lenientKeysLikeFoundation() {
        assertEquals("2026-03-02", DailyKey.dayKey("2026-02-30", 0))
        assertEquals("2026-11-01", DailyKey.dayKey("2026--11-01", 0))
    }

    @Test fun streaks() {
        val days = setOf("2026-10-22", "2026-10-23", "2026-10-24")
        assertEquals(3, Streaks.current(days, "2026-10-24"))
        assertEquals(3, Streaks.current(days, "2026-10-25"))
        assertEquals(0, Streaks.current(days, "2026-10-26"))
        assertEquals(0, Streaks.current(emptySet(), "2026-10-26"))
        assertEquals(0, Streaks.longest(emptySet()))
        val runs = setOf("2026-09-29", "2026-09-30", "2026-10-01", "2026-10-02", "2026-10-10", "2026-10-11")
        assertEquals(4, Streaks.longest(runs))
    }
}

class PaletteTests {
    private val textOn = listOf(
        Palette.Role.INK to listOf(Palette.Role.BACKGROUND, Palette.Role.SURFACE, Palette.Role.SURFACE2),
        Palette.Role.MUTED to listOf(Palette.Role.BACKGROUND, Palette.Role.SURFACE),
        Palette.Role.PRIMARY to listOf(Palette.Role.BACKGROUND, Palette.Role.SURFACE, Palette.Role.SURFACE2),
        Palette.Role.ACCENT to listOf(Palette.Role.BACKGROUND, Palette.Role.SURFACE),
        Palette.Role.ON_PRIMARY to listOf(Palette.Role.PRIMARY),
        Palette.Role.SUIT_RED to listOf(Palette.Role.SURFACE),
    )

    @Test fun textPassesAA() {
        for (palette in Palette.entries) for ((text, backgrounds) in textOn) for (background in backgrounds) {
            val t = palette.hex(text)
            val b = palette.hex(background)
            assertTrue(Contrast.ratio(t.light, b.light) >= 4.5, "$palette light: $text on $background")
            assertTrue(Contrast.ratio(t.dark, b.dark) >= 4.5, "$palette dark: $text on $background")
        }
    }

    @Test fun everyPaletteHasBothModes() {
        for (palette in Palette.entries) {
            assertTrue(palette.hex(Palette.Role.BACKGROUND).light != palette.hex(Palette.Role.BACKGROUND).dark)
            assertTrue(palette.hex(Palette.Role.INK).light != palette.hex(Palette.Role.INK).dark)
        }
    }

    @Test fun valuesMatchSwift() {
        assertEquals(Palette.Hex("#0E6B78", "#5BC2CF"), Palette.TEAL_CORAL.hex(Palette.Role.PRIMARY))
        assertEquals(Palette.Hex("#B4503A", "#F08C74"), Palette.SAGE_SAND.hex(Palette.Role.SUIT_RED))
        assertEquals(Palette.Hex("#B3001B", "#FF6B6B"), Palette.HIGH_CONTRAST.hex(Palette.Role.SUIT_RED))
        assertEquals(Palette.Hex("#E8E2D6", "#2D322D"), Palette.SAGE_CORAL.hex(Palette.Role.SEPARATOR))
    }
}

class DailyReminderPlanTests {
    private val bogota = ZoneId.of("America/Bogota")
    private fun date(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 11, day, hour, minute).atZone(bogota).toInstant()
    private fun plan(now: Instant, completed: Boolean, streak: Int) = DailyReminderPlan.items(now, completed, streak, bogota)
    private val reminder = DailyReminderPlan.Kind.REMINDER
    private val risk = DailyReminderPlan.Kind.STREAK_AT_RISK

    @Test fun todayGetsBothWhenAStreakIsAtStake() {
        val items = plan(date(14, 10), completed = false, streak = 8)
        val today = items.filter { it.dayKey == "2026-11-14" }
        assertEquals(listOf(reminder, risk), today.map { it.kind })
        assertEquals(listOf(19, 21), today.map { it.time.hour })
        assertTrue(today.all { it.streak == 8 })
        val later = items.filter { it.dayKey != "2026-11-14" }
        assertEquals(DailyReminderPlan.DAYS_AHEAD - 1, later.size)
        assertTrue(later.all { it.kind == reminder && it.streak == 0 })
    }

    @Test fun atMostTwoADay() {
        val items = plan(date(14, 10), completed = false, streak = 30)
        assertTrue(items.groupBy { it.dayKey }.values.all { it.size <= 2 })
        assertEquals(items.size, items.map { it.identifier }.toSet().size)
    }

    @Test fun shortStreaksGetNoWarning() {
        assertTrue(plan(date(14, 10), completed = false, streak = 2).all { it.kind == reminder && it.streak == 0 })
    }

    @Test fun completingTodayMovesTheStakeToTomorrow() {
        val items = plan(date(14, 18), completed = true, streak = 9)
        assertTrue(items.none { it.dayKey == "2026-11-14" })
        val tomorrow = items.filter { it.dayKey == "2026-11-15" }
        assertEquals(listOf(reminder, risk), tomorrow.map { it.kind })
        assertTrue(tomorrow.all { it.streak == 9 })
        assertEquals(1, items.count { it.kind == risk })
    }

    @Test fun pastHoursAreSkipped() {
        val evening = plan(date(14, 20), completed = false, streak = 5).filter { it.dayKey == "2026-11-14" }
        assertEquals(listOf(risk), evening.map { it.kind })
        val night = plan(date(14, 22), completed = false, streak = 5)
        assertTrue(night.none { it.dayKey == "2026-11-14" })
        assertTrue(night.all { it.kind == reminder && it.streak == 0 })
    }

    @Test fun duelsGoToTheHigherScoreThenTheFasterTime() {
        fun e(score: Int, seconds: Int?) = DailyDuel.Entry(score, seconds)
        assertEquals(DailyDuel.Result.WON, DailyDuel.result(e(900, 300), e(800, 100)))
        assertEquals(DailyDuel.Result.LOST, DailyDuel.result(e(900, 300), e(900, 200)))
        assertEquals(DailyDuel.Result.WON, DailyDuel.result(e(900, 200), e(900, null)))
        assertEquals(DailyDuel.Result.TIED, DailyDuel.result(e(900, 200), e(900, 200)))
        assertEquals(DailyDuel.Result.NOT_BOTH, DailyDuel.result(null, e(900, 200)))
    }
}
