package com.tavoohoh.quietwords.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgressTests {
    private fun summary(level: Level = Level.GENTLE, seconds: Int = 200, guessed: Boolean = false, hints: Int = 0) =
        GameSummary(500, seconds, level, 10, 400, 0, guessed, hints, WordLanguage.ES, "ocean", emptyList())

    @Test fun achievementsUnlockOnce() {
        var p = PlayerProgress()
        fun rec(e: ProgressEvent): List<Achievement> = p.record(e).also { p = it.first }.second
        assertEquals(listOf(Achievement.FIRST_PUZZLE, Achievement.IN_PLAIN_SIGHT), rec(ProgressEvent.Won(summary(guessed = true), true)))
        assertTrue(rec(ProgressEvent.Won(summary(), true)).isEmpty())
        assertEquals(500, p.bestScore(Level.GENTLE, WordLanguage.ES))
        assertEquals(listOf(Achievement.STEADY_HAND), rec(ProgressEvent.Won(summary(Level.HARD, 599), false)))
        assertEquals(emptyList(), rec(ProgressEvent.WordsFound(99)))
        assertEquals(listOf(Achievement.WORDS_100), rec(ProgressEvent.WordsFound(1)))
        assertEquals(listOf(Achievement.HABIT), rec(ProgressEvent.DailyCompleted(7)))
        assertEquals(listOf(Achievement.BILINGUAL), rec(ProgressEvent.BothLanguages))
        assertEquals(0.1, p.progress(Achievement.WORDS_1000))
    }

    @Test fun mergeKeepsTheMost() {
        var a = PlayerProgress().record(ProgressEvent.WordsFound(50)).first
        val b = PlayerProgress().record(ProgressEvent.WordsFound(120)).first
        a = a.record(ProgressEvent.Won(summary(), true)).first
        val merged = a.merged(b)
        assertTrue(merged.wordsFound == 120 && Achievement.FIRST_PUZZLE in merged.unlocked && Achievement.WORDS_100 in merged.unlocked)
    }

    @Test fun albumCountsByBoardFormPerLanguage() {
        val ocean = ThemeLibrary.shared.theme("ocean")!!
        var album = Album()
        album.add(listOf("CORAL", "OLA", "NOTAWORD"), "ocean", WordLanguage.ES).let { album = it.first; assertEquals(3, it.second) }
        album.add(listOf("CORAL"), "ocean", WordLanguage.ES).let { album = it.first; assertEquals(0, it.second) }
        assertEquals(2, album.count(ocean, WordLanguage.ES).found)
        assertEquals(0, album.count(ocean, WordLanguage.EN).found)
        album = album.add(ocean.words(WordLanguage.EN)!!.all.map { it.letters }, "ocean", WordLanguage.EN).first
        assertTrue(album.isComplete(ocean, WordLanguage.EN) && !album.isComplete(ocean, WordLanguage.ES))
        assertEquals(1, album.total(WordLanguage.EN, ThemeLibrary.shared).badges)
        val other = Album().add(listOf("MAREA"), "ocean", WordLanguage.ES).first
        assertEquals(2, album.merged(other).count(ocean, WordLanguage.ES).found)
    }

    @Test fun inTheRedIsHidden() {
        var p = PlayerProgress()
        fun rec(e: ProgressEvent): List<Achievement> = p.record(e).also { p = it.first }.second
        assertTrue(rec(ProgressEvent.ScoreChanged(0)).isEmpty())
        assertEquals(listOf(Achievement.IN_THE_RED), rec(ProgressEvent.ScoreChanged(-30)))
        assertTrue(rec(ProgressEvent.ScoreChanged(-60)).isEmpty())
        assertTrue(Achievement.IN_THE_RED.isHidden && Achievement.BILINGUAL.isHidden && !Achievement.HABIT.isHidden)
    }

    @Test fun mostAdvancedTheme() {
        var album = Album()
        assertEquals(0.0, album.bestThemeFraction(ThemeLibrary.shared))
        val ocean = ThemeLibrary.shared.theme("ocean")!!
        val all = ocean.words(WordLanguage.ES)!!.all.map { it.letters }
        album = album.add(all.take(all.size / 2), "ocean", WordLanguage.ES).first
        album = album.add(listOf("STAR"), "space", WordLanguage.EN).first
        val c = album.count(ocean, WordLanguage.ES)
        assertEquals(c.found.toDouble() / c.total, album.bestThemeFraction(ThemeLibrary.shared))
    }
}
