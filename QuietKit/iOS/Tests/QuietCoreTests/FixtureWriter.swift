import Foundation
import Testing
@testable import QuietCore

/// Writes the fixtures that the Kotlin port (`QuietKit/Android/quietkit-core`) must match:
/// FNV-1a, SplitMix64, bounded ints and day-key arithmetic. Does nothing unless
/// `QUIET_WRITE_FIXTURES=<dir>` is set, so normal test runs are unaffected.
///
///     QUIET_WRITE_FIXTURES=Android/fixtures swift test --filter FixtureWriter
@Suite struct FixtureWriter {
    @Test func writeFixtures() throws {
        guard let path = ProcessInfo.processInfo.environment["QUIET_WRITE_FIXTURES"], !path.isEmpty else { return }
        let dir = URL(fileURLWithPath: path, isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let data = try JSONSerialization.data(withJSONObject: core(), options: [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes])
        try data.write(to: dir.appendingPathComponent("core.json"))
    }

    private func hex(_ value: UInt64) -> String { String(format: "%016llx", value) }

    private func core() -> [String: Any] {
        let strings = ["", "a", "abc", "2026-11-13", "2026-11-13#es#0", "Ñandú", "ñ", "Pingüino", "Café", "Cafe\u{301}",
                       "N\u{303}", "León marino", "’", "🙂", "日本語", "round-2026-11-01-3", "words-ocean-es-2026-11-01"]
        let fnv = strings.map { ["input": $0, "hash": hex(FNV1a.hash($0))] }
        let seeds: [UInt64] = [0, 1, 7, 42, UInt64.max, 0x9E37_79B9_7F4A_7C15, FNV1a.hash("2026-11-13")]
        let ranges: [ClosedRange<Int>] = [0...0, 0...1, 1...9, 0...3, 0...99, -50...50, 0...999_999, 0...(1 << 40)]
        let random = seeds.map { seed -> [String: Any] in
            var rng = SeededRandom(seed: seed)
            let next = (0..<16).map { _ in hex(rng.next()) }
            let samples = ranges.map { range -> [String: Any] in
                var r = SeededRandom(seed: seed)
                return ["lower": range.lowerBound, "upper": range.upperBound, "values": (0..<32).map { _ in r.nextInt(in: range) }]
            }
            return ["seed": hex(seed), "next": next, "nextInt": samples]
        }
        var dayKeys: [[String: Any]] = []
        var day = "2026-10-20"
        for _ in 0..<500 {
            dayKeys.append(["key": day, "weekday": DailyKey.weekday(ofDayKey: day)!, "seed": hex(DailyKey.seed(forDayKey: day)),
                            "plus1": DailyKey.dayKey(day, addingDays: 1)!, "minus40": DailyKey.dayKey(day, addingDays: -40)!])
            day = DailyKey.dayKey(day, addingDays: 1)!
        }
        return ["fnv1a": fnv, "random": random, "dayKeys": dayKeys]
    }
}
