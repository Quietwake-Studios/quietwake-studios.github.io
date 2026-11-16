package com.tavoohoh.quietnumbers.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tavoohoh.quiet.ui.AchievementRow
import com.tavoohoh.quiet.ui.AchievementsHeader
import com.tavoohoh.quiet.ui.CheaperHintsSection
import com.tavoohoh.quiet.ui.GroupDivider
import com.tavoohoh.quiet.ui.GroupSection
import com.tavoohoh.quiet.ui.QuietScreen
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quiet.ui.QuietType
import com.tavoohoh.quiet.ui.Segments
import com.tavoohoh.quiet.ui.ThemePreview
import com.tavoohoh.quiet.ui.quietClickable
import com.tavoohoh.quietnumbers.AppModel
import com.tavoohoh.quietnumbers.CollectionTab
import com.tavoohoh.quietnumbers.R
import com.tavoohoh.quietnumbers.core.Achievement
import com.tavoohoh.quietnumbers.core.AchievementStep
import com.tavoohoh.quietnumbers.core.AppIconChoice
import com.tavoohoh.quietnumbers.core.ClearEffect
import com.tavoohoh.quietnumbers.core.NumberFont
import com.tavoohoh.quietnumbers.core.Reward
import com.tavoohoh.quietnumbers.core.SoundPack
import com.tavoohoh.quietnumbers.core.ThemeChoice
import com.tavoohoh.quietnumbers.core.TileStyle
import com.tavoohoh.quietnumbers.design.Badge
import com.tavoohoh.quietnumbers.design.condition
import com.tavoohoh.quietnumbers.design.reward
import com.tavoohoh.quietnumbers.design.title

/**
 * Rewards (the cosmetics; locked ones say how to get them) and achievements (how each one is
 * earned, with progress), in two tabs like Quiet Words (iOS `CollectionView`).
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
                Column(Modifier.widthIn(max = if (regular) 680.dp else 480.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Segments(
                        app.collectionTab,
                        listOf(CollectionTab.REWARDS to stringResource(R.string.rewards), CollectionTab.ACHIEVEMENTS to stringResource(R.string.achievements)),
                        { app.collectionTab = it },
                        Modifier.testTag("collectionTabs"),
                    )
                    when (app.collectionTab) {
                        CollectionTab.REWARDS -> RewardsList(app)
                        CollectionTab.ACHIEVEMENTS -> AchievementsList(app)
                    }
                }
            }
        }
    }
}

/** Every cosmetic by category; "Cheaper hints" on top once "In the Red" is earned. */
@Composable
private fun RewardsList(app: AppModel) {
    val colors = QuietTheme.colors
    val looks = app.chosenLooks
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // "In the Red" (hidden): the only reward that touches play, so it can be turned off.
        if (Achievement.IN_THE_RED in app.progress.unlocked) {
            CheaperHintsSection(stringResource(R.string.cheaper_hints_detail), app.cheapHints, app::chooseCheapHints)
        }
        RewardSection(stringResource(R.string.section_theme), ThemeChoice.entries, looks.theme, app, { app.choose(looks.copy(theme = it)) }) {
            ThemePreview(
                it.palette,
                Modifier.size(76.dp, 30.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, colors.separator, RoundedCornerShape(8.dp)),
            )
        }
        RewardSection(stringResource(R.string.section_tiles), TileStyle.entries, looks.tileStyle, app, { app.choose(looks.copy(tileStyle = it)) }) {
            TilePreview(it)
        }
        RewardSection(stringResource(R.string.section_numbers), NumberFont.entries, looks.numberFont, app, { app.choose(looks.copy(numberFont = it)) }) {
            Text(
                "12", style = TextStyle(fontFamily = if (it == NumberFont.SERIF) FontFamily.Serif else FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp),
                color = colors.ink, modifier = Modifier.width(36.dp),
            )
        }
        RewardSection(stringResource(R.string.section_row_clear), ClearEffect.entries, looks.clearEffect, app, { app.choose(looks.copy(clearEffect = it)) })
        RewardSection(stringResource(R.string.section_sound), SoundPack.entries, looks.soundPack, app, { app.choose(looks.copy(soundPack = it)) })
        RewardSection(stringResource(R.string.section_app_icon), AppIconChoice.entries, looks.icon, app, { app.choose(looks.copy(icon = it)) })
    }
}

/** One category: a row per option with its preview; locked ones say how they're earned, the chosen one has a check. */
@Composable
private fun <T : Reward> RewardSection(
    title: String, items: List<T>, selected: T, app: AppModel, onSelect: (T) -> Unit,
    preview: (@Composable (T) -> Unit)? = null,
) {
    val colors = QuietTheme.colors
    GroupSection(title) {
        items.forEachIndexed { i, item ->
            val unlocked = item.isUnlocked(app.progress)
            val detail = when {
                item == ThemeChoice.HIGH_CONTRAST -> stringResource(R.string.theme_high_contrast_note)
                !unlocked -> item.achievement?.condition()
                else -> null
            }
            if (i > 0) GroupDivider()
            Row(
                Modifier.fillMaxWidth()
                    .quietClickable(enabled = unlocked, role = Role.Button) { onSelect(item) }
                    .semantics(mergeDescendants = true) {
                        this.selected = item == selected
                        testTag = "reward-${item.rawValue}"
                        if (!unlocked) stateDescription = "locked"
                    }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (preview != null) Box(Modifier.alpha(if (unlocked) 1f else 0.5f)) { preview(item) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.title(), style = QuietType.body, color = if (unlocked) colors.ink else colors.muted)
                    if (detail != null) Text(detail, style = QuietType.subheadline, color = colors.muted)
                }
                Spacer(Modifier.width(8.dp))
                when {
                    !unlocked -> Icon(Icons.Outlined.Lock, null, tint = colors.muted, modifier = Modifier.size(20.dp))
                    item == selected -> Icon(Icons.Rounded.Check, null, tint = colors.primary, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

/** A "7" drawn with a tile style. */
@Composable
private fun TilePreview(style: TileStyle) {
    val colors = QuietTheme.colors
    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
        when (style) {
            TileStyle.PLAIN -> Unit
            TileStyle.CIRCLE -> Box(Modifier.size(34.dp).background(colors.surface2, CircleShape))
            TileStyle.OUTLINE -> Box(Modifier.size(34.dp).border(1.5.dp, colors.muted, CircleShape))
        }
        Text("7", style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 19.sp), color = colors.ink)
    }
}

/** Every achievement: how it's earned, progress when it counts something, whether it's earned and what it unlocks. */
@Composable
private fun AchievementsList(app: AppModel) {
    val colors = QuietTheme.colors
    val progress = app.progress
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AchievementsHeader(Achievement.entries.count { it in progress.unlocked }, Achievement.entries.size)
        GroupSection(null) {
            Achievement.entries.forEachIndexed { i, achievement ->
                if (i > 0) GroupDivider()
                val earned = achievement in progress.unlocked
                val secret = achievement.isHidden && !earned
                val step = AchievementStep.of(achievement, progress)
                val stepText = step?.let {
                    stringResource(
                        when (it.kind) {
                            AchievementStep.Kind.STREAK -> R.string.step_streak
                            AchievementStep.Kind.ROWS -> R.string.step_rows
                            AchievementStep.Kind.STAGE -> R.string.step_stage
                            AchievementStep.Kind.DAILIES -> R.string.step_dailies
                            AchievementStep.Kind.PAIRS -> R.string.step_pairs
                        },
                        it.shown, it.target,
                    )
                }
                AchievementRow(
                    id = achievement.rawValue, title = achievement.title(), condition = achievement.condition(), isEarned = earned,
                    isSecret = secret, reward = achievement.reward(), stepFraction = step?.fraction?.toFloat(), stepText = stepText,
                ) {
                    if (secret) {
                        Box(Modifier.size(42.dp).background(colors.surface2, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.QuestionMark, null, tint = colors.muted, modifier = Modifier.size(20.dp))
                        }
                    } else {
                        Badge(achievement, 42.dp, earned)
                    }
                }
            }
        }
    }
}
