import Foundation
import QuietCore

/// Freezes what the daily challenge uses so new words never change a day already played.
/// `scripts/lock-daily.py` writes `Themes/daily-lock.json` when a version ships: from a date
/// on, which themes rotate and how many words of each list they use. New words go at the end
/// of their list, so the counted ones never move.
public struct DailyLock: Codable, Hashable, Sendable {
    static let fileName = "daily-lock"

    public struct Counts: Codable, Hashable, Sendable {
        public let common: Int
        public let crafted: Int
        public let rare: Int
        public let regional: Int
        public let hidden: Int

        public init(common: Int, crafted: Int, rare: Int, regional: Int = 0, hidden: Int) {
            self.common = common
            self.crafted = crafted
            self.rare = rare
            self.regional = regional
            self.hidden = hidden
        }

        private enum CodingKeys: String, CodingKey { case common, crafted, rare, regional, hidden }

        public init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            common = try c.decode(Int.self, forKey: .common)
            crafted = try c.decode(Int.self, forKey: .crafted)
            rare = try c.decode(Int.self, forKey: .rare)
            regional = try c.decodeIfPresent(Int.self, forKey: .regional) ?? 0
            hidden = try c.decode(Int.self, forKey: .hidden)
        }
    }

    public struct Entry: Codable, Hashable, Sendable {
        /// First day key it applies to.
        public let from: String
        /// WordTheme id → language code → counts.
        public let themes: [String: [String: Counts]]

        public init(from: String, themes: [String: [String: Counts]]) {
            self.from = from
            self.themes = themes
        }
    }

    public let entries: [Entry]

    public init(entries: [Entry]) {
        self.entries = entries.sorted { $0.from < $1.from }
    }

    /// The entry in force on a day (day keys sort as text).
    public func entry(forDayKey key: String) -> Entry? {
        entries.last { $0.from <= key } ?? entries.first
    }
}

/// Today's board: the same for everyone on a day key and language, built on device.
public enum DailyChallenge {
    /// Without a lock (before the first release), the rotation starts here with every
    /// bilingual theme and all its words.
    static let unlockedEpoch = "2026-11-01"

    /// Monday and Tuesday Gentle, Wednesday to Friday Medium, the weekend Hard.
    public static func level(forDayKey key: String) -> Level {
        switch DailyKey.weekday(ofDayKey: key) ?? 3 {
        case 1, 2: .gentle
        case 3, 4, 5: .medium
        default: .hard
        }
    }

    /// Sunday: Hard with a long hidden word.
    public static func hasLongHidden(_ key: String) -> Bool { DailyKey.weekday(ofDayKey: key) == 7 }

    /// The day's theme: a fixed order of the themes, reshuffled once per round (each theme
    /// once every N days) and never the same theme two days running across rounds.
    public static func themeID(forDayKey key: String, library: ThemeLibrary = .shared) -> String? {
        let (ids, epoch) = rotation(forDayKey: key, library: library)
        guard !ids.isEmpty, let index = dayIndex(key, from: epoch) else { return nil }
        let round = floorDiv(index, ids.count)
        return order(ids, round: round, epoch: epoch)[mod(index, ids.count)]
    }

    public static func board(forDayKey key: String, language: WordLanguage, library: ThemeLibrary = .shared) -> Board? {
        guard let id = themeID(forDayKey: key, library: library), let theme = library.theme(id),
              let words = theme.words(language)?.prefix(counts(id, language, key, library))
        else { return nil }
        let level = level(forDayKey: key)
        let (ids, epoch) = rotation(forDayKey: key, library: library)
        let appearance = max(0, floorDiv(dayIndex(key, from: epoch) ?? 0, max(ids.count, 1)))
        return Generator.board(key: "\(key)#\(language.rawValue)", pool: dayPool(words, id: id, language: language, level: level, appearance: appearance, epoch: epoch),
                               hidden: words.hidden, language: language, level: level, longHidden: hasLongHidden(key),
                               blocklist: library.blocklist[language] ?? [])
    }

    public static func session(forDayKey key: String, language: WordLanguage, library: ThemeLibrary = .shared) -> GameSession? {
        guard let board = board(forDayKey: key, language: language, library: library),
              let id = themeID(forDayKey: key, library: library)
        else { return nil }
        return GameSession(mode: .daily(dayKey: key), themeID: id, level: level(forDayKey: key), board: board)
    }

    // MARK: Private

    /// Each theme walks its list in a seeded order; each appearance starts further along, so
    /// words don't repeat in the daily until the list runs out.
    static func dayPool(_ words: ThemeWords, id: String, language: WordLanguage, level: Level, appearance: Int, epoch: String) -> [Word] {
        let eligible = Generator.eligible(words.all, level: level, language: language)
        guard !eligible.isEmpty else { return [] }
        var rng = SeededRandom(seed: FNV1a.hash("words-\(id)-\(language.rawValue)-\(epoch)"))
        let walk = Generator.shuffled(eligible, &rng)
        let size = min(walk.count, max(level.typicalWords * 4, 60))
        let start = (appearance * level.typicalWords) % walk.count
        return (0..<size).map { walk[(start + $0) % walk.count] }
    }

    private static func rotation(forDayKey key: String, library: ThemeLibrary) -> (ids: [String], epoch: String) {
        if let entry = library.lock.entry(forDayKey: key) {
            return (entry.themes.keys.filter { library.theme($0)?.isBilingual == true }.sorted(), entry.from)
        }
        return (library.themes.filter(\.isBilingual).map(\.id), unlockedEpoch)
    }

    private static func counts(_ id: String, _ language: WordLanguage, _ key: String, _ library: ThemeLibrary) -> DailyLock.Counts? {
        library.lock.entry(forDayKey: key)?.themes[id]?[language.rawValue]
    }

    private static func order(_ ids: [String], round: Int, epoch: String) -> [String] {
        func shuffledRound(_ r: Int) -> [String] {
            var rng = SeededRandom(seed: FNV1a.hash("round-\(epoch)-\(r)"))
            return Generator.shuffled(ids, &rng)
        }
        var current = shuffledRound(round)
        if ids.count > 1, current.first == shuffledRound(round - 1).last {
            current.swapAt(0, 1)
        }
        return current
    }

    private static func dayIndex(_ key: String, from epoch: String) -> Int? {
        guard let day = DailyKey.date(forDayKey: key), let start = DailyKey.date(forDayKey: epoch) else { return nil }
        return Int((day.timeIntervalSince(start) / 86_400).rounded())
    }

    private static func floorDiv(_ a: Int, _ b: Int) -> Int { Int((Double(a) / Double(b)).rounded(.down)) }
    private static func mod(_ a: Int, _ b: Int) -> Int { ((a % b) + b) % b }
}
