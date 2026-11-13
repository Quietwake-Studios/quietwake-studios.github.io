package com.tavoohoh.quietwords.core

import com.tavoohoh.quiet.core.DailyKey
import com.tavoohoh.quiet.core.FNV1a
import com.tavoohoh.quiet.core.SeededRandom
import kotlinx.serialization.Serializable

/**
 * Freezes what the daily challenge uses so new words never change a day already played.
 * `Themes/daily-lock.json` (written by `scripts/lock-daily.py` on iOS) applies to both platforms.
 */
class DailyLock(entries: List<Entry>) {
    val entries: List<Entry> = entries.sortedBy { it.from }

    @Serializable
    data class Counts(val common: Int, val crafted: Int, val rare: Int, val regional: Int = 0, val hidden: Int)

    @Serializable
    data class Entry(
        /** First day key it applies to. */
        val from: String,
        /** WordTheme id → language code → counts. */
        val themes: Map<String, Map<String, Counts>>,
    )

    /** The entry in force on a day (day keys sort as text). */
    fun entry(dayKey: String): Entry? = entries.lastOrNull { it.from <= dayKey } ?: entries.firstOrNull()

    override fun equals(other: Any?) = other is DailyLock && other.entries == entries
    override fun hashCode() = entries.hashCode()

    companion object {
        const val FILE_NAME = "daily-lock"

        fun parse(json: String): DailyLock = DailyLock(JSON.decodeFromString(Raw.serializer(), json).entries)
    }

    @Serializable
    private class Raw(val entries: List<Entry>)
}

/** Today's board: the same for everyone on a day key and language, on iOS and Android. */
object DailyChallenge {
    /** Without a lock, the rotation starts here with every bilingual theme and all its words. */
    const val UNLOCKED_EPOCH = "2026-11-01"

    /** Monday and Tuesday Gentle, Wednesday to Friday Medium, the weekend Hard. */
    fun level(dayKey: String): Level = when (DailyKey.weekday(dayKey) ?: 3) {
        1, 2 -> Level.GENTLE
        3, 4, 5 -> Level.MEDIUM
        else -> Level.HARD
    }

    /** Sunday: Hard with a long hidden word. */
    fun hasLongHidden(dayKey: String): Boolean = DailyKey.weekday(dayKey) == 7

    /** The day's theme: a fixed order reshuffled once per round, never the same theme two days running. */
    fun themeID(dayKey: String, library: ThemeLibrary = ThemeLibrary.shared): String? {
        val (ids, epoch) = rotation(dayKey, library)
        if (ids.isEmpty()) return null
        val index = DailyKey.daysBetween(epoch, dayKey) ?: return null
        val round = Math.floorDiv(index, ids.size)
        return order(ids, round, epoch)[Math.floorMod(index, ids.size)]
    }

    fun board(dayKey: String, language: WordLanguage, library: ThemeLibrary = ThemeLibrary.shared): Board? {
        val id = themeID(dayKey, library) ?: return null
        val theme = library.theme(id) ?: return null
        val words = theme.words(language)?.prefix(counts(id, language, dayKey, library)) ?: return null
        val level = level(dayKey)
        val (ids, epoch) = rotation(dayKey, library)
        val appearance = maxOf(0, Math.floorDiv(DailyKey.daysBetween(epoch, dayKey) ?: 0, maxOf(ids.size, 1)))
        return Generator.board(
            key = "$dayKey#${language.rawValue}",
            pool = dayPool(words, id, language, level, appearance, epoch),
            hidden = words.hidden, language = language, level = level, longHidden = hasLongHidden(dayKey),
            blocklist = library.blocklist[language] ?: emptyList(),
        )
    }

    fun session(dayKey: String, language: WordLanguage, library: ThemeLibrary = ThemeLibrary.shared): GameSession? {
        val board = board(dayKey, language, library) ?: return null
        val id = themeID(dayKey, library) ?: return null
        return GameSession(GameSession.Mode.Daily(dayKey), id, level(dayKey), board)
    }

    /** Each theme walks its list in a seeded order; each appearance starts further along. */
    internal fun dayPool(words: ThemeWords, id: String, language: WordLanguage, level: Level, appearance: Int, epoch: String): List<Word> {
        val eligible = Generator.eligible(words.all, level, language)
        if (eligible.isEmpty()) return emptyList()
        val rng = SeededRandom(FNV1a.hash("words-$id-${language.rawValue}-$epoch"))
        val walk = Generator.shuffled(eligible, rng)
        val size = minOf(walk.size, maxOf(level.typicalWords * 4, 60))
        val start = (appearance * level.typicalWords) % walk.size
        return (0 until size).map { walk[(start + it) % walk.size] }
    }

    private fun rotation(dayKey: String, library: ThemeLibrary): Pair<List<String>, String> {
        val entry = library.lock.entry(dayKey)
        if (entry != null) {
            return entry.themes.keys.filter { library.theme(it)?.isBilingual == true }.sortedWith(Generator::compareScalars) to entry.from
        }
        return library.themes.filter { it.isBilingual }.map { it.id } to UNLOCKED_EPOCH
    }

    private fun counts(id: String, language: WordLanguage, dayKey: String, library: ThemeLibrary): DailyLock.Counts? =
        library.lock.entry(dayKey)?.themes?.get(id)?.get(language.rawValue)

    private fun order(ids: List<String>, round: Int, epoch: String): List<String> {
        fun shuffledRound(r: Int) = Generator.shuffled(ids, SeededRandom(FNV1a.hash("round-$epoch-$r")))
        val current = shuffledRound(round).toMutableList()
        if (ids.size > 1 && current.first() == shuffledRound(round - 1).last()) {
            val tmp = current[0]
            current[0] = current[1]
            current[1] = tmp
        }
        return current
    }
}
