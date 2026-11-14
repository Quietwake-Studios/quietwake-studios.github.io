package com.tavoohoh.quietwords

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tavoohoh.quiet.core.DailyKey
import com.tavoohoh.quiet.ui.DailyReminders
import com.tavoohoh.quiet.ui.QuietTones
import com.tavoohoh.quiet.ui.PlayGamesService
import com.tavoohoh.quietwords.core.CloudSnapshot
import com.tavoohoh.quietwords.core.conceptID
import com.tavoohoh.quiet.core.Palette
import com.tavoohoh.quietwords.core.AppIconChoice
import com.tavoohoh.quietwords.core.BoardFont
import com.tavoohoh.quietwords.core.Looks
import com.tavoohoh.quietwords.core.Marker
import com.tavoohoh.quietwords.design.FeedbackService
import com.tavoohoh.quietwords.design.LauncherIcon
import com.tavoohoh.quiet.ui.StoreReview
import com.tavoohoh.quietwords.core.Achievement
import com.tavoohoh.quietwords.core.Album
import com.tavoohoh.quietwords.core.DailyChallenge
import com.tavoohoh.quietwords.core.DailyID
import com.tavoohoh.quietwords.core.FreeGame
import com.tavoohoh.quietwords.core.GameKind
import com.tavoohoh.quietwords.core.GameSession
import com.tavoohoh.quietwords.core.Level
import com.tavoohoh.quietwords.core.PlayerProgress
import com.tavoohoh.quietwords.core.Started
import com.tavoohoh.quietwords.core.ThemeLibrary
import com.tavoohoh.quietwords.core.WordLanguage
import com.tavoohoh.quietwords.core.WordTheme
import com.tavoohoh.quietwords.core.WordsPlay
import com.tavoohoh.quietwords.core.WordsStore
import com.tavoohoh.quietwords.design.systemWordLanguage
import com.tavoohoh.quietwords.game.GameModel
import com.tavoohoh.quietwords.reminders.wordsReminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import kotlin.random.Random

/** Debug `uiTesting`: cells mark "start"/"end" of the next word and the hidden word row carries the answer. */
val LocalUiTesting = staticCompositionLocalOf { false }

enum class Route { HOME, NEW_GAME, GAME, TUTORIAL, DAILY, SETTINGS, COLLECTION, ALBUM_THEME, LEADERBOARDS }

/** Collection's tabs. */
enum class CollectionTab { ALBUM, REWARDS, ACHIEVEMENTS }

/** "Match system", or a fixed light or dark look. */
enum class AppearanceMode(val rawValue: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object {
        fun fromRaw(raw: String?) = entries.firstOrNull { it.rawValue == raw } ?: SYSTEM
    }
}

/** "Now" for anything dated: today's challenge, the calendar. Debug `--es date <ISO-8601>` fixes it. */
object AppClock {
    var fixed: Instant? = null
    fun now(): Instant = fixed ?: Instant.now()
    val todayKey: String get() = DailyKey.dayKey(now())
}

/**
 * The app's state (survives rotation): navigation, preferences and the game on screen, around
 * `WordsPlay` (words-core), which holds the rules: saved games, the daily per day and
 * language, streak, progress and album. Compose reads `revision`, bumped on every change.
 */
class AppModel(application: Application) : AndroidViewModel(application) {
    private var prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var configured = false

    val library: ThemeLibrary get() = ThemeLibrary.shared
    lateinit var play: WordsPlay
        private set
    lateinit var reminders: DailyReminders
        private set
    /** The once-per-install review prompt (its own preferences; Reset progress keeps them). */
    lateinit var storeReview: StoreReview
        private set
    /** Google Play Games (optional; IDs in games-ids.xml, placeholders until Play Console exists). */
    lateinit var games: PlayGamesService
        private set
    /** Debug `demo`: sample leaderboards and a signed-in look, for screenshots (iOS `-demo`). */
    var demoActive = false
        private set

    /** The active game's finish was already counted for the review prompt. */
    private var reviewCounted = false

    /** Bumped whenever `play` changes (Compose reads it through the getters below). */
    var revision by mutableIntStateOf(0)
        private set

    var wordLanguage: WordLanguage by mutableStateOf(WordLanguage.EN)
        private set
    var hasChosenLanguage by mutableStateOf(false)
        private set
    var tutorialDone by mutableStateOf(false)
        private set
    var appearance by mutableStateOf(AppearanceMode.SYSTEM)
        private set
    var soundEnabled by mutableStateOf(true)
        private set
    var hapticsEnabled by mutableStateOf(true)
        private set
    /** "Larger letters": the board has its own size instead of following the font scale. */
    var largerLetters by mutableStateOf(false)
        private set
    /** "Cheaper hints" (the "In the Red" reward): on when earned, the player can turn it off. */
    var cheapHints by mutableStateOf(true)
        private set
    /** What the player picked in Rewards (`looks` is what applies: only unlocked choices). */
    var chosenLooks by mutableStateOf(Looks())
        private set
    val looks: Looks get() = chosenLooks.effective(progress)

    /** Collection: the tab it opens on, and the theme whose album is open. */
    var collectionTab by mutableStateOf(CollectionTab.ALBUM)
    var albumThemeID: String? by mutableStateOf(null)
        private set
    var albumLanguage by mutableStateOf(WordLanguage.EN)

    /** Haptics and tones for play (Settings › Sound and Haptics). */
    lateinit var feedback: FeedbackService
        private set
    var lastLevel: Level = Level.GENTLE
        private set
    var lastThemeID: String? = null
        private set

    val stack = mutableStateListOf(Route.HOME)
    var activeGame: GameModel? by mutableStateOf(null)
        private set
    var activeKind: GameKind? by mutableStateOf(null)
        private set
    var isPreparing by mutableStateOf(false)
        private set
    /** "Remind you at 7 PM?" after a first completed daily, once. */
    var offersReminders by mutableStateOf(false)

    val todayKey: String get() = AppClock.todayKey
    val needsTutorial: Boolean get() = !tutorialDone

    var uiTesting = false
        private set

    /** Once per process; debug launch arguments (intent extras) only in debug builds. */
    fun configure(intent: Intent?) {
        if (configured) return
        configured = true
        val app = getApplication<Application>()
        val args = if (BuildConfig.DEBUG) LaunchArgs.from(intent) else LaunchArgs()
        uiTesting = args.uiTesting
        AppClock.fixed = args.date
        var directory = storeDirectory(app)
        if (args.uiTesting) {
            // Its own store, empty on every launch (like iOS -uiTesting).
            prefs = app.getSharedPreferences("$PREFS-uitesting", Context.MODE_PRIVATE)
            prefs.edit(commit = true) { clear() }
            directory = File(app.filesDir, "store-uitesting").also { it.deleteRecursively() }
            app.getSharedPreferences("dailyReminders-uitesting", Context.MODE_PRIVATE).edit(commit = true) { clear() }
            app.getSharedPreferences("storeReview-uitesting", Context.MODE_PRIVATE).edit(commit = true) { clear() }
        }
        if (args.demo) DemoData.seed(directory, args.words ?: WordLanguage.EN)
        play = WordsPlay(WordsStore(directory), today = { AppClock.todayKey }, cheapHintsEnabled = { cheapHints })
        feedback = FeedbackService(QuietTones(app), sound = { soundEnabled }, haptics = { hapticsEnabled })
        play.onChange = { revision++ }
        reminders = wordsReminders(app, uiTesting = args.uiTesting) { play.reminderState() }
        storeReview = StoreReview(app, if (args.uiTesting) "storeReview-uitesting" else "storeReview")
        demoActive = args.demo
        // UI tests never touch Play Games (iOS: -uiTesting is offline too).
        games = PlayGamesService(if (args.uiTesting) "" else app.getString(R.string.play_games_app_id))
        games.onAuthenticated = { syncPlayGames() }
        games.initialize(app)
        load()
        args.words?.let { chooseLanguage(it) }
        if (args.tutorialDone) finishTutorial()
        args.appearance?.let(::setAppearanceMode)
        if (args.unlockAll) play.unlockAll()
        applyDebug(args)
        // Warm the theme library and today's board off the main thread.
        viewModelScope.launch(Dispatchers.Default) { library }
    }

    private fun load() {
        wordLanguage = prefs.getString(KEY_LANGUAGE, null)?.let(WordLanguage::fromRaw) ?: systemWordLanguage()
        hasChosenLanguage = prefs.getBoolean(KEY_CHOSE_LANGUAGE, false)
        tutorialDone = prefs.getBoolean(KEY_TUTORIAL, false)
        appearance = AppearanceMode.fromRaw(prefs.getString(KEY_APPEARANCE, null))
        soundEnabled = prefs.getBoolean(KEY_SOUND, true)
        hapticsEnabled = prefs.getBoolean(KEY_HAPTICS, true)
        largerLetters = prefs.getBoolean(KEY_LARGER, false)
        cheapHints = prefs.getBoolean(KEY_CHEAP_HINTS, true)
        chosenLooks = Looks(
            palette = prefs.getString(KEY_PALETTE, null)?.let(Palette::fromRaw) ?: Palette.TEAL_CORAL,
            marker = Marker.fromRaw(prefs.getString(KEY_MARKER, null)) ?: Marker.HIGHLIGHTER,
            boardFont = BoardFont.fromRaw(prefs.getString(KEY_BOARD_FONT, null)) ?: BoardFont.ROUNDED,
            icon = AppIconChoice.fromRaw(prefs.getString(KEY_ICON, null)) ?: AppIconChoice.STANDARD,
        )
        lastLevel = prefs.getString(KEY_LEVEL, null)?.let(Level::fromRaw) ?: Level.GENTLE
        lastThemeID = prefs.getString(KEY_THEME, null)
    }

    // What screens read (through `revision`)

    private inline fun <T> observed(block: () -> T): T {
        @Suppress("UNUSED_VARIABLE") val r = revision
        return block()
    }

    val progress: PlayerProgress get() = observed { play.progress }
    val album: Album get() = observed { play.profile.album }
    val currentStreak: Int get() = observed { play.currentStreak }
    val longestStreak: Int get() = observed { play.longestStreak }
    val freeInProgress: GameSession? get() = observed { play.freeInProgress }
    val completedDays: Set<String> get() = observed { play.store.completedDays }
    val daysInProgress: Set<String> get() = observed { play.store.daysInProgress }
    val lateDays: Set<String> get() = observed { play.progress.lateDays }

    fun isCompleted(dayKey: String, language: WordLanguage) = observed { play.isCompleted(dayKey, language) }
    fun isInProgress(dayKey: String, language: WordLanguage) = observed { play.isInProgress(dayKey, language) }
    fun doneLanguages(dayKey: String) = observed { play.doneLanguages(dayKey) }
    fun bestOfDay(dayKey: String, language: WordLanguage) =
        observed { play.store.record(DailyID(dayKey, language))?.takeIf { it.isCompleted } }
    fun isPlayable(dayKey: String) = play.isPlayable(dayKey)
    fun dailyTheme(dayKey: String): WordTheme? = DailyChallenge.themeID(dayKey, library)?.let(library::theme)

    // Navigation

    fun push(route: Route) {
        stack.add(route)
    }

    fun pop() {
        if (stack.size <= 1) return
        if (stack.last() == Route.GAME) leftGame()
        stack.removeAt(stack.lastIndex)
    }

    fun popToHome() = show()

    private fun show(vararg routes: Route) {
        if (stack.last() == Route.GAME && Route.GAME !in routes) leftGame()
        stack.clear()
        stack.add(Route.HOME)
        stack.addAll(routes)
    }

    /** Home's daily card and the tutorial gate. */
    fun openDaily() = push(if (needsTutorial) Route.TUTORIAL else Route.DAILY)

    // Preferences

    fun setLanguage(language: WordLanguage) {
        wordLanguage = language
        prefs.edit { putString(KEY_LANGUAGE, language.rawValue) }
    }

    fun chooseLanguage(language: WordLanguage) {
        setLanguage(language)
        hasChosenLanguage = true
        prefs.edit { putBoolean(KEY_CHOSE_LANGUAGE, true) }
    }

    fun finishTutorial() {
        tutorialDone = true
        prefs.edit { putBoolean(KEY_TUTORIAL, true) }
    }

    /** After the tutorial: New Game, like iOS. */
    fun tutorialFinished() {
        finishTutorial()
        show(Route.NEW_GAME)
    }

    fun setSound(on: Boolean) { soundEnabled = on; prefs.edit { putBoolean(KEY_SOUND, on) } }
    fun setHaptics(on: Boolean) { hapticsEnabled = on; prefs.edit { putBoolean(KEY_HAPTICS, on) } }
    fun chooseLargerLetters(on: Boolean) { largerLetters = on; prefs.edit { putBoolean(KEY_LARGER, on) } }
    fun chooseCheapHints(on: Boolean) { cheapHints = on; prefs.edit { putBoolean(KEY_CHEAP_HINTS, on) } }

    /** A reward picked in Collection (only unlocked ones can be picked). */
    fun choose(looks: Looks) {
        val iconChanged = looks.icon != chosenLooks.icon
        chosenLooks = looks
        prefs.edit {
            putString(KEY_PALETTE, looks.palette.rawValue)
            putString(KEY_MARKER, looks.marker.rawValue)
            putString(KEY_BOARD_FONT, looks.boardFont.rawValue)
            putString(KEY_ICON, looks.icon.rawValue)
        }
        if (iconChanged && !uiTesting) LauncherIcon.set(getApplication(), looks.icon)
    }

    // Collection

    fun openCollection(tab: CollectionTab) {
        collectionTab = tab
        albumLanguage = wordLanguage
        push(Route.COLLECTION)
    }

    fun openAlbum(themeID: String, language: WordLanguage) {
        albumThemeID = themeID
        albumLanguage = language
        push(Route.ALBUM_THEME)
    }

    val pendingUnlocks: List<Achievement> get() = observed { play.pendingUnlocks }
    fun dismissUnlock(achievement: Achievement) = play.dismissUnlock(achievement)
    /** Best score of the active game's level and language when it started ("New best"). */
    val bestAtStart: Int get() = observed { play.bestAtStart }

    fun setAppearanceMode(mode: AppearanceMode) {
        appearance = mode
        prefs.edit { putString(KEY_APPEARANCE, mode.rawValue) }
    }

    fun theme(id: String): WordTheme? = library.theme(id)

    // Free play

    fun continueFree() {
        play.continueFree()?.let(::start) ?: return
        show(Route.GAME)
    }

    /** A new board of the theme (null: "Surprise", any theme) in the current words language. */
    fun newFree(themeID: String?, level: Level) {
        if (isPreparing) return
        val language = wordLanguage
        val id = themeID ?: FreeGame.surpriseThemeID(language, library) { Random.nextInt(it) } ?: return
        lastLevel = level
        prefs.edit {
            putString(KEY_LEVEL, level.rawValue)
            if (themeID != null) putString(KEY_THEME, themeID)
        }
        if (themeID != null) lastThemeID = themeID
        isPreparing = true
        val scoring = play.freeScoring
        viewModelScope.launch {
            val key = "free-${Random.nextLong().toULong()}"
            val session = withContext(Dispatchers.Default) { FreeGame.session(id, level, language, key, library, scoring) }
            isPreparing = false
            if (session != null) {
                start(play.newFree(session))
                show(Route.GAME)
            }
        }
    }

    // Daily

    /**
     * Resumes the attempt in progress, or starts a new one (the board is generated off the
     * main thread). The Daily screen asks "Continue / Start over" first.
     */
    fun playDaily(dayKey: String, language: WordLanguage = wordLanguage) {
        if (isPreparing || !play.isPlayable(dayKey)) return
        val id = DailyID(dayKey, language)
        play.resumeDaily(id)?.let {
            start(it)
            return push(Route.GAME)
        }
        isPreparing = true
        viewModelScope.launch {
            val board = withContext(Dispatchers.Default) { DailyChallenge.board(dayKey, language, library) }
            isPreparing = false
            if (board != null) {
                start(play.newDaily(id, board))
                push(Route.GAME)
            }
        }
    }

    fun startOver(dayKey: String, language: WordLanguage = wordLanguage) = play.startOver(dayKey, language)

    // The active game

    /** The same board again from the start. A daily starts a new attempt, which replaces the saved one. */
    fun restart() {
        val game = activeGame ?: return
        val kind = activeKind ?: return
        start(play.restart(game.state, kind))
    }

    private fun start(started: Started) = attach(GameModel(started.session), started.kind)

    /** Puts a game on screen, wired to `play` (saving, progress, album). */
    fun attach(model: GameModel, kind: GameKind) {
        activeGame?.dispose()
        model.onChange = { session, events ->
            play.update(session, events)
            if (events.any { it is GameSession.Event.Won }) {
                if (kind is GameKind.Daily) reminders.reschedule()
                pushToPlayGames()
            }
        }
        activeGame = model
        activeKind = kind
        reviewCounted = false
    }

    /**
     * The summary was closed (or the menu's New Game mid-game). A daily, today's or past, goes
     * back to the Daily screen to see the day marked; free games go Home. Returns whether to ask
     * for the store review now (once per install, after the 3rd finished game); with "New Game"
     * the game counts but the request waits for the next Home or Daily screen.
     */
    fun leaveGame(toNewGame: Boolean): Boolean {
        val game = activeGame
        val kind = activeKind
        val finished = game?.state?.isWon == true && !reviewCounted
        if (finished) reviewCounted = true
        val ask = finished && storeReview.gameFinished(toNewGame) && !uiTesting
        when {
            toNewGame -> show(Route.NEW_GAME)
            kind is GameKind.Daily && game?.state?.isWon == true -> show(Route.DAILY)
            else -> show()
        }
        return ask
    }

    /** Home or the Daily screen came up: a review request deferred by "New Game" goes now. */
    fun reviewOnArrival(): Boolean = storeReview.arrived() && !uiTesting

    /**
     * "Reset progress" (like iOS): games, dailies, streak, progress and album are erased and
     * the tutorial shows again. Preferences (words language, appearance, reminders) and the
     * review prompt's state stay. The reminders are planned again from the empty state.
     */
    fun resetProgress() {
        activeGame?.dispose()
        activeGame = null
        activeKind = null
        play.reset()
        tutorialDone = false
        // Like iOS: the cosmetic choices and the tutorial go; settings stay.
        prefs.edit { for (key in REWARD_KEYS + KEY_TUTORIAL) remove(key) }
        cheapHints = true
        if (chosenLooks.icon != AppIconChoice.STANDARD && !uiTesting) LauncherIcon.set(getApplication(), AppIconChoice.STANDARD)
        chosenLooks = Looks()
        if (!uiTesting) reminders.reschedule()
        // The saved game too (iOS: iCloud); if signed out, on the next sign-in.
        prefs.edit { putBoolean(KEY_PENDING_CLOUD_RESET, true) }
        syncPlayGames()
    }

    // Play Games

    private fun gamesID(prefix: String, conceptID: String): String {
        val app = getApplication<Application>()
        val res = app.resources.getIdentifier("${prefix}_$conceptID", "string", app.packageName)
        return if (res == 0) "" else app.getString(res)
    }

    fun leaderboardID(board: com.tavoohoh.quietwords.core.Leaderboard) = gamesID("leaderboard", board.conceptID)

    /** On sign-in: pull what other devices saved, merge it into local data, then push everything. */
    fun syncPlayGames() {
        if (!games.isAuthenticated) return
        viewModelScope.launch {
            runCatching {
                if (prefs.getBoolean(KEY_PENDING_CLOUD_RESET, false)) {
                    if (games.deleteSnapshot(CloudSnapshot.NAME)) prefs.edit { remove(KEY_PENDING_CLOUD_RESET) }
                } else {
                    val bytes = games.loadSnapshot(CloudSnapshot.NAME) { a, b ->
                        val x = CloudSnapshot.decode(a)
                        val y = CloudSnapshot.decode(b)
                        (if (x != null && y != null) x.merged(y) else x ?: y ?: CloudSnapshot()).encode()
                    }
                    bytes?.let(CloudSnapshot::decode)?.let(play::mergeCloud)
                }
            }
            pushToPlayGames()
        }
    }

    /** Achievements, scores (today's best per language with its day and time) and the saved game. */
    fun pushToPlayGames() {
        if (!games.isAuthenticated) return
        val plan = play.playGamesPlan()
        for (a in plan.unlocks) games.unlock(gamesID("achievement", a.conceptID))
        for ((a, steps) in plan.steps) games.setSteps(gamesID("achievement", a.conceptID), steps)
        for (score in plan.scores) games.submit(leaderboardID(score.leaderboard), score.value, score.tag)
        if (prefs.getBoolean(KEY_PENDING_CLOUD_RESET, false)) return
        val snapshot = play.cloudSnapshot().encode()
        viewModelScope.launch { runCatching { games.saveSnapshot(CloudSnapshot.NAME, snapshot) } }
    }

    /** Back from the game: the attempt in progress is saved; maybe offer the reminders. */
    fun leftGame() {
        val completedFirst = play.leftGame(activeGame?.state)
        if (completedFirst && reminders.shouldOffer) offersReminders = true
        pushToPlayGames()
    }

    /** The app went to the background. */
    fun saveActiveGame() {
        if (stack.last() == Route.GAME) play.save(activeGame?.state)
    }

    fun newAlbumWords(session: GameSession): Int = observed { play.newAlbumWords(session) }
    val unlockedThisGame: List<Achievement> get() = observed { play.unlockedThisGame }

    override fun onCleared() {
        activeGame?.dispose()
    }

    // Debug

    private fun applyDebug(args: LaunchArgs) {
        when (args.open) {
            "newGame" -> show(Route.NEW_GAME)
            "tutorial" -> show(Route.TUTORIAL)
            "daily" -> show(Route.DAILY)
            "settings" -> show(Route.SETTINGS)
            "leaderboards" -> show(Route.LEADERBOARDS)
            "collection" -> { show(); openCollection(CollectionTab.REWARDS) }
            "album" -> { show(); openCollection(CollectionTab.ALBUM) }
            "achievements" -> { show(); openCollection(CollectionTab.ACHIEVEMENTS) }
        }
        args.albumTheme?.takeIf { library.theme(it) != null }?.let { id ->
            show(); openCollection(CollectionTab.ALBUM); openAlbum(id, wordLanguage)
        }
        args.playDay?.let { day ->
            val started = play.playDaily(day, wordLanguage) ?: return@let
            start(started)
            // With `won`: every word found, for the summary (screenshots).
            if (args.won) activeGame?.let { model ->
                for (p in model.board.placements) p.cells(model.board.side).let { model.tap(it.first()); model.tap(it.last()) }
            }
            show(Route.DAILY, Route.GAME)
            return
        }
        if (!args.startGame) return
        val level = args.level ?: Level.MEDIUM
        val themeID = args.theme ?: "ocean"
        val base = FreeGame.session(themeID, level, wordLanguage, args.seedKey ?: "debug-${Random.nextLong()}", library) ?: return
        val placements = base.board.placements.indices
        var found = emptyList<Int>()
        if (args.nearlyWon) found = placements.toList().dropLast(1)
        args.found?.let { n -> found = placements.take(n) }
        val session = GameSession.withFound(base.mode, themeID, level, base.board, found, elapsedSeconds = args.clock ?: 134)
        val model = GameModel(session)
        // Debug games aren't saved over the player's free game.
        attach(model, GameKind.Free)
        model.onChange = { _, _ -> }
        if (args.hint) model.requestHint()
        val next = placements.firstOrNull { it !in found }
        if (next != null) {
            val cells = base.board.placements[next].cells(base.board.side)
            // `won`: finds the next word (with `nearlyWon`, the last one) for the summary.
            if (args.won) {
                model.tap(cells.first())
                model.tap(cells.last())
            } else if (args.stroke) {
                // A stroke under a finger, for screenshots.
                model.drag(GameModel.Stroke(cells.first(), cells[minOf(3, cells.size - 1)]))
            }
        }
        show(Route.GAME)
    }

    companion object {
        private const val PREFS = "quietwords"
        private const val KEY_LANGUAGE = "wordLanguage"
        private const val KEY_CHOSE_LANGUAGE = "hasChosenLanguage"
        private const val KEY_TUTORIAL = "tutorialDone"
        private const val KEY_LEVEL = "lastLevel"
        private const val KEY_THEME = "lastTheme"
        private const val KEY_APPEARANCE = "appearanceMode"
        private const val KEY_SOUND = "soundEnabled"
        private const val KEY_HAPTICS = "hapticsEnabled"
        private const val KEY_LARGER = "largerLetters"
        private const val KEY_CHEAP_HINTS = "cheapHints"
        private const val KEY_PALETTE = "palette"
        private const val KEY_MARKER = "marker"
        private const val KEY_BOARD_FONT = "boardFont"
        private const val KEY_ICON = "appIcon"
        /** A reset made while signed out of Play Games: delete the saved game on the next sign-in. */
        private const val KEY_PENDING_CLOUD_RESET = "pendingCloudReset"
        /** Cosmetic choices, cleared by "Reset progress" (iOS `PreferenceKey.rewards`). */
        private val REWARD_KEYS = listOf(KEY_PALETTE, KEY_MARKER, KEY_BOARD_FONT, KEY_ICON, KEY_CHEAP_HINTS)

        fun storeDirectory(context: Context) = File(context.filesDir, "store")
    }
}

/**
 * Debug launch arguments as intent extras (iOS's `-startGame`, `-uiTesting`…), e.g.
 * `adb shell am start -n com.tavoohoh.quietwords/.MainActivity --ez startGame true --es level hard --ei found 5`.
 */
data class LaunchArgs(
    val uiTesting: Boolean = false,
    val startGame: Boolean = false,
    val seedKey: String? = null,
    val level: Level? = null,
    val theme: String? = null,
    val words: WordLanguage? = null,
    val found: Int? = null,
    val nearlyWon: Boolean = false,
    val won: Boolean = false,
    val hint: Boolean = false,
    val stroke: Boolean = false,
    val clock: Int? = null,
    val open: String? = null,
    val tutorialDone: Boolean = false,
    /** A fixed "now" (ISO-8601 instant, `2026-11-13T15:00:00Z`). */
    val date: Instant? = null,
    /** Fills the store with a streak, past days, a daily and a free game halfway (screenshots). */
    val demo: Boolean = false,
    /** Opens a day's challenge (`yyyy-MM-dd`) in the words language (with `won`, finished). */
    val playDay: String? = null,
    val appearance: AppearanceMode? = null,
    /** Every achievement unlocked (iOS `-unlockAll`). */
    val unlockAll: Boolean = false,
    /** Opens one theme's album (iOS `-albumTheme`). */
    val albumTheme: String? = null,
) {
    companion object {
        fun from(intent: Intent?): LaunchArgs {
            val e = intent?.extras ?: return LaunchArgs()
            return LaunchArgs(
                uiTesting = e.getBoolean("uiTesting"),
                startGame = e.getBoolean("startGame"),
                seedKey = e.getString("seedKey"),
                level = e.getString("level")?.let(Level::fromRaw),
                theme = e.getString("theme"),
                words = e.getString("words")?.let(WordLanguage::fromRaw),
                found = if (e.containsKey("found")) e.getInt("found") else null,
                nearlyWon = e.getBoolean("nearlyWon"),
                won = e.getBoolean("won"),
                hint = e.getBoolean("hint"),
                stroke = e.getBoolean("stroke"),
                clock = if (e.containsKey("clock")) e.getInt("clock") else null,
                open = e.getString("open"),
                tutorialDone = e.getBoolean("tutorialDone"),
                date = e.getString("date")?.let { runCatching { Instant.parse(it) }.getOrNull() },
                demo = e.getBoolean("demo"),
                playDay = e.getString("playDay"),
                appearance = e.getString("appearance")?.let(AppearanceMode::fromRaw),
                unlockAll = e.getBoolean("unlockAll"),
                albumTheme = e.getString("albumTheme"),
            )
        }
    }
}
