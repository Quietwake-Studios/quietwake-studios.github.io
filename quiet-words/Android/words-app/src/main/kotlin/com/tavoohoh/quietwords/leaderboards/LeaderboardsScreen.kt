package com.tavoohoh.quietwords.leaderboards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.tavoohoh.quietwords.AppModel
import com.tavoohoh.quietwords.R
import com.tavoohoh.quietwords.core.Leaderboard
import com.tavoohoh.quietwords.core.WordLanguage

/** Minimal leaderboards (iOS `LeaderboardsView`): pick a board, Global or Friends. Play Games does the ranking; today's board breaks ties by time (QuietKit's `LeaderboardContent`). */
@Composable
fun LeaderboardsScreen(app: AppModel, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val colors = QuietTheme.colors
    var tab by rememberSaveable { mutableStateOf(0) }
    var language by rememberSaveable { mutableStateOf(app.wordLanguage) }
    var friendsOnly by rememberSaveable { mutableStateOf(false) }
    val board = when (tab) {
        0 -> Leaderboard.daily(language)
        1 -> Leaderboard.bestHard(language)
        else -> Leaderboard.LONGEST_STREAK
    }
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
            Column(
                Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = if (regular) 560.dp else 480.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Segments(
                        tab, listOf(0 to stringResource(R.string.lb_today), 1 to stringResource(R.string.lb_best_hard), 2 to stringResource(R.string.lb_streak)),
                        { tab = it }, Modifier.testTag("leaderboardTabs"),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (tab != 2) {
                            Segments(
                                language, listOf(WordLanguage.EN to stringResource(R.string.english), WordLanguage.ES to stringResource(R.string.espanol)),
                                { language = it }, Modifier.weight(1f),
                            )
                        }
                        Segments(friendsOnly, scopeOptions(), { friendsOnly = it }, Modifier.weight(1f))
                    }
                    LeaderboardContent(signedIn, state, board.isDaily, onOpenSettings, Modifier.padding(top = 6.dp).weight(1f, fill = true))
                }
            }
        }
    }
}

/** Debug `demo`: the sample scores of iOS's `DemoData.leaderboard`. */
private fun demoScores(board: Leaderboard): List<Long> = when (board) {
    Leaderboard.DAILY_EN, Leaderboard.DAILY_ES -> listOf(1688, 1671, 1640, 1612, 1598, 1574, 1560, 1533, 1512, 1490)
    Leaderboard.BEST_HARD_EN, Leaderboard.BEST_HARD_ES -> listOf(2640, 2512, 2455, 2186, 2140, 2101, 2046, 1990, 1932, 1877)
    Leaderboard.LONGEST_STREAK -> listOf(41, 33, 27, 12, 11, 10, 9, 8, 7, 6)
}
