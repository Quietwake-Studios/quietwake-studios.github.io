/// Points per action. Values from the brief; kept here so they're easy to tune.
public struct Scoring: Hashable, Codable, Sendable {
    public var perLetter = 10
    public var hiddenGuessed = 100
    public var hiddenRevealed = 30
    public var hint = 30
    /// Up to +50 % of the word points, falling linearly to 0 at the level's target time.
    public var maxTimeBonusPercent = 50

    public static let standard = Scoring()
    /// "In the Red" (hidden achievement), free play only: a hint costs one letter instead of
    /// three, the family's 10 → 3 cut rounded to Words' letter value (2026-11-09). Daily
    /// challenges, today's or past, always use `.standard`.
    public static let cheapHints = Scoring(hint: 10)

    public init(hint: Int = 30) {
        self.hint = hint
    }

    public func points(for word: Word) -> Int { perLetter * word.count }

    public func targetSeconds(_ level: Level) -> Int {
        switch level {
        case .gentle: 180
        case .medium: 300
        case .hard: 480
        }
    }

    public func timeBonus(wordPoints: Int, seconds: Int, level: Level) -> Int {
        let target = targetSeconds(level)
        let left = max(0, target - seconds)
        return wordPoints * maxTimeBonusPercent * left / (target * 100)
    }
}

/// A game from the first word to the last: found words, hints, the hidden word and the score.
public struct GameSession: Codable, Hashable, Sendable {
    public enum Mode: Hashable, Codable, Sendable {
        case free
        case daily(dayKey: String)
    }

    public enum HiddenState: String, Hashable, Codable, Sendable {
        /// Still hidden.
        case unknown
        /// Guessed before the last word.
        case guessed
        /// Shown when the last word was found.
        case revealed
    }

    /// Something worth reacting to (feedback, achievements, the album).
    public enum Event: Hashable, Sendable {
        case found(index: Int, points: Int)
        /// A word found before.
        case alreadyFound(index: Int)
        /// A straight line that isn't a word.
        case miss
        case hint(index: Int, cell: Int)
        case guessRight(points: Int)
        case guessWrong(triesLeft: Int)
        case won(GameSummary)
    }

    public static let guessTries = 3

    public let mode: Mode
    public let themeID: String
    public let level: Level
    public let board: Board
    public let scoring: Scoring
    /// Placement indices in the order they were found.
    public private(set) var found: [Int] = []
    /// Placements whose first letter a hint lit (until found).
    public private(set) var hinted: [Int] = []
    public private(set) var hintsUsed = 0
    public private(set) var guessesLeft = GameSession.guessTries
    public private(set) var hiddenState = HiddenState.unknown
    public private(set) var elapsedSeconds = 0
    /// Set when the game ends.
    public private(set) var timeBonus: Int?

    public init(mode: Mode, themeID: String, level: Level, board: Board, scoring: Scoring = .standard) {
        self.mode = mode
        self.themeID = themeID
        self.level = level
        self.board = board
        self.scoring = scoring
    }

    public var language: WordLanguage { board.language }
    public var isWon: Bool { found.count == board.placements.count }
    public var hasPlayed: Bool { !found.isEmpty || hintsUsed > 0 || guessesLeft < Self.guessTries }
    public var isDaily: Bool { if case .daily = mode { true } else { false } }
    public var dayKey: String? { if case .daily(let key) = mode { key } else { nil } }

    public var wordPoints: Int { found.reduce(0) { $0 + scoring.points(for: board.placements[$1].word) } }
    public var hiddenPoints: Int {
        switch hiddenState {
        case .unknown: 0
        case .guessed: scoring.hiddenGuessed
        case .revealed: scoring.hiddenRevealed
        }
    }
    public var hintCost: Int { hintsUsed * scoring.hint }
    /// Can go below zero: hints always cost, like in the rest of the family ("In the Red").
    public var score: Int { wordPoints + hiddenPoints + (timeBonus ?? 0) - hintCost }

    public func isFound(_ index: Int) -> Bool { found.contains(index) }

    /// Cells of every found word (the marker layer).
    public var foundCells: Set<Int> {
        Set(found.flatMap { board.placements[$0].cells(side: board.side) })
    }

    /// First letters lit by hints, for words not found yet.
    public var hintCells: [Int] {
        hinted.filter { !isFound($0) }.compactMap { board.placements[$0].cells(side: board.side).first }
    }

    /// The hidden word's letters shown so far (all of them once guessed or revealed).
    public var hiddenShown: String { hiddenState == .unknown ? "" : board.hidden }

    public mutating func tick() {
        guard !isWon else { return }
        elapsedSeconds += 1
    }

    // MARK: Playing

    /// A stroke from `start` to `end` (either way round). Nil if it isn't a straight line.
    @discardableResult
    public mutating func select(from start: Int, to end: Int) -> [Event]? {
        guard !isWon, start != end, board.line(from: start, to: end) != nil else { return nil }
        guard let index = board.placement(from: start, to: end) else { return [.miss] }
        guard !isFound(index) else { return [.alreadyFound(index: index)] }
        found.append(index)
        var events: [Event] = [.found(index: index, points: scoring.points(for: board.placements[index].word))]
        if isWon { events.append(finish()) }
        return events
    }

    /// Lights the first letter of a word not found yet. Costs points even below zero (the
    /// game's `scoring`: dailies always `.standard`).
    @discardableResult
    public mutating func hint() -> Event? {
        guard !isWon else { return nil }
        let candidates = board.placements.indices.filter { !isFound($0) && !hinted.contains($0) }
        guard let index = candidates.first else { return nil }
        hinted.append(index)
        hintsUsed += 1
        return .hint(index: index, cell: board.placements[index].cells(side: board.side)[0])
    }

    /// Guesses the hidden word: 3 tries per game, a miss costs nothing.
    @discardableResult
    public mutating func guess(_ text: String) -> Event? {
        guard !isWon, hiddenState == .unknown, guessesLeft > 0 else { return nil }
        if WordText.normalize(text, language: language) == board.hidden {
            hiddenState = .guessed
            return .guessRight(points: scoring.hiddenGuessed)
        }
        guessesLeft -= 1
        return .guessWrong(triesLeft: guessesLeft)
    }

    public var canGuess: Bool { !isWon && hiddenState == .unknown && guessesLeft > 0 }

    private mutating func finish() -> Event {
        if hiddenState == .unknown { hiddenState = .revealed }
        timeBonus = scoring.timeBonus(wordPoints: wordPoints, seconds: elapsedSeconds, level: level)
        return .won(GameSummary(score: score, seconds: elapsedSeconds, level: level, words: board.placements.count,
                                wordPoints: wordPoints, timeBonus: timeBonus ?? 0, hiddenGuessed: hiddenState == .guessed,
                                hintsUsed: hintsUsed, language: language, themeID: themeID,
                                foundLetters: board.placements.map(\.word.letters)))
    }

    // MARK: Test and screenshot support

    /// A game with these words already found (tests, demo, tutorial).
    public init(mode: Mode, themeID: String, level: Level, board: Board, found: [Int], elapsedSeconds: Int = 0) {
        self.init(mode: mode, themeID: themeID, level: level, board: board)
        self.found = found
        self.elapsedSeconds = elapsedSeconds
    }
}

/// How a game ended, for the summary, achievements and the album.
public struct GameSummary: Hashable, Codable, Sendable {
    public let score: Int
    public let seconds: Int
    public let level: Level
    public let words: Int
    public let wordPoints: Int
    public let timeBonus: Int
    public let hiddenGuessed: Bool
    public let hintsUsed: Int
    public let language: WordLanguage
    public let themeID: String
    /// Board form of every word on the board (all found by then).
    public let foundLetters: [String]
}
