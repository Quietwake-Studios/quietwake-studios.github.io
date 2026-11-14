package com.tavoohoh.quietwords

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
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
 * Play Games is optional: signed out (and with the placeholder IDs) everything works, the
 * leaderboards point to Settings and Settings shows the account and the privacy text. Runs in English.
 */
@RunWith(AndroidJUnit4::class)
class PlayGamesUiTests {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun launch(vararg extras: Pair<String, String>) {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .putExtra("uiTesting", true).putExtra("tutorialDone", true).putExtra("words", "en")
        for ((k, v) in extras) intent.putExtra(k, v)
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
        // Tapping the row signed out (placeholder IDs) does nothing harmful.
        compose.onNodeWithTag("playGames").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("playGames").assertIsDisplayed()
    }
}
