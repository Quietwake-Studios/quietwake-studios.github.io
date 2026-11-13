package com.tavoohoh.quietwords.home

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tavoohoh.quiet.ui.CapsuleButton
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quiet.ui.QuietType
import com.tavoohoh.quiet.ui.quietClickable
import com.tavoohoh.quiet.ui.rememberHaptics
import com.tavoohoh.quiet.ui.QuietHaptics
import com.tavoohoh.quietwords.AppModel
import com.tavoohoh.quietwords.R
import com.tavoohoh.quietwords.Route
import com.tavoohoh.quietwords.core.DailyChallenge
import com.tavoohoh.quietwords.design.LanguagePill
import com.tavoohoh.quietwords.design.localizedName
import com.tavoohoh.quietwords.design.title
import com.tavoohoh.quietwords.design.wordsTitle

/**
 * Home, Phase 2: the daily card (today's board, nothing saved yet) and New Game. The menu
 * (leaderboards, collection, settings), Continue and the stats come with later phases.
 */
@Composable
fun HomeScreen(app: AppModel) {
    val colors = QuietTheme.colors
    Box(Modifier.fillMaxSize().background(colors.background)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val regular = maxWidth >= 600.dp
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier.widthIn(max = if (regular) 440.dp else 360.dp).fillMaxWidth().padding(top = if (regular) 96.dp else 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painterResource(R.drawable.home_icon), contentDescription = null,
                        modifier = Modifier.size(84.dp).clip(RoundedCornerShape(84.dp * 0.225f)),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.app_name), style = QuietType.largeTitle, color = colors.ink, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.tagline), style = QuietType.body, color = colors.muted)
                    Spacer(Modifier.height(28.dp))
                    DailyCard(app) {
                        if (app.needsTutorial) app.push(Route.TUTORIAL) else app.playDaily()
                    }
                    Spacer(Modifier.height(12.dp))
                    CapsuleButton(
                        stringResource(R.string.new_game),
                        onClick = { app.push(if (app.needsTutorial) Route.TUTORIAL else Route.NEW_GAME) },
                        modifier = Modifier.testTag("newGame"),
                    )
                }
            }
        }
    }
}

/** Today's challenge: theme, level, the EN · ES pill and Play. */
@Composable
private fun DailyCard(app: AppModel, onOpen: () -> Unit) {
    val colors = QuietTheme.colors
    val haptics = rememberHaptics()
    val today = app.todayKey
    val theme = DailyChallenge.themeID(today, app.library)?.let(app::theme)
    val level = DailyChallenge.level(today)
    Column(
        Modifier.fillMaxWidth().background(colors.primary, RoundedCornerShape(24.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    stringResource(R.string.daily_challenge).uppercase(), color = colors.onPrimary,
                    style = QuietType.caption.copy(fontWeight = FontWeight.Bold), modifier = Modifier.alpha(0.8f),
                )
                Text(stringResource(R.string.pair, theme?.localizedName() ?: "", level.title()), style = QuietType.title3, color = colors.onPrimary)
                Text(app.wordLanguage.wordsTitle(), style = QuietType.footnote, color = colors.onPrimary, modifier = Modifier.alpha(0.85f))
            }
            Spacer(Modifier.size(8.dp))
            LanguagePill(app.wordLanguage, app::setLanguage, onPrimary = true, onTick = { haptics.play(QuietHaptics.Event.SELECT) })
        }
        Box(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 46.dp)
                .background(colors.onPrimary, CircleShape)
                .quietClickable(enabled = !app.isPreparing, onClick = onOpen)
                .testTag("daily"),
            contentAlignment = Alignment.Center,
        ) {
            if (app.isPreparing) {
                CircularProgressIndicator(Modifier.size(22.dp), color = colors.primary, strokeWidth = 2.5.dp)
            } else {
                Text(stringResource(R.string.play), style = QuietType.headline, color = colors.primary)
            }
        }
    }
}

