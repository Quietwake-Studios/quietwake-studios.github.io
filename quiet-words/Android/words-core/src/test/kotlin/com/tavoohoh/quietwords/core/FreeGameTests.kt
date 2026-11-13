package com.tavoohoh.quietwords.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FreeGameTests {
    private val library = ThemeLibrary.shared

    @Test fun sameKeySameBoardAsTheGenerator() {
        val words = library.theme("ocean")!!.words(WordLanguage.ES)!!
        val direct = Generator.board("free-42", words.all, words.hidden, WordLanguage.ES, Level.MEDIUM, blocklist = library.blocklist.getValue(WordLanguage.ES))
        val board = FreeGame.board("ocean", Level.MEDIUM, WordLanguage.ES, "free-42")
        assertEquals(direct, board)
        assertTrue(problems(board!!, Level.MEDIUM, library.blocklist.getValue(WordLanguage.ES)).isEmpty())
    }

    @Test fun sessionIsAFreshFreeGame() {
        val session = assertNotNull(FreeGame.session("space", Level.HARD, WordLanguage.EN, "free-7", scoring = Scoring.CHEAP_HINTS))
        assertEquals(GameSession.Mode.Free, session.mode)
        assertEquals(Level.HARD, session.level)
        assertEquals(10, session.scoring.hint)
        assertEquals(0, session.score)
        assertTrue(session.found.isEmpty())
    }

    @Test fun unknownThemeHasNoBoard() {
        assertNull(FreeGame.session("nope", Level.GENTLE, WordLanguage.EN, "k"))
    }

    @Test fun surprisePicksAThemeOfTheLanguage() {
        val first = FreeGame.surpriseThemeID(WordLanguage.EN) { 0 }
        assertEquals(library.themes(WordLanguage.EN).first().id, first)
        val clamped = FreeGame.surpriseThemeID(WordLanguage.ES) { 999 }
        assertEquals(library.themes(WordLanguage.ES).last().id, clamped)
    }
}
