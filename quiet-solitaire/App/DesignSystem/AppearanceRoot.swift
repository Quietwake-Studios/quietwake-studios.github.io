import QuietUI
import SwiftUI

/// Reads the player's choices and publishes them as `\.quietTheme`, `\.appearance`, tint and
/// color scheme. A choice that isn't unlocked (e.g. restored on a new device) falls back to
/// the default.
struct AppearanceRoot: ViewModifier {
    @Environment(AppModel.self) private var app
    @AppStorage(PreferenceKey.board) private var board = BoardChoice.tealCoral
    @AppStorage(PreferenceKey.deck) private var deck = DeckStyle.quiet
    @AppStorage(PreferenceKey.back) private var back = CardBack.theme
    @AppStorage(PreferenceKey.victory) private var victory = VictoryEffect.fireworks
    @AppStorage(PreferenceKey.leftHanded) private var leftHanded = false
    @AppStorage(PreferenceKey.appearanceMode) private var mode = AppearanceMode.system

    func body(content: Content) -> some View {
        // Every theme has light and dark (2026-11-08): it follows the system, or the mode the
        // player fixed in Settings › Appearance.
        let theme = Theme(available(board, or: .tealCoral).palette, colorScheme: mode.colorScheme)
        content
            .environment(\.quietTheme, theme)
            .environment(\.appearance, Appearance(
                deck: available(deck, or: .quiet),
                back: available(back, or: .theme),
                victory: available(victory, or: .fireworks),
                leftHanded: leftHanded
            ))
            .tint(theme.primary)
            .preferredColorScheme(theme.colorScheme)
    }

    private func available<R: Reward>(_ choice: R, or fallback: R) -> R {
        choice.isUnlocked(in: app.progress) ? choice : fallback
    }
}
