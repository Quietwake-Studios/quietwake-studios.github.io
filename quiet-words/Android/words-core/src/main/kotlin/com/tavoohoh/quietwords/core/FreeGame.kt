package com.tavoohoh.quietwords.core

/**
 * Free play: a new board of a theme, level and words language from a key. Same call as the
 * iOS app's `AppModel.newFree` (key `"free-<random>"`), kept here so the app has no rules.
 */
object FreeGame {
    fun board(themeID: String, level: Level, language: WordLanguage, key: String, library: ThemeLibrary = ThemeLibrary.shared): Board? {
        val words = library.theme(themeID)?.words(language) ?: return null
        return Generator.board(key, words.all, words.hidden, language, level, blocklist = library.blocklist[language] ?: emptyList())
    }

    fun session(
        themeID: String, level: Level, language: WordLanguage, key: String,
        library: ThemeLibrary = ThemeLibrary.shared, scoring: Scoring = Scoring.STANDARD,
    ): GameSession? {
        val board = board(themeID, level, language, key, library) ?: return null
        return GameSession(GameSession.Mode.Free, themeID, level, board, scoring)
    }

    /** "Surprise": any theme with words in this language, picked by `pick(count)` (the app passes a random index). */
    fun surpriseThemeID(language: WordLanguage, library: ThemeLibrary = ThemeLibrary.shared, pick: (Int) -> Int): String? {
        val themes = library.themes(language)
        if (themes.isEmpty()) return null
        return themes[pick(themes.size).coerceIn(0, themes.size - 1)].id
    }
}
