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

class MainActivity : ComponentActivity() {
    private val app: AppModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        app.configure(intent)
        setContent {
            // Teal & coral, light or dark following the system (themes and the appearance setting: Phase 4).
            QuietTheme {
                CompositionLocalProvider(LocalUiTesting provides app.uiTesting, LocalFeedback provides app.feedback) {
                    NumbersApp(app)
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
