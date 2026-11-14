package com.tavoohoh.quietnumbers

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Playing with the finger (like iOS's UI tests), on exact boards (`boardValues`). Runs in English. */
@RunWith(AndroidJUnit4::class)
class GameUiTests {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun launch(vararg extras: Pair<String, Any>) {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).putExtra("uiTesting", true)
        for ((k, v) in extras) when (v) {
            is Boolean -> intent.putExtra(k, v)
            is Int -> intent.putExtra(k, v)
            else -> intent.putExtra(k, v.toString())
        }
        scenario = ActivityScenario.launch(intent)
        compose.waitForIdle()
    }

    private fun game(values: String, vararg extras: Pair<String, Any>) = launch("startGame" to true, "seedKey" to "ui", "boardValues" to values, *extras)

    @After fun close() {
        scenario?.close()
    }

    private fun score(): String =
        compose.onNodeWithTag("score").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)!!
    private fun cleared(index: Int): Boolean =
        compose.onNodeWithTag("cell-$index").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription) == "Cleared"
    private fun tap(index: Int) = compose.onNodeWithTag("cell-$index").performClick().also { compose.waitForIdle() }

    @Test fun tapTwoNumbersToMatch() {
        game("4,4,1,2,1,2,1,2,1,2,1,2,1,2,1,2,1,2")
        tap(0); tap(1)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("1", score())
        assertTrue(cleared(0) && cleared(1))
    }

    @Test fun dragFromOneNumberToTheOther() {
        game("3,1,2,1,2,1,2,1,2,7,2,1,2,1,2,1,2,1")
        val board = compose.onNodeWithTag("board").fetchSemanticsNode().boundsInRoot.topLeft
        val from = compose.onNodeWithTag("cell-0").fetchSemanticsNode().boundsInRoot.center - board
        val to = compose.onNodeWithTag("cell-9").fetchSemanticsNode().boundsInRoot.center - board
        compose.onNodeWithTag("board").performTouchInput {
            down(from)
            for (i in 1..10) moveTo(from + (to - from) * (i / 10f))
            up()
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("1", score())
    }

    @Test fun missHintUndoAndPlus() {
        game("4,4,2,3,1,2,1,2,1,2,1,2,1,2,1,2,1,2")
        // Not a pair: nothing changes.
        tap(2); tap(3)
        assertEquals("0", score())
        // A hint costs 10, even below zero.
        compose.onNodeWithTag("hint").performClick()
        compose.waitForIdle()
        assertEquals("-10", score())
        tap(0); tap(1)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("-9", score())
        // Undo takes the match back, not the hint.
        compose.onNodeWithTag("undo").performClick()
        compose.waitForIdle()
        assertEquals("-10", score())
        assertTrue(!cleared(0))
        // + copies every number to the end.
        compose.onNodeWithTag("plus").performClick()
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasTestTag("cell-35")).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("4").assertExists()
    }

    /** A whole game: the last pair, no + left, the summary and back Home. */
    @Test fun aGameEndsWithTheSummary() {
        game("1,2,1,2,1,1,3,4,3", "plusRemaining" to 0)
        tap(4); tap(5)
        compose.waitUntil(8_000) { compose.onAllNodes(hasTestTag("summary")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("No moves left").assertIsDisplayed()
        compose.onNodeWithTag("summaryHome").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("newGame").assertIsDisplayed()
    }

    /** Clearing the board in classic goes on to stage 2 with a new board. */
    @Test fun clearingTheBoardGoesToTheNextStage() {
        game("2,8")
        tap(0); tap(1)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("161", score())
        assertTrue(compose.onAllNodesWithText("Stage 2", substring = true).fetchSemanticsNodes().isNotEmpty())
        // The new board comes in after the match plays out.
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("cell-41")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun dailyShowsTheGoal() {
        launch()
        compose.onNodeWithTag("daily").performClick()
        compose.waitForIdle()
        val subtitle = compose.onNodeWithTag("subtitle").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.first().text
        assertTrue(subtitle, subtitle.startsWith("Stage 1 · Goal "))
    }
}
