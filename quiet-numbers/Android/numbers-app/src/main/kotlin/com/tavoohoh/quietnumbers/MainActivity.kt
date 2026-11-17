package com.tavoohoh.quietnumbers

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quietnumbers.design.LocalFeedback
import com.tavoohoh.quietnumbers.design.BoardLooks
import com.tavoohoh.quietnumbers.design.LocalBoardLooks
import androidx.compose.foundation.isSystemInDarkTheme

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
            val looks = app.looks
            QuietTheme(palette = looks.theme.palette, dark = app.appearance.isDark(isSystemInDarkTheme())) {
                CompositionLocalProvider(
                    LocalUiTesting provides app.uiTesting,
                    LocalFeedback provides app.feedback,
                    LocalBoardLooks provides BoardLooks(looks.tileStyle, looks.numberFont, looks.clearEffect, app.largerNumbers),
                ) {
                    NumbersApp(app)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        app.games.attach(this)
        // The day may have changed, or notifications been turned on or off in the system.
        if (!app.uiTesting) app.reminders.reschedule() else app.reminders.refreshAuthorization()
    }

    override fun onStop() {
        super.onStop()
        app.saveActiveGame()
    }
}
