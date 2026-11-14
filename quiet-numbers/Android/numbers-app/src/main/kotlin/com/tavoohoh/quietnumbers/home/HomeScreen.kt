package com.tavoohoh.quietnumbers.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tavoohoh.quiet.ui.CapsuleButton
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quiet.ui.QuietType
import com.tavoohoh.quiet.ui.quietClickable
import com.tavoohoh.quiet.ui.quietSurface
import com.tavoohoh.quietnumbers.AppModel
import com.tavoohoh.quietnumbers.R

/**
 * Home, Phase 2 (iOS `HomeView`): the icon, the name, the daily card (today's board, nothing
 * saved yet) and New Game. The menu, Continue and the stats come with Phases 3–4.
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
                    Modifier.widthIn(max = if (regular) 440.dp else 360.dp).fillMaxWidth().padding(top = if (regular) 96.dp else 72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(painterResource(R.drawable.home_icon), null, Modifier.size(84.dp).clip(RoundedCornerShape(84.dp * 0.225f)))
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.app_name), style = QuietType.largeTitle, color = colors.ink, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.tagline), style = QuietType.body, color = colors.muted)
                    Spacer(Modifier.height(36.dp))
                    DailyCard(app::playDaily)
                    Spacer(Modifier.height(12.dp))
                    CapsuleButton(stringResource(R.string.new_game), app::newClassic, Modifier.testTag("newGame"))
                }
            }
        }
    }
}

/** Today's challenge: a surface card with its status and a chevron (iOS `DailyCard`). */
@Composable
private fun DailyCard(onOpen: () -> Unit) {
    val colors = QuietTheme.colors
    Row(
        Modifier.fillMaxWidth().quietSurface(RoundedCornerShape(24.dp)).quietClickable(onClick = onOpen).testTag("daily")
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(R.string.daily_challenge), style = QuietType.headline, color = colors.ink)
            Text(stringResource(R.string.not_played_yet), style = QuietType.subheadline, color = colors.muted)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.muted, modifier = Modifier.size(20.dp))
    }
}
