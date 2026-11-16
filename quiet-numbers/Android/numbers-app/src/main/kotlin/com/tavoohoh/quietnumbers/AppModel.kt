package com.tavoohoh.quietnumbers

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
import com.tavoohoh.quiet.core.DailyKey
import com.tavoohoh.quiet.core.DailyRecord
import com.tavoohoh.quiet.core.AppearanceMode
import com.tavoohoh.quiet.ui.DailyReminders
import com.tavoohoh.quiet.ui.LauncherIcon
import com.tavoohoh.quietnumbers.core.AppIconChoice
import com.tavoohoh.quietnumbers.core.ClearEffect
import com.tavoohoh.quietnumbers.core.Looks
import com.tavoohoh.quietnumbers.core.NumberFont
import com.tavoohoh.quietnumbers.core.SoundPack
import com.tavoohoh.quietnumbers.core.ThemeChoice
import com.tavoohoh.quietnumbers.core.TileStyle
import com.tavoohoh.quiet.ui.QuietTones
import com.tavoohoh.quiet.ui.StoreReview
import com.tavoohoh.quiet.ui.useStandardTexts
import com.tavoohoh.quietnumbers.core.Achievement
import com.tavoohoh.quietnumbers.core.Board
import com.tavoohoh.quietnumbers.core.DailyChallenge
import com.tavoohoh.quietnumbers.core.GameKind
import com.tavoohoh.quietnumbers.core.GameMode
import com.tavoohoh.quietnumbers.core.GameSession
import com.tavoohoh.quietnumbers.core.MatchRules
import com.tavoohoh.quietnumbers.core.NumbersPlay
import com.tavoohoh.quietnumbers.core.NumbersStore
import com.tavoohoh.quietnumbers.core.PlayerProgress
import com.tavoohoh.quietnumbers.core.Started
import com.tavoohoh.quietnumbers.design.FeedbackService
import com.tavoohoh.quietnumbers.game.GameModel
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/** Debug `uiTesting` (no splash, its own store, no reminders scheduled). */
val LocalUiTesting = staticCompositionLocalOf { false }

enum class Route { HOME, GAME, DAILY, SETTINGS, COLLECTION, TUTORIAL }

/** Collection's tabs (iOS `CollectionTab`). */
enum class CollectionTab { REWARDS, ACHIEVEMENTS }

/** "Now" for anything dated. Debug `--es date <ISO-8601>` fixes it. */
object AppClock {
    var fixed: Instant? = null
    fun now(): Instant = fixed ?: Instant.now()
    val todayKey: String get() = DailyKey.dayKey(now())
    val hour: Int get() = now().atZone(ZoneId.systemDefault()).hour
}

/**
 * The app's state (port of iOS `AppModel`), around `NumbersPlay` (numbers-core), which holds the
 * rules: the saved classic game, daily attempts, results, streaks and progress. Compose reads
 * `revision`, bumped on every change.
 */
class AppModel(application: Application) : AndroidViewModel(application) {
    private var configured = false
    private var prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    lateinit var play: NumbersPlay
        private set
    lateinit var reminders: DailyReminders
        private set
    /** The store-review prompt after the third finished game, and Settings' "Rate" row. */
    lateinit var storeReview: StoreReview
        private set
    lateinit var feedback: FeedbackService
        private set
    var revision by mutableIntStateOf(0)
        private set

    val stack = mutableStateListOf(Route.HOME)
    var activeGame: GameModel? by mutableStateOf(null)
        private set
    var activeKind: GameKind? by mutableStateOf(null)
        private set
    /** Best classic score when the game on screen started ("New best score!"). */
    var bestAtStart = 0
        private set
    /** "Remind you at 7 PM?" back from a first completed daily, once. */
    var offersReminders by mutableStateOf(false)
    var uiTesting = false
        private set
    var demoActive = false
        private set
    // Preferences (iOS `PreferenceKey`): settings stay after a reset; reward choices and the tutorial don't.
    var appearance by mutableStateOf(AppearanceMode.SYSTEM)
        private set
    var soundEnabled by mutableStateOf(true)
        private set
    var hapticsEnabled by mutableStateOf(true)
        private set
    /** "Larger numbers": cells 52 dp tall and numbers of 32. */
    var largerNumbers by mutableStateOf(false)
        private set
    /** "Cheaper hints" (the "In the Red" reward, classic only): on when earned, the player can turn it off. */
    var cheapHints by mutableStateOf(true)
        private set
    /** What the player picked in Rewards (`looks` is what applies: only unlocked choices). */
    var chosenLooks by mutableStateOf(Looks())
        private set
    private var tutorialSeen = false
    /** The tutorial on screen came from New Game (it starts a game) rather than Settings › How to play. */
    var tutorialIsFirstRun = false
        private set
    var collectionTab by mutableStateOf(CollectionTab.REWARDS)
    /** Debug `tutorial`: UI tests skip the tutorial unless they ask for it (iOS `-tutorial`). */
    private var tutorialInTests = false

    /** No store review in UI tests, the demo or debug games (as iOS). */
    private var reviewsOff = false
    /** The finished game's summary was counted for the review prompt. */
    private var reviewCounted = false

    val todayKey: String get() = AppClock.todayKey

    fun configure(intent: Intent?) {
        if (configured) return
        configured = true
        val app = getApplication<Application>()
        val args = if (BuildConfig.DEBUG) LaunchArgs.from(intent) else LaunchArgs()
        uiTesting = args.uiTesting
        demoActive = args.demo
        AppClock.fixed = args.date
        var directory = storeDirectory(app)
        if (args.uiTesting) {
            // Its own store and preferences, empty on every launch (like iOS -uiTesting).
            directory = File(app.filesDir, "store-uitesting").also { it.deleteRecursively() }
            prefs = app.getSharedPreferences("$PREFS-uitesting", Context.MODE_PRIVATE)
            for (name in listOf("$PREFS-uitesting", "dailyReminders-uitesting", "storeReview-uitesting")) {
                app.getSharedPreferences(name, Context.MODE_PRIVATE).edit(commit = true) { clear() }
            }
        }
        // The demo state never touches the player's store (iOS keeps it in memory).
        if (args.demo) {
            if (!args.uiTesting) directory = File(app.filesDir, "store-demo").also { it.deleteRecursively() }
            DemoData.seed(directory)
        }
        reviewsOff = args.uiTesting || args.demo || args.startGame || args.summary != null
        loadPreferences()
        tutorialInTests = args.tutorial
        play = NumbersPlay(NumbersStore(directory), today = { AppClock.todayKey }, hour = { AppClock.hour }, cheapHintsEnabled = { cheapHints })
        play.onChange = { revision++ }
        if (args.unlockAll) play.unlockAll()
        feedback = FeedbackService(
            QuietTones(app), sound = { soundEnabled }, haptics = { hapticsEnabled },
            voice = { if (looks.soundPack == SoundPack.MARIMBA) QuietTones.Voice.MARIMBA else QuietTones.Voice.SOFT },
        )
        reminders = numbersReminders(app, args.uiTesting) { play.reminderState() }
        storeReview = StoreReview(app, if (args.uiTesting) "storeReview-uitesting" else "storeReview")
        applyDebug(args)
    }

    private fun loadPreferences() {
        appearance = AppearanceMode.fromRaw(prefs.getString(KEY_APPEARANCE, null))
        soundEnabled = prefs.getBoolean(KEY_SOUND, true)
        hapticsEnabled = prefs.getBoolean(KEY_HAPTICS, true)
        largerNumbers = prefs.getBoolean(KEY_LARGER, false)
        cheapHints = prefs.getBoolean(KEY_CHEAP_HINTS, true)
        tutorialSeen = prefs.getBoolean(KEY_TUTORIAL, false)
        chosenLooks = Looks(
            theme = ThemeChoice.fromRaw(prefs.getString(KEY_THEME, null)) ?: ThemeChoice.TEAL_CORAL,
            tileStyle = TileStyle.fromRaw(prefs.getString(KEY_TILES, null)) ?: TileStyle.PLAIN,
            numberFont = NumberFont.fromRaw(prefs.getString(KEY_FONT, null)) ?: NumberFont.ROUNDED,
            clearEffect = ClearEffect.fromRaw(prefs.getString(KEY_CLEAR, null)) ?: ClearEffect.OUTLINE,
            soundPack = SoundPack.fromRaw(prefs.getString(KEY_SOUND_PACK, null)) ?: SoundPack.SOFT,
            icon = AppIconChoice.fromRaw(prefs.getString(KEY_ICON, null)) ?: AppIconChoice.STANDARD,
        )
    }

    // Settings and rewards

    fun setAppearanceMode(mode: AppearanceMode) { appearance = mode; prefs.edit { putString(KEY_APPEARANCE, mode.rawValue) } }
    fun setSound(on: Boolean) { soundEnabled = on; prefs.edit { putBoolean(KEY_SOUND, on) } }
    fun setHaptics(on: Boolean) { hapticsEnabled = on; prefs.edit { putBoolean(KEY_HAPTICS, on) } }
    fun chooseLargerNumbers(on: Boolean) { largerNumbers = on; prefs.edit { putBoolean(KEY_LARGER, on) } }
    fun chooseCheapHints(on: Boolean) { cheapHints = on; prefs.edit { putBoolean(KEY_CHEAP_HINTS, on) } }

    /** What applies: the chosen cosmetics that are unlocked. */
    val looks: Looks get() = chosenLooks.effective(progress)

    /** A reward picked in Collection (only unlocked ones can be picked). */
    fun choose(looks: Looks) {
        val iconChanged = looks.icon != chosenLooks.icon
        chosenLooks = looks
        prefs.edit {
            putString(KEY_THEME, looks.theme.rawValue)
            putString(KEY_TILES, looks.tileStyle.rawValue)
            putString(KEY_FONT, looks.numberFont.rawValue)
            putString(KEY_CLEAR, looks.clearEffect.rawValue)
            putString(KEY_SOUND_PACK, looks.soundPack.rawValue)
            putString(KEY_ICON, looks.icon.rawValue)
        }
        if (iconChanged && !uiTesting) LauncherIcon.set(getApplication(), gold = looks.icon == AppIconChoice.GOLD)
    }

    fun openCollection(tab: CollectionTab = CollectionTab.REWARDS) {
        collectionTab = tab
        push(Route.COLLECTION)
    }

    /**
     * Settings › Reset Progress… (iOS `resetProgress`): games, dailies, streaks, achievements and
     * the reward choices (and the Gold icon) go, with the tutorial; settings and the review count stay.
     */
    fun resetProgress() {
        activeGame?.dispose()
        activeGame = null
        activeKind = null
        play.reset()
        prefs.edit { for (key in REWARD_KEYS + KEY_TUTORIAL) remove(key) }
        tutorialSeen = false
        cheapHints = true
        if (chosenLooks.icon != AppIconChoice.STANDARD && !uiTesting) LauncherIcon.set(getApplication(), gold = false)
        chosenLooks = Looks()
        if (!uiTesting) reminders.reschedule()
    }

    // Tutorial

    /** Before the first game: never played one, none saved, the tutorial not seen (UI tests skip it unless asked). */
    val needsTutorial: Boolean
        get() = observed { (!uiTesting || tutorialInTests) && !tutorialSeen && play.progress.gamesFinished == 0 && !play.hasClassicInProgress }

    /** Home's New Game: the tutorial first, the first time. */
    fun newGameFromHome() {
        if (needsTutorial) openTutorial(firstRun = true) else newClassic()
    }

    fun openTutorial(firstRun: Boolean) {
        tutorialIsFirstRun = firstRun
        push(Route.TUTORIAL)
    }

    /** Played or skipped: from New Game a game starts; from Settings it goes back. */
    fun tutorialFinished() {
        tutorialSeen = true
        prefs.edit { putBoolean(KEY_TUTORIAL, true) }
        if (tutorialIsFirstRun) newClassic() else pop()
    }

    val pendingUnlocks: List<Achievement> get() = observed { play.pendingUnlocks }
    fun dismissUnlock(achievement: Achievement) = play.dismissUnlock(achievement)

    // What screens read (through `revision`)

    private inline fun <T> observed(block: () -> T): T {
        @Suppress("UNUSED_VARIABLE") val r = revision
        return block()
    }

    val progress: PlayerProgress get() = observed { play.progress }
    val currentStreak: Int get() = observed { play.currentStreak }
    val longestStreak: Int get() = observed { play.longestStreak }
    val classicStage: Int? get() = observed { play.classicStage }
    val daysInProgress: Set<String> get() = observed { play.daysInProgress }
    val dailies: Map<String, DailyRecord> get() = observed { play.store.dailies }
    val isReplay: Boolean get() = observed { play.isReplay }
    val unlockedThisGame: List<Achievement> get() = observed { play.unlockedThisGame }
    fun isDailyInProgress(dayKey: String) = observed { play.isDailyInProgress(dayKey) }
    fun hasPlayed(dayKey: String) = observed { play.hasPlayed(dayKey) }
    fun isPlayable(dayKey: String) = play.isPlayable(dayKey)

    // Navigation

    fun push(route: Route) {
        stack.add(route)
    }

    fun pop() {
        if (stack.size <= 1) return
        if (stack.last() == Route.GAME) leftGame()
        stack.removeAt(stack.lastIndex)
    }

    private fun show(vararg routes: Route) {
        if (stack.last() == Route.GAME && Route.GAME !in routes) leftGame()
        stack.clear()
        stack.add(Route.HOME)
        stack.addAll(routes)
    }

    fun popToHome() = show()

    // Classic

    fun continueClassic() {
        play.continueClassic()?.let(::start) ?: return newClassic()
        show(Route.GAME)
    }

    fun newClassic() {
        start(play.newClassic(Random.nextLong().toULong()))
        show(Route.GAME)
    }

    // Daily

    /** Continues the attempt left halfway, or deals the day again. The Daily screen asks "Continue / Start over" first. */
    fun playDaily(dayKey: String) {
        if (!play.isPlayable(dayKey)) return
        start(play.playDaily(dayKey))
        push(Route.GAME)
    }

    fun startOver(dayKey: String) = play.startOver(dayKey)

    // The active game

    private fun start(started: Started) = attach(GameModel(started.session), started.kind)

    private fun attach(model: GameModel, kind: GameKind) {
        activeGame?.dispose()
        bestAtStart = play.progress.bestScore
        model.onEvent = { play.record(it) }
        model.onChange = { session ->
            val completedBefore = play.record(todayKey)?.isCompleted == true
            play.sessionChanged(session)
            if (session.isOver && kind is GameKind.Daily && !uiTesting) reminders.reschedule()
            if (completedBefore != (play.record(todayKey)?.isCompleted == true)) revision++
        }
        activeGame = model
        activeKind = kind
        reviewCounted = false
    }

    /**
     * The summary was closed (or the menu's New Game). Any daily, today's or past, goes back to
     * the Daily screen; classic games go Home. Returns whether to ask for the store review now.
     */
    fun leaveGame(toNewGame: Boolean): Boolean {
        val game = activeGame
        val kind = activeKind
        val finished = game?.session?.isOver == true && !reviewCounted
        if (finished) reviewCounted = true
        val ask = finished && storeReview.gameFinished(toNewGame) && !reviewsOff
        when {
            toNewGame -> newClassic()
            kind is GameKind.Daily && game?.session?.isOver == true -> show(Route.DAILY)
            else -> show()
        }
        return ask
    }

    /** Home or the Daily screen came up: a review request deferred by "New Game" goes now. */
    fun reviewOnArrival(): Boolean = storeReview.arrived() && !reviewsOff

    /** Back from the game: it's saved with every move; maybe offer the reminders. */
    private fun leftGame() {
        activeGame?.commitTime()
        if (play.leftGame() && reminders.shouldOffer) offersReminders = true
    }

    /** The app went to the background: the seconds since the last move are saved too. */
    fun saveActiveGame() {
        if (stack.last() == Route.GAME) activeGame?.commitTime()
    }

    override fun onCleared() {
        activeGame?.dispose()
    }

    // Debug

    private fun applyDebug(args: LaunchArgs) {
        when (args.open) {
            "daily" -> show(Route.DAILY)
            "settings" -> show(Route.SETTINGS)
            "collection" -> { collectionTab = CollectionTab.REWARDS; show(Route.COLLECTION) }
            "achievements" -> { collectionTab = CollectionTab.ACHIEVEMENTS; show(Route.COLLECTION) }
            "tutorial" -> { tutorialIsFirstRun = true; show(Route.TUTORIAL) }
        }
        args.playDay?.let { day ->
            show(Route.DAILY)
            playDaily(day)
            return
        }
        if (args.summary != null) return summaryScene(args.summary, args.summaryDay)
        if (!args.startGame) return
        val seed = args.seedKey?.let { com.tavoohoh.quiet.core.FNV1a.hash(it) } ?: Random.nextLong().toULong()
        val session = args.boardValues?.let {
            if (args.plusRemaining != null) GameSession.withBoard(Board.of(it), seed = seed, plusRemaining = args.plusRemaining)
            else GameSession.withValues(it, seed = seed)
        } ?: GameSession(seed = seed)
        // Debug games aren't saved over the player's classic game.
        val model = GameModel(session)
        play.begin(session, GameKind.Classic)
        activeGame?.dispose()
        activeGame = model
        activeKind = GameKind.Classic
        show(Route.GAME)
        // `moves`: plays the first valid pairs (screenshots: ghosts and a score on the board).
        repeat(args.moves) {
            val pair = MatchRules.validPairs(model.session.board).getOrNull(it % 2) ?: return@repeat
            model.tap(model.session.board.cells[pair.first])
            model.tap(model.session.board.cells[pair.second])
        }
        if (args.hint) model.showHint()
    }

    /** One move from the end, played: a completed daily (`summaryDay`, today by default) or a classic game with no moves left. */
    private fun summaryScene(kind: String, summaryDay: String?) {
        val model: GameModel
        if (kind == "daily") {
            val day = summaryDay ?: todayKey
            val goal = DailyChallenge.goal(day)
            val board = Board.of(listOf(4, 4, 1, 2, 1, 2, 1, 2, 3, 7))
            model = GameModel(GameSession.withBoard(board, GameMode.Daily(day), score = goal - 1, goal = goal))
            play.begin(model.session, GameKind.Daily(day))
            attach(model, GameKind.Daily(day))
            show(Route.DAILY, Route.GAME)
        } else {
            val board = Board.of(listOf(1, 2, 1, 2, 1, 1, 3, 4, 3))
            model = GameModel(GameSession.withBoard(board, plusRemaining = 0, score = 1240, stage = 3))
            play.begin(model.session, GameKind.Classic)
            attach(model, GameKind.Classic)
            show(Route.GAME)
        }
        val cells = model.board.cells
        val (a, b) = if (kind == "daily") 0 to 1 else 4 to 5
        model.tap(cells[a])
        model.tap(cells[b])
    }

    companion object {
        private const val PREFS = "quietnumbers"
        // The same keys as iOS's `PreferenceKey`.
        private const val KEY_APPEARANCE = "appearanceMode"
        private const val KEY_SOUND = "soundEnabled"
        private const val KEY_HAPTICS = "hapticsEnabled"
        private const val KEY_LARGER = "largerNumbers"
        private const val KEY_TUTORIAL = "tutorialSeen"
        private const val KEY_CHEAP_HINTS = "cheapHints"
        private const val KEY_THEME = "theme"
        private const val KEY_TILES = "tileStyle"
        private const val KEY_FONT = "numberFont"
        private const val KEY_CLEAR = "clearEffect"
        private const val KEY_SOUND_PACK = "soundPack"
        private const val KEY_ICON = "appIcon"
        /** Cosmetic choices, cleared by a reset (iOS `PreferenceKey.rewards`). */
        private val REWARD_KEYS = listOf(KEY_THEME, KEY_TILES, KEY_FONT, KEY_CLEAR, KEY_SOUND_PACK, KEY_ICON, KEY_CHEAP_HINTS)
        fun storeDirectory(context: Context) = File(context.filesDir, "store")
    }
}

/** The daily reminders with the family's texts (QuietKit). UI tests get their own preferences. */
fun numbersReminders(context: Context, uiTesting: Boolean = false, state: () -> Pair<Boolean, Int>): DailyReminders =
    DailyReminders(context.applicationContext, R.drawable.ic_notification, if (uiTesting) "dailyReminders-uitesting" else "dailyReminders").apply {
        clock = AppClock::now
        this.state = state
        useStandardTexts(context)
    }

/**
 * Debug launch arguments as intent extras (iOS's `-startGame`, `-seedKey`, `-boardValues`, `-demo`…), e.g.
 * `adb shell am start -n com.tavoohoh.quietnumbers/.MainActivity --ez startGame true --es seedKey demo`.
 */
data class LaunchArgs(
    val uiTesting: Boolean = false,
    val startGame: Boolean = false,
    val seedKey: String? = null,
    val boardValues: List<Int>? = null,
    val open: String? = null,
    val date: Instant? = null,
    /** `daily` or `classic`: the game's last move already played, for the summary. */
    val summary: String? = null,
    /** With `boardValues`: + uses left (0 makes the game end once no pair is left). */
    val plusRemaining: Int? = null,
    /** Pairs played right away, and a hint shown after them (screenshots). */
    val moves: Int = 0,
    val hint: Boolean = false,
    /** Fills the store with a streak, past days, a daily halfway and a classic game (screenshots, iOS `-demo`). */
    val demo: Boolean = false,
    /** Opens a day's challenge (`yyyy-MM-dd`) from the Daily screen. */
    val playDay: String? = null,
    /** With `summary daily`: that past day's challenge instead of today's (iOS `-summaryDay`). */
    val summaryDay: String? = null,
    /** Every achievement unlocked (QA and screenshots, iOS `-unlockAll`). */
    val unlockAll: Boolean = false,
    /** UI tests: show the tutorial before the first game (iOS `-tutorial`). */
    val tutorial: Boolean = false,
) {
    companion object {
        fun from(intent: Intent?): LaunchArgs {
            val e = intent?.extras ?: return LaunchArgs()
            return LaunchArgs(
                uiTesting = e.getBoolean("uiTesting"),
                startGame = e.getBoolean("startGame"),
                seedKey = e.getString("seedKey"),
                boardValues = e.getString("boardValues")?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.isNotEmpty() },
                open = e.getString("open"),
                date = e.getString("date")?.let { runCatching { Instant.parse(it) }.getOrNull() },
                summary = e.getString("summary"),
                moves = e.getInt("moves"),
                hint = e.getBoolean("hint"),
                plusRemaining = if (e.containsKey("plusRemaining")) e.getInt("plusRemaining") else null,
                demo = e.getBoolean("demo"),
                playDay = e.getString("playDay"),
                summaryDay = e.getString("summaryDay"),
                unlockAll = e.getBoolean("unlockAll"),
                tutorial = e.getBoolean("tutorial"),
            )
        }
    }
}
