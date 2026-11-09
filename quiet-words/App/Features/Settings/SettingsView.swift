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
    @State private var confirmsReset = false

    var body: some View {
        @Bindable var app = app
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                GroupSection("Game") {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Word language").foregroundStyle(theme.ink)
                        Segments(selection: $app.wordLanguage, options: [(.en, "English words"), (.es, "Palabras en español")])
                            .accessibilityIdentifier("settingsLanguage")
                        Text("Words only; the app stays in your language. Each language keeps its own album.")
                            .font(.footnote)
                            .foregroundStyle(theme.muted)
                    }
                    .padding(.vertical, 6)
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
        .confirmationDialog("Reset all progress?", isPresented: $confirmsReset, titleVisibility: .visible) {
            Button("Reset progress", role: .destructive) { app.resetProgress() }
        } message: {
            Text("Games, the album, streaks and rewards are erased here and in iCloud. Sound and settings stay.")
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
