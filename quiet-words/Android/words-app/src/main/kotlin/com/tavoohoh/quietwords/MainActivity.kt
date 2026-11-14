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

class MainActivity : ComponentActivity() {
    private val app: AppModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        app.configure(intent)
        setContent {
            // Teal & coral (the palette choice is a reward, Phase 4); light or dark following
            // the system unless Settings › Appearance fixes one.
            val dark = when (app.appearance) {
                AppearanceMode.SYSTEM -> isSystemInDarkTheme()
                AppearanceMode.LIGHT -> false
                AppearanceMode.DARK -> true
            }
            QuietTheme(dark = dark) {
                CompositionLocalProvider(LocalUiTesting provides app.uiTesting) {
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
