package com.tavoohoh.quietnumbers.core

import com.tavoohoh.quiet.core.DailyKey
import com.tavoohoh.quiet.core.DailyRecord
import com.tavoohoh.quiet.core.QuietStore
import com.tavoohoh.quiet.core.Streaks
import java.io.File

/** Which game is on screen. */
sealed interface GameKind {
    data object Classic : GameKind

    /**
     * An attempt at one day's challenge, as many as the player wants (2026-11-02). The day keeps
     * its best result; completing it counts for the streak; only today's go to the leaderboard.
     */
    data class Daily(val dayKey: String) : GameKind
}

/** A game ready to show: `isReplay` when the day was already played to the end before. */
data class Started(val session: GameSession, val kind: GameKind, val isReplay: Boolean = false)

/** Saved games, daily results and progress on the family's `QuietStore` (iOS `PersistenceService`). */
class NumbersStore(directory: File?) {
    val store = QuietStore(directory, GameSession.Saved.serializer(), PlayerProgress.serializer(), PlayerProgress())

    val dailies: Map<String, DailyRecord> get() = store.dailies
    val completedDays: Set<String> get() = store.completedDays
    /** Days with an attempt saved halfway (for "Continue" and the calendar). */
    val daysInProgress: Set<String> get() = store.slots(DAY_PREFIX)

    fun loadGame(slot: String): GameSession? = store.loadGame(slot)?.let(GameSession::restore)
    fun saveGame(session: GameSession, slot: String) = store.saveGame(session.saved(), slot)
    fun deleteGame(slot: String) = store.deleteGame(slot)

    /** Drops attempts left halfway on days that can no longer be played. */
    fun deleteDayGames(before: String) {
        for (day in daysInProgress) if (day < before) deleteGame(day(day))
    }

    /** A finished attempt: the day keeps the better of it and what it had. */
    fun keepBestDaily(dayKey: String, score: Int, isCompleted: Boolean, seconds: Int?) = store.keepBest(dayKey, score, isCompleted, seconds)

    /** Sets a finished result as is (tests and QA: iOS `finishDaily`). */
    fun finishDaily(dayKey: String, score: Int, isCompleted: Boolean, seconds: Int? = null) =
        store.setRecord(dayKey, DailyRecord(score, isFinished = true, isCompleted = isCompleted, seconds = seconds))

    fun mergeDailies(remote: Map<String, DailyRecord>) = store.mergeDailies(remote)

    companion object {
        const val CLASSIC = "classic"
        /** "practice-" since iOS 1.0, kept so the slots read the same on both platforms. */
        const val DAY_PREFIX = "practice-"
        fun day(dayKey: String) = DAY_PREFIX + dayKey
    }
}

/**
 * The rules around games without UI (port of the non-UI half of iOS `AppModel`): the saved
 * classic game, daily attempts (today and the last 30 days, unlimited; the saved one is always
 * the latest), results, streaks and progress. The app wraps it with Compose state; JVM tests
 * drive it directly (`NumbersPlayTests`, from iOS `AppModelTests`).
 */
class NumbersPlay(
    val store: NumbersStore,
    /** Today's day key (the app's clock; fixed in debug launches and tests). */
    private val today: () -> String,
    /** The local hour now ("Night Owl"). */
    private val hour: () -> Int = { java.time.LocalTime.now().hour },
    /** "Cheaper hints" switch in Collection (Phase 4); on by default. */
    private val cheapHintsEnabled: () -> Boolean = { true },
) {
    var progress: PlayerProgress = store.store.loadProfile()
        private set
    /** Newly unlocked achievements waiting to be announced, oldest first. */
    var pendingUnlocks: List<Achievement> = emptyList()
        private set
    /** Achievements unlocked during the active game, for the summary. */
    var unlockedThisGame: List<Achievement> = emptyList()
        private set
    var activeKind: GameKind? = null
        private set
    /** The daily on screen had been played to the end before: the summary says "Replay". */
    var isReplay = false
        private set
    private var completedDailyThisGame = false

    /** Anything the UI shows changed. */
    var onChange: () -> Unit = {}

    init {
        DailyKey.dayKey(today(), -DailyKey.REPLAY_WINDOW)?.let(store::deleteDayGames)
    }

    val todayKey: String get() = today()
    val currentStreak: Int get() = Streaks.current(store.completedDays, todayKey)
    val longestStreak: Int get() = Streaks.longest(store.completedDays)
    val daysInProgress: Set<String> get() = store.daysInProgress

    /** Stage of the saved classic game, if one is in progress. */
    val classicStage: Int? get() = store.loadGame(NumbersStore.CLASSIC)?.takeUnless { it.isOver }?.stage
    val hasClassicInProgress: Boolean get() = classicStage != null

    fun isDailyInProgress(dayKey: String) = dayKey in daysInProgress
    fun record(dayKey: String): DailyRecord? = store.dailies[dayKey]
    /** The day was already played to the end: another attempt is a replay. */
    fun hasPlayed(dayKey: String) = store.dailies[dayKey]?.isFinished == true

    /** Inside the replay window: today and the 30 days before. */
    fun isPlayable(dayKey: String): Boolean {
        val oldest = DailyKey.dayKey(todayKey, -DailyKey.REPLAY_WINDOW) ?: return false
        return dayKey <= todayKey && dayKey >= oldest
    }

    /** What goes to the daily leaderboard after each attempt: today's best, once one ended. */
    val dailyToSubmit: DailyRecord? get() = store.dailies[todayKey]?.takeIf { it.isFinished }

    // Achievements

    fun record(event: ProgressEvent) {
        val (next, unlocked) = progress.record(event)
        progress = next
        store.store.saveProfile(progress)
        pendingUnlocks = pendingUnlocks + unlocked
        unlockedThisGame = unlockedThisGame + unlocked
        onChange()
    }

    fun dismissUnlock(achievement: Achievement) {
        pendingUnlocks = pendingUnlocks - achievement
        onChange()
    }

    // Classic

    /** Classic only: "In the Red" makes hints cheaper unless turned off. Dailies always cost 10. */
    val classicScoring: Scoring
        get() = if (Achievement.IN_THE_RED in progress.unlocked && cheapHintsEnabled()) Scoring.CHEAP_HINTS else Scoring.STANDARD

    fun continueClassic(): Started? {
        val session = store.loadGame(NumbersStore.CLASSIC)?.takeUnless { it.isOver } ?: return null
        return begin(session, GameKind.Classic)
    }

    /** A new classic game; one left unfinished counts as abandoned (its score still a best). */
    fun newClassic(seed: ULong): Started {
        store.loadGame(NumbersStore.CLASSIC)?.takeUnless { it.isOver }?.let {
            record(ProgressEvent.ClassicAbandoned(it.score, it.hasMoves))
        }
        val session = GameSession(seed = seed, scoring = classicScoring)
        store.saveGame(session, NumbersStore.CLASSIC)
        return begin(session, GameKind.Classic)
    }

    // Daily

    /** Continues the attempt left halfway, or deals the day for a new one, saved right away (it replaces whatever that day had). */
    fun playDaily(dayKey: String): Started {
        val replay = hasPlayed(dayKey)
        store.loadGame(NumbersStore.day(dayKey))?.takeUnless { it.isOver }?.let {
            return begin(it, GameKind.Daily(dayKey), replay)
        }
        val session = DailyChallenge.session(dayKey)
        store.saveGame(session, NumbersStore.day(dayKey))
        return begin(session, GameKind.Daily(dayKey), replay)
    }

    /** "Start over" on an attempt left halfway: the next `playDaily` deals the day again. */
    fun startOver(dayKey: String) {
        store.deleteGame(NumbersStore.day(dayKey))
        onChange()
    }

    // The active game

    /** A game goes on screen. */
    fun begin(session: GameSession, kind: GameKind, replay: Boolean = false): Started {
        activeKind = kind
        isReplay = replay
        unlockedThisGame = emptyList()
        completedDailyThisGame = false
        finishedThisGame = session.isOver
        onChange()
        return Started(session, kind, replay)
    }

    /** After every move: saved, the classic best updated, the game finished if it ended. */
    fun sessionChanged(session: GameSession) {
        when (val kind = activeKind ?: return) {
            GameKind.Classic -> {
                store.saveGame(session, NumbersStore.CLASSIC)
                // A new best shows right away (Home, the game's "Best").
                if (session.score > progress.bestScore) record(ProgressEvent.ClassicScore(session.score))
            }
            is GameKind.Daily -> if (!session.isOver) store.saveGame(session, NumbersStore.day(kind.dayKey))
        }
        if (session.isOver) finish(session, activeKind!!)
        onChange()
    }

    /** Back from a game: true once after a daily was completed for the first time that day (offer the reminders). */
    fun leftGame(): Boolean {
        val completed = completedDailyThisGame
        completedDailyThisGame = false
        onChange()
        return completed
    }

    /** Whether today is completed and the current streak, for the reminders. */
    fun reminderState(): Pair<Boolean, Int> = (store.dailies[todayKey]?.isCompleted == true) to currentStreak

    // Play Games

    /** Progress and daily results for the saved game (iOS `Snapshot`). */
    fun cloudSnapshot(): CloudSnapshot = CloudSnapshot(progress, store.dailies)

    /** What another device saved, merged in: nothing local is lost. */
    fun mergeCloud(remote: CloudSnapshot) {
        progress = progress.merged(remote.progress)
        store.store.saveProfile(progress)
        store.mergeDailies(remote.dailies)
        onChange()
    }

    /** Achievements and scores to mirror on Play Games. */
    fun playGamesPush(): com.tavoohoh.quiet.core.PlayGamesPush = PlayGamesPlan.of(progress, store.dailies, todayKey)

    /** Debug `unlockAll` (QA and screenshots, iOS `-unlockAll`): every achievement, nothing announced. */
    fun unlockAll() {
        progress = progress.copy(unlocked = Achievement.entries.toSet())
        store.store.saveProfile(progress)
        onChange()
    }

    /** Erases games, dailies and progress (settings and the review count stay). */
    fun reset() {
        store.store.deleteEverything()
        progress = PlayerProgress()
        store.store.saveProfile(progress)
        activeKind = null
        pendingUnlocks = emptyList()
        unlockedThisGame = emptyList()
        onChange()
    }

    /** The game on screen already ended (it's counted once). */
    private var finishedThisGame = false

    private fun finish(session: GameSession, kind: GameKind) {
        if (finishedThisGame) return
        finishedThisGame = true
        when (kind) {
            GameKind.Classic -> store.deleteGame(NumbersStore.CLASSIC)
            is GameKind.Daily -> {
                val firstCompletion = store.keepBestDaily(kind.dayKey, session.score, session.isCompleted, session.elapsedSeconds)
                store.deleteGame(NumbersStore.day(kind.dayKey))
                if (session.isCompleted && firstCompletion) dayCompleted(kind.dayKey)
            }
        }
        record(ProgressEvent.GameFinished(if (kind == GameKind.Classic) session.score else null))
        // The summary lists what this game unlocked; no banners over it.
        pendingUnlocks = emptyList()
    }

    /** A day completed for the first time counts once (streak, calendar, achievements), today or past. */
    private fun dayCompleted(dayKey: String) {
        if (dayKey == todayKey) {
            record(ProgressEvent.DailyCompleted(currentStreak, hour()))
        } else {
            if (dayKey !in progress.practicedDays) record(ProgressEvent.PracticeCompleted(dayKey))
            // A filled gap can join two runs in the past: the longest streak.
            record(ProgressEvent.DailyCompleted(longestStreak, hour()))
        }
        completedDailyThisGame = true
    }
}
