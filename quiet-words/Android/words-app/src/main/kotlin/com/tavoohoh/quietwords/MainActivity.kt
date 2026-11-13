package com.tavoohoh.quietwords

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
            // Teal & coral, light or dark following the system (the palette choice is a reward, Phase 4).
            QuietTheme {
                CompositionLocalProvider(LocalUiTesting provides app.uiTesting) {
                    WordsApp(app)
                }
            }
        }
    }
}
