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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tavoohoh.quiet.ui.GroupDivider
import com.tavoohoh.quiet.ui.GroupRow
import com.tavoohoh.quiet.ui.GroupSection
import com.tavoohoh.quiet.ui.QuietScreen
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quiet.ui.ReminderToggle
import com.tavoohoh.quiet.ui.StoreReview
import com.tavoohoh.quiet.ui.rememberNotificationRequest
import com.tavoohoh.quietnumbers.AppModel
import com.tavoohoh.quietnumbers.BuildConfig
import com.tavoohoh.quietnumbers.R

/**
 * Settings, Phase 3: the sections in the family's order that exist so far, Daily Challenge (the
 * reminder) and About (Rate, Version). Play, Appearance, Account, Privacy and Reset Progress… come
 * with Phase 4 (iOS `SettingsView`).
 */
@Composable
fun SettingsScreen(app: AppModel, onBack: () -> Unit) {
    val colors = QuietTheme.colors
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
                    GroupSection(stringResource(R.string.daily_challenge)) {
                        ReminderToggle(app.reminders, requestNotifications)
                    }
                    GroupSection(stringResource(R.string.about)) {
                        GroupRow(stringResource(R.string.rate_app), null, Modifier.testTag("rateApp")) { StoreReview.openRatePage(context) }
                        GroupDivider()
                        GroupRow(stringResource(R.string.version), BuildConfig.VERSION_NAME, chevron = false)
                    }
                }
            }
        }
    }
}
