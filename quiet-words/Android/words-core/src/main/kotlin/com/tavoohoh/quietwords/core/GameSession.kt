package com.tavoohoh.quietwords.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Points per action (values from the brief). */
@Serializable
data class Scoring(
    val perLetter: Int = 10,
    val hiddenGuessed: Int = 100,
    val hiddenRevealed: Int = 30,
    val hint: Int = 30,
    /** Up to +50 % of the word points, falling linearly to 0 at the level's target time. */
    val maxTimeBonusPercent: Int = 50,
) {
    fun points(word: Word): Int = perLetter * word.count

    fun targetSeconds(level: Level): Int = when (level) {
        Level.GENTLE -> 180
        Level.MEDIUM -> 300
        Level.HARD -> 480
    }

    fun timeBonus(wordPoints: Int, seconds: Int, level: Level): Int {
        val target = targetSeconds(level)
        val left = maxOf(0, target - seconds)
        return wordPoints * maxTimeBonusPercent * left / (target * 100)
    }

    companion object {
        val STANDARD = Scoring()
        /** "In the Red", free play only: a hint costs one letter. Dailies always use STANDARD. */
        val CHEAP_HINTS = Scoring(hint = 10)
    }
}

/** A game from the first word to the last: found words, hints, the hidden word and the score. */
@Serializable
class GameSession(
    val mode: Mode,
    val themeID: String,
    val level: Level,
    val board: Board,
    val scoring: Scoring = Scoring.STANDARD,
) {
    @Serializable
    sealed interface Mode {
        @Serializable @SerialName("free") data object Free : Mode
        @Serializable @SerialName("daily") data class Daily(val dayKey: String) : Mode
    }

    @Serializable
    enum class HiddenState {
        /** Still hidden. */
        @SerialName("unknown") UNKNOWN,
        /** Guessed before the last word. */
        @SerialName("guessed") GUESSED,
        /** Shown when the last word was found. */
        @SerialName("revealed") REVEALED,
    }

    /** Something worth reacting to (feedback, achievements, the album). */
    sealed interface Event {
        data class Found(val index: Int, val points: Int) : Event
        data class AlreadyFound(val index: Int) : Event
        data object Miss : Event
        data class Hint(val index: Int, val cell: Int) : Event
        data class GuessRight(val points: Int) : Event
        data class GuessWrong(val triesLeft: Int) : Event
        data class Won(val summary: GameSummary) : Event
    }

    /** Placement indices in the order they were found. */
    var found: List<Int> = emptyList()
        private set
    /** Placements whose first letter a hint lit (until found). */
    var hinted: List<Int> = emptyList()
        private set
    var hintsUsed: Int = 0
        private set
    var guessesLeft: Int = GUESS_TRIES
        private set
    var hiddenState: HiddenState = HiddenState.UNKNOWN
        private set
    var elapsedSeconds: Int = 0
        private set
    /** Set when the game ends. */
    var timeBonus: Int? = null
        private set

    val language: WordLanguage get() = board.language
    val isWon: Boolean get() = found.size == board.placements.size
    val hasPlayed: Boolean get() = found.isNotEmpty() || hintsUsed > 0 || guessesLeft < GUESS_TRIES
    val isDaily: Boolean get() = mode is Mode.Daily
    val dayKey: String? get() = (mode as? Mode.Daily)?.dayKey

    val wordPoints: Int get() = found.sumOf { scoring.points(board.placements[it].word) }
    val hiddenPoints: Int get() = when (hiddenState) {
        HiddenState.UNKNOWN -> 0
        HiddenState.GUESSED -> scoring.hiddenGuessed
        HiddenState.REVEALED -> scoring.hiddenRevealed
    }
    val hintCost: Int get() = hintsUsed * scoring.hint
    /** Can go below zero: hints always cost ("In the Red"). */
    val score: Int get() = wordPoints + hiddenPoints + (timeBonus ?: 0) - hintCost

    fun isFound(index: Int): Boolean = index in found

    /** Cells of every found word (the marker layer). */
    val foundCells: Set<Int> get() = found.flatMap { board.placements[it].cells(board.side) }.toSet()

    /** First letters lit by hints, for words not found yet. */
    val hintCells: List<Int> get() = hinted.filter { !isFound(it) }.mapNotNull { board.placements[it].cells(board.side).firstOrNull() }

    /** The hidden word's letters shown so far. */
    val hiddenShown: String get() = if (hiddenState == HiddenState.UNKNOWN) "" else board.hidden

    val canGuess: Boolean get() = !isWon && hiddenState == HiddenState.UNKNOWN && guessesLeft > 0

    fun tick() {
        if (isWon) return
        elapsedSeconds += 1
    }

    /** A stroke from `start` to `end` (either way round). Null if it isn't a straight line. */
    fun select(start: Int, end: Int): List<Event>? {
        if (isWon || start == end || board.line(start, end) == null) return null
        val index = board.placement(start, end) ?: return listOf(Event.Miss)
        if (isFound(index)) return listOf(Event.AlreadyFound(index))
        found = found + index
        val events = mutableListOf<Event>(Event.Found(index, scoring.points(board.placements[index].word)))
        if (isWon) events += finish()
        return events
    }

    /** Lights the first letter of a word not found yet. Costs points even below zero. */
    fun hint(): Event? {
        if (isWon) return null
        val index = board.placements.indices.firstOrNull { !isFound(it) && it !in hinted } ?: return null
        hinted = hinted + index
        hintsUsed += 1
        return Event.Hint(index, board.placements[index].cells(board.side)[0])
    }

    /** Guesses the hidden word: 3 tries per game, a miss costs nothing. */
    fun guess(text: String): Event? {
        if (isWon || hiddenState != HiddenState.UNKNOWN || guessesLeft <= 0) return null
        if (WordText.normalize(text, language) == board.hidden) {
            hiddenState = HiddenState.GUESSED
            return Event.GuessRight(scoring.hiddenGuessed)
        }
        guessesLeft -= 1
        return Event.GuessWrong(guessesLeft)
    }

    private fun finish(): Event {
        if (hiddenState == HiddenState.UNKNOWN) hiddenState = HiddenState.REVEALED
        val bonus = scoring.timeBonus(wordPoints, elapsedSeconds, level)
        timeBonus = bonus
        return Event.Won(
            GameSummary(
                score = score, seconds = elapsedSeconds, level = level, words = board.placements.size,
                wordPoints = wordPoints, timeBonus = bonus, hiddenGuessed = hiddenState == HiddenState.GUESSED,
                hintsUsed = hintsUsed, language = language, themeID = themeID,
                foundLetters = board.placements.map { it.word.letters },
            ),
        )
    }

    /** A copy to keep (Swift's GameSession is a value type). */
    fun copy(): GameSession = restore(mode, themeID, level, board, scoring, found, hinted, hintsUsed, guessesLeft, hiddenState, elapsedSeconds, timeBonus)

    override fun equals(other: Any?): Boolean = other is GameSession && state == other.state
    override fun hashCode(): Int = state.hashCode()

    private val state: List<Any?>
        get() = listOf(mode, themeID, level, board, scoring, found, hinted, hintsUsed, guessesLeft, hiddenState, elapsedSeconds, timeBonus)

    companion object {
        const val GUESS_TRIES = 3

        /** A game with these words already found (tests, demo, tutorial). */
        fun withFound(mode: Mode, themeID: String, level: Level, board: Board, found: List<Int>, elapsedSeconds: Int = 0): GameSession =
            GameSession(mode, themeID, level, board).also {
                it.found = found
                it.elapsedSeconds = elapsedSeconds
            }

        private fun restore(
            mode: Mode, themeID: String, level: Level, board: Board, scoring: Scoring, found: List<Int>, hinted: List<Int>,
            hintsUsed: Int, guessesLeft: Int, hiddenState: HiddenState, elapsedSeconds: Int, timeBonus: Int?,
        ) = GameSession(mode, themeID, level, board, scoring).also {
            it.found = found
            it.hinted = hinted
            it.hintsUsed = hintsUsed
            it.guessesLeft = guessesLeft
            it.hiddenState = hiddenState
            it.elapsedSeconds = elapsedSeconds
            it.timeBonus = timeBonus
        }
    }
}

/** How a game ended, for the summary, achievements and the album. */
@Serializable
data class GameSummary(
    val score: Int,
    val seconds: Int,
    val level: Level,
    val words: Int,
    val wordPoints: Int,
    val timeBonus: Int,
    val hiddenGuessed: Boolean,
    val hintsUsed: Int,
    val language: WordLanguage,
    val themeID: String,
    /** Board form of every word on the board. */
    val foundLetters: List<String>,
)
