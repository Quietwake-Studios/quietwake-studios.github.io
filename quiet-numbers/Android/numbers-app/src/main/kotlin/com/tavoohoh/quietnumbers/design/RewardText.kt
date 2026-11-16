package com.tavoohoh.quietnumbers.design

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.tavoohoh.quietnumbers.R
import com.tavoohoh.quietnumbers.core.Achievement
import com.tavoohoh.quietnumbers.core.AppIconChoice
import com.tavoohoh.quietnumbers.core.ClearEffect
import com.tavoohoh.quietnumbers.core.NumberFont
import com.tavoohoh.quietnumbers.core.Reward
import com.tavoohoh.quietnumbers.core.SoundPack
import com.tavoohoh.quietnumbers.core.ThemeChoice
import com.tavoohoh.quietnumbers.core.TileStyle

/** Names, conditions and rewards of the achievements (iOS `AchievementText.swift`). */
@Composable
@ReadOnlyComposable
fun Achievement.title(): String = stringResource(
    when (this) {
        Achievement.FIRST_STEP -> R.string.ach_first_step
        Achievement.CLEAN_SLATE -> R.string.ach_clean_slate
        Achievement.NO_HELP -> R.string.ach_no_help
        Achievement.CONSISTENCY -> R.string.ach_consistency
        Achievement.HABIT -> R.string.ach_habit
        Achievement.SWEEPER -> R.string.ach_sweeper
        Achievement.STAGE_FIVE -> R.string.ach_stage_five
        Achievement.CENTENNIAL -> R.string.ach_centennial
        Achievement.NIGHT_OWL -> R.string.ach_night_owl
        Achievement.MONOTONE -> R.string.ach_monotone
        Achievement.IN_THE_RED -> R.string.ach_in_the_red
    },
)

@Composable
@ReadOnlyComposable
fun Achievement.condition(): String = stringResource(
    when (this) {
        Achievement.FIRST_STEP -> R.string.cond_first_step
        Achievement.CLEAN_SLATE -> R.string.cond_clean_slate
        Achievement.NO_HELP -> R.string.cond_no_help
        Achievement.CONSISTENCY -> R.string.cond_consistency
        Achievement.HABIT -> R.string.cond_habit
        Achievement.SWEEPER -> R.string.cond_sweeper
        Achievement.STAGE_FIVE -> R.string.cond_stage_five
        Achievement.CENTENNIAL -> R.string.cond_centennial
        Achievement.NIGHT_OWL -> R.string.cond_night_owl
        Achievement.MONOTONE -> R.string.cond_monotone
        Achievement.IN_THE_RED -> R.string.cond_in_the_red
    },
)

/** What it unlocks, for Collection › Achievements (null: nothing). */
@Composable
@ReadOnlyComposable
fun Achievement.reward(): String? = when (this) {
    Achievement.FIRST_STEP -> null
    Achievement.CLEAN_SLATE -> R.string.rew_clean_slate
    Achievement.NO_HELP -> R.string.rew_no_help
    Achievement.CONSISTENCY -> R.string.rew_consistency
    Achievement.HABIT -> R.string.rew_habit
    Achievement.SWEEPER -> R.string.rew_sweeper
    Achievement.STAGE_FIVE -> R.string.rew_stage_five
    Achievement.CENTENNIAL -> R.string.rew_centennial
    Achievement.NIGHT_OWL -> R.string.rew_night_owl
    Achievement.MONOTONE -> R.string.rew_monotone
    Achievement.IN_THE_RED -> R.string.rew_in_the_red
}?.let { stringResource(it) }

/** The badge (same art as iOS and the store's achievements: `render-artwork.py`). */
@get:DrawableRes
val Achievement.badgeRes: Int
    get() = when (this) {
        Achievement.FIRST_STEP -> R.drawable.badge_first_step
        Achievement.CLEAN_SLATE -> R.drawable.badge_clean_slate
        Achievement.NO_HELP -> R.drawable.badge_no_help
        Achievement.CONSISTENCY -> R.drawable.badge_consistency
        Achievement.HABIT -> R.drawable.badge_habit
        Achievement.SWEEPER -> R.drawable.badge_sweeper
        Achievement.STAGE_FIVE -> R.drawable.badge_stage_five
        Achievement.CENTENNIAL -> R.drawable.badge_centennial
        Achievement.NIGHT_OWL -> R.drawable.badge_night_owl
        Achievement.MONOTONE -> R.drawable.badge_monotone
        Achievement.IN_THE_RED -> R.drawable.badge_in_the_red
    }

/** The badge; one not yet earned is faded and without color. */
@Composable
fun Badge(achievement: Achievement, size: Dp, earned: Boolean = true) {
    Image(
        painterResource(achievement.badgeRes), null,
        Modifier.size(size).alpha(if (earned) 1f else 0.4f),
        colorFilter = if (earned) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
    )
}

/** A reward's name in Collection. */
@Composable
@ReadOnlyComposable
fun Reward.title(): String = stringResource(
    when (this) {
        ThemeChoice.TEAL_CORAL -> R.string.theme_teal_coral
        ThemeChoice.SAGE_SAND -> R.string.theme_sage_sand
        ThemeChoice.INDIGO_PEACH -> R.string.theme_indigo_peach
        ThemeChoice.SAGE_CORAL -> R.string.theme_sage_coral
        ThemeChoice.HIGH_CONTRAST -> R.string.theme_high_contrast
        TileStyle.PLAIN -> R.string.tile_plain
        TileStyle.CIRCLE -> R.string.tile_circle
        TileStyle.OUTLINE -> R.string.tile_outline
        NumberFont.ROUNDED -> R.string.font_rounded
        NumberFont.SERIF -> R.string.font_serif
        ClearEffect.OUTLINE -> R.string.clear_outline
        ClearEffect.WAVE -> R.string.clear_wave
        SoundPack.SOFT -> R.string.sound_soft
        SoundPack.MARIMBA -> R.string.sound_marimba
        AppIconChoice.STANDARD -> R.string.icon_standard
        AppIconChoice.GOLD -> R.string.icon_gold
        else -> R.string.theme_teal_coral
    },
)
