package com.tavoohoh.quietwords.collection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tavoohoh.quiet.core.Palette
import com.tavoohoh.quiet.ui.QuietColors
import com.tavoohoh.quiet.ui.QuietScreen
import com.tavoohoh.quiet.ui.ProgressBar
import com.tavoohoh.quiet.ui.ThemePreview
import com.tavoohoh.quiet.ui.AchievementsHeader
import com.tavoohoh.quiet.ui.CheaperHintsSection
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quiet.ui.QuietType
import com.tavoohoh.quiet.ui.Segments
import com.tavoohoh.quiet.ui.quietClickable
import com.tavoohoh.quiet.ui.quietSurface
import com.tavoohoh.quietwords.AppModel
import com.tavoohoh.quietwords.CollectionTab
import com.tavoohoh.quietwords.R
import com.tavoohoh.quietwords.core.Achievement
import com.tavoohoh.quietwords.core.AchievementStep
import com.tavoohoh.quietwords.core.AppIconChoice
import com.tavoohoh.quietwords.core.BoardFont
import com.tavoohoh.quietwords.core.Marker
import com.tavoohoh.quietwords.core.PaletteReward
import com.tavoohoh.quietwords.core.Reward
import com.tavoohoh.quietwords.core.WordLanguage
import com.tavoohoh.quietwords.core.WordTheme
import com.tavoohoh.quiet.ui.GroupSection
import com.tavoohoh.quiet.ui.GroupToggle
import com.tavoohoh.quietwords.design.condition
import com.tavoohoh.quietwords.design.family
import com.tavoohoh.quietwords.design.icon
import com.tavoohoh.quietwords.design.localizedName
import com.tavoohoh.quietwords.design.reward
import com.tavoohoh.quietwords.design.themeIcon
import com.tavoohoh.quietwords.design.title
import com.tavoohoh.quietwords.design.weight
import com.tavoohoh.quietwords.game.drawMarker
import java.text.NumberFormat

/**
 * The album (every word found, per theme and language), the rewards and the achievements
 * (iOS `CollectionView`): three tabs, Album · Rewards · Achievements.
 */
@Composable
fun CollectionScreen(app: AppModel, onBack: () -> Unit) {
    val colors = QuietTheme.colors
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()) {
        val regular = maxWidth >= 600.dp
        QuietScreen(stringResource(R.string.collection), onBack = onBack, backDescription = stringResource(R.string.back)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = if (regular) 560.dp else 480.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Segments(
                        app.collectionTab,
                        listOf(
                            CollectionTab.ALBUM to stringResource(R.string.album),
                            CollectionTab.REWARDS to stringResource(R.string.rewards),
                            CollectionTab.ACHIEVEMENTS to stringResource(R.string.achievements),
                        ),
                        { app.collectionTab = it },
                        Modifier.testTag("collectionTabs"),
                    )
                    when (app.collectionTab) {
                        CollectionTab.ALBUM -> AlbumList(app)
                        CollectionTab.REWARDS -> RewardsList(app)
                        CollectionTab.ACHIEVEMENTS -> AchievementsList(app)
                    }
                }
            }
        }
    }
}

// Album

@Composable
private fun AlbumList(app: AppModel) {
    val colors = QuietTheme.colors
    val language = app.albumLanguage
    val album = app.album
    val total = album.total(language, app.library)
    val themes = app.library.themes(language)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Segments(
            language, listOf(WordLanguage.EN to stringResource(R.string.english), WordLanguage.ES to stringResource(R.string.espanol)),
            { app.albumLanguage = it }, Modifier.testTag("albumLanguage"),
        )
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
            Text(pluralStringResource(R.plurals.n_words, total.found, total.found), style = QuietType.title2.copy(fontWeight = FontWeight.Black), color = colors.ink)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.of_badges, total.total, total.badges), style = QuietType.subheadline, color = colors.muted)
        }
        Column(Modifier.fillMaxWidth().quietSurface(RoundedCornerShape(22.dp))) {
            themes.forEachIndexed { i, theme ->
                AlbumRow(theme, album.count(theme, language).let { it.found to it.total }) { app.openAlbum(theme.id, language) }
                if (i != themes.lastIndex) {
                    Box(Modifier.padding(start = 50.dp).fillMaxWidth().height(1.dp).background(colors.separator))
                }
            }
        }
    }
}

@Composable
private fun AlbumRow(theme: WordTheme, count: Pair<Int, Int>, onOpen: () -> Unit) {
    val colors = QuietTheme.colors
    val (found, total) = count
    val complete = total > 0 && found == total
    val badge = stringResource(if (complete) R.string.badge_earned else R.string.badge_not_earned)
    Row(
        Modifier.fillMaxWidth().quietClickable(onClick = onOpen).testTag("album-${theme.id}").padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(themeIcon(theme.symbol), null, tint = colors.primary, modifier = Modifier.width(26.dp).size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(theme.localizedName(), style = QuietType.headline, color = colors.ink)
                Spacer(Modifier.weight(1f))
                Text("$found/$total", style = QuietType.subheadline.copy(fontFeatureSettings = QuietType.TABULAR), color = colors.muted)
            }
            ProgressBar(if (total > 0) found.toFloat() / total else 0f, if (complete) colors.accent else colors.primary)
        }
        BadgeIcon(complete, Modifier.size(26.dp).semantics { contentDescription = badge })
    }
}

/** The theme's badge: a rosette when earned, a dashed circle until then. */
@Composable
private fun BadgeIcon(complete: Boolean, modifier: Modifier) {
    val colors = QuietTheme.colors
    if (complete) {
        Icon(Icons.Outlined.WorkspacePremium, null, tint = colors.accent, modifier = modifier)
    } else {
        Canvas(modifier) {
            val d = density
            drawCircle(colors.ghost, size.minDimension / 2 - 1.5f * d, style = Stroke(2f * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * d, 3 * d))))
        }
    }
}


/** One theme's album: found words as written; missing ones only show their length. */
@Composable
fun AlbumThemeScreen(app: AppModel, onBack: () -> Unit) {
    val colors = QuietTheme.colors
    val theme = app.albumThemeID?.let(app.library::theme) ?: return
    val language = app.albumLanguage
    val album = app.album
    val found = album.found(theme, language)
    val count = album.count(theme, language)
    val complete = count.total > 0 && count.found == count.total
    val collator = java.text.Collator.getInstance(java.util.Locale(language.rawValue))
    val words = (theme.words(language)?.all ?: emptyList()).distinctBy { it.letters }.sortedWith { a, b -> collator.compare(a.text, b.text) }
    var filter by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    val shown = words.filter { filter == 0 || (filter == 1) == (it.letters in found) }

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()) {
        val regular = maxWidth >= 600.dp
        QuietScreen(
            theme.localizedName(), onBack = onBack, backDescription = stringResource(R.string.back),
            trailing = { Text("${count.found}/${count.total}", style = QuietType.headline.copy(fontFeatureSettings = QuietType.TABULAR), color = colors.muted) },
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = if (regular) 680.dp else 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().quietSurface(RoundedCornerShape(22.dp)).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(54.dp)) {
                                val d = density
                                drawCircle(
                                    colors.ghost, size.minDimension / 2 - 1.25f * d,
                                    style = Stroke(2.5f * d, pathEffect = if (complete) null else PathEffect.dashPathEffect(floatArrayOf(4 * d, 4 * d))),
                                )
                            }
                            Icon(
                                if (complete) Icons.Outlined.WorkspacePremium else themeIcon(theme.symbol), null,
                                tint = if (complete) colors.accent else colors.muted, modifier = Modifier.size(26.dp),
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                if (complete) stringResource(R.string.badge_earned)
                                else pluralStringResource(R.plurals.words_to_badge, count.total - count.found, count.total - count.found),
                                style = QuietType.subheadline.copy(fontWeight = FontWeight.SemiBold), color = colors.ink,
                            )
                            ProgressBar(if (count.total > 0) count.found.toFloat() / count.total else 0f, colors.primary)
                        }
                    }
                    Segments(
                        filter,
                        listOf(0 to stringResource(R.string.filter_all), 1 to stringResource(R.string.filter_found), 2 to stringResource(R.string.filter_missing)),
                        { filter = it },
                    )
                    BoxWithConstraints(Modifier.fillMaxWidth().quietSurface(RoundedCornerShape(22.dp)).padding(16.dp)) {
                        val columns = maxOf(1, (maxWidth / 140.dp).toInt())
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (start in shown.indices step columns) {
                                Row {
                                    for (i in start until start + columns) {
                                        Box(Modifier.weight(1f).height(22.dp), contentAlignment = Alignment.CenterStart) {
                                            val word = shown.getOrNull(i) ?: return@Box
                                            if (word.letters in found) {
                                                Text(word.text, style = QuietType.subheadline, color = colors.ink, maxLines = 1)
                                            } else {
                                                val label = pluralStringResource(R.plurals.missing_word, word.count, word.count)
                                                Row(Modifier.clearAndSetSemantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                    repeat(word.count) { Box(Modifier.size(6.dp).background(colors.ghost, CircleShape)) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.missing_note), style = QuietType.footnote, color = colors.muted,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// Rewards

/** Markers, board letters, themes and the icon. Locked ones say how they're earned. */
@Composable
private fun RewardsList(app: AppModel) {
    val colors = QuietTheme.colors
    val progress = app.progress
    val looks = app.chosenLooks
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // "In the Red" (hidden): the only reward that touches play, so it can be turned off.
        // It doesn't show until earned.
        if (Achievement.IN_THE_RED in progress.unlocked) {
            CheaperHintsSection(stringResource(R.string.cheaper_hints_detail), app.cheapHints, app::chooseCheapHints)
        }
        RewardSection(stringResource(R.string.marker), Marker.entries, looks.marker, app, { app.choose(looks.copy(marker = it)) }) { m, _ ->
            MarkerPreview(m)
        }
        RewardSection(stringResource(R.string.board_letters), BoardFont.entries, looks.boardFont, app, { app.choose(looks.copy(boardFont = it)) }) { f, _ ->
            Text("ABC", style = QuietType.title3.copy(fontFamily = f.family, fontWeight = f.weight, letterSpacing = 2.sp), color = colors.ink)
        }
        RewardSection(
            stringResource(R.string.theme), PaletteReward.all, PaletteReward(looks.palette), app, { app.choose(looks.copy(palette = it.palette)) },
        ) { p, _ -> ThemePreview(p.palette, Modifier.padding(6.dp).fillMaxWidth().height(34.dp)) }
        RewardSection(stringResource(R.string.app_icon), AppIconChoice.entries, looks.icon, app, { app.choose(looks.copy(icon = it)) }) { c, _ ->
            Icon(
                if (c == AppIconChoice.GOLD) Icons.Rounded.Verified else Icons.Outlined.WorkspacePremium, null,
                tint = if (c == AppIconChoice.GOLD) Color(0xFFE3A21A) else colors.primary, modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun <R : Reward> rewardTitle(item: R): String = when (item) {
    is Marker -> item.title()
    is BoardFont -> item.title()
    is PaletteReward -> item.palette.title()
    is AppIconChoice -> item.title()
    else -> item.rawValue
}

@Composable
private fun <T : Reward> RewardSection(
    title: String, items: List<T>, selected: T, app: AppModel, onSelect: (T) -> Unit,
    preview: @Composable (T, Boolean) -> Unit,
) {
    val colors = QuietTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = QuietType.footnote.copy(fontWeight = FontWeight.SemiBold), color = colors.muted, modifier = Modifier.padding(start = 4.dp))
        BoxWithConstraints {
            val columns = maxOf(1, ((maxWidth + 8.dp) / 108.dp).toInt())
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (start in items.indices step columns) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in start until start + columns) {
                            Box(Modifier.weight(1f)) {
                                val item = items.getOrNull(i) ?: return@Box
                                RewardCard(item, item == selected, item.isUnlocked(app.progress), { onSelect(item) }) { preview(item, it) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T : Reward> RewardCard(item: T, isSelected: Boolean, unlocked: Boolean, onSelect: () -> Unit, preview: @Composable (Boolean) -> Unit) {
    val colors = QuietTheme.colors
    val shape = RoundedCornerShape(18.dp)
    val achievement = item.achievement
    Column(
        Modifier
            .fillMaxWidth()
            .quietSurface(shape)
            .border(2.5.dp, if (isSelected) colors.primary else Color.Transparent, shape)
            .quietClickable(enabled = unlocked, role = Role.Button, onClick = onSelect)
            .semantics(mergeDescendants = true) { selected = isSelected; testTag = "reward-${item.rawValue}" }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 46.dp).background(colors.background, RoundedCornerShape(12.dp)).alpha(if (unlocked) 1f else 0.45f),
            contentAlignment = Alignment.Center,
        ) { preview(unlocked) }
        Text(rewardTitle(item), style = QuietType.subheadline.copy(fontWeight = FontWeight.SemiBold), color = colors.ink)
        if (achievement != null && !unlocked) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(Icons.Rounded.Lock, null, tint = colors.muted, modifier = Modifier.size(11.dp))
                Text(achievement.title(), style = QuietType.caption.copy(fontSize = 11.sp), color = colors.muted)
            }
        } else {
            Text(stringResource(R.string.earned), style = QuietType.caption.copy(fontSize = 11.sp), color = colors.muted)
        }
    }
}

@Composable
private fun MarkerPreview(marker: Marker) {
    val colors = QuietTheme.colors
    Box(Modifier.size(width = 90.dp, height = 30.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(width = 90.dp, height = 30.dp)) {
            val d = density
            val rect = Offset(4 * d, size.height / 2 - 10 * d) to Size(size.width - 8 * d, 20 * d)
            drawMarker(marker, rect.first, rect.second, CornerRadius(10 * d), colors.primary, colors.ink, colors.isDark, d)
        }
        Text("MAREA", style = QuietType.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp), color = colors.ink)
    }
}

// Achievements

/**
 * Every achievement: how it's earned, progress when it counts something, whether it's earned
 * and what it unlocks. Hidden ones keep their condition until earned.
 */
@Composable
private fun AchievementsList(app: AppModel) {
    val colors = QuietTheme.colors
    val progress = app.progress
    val earned = Achievement.entries.count { it in progress.unlocked }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AchievementsHeader(earned, Achievement.entries.size)
        GroupSection(null) {
            for (achievement in Achievement.entries) {
                AchievementRow(achievement, achievement in progress.unlocked, AchievementStep.of(achievement, progress, app.album, app.library))
            }
        }
    }
}

@Composable
private fun AchievementRow(achievement: Achievement, isEarned: Boolean, step: AchievementStep?) {
    val colors = QuietTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val secret = achievement.isHidden && !isEarned
    val stepText = when (step) {
        is AchievementStep.Count -> stringResource(R.string.n_of_words, minOf(step.n, step.target), step.target)
        is AchievementStep.Streak -> stringResource(R.string.best_streak_of, minOf(step.n, step.target), step.target)
        is AchievementStep.Percent -> stringResource(R.string.most_advanced, NumberFormat.getPercentInstance(locale).format(step.value))
        null -> null
    }
    com.tavoohoh.quiet.ui.AchievementRow(
        id = achievement.rawValue, title = achievement.title(), condition = achievement.condition(), isEarned = isEarned,
        isSecret = secret, reward = achievement.reward(), stepFraction = step?.fraction?.toFloat(), stepText = stepText,
    ) {
        Box(Modifier.size(42.dp).background(if (isEarned) colors.primary else colors.surface2, CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (secret) Icons.Outlined.QuestionMark else achievement.icon, null, tint = if (isEarned) colors.onPrimary else colors.muted, modifier = Modifier.size(20.dp))
        }
    }
}
