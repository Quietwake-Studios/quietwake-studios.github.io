package com.tavoohoh.quietwords.leaderboards

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import com.tavoohoh.quiet.ui.CapsuleButton
import com.tavoohoh.quiet.ui.QuietScreen
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quiet.ui.QuietType
import com.tavoohoh.quiet.ui.Segments
import com.tavoohoh.quiet.ui.quietSurface
import com.tavoohoh.quietwords.AppModel
import com.tavoohoh.quietwords.R
import com.tavoohoh.quietwords.core.Leaderboard
import com.tavoohoh.quietwords.core.WordLanguage
import com.tavoohoh.quietwords.design.clock
import java.text.NumberFormat

/** Minimal leaderboards (iOS `LeaderboardsView`): pick a board, Global or Friends. Play Games does the ranking; today's board breaks ties by time. */
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
    var state by remember { mutableStateOf<LoadState>(LoadState.Loading) }
    LaunchedEffect(board, friendsOnly, signedIn) {
        if (!signedIn) return@LaunchedEffect
        if (app.demoActive) {
            state = LoadState.Loaded(LeaderboardDemo.entries(board, friendsOnly))
            return@LaunchedEffect
        }
        state = LoadState.Loading
        state = runCatching {
            val (local, top) = app.games.entries(app.leaderboardID(board), friendsOnly, daily = board.isDaily)
            if (board.isDaily) {
                val today = Leaderboards.today(top, app.todayKey)
                LoadState.Loaded(today, today.firstOrNull { it.isLocalPlayer } ?: local?.takeIf { ScoreTag.day(it.tag) == app.todayKey })
            } else {
                LoadState.Loaded(top, local)
            }
        }.getOrElse { LoadState.Failed }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()) {
        val regular = maxWidth >= 600.dp
        QuietScreen(stringResource(R.string.leaderboards), onBack = onBack, backDescription = stringResource(R.string.back)) {
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
                        Segments(friendsOnly, listOf(false to stringResource(R.string.lb_global), true to stringResource(R.string.lb_friends)), { friendsOnly = it }, Modifier.weight(1f))
                    }
                    Box(Modifier.padding(top = 6.dp).weight(1f, fill = true)) {
                        if (!signedIn) {
                            SignedOutCard(onOpenSettings)
                        } else {
                            when (val s = state) {
                                LoadState.Loading -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
                                    CircularProgressIndicator(color = colors.primary)
                                }
                                LoadState.Failed -> Message(Icons.Outlined.WifiOff, stringResource(R.string.couldnt_load), stringResource(R.string.check_connection))
                                is LoadState.Loaded -> if (s.top.isEmpty()) {
                                    Message(Icons.Outlined.EmojiEvents, stringResource(R.string.no_scores), null)
                                } else {
                                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        EntryList(s.top, showsTime = board.isDaily)
                                        val local = s.local
                                        if (local != null && s.top.none { it.isLocalPlayer }) EntryList(listOf(local), showsTime = board.isDaily)
                                        Text(
                                            stringResource(R.string.scores_from), style = QuietType.footnote, color = colors.muted,
                                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed interface LoadState {
    data object Loading : LoadState
    data class Loaded(val top: List<LeaderboardEntry>, val local: LeaderboardEntry? = top.firstOrNull { it.isLocalPlayer }) : LoadState
    data object Failed : LoadState
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
            Text(stringResource(R.string.play_with_friends), style = QuietType.title3, color = colors.ink)
            Text(stringResource(R.string.sign_in_to_see), style = QuietType.subheadline, color = colors.muted, textAlign = TextAlign.Center)
            CapsuleButton(stringResource(R.string.open_settings), onOpenSettings, Modifier.padding(top = 6.dp).testTag("openSettings"))
        }
    }
}

@Composable
private fun Message(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String?) {
    val colors = QuietTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = colors.muted, modifier = Modifier.size(40.dp))
        Text(title, style = QuietType.headline, color = colors.ink)
        if (text != null) Text(text, style = QuietType.subheadline, color = colors.muted, textAlign = TextAlign.Center)
    }
}

/** Debug `demo`: the sample boards of iOS's `DemoData.leaderboard` (names from the Quiet Numbers canvas). */
object LeaderboardDemo {
    private val names = listOf("juli", "tatita12", "vale", "guss", "juanca", "cori16", "eliasb", "mariadelsea", "aleja", "nova47")
    private val friends = setOf("vale", "guss", "juanca", "cori16")
    private val seconds = listOf(214, 236, 251, 268, 240, 290, 311, 284, 330, 356)

    fun entries(board: Leaderboard, friendsOnly: Boolean): List<LeaderboardEntry> {
        val scores: List<Long> = when (board) {
            Leaderboard.DAILY_EN, Leaderboard.DAILY_ES -> listOf(1688, 1671, 1640, 1612, 1598, 1574, 1560, 1533, 1512, 1490)
            Leaderboard.BEST_HARD_EN, Leaderboard.BEST_HARD_ES -> listOf(2640, 2512, 2455, 2186, 2140, 2101, 2046, 1990, 1932, 1877)
            Leaderboard.LONGEST_STREAK -> listOf(41, 33, 27, 12, 11, 10, 9, 8, 7, 6)
        }
        val rows = names.indices.filter { !friendsOnly || names[it] in friends }
        return rows.mapIndexed { rank, i ->
            LeaderboardEntry(
                names[i], rank + 1, names[i], scores[i], names[i] == "guss",
                if (board.isDaily) ScoreTag.daily("demo", seconds[i]) else "",
            )
        }
    }

    /** "#N among friends" on the Daily screen. */
    const val FRIENDS_RANK = 2
}

