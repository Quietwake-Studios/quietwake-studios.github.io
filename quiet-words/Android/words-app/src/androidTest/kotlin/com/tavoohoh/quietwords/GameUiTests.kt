package com.tavoohoh.quietwords

import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tavoohoh.quietwords.game.AnswerMark
import com.tavoohoh.quietwords.game.HiddenAnswer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Playing with the finger, like iOS's UI tests. `uiTesting` marks the first and last cell of
 * the next word ("start"/"end") and puts the hidden word on its row. Runs in English.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class GameUiTests {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun launch(vararg extras: Pair<String, Any>) {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .putExtra("uiTesting", true)
            .putExtra("tutorialDone", true)
            .putExtra("words", "en")
        for ((key, value) in extras) {
            when (value) {
                is Boolean -> intent.putExtra(key, value)
                is Int -> intent.putExtra(key, value)
                else -> intent.putExtra(key, value.toString())
            }
        }
        scenario = ActivityScenario.launch(intent)
        compose.waitForIdle()
    }

    private fun startGame(level: String = "gentle", vararg extras: Pair<String, Any>) =
        launch("startGame" to true, "seedKey" to "android-ui", "level" to level, "theme" to "ocean", "clock" to 0, *extras)

    @After fun close() {
        scenario?.close()
    }

    private val start = SemanticsMatcher.expectValue(AnswerMark, "start")
    private val end = SemanticsMatcher.expectValue(AnswerMark, "end")

    private fun center(matcher: SemanticsMatcher): Offset = compose.onNode(matcher).fetchSemanticsNode().boundsInRoot.center
    private fun hasNextWord() = compose.onAllNodes(start).fetchSemanticsNodes().isNotEmpty()
    private fun foundLabel(): String =
        compose.onNodeWithTag("foundCount").fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.ContentDescription)!!.first()
    private fun score(): String =
        compose.onNodeWithTag("score").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)!!.first()

    /** Drags from the first letter to the last, in small steps (the stroke snaps along the way). */
    private fun dragNextWord(reversed: Boolean = false) {
        val board = compose.onNodeWithTag("board").fetchSemanticsNode().boundsInRoot.topLeft
        var from = center(start) - board
        var to = center(end) - board
        if (reversed) from = to.also { to = from }
        compose.onNodeWithTag("board").performTouchInput {
            down(from)
            for (i in 1..12) moveTo(from + (to - from) * (i / 12f))
            up()
        }
        compose.waitForIdle()
    }

    @Test fun winByDragging() {
        startGame()
        var words = 0
        while (hasNextWord()) {
            // Every other word from the last letter to the first: any sense counts.
            dragNextWord(reversed = words % 2 == 1)
            words++
            assert(words < 40) { "the board never ends" }
        }
        assertEquals("$words of $words found", foundLabel())
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("summaryScore")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Puzzle complete!").assertIsDisplayed()
        compose.onNodeWithTag("summaryNewGame").assertIsDisplayed()
        // The hidden word was revealed at the end.
        val hidden = compose.onNodeWithTag("hiddenWord").fetchSemanticsNode().config[HiddenAnswer]
        assert(compose.onAllNodesWithContentDescription("Hidden word: $hidden").fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun tapFirstThenLastLetter() {
        startGame("medium")
        // Touch: tap the first letter, then the last.
        compose.onNode(start).performClick()
        compose.onNode(end).performClick()
        compose.waitForIdle()
        assert(foundLabel().startsWith("1 of")) { foundLabel() }
        // TalkBack: double-tap is the same tap flow (the semantics click action).
        compose.onNode(start).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNode(end).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assert(foundLabel().startsWith("2 of")) { foundLabel() }
        assertNotEquals("Score 0", score())
    }

    @Test fun wrongGuessCostsNothingThenRightGuess() {
        startGame()
        val hidden = compose.onNodeWithTag("hiddenWord").fetchSemanticsNode().config[HiddenAnswer]
        compose.onNodeWithTag("guess").performClick()
        compose.waitForIdle()
        val wrong = if (hidden.first() == 'Z') "Y".repeat(hidden.length) else "Z".repeat(hidden.length)
        compose.onNodeWithTag("guessField").performTextInput(wrong)
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("guessField")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("2 tries left · a miss costs nothing").assertIsDisplayed()
        assertEquals("Score 0", score())
        // The field empties after the shake; then the right word.
        // Effects run on the test clock: advance it past the 450 ms shake.
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
        compose.onNodeWithTag("guessField").performTextInput(hidden.lowercase())
        compose.waitForIdle()
        assertEquals("Score 100", score())
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("guessField")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Guessed · +100").assertIsDisplayed()
    }

    @Test fun tutorialShowsBothLanguagesFirst() {
        launch("open" to "tutorial")
        compose.onNodeWithText("Which words do you want to find?").assertIsDisplayed()
        compose.onNodeWithTag("language-en").assertIsDisplayed()
        compose.onNodeWithTag("language-es").assertIsDisplayed()
        compose.onNodeWithTag("language-es").performClick()
        compose.onNodeWithTag("tutorialContinue").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Find CORAL").assertIsDisplayed()
    }
}
