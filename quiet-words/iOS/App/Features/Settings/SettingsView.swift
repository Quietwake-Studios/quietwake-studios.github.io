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
            VStack(spacing: 18) {
                GroupSection("Play") {
                    Button { choosesLanguage = true } label: {
                        SettingsRow(title: Text("Word language"),
                                    detail: Text(verbatim: String(localized: app.wordLanguage.name).localizedCapitalized))
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("settingsLanguage")
                    Toggle("Sound", isOn: $sound)
                    Toggle("Haptics", isOn: $haptics)
                    Toggle(isOn: $largerLetters) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Larger letters")
                            Text("Bigger letters on the board.").font(.footnote).foregroundStyle(theme.muted)
                        }
                    }
                }
                .toggleStyle(ThemedToggleStyle())

                appearance

                GroupSection("Daily Challenge") {
                    DailyReminderToggle(reminders: app.reminders)
                        .padding(.vertical, 8)
                        .accessibilityIdentifier("dailyReminder")
                }

                GroupSection("Account") {
                    Button {
                        if app.gameCenter.isAuthenticated { app.gameCenter.showDashboard() } else { app.gameCenter.signIn() }
                    } label: {
                        SettingsRow(title: Text("Game Center"), detail: gameCenterStatus)
                    }
                    Button(action: onHowToPlay) { SettingsRow(title: Text("How to play")) }
                        .accessibilityIdentifier("howToPlay")
                }
                .buttonStyle(.plain)

                GroupSection("Privacy") {
                    Text("Quiet Words doesn't collect any data. Your games, streaks and achievements stay on this device. If you sign in to Game Center, your scores and achievements are shared with Apple's Game Center and your progress is saved in your iCloud.")
                        .font(.subheadline)
                        .foregroundStyle(theme.muted)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.vertical, 14)
                }

                VStack(alignment: .leading, spacing: 8) {
                    GroupSection {
                        Button { confirmsReset = true } label: {
                            Text("Reset Progress…")
                                .foregroundStyle(theme.accent)
                                .frame(maxWidth: .infinity, minHeight: 54, alignment: .leading)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("resetProgress")
                    }
                    Text("Deletes games, daily results, streaks, achievements and unlocked rewards, here and in iCloud.")
                        .font(.footnote)
                        .foregroundStyle(theme.muted)
                        .padding(.horizontal, 16)
                }

                GroupSection("About") {
                    RateAppRow(appName: "Quiet Words", review: app.storeReview)
                    HStack {
                        Text("Version").foregroundStyle(theme.ink)
                        Spacer()
                        Text(verbatim: Self.version).foregroundStyle(theme.muted)
                    }
                    .accessibilityElement(children: .combine)
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
            Button("Reset Progress", role: .destructive) { app.resetProgress() }
        } message: {
            Text("All your game progress is erased.")
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

    private var gameCenterStatus: Text {
        if let name = app.gameCenter.playerName { return Text("Signed in as \(name)") }
        return Text("Not signed in")
    }

    /// Only the version number ("1.0.1"), as in every app of the family.
    private static var version: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
    }
}

/// Title, optional detail under it, and a chevron (as in the other apps' Settings).
private struct SettingsRow: View {
    let title: Text
    var detail: Text?

    @Environment(\.quietTheme) private var theme

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                title.foregroundStyle(theme.ink)
                if let detail {
                    detail
                        .font(.subheadline)
                        .foregroundStyle(theme.muted)
                }
            }
            Spacer()
            Image(systemName: "chevron.forward")
                .font(.footnote.weight(.bold))
                .foregroundStyle(theme.muted)
        }
        .padding(.vertical, 10)
        .contentShape(Rectangle())
    }
}
