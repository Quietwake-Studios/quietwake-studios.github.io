package com.tavoohoh.quiet.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * One day's challenge result (in one language, for games with several): its best result and
 * how many attempts it had. Port of the family's `DailyRecord` (iOS: Numbers' and Words').
 */
@Serializable
data class DailyRecord(
    val score: Int = 0,
    /** An attempt ended (reached its goal or ran out of moves). */
    val isFinished: Boolean = false,
    /** The day counts for the streak. */
    val isCompleted: Boolean = false,
    /** Time played when it finished (leaderboard tie-break). */
    val seconds: Int? = null,
    /** Attempts started (Quiet Words: any after the first is a replay). */
    val attempts: Int = 0,
) {
    /** Completed beats finished beats started; then the higher score, then the faster time. */
    fun better(other: DailyRecord): DailyRecord {
        fun rank(r: DailyRecord) = Triple(if (r.isCompleted) 2 else if (r.isFinished) 1 else 0, r.score, -(r.seconds ?: Int.MAX_VALUE))
        val cmp = compareValuesBy(rank(this), rank(other), { it.first }, { it.second }, { it.third })
        return if (cmp >= 0) this else other
    }
}

/**
 * The family's local store (iOS: SwiftData in each app's `PersistenceService`): small JSON
 * files written atomically under `directory` (null: in memory, for tests). Games in progress go
 * one file per slot (`games/<slot>.json`), daily results in `dailies.json` and the player's
 * progress in `profile.json`. Preferences stay in SharedPreferences.
 *
 * `dayOf` turns a daily key into its day (`"2026-11-08#es"` → `"2026-11-08"` in Quiet Words;
 * the key is the day in Quiet Numbers), so streaks and the calendar count a day once.
 */
class QuietStore<Game, Profile>(
    private val directory: File?,
    private val gameSerializer: KSerializer<Game>,
    private val profileSerializer: KSerializer<Profile>,
    private val emptyProfile: Profile,
    private val dayOf: (String) -> String? = { it },
) {
    private val games = HashMap<String, Game>()
    /** Daily key → best result and attempts. */
    var dailies: Map<String, DailyRecord> = emptyMap()
        private set
    private var profile = emptyProfile

    init {
        if (directory != null) {
            File(directory, GAMES).listFiles { f -> f.extension == "json" }?.forEach { file ->
                read(file, gameSerializer)?.let { games[file.nameWithoutExtension] = it }
            }
            dailies = read(File(directory, DAILIES), DAILIES_SERIALIZER) ?: emptyMap()
            profile = read(File(directory, PROFILE), profileSerializer) ?: emptyProfile
        }
    }

    /** Days completed (in any language): the streak and the calendar are one. */
    val completedDays: Set<String> get() = dailies.filterValues { it.isCompleted }.keys.mapNotNull(dayOf).toSet()

    fun record(key: String): DailyRecord? = dailies[key]

    // Games in progress

    fun loadGame(slot: String): Game? = games[slot]

    fun saveGame(game: Game, slot: String) {
        games[slot] = game
        directory?.let { write(File(File(it, GAMES), "$slot.json"), gameSerializer, game) }
    }

    fun deleteGame(slot: String) {
        games.remove(slot)
        directory?.let { File(File(it, GAMES), "$slot.json").delete() }
    }

    /** Slots saved with this prefix, without it (e.g. the days with an attempt halfway). */
    fun slots(prefix: String): Set<String> = games.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.toSet()

    // Daily challenge

    fun startAttempt(key: String) {
        val old = dailies[key] ?: DailyRecord()
        putDaily(key, old.copy(attempts = old.attempts + 1))
    }

    fun attempts(key: String): Int = dailies[key]?.attempts ?: 0

    /**
     * An attempt ended: the day keeps the better of it and what it had (completed, then score,
     * then time). Returns whether this is the first completion of the key.
     */
    fun keepBest(key: String, score: Int, isCompleted: Boolean, seconds: Int?): Boolean {
        val old = dailies[key] ?: DailyRecord()
        val attempt = DailyRecord(score, isFinished = true, isCompleted = isCompleted, seconds = seconds, attempts = old.attempts)
        val firstCompletion = isCompleted && !old.isCompleted
        val best = old.better(attempt)
        putDaily(key, if (best === old) old.copy(isFinished = true) else attempt)
        return firstCompletion
    }

    /** Sets a result as is (tests and QA). */
    fun setRecord(key: String, record: DailyRecord) = putDaily(key, record)

    /** Adds results from another device, keeping the better one per key. */
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
        directory?.let { write(File(it, PROFILE), profileSerializer, profile) }
    }

    // Reset

    fun deleteEverything() {
        games.clear()
        dailies = emptyMap()
        profile = emptyProfile
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
        if (!file.exists()) null else runCatching { FORMAT.decodeFromString(serializer, file.readText()) }.getOrNull()

    /** Write to a temporary file, then move it over the old one: a crash never leaves half a file. */
    private fun <T> write(file: File, serializer: KSerializer<T>, value: T) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(FORMAT.encodeToString(serializer, value))
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
        val FORMAT = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
