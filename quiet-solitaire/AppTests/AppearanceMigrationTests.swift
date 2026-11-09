import Foundation
import Testing
@testable import Quiet_Solitaire

/// Teal & Coral Light and Dark stopped being boards of their own (2026-11-08): a saved
/// choice becomes Teal & Coral with that mode in Settings › Appearance.
@Suite struct AppearanceMigrationTests {
    private func defaults(_ values: [String: String]) -> UserDefaults {
        let name = "AppearanceMigrationTests-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defaults.removePersistentDomain(forName: name)
        for (key, value) in values { defaults.set(value, forKey: key) }
        return defaults
    }

    @Test(arguments: [("tealCoralLight", AppearanceMode.light), ("tealCoralDark", .dark)])
    func pinnedTealCoralBecomesAMode(saved: String, mode: AppearanceMode) {
        let defaults = defaults([PreferenceKey.board: saved])
        PreferenceMigration.run(defaults)
        #expect(defaults.string(forKey: PreferenceKey.board) == BoardChoice.tealCoral.rawValue)
        #expect(defaults.string(forKey: PreferenceKey.appearanceMode) == mode.rawValue)
        // Teal & Coral is always available, like the boards it replaces.
        #expect(BoardChoice.tealCoral.unlockedBy == nil)
    }

    @Test(arguments: BoardChoice.allCases)
    func otherBoardsAreKept(board: BoardChoice) {
        let defaults = defaults([PreferenceKey.board: board.rawValue, PreferenceKey.appearanceMode: "dark"])
        PreferenceMigration.run(defaults)
        #expect(defaults.string(forKey: PreferenceKey.board) == board.rawValue)
        #expect(defaults.string(forKey: PreferenceKey.appearanceMode) == "dark")
    }

    @Test func nothingSavedStaysNothing() {
        let defaults = defaults([:])
        PreferenceMigration.run(defaults)
        #expect(defaults.string(forKey: PreferenceKey.board) == nil)
        #expect(defaults.string(forKey: PreferenceKey.appearanceMode) == nil)
    }

    /// Every board unlocked by an achievement still is: no achievement lost its reward.
    @Test func achievementBoardsStillUnlock() {
        #expect(BoardChoice.sageSand.unlockedBy == .noHints)
        #expect(BoardChoice.sageCoral.unlockedBy == .tenWins)
        #expect(BoardChoice.indigoPeach.unlockedBy == .consistency)
    }
}
