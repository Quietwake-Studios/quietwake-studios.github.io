import QuietCore
import QuietSudokuCore
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

/// The board: the family's palettes. Every one has light and dark (2026-11-08): the mode
/// follows the system or Settings › Appearance (`AppearanceMode`). Teal & Coral Light and Dark
/// used to be separate choices; `AppearanceMode.migrate` turns them into Teal & Coral + a mode.
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
        case .sageCoral: .nightOwl
        }
    }

    var note: LocalizedStringResource? {
        self == .highContrast ? "Always available for low vision." : nil
    }

    var palette: Palette { Palette(rawValue: rawValue)! }
}

/// The digits' typeface. System fonts only: nothing to license.
enum DigitStyle: String, Reward {
    case rounded, classic, mono, pen

    var title: LocalizedStringResource {
        switch self {
        case .rounded: "Rounded"
        case .classic: "Classic"
        case .mono: "Typewriter"
        case .pen: "Pen"
        }
    }

    var unlockedBy: Achievement? {
        switch self {
        case .rounded: nil
        case .classic: .noMistakes
        case .mono: .noNotes
        case .pen: .expert
        }
    }

    func font(size: CGFloat, weight: Font.Weight) -> Font {
        switch self {
        case .rounded: .system(size: size, weight: weight, design: .rounded)
        case .classic: .system(size: size, weight: weight, design: .serif)
        case .mono: .system(size: size, weight: weight == .regular ? .regular : .medium, design: .monospaced)
        case .pen: .custom("Noteworthy-Bold", fixedSize: size * 1.05)
        }
    }
}

/// Pencil marks in the muted ink, or in the accent.
enum NotesStyle: String, Reward {
    case plain, colored

    var title: LocalizedStringResource {
        switch self {
        case .plain: "Plain"
        case .colored: "In color"
        }
    }

    var unlockedBy: Achievement? { self == .colored ? .tenWins : nil }
}

/// What lights up around the selected cell.
enum HighlightStyle: String, Reward {
    /// Its row, column and box, and the same digit everywhere.
    case full
    /// Only the same digit.
    case soft

    var title: LocalizedStringResource {
        switch self {
        case .full: "Row, column and box"
        case .soft: "Same digit only"
        }
    }

    var unlockedBy: Achievement? { self == .soft ? .quick : nil }
}

/// Every win ends in fireworks; Wave first lights the board up from the last digit.
enum VictoryEffect: String, Reward {
    case fireworks, wave

    var title: LocalizedStringResource {
        switch self {
        case .fireworks: "Fireworks"
        case .wave: "Wave and fireworks"
        }
    }

    var unlockedBy: Achievement? { self == .wave ? .habit : nil }
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
    var digits: DigitStyle = .rounded
    var notes: NotesStyle = .plain
    var highlight: HighlightStyle = .full
    var victory: VictoryEffect = .fireworks
    /// Bigger digits and notes (always available, for low vision).
    var largeDigits = false
    /// Wrong digits turn red as soon as they're written (default), or only once the board is full.
    var showsMistakes = true
}

extension EnvironmentValues {
    @Entry var appearance = Appearance()
}

/// Storage keys for preferences (UserDefaults).
enum PreferenceKey {
    static let board = "board"
    static let digits = "digits"
    static let notes = "notes"
    static let highlight = "highlight"
    static let victory = "victory"
    static let appIcon = "appIcon"
    /// "Cheaper hints" (the "In the Red" reward, 2026-11-10): on when earned, the player can
    /// turn it off in the Collection.
    static let cheapHints = "cheapHints"
    static let soundEnabled = "soundEnabled"
    static let hapticsEnabled = "hapticsEnabled"
    static let showsMistakes = "showsMistakes"
    static let largeDigits = "largeDigits"
    /// Light or dark: follow the system (default) or fix one (2026-11-08). An option, not a
    /// reward: a reset keeps it.
    static let appearanceMode = "appearanceMode"
    /// The level of the last classic game, offered first next time.
    static let classicDifficulty = "classicDifficulty"
    /// The tutorial was played or skipped. Cleared by a reset, like a fresh install.
    static let tutorialSeen = "tutorialSeen"
    /// Set by a reset until the iCloud copy and achievements are cleared too.
    static let pendingCloudReset = "pendingCloudReset"

    /// Cosmetic choices, cleared by a reset.
    static let rewards = [board, digits, notes, highlight, victory, appIcon, cheapHints]
}

/// "Match system", or a fixed light or dark look (Settings › Appearance, 2026-11-08).
enum AppearanceMode: String, CaseIterable {
    case system, light, dark

    var colorScheme: ColorScheme? {
        switch self {
        case .system: nil
        case .light: .light
        case .dark: .dark
        }
    }

    /// Up to 1.0 (2), Teal & Coral Light and Dark were boards of their own: they become Teal &
    /// Coral with the mode fixed. Both were free, so nothing unlocked or synced (iCloud keeps
    /// achievements, not choices) changes. Runs once per launch; a no-op afterwards.
    static func migrate(_ defaults: UserDefaults = .standard) {
        let legacy: [String: AppearanceMode] = ["tealCoralLight": .light, "tealCoralDark": .dark]
        guard let board = defaults.string(forKey: PreferenceKey.board), let mode = legacy[board] else { return }
        defaults.set(BoardChoice.tealCoral.rawValue, forKey: PreferenceKey.board)
        if defaults.string(forKey: PreferenceKey.appearanceMode) == nil {
            defaults.set(mode.rawValue, forKey: PreferenceKey.appearanceMode)
        }
    }
}
