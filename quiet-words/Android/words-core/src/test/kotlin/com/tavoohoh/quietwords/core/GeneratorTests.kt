package com.tavoohoh.quietwords.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GeneratorTests {
    private val library = ThemeLibrary.shared

    private fun board(i: Int, level: Level, language: WordLanguage): Board? {
        val theme = library.themes[i % library.themes.size]
        val words = theme.words(language)!!
        return Generator.board("test-$i", words.all, words.hidden, language, level, blocklist = library.blocklist.getValue(language))
    }

    @Test fun sameKeySameBoard() {
        for (level in Level.entries) {
            assertEquals(board(7, level, WordLanguage.ES), board(7, level, WordLanguage.ES))
            assertNotEquals(board(7, level, WordLanguage.ES), board(8, level, WordLanguage.ES))
        }
    }

    @Test fun boardsFollowTheLevel() {
        for (level in Level.entries) for (language in WordLanguage.entries) for (i in 0 until 20) {
            val b = assertNotNull(board(i, level, language))
            assertEquals(emptyList(), problems(b, level, library.blocklist.getValue(language)))
            val range = when (level) { Level.GENTLE -> 8..20; Level.MEDIUM -> 13..24; Level.HARD -> 14..28 }
            assertTrue(b.placements.size in range, "$level ${b.placements.size} words")
            if (level == Level.HARD) assertTrue(b.placements.count { it.word.group != WordGroup.COMMON } >= 3)
            if (level == Level.GENTLE) assertTrue(b.placements.all { it.word.group == WordGroup.COMMON })
        }
    }

    /** Phase 1 acceptance: 1,000 free boards per level (both languages), all valid. */
    @Test fun aThousandBoardsPerLevel() {
        val failures = mutableListOf<String>()
        for (level in Level.entries) for (i in 0 until 1_000) {
            val language = WordLanguage.entries[i % 2]
            val b = board(i, level, language)
            if (b == null) { failures += "$i $level $language: no board"; continue }
            val issues = problems(b, level, library.blocklist.getValue(language))
            if (issues.isNotEmpty()) failures += "$i $level $language: $issues"
        }
        assertTrue(failures.isEmpty(), failures.take(10).toString())
    }

    /** Swift's goal is under 30 ms per board on an old iPhone; on the JVM this fails above 20 ms on average (after warm-up). */
    @Test fun generationTime() {
        repeat(50) { board(it, Level.HARD, WordLanguage.ES) }
        val report = StringBuilder()
        for (level in Level.entries) {
            val start = System.nanoTime()
            for (i in 0 until 100) board(i, level, WordLanguage.ES)
            val ms = (System.nanoTime() - start) / 1e6 / 100
            report.append("$level ${"%.2f".format(ms)} ms/board  ")
            assertTrue(ms < 20.0, "$level: $ms ms per board")
        }
        println("Generation time (JVM): $report")
    }
}
