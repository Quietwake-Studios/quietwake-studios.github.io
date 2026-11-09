import Foundation
import QuietWordsCore
import SwiftUI

/// UserDefaults keys. Progress and games live in SwiftData; these are choices.
enum PreferenceKey {
    /// The language of the words (not of the app). Unset until the tutorial's first step.
    static let wordLanguage = "wordLanguage"
    static let soundEnabled = "soundEnabled"
    static let hapticsEnabled = "hapticsEnabled"
    static let largerLetters = "largerLetters"
    static let tutorialSeen = "tutorialSeen"
    static let lastLevel = "lastLevel"
    static let lastTheme = "lastTheme"
    static let palette = "palette"
    /// Light or dark when the theme has both: follow the system (default) or fix one (2026-11-08).
    static let appearanceMode = "appearanceMode"
    static let marker = "marker"
    static let boardFont = "boardFont"
    static let appIcon = "appIcon"
    /// A reset made while signed out of Game Center: clear the cloud copy on the next sign-in.
    static let pendingCloudReset = "pendingCloudReset"

    /// Cosmetic choices, cleared by "Reset progress".
    static let rewards = [palette, marker, boardFont, appIcon]
}

/// "Match system", or a fixed light or dark look (only for themes with both modes).
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

extension WordLanguage {
    /// Preselected in the tutorial (never chosen for the player): the device's language.
    static var systemSuggestion: WordLanguage {
        Locale.preferredLanguages.first?.hasPrefix("es") == true ? .es : .en
    }

    /// "English words" / "Palabras en español": written in their own language on purpose.
    var wordsTitle: String {
        switch self {
        case .en: "English words"
        case .es: "Palabras en español"
        }
    }

    var code: String { rawValue.uppercased() }

    /// The language's name in the app's language, for VoiceOver and Settings.
    var name: LocalizedStringResource {
        switch self {
        case .en: "English"
        case .es: "Spanish"
        }
    }
}

extension Level {
    var title: LocalizedStringResource {
        switch self {
        case .gentle: "Gentle"
        case .medium: "Medium"
        case .hard: "Hard"
        }
    }

    /// For `Segments`, which takes keys.
    var titleKey: LocalizedStringKey {
        switch self {
        case .gentle: "Gentle"
        case .medium: "Medium"
        case .hard: "Hard"
        }
    }

    /// "11×11 · about 17 words · 4 directions".
    var detail: LocalizedStringResource {
        "\(side)×\(side) · about \(typicalWords) words · \(directions.count) directions"
    }
}

extension WordTheme {
    /// The theme's name in the app's language (the lists keep their own).
    var localizedName: String {
        let appLanguage: WordLanguage = Bundle.main.preferredLocalizations.first?.hasPrefix("es") == true ? .es : .en
        return name(appLanguage)
    }
}
