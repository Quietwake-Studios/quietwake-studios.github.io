package com.tavoohoh.quietnumbers

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Play Games on screen, without a session (UI tests never start Play Games; iOS's are offline
 * too): the Home trophy, signed-out leaderboards pointing to Settings, the Account row, the
 * privacy text and Reset's note; with the debug demo, the boards and "#2" among friends.
 */
@RunWith(AndroidJUnit4::class)
class PlayGamesUiTests {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun launch(vararg extras: Pair<String, Any>) {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .putExtra("uiTesting", true).putExtra("date", "2026-11-14T15:00:00Z")
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

    @Test fun signedOutLeaderboardsPointToSettings() {
        launch()
        compose.onNodeWithTag("leaderboards").performClick()
        compose.onNodeWithText("Play with friends").assertIsDisplayed()
        compose.onNodeWithTag("openSettings").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("playGames").performScrollTo().assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasText("Not signed in")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun settingsSayWhatPlayGamesShares() {
        launch("open" to "settings")
        compose.onNodeWithText("If you sign in to Play Games", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("here and in the cloud.", substring = true).performScrollTo().assertIsDisplayed()
        // Without a session (or a Play Games ID) a tap does nothing harmful.
        compose.onNodeWithTag("playGames").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("playGames").assertIsDisplayed()
    }

    /** The debug demo's boards (iOS `DemoData.leaderboard`): today's shows times and the tie-break note; the Daily screen, "#2" among friends. */
    @Test fun demoBoardsAndFriendsRank() {
        launch("demo" to true, "open" to "leaderboards")
        compose.onNodeWithText("guss").assertIsDisplayed()
        compose.onNodeWithText("Same score: the faster time ranks higher.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Best score").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("6,120").assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasText("Same score: the faster time ranks higher.")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("daily").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("friendsRank")).fetchSemanticsNodes().isNotEmpty() }
        val tile = compose.onNodeWithTag("friendsRank").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.joinToString { it.text }
        assertTrue(tile, tile.startsWith("#2"))
    }
}
