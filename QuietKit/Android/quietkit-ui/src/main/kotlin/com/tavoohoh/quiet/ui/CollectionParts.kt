package com.tavoohoh.quiet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tavoohoh.quiet.core.Palette

/** A thin progress bar on `surface2` (album themes, achievements). */
@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).background(QuietTheme.colors.surface2, CircleShape)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(color, CircleShape))
    }
}

/** A theme in both modes, side by side: background with its primary and accent (Collection › Rewards). */
@Composable
fun ThemePreview(palette: Palette, modifier: Modifier = Modifier) {
    val light = QuietColors.of(palette, dark = false)
    val dark = QuietColors.of(palette, dark = true)
    Row(modifier) {
        for (c in listOf(light, dark)) {
            Row(
                Modifier.weight(1f).fillMaxHeight().background(
                    c.background,
                    if (c === light) RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp) else RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp),
                ),
                horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(14.dp).background(c.primary, CircleShape))
                Box(Modifier.size(14.dp).background(c.accent, CircleShape))
            }
        }
    }
}

/** "N earned · of M" over the list of achievements. */
@Composable
fun AchievementsHeader(earned: Int, total: Int) {
    val colors = QuietTheme.colors
    Row(
        Modifier.padding(horizontal = 4.dp).semantics(mergeDescendants = true) { testTag = "achievementsHeader" },
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(stringResource(R.string.quiet_n_earned, earned), style = QuietType.title2.copy(fontWeight = FontWeight.Black), color = colors.ink)
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.quiet_of_n, total), style = QuietType.subheadline, color = colors.muted)
    }
}

/**
 * One achievement in Collection › Achievements (iOS `AchievementRow`): its badge, how it's earned,
 * progress when it counts something, what it unlocks and a seal once earned. A hidden one keeps
 * its secret ("Hidden achievement") until earned; `badge` draws the "?" then.
 */
@Composable
fun AchievementRow(
    id: String,
    title: String,
    condition: String,
    isEarned: Boolean,
    isSecret: Boolean,
    reward: String?,
    stepFraction: Float? = null,
    stepText: String? = null,
    badge: @Composable () -> Unit,
) {
    val colors = QuietTheme.colors
    val earnedLabel = stringResource(R.string.quiet_earned)
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { testTag = "achievement-$id" }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) { badge() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (isSecret) stringResource(R.string.quiet_hidden_achievement) else title, style = QuietType.headline, color = colors.ink)
            Text(if (isSecret) stringResource(R.string.quiet_keep_playing) else condition, style = QuietType.subheadline, color = colors.muted)
            if (stepFraction != null && stepText != null && !isEarned) {
                ProgressBar(stepFraction, colors.primary, Modifier.padding(vertical = 2.dp))
                Text(stepText, style = QuietType.caption.copy(fontFeatureSettings = QuietType.TABULAR), color = colors.muted)
            }
            if (!isSecret && reward != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.CardGiftcard, null, tint = if (isEarned) colors.primary else colors.muted, modifier = Modifier.size(14.dp))
                    Text(reward, style = QuietType.caption, color = if (isEarned) colors.primary else colors.muted)
                }
            }
        }
        if (isEarned) Icon(Icons.Rounded.Verified, earnedLabel, tint = colors.primary, modifier = Modifier.size(24.dp))
    }
}

/** "Achievement unlocked" in a capsule during play, with the achievement's badge. */
@Composable
fun UnlockBanner(title: String, modifier: Modifier = Modifier, badge: @Composable () -> Unit) {
    val colors = QuietTheme.colors
    Row(
        modifier.shadow(12.dp, CircleShape).quietSurface(CircleShape)
            .semantics(mergeDescendants = true) {}.testTag("unlockBanner").padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { badge() }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.quiet_achievement_unlocked), style = QuietType.caption, color = colors.muted)
            Text(title, style = QuietType.headline, color = colors.ink)
        }
    }
}

/** "Hints › Cheaper hints" at the top of Rewards, only once "In the Red" is earned. */
@Composable
fun CheaperHintsSection(detail: String, isOn: Boolean, onChange: (Boolean) -> Unit) {
    GroupSection(stringResource(R.string.quiet_hints)) {
        GroupToggle(stringResource(R.string.quiet_cheaper_hints), detail, isOn, Modifier.testTag("cheapHints"), onChange = onChange)
    }
}
