package com.tavoohoh.quietwords

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tavoohoh.quietwords.core.DailyChallenge
import com.tavoohoh.quietwords.core.ThemeLibrary
import com.tavoohoh.quietwords.core.WordLanguage
import com.tavoohoh.quietwords.game.AnswerMark
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The daily rule on screen (like iOS's UI tests): leaving a daily halfway asks Continue /
 * Start over, the calendar opens a past day, and the streak counts a day once across languages.
 * A fixed date (mid-month, so yesterday is on the same page). Runs in English.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DailyUiTests {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    private val today = "2026-11-13"

    private fun launch(vararg extras: Pair<String, String>) {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .putExtra("uiTesting", true)
            .putExtra("tutorialDone", true)
            .putExtra("words", "en")
            .putExtra("date", "${today}T15:00:00Z")
        for ((key, value) in extras) intent.putExtra(key, value)
        scenario = ActivityScenario.launch(intent)
        compose.waitForIdle()
    }

    @After fun close() {
        scenario?.close()
    }

    private val start = SemanticsMatcher.expectValue(AnswerMark, "start")
    private val end = SemanticsMatcher.expectValue(AnswerMark, "end")

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { exists(tag) }
    private fun description(tag: String): String =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)!!.first()
    private fun text(tag: String): String =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.first().text
    private fun foundLabel() = description("foundCount")

    private fun findNextWord() {
        compose.onNode(start).performClick()
        compose.onNode(end).performClick()
        compose.waitForIdle()
    }

    private fun winAndClose() {
        var words = 0
        while (compose.onAllNodes(start).fetchSemanticsNodes().isNotEmpty()) {
            findNextWord()
            assertTrue("the board never ends", ++words < 40)
        }
        waitFor("summaryDone")
        compose.onNodeWithTag("summaryDone").performClick()
        compose.waitForIdle()
        // The first completed daily offers the reminders once.
        if (exists("notNow")) compose.onNodeWithTag("notNow").performClick()
        compose.waitForIdle()
    }

    @Test fun leavingADailyAsksContinueOrStartOver() {
        launch("open" to "daily")
        compose.onNodeWithTag("playToday").performClick()
        waitFor("board")
        findNextWord()
        assertTrue(foundLabel(), foundLabel().startsWith("1 of"))
        compose.onNodeWithTag("back").performClick()
        waitFor("playToday")
        compose.onNodeWithText("Continue").assertIsDisplayed()

        // Continue: the attempt as it was left.
        compose.onNodeWithTag("playToday").performClick()
        compose.onNodeWithText("Continue this challenge?").assertIsDisplayed()
        compose.onNodeWithTag("continueAttempt").performClick()
        waitFor("board")
        assertTrue(foundLabel(), foundLabel().startsWith("1 of"))
        assertTrue(text("context"), text("context").startsWith("Daily ·"))
        compose.onNodeWithTag("back").performClick()
        waitFor("playToday")

        // Start over: a clean board, and it says it's a replay.
        compose.onNodeWithTag("playToday").performClick()
        compose.onNodeWithTag("startOverAttempt").performClick()
        waitFor("board")
        assertTrue(foundLabel(), foundLabel().startsWith("0 of"))
        assertTrue(text("context"), text("context").startsWith("Replay ·"))
    }

    @Test fun calendarOpensAPastDay() {
        launch("open" to "daily")
        val yesterday = "2026-11-12"
        compose.onNodeWithTag("day-$yesterday").performScrollTo().performClick()
        waitFor("board")
        val theme = ThemeLibrary.shared.theme(DailyChallenge.themeID(yesterday)!!)!!.name(WordLanguage.EN)
        assertTrue(text("context"), text("context").startsWith("Daily · $theme ·"))
        compose.onNodeWithTag("back").performClick()
        waitFor("previousMonth")
        // The last 30 days can be played; older ones are shown but don't open.
        compose.onNodeWithTag("previousMonth").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("day-2026-10-14").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("day-2026-10-13").performScrollTo().assertIsNotEnabled()
    }

    /** A finished daily, past or today's, goes back to the calendar (family rule, 2026-11-13). */
    @Test fun finishingAPastDayReturnsToTheCalendar() {
        launch("open" to "daily")
        compose.onNodeWithTag("day-2026-11-12").performScrollTo().performClick()
        waitFor("board")
        winAndClose()
        waitFor("playToday")
        assertTrue(exists("day-2026-11-12"))
        assertEquals("1-day streak", text("dailyStreak"))
    }

    /** Settings › Reset progress asks first, then erases the streak. */
    @Test fun resetProgressErasesTheStreak() {
        launch("open" to "daily")
        compose.onNodeWithTag("playToday").performClick()
        waitFor("board")
        winAndClose()
        waitFor("playToday")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        assertEquals("1-day streak", description("homeStreak"))
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithTag("resetProgress").performScrollTo().performClick()
        compose.onNodeWithText("Reset all progress?").assertIsDisplayed()
        compose.onNodeWithText("All your game progress is erased.").assertIsDisplayed()
        compose.onNodeWithTag("confirmReset").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        assertEquals("0-day streak", description("homeStreak"))
    }

    @Test fun theStreakCountsADayOnceAcrossLanguages() {
        launch()
        assertEquals("0-day streak", description("homeStreak"))
        compose.onNodeWithTag("daily").performClick()
        waitFor("playToday")
        compose.onNodeWithTag("playToday").performClick()
        waitFor("board")
        winAndClose()
        // Back on the Daily screen (family rule), then Home.
        waitFor("playToday")
        assertEquals("1-day streak", text("dailyStreak"))
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        assertEquals("1-day streak", description("homeStreak"))
        assertEquals("English done · Spanish to go", text("dailyStatus"))

        // The same day in Spanish: its own board, the same streak.
        compose.onNodeWithTag("words-es").performClick()
        compose.onNodeWithTag("daily").performClick()
        waitFor("playToday")
        compose.onNodeWithTag("playToday").performClick()
        waitFor("board")
        winAndClose()
        assertEquals("1-day streak", text("dailyStreak"))
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        assertEquals("1-day streak", description("homeStreak"))
        assertEquals("Done in both languages", text("dailyStatus"))
    }
}
