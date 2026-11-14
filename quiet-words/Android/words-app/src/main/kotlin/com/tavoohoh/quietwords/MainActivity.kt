package com.tavoohoh.quietwords

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quietwords.design.BoardLooks
import com.tavoohoh.quietwords.design.LocalBoardLooks
import com.tavoohoh.quietwords.design.LocalFeedback

class MainActivity : ComponentActivity() {
    private val app: AppModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        app.configure(intent)
        setContent {
            // The theme picked in Collection › Rewards (if unlocked); light or dark following the
            // system unless Settings › Appearance fixes one.
            val dark = when (app.appearance) {
                AppearanceMode.SYSTEM -> isSystemInDarkTheme()
                AppearanceMode.LIGHT -> false
                AppearanceMode.DARK -> true
            }
            val looks = app.looks
            QuietTheme(palette = looks.palette, dark = dark) {
                CompositionLocalProvider(
                    LocalUiTesting provides app.uiTesting,
                    LocalFeedback provides app.feedback,
                    LocalBoardLooks provides BoardLooks(looks.marker, looks.boardFont, app.largerLetters),
                ) {
                    WordsApp(app)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The day may have changed, or notifications been turned on or off in the system.
        if (!app.uiTesting) app.reminders.reschedule() else app.reminders.refreshAuthorization()
    }

    override fun onStop() {
        super.onStop()
        app.saveActiveGame()
    }
}
