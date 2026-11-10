import QuietCore
import QuietNumbersCore
import QuietUI
import SwiftUI

/// Achievements and the cosmetics they unlock. Locked items show how to get them.
struct CollectionView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.quietTheme) private var colors
    @Environment(\.horizontalSizeClass) private var sizeClass
    @AppStorage(PreferenceKey.theme) private var theme = ThemeChoice.tealCoral
    @AppStorage(PreferenceKey.tileStyle) private var tileStyle = TileStyle.plain
    @AppStorage(PreferenceKey.numberFont) private var numberFont = NumberFont.rounded
    @AppStorage(PreferenceKey.clearEffect) private var clearEffect = ClearEffect.outline
    @AppStorage(PreferenceKey.soundPack) private var soundPack = SoundPack.soft
    @AppStorage(PreferenceKey.appIcon) private var appIcon = AppIconChoice.standard
    @AppStorage(PreferenceKey.cheapHints) private var cheapHints = true

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                // "In the Red" (hidden): the only reward that touches play, so it can be turned
                // off (2026-11-10). It doesn't show until earned.
                if app.progress.unlocked.contains(.inTheRed) {
                    GroupSection("Hints") {
                        Toggle(isOn: $cheapHints) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Cheaper hints")
                                    .foregroundStyle(colors.ink)
                                Text("A hint costs 3 points instead of 10, except in daily challenges, which always cost 10. From your next game.")
                                    .font(.footnote)
                                    .foregroundStyle(colors.muted)
                            }
                        }
                        .toggleStyle(ThemedToggleStyle())
                        .padding(.vertical, 8)
                        .accessibilityIdentifier("cheapHints")
                    }
                }
                RewardSection(title: "Theme", selection: $theme, progress: app.progress) { choice in
                    ThemePreview(palette: choice.palette)
                }
                RewardSection(title: "Tiles", selection: $tileStyle, progress: app.progress) { style in
                    TilePreview(style: style)
                }
                RewardSection(title: "Numbers", selection: $numberFont, progress: app.progress) { font in
                    Text(verbatim: "12")
                        .font(.system(size: 20, weight: .bold, design: font.design))
                        .foregroundStyle(colors.ink)
                        .frame(width: 36, alignment: .leading)
                }
                RewardSection(title: "Row clear", selection: $clearEffect, progress: app.progress)
                RewardSection(title: "Sound", selection: $soundPack, progress: app.progress)
                RewardSection(title: "App icon", selection: $appIcon, progress: app.progress)
                    .onChange(of: appIcon) { setAppIcon(appIcon) }
                GroupSection("Achievements") {
                    ForEach(Achievement.allCases, id: \.self) { achievement in
                        AchievementRow(achievement: achievement, progress: app.progress.progress(of: achievement))
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .frame(maxWidth: Layout.column(sizeClass))
            .frame(maxWidth: .infinity)
        }
        .screen(Text("Collection"))
    }

    private func setAppIcon(_ choice: AppIconChoice) {
        guard UIApplication.shared.supportsAlternateIcons,
              UIApplication.shared.alternateIconName != choice.iconName
        else { return }
        UIApplication.shared.setAlternateIconName(choice.iconName)
    }
}

private struct RewardSection<R: Reward, Preview: View>: View {
    let title: LocalizedStringKey
    @Binding var selection: R
    let progress: PlayerProgress
    @ViewBuilder let preview: (R) -> Preview

    @Environment(\.quietTheme) private var theme

    var body: some View {
        GroupSection(title) {
            ForEach(Array(R.allCases), id: \.self) { item in
                let isUnlocked = item.isUnlocked(in: progress)
                Button {
                    selection = item
                } label: {
                    HStack(spacing: 14) {
                        preview(item)
                            .opacity(isUnlocked ? 1 : 0.5)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.title)
                                .foregroundStyle(isUnlocked ? theme.ink : theme.muted)
                            if let detail = detail(for: item, isUnlocked: isUnlocked) {
                                Text(detail)
                                    .font(.subheadline)
                                    .foregroundStyle(theme.muted)
                            }
                        }
                        Spacer(minLength: 8)
                        if !isUnlocked {
                            Image(systemName: "lock")
                                .foregroundStyle(theme.muted)
                        } else if item == selection {
                            Image(systemName: "checkmark")
                                .fontWeight(.bold)
                                .foregroundStyle(theme.primary)
                        }
                    }
                    .padding(.vertical, 10)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(!isUnlocked)
                .accessibilityLabel(Text(item.title))
                .accessibilityValue(detail(for: item, isUnlocked: isUnlocked).map { Text($0) } ?? Text(verbatim: ""))
                .accessibilityAddTraits(item == selection ? .isSelected : [])
            }
        }
    }

    private func detail(for item: R, isUnlocked: Bool) -> LocalizedStringResource? {
        if let theme = item as? ThemeChoice, let note = theme.note { return note }
        guard !isUnlocked, let achievement = item.unlockedBy else { return nil }
        return achievement.condition
    }
}

extension RewardSection where Preview == EmptyView {
    init(title: LocalizedStringKey, selection: Binding<R>, progress: PlayerProgress) {
        self.init(title: title, selection: selection, progress: progress) { _ in EmptyView() }
    }
}

/// A theme in both modes, side by side: background with its primary and accent
/// (2026-11-08, every theme has light and dark; the mode is chosen in Settings › Appearance).
private struct ThemePreview: View {
    let palette: Palette

    @Environment(\.quietTheme) private var theme

    var body: some View {
        HStack(spacing: 0) {
            half(\.light)
            half(\.dark)
        }
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .strokeBorder(theme.separator, lineWidth: 1)
        }
        .accessibilityHidden(true)
    }

    private func half(_ mode: KeyPath<(light: String, dark: String), String>) -> some View {
        let color = { (role: Palette.Role) in Color(hex: palette.hex(role)[keyPath: mode]) }
        return HStack(spacing: 3) {
            Circle().fill(color(.primary)).frame(width: 12, height: 12)
            Circle().fill(color(.accent)).frame(width: 12, height: 12)
        }
        .frame(width: 38, height: 30)
        .background(color(.background))
    }
}

extension Color {
    /// `#RRGGBB` (palette previews).
    init(hex: String) {
        let (r, g, b) = Contrast.components(hex)
        self.init(red: r, green: g, blue: b)
    }
}

/// A "7" drawn with a tile style.
private struct TilePreview: View {
    let style: TileStyle

    @Environment(\.quietTheme) private var theme

    var body: some View {
        Text(verbatim: "7")
            .font(.system(size: 19, weight: .semibold, design: .rounded))
            .foregroundStyle(theme.ink)
            .frame(width: 34, height: 34)
            .background {
                switch style {
                case .plain: EmptyView()
                case .circle: Circle().fill(theme.surface2)
                case .outline: Circle().strokeBorder(theme.muted, lineWidth: 1.5)
                }
            }
            .accessibilityHidden(true)
    }
}

private struct AchievementRow: View {
    let achievement: Achievement
    let progress: Double

    @Environment(\.quietTheme) private var theme

    var body: some View {
        // A hidden achievement keeps its secret until earned: "?" instead of the badge.
        let secret = achievement.isHidden && progress < 1
        HStack(spacing: 14) {
            Group {
                if secret {
                    Image(systemName: "questionmark")
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(theme.muted)
                        .frame(width: 44, height: 44)
                        .background(theme.surface2, in: Circle())
                } else {
                    // Locked badges are shown faded and without color.
                    achievement.badge
                        .resizable()
                        .frame(width: 44, height: 44)
                        .saturation(progress >= 1 ? 1 : 0)
                        .opacity(progress >= 1 ? 1 : 0.4)
                }
            }
            .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(secret ? "Hidden achievement" : achievement.title)
                    .font(.headline)
                    .foregroundStyle(theme.ink)
                Text(secret ? "Keep playing: it shows here once earned." : achievement.condition)
                    .font(.subheadline)
                    .foregroundStyle(theme.muted)
                if progress > 0, progress < 1, achievement.target > 1 {
                    ProgressView(value: progress)
                        .tint(theme.primary)
                }
            }
            Spacer(minLength: 8)
            if progress >= 1 {
                Image(systemName: "checkmark.seal.fill")
                    .foregroundStyle(theme.primary)
            }
        }
        .padding(.vertical, 10)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("achievement-\(achievement.rawValue)")
        .accessibilityValue(progress >= 1 ? Text("Unlocked") : Text(progress, format: .percent.precision(.fractionLength(0))))
    }
}
