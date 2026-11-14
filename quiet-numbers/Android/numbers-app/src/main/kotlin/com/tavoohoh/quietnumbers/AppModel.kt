package com.tavoohoh.quietnumbers

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.AndroidViewModel
import com.tavoohoh.quiet.core.DailyKey
import com.tavoohoh.quiet.ui.QuietTones
import com.tavoohoh.quietnumbers.core.Board
import com.tavoohoh.quietnumbers.core.DailyChallenge
import com.tavoohoh.quietnumbers.core.GameMode
import com.tavoohoh.quietnumbers.core.GameSession
import com.tavoohoh.quietnumbers.design.FeedbackService
import com.tavoohoh.quietnumbers.game.GameModel
import java.time.Instant
import kotlin.random.Random

/** Debug `uiTesting` (no splash). */
val LocalUiTesting = staticCompositionLocalOf { false }

enum class Route { HOME, GAME }

/** Which game is on screen. */
sealed interface GameKind {
    data object Classic : GameKind
    data class Daily(val dayKey: String) : GameKind
}

/** "Now" for anything dated. Debug `--es date <ISO-8601>` fixes it. */
object AppClock {
    var fixed: Instant? = null
    fun now(): Instant = fixed ?: Instant.now()
    val todayKey: String get() = DailyKey.dayKey(now())
}

/**
 * The app's state, Phase 2: navigation and the game on screen. Saving, the daily calendar,
 * progress and settings come with Phases 3 and 4 (the same structure as Quiet Words).
 */
class AppModel(application: Application) : AndroidViewModel(application) {
    private var configured = false
    val stack = mutableStateListOf(Route.HOME)
    var activeGame: GameModel? by mutableStateOf(null)
        private set
    var activeKind: GameKind? by mutableStateOf(null)
        private set
    var uiTesting = false
        private set
    lateinit var feedback: FeedbackService
        private set
    val todayKey: String get() = AppClock.todayKey

    fun configure(intent: Intent?) {
        if (configured) return
        configured = true
        val args = if (BuildConfig.DEBUG) LaunchArgs.from(intent) else LaunchArgs()
        uiTesting = args.uiTesting
        AppClock.fixed = args.date
        feedback = FeedbackService(QuietTones(getApplication()))
        applyDebug(args)
    }

    fun push(route: Route) {
        stack.add(route)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun popToHome() {
        stack.clear()
        stack.add(Route.HOME)
    }

    /** A new classic game (random seed). */
    fun newClassic() {
        start(GameModel(GameSession(seed = Random.nextLong().toULong())), GameKind.Classic)
        popToHome()
        push(Route.GAME)
    }

    /** Today's challenge (Phase 2: a fresh attempt, nothing saved). */
    fun playDaily() {
        val day = todayKey
        start(GameModel(DailyChallenge.session(day)), GameKind.Daily(day))
        push(Route.GAME)
    }

    private fun start(model: GameModel, kind: GameKind) {
        activeGame?.dispose()
        activeGame = model
        activeKind = kind
    }

    override fun onCleared() {
        activeGame?.dispose()
    }

    // Debug

    private fun applyDebug(args: LaunchArgs) {
        if (args.open == "daily") return playDaily()
        if (args.summary != null) return summaryScene(args.summary)
        if (!args.startGame) return
        val seed = args.seedKey?.let { com.tavoohoh.quiet.core.FNV1a.hash(it) } ?: Random.nextLong().toULong()
        val session = args.boardValues?.let {
            if (args.plusRemaining != null) GameSession.withBoard(Board.of(it), seed = seed, plusRemaining = args.plusRemaining)
            else GameSession.withValues(it, seed = seed)
        } ?: GameSession(seed = seed)
        val model = GameModel(session)
        start(model, GameKind.Classic)
        push(Route.GAME)
        // `moves`: plays the first valid pairs (screenshots: ghosts and a score on the board).
        repeat(args.moves) {
            val pair = com.tavoohoh.quietnumbers.core.MatchRules.validPairs(model.session.board).getOrNull(it % 2) ?: return@repeat
            model.tap(model.session.board.cells[pair.first])
            model.tap(model.session.board.cells[pair.second])
        }
        if (args.hint) model.showHint()
    }

    /** One move from the end, played: a completed daily (fireworks and "Done") or a classic game with no moves left. */
    private fun summaryScene(kind: String) {
        val model: GameModel
        if (kind == "daily") {
            val day = todayKey
            val goal = DailyChallenge.goal(day)
            val board = Board.of(listOf(4, 4, 1, 2, 1, 2, 1, 2, 3, 7))
            model = GameModel(GameSession.withBoard(board, GameMode.Daily(day), score = goal - 1, goal = goal))
            start(model, GameKind.Daily(day))
        } else {
            val board = Board.of(listOf(1, 2, 1, 2, 1, 1, 3, 4, 3))
            model = GameModel(GameSession.withBoard(board, plusRemaining = 0, score = 1240, stage = 3))
            start(model, GameKind.Classic)
        }
        push(Route.GAME)
        val cells = model.board.cells
        val (a, b) = if (kind == "daily") 0 to 1 else 4 to 5
        model.tap(cells[a])
        model.tap(cells[b])
    }

}

/**
 * Debug launch arguments as intent extras (iOS's `-startGame`, `-seedKey`, `-boardValues`…), e.g.
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
            )
        }
    }
}
