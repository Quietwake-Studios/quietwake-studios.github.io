package com.tavoohoh.quiet.ui

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tavoohoh.quiet.core.DailyReminderPlan
import com.tavoohoh.quiet.core.ReminderText

/**
 * The family's texts for the daily reminders (iOS: each app's `Reminders.swift`, all alike):
 * "Daily Challenge" with today's waiting challenge or the streak, and "Your streak" at 21:00.
 * `friends` gives a game's own line once friends are known (Play Games; null until then).
 */
fun DailyReminders.useStandardTexts(context: Context, friends: (ReminderText) -> String? = { null }) {
    val app = context.applicationContext
    channelName = app.getString(R.string.quiet_reminder_title)
    texts = { item ->
        val title = app.getString(R.string.quiet_reminder_title)
        when (val text = ReminderText.of(item)) {
            is ReminderText.StreakAtRisk -> DailyReminders.Content(
                app.getString(R.string.quiet_your_streak), app.resources.getQuantityString(R.plurals.quiet_reminder_at_risk, text.days, text.days),
            )
            is ReminderText.Streak -> DailyReminders.Content(title, app.resources.getQuantityString(R.plurals.quiet_reminder_streak, text.days, text.days))
            ReminderText.Waiting -> DailyReminders.Content(title, app.getString(R.string.quiet_reminder_waiting))
            is ReminderText.OneFriend, is ReminderText.Friends -> DailyReminders.Content(title, friends(text) ?: app.getString(R.string.quiet_reminder_waiting))
        }
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
        title = { Text(stringResource(R.string.quiet_reminder_offer_title, DayFormat.hour(DailyReminderPlan.REMINDER_HOUR, locale)), style = QuietType.headline, color = colors.ink) },
        text = { Text(stringResource(R.string.quiet_reminder_offer_text), style = QuietType.body, color = colors.muted) },
        confirmButton = {
            TextButton(onClick = onRemind, modifier = Modifier.testTag("remindMe")) {
                Text(stringResource(R.string.quiet_remind_me), color = colors.primary, style = QuietType.headline)
            }
        },
        dismissButton = {
            TextButton(onClick = onNotNow, modifier = Modifier.testTag("notNow")) {
                Text(stringResource(R.string.quiet_not_now), color = colors.muted, style = QuietType.headline)
            }
        },
    )
}

/** "Daily challenge reminder": turning it on asks for permission the first time; if notifications are off for the app, it points to the system settings. */
@Composable
fun ReminderToggle(reminders: DailyReminders, request: () -> Unit) {
    val colors = QuietTheme.colors
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val denied = reminders.authorization == DailyReminders.Authorization.DENIED
    GroupToggle(
        stringResource(R.string.quiet_reminder_toggle),
        stringResource(R.string.quiet_reminder_detail, DayFormat.hour(DailyReminderPlan.REMINDER_HOUR, locale), DayFormat.hour(DailyReminderPlan.STREAK_HOUR, locale)),
        reminders.isActive, Modifier.testTag("reminderToggle"), enabled = !denied,
    ) { on -> if (reminders.setEnabled(on)) request() }
    if (denied) {
        Text(
            stringResource(R.string.quiet_reminder_denied), style = QuietType.footnote, color = colors.primary,
            modifier = Modifier.padding(bottom = 10.dp).quietClickable { context.startActivity(reminders.settingsIntent()) },
        )
    }
}

