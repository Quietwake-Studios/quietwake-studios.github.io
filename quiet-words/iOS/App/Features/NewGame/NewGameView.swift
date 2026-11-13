import QuietUI
import QuietWordsCore
import SwiftUI

/// Free play: the level, the EN · ES pill beside it (remembered, never asked before each
/// game), and the themes with their album progress. Every theme is open from the start.
struct NewGameView: View {
    let onPlay: (_ themeID: String?, _ level: Level) -> Void

    @Environment(AppModel.self) private var app
    @Environment(\.quietTheme) private var theme
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var level = Level.gentle
    /// nil = Surprise.
    @State private var selection: String?
    @State private var didAppear = false

    var body: some View {
        @Bindable var app = app
        let themes = app.library.themes(for: app.wordLanguage)
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: 10) {
                    HStack(spacing: 8) {
                        Segments(selection: $level, options: Level.allCases.map { ($0, $0.titleKey) })
                        LanguagePill(language: $app.wordLanguage)
                    }
                    Text("\(Text(level.detail)) · \(app.wordLanguage.wordsTitle)")
                        .font(.footnote)
                        .foregroundStyle(theme.muted)
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 10)], spacing: 10) {
                        ThemeCard(symbol: "shuffle", name: Text("Surprise"), detail: Text("Any theme"), progress: nil,
                                  isSelected: selection == nil) { selection = nil }
                            .accessibilityIdentifier("theme-surprise")
                        ForEach(themes) { item in
                            let count = app.profile.album.count(item, app.wordLanguage)
                            ThemeCard(symbol: item.symbol, name: Text(verbatim: item.localizedName),
                                      detail: Text(verbatim: "\(count.found)/\(count.total)"), progress: count,
                                      isSelected: selection == item.id) { selection = item.id }
                                .accessibilityIdentifier("theme-\(item.id)")
                        }
                    }
                    .padding(.top, 4)
                }
                .padding(.horizontal, 20)
                .padding(.top, 10)
                .padding(.bottom, 20)
                .frame(maxWidth: Layout.column(sizeClass))
                .frame(maxWidth: .infinity)
            }
            Button {
                onPlay(selection, level)
            } label: {
                if app.isPreparing {
                    ProgressView().tint(theme.onPrimary)
                } else if let id = selection, let item = app.library.theme(id) {
                    Text("Play \(item.localizedName)")
                } else {
                    Text("Play a surprise")
                }
            }
            .buttonStyle(CapsuleButtonStyle())
            .disabled(app.isPreparing)
            .frame(maxWidth: Layout.buttons(sizeClass))
            .padding(.horizontal, 20)
            .padding(.bottom, 12)
            .accessibilityIdentifier("play")
        }
        .screen(Text("New Game"))
        .onAppear {
            guard !didAppear else { return }
            didAppear = true
            level = app.lastLevel
            selection = app.lastThemeID.flatMap { app.library.theme($0)?.id }
        }
    }
}

/// A theme: its symbol, name and album progress in the chosen language; the badge (accent)
/// once complete.
struct ThemeCard: View {
    let symbol: String
    let name: Text
    let detail: Text
    let progress: (found: Int, total: Int)?
    let isSelected: Bool
    let action: () -> Void

    @Environment(\.quietTheme) private var theme

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 10) {
                    Image(systemName: symbol)
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(theme.primary)
                        .frame(width: 24)
                    name.font(.headline).foregroundStyle(theme.ink).lineLimit(1).minimumScaleFactor(0.8)
                    Spacer(minLength: 0)
                }
                HStack {
                    detail.font(.footnote.monospacedDigit()).foregroundStyle(theme.muted)
                    Spacer()
                    if isComplete {
                        Label("Badge", systemImage: "rosette")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(theme.accent)
                    }
                }
                if let progress, progress.total > 0 {
                    Capsule().fill(theme.surface2)
                        .frame(height: 4)
                        .overlay(alignment: .leading) {
                            GeometryReader { proxy in
                                Capsule().fill(isComplete ? theme.accent : theme.primary)
                                    .frame(width: proxy.size.width * CGFloat(progress.found) / CGFloat(progress.total))
                            }
                        }
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background { SurfaceShape(shape: RoundedRectangle(cornerRadius: 20, style: .continuous)) }
            .overlay {
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .strokeBorder(isSelected ? theme.primary : .clear, lineWidth: 2.5)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }

    private var isComplete: Bool { progress.map { $0.total > 0 && $0.found == $0.total } ?? false }
}
