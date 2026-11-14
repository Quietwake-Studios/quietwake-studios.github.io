package com.tavoohoh.quietwords.design

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.tavoohoh.quiet.core.Palette
import com.tavoohoh.quietwords.R
import com.tavoohoh.quietwords.core.Achievement
import com.tavoohoh.quietwords.core.AppIconChoice
import com.tavoohoh.quietwords.core.BoardFont
import com.tavoohoh.quietwords.core.Marker

/** Names, conditions, rewards and icons of the achievements (iOS `Rewards.swift`; Material icons for SF Symbols). */
@Composable
@ReadOnlyComposable
fun Achievement.title(): String = stringResource(
    when (this) {
        Achievement.FIRST_PUZZLE -> R.string.ach_first_puzzle
        Achievement.WORDS_100 -> R.string.ach_words100
        Achievement.WORDS_1000 -> R.string.ach_words1000
        Achievement.WORDS_10000 -> R.string.ach_words10000
        Achievement.THEME_COMPLETE -> R.string.ach_theme_complete
        Achievement.IN_PLAIN_SIGHT -> R.string.ach_in_plain_sight
        Achievement.HABIT -> R.string.ach_habit
        Achievement.CONSISTENCY -> R.string.ach_consistency
        Achievement.STEADY_HAND -> R.string.ach_steady_hand
        Achievement.BILINGUAL -> R.string.ach_bilingual
        Achievement.IN_THE_RED -> R.string.ach_in_the_red
    },
)

@Composable
@ReadOnlyComposable
fun Achievement.condition(): String = stringResource(
    when (this) {
        Achievement.FIRST_PUZZLE -> R.string.cond_first_puzzle
        Achievement.WORDS_100 -> R.string.cond_words100
        Achievement.WORDS_1000 -> R.string.cond_words1000
        Achievement.WORDS_10000 -> R.string.cond_words10000
        Achievement.THEME_COMPLETE -> R.string.cond_theme_complete
        Achievement.IN_PLAIN_SIGHT -> R.string.cond_in_plain_sight
        Achievement.HABIT -> R.string.cond_habit
        Achievement.CONSISTENCY -> R.string.cond_consistency
        Achievement.STEADY_HAND -> R.string.cond_steady_hand
        Achievement.BILINGUAL -> R.string.cond_bilingual
        Achievement.IN_THE_RED -> R.string.cond_in_the_red
    },
)

/** What it unlocks (null: only the achievement). */
@Composable
@ReadOnlyComposable
fun Achievement.reward(): String? = when (this) {
    Achievement.FIRST_PUZZLE -> R.string.rew_first_puzzle
    Achievement.WORDS_100 -> R.string.rew_words100
    Achievement.WORDS_1000 -> R.string.rew_words1000
    Achievement.WORDS_10000 -> R.string.rew_words10000
    Achievement.THEME_COMPLETE -> R.string.rew_theme_complete
    Achievement.IN_PLAIN_SIGHT -> R.string.rew_in_plain_sight
    Achievement.HABIT -> R.string.rew_habit
    Achievement.CONSISTENCY -> R.string.rew_consistency
    Achievement.STEADY_HAND -> R.string.rew_steady_hand
    Achievement.BILINGUAL -> null
    Achievement.IN_THE_RED -> R.string.rew_in_the_red
}?.let { stringResource(it) }

val Achievement.icon: ImageVector
    get() = when (this) {
        Achievement.FIRST_PUZZLE -> Icons.Outlined.Check
        Achievement.WORDS_100 -> Icons.Outlined.Pin
        Achievement.WORDS_1000 -> Icons.Outlined.Layers
        Achievement.WORDS_10000 -> Icons.Outlined.AutoStories
        Achievement.THEME_COMPLETE -> Icons.Outlined.WorkspacePremium
        Achievement.IN_PLAIN_SIGHT -> Icons.Outlined.Visibility
        Achievement.HABIT -> Icons.Outlined.LocalFireDepartment
        Achievement.CONSISTENCY -> Icons.Outlined.CalendarMonth
        Achievement.STEADY_HAND -> Icons.Outlined.Schedule
        Achievement.BILINGUAL -> Icons.Outlined.Language
        Achievement.IN_THE_RED -> Icons.Outlined.RemoveCircleOutline
    }

@Composable
@ReadOnlyComposable
fun Marker.title(): String = stringResource(
    when (this) {
        Marker.HIGHLIGHTER -> R.string.marker_highlighter
        Marker.PENCIL -> R.string.marker_pencil
        Marker.INK -> R.string.marker_ink
        Marker.UNDERLINE -> R.string.marker_underline
    },
)

@Composable
@ReadOnlyComposable
fun BoardFont.title(): String = stringResource(
    when (this) {
        BoardFont.ROUNDED -> R.string.font_rounded
        BoardFont.SERIF -> R.string.font_serif
        BoardFont.MONO -> R.string.font_mono
    },
)

@Composable
@ReadOnlyComposable
fun Palette.title(): String = stringResource(
    when (this) {
        Palette.TEAL_CORAL -> R.string.palette_teal_coral
        Palette.SAGE_SAND -> R.string.palette_sage_sand
        Palette.INDIGO_PEACH -> R.string.palette_indigo_peach
        Palette.SAGE_CORAL -> R.string.palette_sage_coral
        Palette.HIGH_CONTRAST -> R.string.palette_high_contrast
    },
)

@Composable
@ReadOnlyComposable
fun AppIconChoice.title(): String = stringResource(if (this == AppIconChoice.GOLD) R.string.icon_gold else R.string.icon_standard)

