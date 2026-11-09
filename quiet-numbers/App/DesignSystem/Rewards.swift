import QuietCore
import QuietNumbersCore
import QuietUI
import SwiftUI

/// A cosmetic option in the Collection. Some are free; the rest unlock with an achievement.
protocol Reward: CaseIterable, Hashable, RawRepresentable<String> {
    var title: LocalizedStringResource { get }
    var unlockedBy: Achievement? { get }
}

extension Reward {
    func isUnlocked(in progress: PlayerProgress) -> Bool {
        unlockedBy.map(progress.unlocked.contains) ?? true
    }
}

enum ThemeChoice: String, Reward {
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

    /// The default and High contrast (accessibility) are never locked.
    var unlockedBy: Achievement? {
        switch self {
        case .tealCoral, .highContrast: nil
        case .sageSand: .noHelp
        case .indigoPeach: .consistency
        case .sageCoral: .nightOwl
        }
    }

    /// Shown instead of an unlock condition.
    var note: LocalizedStringResource? {
        self == .highContrast ? "Always available for low vision." : nil
    }

    /// The kit's palette behind this choice.
    var palette: Palette {
        switch self {
        case .tealCoral: .tealCoral
        case .sageSand: .sageSand
        case .indigoPeach: .indigoPeach
        case .sageCoral: .sageCoral
        case .highContrast: .highContrast
        }
    }

    /// Every theme has light and dark (2026-11-08): it follows the system, or the mode fixed
    /// in Settings › Appearance.
    func theme(_ mode: AppearanceMode) -> Theme {
        Theme(palette, colorScheme: mode.colorScheme)
    }

    /// Ids saved by older versions, mapped to the theme that took their place and, for the
    /// old pinned variants, the appearance mode they fixed.
    /// - Before the 2026 redesign: same unlock, except Ember, whose slot went to the
    ///   always-available High contrast.
    /// - Teal & Coral Light and Dark (2026-10-27 to 2026-11-08): Teal & Coral with the mode
    ///   fixed in Settings › Appearance. Both were always available, so no unlock is lost.
    private static let legacyIDs: [String: (theme: ThemeChoice, mode: AppearanceMode?)] = [
        "paper": (.tealCoral, nil), "slate": (.sageSand, nil), "sand": (.indigoPeach, nil),
        "midnight": (.sageCoral, nil), "ember": (.highContrast, nil),
        "tealCoralLight": (.tealCoral, .light), "tealCoralDark": (.tealCoral, .dark),
    ]

    static func migrateSavedChoice(in defaults: UserDefaults = .standard) {
        guard let saved = defaults.string(forKey: PreferenceKey.theme), let legacy = legacyIDs[saved] else { return }
        defaults.set(legacy.theme.rawValue, forKey: PreferenceKey.theme)
        if let mode = legacy.mode, defaults.string(forKey: PreferenceKey.appearanceMode) == nil {
            defaults.set(mode.rawValue, forKey: PreferenceKey.appearanceMode)
        }
    }
}

/// "Match system", or a fixed light or dark look for every theme (2026-11-08).
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

enum TileStyle: String, Reward {
    case plain, circle, outline

    var title: LocalizedStringResource {
        switch self {
        case .plain: "Plain"
        case .circle: "Circle"
        case .outline: "Outline"
        }
    }

    var unlockedBy: Achievement? {
        switch self {
        case .plain: nil
        case .circle: .cleanSlate
        case .outline: .stageFive
        }
    }
}

enum NumberFont: String, Reward {
    case rounded, serif

    var title: LocalizedStringResource {
        switch self {
        case .rounded: "SF Rounded"
        case .serif: "New York"
        }
    }

    var unlockedBy: Achievement? { self == .serif ? .habit : nil }

    var design: Font.Design { self == .serif ? .serif : .rounded }
}

enum ClearEffect: String, Reward {
    case outline, wave

    var title: LocalizedStringResource {
        switch self {
        case .outline: "Outline"
        case .wave: "Wave"
        }
    }

    var unlockedBy: Achievement? { self == .wave ? .sweeper : nil }
}

enum SoundPack: String, Reward {
    case soft, marimba

    var title: LocalizedStringResource {
        switch self {
        case .soft: "Soft"
        case .marimba: "Marimba"
        }
    }

    var unlockedBy: Achievement? { self == .marimba ? .monotone : nil }
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

/// The player's chosen cosmetics for the board, read by every screen. The colors are the
/// kit's `\.quietTheme`.
struct Appearance {
    var tileStyle: TileStyle = .plain
    var numberFont: NumberFont = .rounded
    var clearEffect: ClearEffect = .outline
    var largerNumbers = false
}

extension EnvironmentValues {
    @Entry var appearance = Appearance()
}

/// Storage keys for preferences (UserDefaults).
enum PreferenceKey {
    static let theme = "theme"
    /// Light or dark: follow the system (default) or fix one (Settings › Appearance, 2026-11-08).
    /// A setting, not a reward: a reset keeps it.
    static let appearanceMode = "appearanceMode"
    static let tileStyle = "tileStyle"
    static let numberFont = "numberFont"
    static let clearEffect = "clearEffect"
    static let soundPack = "soundPack"
    static let appIcon = "appIcon"
    static let soundEnabled = "soundEnabled"
    static let hapticsEnabled = "hapticsEnabled"
    static let largerNumbers = "largerNumbers"
    /// The tutorial was played or skipped. Cleared by a reset, like a fresh install.
    static let tutorialSeen = "tutorialSeen"
    /// Set by a reset until the iCloud copy and achievements are cleared too.
    static let pendingCloudReset = "pendingCloudReset"

    /// Cosmetic choices, cleared by a reset.
    static let rewards = [theme, tileStyle, numberFont, clearEffect, soundPack, appIcon]
}
