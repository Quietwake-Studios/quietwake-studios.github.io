package com.tavoohoh.quietwords.core

import com.tavoohoh.quiet.core.Palette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The rewards table of the brief (`quiet-words/CLAUDE.md` › Logros y recompensas). */
class RewardTests {
    @Test fun eachRewardHasItsAchievement() {
        assertEquals(Achievement.FIRST_PUZZLE, Marker.PENCIL.achievement)
        assertEquals(Achievement.WORDS_100, PaletteReward(Palette.SAGE_SAND).achievement)
        assertEquals(Achievement.WORDS_1000, AppIconChoice.GOLD.achievement)
        assertEquals(Achievement.WORDS_10000, BoardFont.MONO.achievement)
        assertEquals(Achievement.THEME_COMPLETE, BoardFont.SERIF.achievement)
        assertEquals(Achievement.IN_PLAIN_SIGHT, Marker.INK.achievement)
        assertEquals(Achievement.HABIT, PaletteReward(Palette.INDIGO_PEACH).achievement)
        assertEquals(Achievement.CONSISTENCY, PaletteReward(Palette.SAGE_CORAL).achievement)
        assertEquals(Achievement.STEADY_HAND, Marker.UNDERLINE.achievement)
    }

    @Test fun defaultsAndHighContrastAreAlwaysThere() {
        val none = PlayerProgress()
        for (r in listOf(Marker.HIGHLIGHTER, BoardFont.ROUNDED, AppIconChoice.STANDARD, PaletteReward(Palette.TEAL_CORAL), PaletteReward(Palette.HIGH_CONTRAST))) {
            assertNull(r.achievement)
            assertTrue(r.isUnlocked(none))
        }
        assertFalse(Marker.PENCIL.isUnlocked(none))
        assertTrue(Marker.PENCIL.isUnlocked(PlayerProgress.withUnlocked(setOf(Achievement.FIRST_PUZZLE))))
    }

    /** A choice that isn't unlocked (after a reset) falls back to the default. */
    @Test fun lockedChoicesFallBack() {
        val chosen = Looks(Palette.SAGE_CORAL, Marker.INK, BoardFont.MONO, AppIconChoice.GOLD)
        assertEquals(Looks(), chosen.effective(PlayerProgress()))
        val all = PlayerProgress.withUnlocked(Achievement.entries.toSet())
        assertEquals(chosen, chosen.effective(all))
        assertEquals(Palette.HIGH_CONTRAST, Looks(palette = Palette.HIGH_CONTRAST).effective(PlayerProgress()).palette)
    }

    @Test fun stepsCountWhatTheyShould() {
        val progress = PlayerProgress(wordsFound = 42, bestStreak = 3)
        val album = Album()
        val library = ThemeLibrary.shared
        assertEquals(AchievementStep.Count(42, 100), AchievementStep.of(Achievement.WORDS_100, progress, album, library))
        assertEquals(AchievementStep.Streak(3, 30), AchievementStep.of(Achievement.CONSISTENCY, progress, album, library))
        assertEquals(1.0, AchievementStep.Count(2000, 1000).fraction)
        assertNull(AchievementStep.of(Achievement.BILINGUAL, progress, album, library))
        val words = library.theme("ocean")!!.words(WordLanguage.EN)!!.all.map { it.letters }
        val half = album.add(words.take(words.toSet().size / 2), "ocean", WordLanguage.EN).first
        val step = AchievementStep.of(Achievement.THEME_COMPLETE, progress, half, library) as AchievementStep.Percent
        assertTrue(step.value in 0.4..0.6, "${step.value}")
    }
}
