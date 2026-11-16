package com.tavoohoh.quietnumbers

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tavoohoh.quiet.core.DailyKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The daily rule on screen (iOS `DailyRestartUITests` and the family's "back to the calendar"):
 * Continue / Start over, past days from the calendar, Done back to the Daily screen, the streak
 * and Continue on Home. A fixed date; runs in English.
 */
@RunWith(AndroidJUnit4::class)
class DailyUiTests {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val date = "2026-11-14T15:00:00Z"
    private val today = DailyKey.dayKey(Instant.parse(date))
    private val yesterday = DailyKey.dayKey(today, -1)!!

    private fun launch(vararg extras: Pair<String, String>) {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .putExtra("uiTesting", true).putExtra("date", date)
        for ((k, v) in extras) intent.putExtra(k, v)
        scenario = ActivityScenario.launch(intent)
        compose.waitForIdle()
    }

    @After fun close() {
        scenario?.close()
    }

    private fun tag(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun click(tag: String) = tag(tag).performClick().also { compose.waitForIdle() }
    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun text(tag: String) = tag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.joinToString { it.text }
    /** A tile merges its texts: "1, Streak". */
    private fun tileText(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.joinToString { it.text }
    private fun score() = tag("score").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)!!
    private fun backToDaily() {
        click("back")
        compose.waitUntil(3_000) { exists("playToday") }
    }

    /** Leaving an attempt halfway saves its latest state; coming back asks "Continue" or "Start over". */
    @Test fun continueOrStartOver() {
        launch()
        click("daily")
        click("playToday")
        click("hint")
        assertEquals("-10", score())
        backToDaily()
        assertEquals("In progress", text("todayStatus"))
        assertTrue(tag("day-$today").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)!!.contains("In progress"))
        // Continue: the same attempt.
        click("playToday")
        click("continueAttempt")
        assertEquals("-10", score())
        backToDaily()
        // Start over: a new attempt, and that's what's saved from now on.
        click("playToday")
        click("startOverAttempt")
        assertEquals("0", score())
        backToDaily()
        click("playToday")
        click("continueAttempt")
        assertEquals("0", score())
    }

    /** A past day opens from the calendar with its own goal (the date is fixed at Nov 14, 2026, afternoon UTC). */
    @Test fun pastDayFromTheCalendar() {
        launch("open" to "daily")
        tag("day-$yesterday").performScrollTo().performClick()
        compose.waitForIdle()
        // A past day's game says which day it is.
        val subtitle = text("subtitle")
        assertTrue(subtitle, subtitle.startsWith("Nov 13 · Goal "))
    }

    /** Done after today's challenge goes back to the calendar: completed, a 1-day streak, on Home too. */
    @Test fun doneAfterTodayReturnsToTheCalendar() {
        launch("summary" to "daily")
        compose.waitUntil(8_000) { exists("summaryDone") }
        tag("summaryDone").performScrollTo().performClick()
        compose.waitUntil(3_000) { exists("playToday") }
        assertTrue(text("todayStatus").startsWith("Completed · "))
        assertTrue(tileText("dailyStreak").startsWith("1"))
        assertTrue(tag("day-$today").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)!!.contains("Completed"))
        // A first completed daily offers the reminders, once.
        compose.waitUntil(3_000) { exists("notNow") }
        click("notNow")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        compose.waitUntil(3_000) { exists("newGame") }
        assertEquals("1-day streak", tag("homeStreak").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)!!.first())
    }

    /** Done after a past day goes back to the calendar too, with that day filled in. */
    @Test fun doneAfterAPastDayReturnsToTheCalendar() {
        launch("summary" to "daily", "summaryDay" to yesterday)
        compose.waitUntil(8_000) { exists("summaryDone") }
        tag("summaryDone").performScrollTo().performClick()
        compose.waitUntil(3_000) { exists("playToday") }
        val status = tag("day-$yesterday").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)!!
        assertTrue(status, status.contains("Completed"))
        // A past day counts for the streak once: yesterday alone is a 1-day streak still alive today.
        assertTrue(tileText("dailyStreak").startsWith("1"))
    }

    /** A classic game is saved with every move: Home offers "Continue · Stage 1". */
    @Test fun continueAClassicGameFromHome() {
        launch()
        click("newGame")
        click("hint")
        click("back")
        compose.waitUntil(3_000) { exists("continue") }
        tag("continue").assertIsDisplayed()
        click("continue")
        assertEquals("-10", score())
    }

    /** Settings: the reminder switch and the About rows. */
    @Test fun settingsHasTheReminderAndRate() {
        launch()
        click("settings")
        tag("reminderToggle").assertIsDisplayed()
        tag("rateApp").performScrollTo().assertIsDisplayed()
    }
}
