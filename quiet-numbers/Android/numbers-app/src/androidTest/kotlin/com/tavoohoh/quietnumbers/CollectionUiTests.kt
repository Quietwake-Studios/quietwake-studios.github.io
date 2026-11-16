package com.tavoohoh.quietnumbers

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 4 on screen (iOS `CollectionUITests` and the tutorial): Collection's two tabs, locked and
 * chosen rewards, "Cheaper hints", Settings in the family's order, Reset Progress and the tutorial.
 * Runs in English.
 */
@RunWith(AndroidJUnit4::class)
class CollectionUiTests {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun launch(vararg extras: Pair<String, Any>) {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).putExtra("uiTesting", true)
        for ((k, v) in extras) when (v) {
            is Boolean -> intent.putExtra(k, v)
            else -> intent.putExtra(k, v.toString())
        }
        scenario = ActivityScenario.launch(intent)
        compose.waitForIdle()
    }

    @After fun close() {
        scenario?.close()
    }

    private fun tag(tag: String) = compose.onNodeWithTag(tag)
    /** Scrolls to it first when it's in a scrolling list. */
    private fun click(tag: String) {
        val node = tag(tag)
        try { node.performScrollTo() } catch (_: AssertionError) {}
        node.performClick()
        compose.waitForIdle()
    }
    private fun achievementsTab() {
        compose.onNodeWithText("Achievements").performScrollTo().performClick()
        compose.waitForIdle()
    }
    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun header() = tag("achievementsHeader").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.joinToString(" ") { it.text }

    @Test fun collectionHasRewardsAndAchievements() {
        launch()
        click("collection")
        tag("reward-tealCoral").assertIsSelected()
        // Locked: says how it's earned and can't be picked.
        compose.onNodeWithText("Clear a board without using hints.").assertExists()
        click("reward-sageSand")
        tag("reward-sageSand").assertIsNotSelected()
        assertTrue(!exists("cheapHints"))
        achievementsTab()
        assertEquals("0 earned of 11", header())
        // "In the Red" keeps its secret until earned.
        compose.onNodeWithText("Hidden achievement").performScrollTo().assertIsDisplayed()
    }

    @Test fun unlockedRewardsCanBeChosen() {
        launch("unlockAll" to true, "open" to "collection")
        click("reward-sageSand")
        tag("reward-sageSand").assertIsSelected()
        tag("reward-tealCoral").assertIsNotSelected()
        click("reward-circle")
        tag("reward-circle").assertIsSelected()
        click("reward-gold")
        tag("reward-gold").assertIsSelected()
        achievementsTab()
        assertEquals("11 earned of 11", header())
        compose.onNodeWithText("In the Red").performScrollTo().assertIsDisplayed()
    }

    /** "In the Red" makes classic hints cost 3, and the switch in Collection turns it off (from the next game). */
    @Test fun cheaperHintsSwitch() {
        launch("unlockAll" to true)
        click("newGame")
        compose.onNodeWithText("-3").assertExists()
        click("back")
        click("collection")
        click("cheapHints")
        click("back")
        click("newGame")
        compose.onNodeWithText("-10").assertExists()
    }

    @Test fun settingsInTheFamilyOrder() {
        launch("open" to "settings")
        for (t in listOf("sound", "haptics", "largerNumbers", "matchSystem", "reminderToggle", "howToPlay", "resetProgress", "rateApp")) {
            tag(t).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Quiet Numbers doesn't collect any data.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Rate Quiet Numbers").assertExists()
        // Match system off: Light · Dark shows up.
        assertTrue(!exists("appearanceMode"))
        click("matchSystem")
        tag("appearanceMode").assertIsDisplayed()
    }

    @Test fun resetProgressErasesAchievements() {
        launch("unlockAll" to true, "open" to "settings")
        click("resetProgress")
        compose.onNodeWithText("All your game progress is erased.").assertIsDisplayed()
        click("confirmReset")
        click("back")
        click("collection")
        achievementsTab()
        assertEquals("0 earned of 11", header())
    }

    /** The first New Game opens the tutorial: only the ringed pair counts; at the end a game starts. */
    @Test fun tutorialBeforeTheFirstGame() {
        launch("tutorial" to true)
        click("newGame")
        tag("skipTutorial").assertIsDisplayed()
        // Off the guided pair: nothing happens.
        tag("cell-0").performClick(); tag("cell-1").performClick()
        compose.waitForIdle()
        assertEquals("0", score())
        for ((a, b) in listOf(10 to 11, 12 to 21, 13 to 3, 9 to 14, 17 to 18, 15 to 16)) {
            // The last pair leaves no pairs: + pulses endlessly from then on, so the clock goes by hand.
            if (a == 15) compose.mainClock.autoAdvance = false
            tag("cell-$a").performClick(); tag("cell-$b").performClick()
            if (a == 15) repeat(40) { val t = stepText(); if (t.isEmpty() || t.startsWith(stepTitleBefore)) compose.mainClock.advanceTimeBy(100) }
            compose.waitUntil(5_000) { stepText().isNotEmpty() && !stepText().startsWith(stepTitleBefore) }
            stepTitleBefore = stepText()
        }
        tag("plus").performClick()
        repeat(40) { if (!exists("finishTutorial")) compose.mainClock.advanceTimeBy(100) }
        compose.mainClock.autoAdvance = true
        click("finishTutorial")
        compose.waitUntil(3_000) { exists("menu") }
        assertTrue(subtitle().startsWith("Stage 1"))
    }

    @Test fun howToPlayFromSettingsGoesBack() {
        launch("open" to "settings")
        click("howToPlay")
        click("skipTutorial")
        compose.waitUntil(3_000) { exists("resetProgress") }
    }

    private var stepTitleBefore = "Equal numbers"
    /** The step card's text once the change of step has played out ("" while two cards cross-fade). */
    private fun stepText(): String {
        val nodes = compose.onAllNodes(hasTestTag("tutorialStep")).fetchSemanticsNodes()
        return nodes.singleOrNull()?.config?.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
    }
    private fun score() = tag("score").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)!!
    private fun subtitle() = tag("subtitle").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.first().text
}
