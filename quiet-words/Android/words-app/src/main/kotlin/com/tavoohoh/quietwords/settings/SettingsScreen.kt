package com.tavoohoh.quietwords.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tavoohoh.quiet.core.DailyReminderPlan
import com.tavoohoh.quiet.ui.DailyReminders
import com.tavoohoh.quiet.ui.QuietScreen
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quiet.ui.QuietType
import com.tavoohoh.quiet.ui.Segments
import com.tavoohoh.quiet.ui.ReminderToggle
import com.tavoohoh.quiet.ui.rememberNotificationRequest
import com.tavoohoh.quiet.ui.StoreReview
import com.tavoohoh.quiet.ui.quietClickable
import com.tavoohoh.quiet.ui.quietSurface
import com.tavoohoh.quietwords.AppModel
import com.tavoohoh.quietwords.AppearanceMode
import com.tavoohoh.quietwords.BuildConfig
import com.tavoohoh.quietwords.R
import com.tavoohoh.quietwords.core.WordLanguage
import com.tavoohoh.quiet.ui.DayFormat
import com.tavoohoh.quietwords.design.LanguageChoice
import com.tavoohoh.quietwords.design.displayName
import com.tavoohoh.quiet.ui.GroupDivider
import com.tavoohoh.quiet.ui.GroupRow
import com.tavoohoh.quiet.ui.GroupSection
import com.tavoohoh.quiet.ui.GroupToggle
import com.tavoohoh.quiet.ui.AppearanceSection
import com.tavoohoh.quiet.ui.PrivacySection
import com.tavoohoh.quiet.ui.ResetProgressSection
import com.tavoohoh.quiet.ui.AboutSection

/**
 * Settings, in the family's order (iOS `SettingsView`): Play (words language, sound, haptics,
 * larger letters), Appearance, Daily Challenge (reminder), Account, Privacy, Reset Progress…
 * and About. Play Games comes in Phase 5.
 */
@Composable
fun SettingsScreen(app: AppModel, onBack: () -> Unit, onHowToPlay: () -> Unit) {
    val colors = QuietTheme.colors
    var choosesLanguage by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val requestNotifications = rememberNotificationRequest(app.reminders)

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()) {
        val regular = maxWidth >= 600.dp
        QuietScreen(stringResource(R.string.settings), onBack = onBack, backDescription = stringResource(R.string.back)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = if (regular) 560.dp else 480.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    GroupSection(stringResource(R.string.play_section)) {
                        GroupRow(stringResource(R.string.word_language), app.wordLanguage.displayName(), Modifier.testTag("settingsLanguage")) { choosesLanguage = true }
                        GroupDivider()
                        GroupToggle(stringResource(R.string.sound), null, app.soundEnabled, Modifier.testTag("sound"), onChange = app::setSound)
                        GroupDivider()
                        GroupToggle(stringResource(R.string.haptics), null, app.hapticsEnabled, Modifier.testTag("haptics"), onChange = app::setHaptics)
                        GroupDivider()
                        GroupToggle(
                            stringResource(R.string.larger_letters), stringResource(R.string.larger_letters_detail), app.largerLetters,
                            Modifier.testTag("largerLetters"), onChange = app::chooseLargerLetters,
                        )
                    }
                    AppearanceSection(app.appearance, app::setAppearanceMode)
                    GroupSection(stringResource(R.string.daily_challenge_title)) {
                        ReminderToggle(app.reminders, requestNotifications)
                    }
                    GroupSection(stringResource(R.string.account)) {
                        com.tavoohoh.quiet.ui.PlayGamesRow(app.games)
                        GroupDivider()
                        GroupRow(stringResource(R.string.how_to_play), null, Modifier.testTag("howToPlay"), onClick = onHowToPlay)
                    }
                    PrivacySection(stringResource(R.string.app_name))
                    ResetProgressSection(app::resetProgress)
                    AboutSection(stringResource(R.string.app_name), BuildConfig.VERSION_NAME)
                }
            }
        }
    }

    if (choosesLanguage) {
        AlertDialog(
            onDismissRequest = { choosesLanguage = false },
            containerColor = colors.background,
            title = { Text(stringResource(R.string.word_language), style = QuietType.headline, color = colors.ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LanguageChoice(WordLanguage.EN, "TIDE · CORAL · ABYSSAL", stringResource(R.string.themed_en), app.wordLanguage, {
                        app.setLanguage(it); choosesLanguage = false
                    })
                    LanguageChoice(WordLanguage.ES, "MAREA · CORAL · ÑANDÚ", stringResource(R.string.themed_es), app.wordLanguage, {
                        app.setLanguage(it); choosesLanguage = false
                    })
                }
            },
            confirmButton = {
                TextButton(onClick = { choosesLanguage = false }) { Text(stringResource(R.string.done), color = colors.primary, style = QuietType.headline) }
            },
        )
    }
}
