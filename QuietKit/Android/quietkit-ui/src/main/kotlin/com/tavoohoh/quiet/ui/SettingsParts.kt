package com.tavoohoh.quiet.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tavoohoh.quiet.core.AppearanceMode

/*
 * The Settings sections every Quiet game has, with the family's copy (root CLAUDE.md, "Ajustes
 * iguales en todos los juegos"): Appearance, Privacy, Reset Progress… with its footnote and
 * confirmation, and About (Rate and Version).
 */

/** "Match system", and off: Light · Dark, starting on the opposite of what's on screen so the change shows right away. */
@Composable
fun AppearanceSection(mode: AppearanceMode, onChange: (AppearanceMode) -> Unit) {
    val dark = QuietTheme.colors.isDark
    GroupSection(stringResource(R.string.quiet_appearance)) {
        GroupToggle(stringResource(R.string.quiet_match_system), null, mode == AppearanceMode.SYSTEM, Modifier.testTag("matchSystem")) { matches ->
            onChange(if (matches) AppearanceMode.SYSTEM else if (dark) AppearanceMode.LIGHT else AppearanceMode.DARK)
        }
        if (mode != AppearanceMode.SYSTEM) {
            Segments(
                mode, listOf(AppearanceMode.LIGHT to stringResource(R.string.quiet_light), AppearanceMode.DARK to stringResource(R.string.quiet_dark)),
                onChange, Modifier.padding(vertical = 8.dp).testTag("appearanceMode"),
            )
        }
    }
}

/** The family's privacy text with the app's name (Android: Play Games and the Google account). */
@Composable
fun PrivacySection(appName: String) {
    GroupSection(stringResource(R.string.quiet_privacy)) {
        Text(
            stringResource(R.string.quiet_privacy_text, appName), style = QuietType.subheadline, color = QuietTheme.colors.muted,
            modifier = Modifier.padding(vertical = 10.dp),
        )
    }
}

/** "Reset Progress…" with its footnote; asks first with the family's copy. */
@Composable
fun ResetProgressSection(onReset: () -> Unit) {
    val colors = QuietTheme.colors
    var confirms by rememberSaveable { mutableStateOf(false) }
    Column {
        GroupSection(null) {
            Text(
                stringResource(R.string.quiet_reset_progress), style = QuietType.body, color = colors.accent,
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).quietClickable { confirms = true }
                    .padding(vertical = 13.dp).testTag("resetProgress"),
            )
        }
        Text(
            stringResource(R.string.quiet_reset_footnote), style = QuietType.footnote, color = colors.muted,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )
    }
    if (confirms) {
        AlertDialog(
            onDismissRequest = { confirms = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.quiet_reset_question), style = QuietType.headline, color = colors.ink) },
            text = { Text(stringResource(R.string.quiet_reset_message), style = QuietType.body, color = colors.muted) },
            confirmButton = {
                TextButton(onClick = { confirms = false; onReset() }, modifier = Modifier.testTag("confirmReset")) {
                    Text(stringResource(R.string.quiet_reset_confirm), color = colors.accent, style = QuietType.headline)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirms = false }) {
                    Text(stringResource(R.string.quiet_cancel), color = colors.primary, style = QuietType.headline)
                }
            },
        )
    }
}

/** About: "Rate <app>" (the store's review page) and Version, only the number. */
@Composable
fun AboutSection(appName: String, version: String) {
    val context = LocalContext.current
    GroupSection(stringResource(R.string.quiet_about)) {
        GroupRow(stringResource(R.string.quiet_rate_app, appName), null, Modifier.testTag("rateApp")) { StoreReview.openRatePage(context) }
        GroupDivider()
        GroupRow(stringResource(R.string.quiet_version), version, chevron = false)
    }
}
