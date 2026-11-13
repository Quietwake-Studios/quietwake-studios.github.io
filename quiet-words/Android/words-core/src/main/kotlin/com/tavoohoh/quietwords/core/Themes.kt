package com.tavoohoh.quietwords.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One theme in one language: its name, hidden words and the word groups. */
data class ThemeWords(
    val name: String,
    /** Board form (upper case), 4–9 letters. */
    val hidden: List<String>,
    val common: List<Word>,
    val crafted: List<Word>,
    val rare: List<Word>,
    val regional: List<Word>,
) {
    val all: List<Word> get() = common + crafted + rare + regional

    fun words(group: WordGroup): List<Word> = when (group) {
        WordGroup.COMMON -> common
        WordGroup.CRAFTED -> crafted
        WordGroup.RARE -> rare
        WordGroup.REGIONAL -> regional
    }

    /** The first words of each list as the daily lock counted them; all of them without a lock. */
    fun prefix(counts: DailyLock.Counts?): ThemeWords {
        if (counts == null) return this
        return ThemeWords(name, hidden.take(counts.hidden), common.take(counts.common), crafted.take(counts.crafted),
            rare.take(counts.rare), regional.take(counts.regional))
    }
}

/** A theme: one JSON file in the iOS package's `Resources/Themes/` (its name is the id). */
data class WordTheme(
    val id: String,
    /** SF Symbol name on iOS; Android maps it to its own icon. */
    val symbol: String,
    val languages: Map<WordLanguage, ThemeWords>,
) {
    fun words(language: WordLanguage): ThemeWords? = languages[language]
    fun name(language: WordLanguage): String = languages[language]?.name ?: id

    /** Both languages written: only then it can join the daily challenge. */
    val isBilingual: Boolean get() = languages.size == WordLanguage.entries.size

    companion object {
        fun parse(id: String, json: String): WordTheme {
            val raw = JSON.decodeFromString(RawTheme.serializer(), json)
            val languages = linkedMapOf<WordLanguage, ThemeWords>()
            for ((language, part) in listOf(WordLanguage.EN to raw.en, WordLanguage.ES to raw.es)) {
                if (part == null) continue
                fun words(list: List<String>?, group: WordGroup) = (list ?: emptyList()).map { Word(it, language, group) }
                languages[language] = ThemeWords(
                    name = part.name,
                    hidden = (part.hidden ?: emptyList()).map { WordText.normalize(it, language) },
                    common = words(part.common, WordGroup.COMMON), crafted = words(part.crafted, WordGroup.CRAFTED),
                    rare = words(part.rare, WordGroup.RARE), regional = words(part.regional, WordGroup.REGIONAL),
                )
            }
            return WordTheme(id, raw.symbol, languages)
        }
    }

    @Serializable
    private data class RawTheme(val symbol: String, val en: Part? = null, val es: Part? = null) {
        @Serializable
        data class Part(
            val name: String,
            val hidden: List<String>? = null,
            val common: List<String>? = null,
            val crafted: List<String>? = null,
            val rare: List<String>? = null,
            val regional: List<String>? = null,
        )
    }
}

internal val JSON = Json { ignoreUnknownKeys = true }

/** Every theme, sorted by id, plus the blocklists and the daily lock. */
class ThemeLibrary(themes: List<WordTheme>, val blocklist: Map<WordLanguage, List<String>>, val lock: DailyLock) {
    val themes: List<WordTheme> = themes.sortedWith { a, b -> Generator.compareScalars(a.id, b.id) }

    fun theme(id: String): WordTheme? = themes.firstOrNull { it.id == id }

    /** Themes with words in this language (free play). */
    fun themes(language: WordLanguage): List<WordTheme> = themes.filter { it.languages[language] != null }

    companion object {
        /** The content shipped with the app: the iOS package's resources (see words-core/build.gradle.kts). */
        val shared: ThemeLibrary by lazy { load { path -> ThemeLibrary::class.java.classLoader.getResource(path)?.readText() } }

        /**
         * Loads from a resource reader. `Themes/index.txt` (generated at build time) lists the
         * theme files; `daily-lock.json` in that folder is the lock, not a theme.
         */
        fun load(read: (String) -> String?): ThemeLibrary {
            val index = read("Themes/index.txt") ?: error("Themes/index.txt missing")
            val themes = mutableListOf<WordTheme>()
            var lock = DailyLock(emptyList())
            for (file in index.lines().map { it.trim() }.filter { it.endsWith(".json") }) {
                val id = file.removeSuffix(".json")
                val json = read("Themes/$file") ?: error("Themes/$file missing")
                if (id == DailyLock.FILE_NAME) lock = DailyLock.parse(json) else themes += WordTheme.parse(id, json)
            }
            val blocklist = WordLanguage.entries.associateWith { language ->
                (read("Blocklist/${language.rawValue}.txt") ?: "").split("\n").mapNotNull { line ->
                    val word = line.split("#", limit = 2).first().trim { Character.isSpaceChar(it) || it == '\t' }
                    if (word.isEmpty()) null else WordText.normalize(word, language)
                }
            }
            return ThemeLibrary(themes, blocklist, lock)
        }
    }
}
