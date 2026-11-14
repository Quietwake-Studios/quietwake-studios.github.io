package com.tavoohoh.quietwords.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.tavoohoh.quiet.ui.CapsuleButton
import com.tavoohoh.quiet.ui.Fireworks
import com.tavoohoh.quiet.ui.QuietIcons
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quiet.ui.QuietType
import com.tavoohoh.quiet.ui.RoundIconButton
import com.tavoohoh.quiet.ui.rememberHaptics
import com.tavoohoh.quietwords.core.GameKind
import com.tavoohoh.quietwords.R
import com.tavoohoh.quietwords.design.clock
import com.tavoohoh.quietwords.design.play
import com.tavoohoh.quietwords.design.title
import kotlinx.coroutines.delay

/**
 * The game screen. Phone: board, hidden word, list below, actions at the bottom. Tablet
 * (600 dp and wider, landscape-ish): the list and controls beside the board, as on iPad.
 */
@Composable
fun GameScreen(
    model: GameModel,
    kind: GameKind,
    themeName: String,
    onBack: () -> Unit,
    onNewGame: () -> Unit,
    onRestart: () -> Unit,
    onHome: () -> Unit,
) {
    val colors = QuietTheme.colors
    val haptics = rememberHaptics()
    val reduceMotion = QuietTheme.reduceMotion
    SideEffect {
        model.onFeedback = { haptics.play(it) }
        model.reduceMotion = reduceMotion
    }
    // The clock runs while the app is in front.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(model) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(1000)
                model.tick()
            }
        }
    }
    var guessing by remember { mutableStateOf(false) }
    var confirmsRestart by remember { mutableStateOf(false) }
    val session = model.state

    val parts = GameParts(
        model = model, kind = kind, themeName = themeName, onBack = onBack, onNewGame = onNewGame,
        onRestartRequest = { confirmsRestart = true }, onGuess = { guessing = true },
    )

    // Tablet (the iPad rule): the list beside the board, and the summary as a centred card.
    var wide by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(colors.background)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val regular = maxWidth >= 600.dp
            val isWide = regular && (maxWidth > maxHeight * 0.9f || maxWidth > 900.dp)
            SideEffect { wide = isWide }
            if (isWide) {
                parts.Wide(maxWidth, maxHeight)
            } else {
                parts.Compact(maxWidth, maxHeight, regular)
            }
        }
        AnimatedVisibility(
            model.showsSummary,
            enter = when {
                reduceMotion -> fadeIn()
                wide -> fadeIn() + scaleIn(initialScale = 0.96f)
                else -> slideInVertically { it } + fadeIn()
            },
            exit = fadeOut(),
        ) {
            SummarySheet(session, kind, themeName, card = wide, onNewGame = onNewGame, onHome = onHome)
        }
        if (model.showsSummary && kind is GameKind.Daily) {
            Fireworks(listOf(colors.primary, colors.accent, colors.suitRed, androidx.compose.ui.graphics.Color(0xFFF2BF4D), colors.ink), Modifier.fillMaxSize())
        }
    }
    BackHandler(enabled = model.showsSummary) { onHome() }

    if (guessing) GuessSheet(model) { guessing = false }
    if (confirmsRestart) {
        AlertDialog(
            onDismissRequest = { confirmsRestart = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.start_over_question), style = QuietType.headline, color = colors.ink) },
            confirmButton = {
                TextButton(onClick = { confirmsRestart = false; onRestart() }) {
                    Text(stringResource(R.string.start_over), color = colors.accent, style = QuietType.headline)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmsRestart = false }) {
                    Text(stringResource(R.string.cancel), color = colors.primary, style = QuietType.headline)
                }
            },
        )
    }
}

private class GameParts(
    val model: GameModel,
    val kind: GameKind,
    val themeName: String,
    val onBack: () -> Unit,
    val onNewGame: () -> Unit,
    val onRestartRequest: () -> Unit,
    val onGuess: () -> Unit,
) {
    @Composable
    fun Compact(width: Dp, height: Dp, regular: Boolean) {
        val boardSize = min(min(width - 32.dp, if (regular) 760.dp else 540.dp), height * 0.48f)
        val session = model.state
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                BackButton()
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Score()
                    Context()
                }
                Menu()
            }
            BoardView(model, boardSize, Modifier.padding(top = 12.dp))
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HiddenWordRow(session)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    WordsHeader(session)
                    WordList(session)
                }
            }
            Actions(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 8.dp))
        }
    }

    @Composable
    fun Wide(width: Dp, height: Dp) {
        val boardSize = min(min(height - 80.dp, width * 0.58f), 680.dp)
        val session = model.state
        Row(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BoardView(model, boardSize)
            Column(Modifier.widthIn(max = 420.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    BackButton()
                    Spacer(Modifier.weight(1f))
                    Menu()
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Score()
                    Context()
                }
                HiddenWordRow(session, size = 30.dp, stacked = session.board.hidden.length > 6)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WordsHeader(session)
                    WordList(session, columns = 2)
                }
                Actions()
            }
        }
    }

    @Composable
    fun BackButton() {
        RoundIconButton(QuietIcons.Back, stringResource(R.string.back), onBack, Modifier.testTag("back"))
    }

    @Composable
    fun Menu() {
        val colors = QuietTheme.colors
        var open by remember { mutableStateOf(false) }
        Box {
            RoundIconButton(QuietIcons.More, stringResource(R.string.menu), { open = true }, Modifier.testTag("menu"))
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.surface) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.start_over), color = colors.ink) },
                    leadingIcon = { Icon(Icons.Outlined.Replay, null, tint = colors.ink) },
                    onClick = { open = false; onRestartRequest() },
                )
                if (kind == GameKind.Free) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.new_game), color = colors.ink) },
                        leadingIcon = { Icon(Icons.Outlined.Add, null, tint = colors.ink) },
                        onClick = { open = false; onNewGame() },
                    )
                }
            }
        }
    }

    @Composable
    fun Score() {
        val score = model.state.score
        val label = stringResource(R.string.score_a11y, score)
        Text(
            "$score", style = QuietType.score(40.sp), color = QuietTheme.colors.ink,
            modifier = Modifier.clearAndSetSemantics { contentDescription = label; testTag = "score" },
        )
    }

    @Composable
    fun Context() {
        val session = model.state
        val time = clock(session.elapsedSeconds)
        val text = when (kind) {
            GameKind.Free -> stringResource(R.string.context_free, themeName, session.level.title(), time)
            is GameKind.Daily ->
                stringResource(if (kind.isReplay) R.string.context_replay else R.string.context_daily, themeName, time)
        }
        Text(
            text, style = QuietType.subheadline.copy(fontFeatureSettings = QuietType.TABULAR), color = QuietTheme.colors.muted,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("context"),
        )
    }

    @Composable
    fun Actions(modifier: Modifier = Modifier) {
        val colors = QuietTheme.colors
        val session = model.state
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CapsuleButton(
                onClick = { model.requestHint() }, modifier = Modifier.weight(1f).testTag("hint"), prominent = false,
                enabled = !session.isWon, description = stringResource(R.string.hint_a11y, session.scoring.hint),
            ) {
                Icon(Icons.Outlined.Lightbulb, null, Modifier.size(20.dp))
                Text(stringResource(R.string.hint), maxLines = 1)
                Text("−${session.scoring.hint}", color = colors.muted, maxLines = 1)
            }
            CapsuleButton(
                onClick = onGuess, modifier = Modifier.weight(1f).testTag("guess"), prominent = false,
                enabled = session.canGuess, description = stringResource(R.string.guess_a11y, session.scoring.hiddenGuessed),
            ) {
                Icon(Icons.Outlined.Visibility, null, Modifier.size(20.dp))
                Text(stringResource(R.string.guess), maxLines = 1)
                Text("+${session.scoring.hiddenGuessed}", color = colors.accent, maxLines = 1)
            }
        }
    }
}
