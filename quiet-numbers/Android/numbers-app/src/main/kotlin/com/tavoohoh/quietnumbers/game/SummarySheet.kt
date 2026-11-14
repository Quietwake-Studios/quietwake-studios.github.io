package com.tavoohoh.quietnumbers.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tavoohoh.quiet.ui.CapsuleButton
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quiet.ui.QuietType
import com.tavoohoh.quiet.ui.clock
import com.tavoohoh.quietnumbers.GameKind
import com.tavoohoh.quietnumbers.R
import com.tavoohoh.quietnumbers.core.GameSession
import java.text.NumberFormat

/**
 * End of a game (iOS `GameSummarySheet`): the score, how it went and, later, what it unlocked.
 * A sheet from the bottom, 520 dp at most (also on tablets, as on iPad).
 */
@Composable
fun SummarySheet(session: GameSession, kind: GameKind, isNewBest: Boolean, isReplay: Boolean = false, onNewGame: () -> Unit, onHome: () -> Unit) {
    val colors = QuietTheme.colors
    val n = NumberFormat.getIntegerInstance()
    Column(
        Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .shadow(30.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(colors.surface, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 28.dp, bottom = 12.dp)
            .testTag("summary"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(Modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(if (session.isCompleted) R.string.challenge_complete else R.string.no_moves_left), style = QuietType.headline, color = colors.muted)
            Text(n.format(session.score), style = QuietType.score(56.sp), color = colors.ink, modifier = Modifier.testTag("summaryScore"))
            val tag = when {
                isNewBest -> stringResource(R.string.new_best_score)
                isReplay -> stringResource(R.string.replay)
                else -> null
            }
            if (tag != null) {
                Text(
                    tag, style = QuietType.subheadline.copy(fontWeight = FontWeight.Black), color = colors.accent,
                    modifier = Modifier.padding(top = 4.dp).background(colors.accent.copy(alpha = 0.14f), CircleShape).padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }
        Column(Modifier.fillMaxWidth().background(colors.background, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp)) {
            val rows = buildList {
                session.goal?.let { add(stringResource(R.string.goal) to n.format(it)) }
                add(stringResource(R.string.time) to clock(session.elapsedSeconds))
                if (session.goal != null || kind == GameKind.Classic) add(stringResource(R.string.stage) to n.format(session.stage))
                add(stringResource(R.string.pairs) to n.format(session.stats.pairs))
                add(stringResource(R.string.rows_cleared) to n.format(session.stats.rowsCleared))
                add(stringResource(R.string.plus_used) to n.format(session.stats.plusUsed))
                add(stringResource(R.string.hints) to n.format(session.hintsUsed))
            }
            rows.forEachIndexed { i, (label, value) ->
                Row(Modifier.fillMaxWidth().height(40.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = QuietType.subheadline, color = colors.muted)
                    Spacer(Modifier.weight(1f))
                    Text(value, style = QuietType.subheadline.copy(fontWeight = FontWeight.Black, fontFeatureSettings = QuietType.TABULAR), color = colors.ink)
                }
                if (i != rows.lastIndex) HorizontalDivider(thickness = 1.dp, color = colors.separator)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (kind == GameKind.Classic) {
                CapsuleButton(stringResource(R.string.new_game), onNewGame, Modifier.testTag("summaryNewGame"))
                CapsuleButton(stringResource(R.string.home), onHome, Modifier.testTag("summaryHome"), prominent = false)
            } else {
                CapsuleButton(stringResource(R.string.done), onHome, Modifier.testTag("summaryDone"))
            }
        }
    }
}
