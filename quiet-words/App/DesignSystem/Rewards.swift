import QuietCore
import QuietUI
import QuietWordsCore
import SwiftUI

/// A cosmetic earned by an achievement (or always available).
protocol Reward: CaseIterable, Hashable {
    /// Nil: always available.
    var achievement: Achievement? { get }
    var title: LocalizedStringResource { get }
    /// A second line on the reward's card (a theme's modes).
    var detail: LocalizedStringResource? { get }
}

extension Reward {
    var detail: LocalizedStringResource? { nil }

    func isUnlocked(in progress: PlayerProgress) -> Bool {
        achievement.map(progress.unlocked.contains) ?? true
    }
}

extension Marker: Reward {
    var achievement: Achievement? {
        switch self {
        case .highlighter: nil
        case .pencil: .firstPuzzle
        case .ink: .inPlainSight
        case .underline: .steadyHand
        }
    }

    var title: LocalizedStringResource {
        switch self {
        case .highlighter: "Highlighter"
        case .pencil: "Pencil"
        case .ink: "Ink"
        case .underline: "Underline"
        }
    }
}

extension BoardFont: Reward {
    var achievement: Achievement? {
        switch self {
        case .rounded: nil
        case .serif: .themeComplete
        case .mono: .words10000
        }
    }

    var title: LocalizedStringResource {
        switch self {
        case .rounded: "Rounded"
        case .serif: "Serif"
        case .mono: "Mono"
        }
    }
}

extension Palette: Reward {
    /// High contrast is accessibility: always available.
    var achievement: Achievement? {
        switch self {
        case .tealCoral, .highContrast: nil
        case .sageSand: .words100
        case .indigoPeach: .habit
        case .sageCoral: .consistency
        }
    }

    var title: LocalizedStringResource {
        switch self {
        case .tealCoral: "Teal & coral"
        case .sageSand: "Sage & sand"
        case .indigoPeach: "Indigo & peach"
        case .sageCoral: "Sage & coral"
        case .highContrast: "High contrast"
        }
    }

}

/// The Home screen icon.
enum AppIconChoice: String, CaseIterable, Reward {
    case standard, gold

    var achievement: Achievement? { self == .gold ? .words1000 : nil }
    var title: LocalizedStringResource { self == .gold ? "Gold" : "Standard" }
    /// `ASSETCATALOG_COMPILER_ALTERNATE_APPICON_NAMES` in project.yml.
    var iconName: String? { self == .gold ? "AppIcon-Gold" : nil }
}

extension Achievement {
    var gameCenterID: String {
        switch self {
        case .firstPuzzle: "qw_first_puzzle"
        case .words100: "qw_words_100"
        case .words1000: "qw_words_1000"
        case .words10000: "qw_words_10000"
        case .themeComplete: "qw_theme_complete"
        case .inPlainSight: "qw_hidden_early"
        case .habit: "qw_daily_7"
        case .consistency: "qw_daily_30"
        case .steadyHand: "qw_hard_fast"
        case .bilingual: "qw_bilingual"
        }
    }

    var title: LocalizedStringResource {
        switch self {
        case .firstPuzzle: "First Puzzle"
        case .words100: "A Hundred Words"
        case .words1000: "A Thousand Words"
        case .words10000: "Ten Thousand Words"
        case .themeComplete: "Theme Complete"
        case .inPlainSight: "In Plain Sight"
        case .habit: "Habit"
        case .consistency: "Consistency"
        case .steadyHand: "Steady Hand"
        case .bilingual: "Bilingual"
        }
    }

    var condition: LocalizedStringResource {
        switch self {
        case .firstPuzzle: "Finish your first puzzle."
        case .words100: "Find 100 words."
        case .words1000: "Find 1,000 words."
        case .words10000: "Find 10,000 words."
        case .themeComplete: "Find every word of a theme in the album."
        case .inPlainSight: "Guess the hidden word before the end, without hints."
        case .habit: "Complete 7 daily challenges in a row."
        case .consistency: "Complete 30 daily challenges in a row."
        case .steadyHand: "Finish a Hard puzzle without hints in under 10 minutes."
        case .bilingual: "Complete the day's challenge in both languages."
        }
    }

    var systemImage: String {
        switch self {
        case .firstPuzzle: "checkmark"
        case .words100: "textformat.123"
        case .words1000: "square.stack.3d.up"
        case .words10000: "book"
        case .themeComplete: "rosette"
        case .inPlainSight: "eye"
        case .habit: "flame"
        case .consistency: "calendar"
        case .steadyHand: "clock"
        case .bilingual: "globe"
        }
    }

    /// What it unlocks, for the summary and the collection.
    var reward: LocalizedStringResource? {
        switch self {
        case .firstPuzzle: "Pencil marker"
        case .words100: "Sage & sand theme"
        case .words1000: "Gold icon"
        case .words10000: "Mono letters"
        case .themeComplete: "Serif letters"
        case .inPlainSight: "Ink marker"
        case .habit: "Indigo & peach theme"
        case .consistency: "Sage & coral theme"
        case .steadyHand: "Underline marker"
        case .bilingual: nil
        }
    }
}
