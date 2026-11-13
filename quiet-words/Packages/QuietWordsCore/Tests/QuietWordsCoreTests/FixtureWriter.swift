import Foundation
import Testing
@testable import QuietWordsCore
import QuietCore

/// Writes the cross-platform fixtures that the Android port (`Android/fixtures/` of the app) must
/// match: hashes, PRNG output, normalized theme words and daily/free boards. Does nothing
/// unless `QUIET_WRITE_FIXTURES=<dir>` is set, so normal test runs are unaffected.
///
///     QUIET_WRITE_FIXTURES=<Android>/fixtures swift test -c release --filter FixtureWriter
@Suite struct FixtureWriter {
    @Test func writeFixtures() throws {
        guard let path = ProcessInfo.processInfo.environment["QUIET_WRITE_FIXTURES"], !path.isEmpty else { return }
        let dir = URL(fileURLWithPath: path, isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try write(basics(), to: dir.appendingPathComponent("words.json"))
        try write(themes(), to: dir.appendingPathComponent("themes.json"))
        try write(dailies(), to: dir.appendingPathComponent("daily.json"))
        try write(freeBoards(), to: dir.appendingPathComponent("free.json"))
    }

    private func write(_ object: Any, to url: URL) throws {
        let data = try JSONSerialization.data(withJSONObject: object, options: [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes])
        try data.write(to: url)
    }

    private func hex(_ value: UInt64) -> String { String(format: "%016llx", value) }

    // MARK: Basics

    /// Words-specific helpers. FNV-1a, SplitMix64 and day keys are QuietCore's own fixtures
    /// (QuietKit/Tests/QuietCoreTests/FixtureWriter.swift).
    private func basics() -> [String: Any] {
        var shuffles: [[String: Any]] = []
        for seed: UInt64 in [0, 3, 99] {
            var rng = SeededRandom(seed: seed)
            shuffles.append(["seed": hex(seed), "result": Generator.shuffled(Array(0..<20), &rng)])
        }
        var levels: [[String: Any]] = []
        var day = "2026-10-20"
        for _ in 0..<60 {
            levels.append(["key": day, "level": DailyChallenge.level(forDayKey: day).rawValue,
                           "longHidden": DailyChallenge.hasLongHidden(day)])
            day = DailyKey.dayKey(day, addingDays: 1)!
        }
        let normalize = [("Estrella de mar", "es"), ("Pingüino", "es"), ("Ñandú", "es"), ("Ñandú", "en"), ("Café au lait", "en"),
                         ("Piña", "en"), ("Pu-erh", "en"), ("Rock ’n’ roll", "en"), ("N\u{303}andu\u{301}", "es"),
                         ("Straße", "en"), ("Æther", "en"), ("Øresund", "en"), ("crème brûlée", "en"), ("Çay", "es"), ("niño", "es")]
            .map { text, code -> [String: Any] in
                let language = WordLanguage(rawValue: code)!
                let out = WordText.normalize(text, language: language)
                return ["text": text, "language": code, "letters": out, "isBoardText": WordText.isBoardText(out, language: language)]
            }
        return ["shuffle": shuffles, "levels": levels, "normalize": normalize]
    }

    // MARK: Content

    private func themes() -> [String: Any] {
        let library = ThemeLibrary.shared
        var themes: [[String: Any]] = []
        for theme in library.themes {
            var languages: [String: Any] = [:]
            for language in WordLanguage.allCases {
                guard let words = theme.words(language) else { continue }
                languages[language.rawValue] = [
                    "name": words.name, "hidden": words.hidden,
                    "words": words.all.map { ["text": $0.text, "letters": $0.letters, "group": $0.group.rawValue, "count": $0.count] },
                ]
            }
            themes.append(["id": theme.id, "symbol": theme.symbol, "languages": languages])
        }
        var blocklist: [String: Any] = [:]
        for language in WordLanguage.allCases { blocklist[language.rawValue] = library.blocklist[language] ?? [] }
        return ["themes": themes, "blocklist": blocklist, "lockEntries": library.lock.entries.map(\.from)]
    }

    // MARK: Boards

    private func json(_ board: Board) -> [String: Any] {
        [
            "side": board.side, "language": board.language.rawValue, "hidden": board.hidden, "hiddenCells": board.hiddenCells,
            "rows": (0..<board.side).map { r in board.letters[(r * board.side)..<((r + 1) * board.side)].joined() },
            "placements": board.placements.map {
                ["text": $0.word.text, "letters": $0.word.letters, "group": $0.word.group.rawValue,
                 "row": $0.row, "column": $0.column, "dr": $0.direction.dr, "dc": $0.direction.dc]
            },
            "fingerprint": fingerprint(board),
        ]
    }

    /// Every day from before the rotation epoch to well past a dozen rounds, plus a few far ones.
    private func dailies() -> [[String: Any]] {
        var days: [String] = []
        var day = "2026-10-25"
        while day <= "2027-03-31" {
            days.append(day)
            day = DailyKey.dayKey(day, addingDays: 1)!
        }
        days += ["2027-07-04", "2027-12-31", "2028-02-29", "2030-06-15"]
        var out: [[String: Any]] = []
        for day in days {
            for language in WordLanguage.allCases {
                var entry: [String: Any] = ["day": day, "language": language.rawValue,
                                            "level": DailyChallenge.level(forDayKey: day).rawValue,
                                            "theme": DailyChallenge.themeID(forDayKey: day) ?? NSNull()]
                entry["board"] = DailyChallenge.board(forDayKey: day, language: language).map(json) ?? NSNull()
                out.append(entry)
            }
        }
        return out
    }

    /// Free boards as `GeneratorTests` builds them, plus the session board.
    private func freeBoards() -> [[String: Any]] {
        let library = ThemeLibrary.shared
        var out: [[String: Any]] = []
        func add(_ key: String, _ themeID: String, _ level: Level, _ language: WordLanguage) {
            let words = library.theme(themeID)!.words(language)!
            let board = Generator.board(key: key, pool: words.all, hidden: words.hidden, language: language, level: level,
                                        blocklist: library.blocklist[language]!)
            out.append(["key": key, "theme": themeID, "level": level.rawValue, "language": language.rawValue,
                        "board": board.map(json) ?? NSNull()])
        }
        for i in 0..<24 {
            for level in Level.allCases {
                for language in WordLanguage.allCases {
                    add("test-\(i)", library.themes[i % library.themes.count].id, level, language)
                }
            }
        }
        for level in Level.allCases { add("session", "ocean", level, .en) }
        add("Ñandú-ñ", "biology", .hard, .es)
        return out
    }
}
