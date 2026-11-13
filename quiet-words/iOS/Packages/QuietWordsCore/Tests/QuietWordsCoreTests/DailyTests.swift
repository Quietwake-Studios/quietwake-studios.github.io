import Testing
@testable import QuietWordsCore
import QuietCore

@Suite struct DailyTests {
    /// Reference boards: the same date and language give the same board on every device and
    /// version. Changing the generator, the themes without a lock or the rotation changes them:
    /// only on purpose.
    static let reference: [(day: String, language: WordLanguage, theme: String, fingerprint: String)] = [
        ("2026-11-09", .en, "biology", "46c3213a6c53a923"),
        ("2026-11-09", .es, "biology", "7e90ec475cc91011"),
        ("2026-11-11", .en, "botany", "d39d4820aa8f0b6e"),
        ("2026-11-14", .es, "food", "dfbee008d7950452"),
        ("2026-11-15", .en, "biology", "2a854db7fffe76cc"),
    ]

    @Test func sameDateSameBoard() {
        for ref in Self.reference {
            let board = DailyChallenge.board(forDayKey: ref.day, language: ref.language)!
            #expect(DailyChallenge.themeID(forDayKey: ref.day) == ref.theme, "\(ref.day)")
            #expect(fingerprint(board) == ref.fingerprint, "\(ref.day) \(ref.language)")
        }
    }

    @Test func levelByWeekday() {
        // 2026-11-09 is a Monday.
        let expected: [Level] = [.gentle, .gentle, .medium, .medium, .medium, .hard, .hard]
        for (offset, level) in expected.enumerated() {
            let day = DailyKey.dayKey("2026-11-09", addingDays: offset)!
            #expect(DailyChallenge.level(forDayKey: day) == level)
            #expect(DailyChallenge.hasLongHidden(day) == (offset == 6))
        }
        let sunday = DailyChallenge.board(forDayKey: "2026-11-15", language: .es)!
        #expect((7...9).contains(sunday.hidden.count))
    }

    @Test func languagesHaveTheirOwnBoard() {
        let en = DailyChallenge.session(forDayKey: "2026-11-10", language: .en)!
        let es = DailyChallenge.session(forDayKey: "2026-11-10", language: .es)!
        #expect(en.themeID == es.themeID)
        #expect(en.board.language == .en && es.board.language == .es)
        #expect(en.board.letters != es.board.letters)
        #expect(en.mode == .daily(dayKey: "2026-11-10"))
    }

    /// A year of dailies in both languages: every day has a valid board, each theme comes
    /// once per round and never two days running.
    @Test func aYearOfDailies() {
        let library = ThemeLibrary.shared
        var day = "2026-11-01"
        var previous: String?
        var round: [String] = []
        for _ in 0..<365 {
            let theme = DailyChallenge.themeID(forDayKey: day)!
            #expect(theme != previous, "\(day) repeats \(theme)")
            round.append(theme)
            if round.count == library.themes.count {
                #expect(Set(round).count == library.themes.count, "round ending \(day)")
                round = []
            }
            for language in WordLanguage.allCases {
                let level = DailyChallenge.level(forDayKey: day)
                if let board = DailyChallenge.board(forDayKey: day, language: language) {
                    #expect(problems(board, level: level, blocklist: library.blocklist[language]!).isEmpty, "\(day) \(language)")
                } else {
                    Issue.record("\(day) \(language): no board")
                }
            }
            previous = theme
            day = DailyKey.dayKey(day, addingDays: 1)!
        }
    }

    @Test func lockFreezesTheWords() {
        let full = ThemeLibrary.shared
        let counts = DailyLock.Counts(common: 90, crafted: 70, rare: 30, hidden: 15)
        var themes: [String: [String: DailyLock.Counts]] = [:]
        for theme in full.themes { themes[theme.id] = ["en": counts, "es": counts] }
        let locked = ThemeLibrary(themes: full.themes, blocklist: full.blocklist,
                                  lock: DailyLock(entries: [DailyLock.Entry(from: "2026-11-01", themes: themes)]))
        let board = DailyChallenge.board(forDayKey: "2026-11-12", language: .en, library: locked)!
        let id = DailyChallenge.themeID(forDayKey: "2026-11-12", library: locked)!
        let allowed = Set(full.theme(id)!.words(.en)!.prefix(counts).all.map(\.letters))
        #expect(board.placements.allSatisfy { allowed.contains($0.word.letters) })
    }
}
