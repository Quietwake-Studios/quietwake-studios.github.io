package com.tavoohoh.quietwords.core

import com.tavoohoh.quiet.core.DailyKey
import com.tavoohoh.quiet.core.Streaks

/** Which game is on screen. */
sealed interface GameKind {
    data object Free : GameKind

    /**
     * A day's challenge in one language, today's or one of the last 30, saved per day and
     * language (the family's daily rule). `isReplay`: not that day's first attempt.
     */
    data class Daily(val id: DailyID, val isReplay: Boolean) : GameKind
}

/** A game ready to show. */
data class Started(val session: GameSession, val kind: GameKind)

/**
 * The rules around games, without UI (port of the non-UI half of iOS `AppModel`): the saved
 * free game, the daily challenge per day and language (today's or one of the last 30,
 * unlimited attempts, the latest attempt always the one saved), one streak and calendar for
 * both languages, progress and the album. The app wraps it with Compose state; JVM tests
 * drive it directly (`DailyRuleTests`).
 */
class WordsPlay(
    val store: WordsStore,
    val library: ThemeLibrary = ThemeLibrary.shared,
    /** Today's day key (the app's clock; fixed in debug launches and tests). */
    private val today: () -> String,
    /** "Cheaper hints" switch in Rewards (Phase 4); on by default. */
    private val cheapHintsEnabled: () -> Boolean = { true },
) {
    var profile: Profile = store.loadProfile()
        private set
    /** The saved free game, if one is in progress. */
    var freeInProgress: GameSession? = store.loadGame(Slot.Free)?.takeUnless { it.isWon }
        private set
    var activeKind: GameKind? = null
        private set
    /** Best score of the active game's level and language when it started ("New best"). */
    var bestAtStart = 0
        private set
    /** Album words of the active game's theme before it started (summary: "7 new"). */
    var albumAtStart = 0
        private set
    var unlockedThisGame: List<Achievement> = emptyList()
        private set
    private var completedDailyThisGame = false

    /** Anything the UI shows changed. */
    var onChange: () -> Unit = {}

    init {
        DailyKey.dayKey(today(), -DailyKey.REPLAY_WINDOW)?.let(store::deleteDailyGames)
    }

    val progress: PlayerProgress get() = profile.progress
    val todayKey: String get() = today()

    // Daily

    val currentStreak: Int get() = Streaks.current(store.completedDays, todayKey)
    val longestStreak: Int get() = Streaks.longest(store.completedDays)

    fun isCompleted(dayKey: String, language: WordLanguage): Boolean = store.record(DailyID(dayKey, language))?.isCompleted == true
    fun isInProgress(dayKey: String, language: WordLanguage): Boolean = DailyID(dayKey, language) in store.dailiesInProgress

    /** Languages whose daily is done on that day (the pill's check). */
    fun doneLanguages(dayKey: String): Set<WordLanguage> = WordLanguage.entries.filter { isCompleted(dayKey, it) }.toSet()

    /** Inside the replay window: today and the 30 days before. */
    fun isPlayable(dayKey: String): Boolean {
        val oldest = DailyKey.dayKey(todayKey, -DailyKey.REPLAY_WINDOW) ?: return false
        return dayKey <= todayKey && dayKey >= oldest
    }

    /** The attempt in progress, if any (resumed as is). */
    fun resumeDaily(id: DailyID): Started? {
        val saved = store.loadGame(Slot.Daily(id))?.takeUnless { it.isWon } ?: return null
        return began(saved, dailyKind(id))
    }

    /**
     * A new attempt on that day's board, saved right away in its slot. The slot holds only
     * the attempt in progress: never an earlier one (bug found in Quiet Numbers, 2026-11-02).
     * Hints always cost the normal price in a daily, today's or past.
     */
    fun newDaily(id: DailyID, board: Board): Started {
        val session = GameSession(
            GameSession.Mode.Daily(id.dayKey), DailyChallenge.themeID(id.dayKey, library) ?: "",
            DailyChallenge.level(id.dayKey), board, Scoring.STANDARD,
        )
        return startAttempt(session, id)
    }

    /** Resumes the attempt in progress, or starts a new one (generating the board here). */
    fun playDaily(dayKey: String, language: WordLanguage): Started? {
        if (!isPlayable(dayKey)) return null
        val id = DailyID(dayKey, language)
        resumeDaily(id)?.let { return it }
        val board = DailyChallenge.board(dayKey, language, library) ?: return null
        return newDaily(id, board)
    }

    /** "Start over" on the Continue question: the saved attempt is dropped. */
    fun startOver(dayKey: String, language: WordLanguage) {
        store.deleteGame(Slot.Daily(DailyID(dayKey, language)))
        onChange()
    }

    private fun startAttempt(session: GameSession, id: DailyID): Started {
        store.startAttempt(id)
        store.saveGame(session, Slot.Daily(id))
        return began(session, dailyKind(id))
    }

    private fun dailyKind(id: DailyID) = GameKind.Daily(id, isReplay = store.attempts(id) > 1)

    // Free play

    /** Free play only: "In the Red" makes hints cheaper unless turned off. Dailies are always standard. */
    val freeScoring: Scoring
        get() = if (Achievement.IN_THE_RED in progress.unlocked && cheapHintsEnabled()) Scoring.CHEAP_HINTS else Scoring.STANDARD

    fun continueFree(): Started? = freeInProgress?.let { began(it.copy(), GameKind.Free) }

    /** A new free game (built by `FreeGame` with `freeScoring`); an unfinished one counts as abandoned. */
    fun newFree(session: GameSession): Started {
        freeInProgress?.let { record(ProgressEvent.Abandoned(it.hasPlayed)) }
        store.saveGame(session, Slot.Free)
        freeInProgress = session.copy()
        return began(session, GameKind.Free)
    }

    /** The same board again from the start. A daily starts a new attempt, which replaces the saved one. */
    fun restart(current: GameSession, kind: GameKind): Started {
        val scoring = if (kind == GameKind.Free) freeScoring else Scoring.STANDARD
        val session = GameSession(current.mode, current.themeID, current.level, current.board, scoring)
        return when (kind) {
            GameKind.Free -> {
                store.saveGame(session, Slot.Free)
                freeInProgress = session.copy()
                began(session, kind)
            }
            is GameKind.Daily -> startAttempt(session, kind.id)
        }
    }

    // The active game

    /** A game goes on screen (also debug games that aren't saved). */
    fun began(session: GameSession, kind: GameKind): Started {
        bestAtStart = progress.bestScore(session.level, session.language)
        albumAtStart = library.theme(session.themeID)?.let { profile.album.count(it, session.language).found } ?: 0
        unlockedThisGame = emptyList()
        completedDailyThisGame = false
        activeKind = kind
        onChange()
        return Started(session, kind)
    }

    /** New words the active game added to its theme's album (the summary). */
    fun newAlbumWords(session: GameSession): Int =
        (library.theme(session.themeID)?.let { profile.album.count(it, session.language).found } ?: 0) - albumAtStart

    /** After every move: progress for each event, then the game is saved (or finished). */
    fun update(session: GameSession, events: List<GameSession.Event>) {
        for (event in events) handle(event, session)
        val kind = activeKind ?: return
        if (events.any { it is GameSession.Event.Won }) {
            finish(session, kind)
        } else if (!session.isWon) {
            store.saveGame(session, slot(kind))
            if (kind == GameKind.Free) freeInProgress = session.copy()
        }
        onChange()
    }

    /**
     * Leaving the game (back, or the app going to the background): the attempt in progress
     * is saved as it is. Returns true once after a daily was completed for the first time that
     * day (the moment to offer the reminders).
     */
    fun leftGame(session: GameSession?): Boolean {
        save(session)
        val completed = completedDailyThisGame
        completedDailyThisGame = false
        onChange()
        return completed
    }

    fun save(session: GameSession?) {
        val kind = activeKind ?: return
        if (session == null || session.isWon) return
        store.saveGame(session, slot(kind))
        if (kind == GameKind.Free) freeInProgress = session.copy()
    }

    /** "Reset progress": games, dailies, streak, progress and album are erased. */
    fun reset() {
        store.deleteEverything()
        profile = Profile()
        freeInProgress = null
        activeKind = null
        unlockedThisGame = emptyList()
        completedDailyThisGame = false
        onChange()
    }

    /** Whether today is done (either language) and the current streak, for the reminders. */
    fun reminderState(): Pair<Boolean, Int> = (todayKey in store.completedDays) to currentStreak

    // Progress

    fun record(event: ProgressEvent) {
        val (progress, unlocked) = profile.progress.record(event)
        profile = profile.copy(progress = progress)
        store.saveProfile(profile)
        unlockedThisGame = unlockedThisGame + unlocked
    }

    private fun handle(event: GameSession.Event, session: GameSession) {
        when (event) {
            is GameSession.Event.Found -> record(ProgressEvent.WordsFound(1))
            is GameSession.Event.Hint -> record(ProgressEvent.ScoreChanged(session.score))
            is GameSession.Event.Won -> {
                val summary = event.summary
                record(ProgressEvent.Won(summary, isFree = activeKind == GameKind.Free))
                val theme = library.theme(summary.themeID) ?: return
                val wasComplete = profile.album.isComplete(theme, summary.language)
                profile = profile.copy(album = profile.album.add(summary.foundLetters, theme.id, summary.language).first)
                store.saveProfile(profile)
                if (!wasComplete && profile.album.isComplete(theme, summary.language)) record(ProgressEvent.ThemeCompleted)
            }
            else -> Unit
        }
    }

    private fun finish(session: GameSession, kind: GameKind) {
        when (kind) {
            GameKind.Free -> {
                store.deleteGame(Slot.Free)
                freeInProgress = null
            }
            is GameKind.Daily -> {
                val id = kind.id
                store.deleteGame(Slot.Daily(id))
                val dayWasDone = id.dayKey in store.completedDays
                if (store.recordWin(id, session.score, session.elapsedSeconds)) {
                    // The streak counts the day once, whichever language came first.
                    if (!dayWasDone) {
                        if (id.dayKey != todayKey) record(ProgressEvent.CompletedLate(id.dayKey))
                        record(ProgressEvent.DailyCompleted(longestStreak))
                        completedDailyThisGame = true
                    }
                    if (WordLanguage.entries.all { isCompleted(id.dayKey, it) }) record(ProgressEvent.BothLanguages)
                }
            }
        }
    }

    private fun slot(kind: GameKind): Slot = when (kind) {
        GameKind.Free -> Slot.Free
        is GameKind.Daily -> Slot.Daily(kind.id)
    }
}
