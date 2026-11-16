package com.tavoohoh.quietnumbers.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tavoohoh.quiet.ui.AboutSection
import com.tavoohoh.quiet.ui.AppearanceSection
import com.tavoohoh.quiet.ui.GroupDivider
import com.tavoohoh.quiet.ui.GroupRow
import com.tavoohoh.quiet.ui.GroupSection
import com.tavoohoh.quiet.ui.GroupToggle
import com.tavoohoh.quiet.ui.PrivacySection
import com.tavoohoh.quiet.ui.QuietScreen
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quiet.ui.ReminderToggle
import com.tavoohoh.quiet.ui.ResetProgressSection
import com.tavoohoh.quiet.ui.rememberNotificationRequest
import com.tavoohoh.quietnumbers.AppModel
import com.tavoohoh.quietnumbers.BuildConfig
import com.tavoohoh.quietnumbers.R

/**
 * Settings in the family's order (iOS `SettingsView`): Play (sound, haptics, larger numbers),
 * Appearance, Daily Challenge (reminder), Account (How to play; Play Games comes with Phase 5),
 * Privacy, Reset Progress… with its footnote, and About.
 */
@Composable
fun SettingsScreen(app: AppModel, onBack: () -> Unit) {
    val colors = QuietTheme.colors
    val requestNotifications = rememberNotificationRequest(app.reminders)
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()) {
        val regular = maxWidth >= 600.dp
        QuietScreen(stringResource(R.string.settings), onBack = onBack, backDescription = stringResource(R.string.back)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = if (regular) 680.dp else 480.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    GroupSection(stringResource(R.string.play_section)) {
                        GroupToggle(stringResource(R.string.sound), null, app.soundEnabled, Modifier.testTag("sound"), onChange = app::setSound)
                        GroupDivider()
                        GroupToggle(stringResource(R.string.haptics), null, app.hapticsEnabled, Modifier.testTag("haptics"), onChange = app::setHaptics)
                        GroupDivider()
                        GroupToggle(stringResource(R.string.larger_numbers), null, app.largerNumbers, Modifier.testTag("largerNumbers"), onChange = app::chooseLargerNumbers)
                    }
                    AppearanceSection(app.appearance, app::setAppearanceMode)
                    GroupSection(stringResource(R.string.daily_challenge)) {
                        ReminderToggle(app.reminders, requestNotifications)
                    }
                    GroupSection(stringResource(R.string.account)) {
                        GroupRow(stringResource(R.string.how_to_play), null, Modifier.testTag("howToPlay")) { app.openTutorial(firstRun = false) }
                    }
                    PrivacySection(stringResource(R.string.app_name))
                    ResetProgressSection(app::resetProgress)
                    AboutSection(stringResource(R.string.app_name), BuildConfig.VERSION_NAME)
                }
            }
        }
    }
}
