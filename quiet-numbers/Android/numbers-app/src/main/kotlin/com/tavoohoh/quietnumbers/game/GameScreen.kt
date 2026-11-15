package com.tavoohoh.quietnumbers.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.tavoohoh.quiet.core.FNV1a
import com.tavoohoh.quiet.ui.Fireworks
import com.tavoohoh.quiet.ui.QuietIcons
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quiet.ui.QuietType
import com.tavoohoh.quiet.ui.RoundIconButton
import com.tavoohoh.quiet.ui.quietClickable
import com.tavoohoh.quiet.ui.quietSurface
import com.tavoohoh.quietnumbers.core.GameKind
import com.tavoohoh.quietnumbers.R
import com.tavoohoh.quiet.ui.DayFormat
import com.tavoohoh.quietnumbers.design.LocalFeedback
import com.tavoohoh.quietnumbers.core.Board
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import kotlin.math.floor

/**
 * The game screen (port of iOS `GameView`): back and menu, the score with the stage (and best,
 * or the day's goal) and praise as a toast, the 1–9 strip, the board, and Undo · + · Hint
 * within thumb reach. The summary comes up from the bottom when the game ends.
 */
@Composable
fun GameScreen(
    model: GameModel,
    kind: GameKind,
    todayKey: String,
    /** Best classic score so far; null for daily challenges. */
    bestScore: Int?,
    /** The day was already played to the end: the summary says "Replay". */
    isReplay: Boolean = false,
    onBack: () -> Unit,
    onNewGame: () -> Unit,
    onHome: () -> Unit,
) {
    val colors = QuietTheme.colors
    val reduceMotion = QuietTheme.reduceMotion
    val haptics = com.tavoohoh.quiet.ui.rememberHaptics()
    val feedback = LocalFeedback.current
    SideEffect {
        model.reduceMotion = reduceMotion
        model.onFeedback = { feedback.play(it, haptics) }
    }
    val session = model.session
    val startingBest = remember(model) { bestScore }
    var celebrates by remember(model) { mutableStateOf(false) }
    var showsSummary by remember(model) { mutableStateOf(false) }

    // The clock: one tick a second while the game is on screen and the app in front.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(model) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                while (!model.session.isOver) {
                    delay(1000)
                    model.tick()
                }
            } finally {
                model.commitTime()
            }
        }
    }
    // The last match plays out first; a completed challenge gets fireworks, then the summary.
    LaunchedEffect(model, session.isOver) {
        if (!session.isOver) return@LaunchedEffect
        delay(500)
        if (session.isCompleted && !reduceMotion) {
            celebrates = true
            delay(1400)
        }
        showsSummary = true
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            val regular = maxWidth >= 600.dp
            val boardWidth = minOf(maxWidth - 48.dp, if (regular) 760.dp else 540.dp)
            val cellWidth = floor(boardWidth.value / 9).dp
            val cellHeight = maxOf(46.dp, cellWidth)
            Column(Modifier.widthIn(max = boardWidth + 48.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundIconButton(QuietIcons.Back, stringResource(R.string.back), onBack, Modifier.testTag("back"))
                    Spacer(Modifier.weight(1f))
                    if (kind == GameKind.Classic) GameMenu(onNewGame)
                }
                TopBar(model, subtitle(model, kind, todayKey, startingBest))
                DigitStrip(model.board, Modifier.padding(top = 14.dp))
                BoardView(model, cellWidth, cellHeight, Modifier.padding(top = 12.dp).weight(1f))
                ActionBar(model)
            }
        }
        // Stage reached after clearing a board.
        val banner = model.stageBanner
        AnimatedVisibility(banner != null, Modifier.align(Alignment.Center), enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut()) {
            var shown by remember { mutableStateOf(banner ?: 1) }
            if (banner != null) shown = banner
            Text(
                stringResource(R.string.stage_n, shown), style = QuietType.title, color = colors.ink,
                modifier = Modifier.shadow(12.dp, CircleShape).quietSurface(CircleShape).padding(horizontal = 28.dp, vertical = 14.dp),
            )
        }
        if (banner != null) LaunchedEffect(banner) {
            delay(1400)
            model.stageBanner = null
        }
        AnimatedVisibility(showsSummary, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
        }
        AnimatedVisibility(
            showsSummary, Modifier.align(Alignment.BottomCenter),
            enter = if (reduceMotion) fadeIn() else slideInVertically { it }, exit = if (reduceMotion) fadeOut() else slideOutVertically { it },
        ) {
            SummarySheet(
                session, kind,
                isNewBest = (startingBest ?: 0) > 0 && session.score > (startingBest ?: 0),
                isReplay = isReplay,
                onNewGame = onNewGame, onHome = onHome,
            )
        }
        if (celebrates) {
            val seed = when (kind) {
                GameKind.Classic -> 7uL
                is GameKind.Daily -> FNV1a.hash(kind.dayKey)
            }
            Fireworks(listOf(colors.primary, colors.accent, colors.suitRed, Color(0xFFE3B340), colors.ink.copy(alpha = 0.6f)), Modifier.fillMaxSize(), seed = seed)
        }
    }
    BackHandler(enabled = showsSummary) { onHome() }
}

@Composable
private fun subtitle(model: GameModel, kind: GameKind, todayKey: String, startingBest: Int?): String {
    val session = model.session
    val format = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    val goal = session.goal?.let {
        " · " + if (session.isCompleted) stringResource(R.string.goal_reached) else stringResource(R.string.goal_n, format.format(it))
    }.orEmpty()
    return when (kind) {
        GameKind.Classic ->
            stringResource(R.string.stage_n, session.stage) + " · " + stringResource(R.string.best_n, format.format(maxOf(startingBest ?: 0, model.displayedScore)))
        is GameKind.Daily ->
            // A past day says which; today's shows the stage.
            if (kind.dayKey == todayKey) stringResource(R.string.stage_n, session.stage) + goal
            else DayFormat.short(kind.dayKey, LocalConfiguration.current.locales[0]) + goal
    }
}

@Composable
private fun GameMenu(onNewGame: () -> Unit) {
    val colors = QuietTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        RoundIconButton(QuietIcons.More, stringResource(R.string.menu), { open = true }, Modifier.testTag("menu"), tint = colors.primary)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.surface) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.new_game), color = colors.ink) },
                leadingIcon = { Icon(Icons.Outlined.Refresh, null, tint = colors.ink) },
                onClick = { open = false; onNewGame() },
                modifier = Modifier.testTag("menuNewGame"),
            )
        }
    }
}

/** Score and stage, with praise as a toast on the right. */
@Composable
private fun TopBar(model: GameModel, subtitle: String) {
    val colors = QuietTheme.colors
    val scoreLabel = stringResource(R.string.score)
    val score = model.session.score
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 14.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    NumberFormat.getIntegerInstance().format(model.displayedScore), style = QuietType.score(44.sp), color = colors.ink,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = scoreLabel; stateDescription = "$score"; testTag = "score" },
                )
                ScoreChangeLabel(model, Modifier.padding(bottom = 6.dp))
            }
            Text(subtitle, style = QuietType.subheadline.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = QuietType.TABULAR), color = colors.muted, modifier = Modifier.testTag("subtitle"))
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.padding(bottom = 6.dp)) {
            model.praise?.let { praise -> androidx.compose.runtime.key(praise.id) { PraiseToast(praise) { model.dismissPraise(praise) } } }
        }
    }
}

/** "-3" (hints) or undone points next to the score; with Reduce Motion, match points too. */
@Composable
private fun ScoreChangeLabel(model: GameModel, modifier: Modifier) {
    val colors = QuietTheme.colors
    val change = model.scoreChange ?: return
    androidx.compose.runtime.key(change.id) {
        val rise = remember { Animatable(0f) }
        LaunchedEffect(change.id) {
            rise.animateTo(1f, tween(180))
            delay(900)
            model.dismissScoreChange(change)
        }
        Text(
            if (change.amount > 0) "+${change.amount}" else "${change.amount}",
            style = QuietType.title3.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = QuietType.TABULAR),
            color = if (change.amount > 0) colors.primary else colors.muted,
            modifier = modifier.alpha(rise.value).semantics { },
        )
    }
}

/** Praise next to the score: drops in, stays 1.2 s, fades. */
@Composable
private fun PraiseToast(praise: Praise, onDone: () -> Unit) {
    val colors = QuietTheme.colors
    val reduceMotion = QuietTheme.reduceMotion
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(praise.id) {
        delay(praise.delayMillis)
        alpha.animateTo(1f, if (reduceMotion) tween(0) else tween(180))
        delay(1200)
        alpha.animateTo(0f, if (reduceMotion) tween(0) else tween(200))
        onDone()
    }
    val text = when (praise.kind) {
        Praise.Kind.GOAL_REACHED -> stringResource(R.string.goal_reached_praise)
        Praise.Kind.NO_PAIRS -> stringResource(R.string.no_pairs_try_plus)
        Praise.Kind.TRIPLE_ROW -> stringResource(R.string.triple_row)
        Praise.Kind.DOUBLE_ROW -> stringResource(R.string.double_row)
        Praise.Kind.LONG_SHOT -> stringResource(R.string.long_shot)
        Praise.Kind.NO_MORE -> stringResource(R.string.no_more_digit, praise.value)
        Praise.Kind.IN_A_ROW -> stringResource(R.string.in_a_row, praise.value)
    }
    Text(
        text, style = QuietType.headline.copy(fontWeight = FontWeight.Black), color = colors.accent, maxLines = 1,
        modifier = Modifier
            .alpha(alpha.value)
            .scale(if (reduceMotion) 1f else 0.9f + 0.1f * alpha.value)
            .background(colors.accent.copy(alpha = 0.14f), CircleShape)
            .border(1.5.dp, colors.accent, CircleShape)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("praise"),
    )
}

/** 1 to 9 above the board; a digit with none left on it fades and is struck through. */
@Composable
private fun DigitStrip(board: Board, modifier: Modifier) {
    val colors = QuietTheme.colors
    val left = board.activeValues.toSet()
    val label = stringResource(R.string.numbers_left, (1..9).filter { it in left }.joinToString(", "))
    Row(modifier.widthIn(max = 360.dp).fillMaxWidth().padding(horizontal = 24.dp).clearAndSetSemantics { contentDescription = label }) {
        for (digit in 1..9) {
            val gone = digit !in left
            Text(
                "$digit", modifier = Modifier.weight(1f),
                style = QuietType.subheadline.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = QuietType.TABULAR, textDecoration = if (gone) TextDecoration.LineThrough else null),
                color = if (gone) colors.ghost else colors.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** Undo, + and hint, at the bottom within thumb reach. */
@Composable
private fun ActionBar(model: GameModel) {
    val session = model.session
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)) {
        CounterButton(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.undo), stringResource(R.string.undo), null, model.canUndo, false, 0, Modifier.weight(1f).testTag("undo")) { model.undo() }
        CounterButton(
            Icons.Rounded.Add, "${session.plusRemaining}", stringResource(R.string.add_numbers),
            pluralStringResource(R.plurals.remaining, session.plusRemaining, session.plusRemaining),
            model.canAddNumbers, model.needsNumbers, model.plusNudges, Modifier.weight(1f).testTag("plus"),
        ) { model.addNumbers() }
        CounterButton(
            Icons.Outlined.Lightbulb, "${-session.scoring.hintCost}", stringResource(R.string.hint),
            pluralStringResource(R.plurals.costs_points, session.scoring.hintCost, session.scoring.hintCost),
            model.canUseHint, false, 0, Modifier.weight(1f).testTag("hint"),
        ) { model.showHint() }
    }
}

@Composable
private fun CounterButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector, caption: String, label: String, value: String?,
    enabled: Boolean, highlighted: Boolean, nudges: Int, modifier: Modifier, onClick: () -> Unit,
) {
    val colors = QuietTheme.colors
    val bounce = remember { Animatable(1f) }
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(nudges) {
        if (nudges == 0) return@LaunchedEffect
        repeat(2) {
            bounce.animateTo(1.25f, tween(120))
            bounce.animateTo(1f, tween(140))
        }
    }
    LaunchedEffect(highlighted) {
        while (highlighted) {
            pulse.animateTo(0.4f, tween(500))
            pulse.animateTo(1f, tween(500))
        }
        pulse.snapTo(1f)
    }
    Column(
        modifier
            .defaultMinSize(minHeight = 56.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .quietClickable(enabled = enabled, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                if (value != null) stateDescription = value
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = colors.primary, modifier = Modifier.size(28.dp).scale(bounce.value).alpha(pulse.value))
        Text(caption, style = QuietType.subheadline.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = QuietType.TABULAR), color = colors.primary)
    }
}
