import Foundation
import Testing
@testable import Quiet_Sudoku

/// Teal & Coral Light and Dark stopped being boards of their own (2026-11-08): they become
/// Teal & Coral with the mode fixed in Settings › Appearance.
@Suite struct AppearanceMigrationTests {
    private func defaults(board: String?, mode: String? = nil) -> UserDefaults {
        let name = "AppearanceMigrationTests-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defaults.removePersistentDomain(forName: name)
        if let board { defaults.set(board, forKey: PreferenceKey.board) }
        if let mode { defaults.set(mode, forKey: PreferenceKey.appearanceMode) }
        return defaults
    }

    @Test(arguments: [("tealCoralLight", AppearanceMode.light), ("tealCoralDark", .dark)])
    func oldTealCoralModesBecomeTealCoralWithTheMode(board: String, mode: AppearanceMode) {
        let defaults = defaults(board: board)
        AppearanceMode.migrate(defaults)
        #expect(defaults.string(forKey: PreferenceKey.board) == BoardChoice.tealCoral.rawValue)
        #expect(defaults.string(forKey: PreferenceKey.appearanceMode) == mode.rawValue)
        // A second launch changes nothing.
        AppearanceMode.migrate(defaults)
        #expect(defaults.string(forKey: PreferenceKey.board) == BoardChoice.tealCoral.rawValue)
        #expect(defaults.string(forKey: PreferenceKey.appearanceMode) == mode.rawValue)
    }

    @Test func aModeAlreadyChosenWins() {
        let defaults = defaults(board: "tealCoralDark", mode: AppearanceMode.system.rawValue)
        AppearanceMode.migrate(defaults)
        #expect(defaults.string(forKey: PreferenceKey.board) == BoardChoice.tealCoral.rawValue)
        #expect(defaults.string(forKey: PreferenceKey.appearanceMode) == AppearanceMode.system.rawValue)
    }

    @Test(arguments: [nil] + BoardChoice.allCases.map(\.rawValue))
    func otherBoardsStayAsTheyAre(board: String?) {
        let defaults = defaults(board: board)
        AppearanceMode.migrate(defaults)
        #expect(defaults.string(forKey: PreferenceKey.board) == board)
        #expect(defaults.string(forKey: PreferenceKey.appearanceMode) == nil)
    }

    /// Every board is a real palette, and each one that an achievement unlocks still has it.
    @Test func everyThemeIsAPaletteAndKeepsItsAchievement() {
        #expect(BoardChoice.allCases.map(\.palette) == [.tealCoral, .sageSand, .indigoPeach, .sageCoral, .highContrast])
        #expect(BoardChoice.allCases.compactMap(\.unlockedBy) == [.noHints, .consistency, .nightOwl])
    }
}
