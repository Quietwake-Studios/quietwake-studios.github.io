/// Achievements. Each one unlocks a cosmetic reward (or nothing, for Bilingual); none touches play.
public enum Achievement: String, CaseIterable, Codable, Sendable {
    /// Finish a first puzzle.
    case firstPuzzle
    case words100
    case words1000
    case words10000
    /// Find every word of a theme in one language (the album).
    case themeComplete
    /// Guess the hidden word before the end, without hints.
    case inPlainSight
    /// 7-day daily streak.
    case habit
    /// 30-day daily streak.
    case consistency
    /// Finish a Hard puzzle without hints in under 10 minutes.
    case steadyHand
    /// Finish the day's challenge in both languages. Hidden.
    case bilingual
    /// Take the score below zero. Hidden; makes hints cheaper in free play (2026-11-09).
    case inTheRed

    /// Amount needed for counted achievements; 1 for one-off ones.
    public var target: Int {
        switch self {
        case .words100: 100
        case .words1000: 1_000
        case .words10000: 10_000
        case .habit: 7
        case .consistency: 30
        default: 1
        }
    }

    public var isHidden: Bool { self == .bilingual || self == .inTheRed }

    /// "Steady Hand" needs a Hard puzzle in less than this.
    public static let steadyHandSeconds = 600
}

/// Something that happened in play, fed to `PlayerProgress`.
public enum ProgressEvent: Sendable {
    case won(GameSummary, isFree: Bool)
    /// Words found in a game (each find, even replays: "1,000 words").
    case wordsFound(Int)
    /// The active game's score after a hint (below zero: "In the Red").
    case scoreChanged(Int)
    case themeCompleted
    /// A free game was replaced before it ended; it counts as played if anything was played.
    case abandoned(played: Bool)
    /// A day's challenge was completed for the first time (in either language).
    case dailyCompleted(longestStreak: Int)
    /// Today's challenge done in both languages.
    case bothLanguages
    /// A past day first completed after its date (the calendar tints it apart).
    case completedLate(dayKey: String)
}

/// Lifetime counters and unlocked achievements. Stored locally; works without Game Center.
public struct PlayerProgress: Codable, Hashable, Sendable {
    public private(set) var unlocked: Set<Achievement> = []
    public private(set) var gamesWon = 0
    public private(set) var gamesPlayed = 0
    public private(set) var wordsFound = 0
    public private(set) var dailiesCompleted = 0
    public private(set) var bestStreak = 0
    /// Best free-play score per level and language ("hard-es").
    public private(set) var bestScores: [String: Int] = [:]
    public private(set) var lateDays: Set<String> = []

    public init() {}

    /// Starts with achievements already unlocked (QA and screenshots).
    public init(unlocked: Set<Achievement>) {
        self.unlocked = unlocked
    }

    public static func key(_ level: Level, _ language: WordLanguage) -> String { "\(level.rawValue)-\(language.rawValue)" }

    public func bestScore(_ level: Level, _ language: WordLanguage) -> Int { bestScores[Self.key(level, language)] ?? 0 }

    /// Applies an event and returns achievements unlocked by it.
    public mutating func record(_ event: ProgressEvent) -> [Achievement] {
        var hits: [Achievement] = []
        switch event {
        case .won(let summary, let isFree):
            gamesWon += 1
            gamesPlayed += 1
            hits.append(.firstPuzzle)
            if summary.hiddenGuessed, summary.hintsUsed == 0 { hits.append(.inPlainSight) }
            if summary.level == .hard, summary.hintsUsed == 0, summary.seconds < Achievement.steadyHandSeconds { hits.append(.steadyHand) }
            if isFree {
                let key = Self.key(summary.level, summary.language)
                bestScores[key] = max(bestScores[key] ?? 0, summary.score)
            }
        case .wordsFound(let count):
            wordsFound += count
        case .scoreChanged(let score):
            if score < 0 { hits.append(.inTheRed) }
        case .themeCompleted:
            hits.append(.themeComplete)
        case .abandoned(let played):
            if played { gamesPlayed += 1 }
        case .dailyCompleted(let streak):
            dailiesCompleted += 1
            bestStreak = max(bestStreak, streak)
        case .bothLanguages:
            hits.append(.bilingual)
        case .completedLate(let dayKey):
            lateDays.insert(dayKey)
        }
        hits += Achievement.allCases.filter { $0.target > 1 && count(for: $0) >= $0.target }
        return unlock(hits)
    }

    /// Combines progress from two devices: every counter keeps its highest value and
    /// achievements are the union, so nothing earned is ever lost.
    public func merged(with other: PlayerProgress) -> PlayerProgress {
        var result = self
        result.unlocked.formUnion(other.unlocked)
        result.gamesWon = max(gamesWon, other.gamesWon)
        result.gamesPlayed = max(gamesPlayed, other.gamesPlayed)
        result.wordsFound = max(wordsFound, other.wordsFound)
        result.dailiesCompleted = max(dailiesCompleted, other.dailiesCompleted)
        result.bestStreak = max(bestStreak, other.bestStreak)
        result.bestScores.merge(other.bestScores) { max($0, $1) }
        result.lateDays.formUnion(other.lateDays)
        return result
    }

    /// Progress towards an achievement, 0...1 (for Game Center's percentage).
    public func progress(of achievement: Achievement) -> Double {
        if unlocked.contains(achievement) { return 1 }
        return min(Double(count(for: achievement)) / Double(achievement.target), 1)
    }

    private func count(for achievement: Achievement) -> Int {
        switch achievement {
        case .words100, .words1000, .words10000: wordsFound
        case .habit, .consistency: bestStreak
        default: 0
        }
    }

    private mutating func unlock(_ candidates: [Achievement]) -> [Achievement] {
        candidates.filter { unlocked.insert($0).inserted }
    }
}

/// Every word found, per theme and language, by its board form (so lists can grow and be
/// reordered without losing anyone's progress). One album per language.
public struct Album: Codable, Hashable, Sendable {
    /// "ocean/es" → board forms.
    public private(set) var words: [String: Set<String>] = [:]

    public init() {}

    static func key(_ theme: String, _ language: WordLanguage) -> String { "\(theme)/\(language.rawValue)" }

    /// Adds a game's words; returns how many were new.
    @discardableResult
    public mutating func add(_ letters: [String], theme: String, language: WordLanguage) -> Int {
        let key = Self.key(theme, language)
        var set = words[key] ?? []
        let before = set.count
        set.formUnion(letters)
        words[key] = set
        return set.count - before
    }

    public func found(_ theme: WordTheme, _ language: WordLanguage) -> Set<String> {
        let all = Set(theme.words(language)?.all.map(\.letters) ?? [])
        return (words[Self.key(theme.id, language)] ?? []).intersection(all)
    }

    /// Found / total for the theme in this language ("Ocean 87/152").
    public func count(_ theme: WordTheme, _ language: WordLanguage) -> (found: Int, total: Int) {
        let total = Set(theme.words(language)?.all.map(\.letters) ?? []).count
        return (found(theme, language).count, total)
    }

    public func isComplete(_ theme: WordTheme, _ language: WordLanguage) -> Bool {
        let c = count(theme, language)
        return c.total > 0 && c.found == c.total
    }

    public func total(_ language: WordLanguage, in library: ThemeLibrary) -> (found: Int, total: Int, badges: Int) {
        var found = 0, total = 0, badges = 0
        for theme in library.themes(for: language) {
            let c = count(theme, language)
            found += c.found
            total += c.total
            if c.total > 0, c.found == c.total { badges += 1 }
        }
        return (found, total, badges)
    }

    /// How far the most advanced theme is, in either language, 0...1 ("Theme Complete").
    public func bestThemeFraction(in library: ThemeLibrary) -> Double {
        WordLanguage.allCases.flatMap { language in
            library.themes(for: language).map { theme in
                let c = count(theme, language)
                return c.total > 0 ? Double(c.found) / Double(c.total) : 0
            }
        }.max() ?? 0
    }

    public func merged(with other: Album) -> Album {
        var result = self
        result.words.merge(other.words) { $0.union($1) }
        return result
    }
}
