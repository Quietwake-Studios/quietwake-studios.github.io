package com.tavoohoh.quietwords.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tavoohoh.quiet.ui.CapsuleButton
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quiet.ui.QuietType
import com.tavoohoh.quietwords.GameKind
import com.tavoohoh.quietwords.R
import com.tavoohoh.quietwords.core.GameSession
import com.tavoohoh.quietwords.design.clock

/**
 * End of a game: score, the hidden word, the stats and the words. A bottom sheet on phones,
 * a centred card on tablets; drawn in the game screen's window (not a dialog), so the
 * daily's fireworks can go over it.
 */
@Composable
fun SummarySheet(session: GameSession, kind: GameKind, themeName: String, card: Boolean, onNewGame: () -> Unit, onHome: () -> Unit) {
    val colors = QuietTheme.colors
    val content = @Composable {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (kind is GameKind.Daily) R.string.summary_daily else R.string.summary_free),
                style = QuietType.headline, color = colors.muted, modifier = Modifier.padding(top = 28.dp),
            )
            Text("${session.score}", style = QuietType.score(56.sp), color = colors.ink, modifier = Modifier.testTag("summaryScore"))
            Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HiddenSlots(
                    session.board.hidden.length, session.board.hidden,
                    if (session.hiddenState == GameSession.HiddenState.GUESSED) SlotState.GUESSED else SlotState.REVEALED, 36.dp,
                )
                Text(stringResource(R.string.leftover_letters), style = QuietType.footnote, color = colors.muted)
            }
            Stats(session)
            Words(session)
            Buttons(kind, onNewGame, onHome, Modifier.padding(top = 4.dp))
        }
    }
    val scrim = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f))
    if (card) {
        // Tablet: a centred card, like the iPad's form sheet.
        Box(scrim.safeDrawingPadding().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(32.dp))
                    .verticalScroll(rememberScrollState())
                    .testTag("summary"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { content() }
        }
    } else {
        // Phone: a sheet from the bottom.
        Box(scrim, contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Spacer(Modifier.height(36.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(colors.surface, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .testTag("summary"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { content() }
            }
        }
    }
}

@Composable
private fun Stats(session: GameSession) {
    val colors = QuietTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.background, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 2.dp)) {
        StatRow(stringResource(R.string.words), "${session.found.size}/${session.board.placements.size} · ${session.wordPoints}", first = true)
        StatRow(stringResource(R.string.time), clock(session.elapsedSeconds))
        StatRow(stringResource(R.string.time_bonus), "+${session.timeBonus ?: 0}")
        StatRow(
            stringResource(R.string.hidden_word),
            if (session.hiddenState == GameSession.HiddenState.GUESSED) stringResource(R.string.summary_hidden_guessed, session.scoring.hiddenGuessed)
            else stringResource(R.string.summary_hidden_revealed, session.scoring.hiddenRevealed),
        )
        if (session.hintsUsed > 0) StatRow(stringResource(R.string.hints), "${session.hintsUsed} · −${session.hintCost}")
    }
}

@Composable
private fun StatRow(label: String, value: String, first: Boolean = false) {
    val colors = QuietTheme.colors
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        if (!first) HorizontalDivider(thickness = 1.dp, color = colors.separator.copy(alpha = 0.6f))
        Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 38.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = QuietType.subheadline, color = colors.muted)
            Spacer(Modifier.weight(1f))
            Text(value, style = QuietType.subheadline.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = QuietType.TABULAR), color = colors.ink)
        }
    }
}

/** The board's words, as written. (iOS opens their definitions; Android has no system dictionary sheet.) */
@Composable
private fun Words(session: GameSession) {
    val colors = QuietTheme.colors
    Column(
        Modifier.fillMaxWidth().background(colors.background, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.words).uppercase(), style = QuietType.caption.copy(fontWeight = FontWeight.Bold), color = colors.muted)
        BoxWithConstraints {
            val columns = maxOf(1, (maxWidth / 130.dp).toInt())
            val words = session.board.placements.map { it.word.text }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (start in words.indices step columns) {
                    Row {
                        for (i in start until start + columns) {
                            Box(Modifier.weight(1f)) {
                                if (i < words.size) Text(words[i], style = QuietType.subheadline, color = colors.ink)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Buttons(kind: GameKind, onNewGame: () -> Unit, onHome: () -> Unit, modifier: Modifier) {
    when (kind) {
        GameKind.Free -> Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CapsuleButton(stringResource(R.string.home), onHome, Modifier.weight(1f).testTag("summaryHome"), prominent = false)
            CapsuleButton(stringResource(R.string.new_game), onNewGame, Modifier.weight(1f).testTag("summaryNewGame"))
        }
        is GameKind.Daily -> CapsuleButton(stringResource(R.string.done), onHome, modifier.testTag("summaryDone"))
    }
}
