import QuietUI
import QuietWordsCore
import SwiftUI

struct SettingsView: View {
    let onHowToPlay: () -> Void

    @Environment(AppModel.self) private var app
    @Environment(\.quietTheme) private var theme
    @Environment(\.horizontalSizeClass) private var sizeClass
    @AppStorage(PreferenceKey.soundEnabled) private var sound = true
    @AppStorage(PreferenceKey.hapticsEnabled) private var haptics = true
    @AppStorage(PreferenceKey.largerLetters) private var largerLetters = false
    @AppStorage(PreferenceKey.appearanceMode) private var mode = AppearanceMode.system
    @Environment(\.colorScheme) private var colorScheme
    @State private var confirmsReset = false
    @State private var choosesLanguage = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                GroupSection("Game") {
                    Button { choosesLanguage = true } label: {
                        row("Word language", value: Text(verbatim: String(localized: app.wordLanguage.name).localizedCapitalized))
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("settingsLanguage")
                    Toggle("Sound", isOn: $sound).toggleStyle(ThemedToggleStyle())
                    Toggle("Haptics", isOn: $haptics).toggleStyle(ThemedToggleStyle())
                    Toggle(isOn: $largerLetters) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Larger letters")
                            Text("Bigger letters on the board.").font(.footnote).foregroundStyle(theme.muted)
                        }
                    }
                    .toggleStyle(ThemedToggleStyle())
                    DailyReminderToggle(reminders: app.reminders)
                }
                appearance
                GroupSection("Account") {
                    Button {
                        app.gameCenter.isAuthenticated ? app.gameCenter.showDashboard() : app.gameCenter.signIn()
                    } label: {
                        row("Game Center", value: app.gameCenter.isAuthenticated ? Text("Signed in") : Text("Sign in"))
                    }
                    .buttonStyle(.plain)
                    Button(action: onHowToPlay) { row("How to play", value: nil) }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("howToPlay")
                }
                GroupSection("Privacy") {
                    Text("Quiet Words collects nothing. Your games, album and progress stay on this device; with Game Center they also sync through your Apple account.")
                        .font(.subheadline)
                        .foregroundStyle(theme.muted)
                        .padding(.vertical, 8)
                }
                GroupSection {
                    Button(role: .destructive) { confirmsReset = true } label: {
                        Text("Reset progress").foregroundStyle(theme.accent).frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                    }
                    .buttonStyle(.plain)
                    row("Version", value: Text(verbatim: version))
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .frame(maxWidth: Layout.column(sizeClass))
            .frame(maxWidth: .infinity)
        }
        .screen(Text("Settings"))
        #if DEBUG
        .onAppear { if ProcessInfo.processInfo.arguments.contains("-wordLanguageSheet") { choosesLanguage = true } }
        #endif
        .sheet(isPresented: $choosesLanguage) {
            WordLanguageSheet()
                .presentationDetents([.medium, .large])
                .presentationCornerRadius(32)
        }
        .confirmationDialog("Reset all progress?", isPresented: $confirmsReset, titleVisibility: .visible) {
            Button("Reset progress", role: .destructive) { app.resetProgress() }
        } message: {
            Text("Games, the album, streaks and rewards are erased here and in iCloud. Sound and settings stay.")
        }
    }

    // MARK: Appearance

    /// "Match system", and off: Light · Dark, starting on the opposite of what's on screen so
    /// the change shows right away. Every theme has both modes (2026-11-08).
    private var appearance: some View {
        GroupSection("Appearance") {
            Toggle("Match system", isOn: Binding(
                get: { mode == .system },
                set: { matches in
                    withAnimation(.quiet) {
                        mode = matches ? .system : (colorScheme == .dark ? .light : .dark)
                    }
                }
            ))
            .toggleStyle(ThemedToggleStyle())
            .accessibilityIdentifier("matchSystem")
            if mode != .system {
                Segments(selection: $mode, options: [(.light, "Light"), (.dark, "Dark")])
                    .padding(.vertical, 6)
                    .accessibilityElement(children: .contain)
                    .accessibilityIdentifier("appearanceMode")
                    .accessibilityValue(Text(verbatim: mode.rawValue))
            }
        }
    }

    private func row(_ label: LocalizedStringKey, value: Text?) -> some View {
        HStack {
            Text(label).foregroundStyle(theme.ink)
            Spacer()
            if let value { value.foregroundStyle(theme.muted) }
            Image(systemName: "chevron.forward").font(.footnote.weight(.semibold)).foregroundStyle(theme.muted)
        }
        .frame(minHeight: 44)
        .contentShape(Rectangle())
    }

    private var version: String {
        let info = Bundle.main.infoDictionary
        return "\(info?["CFBundleShortVersionString"] as? String ?? "") (\(info?["CFBundleVersion"] as? String ?? ""))"
    }
}
