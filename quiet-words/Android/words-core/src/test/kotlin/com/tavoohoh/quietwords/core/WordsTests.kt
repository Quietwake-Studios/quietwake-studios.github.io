package com.tavoohoh.quietwords.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WordsTests {
    @Test fun normalizesForTheBoard() {
        assertEquals("ESTRELLADEMAR", WordText.normalize("Estrella de mar", WordLanguage.ES))
        assertEquals("PINGUINO", WordText.normalize("Pingüino", WordLanguage.ES))
        assertEquals("ÑANDU", WordText.normalize("Ñandú", WordLanguage.ES))
        assertEquals("CAFEAULAIT", WordText.normalize("Café au lait", WordLanguage.EN))
        assertEquals("PINA", WordText.normalize("Piña", WordLanguage.EN))
        assertEquals("PUERH", WordText.normalize("Pu-erh", WordLanguage.EN))
    }

    @Test fun libraryLoadsEveryTheme() {
        val library = ThemeLibrary.shared
        assertEquals(
            listOf("biology", "botany", "coffee_tea", "food", "geography", "modern_age", "mountain", "music", "ocean", "renaissance_industry", "space"),
            library.themes.map { it.id },
        )
        for (theme in library.themes) {
            assertTrue(theme.isBilingual, theme.id)
            for (language in WordLanguage.entries) {
                val words = theme.words(language)!!
                assertTrue(words.all.size >= 200, "${theme.id} $language")
                assertTrue(words.hidden.size >= 15, "${theme.id} $language")
                assertTrue(words.all.all { WordText.isBoardText(it.letters, language) && it.count in 3..12 })
                assertTrue(words.regional.size <= 20)
            }
        }
        assertTrue("FUCK" in library.blocklist.getValue(WordLanguage.EN))
        assertTrue("MIERDA" in library.blocklist.getValue(WordLanguage.ES))
    }

    @Test fun listKeepsTheSpelling() {
        val ocean = ThemeLibrary.shared.theme("ocean")!!.words(WordLanguage.ES)!!
        val word = ocean.all.first { it.text == "León marino" }
        assertEquals("LEONMARINO", word.letters)
        assertEquals(WordGroup.COMMON, word.group)
    }
}
