package com.tavoohoh.quietnumbers.core

import com.tavoohoh.quiet.core.Palette

/**
 * Cosmetic rewards (port of iOS `Rewards.swift`): each one is earned by an achievement, or is
 * always available (`achievement == null`). None changes play; "In the Red"'s cheaper hints
 * (classic only) is a switch, not a reward to pick.
 */
interface Reward {
    val rawValue: String
    /** Null: always available. */
    val achievement: Achievement?

    fun isUnlocked(progress: PlayerProgress): Boolean = achievement?.let { it in progress.unlocked } ?: true
}

/** The color theme (the kit's palettes). The default and High contrast (accessibility) are never locked. */
enum class ThemeChoice(override val rawValue: String, val palette: Palette, override val achievement: Achievement?) : Reward {
    TEAL_CORAL("tealCoral", Palette.TEAL_CORAL, null),
    SAGE_SAND("sageSand", Palette.SAGE_SAND, Achievement.NO_HELP),
    INDIGO_PEACH("indigoPeach", Palette.INDIGO_PEACH, Achievement.CONSISTENCY),
    SAGE_CORAL("sageCoral", Palette.SAGE_CORAL, Achievement.NIGHT_OWL),
    HIGH_CONTRAST("highContrast", Palette.HIGH_CONTRAST, null);

    companion object {
        fun fromRaw(raw: String?) = entries.firstOrNull { it.rawValue == raw }
    }
}

/** What's drawn behind each number. Ghosts never get a tile. */
enum class TileStyle(override val rawValue: String, override val achievement: Achievement?) : Reward {
    PLAIN("plain", null),
    CIRCLE("circle", Achievement.CLEAN_SLATE),
    OUTLINE("outline", Achievement.STAGE_FIVE);

    companion object {
        fun fromRaw(raw: String?) = entries.firstOrNull { it.rawValue == raw }
    }
}

/** The numbers' typeface: rounded by default, serif earned (iOS SF Rounded and New York). */
enum class NumberFont(override val rawValue: String, override val achievement: Achievement?) : Reward {
    ROUNDED("rounded", null),
    SERIF("serif", Achievement.HABIT);

    companion object {
        fun fromRaw(raw: String?) = entries.firstOrNull { it.rawValue == raw }
    }
}

/** What a cleared row does before it collapses. */
enum class ClearEffect(override val rawValue: String, override val achievement: Achievement?) : Reward {
    OUTLINE("outline", null),
    WAVE("wave", Achievement.SWEEPER);

    companion object {
        fun fromRaw(raw: String?) = entries.firstOrNull { it.rawValue == raw }
    }
}

/** The synthesizer voice of the tones. */
enum class SoundPack(override val rawValue: String, override val achievement: Achievement?) : Reward {
    SOFT("soft", null),
    MARIMBA("marimba", Achievement.MONOTONE);

    companion object {
        fun fromRaw(raw: String?) = entries.firstOrNull { it.rawValue == raw }
    }
}

/** The launcher icon. */
enum class AppIconChoice(override val rawValue: String, override val achievement: Achievement?) : Reward {
    STANDARD("standard", null),
    GOLD("gold", Achievement.CENTENNIAL);

    companion object {
        fun fromRaw(raw: String?) = entries.firstOrNull { it.rawValue == raw }
    }
}

/** The player's chosen cosmetics. */
data class Looks(
    val theme: ThemeChoice = ThemeChoice.TEAL_CORAL,
    val tileStyle: TileStyle = TileStyle.PLAIN,
    val numberFont: NumberFont = NumberFont.ROUNDED,
    val clearEffect: ClearEffect = ClearEffect.OUTLINE,
    val soundPack: SoundPack = SoundPack.SOFT,
    val icon: AppIconChoice = AppIconChoice.STANDARD,
) {
    /** The same choices, each one only if unlocked (after a reset or on another device): iOS `AppearanceRoot.available`. */
    fun effective(progress: PlayerProgress) = Looks(
        theme = theme.takeIf { it.isUnlocked(progress) } ?: ThemeChoice.TEAL_CORAL,
        tileStyle = tileStyle.takeIf { it.isUnlocked(progress) } ?: TileStyle.PLAIN,
        numberFont = numberFont.takeIf { it.isUnlocked(progress) } ?: NumberFont.ROUNDED,
        clearEffect = clearEffect.takeIf { it.isUnlocked(progress) } ?: ClearEffect.OUTLINE,
        soundPack = soundPack.takeIf { it.isUnlocked(progress) } ?: SoundPack.SOFT,
        icon = icon.takeIf { it.isUnlocked(progress) } ?: AppIconChoice.STANDARD,
    )

    companion object {
        /** Ids saved by older iOS versions (the Snapshot never carries them, but the rule is the same): see iOS `ThemeChoice.legacyIDs`. */
        val legacyThemes = mapOf(
            "paper" to ThemeChoice.TEAL_CORAL, "slate" to ThemeChoice.SAGE_SAND, "sand" to ThemeChoice.INDIGO_PEACH,
            "midnight" to ThemeChoice.SAGE_CORAL, "ember" to ThemeChoice.HIGH_CONTRAST,
        )
    }
}

/** Every achievement that unlocks something (Collection › Achievements says what): the rewards' unlocks plus "In the Red"'s cheaper hints. */
val Achievement.hasReward: Boolean
    get() = this == Achievement.IN_THE_RED || (
        ThemeChoice.entries + TileStyle.entries + NumberFont.entries + ClearEffect.entries + SoundPack.entries + AppIconChoice.entries
        ).any { it.achievement == this }

/** How far a counted achievement has come (Collection › Achievements). Null: one-off. */
data class AchievementStep(val kind: Kind, val value: Int, val target: Int) {
    enum class Kind { STREAK, ROWS, STAGE, DAILIES, PAIRS }

    val fraction: Double get() = minOf(value.toDouble() / maxOf(target, 1), 1.0)
    val shown: Int get() = minOf(value, target)

    companion object {
        fun of(achievement: Achievement, progress: PlayerProgress): AchievementStep? {
            val t = achievement.target
            return when (achievement) {
                Achievement.CONSISTENCY, Achievement.HABIT -> AchievementStep(Kind.STREAK, progress.bestStreak, t)
                Achievement.SWEEPER -> AchievementStep(Kind.ROWS, progress.rowsCleared, t)
                Achievement.STAGE_FIVE -> AchievementStep(Kind.STAGE, progress.highestStage, t)
                Achievement.CENTENNIAL -> AchievementStep(Kind.DAILIES, progress.dailiesCompleted, t)
                Achievement.MONOTONE -> AchievementStep(Kind.PAIRS, progress.bestEqualPairsRun, t)
                Achievement.FIRST_STEP, Achievement.CLEAN_SLATE, Achievement.NO_HELP, Achievement.NIGHT_OWL, Achievement.IN_THE_RED -> null
            }
        }
    }
}
