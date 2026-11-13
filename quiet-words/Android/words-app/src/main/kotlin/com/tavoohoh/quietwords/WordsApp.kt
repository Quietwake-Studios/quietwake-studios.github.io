package com.tavoohoh.quietwords

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.tavoohoh.quiet.ui.QuietSplash
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quietwords.design.localizedName
import com.tavoohoh.quietwords.game.GameScreen
import com.tavoohoh.quietwords.home.HomeScreen
import com.tavoohoh.quietwords.newgame.NewGameScreen
import com.tavoohoh.quietwords.tutorial.TutorialScreen
import kotlinx.coroutines.CancellationException

/** One stack of screens; system back (with its predictive preview) pops it. */
@Composable
fun WordsApp(app: AppModel) {
    val colors = QuietTheme.colors
    val reduceMotion = QuietTheme.reduceMotion
    var backProgress by remember { mutableFloatStateOf(0f) }
    val depth = app.stack.size
    val route = app.stack.last()

    PredictiveBackHandler(enabled = depth > 1) { progress ->
        try {
            progress.collect { backProgress = it.progress }
            backProgress = 0f
            app.pop()
        } catch (e: CancellationException) {
            backProgress = 0f
            throw e
        }
    }

    // The studio's lockup after the system splash (not in UI tests).
    var showsSplash by rememberSaveable { mutableStateOf(!app.uiTesting) }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        AnimatedContent(
            targetState = depth to route,
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                } else if (targetState.first >= initialState.first) {
                    (slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(200))) togetherWith fadeOut(tween(150))
                } else {
                    fadeIn(tween(200)) togetherWith (slideOutHorizontally(tween(220)) { it / 4 } + fadeOut(tween(180)))
                }
            },
            label = "screens",
        ) { (_, screen) ->
            val shrink = if (reduceMotion) 0f else backProgress
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    // The predictive back preview: the screen shrinks and slides as the gesture goes.
                    scaleX = 1f - 0.08f * shrink
                    scaleY = 1f - 0.08f * shrink
                    translationX = 24.dp.toPx() * shrink
                    shape = androidx.compose.foundation.shape.RoundedCornerShape((32 * shrink).dp)
                    clip = shrink > 0f
                },
            ) {
                Screen(app, screen)
            }
        }
        AnimatedVisibility(showsSplash, enter = EnterTransition.None, exit = if (reduceMotion) ExitTransition.None else fadeOut(tween(350))) {
            QuietSplash(onFinished = { showsSplash = false })
        }
    }
}

@Composable
private fun Screen(app: AppModel, route: Route) {
    when (route) {
        Route.HOME -> HomeScreen(app)
        Route.NEW_GAME -> NewGameScreen(app, onBack = app::pop)
        Route.TUTORIAL -> TutorialScreen(app, onFinish = app::tutorialFinished)
        Route.GAME -> {
            val game = app.activeGame
            val kind = app.activeKind
            if (game != null && kind != null) {
                GameScreen(
                    model = game,
                    kind = kind,
                    themeName = app.theme(game.state.themeID)?.localizedName() ?: "",
                    onBack = app::pop,
                    onNewGame = { app.popToHome(); app.push(Route.NEW_GAME) },
                    onRestart = app::restart,
                    onHome = app::popToHome,
                )
            }
        }
    }
}
