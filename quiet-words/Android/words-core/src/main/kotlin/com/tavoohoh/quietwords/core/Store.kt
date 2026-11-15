package com.tavoohoh.quietwords.core

import kotlinx.serialization.Serializable
import com.tavoohoh.quiet.core.QuietStore
import java.io.File

/** Day and language of a daily record ("2026-11-08#es"). */
data class DailyID(val dayKey: String, val language: WordLanguage) {
    val key: String get() = "$dayKey#${language.rawValue}"

    companion object {
        fun fromKey(key: String): DailyID? {
            val parts = key.split("#")
            if (parts.size != 2) return null
            val language = WordLanguage.fromRaw(parts[1]) ?: return null
            return DailyID(parts[0], language)
        }
    }
}

/** One day's challenge result in one language: the family's `DailyRecord` (QuietKit). */
typealias DailyRecord = com.tavoohoh.quiet.core.DailyRecord

/** What's saved besides games: counters, achievements and every word found. */
@Serializable
data class Profile(val progress: PlayerProgress = PlayerProgress(), val album: Album = Album()) {
    fun merged(other: Profile) = Profile(progress.merged(other.progress), album.merged(other.album))
}

/** Where a game in progress is kept. */
sealed interface Slot {
    val rawValue: String

    data object Free : Slot {
        override val rawValue = "free"
    }

    /** The attempt in progress at one day's challenge in one language: always the latest. */
    data class Daily(val id: DailyID) : Slot {
        override val rawValue get() = DAILY_PREFIX + id.key
    }

    companion object {
        const val DAILY_PREFIX = "daily-"
    }
}

/**
 * Local storage (port of iOS `PersistenceService`), on the family's `QuietStore` (QuietKit):
 * games in progress one file per slot (`games/free.json`, `games/daily-2026-11-08#es.json`),
 * daily results per day and language in `dailies.json`, progress and album in `profile.json`.
 */
class WordsStore(directory: File?) {
    private val store = QuietStore(directory, GameSession.serializer(), Profile.serializer(), Profile()) { DailyID.fromKey(it)?.dayKey }

    /** "2026-11-08#es" → best result and attempts. */
    val dailies: Map<String, DailyRecord> get() = store.dailies

    /** Days completed in either language: the streak and the calendar are one. */
    val completedDays: Set<String> get() = store.completedDays

    /** Day-and-language attempts saved halfway. */
    val dailiesInProgress: Set<DailyID> get() = store.slots(Slot.DAILY_PREFIX).mapNotNull(DailyID::fromKey).toSet()

    /** Days with an attempt halfway in either language (the calendar's dot). */
    val daysInProgress: Set<String> get() = dailiesInProgress.map { it.dayKey }.toSet()

    fun record(id: DailyID): DailyRecord? = store.record(id.key)

    fun loadGame(slot: Slot): GameSession? = store.loadGame(slot.rawValue)?.copy()

    fun saveGame(session: GameSession, slot: Slot) = store.saveGame(session.copy(), slot.rawValue)

    fun deleteGame(slot: Slot) = store.deleteGame(slot.rawValue)

    /** Drops daily games for days that can no longer be replayed. */
    fun deleteDailyGames(before: String) {
        for (id in dailiesInProgress) if (id.dayKey < before) deleteGame(Slot.Daily(id))
    }

    fun startAttempt(id: DailyID) = store.startAttempt(id.key)

    fun attempts(id: DailyID): Int = store.attempts(id.key)

    /** A win: keeps the best of the day in that language. Returns whether it's that language's first win of the day. */
    fun recordWin(id: DailyID, score: Int, seconds: Int): Boolean = store.keepBest(id.key, score, isCompleted = true, seconds = seconds)

    /** Adds daily results from another device, keeping the better result per day and language. */
    fun mergeDailies(remote: Map<String, DailyRecord>) = store.mergeDailies(remote)

    fun loadProfile(): Profile = store.loadProfile()

    fun saveProfile(profile: Profile) = store.saveProfile(profile)

    fun deleteEverything() = store.deleteEverything()
}
