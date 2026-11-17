package com.tavoohoh.quietnumbers.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tavoohoh.quiet.core.DailyKey
import com.tavoohoh.quiet.ui.AccentButton
import com.tavoohoh.quiet.ui.CalendarDay
import com.tavoohoh.quiet.ui.CalendarView
import com.tavoohoh.quiet.ui.DayFormat
import com.tavoohoh.quiet.ui.QuietScreen
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quiet.ui.QuietType
import com.tavoohoh.quiet.ui.quietSurface
import com.tavoohoh.quietnumbers.AppClock
import com.tavoohoh.quietnumbers.AppModel
import com.tavoohoh.quietnumbers.R
import com.tavoohoh.quietnumbers.core.DailyChallenge
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.time.Duration
import java.time.YearMonth

/** Today's challenge, streaks and the calendar of past days (iOS `DailyChallengeView`). A day left halfway asks "Continue / Start over" first. */
@Composable
fun DailyScreen(app: AppModel, onBack: () -> Unit) {
    val colors = QuietTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val today = app.todayKey
    var month by rememberSaveable { mutableStateOf(YearMonth.from(DailyKey.date(today)!!).toString()) }
    var unfinished by rememberSaveable { mutableStateOf<String?>(null) }

    fun play(dayKey: String) {
        if (app.isDailyInProgress(dayKey)) unfinished = dayKey else app.playDaily(dayKey)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()) {
        val regular = maxWidth >= 600.dp
        val landscape = regular && maxWidth > maxHeight
        QuietScreen(stringResource(R.string.daily_challenge), onBack = onBack, backDescription = stringResource(R.string.back)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val calendar = @Composable { m: Modifier -> CalendarCard(app, today, YearMonth.parse(month), { month = it.toString() }, regular, ::play, m) }
                if (landscape) {
                    // Tablet in landscape: today's card beside the calendar (as in Quiet Words).
                    Row(Modifier.widthIn(max = 1100.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f)) { TodayCard(app, today) { play(today) } }
                        calendar(Modifier.weight(1.25f))
                    }
                } else {
                    Column(Modifier.widthIn(max = if (regular) 640.dp else 460.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TodayCard(app, today) { play(today) }
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
                    TextButton(onClick = { unfinished = null; app.startOver(dayKey); app.playDaily(dayKey) }, modifier = Modifier.testTag("startOverAttempt")) {
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

/** Today in `primary`: date, status, the play button and three small stats. */
@Composable
private fun TodayCard(app: AppModel, today: String, onPlay: () -> Unit) {
    val colors = QuietTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val n = NumberFormat.getIntegerInstance(locale)
    val record = app.dailies[today]
    val inProgress = app.isDailyInProgress(today)
    val status = when {
        inProgress -> stringResource(R.string.in_progress)
        record == null -> stringResource(R.string.goal_points, n.format(DailyChallenge.goal(today)))
        record.isCompleted -> stringResource(R.string.completed_score, n.format(record.score))
        else -> stringResource(R.string.played_score, n.format(record.score))
    }
    val playTitle = stringResource(
        when {
            inProgress -> R.string.continue_
            app.hasPlayed(today) -> R.string.play_again
            else -> R.string.play
        },
    )
    Column(
        Modifier.fillMaxWidth().background(colors.primary, RoundedCornerShape(26.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(DayFormat.weekday(today, locale), style = QuietType.subheadline, color = colors.onPrimary, modifier = Modifier.alpha(0.85f))
                Text(status, style = QuietType.title3, color = colors.onPrimary, modifier = Modifier.testTag("todayStatus"))
            }
            AccentButton(playTitle, onPlay, Modifier.testTag("playToday"))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(n.format(app.currentStreak), stringResource(R.string.streak), true, Modifier.weight(1f).testTag("dailyStreak"))
            StatTile(n.format(app.longestStreak), stringResource(R.string.best_streak), false, Modifier.weight(1f))
            // Today's place among Play Games friends (time breaks ties, like iOS); "–" signed out or not on the board.
            val friendsRank = com.tavoohoh.quiet.ui.rememberFriendsRank(
                app.games, app.leaderboardID(com.tavoohoh.quietnumbers.core.Leaderboard.DAILY), today,
                demoRank = if (app.demoActive) com.tavoohoh.quiet.core.LeaderboardDemo.FRIENDS_RANK else null,
            )
            StatTile(friendsRank?.let { "#${n.format(it)}" } ?: "–", stringResource(R.string.friends), false, Modifier.weight(1f).testTag("friendsRank"))
        }
        if (app.hasPlayed(today)) {
            Text(stringResource(R.string.replay_note), style = QuietType.footnote, color = colors.onPrimary, modifier = Modifier.alpha(0.85f))
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, flame: Boolean, modifier: Modifier) {
    val colors = QuietTheme.colors
    Column(
        modifier.background(colors.onPrimary.copy(alpha = 0.12f), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (flame) Icon(Icons.Rounded.LocalFireDepartment, null, tint = colors.onPrimary.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
            Text(value, style = QuietType.stat, color = colors.onPrimary, maxLines = 1)
        }
        Text(label, style = QuietType.footnote, color = colors.onPrimary, maxLines = 1, modifier = Modifier.alpha(0.85f))
    }
}

/** The month, the countdown to the next challenge and the note about past days. */
@Composable
private fun CalendarCard(
    app: AppModel, today: String, month: YearMonth, onMonth: (YearMonth) -> Unit, regular: Boolean,
    onSelect: (String) -> Unit, modifier: Modifier,
) {
    val colors = QuietTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val dailies = app.dailies
    val practiced = app.progress.practicedDays
    val inProgress = app.daysInProgress
    Column(
        modifier.fillMaxWidth().quietSurface(RoundedCornerShape(26.dp)).padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CalendarView(
            month = month, onMonth = onMonth, todayKey = today,
            day = { k ->
                val r = dailies[k]
                CalendarDay(isCompleted = r?.isCompleted == true, isLate = k in practiced, isPlayed = r?.isFinished == true, isInProgress = k in inProgress)
            },
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
        Text(stringResource(R.string.past_days_note), style = QuietType.footnote, color = colors.muted, textAlign = TextAlign.Center)
        Spacer(Modifier.size(2.dp))
    }
}
