package com.tavoohoh.quietnumbers

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.tavoohoh.quiet.ui.QuietSplash
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quietnumbers.game.GameScreen
import com.tavoohoh.quietnumbers.core.GameKind
import com.tavoohoh.quietnumbers.daily.DailyScreen
import com.tavoohoh.quietnumbers.settings.SettingsScreen
import com.tavoohoh.quiet.ui.ReminderOffer
import com.tavoohoh.quiet.ui.rememberNotificationRequest
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.LaunchedEffect
import com.tavoohoh.quietnumbers.home.HomeScreen
import kotlinx.coroutines.CancellationException

/** One stack of screens; system back (with its predictive preview) pops it. Same shell as Quiet Words. */
@Composable
fun NumbersApp(app: AppModel) {
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
    var showsSplash by rememberSaveable { mutableStateOf(!app.uiTesting && app.stack.size == 1) }

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
                    scaleX = 1f - 0.08f * shrink
                    scaleY = 1f - 0.08f * shrink
                    translationX = 24.dp.toPx() * shrink
                    shape = RoundedCornerShape((32 * shrink).dp)
                    clip = shrink > 0f
                },
            ) {
                Screen(app, screen)
            }
        }
        val requestNotifications = rememberNotificationRequest(app.reminders)
        if (app.offersReminders) {
            ReminderOffer(
                onRemind = {
                    app.offersReminders = false
                    if (app.reminders.needsPermissionRequest) requestNotifications() else app.reminders.onPermissionResult(true)
                },
                onNotNow = {
                    app.offersReminders = false
                    app.reminders.markOffered()
                },
            )
        }
        AnimatedVisibility(showsSplash, enter = EnterTransition.None, exit = if (reduceMotion) ExitTransition.None else fadeOut(tween(350))) {
            QuietSplash(onFinished = { showsSplash = false })
        }
    }
}

@Composable
private fun Screen(app: AppModel, route: Route) {
    val activity = LocalActivity.current
    if (route == Route.HOME || route == Route.DAILY) {
        // Back on Home or the Daily screen: a review request that waited ("New Game") can show now.
        LaunchedEffect(Unit) { if (app.reviewOnArrival()) activity?.let(app.storeReview::request) }
    }
    when (route) {
        Route.HOME -> HomeScreen(app)
        Route.DAILY -> DailyScreen(app, onBack = app::pop)
        Route.SETTINGS -> SettingsScreen(app, onBack = app::pop)
        Route.COLLECTION -> com.tavoohoh.quietnumbers.collection.CollectionScreen(app, onBack = app::pop)
        Route.TUTORIAL -> com.tavoohoh.quietnumbers.tutorial.TutorialScreen(app.tutorialIsFirstRun, onBack = app::pop, onFinish = app::tutorialFinished)
        Route.GAME -> {
            val game = app.activeGame
            val kind = app.activeKind
            if (game != null && kind != null) {
                GameScreen(
                    model = game, kind = kind, todayKey = app.todayKey,
                    bestScore = if (kind == GameKind.Classic) app.bestAtStart else null,
                    isReplay = app.isReplay,
                    unlocked = app.unlockedThisGame,
                    pendingUnlock = app.pendingUnlocks.firstOrNull(),
                    onUnlockShown = app::dismissUnlock,
                    onBack = app::pop,
                    onNewGame = { if (app.leaveGame(toNewGame = true)) activity?.let(app.storeReview::request) },
                    onHome = { if (app.leaveGame(toNewGame = false)) activity?.let(app.storeReview::request) },
                )
            }
        }
    }
}
