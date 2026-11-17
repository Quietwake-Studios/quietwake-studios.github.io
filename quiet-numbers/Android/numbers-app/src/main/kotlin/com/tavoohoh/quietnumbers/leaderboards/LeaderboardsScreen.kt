package com.tavoohoh.quietnumbers.leaderboards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tavoohoh.quiet.core.LeaderboardDemo
import com.tavoohoh.quiet.ui.LeaderboardContent
import com.tavoohoh.quiet.ui.LeaderboardLoad
import com.tavoohoh.quiet.ui.QuietScreen
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quiet.ui.Segments
import com.tavoohoh.quiet.ui.leaderboardsTitle
import com.tavoohoh.quiet.ui.loadLeaderboard
import com.tavoohoh.quiet.ui.scopeOptions
import com.tavoohoh.quietnumbers.AppModel
import com.tavoohoh.quietnumbers.R
import com.tavoohoh.quietnumbers.core.Leaderboard

/**
 * Leaderboards (iOS `LeaderboardsView`): Today · Best score · Streak, Global · Friends. Play
 * Games does the ranking; today's board keeps only today's results and breaks ties by time
 * (QuietKit's `LeaderboardContent`). Signed out: "Play with friends" → Settings.
 */
@Composable
fun LeaderboardsScreen(app: AppModel, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val colors = QuietTheme.colors
    var board by rememberSaveable { mutableStateOf(Leaderboard.DAILY) }
    var friendsOnly by rememberSaveable { mutableStateOf(false) }
    val signedIn = app.demoActive || app.games.isAuthenticated
    var state by remember { mutableStateOf<LeaderboardLoad>(LeaderboardLoad.Loading) }
    LaunchedEffect(board, friendsOnly, signedIn) {
        if (!signedIn) return@LaunchedEffect
        if (app.demoActive) {
            state = LeaderboardLoad.Loaded(LeaderboardDemo.entries(demoScores(board), board.isDaily, friendsOnly))
            return@LaunchedEffect
        }
        state = LeaderboardLoad.Loading
        state = loadLeaderboard(app.games, app.leaderboardID(board), friendsOnly, board.isDaily, app.todayKey)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()) {
        val regular = maxWidth >= 600.dp
        QuietScreen(leaderboardsTitle(), onBack = onBack, backDescription = stringResource(R.string.back)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = if (regular) 680.dp else 480.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Segments(
                        board,
                        listOf(
                            Leaderboard.DAILY to stringResource(R.string.lb_today),
                            Leaderboard.BEST_SCORE to stringResource(R.string.best_score),
                            Leaderboard.LONGEST_STREAK to stringResource(R.string.streak),
                        ),
                        { board = it }, Modifier.testTag("leaderboardTabs"),
                    )
                    Segments(friendsOnly, scopeOptions(), { friendsOnly = it })
                    LeaderboardContent(signedIn, state, board.isDaily, onOpenSettings, Modifier.padding(top = 6.dp).weight(1f, fill = true), tieBreakNote = true)
                }
            }
        }
    }
}

/** Debug `demo`: iOS's `DemoData.leaderboard` scores. */
private fun demoScores(board: Leaderboard): List<Long> = when (board) {
    Leaderboard.DAILY -> listOf(412, 398, 377, 361, 340, 336, 333, 318, 296, 281)
    Leaderboard.BEST_SCORE -> listOf(6120, 5480, 4905, 4210, 3870, 3215, 2990, 2480, 2310, 1995)
    Leaderboard.LONGEST_STREAK -> listOf(212, 148, 96, 71, 64, 40, 33, 21, 12, 9)
}
