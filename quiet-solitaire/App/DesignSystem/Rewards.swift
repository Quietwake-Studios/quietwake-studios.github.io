import QuietCore
import QuietSolitaireCore
import QuietUI
import SwiftUI

/// A cosmetic option in the Collection. Some are free; the rest unlock with an achievement.
protocol Reward: CaseIterable, Hashable, RawRepresentable<String> {
    var title: LocalizedStringResource { get }
    var unlockedBy: Achievement? { get }
    /// Shown instead of an unlock condition (always-available accessibility options).
    var note: LocalizedStringResource? { get }
}

extension Reward {
    var note: LocalizedStringResource? { nil }

    func isUnlocked(in progress: PlayerProgress) -> Bool {
        unlockedBy.map(progress.unlocked.contains) ?? true
    }
}

/// The table: the family's palettes. Every one has light and dark (2026-11-08); the mode
/// follows the system or is fixed in Settings › Appearance (`AppearanceMode`).
enum BoardChoice: String, Reward {
    case tealCoral, sageSand, indigoPeach, sageCoral, highContrast

    var title: LocalizedStringResource {
        switch self {
        case .tealCoral: "Teal & Coral"
        case .sageSand: "Sage & Sand"
        case .indigoPeach: "Indigo & Peach"
        case .sageCoral: "Sage & Coral"
        case .highContrast: "High contrast"
        }
    }

    var unlockedBy: Achievement? {
        switch self {
        case .tealCoral, .highContrast: nil
        case .sageSand: .noHints
        case .indigoPeach: .consistency
        case .sageCoral: .tenWins
        }
    }

    var note: LocalizedStringResource? {
        self == .highContrast ? "Always available for low vision." : nil
    }

    var palette: Palette { Palette(rawValue: rawValue)! }
}

/// Card faces.
enum DeckStyle: String, Reward {
    case quiet, illustrated, classic, pixel, fourColor, largeIndex

    var title: LocalizedStringResource {
        switch self {
        case .quiet: "Quiet"
        case .illustrated: "Illustrated"
        case .classic: "Classic"
        case .pixel: "Pixel"
        case .fourColor: "Four colors"
        case .largeIndex: "Large index"
        }
    }

    var unlockedBy: Achievement? {
        switch self {
        case .quiet, .fourColor, .largeIndex: nil
        case .illustrated: .habit
        case .classic: .onePass
        case .pixel: .economy
        }
    }

    var note: LocalizedStringResource? {
        switch self {
        case .fourColor: "Always available to tell suits apart."
        case .largeIndex: "Always available for low vision."
        default: nil
        }
    }
}

enum CardBack: String, Reward {
    case theme, coral, linen, night, gold

    var title: LocalizedStringResource {
        switch self {
        case .theme: "Theme"
        case .coral: "Coral"
        case .linen: "Linen"
        case .night: "Night"
        case .gold: "Gold"
        }
    }

    var unlockedBy: Achievement? {
        switch self {
        case .theme: nil
        case .coral: .noUndo
        case .linen: .ladder
        case .night: .nightOwl
        case .gold: .centennial
        }
    }
}

/// Every win ends in fireworks; Cascade first sends the cards tumbling off the table.
enum VictoryEffect: String, Reward {
    case fireworks, cascade

    var title: LocalizedStringResource {
        switch self {
        case .fireworks: "Fireworks"
        case .cascade: "Cascade and fireworks"
        }
    }

    var unlockedBy: Achievement? { self == .cascade ? .fiftyWins : nil }
}

enum AppIconChoice: String, Reward {
    case standard, gold

    var title: LocalizedStringResource {
        switch self {
        case .standard: "Standard"
        case .gold: "Gold"
        }
    }

    var unlockedBy: Achievement? { self == .gold ? .centennial : nil }

    /// Name for `setAlternateIconName`; `nil` is the primary icon.
    var iconName: String? { self == .gold ? "AppIcon-Gold" : nil }
}

/// The player's chosen cosmetics and options, read by the board.
struct Appearance {
    var deck: DeckStyle = .quiet
    var back: CardBack = .theme
    var victory: VictoryEffect = .fireworks
    var leftHanded = false
}

extension EnvironmentValues {
    @Entry var appearance = Appearance()
}

/// Storage keys for preferences (UserDefaults).
enum PreferenceKey {
    static let board = "board"
    /// Light or dark: follow the system (default) or fix one (2026-11-08). A setting, not a
    /// reward: a reset keeps it.
    static let appearanceMode = "appearanceMode"
    static let deck = "deck"
    static let back = "back"
    static let victory = "victory"
    static let appIcon = "appIcon"
    static let soundEnabled = "soundEnabled"
    static let hapticsEnabled = "hapticsEnabled"
    static let tapToMove = "tapToMove"
    static let winnableOnly = "winnableOnly"
    static let leftHanded = "leftHanded"
    /// The tutorial was played or skipped. Cleared by a reset, like a fresh install.
    static let tutorialSeen = "tutorialSeen"
    /// Set by a reset until the iCloud copy and achievements are cleared too.
    static let pendingCloudReset = "pendingCloudReset"

    /// Cosmetic choices, cleared by a reset.
    static let rewards = [board, deck, back, victory, appIcon]
}

/// "Match system", or a fixed light or dark look. Every theme has both (2026-11-08).
enum AppearanceMode: String, CaseIterable {
    case system, light, dark

    var colorScheme: ColorScheme? {
        switch self {
        case .system: nil
        case .light: .light
        case .dark: .dark
        }
    }
}

/// Saved choices from older versions, updated once at launch.
enum PreferenceMigration {
    /// Until 2026-11-08 Teal & Coral Light and Dark were boards of their own: Teal & Coral pinned to
    /// one mode (always available, no achievement). They become Teal & Coral with that mode in
    /// Settings › Appearance (2026-11-08). The board is a local preference; nothing of it
    /// travels in the iCloud snapshot (achievements and dailies only).
    static func run(_ defaults: UserDefaults = .standard) {
        let pinned: [String: AppearanceMode] = ["tealCoralLight": .light, "tealCoralDark": .dark]
        guard let board = defaults.string(forKey: PreferenceKey.board), let mode = pinned[board] else { return }
        defaults.set(BoardChoice.tealCoral.rawValue, forKey: PreferenceKey.board)
        defaults.set(mode.rawValue, forKey: PreferenceKey.appearanceMode)
    }
}
