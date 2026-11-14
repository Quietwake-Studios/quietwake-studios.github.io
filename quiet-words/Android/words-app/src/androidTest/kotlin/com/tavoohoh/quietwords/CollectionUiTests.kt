package com.tavoohoh.quietwords

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Collection (like iOS's `testAchievementsTab` and `testCheapHintsToggle`), the album and a reward. Runs in English. */
@RunWith(AndroidJUnit4::class)
class CollectionUiTests {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun launch(vararg extras: Pair<String, Any>) {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .putExtra("uiTesting", true).putExtra("tutorialDone", true).putExtra("words", "en")
        for ((key, value) in extras) if (value is Boolean) intent.putExtra(key, value) else intent.putExtra(key, value.toString())
        scenario = ActivityScenario.launch(intent)
        compose.waitForIdle()
    }

    @After fun close() {
        scenario?.close()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    /** All the texts of a merged row (what TalkBack reads). */
    private fun label(tag: String): String =
        compose.onNodeWithTag(tag).performScrollTo().fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }

    @Test fun achievementsTab() {
        launch("open" to "collection")
        compose.onNodeWithText("Achievements").performClick()
        val first = label("achievement-firstPuzzle")
        assertTrue(first, first.contains("Finish your first puzzle."))
        val words = label("achievement-words100")
        assertTrue(words, words.contains("0 of 100 words"))
        val red = label("achievement-inTheRed")
        assertTrue(red, red.contains("Hidden achievement"))
        assertFalse(red, red.contains("below zero"))
        compose.onNodeWithText("0 earned").assertExists()
    }

    /** "Cheaper hints" shows in Rewards only once "In the Red" is earned, and starts on. */
    @Test fun cheapHintsToggle() {
        launch("open" to "collection")
        compose.onNodeWithText("MARKER").assertExists()
        assertFalse(exists("cheapHints"))
        scenario?.close()
        launch("open" to "collection", "unlockAll" to true)
        compose.onNodeWithTag("cheapHints").assertIsOn().performClick()
        compose.onNodeWithTag("cheapHints").assertIsOff()
        compose.onNodeWithText("Achievements").performClick()
        val red = label("achievement-inTheRed")
        assertTrue(red, red.contains("In the Red"))
        compose.onNodeWithText("11 earned").assertExists()
    }

    /** Locked rewards can't be picked; once earned, they can. */
    @Test fun pickingAMarker() {
        launch("open" to "collection")
        compose.onNodeWithTag("reward-pencil").performScrollTo().performClick()
        compose.onNodeWithTag("reward-highlighter").assertIsSelected()
        scenario?.close()
        launch("open" to "collection", "unlockAll" to true)
        compose.onNodeWithTag("reward-pencil").performScrollTo().performClick()
        compose.onNodeWithTag("reward-pencil").assertIsSelected()
    }

    /** The album opens from Home's stat; a theme shows its words to the badge. */
    @Test fun albumOpensATheme() {
        launch()
        compose.onNodeWithTag("albumStat").performClick()
        compose.onNodeWithTag("album-ocean").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Missing words show only their length.").performScrollTo().assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasTestTag("album-ocean")).fetchSemanticsNodes().isEmpty())
    }
}
