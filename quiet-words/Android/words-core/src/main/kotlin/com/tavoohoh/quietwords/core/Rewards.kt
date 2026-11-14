package com.tavoohoh.quietwords.core

import com.tavoohoh.quiet.core.Palette

/**
 * Cosmetic rewards (port of iOS `Rewards.swift`): each one is earned by an achievement, or is
 * always available (`achievement == null`). None changes play, except "In the Red"'s cheaper
 * hints in free play, which is a switch, not a reward to pick.
 */
interface Reward {
    val rawValue: String
    /** Null: always available. */
    val achievement: Achievement?

    fun isUnlocked(progress: PlayerProgress): Boolean = achievement?.let { it in progress.unlocked } ?: true
}

/** The marker a found word stays in. */
enum class Marker(override val rawValue: String, override val achievement: Achievement?) : Reward {
    HIGHLIGHTER("highlighter", null),
    PENCIL("pencil", Achievement.FIRST_PUZZLE),
    INK("ink", Achievement.IN_PLAIN_SIGHT),
    UNDERLINE("underline", Achievement.STEADY_HAND);

    companion object {
        fun fromRaw(raw: String?) = entries.firstOrNull { it.rawValue == raw }
    }
}

/** The letters of the board: rounded by default, serif and mono earned. */
enum class BoardFont(override val rawValue: String, override val achievement: Achievement?) : Reward {
    ROUNDED("rounded", null),
    SERIF("serif", Achievement.THEME_COMPLETE),
    MONO("mono", Achievement.WORDS_10000);

    companion object {
        fun fromRaw(raw: String?) = entries.firstOrNull { it.rawValue == raw }
    }
}

/** The launcher icon. */
enum class AppIconChoice(override val rawValue: String, override val achievement: Achievement?) : Reward {
    STANDARD("standard", null),
    GOLD("gold", Achievement.WORDS_1000);

    companion object {
        fun fromRaw(raw: String?) = entries.firstOrNull { it.rawValue == raw }
    }
}

/** A color theme as a reward. High contrast is accessibility: always available. */
data class PaletteReward(val palette: Palette) : Reward {
    override val rawValue: String get() = palette.rawValue
    override val achievement: Achievement?
        get() = when (palette) {
            Palette.TEAL_CORAL, Palette.HIGH_CONTRAST -> null
            Palette.SAGE_SAND -> Achievement.WORDS_100
            Palette.INDIGO_PEACH -> Achievement.HABIT
            Palette.SAGE_CORAL -> Achievement.CONSISTENCY
        }

    companion object {
        val all: List<PaletteReward> get() = Palette.entries.map(::PaletteReward)
    }
}

/** What the board and the app look like from the player's choices; a choice not unlocked falls back to the default. */
data class Looks(
    val palette: Palette = Palette.TEAL_CORAL,
    val marker: Marker = Marker.HIGHLIGHTER,
    val boardFont: BoardFont = BoardFont.ROUNDED,
    val icon: AppIconChoice = AppIconChoice.STANDARD,
) {
    /** The same choices, each one only if unlocked (e.g. after a reset or on another device). */
    fun effective(progress: PlayerProgress) = Looks(
        palette = if (PaletteReward(palette).isUnlocked(progress)) palette else Palette.TEAL_CORAL,
        marker = if (marker.isUnlocked(progress)) marker else Marker.HIGHLIGHTER,
        boardFont = if (boardFont.isUnlocked(progress)) boardFont else BoardFont.ROUNDED,
        icon = if (icon.isUnlocked(progress)) icon else AppIconChoice.STANDARD,
    )
}

/** Progress shown on an achievement's row in Collection, for the ones that count something. */
sealed interface AchievementStep {
    val fraction: Double

    data class Count(val n: Int, val target: Int) : AchievementStep {
        override val fraction get() = minOf(n.toDouble() / target, 1.0)
    }

    data class Streak(val n: Int, val target: Int) : AchievementStep {
        override val fraction get() = minOf(n.toDouble() / target, 1.0)
    }

    /** The most advanced theme. */
    data class Percent(val value: Double) : AchievementStep {
        override val fraction get() = value
    }

    companion object {
        fun of(achievement: Achievement, progress: PlayerProgress, album: Album, library: ThemeLibrary): AchievementStep? =
            when (achievement) {
                Achievement.WORDS_100, Achievement.WORDS_1000, Achievement.WORDS_10000 -> Count(progress.wordsFound, achievement.target)
                Achievement.HABIT, Achievement.CONSISTENCY -> Streak(progress.bestStreak, achievement.target)
                Achievement.THEME_COMPLETE -> Percent(album.bestThemeFraction(library))
                else -> null
            }
    }
}
