#if DEBUG
import Foundation
import QuietCore
import QuietUI
import QuietWordsCore

/// `-demo`: a fixed, believable state for App Store screenshots, kept in memory only.
/// Dates follow the real clock (so "Next challenge in…" counts down right): a 9-day daily
/// streak ending yesterday (today too with `-completeToday`), a best streak of 12, some 1,200
/// words in the album (Coffee & tea complete in both languages), a free game halfway, several
/// rewards unlocked and sample leaderboards.
enum DemoData {
    static let isActive = ProcessInfo.processInfo.arguments.contains("-demo")

    /// `-completeToday`: today's daily already won in the words' language. Without it the player
    /// hasn't played today, so they're not on today's boards either.
    private static let completesToday = ProcessInfo.processInfo.arguments.contains("-completeToday")

    private static var today: String { DailyKey.dayKey(for: AppClock.now) }

    private static func day(_ offset: Int) -> String { DailyKey.dayKey(today, addingDays: offset)! }

    /// The last 8 days, plus a 12-day run a month ago (the best streak).
    private static var completedDays: [(day: String, score: Int, seconds: Int)] {
        (1...8).map { (day(-$0), 1_420 + ($0 * 37) % 190, 230 + ($0 * 23) % 120) }
            + (14...25).map { (day(-$0), 1_380 + ($0 * 41) % 220, 250 + ($0 * 19) % 140) }
    }

    /// How much of each theme's album is filled, per language (one theme complete: its badge).
    private static let albumShare: [String: Double] = [
        "coffee_tea": 1, "ocean": 0.82, "space": 0.48, "botany": 0.41, "food": 0.37, "music": 0.29,
        "geography": 0.22, "mountain": 0.18, "biology": 0.12, "renaissance_industry": 0.08, "modern_age": 0.05,
    ]

    static var profile: Profile {
        let library = ThemeLibrary.shared
        var album = Album()
        for language in WordLanguage.allCases {
            for theme in library.themes(for: language) {
                // Spread evenly through the alphabetical list, as if found over many games.
                let words = (theme.words(language)?.all ?? []).sorted { $0.text < $1.text }.map(\.letters)
                let share = albumShare[theme.id] ?? 0
                let kept = words.indices.filter { (Double($0 + 1) * share).rounded(.down) > (Double($0) * share).rounded(.down) }
                album.add(kept.map { words[$0] }, theme: theme.id, language: language)
            }
        }
        let found = WordLanguage.allCases.reduce(0) { $0 + album.total($1, in: library).found }
        let unlocked: Set<Achievement> = ProcessInfo.processInfo.arguments.contains("-unlockAll")
            ? Set(Achievement.allCases)
            : [.firstPuzzle, .words100, .words1000, .themeComplete, .inPlainSight, .habit]
        var progress = PlayerProgress(unlocked: unlocked)
        _ = progress.record(.wordsFound(found))
        _ = progress.record(.dailyCompleted(longestStreak: 12))
        if let hard = wonHardGame() {
            for _ in 0..<57 { _ = progress.record(.won(hard, isFree: true)) }
        }
        for _ in 0..<9 { _ = progress.record(.abandoned(played: true)) }
        return Profile(progress: progress, album: album)
    }

    /// A Hard board won by finding its last word, for the counters and the best Hard score.
    private static func wonHardGame() -> GameSummary? {
        let library = ThemeLibrary.shared
        guard let words = library.theme("ocean")?.words(.en),
              let board = Generator.board(key: "quiet-demo-hard", pool: words.all, hidden: words.hidden, language: .en,
                                          level: .hard, blocklist: library.blocklist[.en] ?? [])
        else { return nil }
        var session = GameSession(mode: .free, themeID: "ocean", level: .hard, board: board,
                                  found: Array(board.placements.indices.dropLast()), elapsedSeconds: 455)
        let cells = board.placements[board.placements.count - 1].cells(side: board.side)
        for event in session.select(from: cells.first!, to: cells.last!) ?? [] {
            if case .won(let summary) = event { return summary }
        }
        return nil
    }

    @MainActor
    static func seed(_ persistence: PersistenceService, language: WordLanguage) {
        for (index, entry) in completedDays.enumerated() {
            // Most days in the player's language, some in both.
            for lang in WordLanguage.allCases where lang == language || index % 3 == 0 {
                persistence.startAttempt(DailyID(entry.day, lang))
                _ = persistence.recordWin(DailyID(entry.day, lang), score: entry.score, seconds: entry.seconds)
            }
        }
        if completesToday {
            persistence.startAttempt(DailyID(today, language))
            _ = persistence.recordWin(DailyID(today, language), score: 1_612, seconds: 268)
        }
        if let game = freeGame(language) { persistence.saveGame(game, in: .free) }
    }

    /// A medium Ocean board with a third of its words found, 2:14 on the clock (Home's "Continue").
    private static func freeGame(_ language: WordLanguage) -> GameSession? {
        let library = ThemeLibrary.shared
        guard let words = library.theme("ocean")?.words(language),
              let board = Generator.board(key: "quiet-demo", pool: words.all, hidden: words.hidden, language: language,
                                          level: .medium, blocklist: library.blocklist[language] ?? [])
        else { return nil }
        return GameSession(mode: .free, themeID: "ocean", level: .medium, board: board,
                           found: Array(board.placements.indices.prefix(board.placements.count / 3)), elapsedSeconds: 134)
    }

    /// Sample leaderboards (names from the Quiet Numbers canvas).
    static func leaderboard(_ board: Leaderboard) -> (local: GameCenterService.Entry?, top: [GameCenterService.Entry]) {
        let names = ["juli", "tatita12", "vale", "guss", "juanca", "cori16", "eliasb", "mariadelsea", "aleja", "nova47"]
        let scores: [Int] = switch board {
        case .dailyEN, .dailyES: [1_688, 1_671, 1_640, 1_612, 1_598, 1_574, 1_560, 1_533, 1_512, 1_490]
        case .bestHardEN, .bestHardES: [2_640, 2_512, 2_455, 2_186, 2_140, 2_101, 2_046, 1_990, 1_932, 1_877]
        case .longestStreak: [41, 33, 27, 12, 11, 10, 9, 8, 7, 6]
        }
        let seconds = [214, 236, 251, 268, 240, 290, 311, 284, 330, 356]
        let isDaily = board == .dailyEN || board == .dailyES
        let rows = zip(zip(names, scores), seconds).map { ($0.0, $0.1, $1) }
            .filter { !isDaily || completesToday || $0.0 != "guss" }
        let top = rows.enumerated().map { index, row in
            GameCenterService.Entry(id: row.0, rank: index + 1, name: row.0, score: row.1, isLocalPlayer: row.0 == "guss",
                                    context: isDaily ? GameCenterService.dailyContext(seconds: row.2) : 0)
        }
        return (top.first(where: \.isLocalPlayer), top)
    }

    /// Today's friends board for "Friends today" (times in the context, like real scores).
    static var friendsEntries: [GameCenterService.Entry] {
        let friends: [(String, Int, Int?)] = [("vale", 1_640, 251), ("guss", 1_612, 268), ("juanca", 1_598, 240), ("cori16", 1_574, 290)]
            .filter { completesToday || $0.0 != "guss" }
        let entries = friends.enumerated().map { index, friend in
            GameCenterService.Entry(id: friend.0, rank: index + 1, name: friend.0, score: friend.1, isLocalPlayer: friend.0 == "guss",
                                    context: GameCenterService.dailyContext(seconds: friend.2))
        }
        return GameCenterService.rankedByScoreAndTime(entries)
    }

    /// A friend's profile against the demo player: today, best, streak and the last 7 days.
    static func headToHead(with name: String) -> HeadToHead {
        func entry(_ score: Int, _ seconds: Int? = nil, mine: Bool) -> GameCenterService.Entry {
            GameCenterService.Entry(id: mine ? "guss" : name, rank: 0, name: mine ? "guss" : name, score: score,
                                    isLocalPlayer: mine, context: GameCenterService.dailyContext(seconds: seconds))
        }
        let days: [(Int, Int, Int?, Int?)] = [(1_540, 1_498, 280, 301), (1_472, 1_520, 330, 290), (1_601, 1_577, 255, 270),
                                              (1_390, 1_350, 360, 410), (1_505, 1_505, 262, 299), (1_280, 1_420, 420, 380), (1_488, 1_440, 300, 315)]
        let past = days.enumerated().map { index, day in
            HeadToHead.PastDaily(
                start: Calendar.current.date(byAdding: .day, value: -(index + 1), to: AppClock.now)!,
                matchup: .init(mine: entry(day.0, day.2, mine: true), theirs: entry(day.1, day.3, mine: false))
            )
        }
        return HeadToHead(
            today: .init(mine: completesToday ? entry(1_612, 268, mine: true) : nil, theirs: entry(1_640, 251, mine: false)),
            best: .init(mine: entry(2_186, mine: true), theirs: entry(2_455, mine: false)),
            streak: .init(mine: entry(12, mine: true), theirs: entry(27, mine: false)),
            past: past
        )
    }

    /// `-demoClock <seconds>` presets the clock of a `-startGame` board.
    static var clock: Int? {
        let arguments = ProcessInfo.processInfo.arguments
        guard let i = arguments.firstIndex(of: "-demoClock"), i + 1 < arguments.count else { return nil }
        return Int(arguments[i + 1])
    }
}
#endif
