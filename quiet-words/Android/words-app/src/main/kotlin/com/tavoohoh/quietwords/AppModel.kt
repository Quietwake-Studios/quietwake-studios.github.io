package com.tavoohoh.quietwords

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tavoohoh.quiet.core.DailyKey
import com.tavoohoh.quietwords.core.DailyChallenge
import com.tavoohoh.quietwords.core.FreeGame
import com.tavoohoh.quietwords.core.GameSession
import com.tavoohoh.quietwords.core.Level
import com.tavoohoh.quietwords.core.ThemeLibrary
import com.tavoohoh.quietwords.core.WordLanguage
import com.tavoohoh.quietwords.core.WordTheme
import com.tavoohoh.quietwords.design.systemWordLanguage
import com.tavoohoh.quietwords.game.GameModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.random.Random

/** Debug `uiTesting`: cells mark "start"/"end" of the next word and the hidden word row carries the answer. */
val LocalUiTesting = staticCompositionLocalOf { false }

enum class Route { HOME, NEW_GAME, GAME, TUTORIAL }

sealed interface GameKind {
    data object Free : GameKind
    /** Phase 2: today's board, without saving. */
    data class Daily(val dayKey: String) : GameKind
}

/**
 * The app's state (survives rotation): navigation, the words language and the game on
 * screen. Phase 2 keeps only the words language, the last level/theme and "tutorial done";
 * games and dailies are saved from Phase 3.
 */
class AppModel(application: Application) : AndroidViewModel(application) {
    private var prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var configured = false

    val library: ThemeLibrary get() = ThemeLibrary.shared

    var wordLanguage: WordLanguage by mutableStateOf(WordLanguage.EN)
        private set
    var hasChosenLanguage by mutableStateOf(false)
        private set
    var tutorialDone by mutableStateOf(false)
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

    val todayKey: String get() = DailyKey.dayKey(Instant.now())
    val needsTutorial: Boolean get() = !tutorialDone

    /** Once per process; debug launch arguments (intent extras) only in debug builds. */
    fun configure(intent: Intent?) {
        if (configured) return
        configured = true
        val args = if (BuildConfig.DEBUG) LaunchArgs.from(intent) else LaunchArgs()
        uiTesting = args.uiTesting
        if (args.uiTesting) {
            // Its own store, empty on every launch (like iOS -uiTesting).
            prefs = getApplication<Application>().getSharedPreferences("$PREFS-uitesting", Context.MODE_PRIVATE)
            prefs.edit(commit = true) { clear() }
        }
        load()
        args.words?.let { chooseLanguage(it) }
        if (args.tutorialDone) finishTutorial()
        applyDebug(args)
        // Warm the theme library off the main thread.
        viewModelScope.launch(Dispatchers.Default) { library }
    }

    var uiTesting = false
        private set

    private fun load() {
        wordLanguage = prefs.getString(KEY_LANGUAGE, null)?.let(WordLanguage::fromRaw) ?: systemWordLanguage()
        hasChosenLanguage = prefs.getBoolean(KEY_CHOSE_LANGUAGE, false)
        tutorialDone = prefs.getBoolean(KEY_TUTORIAL, false)
        lastLevel = prefs.getString(KEY_LEVEL, null)?.let(Level::fromRaw) ?: Level.GENTLE
        lastThemeID = prefs.getString(KEY_THEME, null)
    }

    // Navigation

    fun push(route: Route) {
        stack.add(route)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun popToHome() = show()

    private fun show(vararg routes: Route) {
        stack.clear()
        stack.add(Route.HOME)
        stack.addAll(routes)
    }

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

    fun theme(id: String): WordTheme? = library.theme(id)

    // Games

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
        viewModelScope.launch {
            val key = "free-${Random.nextLong().toULong()}"
            val session = withContext(Dispatchers.Default) { FreeGame.session(id, level, language, key, library) }
            isPreparing = false
            if (session != null) {
                start(GameModel(session), GameKind.Free)
                show(Route.GAME)
            }
        }
    }

    /** Today's board in the current words language (Phase 2: always a fresh attempt, nothing saved). */
    fun playDaily() {
        if (isPreparing) return
        val day = todayKey
        val language = wordLanguage
        isPreparing = true
        viewModelScope.launch {
            val session = withContext(Dispatchers.Default) { DailyChallenge.session(day, language, library) }
            isPreparing = false
            if (session != null) {
                start(GameModel(session), GameKind.Daily(day))
                show(Route.GAME)
            }
        }
    }

    /** The same board again from the start. */
    fun restart() {
        val game = activeGame ?: return
        val kind = activeKind ?: return
        val old = game.state
        start(GameModel(GameSession(old.mode, old.themeID, old.level, old.board, old.scoring)), kind)
    }

    fun start(model: GameModel, kind: GameKind) {
        activeGame?.dispose()
        activeGame = model
        activeKind = kind
    }

    override fun onCleared() {
        activeGame?.dispose()
    }

    // Debug

    private fun applyDebug(args: LaunchArgs) {
        when (args.open) {
            "newGame" -> show(Route.NEW_GAME)
            "tutorial" -> show(Route.TUTORIAL)
            "daily" -> playDaily()
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
        start(model, GameKind.Free)
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
            )
        }
    }
}
