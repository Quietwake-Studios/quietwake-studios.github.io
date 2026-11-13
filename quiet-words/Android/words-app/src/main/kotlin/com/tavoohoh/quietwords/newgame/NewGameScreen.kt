package com.tavoohoh.quietwords.newgame

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tavoohoh.quiet.ui.CapsuleButton
import com.tavoohoh.quiet.ui.QuietHaptics
import com.tavoohoh.quiet.ui.QuietScreen
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quiet.ui.QuietType
import com.tavoohoh.quiet.ui.Segments
import com.tavoohoh.quiet.ui.quietClickable
import com.tavoohoh.quiet.ui.quietSurface
import com.tavoohoh.quiet.ui.rememberHaptics
import com.tavoohoh.quietwords.AppModel
import com.tavoohoh.quietwords.R
import com.tavoohoh.quietwords.core.Album
import com.tavoohoh.quietwords.core.Level
import com.tavoohoh.quietwords.design.LanguagePill
import com.tavoohoh.quietwords.design.detail
import com.tavoohoh.quietwords.design.localizedName
import com.tavoohoh.quietwords.design.themeIcon
import com.tavoohoh.quietwords.design.title
import com.tavoohoh.quietwords.design.wordsTitle

/**
 * Free play: the level, the EN · ES pill beside it (remembered, never asked before each
 * game), and the themes with their album progress (empty until the album, Phase 4).
 */
@Composable
fun NewGameScreen(app: AppModel, onBack: () -> Unit) {
    val colors = QuietTheme.colors
    val haptics = rememberHaptics()
    var level by rememberSaveable { mutableStateOf(app.lastLevel) }
    /** null = Surprise. */
    var selection by rememberSaveable { mutableStateOf(app.lastThemeID?.takeIf { app.theme(it) != null }) }
    val language = app.wordLanguage
    val themes = app.library.themes(language)
    val album = Album()

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()) {
        val regular = maxWidth >= 600.dp
        QuietScreen(stringResource(R.string.new_game), onBack = onBack, backDescription = stringResource(R.string.back)) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(Modifier.widthIn(max = if (regular) 680.dp else 560.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Segments(level, Level.entries.map { it to it.title() }, { level = it }, Modifier.weight(1f))
                            LanguagePill(language, app::setLanguage, onTick = { haptics.play(QuietHaptics.Event.SELECT) })
                        }
                        Text("${level.detail()} · ${language.wordsTitle()}", style = QuietType.footnote, color = colors.muted)
                        BoxWithConstraints(Modifier.padding(top = 4.dp)) {
                            val columns = maxOf(1, ((maxWidth + 10.dp) / 160.dp).toInt())
                            val cards: List<@Composable (Modifier) -> Unit> = listOf<@Composable (Modifier) -> Unit> { m ->
                                ThemeCard(
                                    themeIcon("shuffle"), stringResource(R.string.surprise), stringResource(R.string.any_theme), null,
                                    selection == null, m.testTag("theme-surprise"),
                                ) { selection = null }
                            } + themes.map { theme ->
                                { m: Modifier ->
                                    val count = album.count(theme, language)
                                    ThemeCard(
                                        themeIcon(theme.symbol), theme.localizedName(), "${count.found}/${count.total}", count.found to count.total,
                                        selection == theme.id, m.testTag("theme-${theme.id}"),
                                    ) { selection = theme.id }
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                for (start in cards.indices step columns) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        for (i in start until start + columns) {
                                            Box(Modifier.weight(1f)) { cards.getOrNull(i)?.invoke(Modifier) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                val selected = selection?.let(app::theme)
                CapsuleButton(
                    onClick = { app.newFree(selection, level) },
                    modifier = Modifier.widthIn(max = if (regular) 440.dp else 360.dp).padding(horizontal = 20.dp).padding(bottom = 12.dp).testTag("play"),
                    enabled = !app.isPreparing,
                ) {
                    if (app.isPreparing) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = colors.onPrimary, strokeWidth = 2.5.dp)
                    } else {
                        Text(
                            if (selected != null) stringResource(R.string.play_theme, selected.localizedName()) else stringResource(R.string.play_surprise),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** A theme: its icon, name and album progress in the chosen language. */
@Composable
private fun ThemeCard(
    icon: ImageVector, name: String, detail: String, progress: Pair<Int, Int>?, isSelected: Boolean,
    modifier: Modifier, onClick: () -> Unit,
) {
    val colors = QuietTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .quietSurface(shape)
            .border(2.5.dp, if (isSelected) colors.primary else Color.Transparent, shape)
            .quietClickable(onClick = onClick)
            .semantics(mergeDescendants = true) { selected = isSelected }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = colors.primary, modifier = Modifier.size(22.dp))
            Text(name, style = QuietType.headline, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(detail, style = QuietType.footnote.copy(fontFeatureSettings = QuietType.TABULAR), color = colors.muted)
        if (progress != null && progress.second > 0) {
            Box(Modifier.fillMaxWidth().height(4.dp).background(colors.surface2, CircleShape)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress.first.toFloat() / progress.second).background(colors.primary, CircleShape))
            }
        } else {
            Spacer(Modifier.height(4.dp).width(1.dp))
        }
    }
}
