package com.tavoohoh.quiet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tavoohoh.quiet.core.FNV1a
import com.tavoohoh.quiet.core.LeaderboardEntry
import com.tavoohoh.quiet.core.Leaderboards
import com.tavoohoh.quiet.core.ScoreTag
import java.text.NumberFormat

/*
 * The family's minimal leaderboards (iOS `LeaderboardsView`): a page of Play Games' board with
 * medals, an avatar initial and the player's row highlighted; a daily board shows each time and
 * only today's results, ranked by score then time; signed out, faded rows behind "Play with
 * friends". Each game picks its boards with its own Segments above `LeaderboardContent`.
 */

sealed interface LeaderboardLoad {
    data object Loading : LeaderboardLoad
    data class Loaded(val top: List<LeaderboardEntry>, val local: LeaderboardEntry? = top.firstOrNull { it.isLocalPlayer }) : LeaderboardLoad
    data object Failed : LeaderboardLoad
}

/** One board from Play Games; a daily one keeps only today's results (by the tag's day) and breaks ties by time. */
suspend fun loadLeaderboard(games: PlayGamesService, id: String, friendsOnly: Boolean, daily: Boolean, todayKey: String): LeaderboardLoad =
    runCatching {
        val (local, top) = games.entries(id, friendsOnly, daily)
        if (daily) {
            val today = Leaderboards.today(top, todayKey)
            LeaderboardLoad.Loaded(today, today.firstOrNull { it.isLocalPlayer } ?: local?.takeIf { ScoreTag.day(it.tag) == todayKey })
        } else {
            LeaderboardLoad.Loaded(top, local)
        }
    }.getOrElse { LeaderboardLoad.Failed }

/**
 * The board under the pickers: signed out, the "Play with friends" card; otherwise loading, the
 * rows (plus the player's own row if it's off the page) and the source note. `tieBreakNote`
 * adds "Same score: the faster time ranks higher." under a daily board (Quiet Numbers).
 */
@Composable
fun LeaderboardContent(
    signedIn: Boolean, state: LeaderboardLoad, daily: Boolean, onOpenSettings: () -> Unit, modifier: Modifier = Modifier,
    tieBreakNote: Boolean = false,
) {
    val colors = QuietTheme.colors
    Box(modifier) {
        if (!signedIn) {
            SignedOutCard(onOpenSettings)
        } else when (state) {
            LeaderboardLoad.Loading -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
                CircularProgressIndicator(color = colors.primary)
            }
            LeaderboardLoad.Failed -> Message(Icons.Outlined.WifiOff, stringResource(R.string.quiet_couldnt_load), stringResource(R.string.quiet_check_connection))
            is LeaderboardLoad.Loaded -> if (state.top.isEmpty()) {
                Message(Icons.Outlined.EmojiEvents, stringResource(R.string.quiet_no_scores), null)
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    EntryList(state.top, showsTime = daily)
                    val local = state.local
                    if (local != null && state.top.none { it.isLocalPlayer }) EntryList(listOf(local), showsTime = daily)
                    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.quiet_scores_from), style = QuietType.footnote, color = colors.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        if (daily && tieBreakNote) {
                            Text(stringResource(R.string.quiet_same_score_faster), style = QuietType.footnote, color = colors.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryList(entries: List<LeaderboardEntry>, showsTime: Boolean) {
    Column(Modifier.fillMaxWidth().quietSurface(RoundedCornerShape(24.dp)).padding(6.dp)) {
        for (entry in entries) EntryRow(entry, showsTime)
    }
}

@Composable
private fun EntryRow(entry: LeaderboardEntry, showsTime: Boolean) {
    val colors = QuietTheme.colors
    val medal = when (entry.rank) {
        1 -> colors.accent to colors.onAccent
        2 -> colors.primary to colors.onPrimary
        3 -> colors.muted to colors.background
        else -> null
    }
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 54.dp)
            .background(if (entry.isLocalPlayer) colors.primary.copy(alpha = 0.16f) else Color.Transparent, RoundedCornerShape(16.dp))
            .semantics(mergeDescendants = true) {}.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(28.dp).background(medal?.first ?: Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
            Text("${entry.rank}", style = QuietType.subheadline.copy(fontWeight = FontWeight.Black, fontFeatureSettings = QuietType.TABULAR), color = medal?.second ?: colors.muted)
        }
        Box(Modifier.size(34.dp).background(avatarColor(entry.name), CircleShape), contentAlignment = Alignment.Center) {
            Text(entry.name.take(1).uppercase(), style = QuietType.subheadline.copy(fontWeight = FontWeight.Black), color = Color.White)
        }
        Text(
            entry.name, style = QuietType.body.copy(fontWeight = if (entry.isLocalPlayer) FontWeight.Black else FontWeight.Normal),
            color = if (entry.isLocalPlayer) colors.primary else colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (showsTime) ScoreTag.seconds(entry.tag)?.let {
            Text(clock(it), style = QuietType.subheadline.copy(fontFeatureSettings = QuietType.TABULAR), color = colors.muted)
        }
        Text(
            NumberFormat.getIntegerInstance().format(entry.score),
            style = QuietType.title3.copy(fontWeight = if (entry.isLocalPlayer) FontWeight.Black else FontWeight.Medium, fontFeatureSettings = QuietType.TABULAR),
            color = if (entry.isLocalPlayer) colors.primary else colors.ink,
        )
    }
}

/** A stable, muted hue per name (FNV-1a, like iOS). */
private fun avatarColor(name: String): Color {
    val hue = (FNV1a.hash(name) % 360u).toFloat()
    return Color.hsv(hue, 0.45f, 0.55f)
}

/** Signed out: faded placeholder rows behind a card that points to Settings. */
@Composable
private fun SignedOutCard(onOpenSettings: () -> Unit) {
    val colors = QuietTheme.colors
    Box(Modifier.fillMaxWidth().quietSurface(RoundedCornerShape(24.dp))) {
        Column(Modifier.padding(6.dp).alpha(0.6f)) {
            for (width in listOf(110, 80, 130, 90, 120, 70, 100, 85)) {
                Row(Modifier.height(50.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(28.dp).background(colors.surface2, CircleShape))
                    Box(Modifier.size(34.dp).background(colors.surface2, CircleShape))
                    Box(Modifier.width(width.dp).height(12.dp).background(colors.surface2, CircleShape))
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.width(36.dp).height(12.dp).background(colors.surface2, CircleShape))
                }
            }
        }
        Column(
            Modifier.padding(horizontal = 16.dp).padding(top = 110.dp).fillMaxWidth()
                .shadow(20.dp, RoundedCornerShape(24.dp)).background(colors.background, RoundedCornerShape(24.dp))
                .padding(horizontal = 22.dp, vertical = 26.dp).testTag("signedOutCard"),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(56.dp).background(colors.primary.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.EmojiEvents, null, tint = colors.primary, modifier = Modifier.size(28.dp))
            }
            Text(stringResource(R.string.quiet_play_with_friends), style = QuietType.title3, color = colors.ink)
            Text(stringResource(R.string.quiet_sign_in_to_see), style = QuietType.subheadline, color = colors.muted, textAlign = TextAlign.Center)
            CapsuleButton(stringResource(R.string.quiet_open_settings), onOpenSettings, Modifier.padding(top = 6.dp).testTag("openSettings"))
        }
    }
}

@Composable
private fun Message(icon: ImageVector, title: String, text: String?) {
    val colors = QuietTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = colors.muted, modifier = Modifier.size(40.dp))
        Text(title, style = QuietType.headline, color = colors.ink)
        if (text != null) Text(text, style = QuietType.subheadline, color = colors.muted, textAlign = TextAlign.Center)
    }
}

/**
 * Today's place among Play Games friends on the Daily screen (time breaks ties, like iOS); null
 * when signed out or not on today's board. `demoRank` stands in for the debug demo.
 */
@Composable
fun rememberFriendsRank(games: PlayGamesService, leaderboardID: String, todayKey: String, demoRank: Int? = null): Int? {
    var rank by remember { mutableStateOf<Int?>(null) }
    val signedIn = games.isAuthenticated
    LaunchedEffect(signedIn, leaderboardID, todayKey, demoRank) {
        rank = when {
            demoRank != null -> demoRank
            !signedIn -> null
            else -> runCatching {
                val (_, top) = games.entries(leaderboardID, friendsOnly = true, daily = true)
                Leaderboards.today(top, todayKey).firstOrNull { it.isLocalPlayer }?.rank
            }.getOrNull()
        }
    }
    return rank
}

/** "#N among friends". */
@Composable
fun amongFriends(rank: Int): String = stringResource(R.string.quiet_among_friends, rank)

/** Settings › Account › "Play Games": signed in as… / not signed in; a tap signs in or shows Play's achievements. */
@Composable
fun PlayGamesRow(games: PlayGamesService, demoName: String? = null) {
    val name = demoName ?: games.playerName
    GroupRow(
        stringResource(R.string.quiet_play_games),
        if (name != null) stringResource(R.string.quiet_signed_in_as, name) else stringResource(R.string.quiet_not_signed_in),
        Modifier.testTag("playGames"),
    ) { if (games.isAuthenticated) games.showAchievements() else games.signIn() }
}

/** "Leaderboards" (the screen's title, the Home trophy's label). */
@Composable
fun leaderboardsTitle(): String = stringResource(R.string.quiet_leaderboards)

/** Global · Friends. */
@Composable
fun scopeOptions(): List<Pair<Boolean, String>> =
    listOf(false to stringResource(R.string.quiet_lb_global), true to stringResource(R.string.quiet_lb_friends))
