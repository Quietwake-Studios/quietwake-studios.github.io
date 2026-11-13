import QuietCore
import QuietSolitaireCore
import QuietUI
import SwiftUI

enum CollectionTab: Hashable {
    case rewards, achievements
}

/// Rewards (the cosmetics, locked ones show how to get them) and achievements (how each one is
/// earned, with progress), in two tabs like Quiet Words (2026-11-13).
struct CollectionView: View {
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var tab = CollectionTab.rewards

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                Segments(selection: $tab, options: [(.rewards, "Rewards"), (.achievements, "Achievements")])
                    .accessibilityIdentifier("collectionTabs")
                switch tab {
                case .rewards: RewardsList()
                case .achievements: AchievementsList()
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .frame(maxWidth: Layout.column(sizeClass))
            .frame(maxWidth: .infinity)
        }
        .screen(Text("Collection"))
    }
}

/// Every cosmetic by category; "Cheaper hints" at the end once "In the Red" is earned.
private struct RewardsList: View {
    @Environment(AppModel.self) private var app
    @Environment(\.quietTheme) private var theme
    @AppStorage(PreferenceKey.board) private var board = BoardChoice.tealCoral
    @AppStorage(PreferenceKey.deck) private var deck = DeckStyle.quiet
    @AppStorage(PreferenceKey.back) private var back = CardBack.theme
    @AppStorage(PreferenceKey.victory) private var victory = VictoryEffect.fireworks
    @AppStorage(PreferenceKey.appIcon) private var appIcon = AppIconChoice.standard
    @AppStorage(PreferenceKey.cheapHints) private var cheapHints = true

    var body: some View {
        VStack(spacing: 18) {
            RewardSection(title: "Deck", selection: $deck, progress: app.progress) { style in
                CardFace(card: Card(1, .hearts), deck: style, width: 30)
            }
            BackPicker(selection: $back, progress: app.progress)
            RewardSection(title: "Board", selection: $board, progress: app.progress) { choice in
                ThemePreview(palette: choice.palette)
            }
            RewardSection(title: "Victory", selection: $victory, progress: app.progress)
            RewardSection(title: "App icon", selection: $appIcon, progress: app.progress)
                .onChange(of: appIcon) { setAppIcon(appIcon) }
            // "In the Red" (hidden): the only reward that touches play, so it can be turned
            // off (2026-11-10). It doesn't show until earned.
            if app.progress.unlocked.contains(.inTheRed) {
                GroupSection("Hints") {
                    Toggle(isOn: $cheapHints) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Cheaper hints").foregroundStyle(theme.ink)
                            Text("Classic: a hint costs 3 points instead of 10. Daily challenges always cost 10. From your next game.")
                                .font(.subheadline)
                                .foregroundStyle(theme.muted)
                        }
                    }
                    .toggleStyle(ThemedToggleStyle())
                    .padding(.vertical, 8)
                    .accessibilityIdentifier("cheapHints")
                }
            }
        }
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
                            .opacity(isUnlocked ? 1 : 0.4)
                            .accessibilityHidden(true)
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
        if let note = item.note { return note }
        guard !isUnlocked, let achievement = item.unlockedBy else { return nil }
        return achievement.condition
    }
}

extension RewardSection where Preview == EmptyView {
    init(title: LocalizedStringKey, selection: Binding<R>, progress: PlayerProgress) {
        self.init(title: title, selection: selection, progress: progress) { _ in EmptyView() }
    }
}

/// Card backs side by side; the chosen one ringed, locked ones faded.
private struct BackPicker: View {
    @Binding var selection: CardBack
    let progress: PlayerProgress

    @Environment(\.quietTheme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Card back")
                .font(.subheadline.bold())
                .foregroundStyle(theme.muted)
                .padding(.horizontal, 16)
                .accessibilityAddTraits(.isHeader)
            HStack(alignment: .top, spacing: 8) {
                ForEach(CardBack.allCases, id: \.self) { back in
                    let isUnlocked = back.isUnlocked(in: progress)
                    Button {
                        selection = back
                    } label: {
                        VStack(spacing: 8) {
                            CardBackView(back: back, width: 44)
                                .overlay {
                                    if back == selection {
                                        RoundedRectangle(cornerRadius: 7, style: .continuous)
                                            .strokeBorder(theme.primary, lineWidth: 2)
                                            .padding(-4)
                                    }
                                }
                                .overlay(alignment: .bottomTrailing) {
                                    if !isUnlocked {
                                        Image(systemName: "lock.fill")
                                            .font(.caption2)
                                            .foregroundStyle(theme.muted)
                                            .padding(4)
                                            .background(theme.surface, in: Circle())
                                            .offset(x: 6, y: 6)
                                    }
                                }
                                .opacity(isUnlocked ? 1 : 0.45)
                            Text(back.title)
                                .font(.caption.weight(.bold))
                                .foregroundStyle(isUnlocked ? theme.ink : theme.muted)
                                .lineLimit(1)
                                .minimumScaleFactor(0.8)
                        }
                        .frame(maxWidth: .infinity)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .disabled(!isUnlocked)
                    .accessibilityLabel(Text(back.title))
                    .accessibilityValue(isUnlocked ? Text(verbatim: "") : Text(back.unlockedBy?.condition ?? ""))
                    .accessibilityAddTraits(back == selection ? .isSelected : [])
                }
            }
            .padding(16)
            .background { SurfaceShape(shape: RoundedRectangle(cornerRadius: 22, style: .continuous)) }
        }
    }
}

/// A theme in both modes, side by side: its background with primary and accent dots
/// (2026-11-08, every theme has light and dark).
private struct ThemePreview: View {
    let palette: Palette

    var body: some View {
        HStack(spacing: 0) {
            half(\.light)
            half(\.dark)
        }
        .frame(width: 64, height: 30)
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .strokeBorder(Color.hex(palette.hex(.ghost).light).opacity(0.6), lineWidth: 1)
        }
    }

    private func half(_ mode: KeyPath<(light: String, dark: String), String>) -> some View {
        let color = { (role: Palette.Role) in Color.hex(palette.hex(role)[keyPath: mode]) }
        return HStack(spacing: 3) {
            Circle().fill(color(.primary)).frame(width: 11, height: 11)
            Circle().fill(color(.accent)).frame(width: 11, height: 11)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(color(.background))
    }
}

/// Every achievement: how it's earned, progress when it counts something, whether it's earned
/// and what it unlocks. A hidden one keeps its condition until earned (like Quiet Words).
private struct AchievementsList: View {
    @Environment(AppModel.self) private var app
    @Environment(\.quietTheme) private var theme

    var body: some View {
        let progress = app.progress
        let earned = Achievement.allCases.filter(progress.unlocked.contains).count
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                Text("\(earned) earned").font(.system(.title2, design: .rounded, weight: .heavy)).foregroundStyle(theme.ink)
                Spacer()
                Text("of \(Achievement.allCases.count)").font(.subheadline).foregroundStyle(theme.muted)
            }
            .padding(.horizontal, 4)
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("achievementsHeader")
            GroupSection {
                ForEach(Achievement.allCases, id: \.self) { achievement in
                    AchievementRow(achievement: achievement, isEarned: progress.unlocked.contains(achievement),
                                   step: step(achievement, progress))
                }
            }
        }
    }

    /// Progress for the ones that count something (nil: one-off).
    private func step(_ achievement: Achievement, _ progress: PlayerProgress) -> AchievementRow.Step? {
        let target = achievement.target
        return switch achievement {
        case .consistency, .habit:
            AchievementRow.Step(progress.bestStreak, of: target, Text("Best streak: \(min(progress.bestStreak, target)) of \(target) days"))
        case .tenWins, .fiftyWins, .centennial:
            AchievementRow.Step(progress.gamesWon, of: target, Text("\(min(progress.gamesWon, target)) of \(target) wins"))
        default:
            nil
        }
    }
}

private struct AchievementRow: View {
    /// How far a counted achievement has come, and how to say it.
    struct Step {
        let fraction: Double
        let text: Text

        init(_ value: Int, of target: Int, _ text: Text) {
            fraction = min(Double(value) / Double(max(target, 1)), 1)
            self.text = text
        }
    }

    let achievement: Achievement
    let isEarned: Bool
    let step: Step?

    @Environment(\.quietTheme) private var theme

    var body: some View {
        // A hidden achievement ("In the Red") says nothing until earned.
        let secret = achievement.isHidden && !isEarned
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: secret ? "questionmark" : achievement.systemImage)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(isEarned ? theme.onPrimary : theme.muted)
                .frame(width: 42, height: 42)
                .background(isEarned ? theme.primary : theme.surface2, in: Circle())
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(secret ? "Hidden achievement" : achievement.title)
                    .font(.headline)
                    .foregroundStyle(theme.ink)
                Text(secret ? "Keep playing: it shows here once earned." : achievement.condition)
                    .font(.subheadline)
                    .foregroundStyle(theme.muted)
                if let step, !isEarned {
                    ProgressView(value: step.fraction).tint(theme.primary)
                    step.text.font(.caption.monospacedDigit()).foregroundStyle(theme.muted)
                }
                if !secret, let reward = achievement.reward {
                    Label { Text(reward) } icon: { Image(systemName: "gift") }
                        .font(.caption)
                        .foregroundStyle(isEarned ? theme.primary : theme.muted)
                }
            }
            Spacer(minLength: 8)
            if isEarned {
                Image(systemName: "checkmark.seal.fill")
                    .font(.title3)
                    .foregroundStyle(theme.primary)
                    .accessibilityLabel(Text("Earned"))
            }
        }
        .padding(.vertical, 12)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("achievement-\(achievement.rawValue)")
    }
}
