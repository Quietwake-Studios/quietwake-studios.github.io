import Testing
@testable import QuietSudokuCore
import QuietCore

@Suite struct DailyTests {
    /// Reference boards: the same date gives the same puzzle on every device and version.
    /// Changing the generator, the solver or the technique order changes them: only on purpose
    /// (last time 2026-11-13, the easier scale, before the 1.0 was published).
    static let reference: [(day: String, givens: String)] = [
        ("2026-11-02", "2...9.....1867.2.3.47..36.5.361..7.9.7.....3.1.2..746.7.12..38.3.4.6952.....1...7"), // easy
        ("2026-11-04", "...2..874.4187.5..8...4...6..849......6...3......529..2...1...3..9.3426.365..8..."), // medium
        ("2026-11-06", "2.4...1..5......47.....95.8..832......59.47......876..8.91.....41......5..6...2.9"), // hard
        ("2026-11-07", "..2.6.9.3..6..2....3.....5...947.3.5.1.....6.8.7.531...9.....2....3..7..2.8.1.5.."), // hard
        ("2026-11-08", ".....5.....46.7.3..5..428..7.....6.852.....411.8.....7..347..5..6.1..4.....9....."), // expert
    ]

    @Test func sameDateSamePuzzle() {
        for (day, givens) in Self.reference {
            #expect(DailyChallenge.puzzle(forDayKey: day).givens.string == givens, "\(day)")
        }
    }

    @Test func difficultyByWeekday() {
        // 2026-11-02 is a Monday.
        let expected: [Difficulty] = [.easy, .easy, .medium, .medium, .hard, .hard, .expert]
        for (offset, difficulty) in expected.enumerated() {
            #expect(DailyChallenge.difficulty(forDayKey: DailyKey.dayKey("2026-11-02", addingDays: offset)!) == difficulty)
        }
    }

    @Test func dailySession() {
        let session = DailyChallenge.session(forDayKey: "2026-11-08")
        #expect(session.mode == .daily(dayKey: "2026-11-08"))
        #expect(session.difficulty == .expert)
        #expect(session.scoring.hint == 10)
    }

    /// A whole year of dailies: unique, and the day's level by technique. Slow in debug:
    /// run with `swift test -c release`.
    @Test func aYearOfDailies() {
        var day = "2026-11-01"
        for _ in 0..<365 {
            let puzzle = DailyChallenge.puzzle(forDayKey: day)
            check(puzzle, DailyChallenge.difficulty(forDayKey: day))
            day = DailyKey.dayKey(day, addingDays: 1)!
        }
    }
}
