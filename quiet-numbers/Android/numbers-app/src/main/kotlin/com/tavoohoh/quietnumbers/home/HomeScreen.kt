package com.tavoohoh.quietnumbers.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tavoohoh.quiet.ui.CapsuleButton
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quiet.ui.QuietType
import com.tavoohoh.quiet.ui.quietClickable
import com.tavoohoh.quiet.ui.quietSurface
import com.tavoohoh.quietnumbers.AppModel
import com.tavoohoh.quietnumbers.R
import com.tavoohoh.quietnumbers.Route
import java.text.NumberFormat

/**
 * Home (iOS `HomeView`): the menu at the top right, the icon and name, the daily card with the
 * streak, "Continue · Stage N" when a classic game is saved, New Game and "Your stats".
 * Leaderboards and Collection join the menu in Phases 4–5.
 */
@Composable
fun HomeScreen(app: AppModel) {
    val colors = QuietTheme.colors
    Box(Modifier.fillMaxSize().background(colors.background)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val regular = maxWidth >= 600.dp
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = if (regular) 440.dp else 360.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Menu(
                        listOf(MenuItem(Icons.Outlined.Settings, stringResource(R.string.settings), "settings") { app.push(Route.SETTINGS) }),
                    )
                    Spacer(Modifier.height(if (regular) 64.dp else 40.dp))
                    Image(painterResource(R.drawable.home_icon), null, Modifier.size(84.dp).clip(RoundedCornerShape(84.dp * 0.225f)))
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.app_name), style = QuietType.largeTitle, color = colors.ink, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.tagline), style = QuietType.body, color = colors.muted)
                    Spacer(Modifier.height(36.dp))
                    DailyCard(app) { app.push(Route.DAILY) }
                    Spacer(Modifier.height(12.dp))
                    app.classicStage?.let { stage ->
                        CapsuleButton(stringResource(R.string.continue_stage, stage), app::continueClassic, Modifier.testTag("continue"))
                        Spacer(Modifier.height(12.dp))
                    }
                    CapsuleButton(stringResource(R.string.new_game), app::newClassic, Modifier.testTag("newGame"), prominent = app.classicStage == null)
                    Spacer(Modifier.height(30.dp))
                    Stats(app)
                }
            }
        }
    }
}

private data class MenuItem(val icon: ImageVector, val label: String, val tag: String, val action: () -> Unit)

/** The menu in a capsule at the top right (iOS `HomeMenu`). */
@Composable
private fun Menu(items: List<MenuItem>) {
    val colors = QuietTheme.colors
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Spacer(Modifier.weight(1f))
        Row(Modifier.quietSurface(CircleShape).padding(horizontal = 6.dp)) {
            for ((icon, label, tag, action) in items) {
                Box(Modifier.size(52.dp).quietClickable(onClick = action).testTag(tag), contentAlignment = Alignment.Center) {
                    Icon(icon, label, tint = colors.primary, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/** Today's challenge and the streak (iOS `DailyCard`). */
@Composable
private fun DailyCard(app: AppModel, onOpen: () -> Unit) {
    val colors = QuietTheme.colors
    val n = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    val today = app.todayKey
    val record = app.dailies[today]
    val status = when {
        app.isDailyInProgress(today) -> stringResource(R.string.in_progress)
        record == null -> stringResource(R.string.not_played_yet)
        record.isCompleted -> stringResource(R.string.completed_score, n.format(record.score))
        else -> stringResource(R.string.played_score, n.format(record.score))
    }
    val streak = app.currentStreak
    Row(
        Modifier.fillMaxWidth().quietSurface(RoundedCornerShape(24.dp)).quietClickable(onClick = onOpen).testTag("daily")
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(R.string.daily_challenge), style = QuietType.headline, color = colors.ink)
            Text(status, style = QuietType.subheadline, color = colors.muted, modifier = Modifier.testTag("dailyStatus"))
        }
        if (streak > 0) {
            val label = pluralStringResource(R.plurals.day_streak, streak, streak)
            Row(
                Modifier.clearAndSetSemantics { contentDescription = label; testTag = "homeStreak" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(Icons.Rounded.LocalFireDepartment, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                Text(n.format(streak), style = QuietType.title3, color = colors.accent)
            }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.muted, modifier = Modifier.size(20.dp))
    }
}

/** Best classic score, best daily streak and games (iOS `StatsRow`). */
@Composable
private fun Stats(app: AppModel) {
    val colors = QuietTheme.colors
    val n = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.your_stats), style = QuietType.subheadline.copy(fontWeight = FontWeight.Bold), color = colors.muted,
            modifier = Modifier.semantics { heading() },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(n.format(app.progress.bestScore), stringResource(R.string.best_score), Modifier.weight(1f).testTag("statBest"))
            StatTile(n.format(app.longestStreak), stringResource(R.string.top_streak), Modifier.weight(1f).testTag("statStreak"))
            StatTile(n.format(app.progress.gamesPlayed), stringResource(R.string.games), Modifier.weight(1f).testTag("statGames"))
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier) {
    val colors = QuietTheme.colors
    Column(
        modifier.quietSurface(RoundedCornerShape(18.dp)).padding(14.dp).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = QuietType.stat, color = colors.ink, maxLines = 1)
        Text(label, style = QuietType.footnote, color = colors.muted, maxLines = 1)
    }
}
