package com.tavoohoh.quietnumbers.tutorial

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tavoohoh.quiet.ui.CapsuleButton
import com.tavoohoh.quiet.ui.QuietIcons
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quiet.ui.QuietType
import com.tavoohoh.quiet.ui.RoundIconButton
import com.tavoohoh.quiet.ui.quietClickable
import com.tavoohoh.quiet.ui.quietSurface
import com.tavoohoh.quiet.ui.rememberHaptics
import com.tavoohoh.quietnumbers.R
import com.tavoohoh.quietnumbers.core.TutorialScript
import com.tavoohoh.quietnumbers.design.LocalBoardLooks
import com.tavoohoh.quietnumbers.design.LocalFeedback
import com.tavoohoh.quietnumbers.game.ActionBar
import com.tavoohoh.quietnumbers.game.BoardView
import com.tavoohoh.quietnumbers.game.GameModel
import com.tavoohoh.quietnumbers.game.TopBar
import kotlinx.coroutines.delay
import kotlin.math.floor

/**
 * Interactive tutorial on the real board (iOS `TutorialView`): each step rings the pair to play
 * (like a hint), explains why it's a pair, and only that move counts. Shown before the first
 * game and from Settings › How to play. Nothing here counts for progress.
 */
@Composable
fun TutorialScreen(isFirstRun: Boolean, onBack: () -> Unit, onFinish: () -> Unit) {
    val colors = QuietTheme.colors
    val reduceMotion = QuietTheme.reduceMotion
    val haptics = rememberHaptics()
    val feedback = LocalFeedback.current
    val model = remember { GameModel(TutorialScript.session()) }
    DisposableEffect(model) { onDispose { model.dispose() } }
    SideEffect {
        model.reduceMotion = reduceMotion
        model.onFeedback = { feedback.play(it, haptics) }
    }
    val steps = TutorialScript.steps
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    var finished by rememberSaveable { mutableStateOf(false) }

    fun guide(index: Int) {
        when (val move = steps[index].move) {
            is TutorialScript.Move.Pair -> model.pointAt(GameModel.Guide(setOf(move.a, move.b)))
            TutorialScript.Move.AddNumbers -> model.pointAt(GameModel.Guide(emptySet(), allowsAdd = true))
        }
    }
    LaunchedEffect(Unit) { if (!finished) guide(stepIndex) }
    // After a step's move, let its effects play, then point at the next one.
    LaunchedEffect(model.session, finished) {
        if (finished || !TutorialScript.isDone(steps[stepIndex], model.session)) return@LaunchedEffect
        delay(if (reduceMotion) 200 else 800)
        val next = stepIndex + 1
        if (next < steps.size) {
            stepIndex = next
            guide(next)
        } else {
            finished = true
            model.pointAt(GameModel.Guide(emptySet()))
        }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            val regular = maxWidth >= 600.dp
            val boardWidth = minOf(maxWidth - 48.dp, if (regular) 760.dp else 540.dp)
            val cellWidth = floor(boardWidth.value / 9).dp
            val cellHeight = maxOf(if (LocalBoardLooks.current.largerNumbers) 52.dp else 46.dp, cellWidth)
            Column(Modifier.widthIn(max = boardWidth + 48.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundIconButton(QuietIcons.Back, stringResource(R.string.back), onBack, Modifier.testTag("back"))
                    Spacer(Modifier.weight(1f))
                    if (!finished) {
                        Text(
                            stringResource(R.string.skip), style = QuietType.headline, color = colors.primary,
                            modifier = Modifier.defaultMinSize(48.dp, 48.dp).quietClickable(onClick = onFinish).padding(12.dp).testTag("skipTutorial"),
                        )
                    }
                }
                TopBar(model, stringResource(R.string.tutorial_step, minOf(stepIndex + 1, steps.size), steps.size))
                BoardView(model, cellWidth, cellHeight, Modifier.padding(top = 20.dp).weight(1f))
                AnimatedContent(
                    targetState = if (finished) 0 else steps[stepIndex].id,
                    transitionSpec = { (fadeIn(tween(200)) + slideInVertically { if (reduceMotion) 0 else it / 8 }) togetherWith fadeOut(tween(120)) },
                    label = "step",
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
                ) { id ->
                    Explanation(id, isFirstRun, onFinish)
                }
                ActionBar(model)
            }
        }
    }
}

/** The step's title and why it's a pair; at the end, "You're ready" and the button. */
@Composable
private fun Explanation(stepID: Int, isFirstRun: Boolean, onFinish: () -> Unit) {
    val colors = QuietTheme.colors
    Column(
        Modifier.fillMaxWidth().quietSurface(RoundedCornerShape(22.dp)).padding(16.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }.testTag("tutorialStep"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (stepID == 0) {
            Text(stringResource(R.string.youre_ready), style = QuietType.title3, color = colors.ink)
            Text(stringResource(R.string.tutorial_done_text), style = QuietType.body, color = colors.muted)
            CapsuleButton(
                stringResource(if (isFirstRun) R.string.start_playing else R.string.done), onFinish,
                Modifier.padding(top = 6.dp).testTag("finishTutorial"),
            )
        } else {
            Text(stringResource(TITLES[stepID - 1]), style = QuietType.title3, color = colors.ink)
            Text(stringResource(TEXTS[stepID - 1]), style = QuietType.body, color = colors.muted)
        }
    }
}

private val TITLES = listOf(
    R.string.tutorial_1_title, R.string.tutorial_2_title, R.string.tutorial_3_title, R.string.tutorial_4_title,
    R.string.tutorial_5_title, R.string.tutorial_6_title, R.string.tutorial_7_title,
)
private val TEXTS = listOf(
    R.string.tutorial_1_text, R.string.tutorial_2_text, R.string.tutorial_3_text, R.string.tutorial_4_text,
    R.string.tutorial_5_text, R.string.tutorial_6_text, R.string.tutorial_7_text,
)
