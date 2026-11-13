package com.tavoohoh.quietwords.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Achievements. Each one unlocks a cosmetic reward (or nothing, for Bilingual); none touches play. */
@Serializable
enum class Achievement(val rawValue: String) {
    @SerialName("firstPuzzle") FIRST_PUZZLE("firstPuzzle"),
    @SerialName("words100") WORDS_100("words100"),
    @SerialName("words1000") WORDS_1000("words1000"),
    @SerialName("words10000") WORDS_10000("words10000"),
    @SerialName("themeComplete") THEME_COMPLETE("themeComplete"),
    @SerialName("inPlainSight") IN_PLAIN_SIGHT("inPlainSight"),
    @SerialName("habit") HABIT("habit"),
    @SerialName("consistency") CONSISTENCY("consistency"),
    @SerialName("steadyHand") STEADY_HAND("steadyHand"),
    @SerialName("bilingual") BILINGUAL("bilingual"),
    @SerialName("inTheRed") IN_THE_RED("inTheRed");

    /** Amount needed for counted achievements; 1 for one-off ones. */
    val target: Int get() = when (this) {
        WORDS_100 -> 100
        WORDS_1000 -> 1_000
        WORDS_10000 -> 10_000
        HABIT -> 7
        CONSISTENCY -> 30
        else -> 1
    }

    val isHidden: Boolean get() = this == BILINGUAL || this == IN_THE_RED

    companion object {
        /** "Steady Hand" needs a Hard puzzle in less than this. */
        const val STEADY_HAND_SECONDS = 600
    }
}

/** Something that happened in play, fed to `PlayerProgress`. */
sealed interface ProgressEvent {
    data class Won(val summary: GameSummary, val isFree: Boolean) : ProgressEvent
    data class WordsFound(val count: Int) : ProgressEvent
    data class ScoreChanged(val score: Int) : ProgressEvent
    data object ThemeCompleted : ProgressEvent
    data class Abandoned(val played: Boolean) : ProgressEvent
    data class DailyCompleted(val longestStreak: Int) : ProgressEvent
    data object BothLanguages : ProgressEvent
    data class CompletedLate(val dayKey: String) : ProgressEvent
}

/** Lifetime counters and unlocked achievements. Stored locally; works without Play Games. */
@Serializable
data class PlayerProgress(
    val unlocked: Set<Achievement> = emptySet(),
    val gamesWon: Int = 0,
    val gamesPlayed: Int = 0,
    val wordsFound: Int = 0,
    val dailiesCompleted: Int = 0,
    val bestStreak: Int = 0,
    /** Best free-play score per level and language ("hard-es"). */
    val bestScores: Map<String, Int> = emptyMap(),
    val lateDays: Set<String> = emptySet(),
) {
    fun bestScore(level: Level, language: WordLanguage): Int = bestScores[key(level, language)] ?: 0

    /** Applies an event; returns the new progress and the achievements it unlocked. */
    fun record(event: ProgressEvent): Pair<PlayerProgress, List<Achievement>> {
        var p = this
        val hits = mutableListOf<Achievement>()
        when (event) {
            is ProgressEvent.Won -> {
                val s = event.summary
                p = p.copy(gamesWon = gamesWon + 1, gamesPlayed = gamesPlayed + 1)
                hits += Achievement.FIRST_PUZZLE
                if (s.hiddenGuessed && s.hintsUsed == 0) hits += Achievement.IN_PLAIN_SIGHT
                if (s.level == Level.HARD && s.hintsUsed == 0 && s.seconds < Achievement.STEADY_HAND_SECONDS) hits += Achievement.STEADY_HAND
                if (event.isFree) {
                    val k = key(s.level, s.language)
                    p = p.copy(bestScores = p.bestScores + (k to maxOf(p.bestScores[k] ?: 0, s.score)))
                }
            }
            is ProgressEvent.WordsFound -> p = p.copy(wordsFound = wordsFound + event.count)
            is ProgressEvent.ScoreChanged -> if (event.score < 0) hits += Achievement.IN_THE_RED
            ProgressEvent.ThemeCompleted -> hits += Achievement.THEME_COMPLETE
            is ProgressEvent.Abandoned -> if (event.played) p = p.copy(gamesPlayed = gamesPlayed + 1)
            is ProgressEvent.DailyCompleted ->
                p = p.copy(dailiesCompleted = dailiesCompleted + 1, bestStreak = maxOf(bestStreak, event.longestStreak))
            ProgressEvent.BothLanguages -> hits += Achievement.BILINGUAL
            is ProgressEvent.CompletedLate -> p = p.copy(lateDays = lateDays + event.dayKey)
        }
        hits += Achievement.entries.filter { it.target > 1 && p.count(it) >= it.target }
        val unlocked = LinkedHashSet(p.unlocked)
        val new = hits.filter { unlocked.add(it) }
        return p.copy(unlocked = unlocked) to new
    }

    /** Combines progress from two devices: highest counters, union of achievements. */
    fun merged(other: PlayerProgress): PlayerProgress = PlayerProgress(
        unlocked = unlocked + other.unlocked,
        gamesWon = maxOf(gamesWon, other.gamesWon),
        gamesPlayed = maxOf(gamesPlayed, other.gamesPlayed),
        wordsFound = maxOf(wordsFound, other.wordsFound),
        dailiesCompleted = maxOf(dailiesCompleted, other.dailiesCompleted),
        bestStreak = maxOf(bestStreak, other.bestStreak),
        bestScores = (bestScores.keys + other.bestScores.keys).associateWith { maxOf(bestScores[it] ?: Int.MIN_VALUE, other.bestScores[it] ?: Int.MIN_VALUE) },
        lateDays = lateDays + other.lateDays,
    )

    /** Progress towards an achievement, 0..1. */
    fun progress(achievement: Achievement): Double {
        if (achievement in unlocked) return 1.0
        return minOf(count(achievement).toDouble() / achievement.target, 1.0)
    }

    private fun count(achievement: Achievement): Int = when (achievement) {
        Achievement.WORDS_100, Achievement.WORDS_1000, Achievement.WORDS_10000 -> wordsFound
        Achievement.HABIT, Achievement.CONSISTENCY -> bestStreak
        else -> 0
    }

    companion object {
        fun key(level: Level, language: WordLanguage): String = "${level.rawValue}-${language.rawValue}"

        /** Starts with achievements already unlocked (QA and screenshots). */
        fun withUnlocked(unlocked: Set<Achievement>) = PlayerProgress(unlocked = unlocked)
    }
}

/** Every word found, per theme and language, by its board form. One album per language. */
@Serializable
data class Album(
    /** "ocean/es" → board forms. */
    val words: Map<String, Set<String>> = emptyMap(),
) {
    /** Adds a game's words; returns the new album and how many were new. */
    fun add(letters: List<String>, theme: String, language: WordLanguage): Pair<Album, Int> {
        val k = key(theme, language)
        val before = words[k] ?: emptySet()
        val after = before + letters
        return copy(words = words + (k to after)) to (after.size - before.size)
    }

    fun found(theme: WordTheme, language: WordLanguage): Set<String> {
        val all = theme.words(language)?.all?.map { it.letters }?.toSet() ?: emptySet()
        return (words[key(theme.id, language)] ?: emptySet()).intersect(all)
    }

    data class Count(val found: Int, val total: Int)

    /** Found / total for the theme in this language ("Ocean 87/152"). */
    fun count(theme: WordTheme, language: WordLanguage): Count {
        val total = (theme.words(language)?.all?.map { it.letters }?.toSet() ?: emptySet()).size
        return Count(found(theme, language).size, total)
    }

    fun isComplete(theme: WordTheme, language: WordLanguage): Boolean {
        val c = count(theme, language)
        return c.total > 0 && c.found == c.total
    }

    data class Total(val found: Int, val total: Int, val badges: Int)

    fun total(language: WordLanguage, library: ThemeLibrary): Total {
        var found = 0
        var total = 0
        var badges = 0
        for (theme in library.themes(language)) {
            val c = count(theme, language)
            found += c.found
            total += c.total
            if (c.total > 0 && c.found == c.total) badges += 1
        }
        return Total(found, total, badges)
    }

    /** How far the most advanced theme is, in either language, 0..1. */
    fun bestThemeFraction(library: ThemeLibrary): Double =
        WordLanguage.entries.flatMap { language ->
            library.themes(language).map { theme ->
                val c = count(theme, language)
                if (c.total > 0) c.found.toDouble() / c.total else 0.0
            }
        }.maxOrNull() ?: 0.0

    fun merged(other: Album): Album =
        Album((words.keys + other.words.keys).associateWith { (words[it] ?: emptySet()) + (other.words[it] ?: emptySet()) })

    companion object {
        fun key(theme: String, language: WordLanguage) = "$theme/${language.rawValue}"
    }
}
