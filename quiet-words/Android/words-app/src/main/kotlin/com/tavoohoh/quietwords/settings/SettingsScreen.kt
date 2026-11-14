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

/**
 * Settings, minimal for Phase 3 (iOS `SettingsView` is the reference): words language, the
 * daily reminder, appearance, how to play, rate the app, privacy, reset progress and version.
 * Sound, larger letters and Play Games come with later phases.
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
                    Section(stringResource(R.string.game_section)) {
                        SettingsRow(stringResource(R.string.word_language), app.wordLanguage.displayName(), Modifier.testTag("settingsLanguage")) { choosesLanguage = true }
                        Divider()
                        ReminderToggle(app.reminders, requestNotifications)
                    }
                    Section(stringResource(R.string.appearance)) {
                        val dark = colors.isDark
                        Toggle(stringResource(R.string.match_system), null, app.appearance == AppearanceMode.SYSTEM, Modifier.testTag("matchSystem")) { matches ->
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
                    Section(stringResource(R.string.account)) {
                        SettingsRow(stringResource(R.string.how_to_play), null, Modifier.testTag("howToPlay"), onClick = onHowToPlay)
                    }
                    Section(stringResource(R.string.privacy)) {
                        Text(stringResource(R.string.privacy_text), style = QuietType.subheadline, color = colors.muted, modifier = Modifier.padding(vertical = 10.dp))
                    }
                    Section(null) {
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
                    Section(stringResource(R.string.about)) {
                        SettingsRow(stringResource(R.string.rate_app), null, Modifier.testTag("rateApp")) { StoreReview.openRatePage(context) }
                        Divider()
                        SettingsRow(stringResource(R.string.version), BuildConfig.VERSION_NAME, chevron = false)
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
    Toggle(
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

// Rows

@Composable
private fun Section(title: String?, content: @Composable ColumnScope.() -> Unit) {
    val colors = QuietTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Text(
                title.uppercase(), style = QuietType.caption.copy(fontWeight = FontWeight.Bold), color = colors.muted,
                modifier = Modifier.padding(start = 16.dp).semantics { heading() },
            )
        }
        Column(Modifier.fillMaxWidth().quietSurface(RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 4.dp), content = content)
    }
}

@Composable
private fun Divider() = HorizontalDivider(thickness = 1.dp, color = QuietTheme.colors.separator.copy(alpha = 0.6f))

@Composable
private fun SettingsRow(label: String, value: String?, modifier: Modifier = Modifier, chevron: Boolean = true, onClick: (() -> Unit)? = null) {
    val colors = QuietTheme.colors
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
            .then(if (onClick != null) Modifier.quietClickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = QuietType.body, color = colors.ink)
        Spacer(Modifier.weight(1f))
        if (value != null) Text(value, style = QuietType.body, color = colors.muted)
        if (chevron && onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.muted, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun Toggle(label: String, detail: String?, checked: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val colors = QuietTheme.colors
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp)
            .quietClickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = QuietType.body, color = colors.ink)
            if (detail != null) Text(detail, style = QuietType.subheadline, color = colors.muted)
        }
        Switch(
            checked = checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.primary, checkedThumbColor = colors.onPrimary,
                uncheckedTrackColor = colors.surface2, uncheckedThumbColor = colors.muted, uncheckedBorderColor = colors.surface2,
            ),
        )
    }
}
