package com.tavoohoh.quietwords.core

import com.tavoohoh.quiet.core.DailyKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class DailyTests {
    /** The same reference boards as Swift's DailyTests. */
    private val reference = listOf(
        Triple("2026-11-09", WordLanguage.EN, "biology" to "46c3213a6c53a923"),
        Triple("2026-11-09", WordLanguage.ES, "biology" to "7e90ec475cc91011"),
        Triple("2026-11-11", WordLanguage.EN, "botany" to "d39d4820aa8f0b6e"),
        Triple("2026-11-14", WordLanguage.ES, "food" to "dfbee008d7950452"),
        Triple("2026-11-15", WordLanguage.EN, "biology" to "2a854db7fffe76cc"),
    )

    @Test fun sameDateSameBoard() {
        for ((day, language, expected) in reference) {
            val board = DailyChallenge.board(day, language)!!
            assertEquals(expected.first, DailyChallenge.themeID(day), day)
            assertEquals(expected.second, fingerprint(board), "$day $language")
        }
    }

    @Test fun levelByWeekday() {
        val expected = listOf(Level.GENTLE, Level.GENTLE, Level.MEDIUM, Level.MEDIUM, Level.MEDIUM, Level.HARD, Level.HARD)
        for ((offset, level) in expected.withIndex()) {
            val day = DailyKey.dayKey("2026-11-09", offset)!!
            assertEquals(level, DailyChallenge.level(day))
            assertEquals(offset == 6, DailyChallenge.hasLongHidden(day))
        }
        val sunday = DailyChallenge.board("2026-11-15", WordLanguage.ES)!!
        assertTrue(sunday.hidden.length in 7..9)
    }

    @Test fun languagesHaveTheirOwnBoard() {
        val en = DailyChallenge.session("2026-11-10", WordLanguage.EN)!!
        val es = DailyChallenge.session("2026-11-10", WordLanguage.ES)!!
        assertEquals(en.themeID, es.themeID)
        assertTrue(en.board.language == WordLanguage.EN && es.board.language == WordLanguage.ES)
        assertNotEquals(en.board.letters, es.board.letters)
        assertEquals(GameSession.Mode.Daily("2026-11-10"), en.mode)
    }

    @Test fun aYearOfDailies() {
        val library = ThemeLibrary.shared
        var day = "2026-11-01"
        var previous: String? = null
        var round = mutableListOf<String>()
        repeat(365) {
            val theme = DailyChallenge.themeID(day)!!
            assertNotEquals(previous, theme, "$day repeats $theme")
            round += theme
            if (round.size == library.themes.size) {
                assertEquals(library.themes.size, round.toSet().size, "round ending $day")
                round = mutableListOf()
            }
            for (language in WordLanguage.entries) {
                val level = DailyChallenge.level(day)
                val board = DailyChallenge.board(day, language) ?: fail("$day $language: no board")
                assertEquals(emptyList(), problems(board, level, library.blocklist.getValue(language)), "$day $language")
            }
            previous = theme
            day = DailyKey.dayKey(day, 1)!!
        }
    }

    @Test fun lockFreezesTheWords() {
        val full = ThemeLibrary.shared
        val counts = DailyLock.Counts(common = 90, crafted = 70, rare = 30, hidden = 15)
        val themes = full.themes.associate { it.id to mapOf("en" to counts, "es" to counts) }
        val locked = ThemeLibrary(full.themes, full.blocklist, DailyLock(listOf(DailyLock.Entry("2026-11-01", themes))))
        val board = DailyChallenge.board("2026-11-12", WordLanguage.EN, locked)!!
        val id = DailyChallenge.themeID("2026-11-12", locked)!!
        val allowed = full.theme(id)!!.words(WordLanguage.EN)!!.prefix(counts).all.map { it.letters }.toSet()
        assertTrue(board.placements.all { it.word.letters in allowed })
    }

    @Test fun lockFileParses() {
        val lock = DailyLock.parse(
            """{"entries":[{"from":"2026-12-01","themes":{"ocean":{"en":{"common":1,"crafted":2,"rare":3,"hidden":4,"digest":"x"}}}},
               {"from":"2026-11-01","themes":{}}]}""",
        )
        assertEquals(listOf("2026-11-01", "2026-12-01"), lock.entries.map { it.from })
        assertEquals(0, lock.entry("2026-12-05")!!.themes.getValue("ocean").getValue("en").regional)
        assertEquals("2026-11-01", lock.entry("2026-10-01")!!.from)
    }
}
