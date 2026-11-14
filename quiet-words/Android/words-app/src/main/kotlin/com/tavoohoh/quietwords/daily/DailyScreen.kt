package com.tavoohoh.quietwords.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tavoohoh.quiet.core.DailyKey
import com.tavoohoh.quiet.ui.QuietHaptics
import com.tavoohoh.quiet.ui.QuietScreen
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quiet.ui.QuietType
import com.tavoohoh.quiet.ui.quietClickable
import com.tavoohoh.quiet.ui.quietSurface
import com.tavoohoh.quiet.ui.rememberHaptics
import com.tavoohoh.quietwords.AppClock
import com.tavoohoh.quietwords.AppModel
import com.tavoohoh.quietwords.R
import com.tavoohoh.quietwords.core.DailyChallenge
import com.tavoohoh.quietwords.design.DayFormat
import com.tavoohoh.quietwords.design.LanguagePill
import com.tavoohoh.quietwords.design.localizedName
import com.tavoohoh.quietwords.design.title
import com.tavoohoh.quietwords.design.wordsTitle
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.YearMonth

/**
 * Today's challenge in either language, the streak (one for both) and the calendar
 * (iOS `DailyChallengeView`). A day left halfway asks "Continue / Start over" first.
 */
@Composable
fun DailyScreen(app: AppModel, onBack: () -> Unit) {
    val colors = QuietTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val today = app.todayKey
    var month by rememberSaveable { mutableStateOf(YearMonth.from(DailyKey.date(today)!!).toString()) }
    /** A day left halfway: continue it or start over. */
    var unfinished by rememberSaveable { mutableStateOf<String?>(null) }

    fun play(dayKey: String) {
        if (app.isInProgress(dayKey, app.wordLanguage)) unfinished = dayKey else app.playDaily(dayKey)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()) {
        val regular = maxWidth >= 600.dp
        val landscape = regular && maxWidth > maxHeight
        QuietScreen(stringResource(R.string.daily_challenge_title), onBack = onBack, backDescription = stringResource(R.string.back)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val calendar: @Composable (Modifier) -> Unit = { modifier ->
                    CalendarCard(app, today, YearMonth.parse(month), { month = it.toString() }, regular, ::play, modifier)
                }
                if (landscape) {
                    // Tablet in landscape: today's card beside the calendar, so neither scrolls.
                    Row(Modifier.widthIn(max = 1100.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f)) { TodayCard(app, today, onPlay = { play(today) }) }
                        calendar(Modifier.weight(1.25f))
                    }
                } else {
                    Column(Modifier.widthIn(max = if (regular) 560.dp else 440.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TodayCard(app, today, onPlay = { play(today) })
                        calendar(Modifier)
                    }
                }
            }
        }
    }

    unfinished?.let { dayKey ->
        AlertDialog(
            onDismissRequest = { unfinished = null },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.continue_question), style = QuietType.headline, color = colors.ink) },
            text = { Text(stringResource(R.string.left_halfway, DayFormat.short(dayKey, locale)), style = QuietType.body, color = colors.muted) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { unfinished = null; app.playDaily(dayKey) }, modifier = Modifier.testTag("continueAttempt")) {
                        Text(stringResource(R.string.continue_), color = colors.primary, style = QuietType.headline)
                    }
                    TextButton(
                        onClick = { unfinished = null; app.startOver(dayKey); app.playDaily(dayKey) },
                        modifier = Modifier.testTag("startOverAttempt"),
                    ) {
                        Text(stringResource(R.string.start_over), color = colors.accent, style = QuietType.headline)
                    }
                    TextButton(onClick = { unfinished = null }) {
                        Text(stringResource(R.string.cancel), color = colors.muted, style = QuietType.headline)
                    }
                }
            },
        )
    }
}

/** Today's card: weekday, theme · level, the EN · ES pill with the board size, Play, the streak and the status. */
@Composable
private fun TodayCard(app: AppModel, today: String, onPlay: () -> Unit) {
    val colors = QuietTheme.colors
    val haptics = rememberHaptics()
    val locale = LocalConfiguration.current.locales[0]
    val level = DailyChallenge.level(today)
    val language = app.wordLanguage
    val done = app.doneLanguages(today)
    Column(
        Modifier.fillMaxWidth().background(colors.primary, RoundedCornerShape(26.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(DayFormat.weekday(today, locale), style = QuietType.subheadline, color = colors.onPrimary, modifier = Modifier.alpha(0.85f))
        Text(
            stringResource(R.string.pair, app.dailyTheme(today)?.localizedName() ?: "", level.title()),
            style = QuietType.title2.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Black), color = colors.onPrimary,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LanguagePill(language, app::setLanguage, done = done, onPrimary = true, onTick = { haptics.play(QuietHaptics.Event.SELECT) })
            val side = level.side
            Text(
                if (DailyChallenge.hasLongHidden(today)) stringResource(R.string.side_long_hidden, side, side) else stringResource(R.string.side, side, side),
                style = QuietType.footnote, color = colors.onPrimary, modifier = Modifier.alpha(0.85f),
            )
        }
        val title = when {
            app.isInProgress(today, language) -> R.string.continue_
            language in done -> R.string.play_again
            else -> R.string.play
        }
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 46.dp).background(colors.onPrimary, CircleShape)
                .quietClickable(enabled = !app.isPreparing, onClick = onPlay).testTag("playToday"),
            contentAlignment = Alignment.Center,
        ) {
            if (app.isPreparing) {
                CircularProgressIndicator(Modifier.size(22.dp), color = colors.primary, strokeWidth = 2.5.dp)
            } else {
                Text(stringResource(title), style = QuietType.headline, color = colors.primary)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.LocalFireDepartment, null, tint = colors.onPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(4.dp))
            val streak = app.currentStreak
            Text(
                androidx.compose.ui.res.pluralStringResource(R.plurals.streak_label, streak, streak), style = QuietType.subheadline,
                color = colors.onPrimary, modifier = Modifier.testTag("dailyStreak"),
            )
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.best_n, app.longestStreak), style = QuietType.subheadline, color = colors.onPrimary, modifier = Modifier.testTag("dailyBest"))
            // Signed in to Play Games: today's place among friends (time breaks ties, like iOS).
            var friendsRank by androidx.compose.runtime.remember { mutableStateOf<Int?>(null) }
            val signedIn = app.games.isAuthenticated
            LaunchedEffect(signedIn, language, app.demoActive) {
                friendsRank = when {
                    app.demoActive -> com.tavoohoh.quietwords.leaderboards.LeaderboardDemo.FRIENDS_RANK
                    !signedIn -> null
                    else -> runCatching {
                        val board = com.tavoohoh.quietwords.core.Leaderboard.daily(language)
                        val (_, top) = app.games.entries(app.leaderboardID(board), friendsOnly = true, daily = true)
                        com.tavoohoh.quiet.core.Leaderboards.today(top, today).firstOrNull { it.isLocalPlayer }?.rank
                    }.getOrNull()
                }
            }
            friendsRank?.let {
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.among_friends, it), style = QuietType.subheadline, color = colors.onPrimary, modifier = Modifier.testTag("friendsRank"))
            }
        }
        val record = app.bestOfDay(today, language)
        Text(
            if (record != null) stringResource(R.string.best_of_day, language.wordsTitle(), record.score)
            else stringResource(R.string.not_played_yet, language.wordsTitle()),
            style = QuietType.footnote, color = colors.onPrimary, modifier = Modifier.alpha(0.85f),
        )
    }
}

/** The month, the countdown to the next challenge and how the daily works. */
@Composable
private fun CalendarCard(
    app: AppModel, today: String, month: YearMonth, onMonth: (YearMonth) -> Unit, regular: Boolean,
    onSelect: (String) -> Unit, modifier: Modifier,
) {
    val colors = QuietTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier.fillMaxWidth().quietSurface(RoundedCornerShape(26.dp)).padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CalendarView(
            month = month, onMonth = onMonth, todayKey = today,
            completed = app.completedDays, late = app.lateDays, inProgress = app.daysInProgress,
            isPlayable = app::isPlayable, regular = regular, onSelect = onSelect,
        )
        var now by remember { mutableStateOf(AppClock.now()) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(30_000)
                now = AppClock.now()
            }
        }
        Text(
            stringResource(R.string.next_challenge_in, DayFormat.countdown(Duration.between(now, DailyKey.nextRollover(now)), locale)),
            style = QuietType.subheadline, color = colors.muted, modifier = Modifier.padding(top = 4.dp),
        )
        Text(stringResource(R.string.daily_explain), style = QuietType.footnote, color = colors.muted, textAlign = TextAlign.Center)
    }
}
