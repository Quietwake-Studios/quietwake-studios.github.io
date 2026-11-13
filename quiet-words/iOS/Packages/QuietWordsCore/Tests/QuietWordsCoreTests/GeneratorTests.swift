import Foundation
import Testing
@testable import QuietWordsCore

@Suite struct GeneratorTests {
    let library = ThemeLibrary.shared

    func board(_ i: Int, _ level: Level, _ language: WordLanguage) -> Board? {
        let theme = library.themes[i % library.themes.count]
        let words = theme.words(language)!
        return Generator.board(key: "test-\(i)", pool: words.all, hidden: words.hidden, language: language, level: level,
                               blocklist: library.blocklist[language]!)
    }

    @Test func sameKeySameBoard() {
        for level in Level.allCases {
            #expect(board(7, level, .es) == board(7, level, .es))
            #expect(board(7, level, .es) != board(8, level, .es))
        }
    }

    @Test func boardsFollowTheLevel() {
        for level in Level.allCases {
            for language in WordLanguage.allCases {
                for i in 0..<20 {
                    let b = try! #require(board(i, level, language))
                    #expect(problems(b, level: level, blocklist: library.blocklist[language]!).isEmpty)
                    let range = level == .gentle ? 8...20 : level == .medium ? 13...24 : 14...28
                    #expect(range.contains(b.placements.count), "\(level) \(b.placements.count) words")
                    if level == .hard { #expect(b.placements.filter { $0.word.group != .common }.count >= 3) }
                    if level == .gentle { #expect(b.placements.allSatisfy { $0.word.group == .common }) }
                }
            }
        }
    }

    /// 10,000 boards: none fails, every word reads once, no blocked word, the leftover
    /// letters spell the hidden word. Run with `swift test -c release` (slow in debug).
    @Test func tenThousandBoards() {
        var failures: [String] = []
        for i in 0..<10_000 {
            let level = Level.allCases[i % 3]
            let language = WordLanguage.allCases[(i / 3) % 2]
            guard let b = board(i, level, language) else { failures.append("\(i) \(level) \(language): no board"); continue }
            let issues = problems(b, level: level, blocklist: library.blocklist[language]!)
            if !issues.isEmpty { failures.append("\(i): \(issues)") }
        }
        #expect(failures.isEmpty, "\(failures.prefix(10))")
    }

    /// Goal: under 30 ms per board on an old iPhone. A Mac in release does a Hard board in a
    /// few ms; this fails above 20 ms on average (150 ms in Debug, e.g. from the Xcode scheme).
    @Test func generationTime() {
        #if DEBUG
        let limit = 150.0
        #else
        let limit = 20.0
        #endif
        for level in Level.allCases {
            let start = Date()
            for i in 0..<100 { _ = board(i, level, .es) }
            let ms = Date().timeIntervalSince(start) * 10
            #expect(ms < limit, "\(level): \(ms) ms per board")
        }
    }
}
