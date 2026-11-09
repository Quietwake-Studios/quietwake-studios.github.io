import QuietCore
import QuietUI
import QuietWordsCore
import SwiftUI

/// The letters of the board (a cosmetic reward): rounded by default, serif and mono earned.
enum BoardFont: String, CaseIterable, Codable {
    case rounded, serif, mono

    func font(size: CGFloat) -> Font {
        switch self {
        case .rounded: .system(size: size, weight: .semibold, design: .rounded)
        case .serif: .system(size: size, weight: .medium, design: .serif)
        case .mono: .system(size: size, weight: .medium, design: .monospaced)
        }
    }
}

/// What the board looks like, from the player's choices.
struct Appearance {
    var marker: Marker = .highlighter
    var boardFont: BoardFont = .rounded
    /// "Larger letters": the board doesn't follow Dynamic Type, it has this instead.
    var largerLetters = false
}

extension EnvironmentValues {
    @Entry var appearance = Appearance()
}

/// Reads the player's choices and publishes them as `\.quietTheme`, `\.appearance`, tint and
/// color scheme. A choice that isn't unlocked (e.g. restored on a new device) falls back to
/// the default.
struct AppearanceRoot: ViewModifier {
    @Environment(AppModel.self) private var app
    @AppStorage(PreferenceKey.palette) private var palette = Palette.tealCoral
    @AppStorage(PreferenceKey.marker) private var marker = Marker.highlighter
    @AppStorage(PreferenceKey.boardFont) private var boardFont = BoardFont.rounded
    @AppStorage(PreferenceKey.largerLetters) private var largerLetters = false

    func body(content: Content) -> some View {
        let theme = Theme(app.isUnlocked(palette) ? palette : .tealCoral)
        content
            .environment(\.quietTheme, theme)
            .environment(\.appearance, Appearance(
                marker: app.isUnlocked(marker) ? marker : .highlighter,
                boardFont: app.isUnlocked(boardFont) ? boardFont : .rounded,
                largerLetters: largerLetters
            ))
            .tint(theme.primary)
            .preferredColorScheme(theme.colorScheme)
    }
}
