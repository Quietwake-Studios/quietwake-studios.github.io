package com.tavoohoh.quietwords.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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

/** One day's challenge in one language: its best result and how many attempts it had. */
@Serializable
data class DailyRecord(
    val score: Int = 0,
    val isCompleted: Boolean = false,
    /** Time played when it was won (leaderboard tie-break). */
    val seconds: Int? = null,
    /** Attempts started; any after the first is a replay. */
    val attempts: Int = 0,
) {
    /** Completed beats started; then the higher score, then the faster time. */
    fun better(other: DailyRecord): DailyRecord {
        fun rank(r: DailyRecord) = Triple(if (r.isCompleted) 1 else 0, r.score, -(r.seconds ?: Int.MAX_VALUE))
        val a = rank(this)
        val b = rank(other)
        val cmp = compareValuesBy(a, b, { it.first }, { it.second }, { it.third })
        return if (cmp >= 0) this else other
    }
}

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
 * Local storage (port of iOS `PersistenceService`, SwiftData there): small JSON files written
 * atomically under `directory` (null: in memory, for tests). Games in progress go one file per
 * slot (`games/free.json`, `games/daily-2026-11-08#es.json`); daily results in `dailies.json`;
 * progress and album in `profile.json`. Preferences stay in SharedPreferences.
 */
class WordsStore(private val directory: File?) {
    private val games = HashMap<String, GameSession>()
    /** "2026-11-08#es" → best result and attempts. */
    var dailies: Map<String, DailyRecord> = emptyMap()
        private set
    private var profile = Profile()

    init {
        if (directory != null) {
            File(directory, GAMES).listFiles { f -> f.extension == "json" }?.forEach { file ->
                read(file, GameSession.serializer())?.let { games[file.nameWithoutExtension] = it }
            }
            dailies = read(File(directory, DAILIES), DAILIES_SERIALIZER) ?: emptyMap()
            profile = read(File(directory, PROFILE), Profile.serializer()) ?: Profile()
        }
    }

    /** Days completed in either language: the streak and the calendar are one. */
    val completedDays: Set<String>
        get() = dailies.filterValues { it.isCompleted }.keys.mapNotNull { DailyID.fromKey(it)?.dayKey }.toSet()

    /** Day-and-language attempts saved halfway. */
    val dailiesInProgress: Set<DailyID>
        get() = games.keys.filter { it.startsWith(Slot.DAILY_PREFIX) }.mapNotNull { DailyID.fromKey(it.removePrefix(Slot.DAILY_PREFIX)) }.toSet()

    /** Days with an attempt halfway in either language (the calendar's dot). */
    val daysInProgress: Set<String> get() = dailiesInProgress.map { it.dayKey }.toSet()

    fun record(id: DailyID): DailyRecord? = dailies[id.key]

    // Games in progress

    fun loadGame(slot: Slot): GameSession? = games[slot.rawValue]?.copy()

    fun saveGame(session: GameSession, slot: Slot) {
        val copy = session.copy()
        games[slot.rawValue] = copy
        directory?.let { write(File(File(it, GAMES), "${slot.rawValue}.json"), GameSession.serializer(), copy) }
    }

    fun deleteGame(slot: Slot) {
        games.remove(slot.rawValue)
        directory?.let { File(File(it, GAMES), "${slot.rawValue}.json").delete() }
    }

    /** Drops daily games for days that can no longer be replayed. */
    fun deleteDailyGames(before: String) {
        for (id in dailiesInProgress) if (id.dayKey < before) deleteGame(Slot.Daily(id))
    }

    // Daily challenge

    fun startAttempt(id: DailyID) {
        val old = dailies[id.key] ?: DailyRecord()
        putDaily(id.key, old.copy(attempts = old.attempts + 1))
    }

    fun attempts(id: DailyID): Int = dailies[id.key]?.attempts ?: 0

    /**
     * A win: keeps the best of the day in that language (higher score, then faster).
     * Returns whether it's that language's first win of the day.
     */
    fun recordWin(id: DailyID, score: Int, seconds: Int): Boolean {
        val old = dailies[id.key] ?: DailyRecord()
        val isFirst = !old.isCompleted
        val better = isFirst || score > old.score || (score == old.score && seconds < (old.seconds ?: Int.MAX_VALUE))
        val new = if (better) old.copy(score = score, seconds = seconds, isCompleted = true) else old.copy(isCompleted = true)
        putDaily(id.key, new)
        return isFirst
    }

    /** Adds daily results from another device, keeping the better result per day and language. */
    fun mergeDailies(remote: Map<String, DailyRecord>) {
        val merged = dailies.toMutableMap()
        for ((key, record) in remote) {
            val local = merged[key]
            merged[key] = if (local == null) record else local.better(record).copy(attempts = maxOf(local.attempts, record.attempts))
        }
        dailies = merged
        saveDailies()
    }

    // Profile

    fun loadProfile(): Profile = profile

    fun saveProfile(profile: Profile) {
        this.profile = profile
        directory?.let { write(File(it, PROFILE), Profile.serializer(), profile) }
    }

    // Reset

    fun deleteEverything() {
        games.clear()
        dailies = emptyMap()
        profile = Profile()
        directory?.let { File(it, GAMES).deleteRecursively(); File(it, DAILIES).delete(); File(it, PROFILE).delete() }
    }

    // Private

    private fun putDaily(key: String, record: DailyRecord) {
        dailies = dailies + (key to record)
        saveDailies()
    }

    private fun saveDailies() {
        directory?.let { write(File(it, DAILIES), DAILIES_SERIALIZER, dailies) }
    }

    private fun <T> read(file: File, serializer: KSerializer<T>): T? =
        if (!file.exists()) null else runCatching { STORE_JSON.decodeFromString(serializer, file.readText()) }.getOrNull()

    /** Write to a temporary file, then move it over the old one: a crash never leaves half a file. */
    private fun <T> write(file: File, serializer: KSerializer<T>, value: T) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(STORE_JSON.encodeToString(serializer, value))
        runCatching {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        private const val GAMES = "games"
        private const val DAILIES = "dailies.json"
        private const val PROFILE = "profile.json"
        private val DAILIES_SERIALIZER = MapSerializer(String.serializer(), DailyRecord.serializer())
        private val STORE_JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
