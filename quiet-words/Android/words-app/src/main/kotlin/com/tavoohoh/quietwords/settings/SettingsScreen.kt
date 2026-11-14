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
import com.tavoohoh.quiet.ui.StoreReview
import com.tavoohoh.quiet.ui.quietClickable
import com.tavoohoh.quiet.ui.quietSurface
import com.tavoohoh.quietwords.AppModel
import com.tavoohoh.quietwords.AppearanceMode
import com.tavoohoh.quietwords.BuildConfig
import com.tavoohoh.quietwords.R
import com.tavoohoh.quietwords.core.WordLanguage
import com.tavoohoh.quietwords.design.DayFormat
import com.tavoohoh.quietwords.design.LanguageChoice
import com.tavoohoh.quietwords.design.displayName
import com.tavoohoh.quietwords.design.GroupDivider
import com.tavoohoh.quietwords.design.GroupRow
import com.tavoohoh.quietwords.design.GroupSection
import com.tavoohoh.quietwords.design.GroupToggle

/**
 * Settings, in the family's order (iOS `SettingsView`): Play (words language, sound, haptics,
 * larger letters), Appearance, Daily Challenge (reminder), Account, Privacy, Reset Progress…
 * and About. Play Games comes in Phase 5.
 */
@Composable
fun SettingsScreen(app: AppModel, onBack: () -> Unit, onHowToPlay: () -> Unit) {
    val colors = QuietTheme.colors
    var choosesLanguage by rememberSaveable { mutableStateOf(false) }
    var confirmsReset by rememberSaveable { mutableStateOf(false) }
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
                    GroupSection(stringResource(R.string.appearance)) {
                        val dark = colors.isDark
                        GroupToggle(stringResource(R.string.match_system), null, app.appearance == AppearanceMode.SYSTEM, Modifier.testTag("matchSystem")) { matches ->
                            // Off: start on the opposite of what's on screen so the change shows right away.
                            app.setAppearanceMode(if (matches) AppearanceMode.SYSTEM else if (dark) AppearanceMode.LIGHT else AppearanceMode.DARK)
                        }
                        if (app.appearance != AppearanceMode.SYSTEM) {
                            Segments(
                                app.appearance, listOf(AppearanceMode.LIGHT to stringResource(R.string.light), AppearanceMode.DARK to stringResource(R.string.dark)),
                                app::setAppearanceMode, Modifier.padding(vertical = 8.dp).testTag("appearanceMode"),
                            )
                        }
                    }
                    GroupSection(stringResource(R.string.daily_challenge_title)) {
                        ReminderToggle(app.reminders, requestNotifications)
                    }
                    GroupSection(stringResource(R.string.account)) {
                        GroupRow(stringResource(R.string.how_to_play), null, Modifier.testTag("howToPlay"), onClick = onHowToPlay)
                    }
                    GroupSection(stringResource(R.string.privacy)) {
                        Text(stringResource(R.string.privacy_text), style = QuietType.subheadline, color = colors.muted, modifier = Modifier.padding(vertical = 10.dp))
                    }
                    GroupSection(null) {
                        Text(
                            stringResource(R.string.reset_progress), style = QuietType.body, color = colors.accent,
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).quietClickable { confirmsReset = true }
                                .padding(vertical = 13.dp).testTag("resetProgress"),
                        )
                    }
                    Text(
                        stringResource(R.string.reset_footnote), style = QuietType.footnote, color = colors.muted,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 0.dp).offset(y = (-10).dp),
                    )
                    GroupSection(stringResource(R.string.about)) {
                        GroupRow(stringResource(R.string.rate_app), null, Modifier.testTag("rateApp")) { StoreReview.openRatePage(context) }
                        GroupDivider()
                        GroupRow(stringResource(R.string.version), BuildConfig.VERSION_NAME, chevron = false)
                    }
                }
            }
        }
    }

    if (confirmsReset) {
        AlertDialog(
            onDismissRequest = { confirmsReset = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.reset_question), style = QuietType.headline, color = colors.ink) },
            text = { Text(stringResource(R.string.reset_message), style = QuietType.body, color = colors.muted) },
            confirmButton = {
                TextButton(onClick = { confirmsReset = false; app.resetProgress() }, modifier = Modifier.testTag("confirmReset")) {
                    Text(stringResource(R.string.reset_confirm), color = colors.accent, style = QuietType.headline)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmsReset = false }) {
                    Text(stringResource(R.string.cancel), color = colors.primary, style = QuietType.headline)
                }
            },
        )
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

/**
 * Launches the system's notification prompt (Android 13+) and reports the answer to the
 * reminders; on older versions there is no prompt, so it counts as granted.
 */
@Composable
fun rememberNotificationRequest(reminders: DailyReminders): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        reminders.onPermissionResult(granted)
    }
    return remember(reminders) {
        {
            if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else reminders.onPermissionResult(true)
        }
    }
}

/** "Remind you at 7 PM?" after a first completed daily. Asked once; Settings keeps the switch. */
@Composable
fun ReminderOffer(onRemind: () -> Unit, onNotNow: () -> Unit) {
    val colors = QuietTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    AlertDialog(
        onDismissRequest = onNotNow,
        containerColor = colors.surface,
        title = { Text(stringResource(R.string.reminder_offer_title, DayFormat.hour(DailyReminderPlan.REMINDER_HOUR, locale)), style = QuietType.headline, color = colors.ink) },
        text = { Text(stringResource(R.string.reminder_offer_text), style = QuietType.body, color = colors.muted) },
        confirmButton = {
            TextButton(onClick = onRemind, modifier = Modifier.testTag("remindMe")) {
                Text(stringResource(R.string.remind_me), color = colors.primary, style = QuietType.headline)
            }
        },
        dismissButton = {
            TextButton(onClick = onNotNow, modifier = Modifier.testTag("notNow")) {
                Text(stringResource(R.string.not_now), color = colors.muted, style = QuietType.headline)
            }
        },
    )
}

/** "Daily challenge reminder": turning it on asks for permission the first time; if notifications are off for the app, it points to the system settings. */
@Composable
private fun ReminderToggle(reminders: DailyReminders, request: () -> Unit) {
    val colors = QuietTheme.colors
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val denied = reminders.authorization == DailyReminders.Authorization.DENIED
    GroupToggle(
        stringResource(R.string.reminder_toggle),
        stringResource(R.string.reminder_detail, DayFormat.hour(DailyReminderPlan.REMINDER_HOUR, locale), DayFormat.hour(DailyReminderPlan.STREAK_HOUR, locale)),
        reminders.isActive, Modifier.testTag("reminderToggle"), enabled = !denied,
    ) { on -> if (reminders.setEnabled(on)) request() }
    if (denied) {
        Text(
            stringResource(R.string.reminder_denied), style = QuietType.footnote, color = colors.primary,
            modifier = Modifier.padding(bottom = 10.dp).quietClickable { context.startActivity(reminders.settingsIntent()) },
        )
    }
}

