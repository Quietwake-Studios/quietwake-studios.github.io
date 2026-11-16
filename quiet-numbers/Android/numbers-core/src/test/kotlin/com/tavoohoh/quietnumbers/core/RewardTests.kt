package com.tavoohoh.quietnumbers.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** iOS `lockedRewardsNeedTheirAchievement`, `everyThemeAchievementStillHasItsTheme`, `AchievementRewardTests` and `TutorialScriptTests`. */
class RewardTests {
    @Test fun lockedRewardsNeedTheirAchievement() {
        val progress = PlayerProgress(unlocked = setOf(Achievement.NO_HELP))
        assertTrue(ThemeChoice.TEAL_CORAL.isUnlocked(progress))
        assertTrue(ThemeChoice.SAGE_SAND.isUnlocked(progress))
        assertFalse(ThemeChoice.SAGE_CORAL.isUnlocked(progress))
        assertTrue(ThemeChoice.HIGH_CONTRAST.isUnlocked(PlayerProgress()))
    }

    @Test fun everyThemeAchievementStillHasItsTheme() {
        assertEquals(setOf(Achievement.NO_HELP, Achievement.CONSISTENCY, Achievement.NIGHT_OWL), ThemeChoice.entries.mapNotNull { it.achievement }.toSet())
    }

    /** Same unlocks as iOS's reward table (quiet-numbers/CLAUDE.md). */
    @Test fun everyAchievementUnlocksWhatIOSDoes() {
        val unlocks = mapOf(
            Achievement.CLEAN_SLATE to TileStyle.CIRCLE, Achievement.STAGE_FIVE to TileStyle.OUTLINE, Achievement.HABIT to NumberFont.SERIF,
            Achievement.SWEEPER to ClearEffect.WAVE, Achievement.MONOTONE to SoundPack.MARIMBA, Achievement.CENTENNIAL to AppIconChoice.GOLD,
        )
        for ((achievement, reward) in unlocks) assertEquals(achievement, reward.achievement)
        assertEquals(Achievement.entries.toSet() - Achievement.FIRST_STEP, Achievement.entries.filter { it.hasReward }.toSet())
    }

    @Test fun choicesNotUnlockedFallBackToTheDefaults() {
        val chosen = Looks(ThemeChoice.SAGE_CORAL, TileStyle.CIRCLE, NumberFont.SERIF, ClearEffect.WAVE, SoundPack.MARIMBA, AppIconChoice.GOLD)
        assertEquals(Looks(), chosen.effective(PlayerProgress()))
        val all = PlayerProgress(unlocked = Achievement.entries.toSet())
        assertEquals(chosen, chosen.effective(all))
        assertEquals(ThemeChoice.HIGH_CONTRAST, Looks(theme = ThemeChoice.HIGH_CONTRAST).effective(PlayerProgress()).theme)
    }

    @Test fun countedAchievementsShowTheirProgress() {
        val progress = PlayerProgress(bestStreak = 9, rowsCleared = 40)
        assertEquals(AchievementStep(AchievementStep.Kind.STREAK, 9, 7), AchievementStep.of(Achievement.CONSISTENCY, progress))
        assertEquals(0.3, AchievementStep.of(Achievement.HABIT, progress)!!.fraction, 1e-9)
        assertEquals(0.4, AchievementStep.of(Achievement.SWEEPER, progress)!!.fraction, 1e-9)
        assertNull(AchievementStep.of(Achievement.NIGHT_OWL, progress))
    }

    /** The tutorial board plays exactly as the steps say. */
    @Test fun everyTutorialStepIsLegalInOrder() {
        val session = TutorialScript.session()
        for (step in TutorialScript.steps) {
            assertFalse(TutorialScript.isDone(step, session), "step ${step.id} done too early")
            when (val move = step.move) {
                is TutorialScript.Move.Pair -> {
                    val outcome = session.matchCells(move.a, move.b)
                    assertNotNull(outcome, "step ${step.id} isn't a valid pair")
                    when (step.id) {
                        1, 2, 3 -> assertEquals(0, outcome!!.pair.gap)
                        4 -> assertTrue(outcome!!.pair.gap >= 4, "the long shot needs 4+ cleared cells between")
                        5 -> assertEquals(Route.READING_ORDER, outcome!!.pair.route)
                        6 -> assertEquals(1, outcome!!.clearedRows.size)
                    }
                }
                TutorialScript.Move.AddNumbers -> {
                    assertFalse(session.hasValidPair, "the + step should come with no pairs left")
                    assertTrue(session.addNumbers())
                }
            }
            assertTrue(TutorialScript.isDone(step, session), "step ${step.id} not detected")
        }
    }
}
