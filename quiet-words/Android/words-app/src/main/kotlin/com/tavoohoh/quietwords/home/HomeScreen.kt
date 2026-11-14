package com.tavoohoh.quietwords.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tavoohoh.quiet.ui.CapsuleButton
import com.tavoohoh.quiet.ui.QuietHaptics
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quiet.ui.QuietType
import com.tavoohoh.quiet.ui.quietClickable
import com.tavoohoh.quiet.ui.quietSurface
import com.tavoohoh.quiet.ui.rememberHaptics
import com.tavoohoh.quietwords.AppModel
import com.tavoohoh.quietwords.R
import com.tavoohoh.quietwords.Route
import com.tavoohoh.quietwords.core.DailyChallenge
import com.tavoohoh.quietwords.core.WordLanguage
import com.tavoohoh.quietwords.design.LanguagePill
import com.tavoohoh.quietwords.design.displayName
import com.tavoohoh.quietwords.design.localizedName
import com.tavoohoh.quietwords.design.title
import com.tavoohoh.quietwords.design.wordsTitle

/**
 * Home: the menu (Settings for now; Leaderboards and Collection come with later phases), the
 * daily card (opens Daily Challenge), Continue for a free game in progress, New Game and the
 * stats (puzzles, album words, best streak).
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
                Column(
                    Modifier.widthIn(max = if (regular) 440.dp else 360.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Menu(app)
                    Spacer(Modifier.height(if (regular) 56.dp else 16.dp))
                    Image(
                        painterResource(R.drawable.home_icon), contentDescription = null,
                        modifier = Modifier.size(84.dp).clip(RoundedCornerShape(84.dp * 0.225f)),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.app_name), style = QuietType.largeTitle, color = colors.ink, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.tagline), style = QuietType.body, color = colors.muted)
                    Spacer(Modifier.height(28.dp))
                    DailyCard(app, onOpen = app::openDaily)
                    Spacer(Modifier.height(12.dp))
                    app.freeInProgress?.let { saved ->
                        CapsuleButton(
                            stringResource(R.string.continue_pair, app.theme(saved.themeID)?.localizedName() ?: "", saved.level.title()),
                            onClick = app::continueFree, prominent = false, modifier = Modifier.testTag("continue"),
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    CapsuleButton(
                        stringResource(R.string.new_game),
                        onClick = { app.push(if (app.needsTutorial) Route.TUTORIAL else Route.NEW_GAME) },
                        modifier = Modifier.testTag("newGame"),
                    )
                    Spacer(Modifier.height(28.dp))
                    Stats(app)
                }
            }
        }
    }
}

/** Settings in a capsule at the top right (Leaderboards and Collection join it later). */
@Composable
private fun Menu(app: AppModel) {
    val colors = QuietTheme.colors
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.quietSurface(CircleShape).padding(horizontal = 6.dp).size(width = 44.dp, height = 44.dp)
                .quietClickable { app.push(Route.SETTINGS) }.testTag("settings"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Settings, stringResource(R.string.settings), tint = colors.ink, modifier = Modifier.size(22.dp))
        }
    }
}

/** Puzzles · Album · Best streak. */
@Composable
private fun Stats(app: AppModel) {
    val album = app.album.total(app.wordLanguage, app.library)
    Row(Modifier.fillMaxWidth()) {
        Stat("${app.progress.gamesWon}", stringResource(R.string.puzzles), Modifier.weight(1f))
        Stat("${album.found}", stringResource(R.string.album), Modifier.weight(1f))
        Stat("${app.longestStreak}", stringResource(R.string.best_streak), Modifier.weight(1f).testTag("bestStreak"))
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    val colors = QuietTheme.colors
    Column(modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = QuietType.stat, color = colors.ink)
        Text(label, style = QuietType.footnote, color = colors.muted)
    }
}

/** Today's challenge: theme, level, status, the streak, the EN · ES pill (a check on a language done today) and Play. */
@Composable
private fun DailyCard(app: AppModel, onOpen: () -> Unit) {
    val colors = QuietTheme.colors
    val haptics = rememberHaptics()
    val today = app.todayKey
    val theme = app.dailyTheme(today)
    val level = DailyChallenge.level(today)
    val done = app.doneLanguages(today)
    val language = app.wordLanguage
    val streak = app.currentStreak
    val streakLabel = androidx.compose.ui.res.pluralStringResource(R.plurals.streak_label, streak, streak)
    Column(
        Modifier.fillMaxWidth().background(colors.primary, RoundedCornerShape(24.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    stringResource(R.string.daily_challenge).uppercase(), color = colors.onPrimary,
                    style = QuietType.caption.copy(fontWeight = FontWeight.Bold), modifier = Modifier.alpha(0.8f),
                )
                Text(stringResource(R.string.pair, theme?.localizedName() ?: "", level.title()), style = QuietType.title3, color = colors.onPrimary)
                Text(dailyStatus(done, language), style = QuietType.footnote, color = colors.onPrimary, modifier = Modifier.alpha(0.85f).testTag("dailyStatus"))
            }
            Spacer(Modifier.size(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.clearAndSetSemantics { contentDescription = streakLabel; testTag = "homeStreak" },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Rounded.LocalFireDepartment, null, tint = colors.onPrimary, modifier = Modifier.size(22.dp))
                    Text("$streak", style = QuietType.title3, color = colors.onPrimary)
                }
                LanguagePill(language, app::setLanguage, done = done, onPrimary = true, onTick = { haptics.play(QuietHaptics.Event.SELECT) })
            }
        }
        val play = when {
            language in done -> R.string.play_again
            app.isInProgress(today, language) -> R.string.continue_
            else -> R.string.play
        }
        Box(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 46.dp)
                .background(colors.onPrimary, CircleShape)
                .quietClickable(onClick = onOpen)
                .testTag("daily"),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(play), style = QuietType.headline, color = colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** "Done in both languages", "English done · Spanish to go", or the words language. */
@Composable
private fun dailyStatus(done: Set<WordLanguage>, language: WordLanguage): String {
    if (done.size == 2) return stringResource(R.string.done_both)
    val one = done.firstOrNull() ?: return language.wordsTitle()
    val other = WordLanguage.entries.first { it != one }
    return stringResource(R.string.done_one, one.displayName(), other.displayName())
}

