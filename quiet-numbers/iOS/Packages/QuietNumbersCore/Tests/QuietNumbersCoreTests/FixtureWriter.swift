import Foundation
import Testing
@testable import QuietNumbersCore
import QuietCore

/// Writes the cross-platform fixtures that the Android port (`quiet-numbers/Android/fixtures/`)
/// must match: daily goals and boards stage by stage, classic boards, and whole games played
/// move by move (matches, +, hints, misses and undo) with the state after each step. Does
/// nothing unless `QUIET_WRITE_FIXTURES=<dir>` is set, so normal test runs are unaffected.
///
///     QUIET_WRITE_FIXTURES=<quiet-numbers>/Android/fixtures swift test -c release --filter FixtureWriter
@Suite struct FixtureWriter {
    @Test func writeFixtures() throws {
        guard let path = ProcessInfo.processInfo.environment["QUIET_WRITE_FIXTURES"], !path.isEmpty else { return }
        let dir = URL(fileURLWithPath: path, isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try write(boards(), to: dir.appendingPathComponent("boards.json"))
        try write(games(), to: dir.appendingPathComponent("games.json"))
    }

    private func write(_ object: Any, to url: URL) throws {
        let data = try JSONSerialization.data(withJSONObject: object, options: [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes])
        try data.write(to: url)
    }

    private func hex(_ value: UInt64) -> String { String(format: "%016llx", value) }

    // MARK: Boards

    /// Daily goals and the first five stages' boards of each day (the generator advances
    /// only when a board is made), plus classic seeds.
    private func boards() -> [String: Any] {
        var dailies: [[String: Any]] = []
        var day = "2026-10-25"
        for _ in 0..<60 {
            var random = SeededRandom(seed: DailyKey.seed(forDayKey: day))
            let stages = (1...5).map { BoardGenerator.makeBoard(using: &random, stage: $0).cells.map(\.value) }
            dailies.append(["key": day, "goal": DailyChallenge.goal(forDayKey: day), "stages": stages])
            day = DailyKey.dayKey(day, addingDays: 1)!
        }
        var classic: [[String: Any]] = []
        for seed: UInt64 in [0, 1, 2, 42, 1234, 0xDEADBEEF, UInt64.max] {
            var random = SeededRandom(seed: seed)
            classic.append(["seed": hex(seed), "board": BoardGenerator.makeBoard(using: &random).cells.map(\.value)])
        }
        var pairable: [[String: Any]] = []
        for values in [[1, 9, 5, 5], [1, 9, 5], [2, 8, 8, 2, 3], [4, 6, 6, 4, 7, 3]] {
            pairable.append(["values": values, "pairable": BoardGenerator.isPairable(values)])
        }
        return ["daily": dailies, "classic": classic, "pairable": pairable]
    }

    // MARK: Games

    /// Games played by a fixed script: a match (the n-th valid pair, cycling), + when stuck,
    /// a hint every 7th step, a miss every 5th and an undo every 9th, until the game ends or
    /// 220 steps. Each step records the full state.
    private func games() -> [String: Any] {
        var games: [[String: Any]] = []
        let starts: [(String, GameSession)] = [
            ("classic-7", GameSession(seed: 7)),
            ("classic-99", GameSession(seed: 99)),
            ("classic-cheap", GameSession(seed: 2026, scoring: .cheapHints)),
            ("daily-2026-11-14", DailyChallenge.session(forDayKey: "2026-11-14")),
            ("daily-2026-11-02", DailyChallenge.session(forDayKey: "2026-11-02")),
            ("values", GameSession(values: [1, 9, 5, 5, 3, 7, 2, 8, 4, 6, 6, 4, 9, 1, 2, 2, 8, 8], seed: 5)),
        ]
        for (name, start) in starts {
            var session = start
            var steps: [[String: Any]] = []
            for step in 0..<220 where !session.isOver {
                var action: [String: Any] = [:]
                if step % 9 == 8, session.canUndo {
                    action = ["undo": session.undo()]
                } else if step % 7 == 6 {
                    if let hint = session.useHint() {
                        action = ["hint": [hint.pair.first, hint.pair.second], "cost": hint.cost]
                    } else {
                        action = ["hint": NSNull()]
                    }
                } else if step % 5 == 4 {
                    session.registerMiss()
                    action = ["miss": true]
                } else {
                    let pairs = MatchRules.validPairs(on: session.board)
                    if pairs.isEmpty {
                        action = ["plus": session.addNumbers()]
                    } else {
                        let pair = pairs[step % pairs.count]
                        let outcome = session.match(at: pair.first, pair.second)!
                        action = [
                            "match": [pair.first, pair.second], "gap": pair.gap, "route": pair.route.rawValue,
                            "points": outcome.points, "rows": outcome.clearedRows, "digits": outcome.clearedDigits,
                            "boardCleared": outcome.boardCleared, "reachedGoal": outcome.reachedGoal,
                        ]
                    }
                }
                session.addTime(3)
                steps.append([
                    "action": action, "score": session.score, "stage": session.stage, "plus": session.plusRemaining,
                    "hints": session.hintsUsed, "streak": session.streak, "over": session.isOver, "completed": session.isCompleted,
                    "canUndo": session.canUndo, "seconds": session.elapsedSeconds,
                    "values": session.board.cells.map(\.value), "cleared": session.board.cells.map(\.isCleared),
                    "added": session.board.cells.map(\.isAdded),
                    "stats": [session.stats.pairs, session.stats.rowsCleared, session.stats.plusUsed, session.stats.longestStreak],
                    "validPairs": MatchRules.validPairs(on: session.board).map { [$0.first, $0.second, $0.gap] },
                ])
            }
            games.append(["name": name, "goal": session.goal as Any, "steps": steps])
        }
        return ["games": games]
    }
}
