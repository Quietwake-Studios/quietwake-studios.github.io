import QuietCore
import QuietUI
import QuietWordsCore
import SwiftUI

/// The album (every word found, per theme and language) and the rewards.
struct CollectionView: View {
    let startsOnAlbum: Bool

    @Environment(AppModel.self) private var app
    @Environment(\.quietTheme) private var theme
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var showsAlbum = true
    @State private var language = WordLanguage.en
    @State private var didAppear = false

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                Segments(selection: $showsAlbum, options: [(true, "Album"), (false, "Rewards")])
                    .accessibilityIdentifier("collectionTabs")
                if showsAlbum { album } else { RewardsList() }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .frame(maxWidth: Layout.column(sizeClass))
            .frame(maxWidth: .infinity)
        }
        .screen(Text("Collection"))
        .onAppear {
            guard !didAppear else { return }
            didAppear = true
            showsAlbum = startsOnAlbum
            language = app.wordLanguage
        }
    }

    // MARK: Album

    private var album: some View {
        let total = app.profile.album.total(language, in: app.library)
        return VStack(spacing: 12) {
            Segments(selection: $language, options: [(.en, "English"), (.es, "Español")])
            HStack(alignment: .firstTextBaseline) {
                Text("\(total.found) words").font(.system(.title2, design: .rounded, weight: .heavy)).foregroundStyle(theme.ink)
                Spacer()
                Text("of \(total.total) · badges: \(total.badges)").font(.subheadline).foregroundStyle(theme.muted)
            }
            .padding(.horizontal, 4)
            VStack(spacing: 0) {
                ForEach(app.library.themes(for: language)) { item in
                    NavigationLink {
                        AlbumThemeView(themeID: item.id, language: language)
                    } label: {
                        AlbumRow(theme: item, count: app.profile.album.count(item, language))
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("album-\(item.id)")
                    if item.id != app.library.themes(for: language).last?.id {
                        Rectangle().fill(theme.separator).frame(height: 1).padding(.leading, 50)
                    }
                }
            }
            .background { SurfaceShape(shape: RoundedRectangle(cornerRadius: 22, style: .continuous)) }
        }
    }
}

private struct AlbumRow: View {
    let theme: WordTheme
    let count: (found: Int, total: Int)

    @Environment(\.quietTheme) private var colors

    var body: some View {
        let complete = count.total > 0 && count.found == count.total
        HStack(spacing: 12) {
            Image(systemName: theme.symbol).font(.system(size: 18, weight: .semibold)).foregroundStyle(colors.primary).frame(width: 26)
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(verbatim: theme.localizedName).font(.headline).foregroundStyle(colors.ink)
                    Spacer()
                    Text(verbatim: "\(count.found)/\(count.total)").font(.subheadline.monospacedDigit()).foregroundStyle(colors.muted)
                }
                ProgressView(value: Double(count.found), total: Double(max(count.total, 1)))
                    .tint(complete ? colors.accent : colors.primary)
            }
            Image(systemName: complete ? "rosette" : "circle.dashed")
                .font(.title3)
                .foregroundStyle(complete ? colors.accent : colors.ghost)
                .accessibilityLabel(complete ? Text("Badge earned") : Text("Badge not earned yet"))
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .contentShape(Rectangle())
    }
}

/// One theme's album: found words as written; missing ones only show their length.
struct AlbumThemeView: View {
    let themeID: String
    let language: WordLanguage

    @Environment(AppModel.self) private var app
    @Environment(\.quietTheme) private var theme
    @State private var filter = 0
    @State private var lookUp: LookUpTerm?

    var body: some View {
        let item = app.library.theme(themeID)!
        let found = app.profile.album.found(item, language)
        let count = app.profile.album.count(item, language)
        let words = (item.words(language)?.all ?? []).sorted { $0.text.localizedCompare($1.text) == .orderedAscending }
        let shown = words.filter { filter == 0 || (filter == 1) == found.contains($0.letters) }
        ScrollView {
            VStack(spacing: 12) {
                HStack(spacing: 14) {
                    Image(systemName: count.found == count.total ? "rosette" : item.symbol)
                        .font(.title2)
                        .foregroundStyle(count.found == count.total ? theme.accent : theme.muted)
                        .frame(width: 54, height: 54)
                        .overlay { Circle().strokeBorder(theme.ghost, style: StrokeStyle(lineWidth: 2.5, dash: count.found == count.total ? [] : [4, 4])) }
                    VStack(alignment: .leading, spacing: 6) {
                        Text(count.found == count.total ? "Badge earned" : "\(count.total - count.found) words to the badge")
                            .font(.subheadline.weight(.semibold)).foregroundStyle(theme.ink)
                        ProgressView(value: Double(count.found), total: Double(max(count.total, 1))).tint(theme.primary)
                    }
                }
                .card(padding: 14, cornerRadius: 22)
                Segments(selection: $filter, options: [(0, "All"), (1, "Found"), (2, "Missing")])
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 140), alignment: .leading)], alignment: .leading, spacing: 10) {
                    ForEach(shown, id: \.letters) { word in
                        if found.contains(word.letters) {
                            LookUpWord(text: word.text, lookUp: $lookUp)
                        } else {
                            HStack(spacing: 3) {
                                ForEach(0..<word.count, id: \.self) { _ in Circle().fill(theme.ghost).frame(width: 6, height: 6) }
                            }
                            .frame(height: 20)
                            .accessibilityLabel(Text("Missing word, \(word.count) letters"))
                        }
                    }
                }
                .padding(16)
                .background { SurfaceShape(shape: RoundedRectangle(cornerRadius: 22, style: .continuous)) }
                Text("Missing words show only their length. Tap a word with a book to see its definition.").font(.footnote).foregroundStyle(theme.muted)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
        }
        .lookUpSheet($lookUp)
        .screen(Text(verbatim: item.localizedName)) {
            Text(verbatim: "\(count.found)/\(count.total)").font(.headline.monospacedDigit()).foregroundStyle(theme.muted)
        }
    }
}

/// Markers, board letters, themes and the icon. Locked ones say how they're earned.
private struct RewardsList: View {
    @Environment(AppModel.self) private var app
    @Environment(\.quietTheme) private var theme
    @AppStorage(PreferenceKey.marker) private var marker = Marker.highlighter
    @AppStorage(PreferenceKey.boardFont) private var boardFont = BoardFont.rounded
    @AppStorage(PreferenceKey.palette) private var palette = Palette.tealCoral
    @AppStorage(PreferenceKey.appIcon) private var icon = AppIconChoice.standard

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            section("Marker", Marker.allCases, selection: $marker) { MarkerPreview(marker: $0) }
            section("Board letters", BoardFont.allCases, selection: $boardFont) { font in
                Text(verbatim: "ABC").font(font.font(size: 20)).tracking(2).foregroundStyle(theme.ink)
            }
            section("Theme", Palette.allCases, selection: $palette) { option in
                let t = Theme(option)
                HStack(spacing: 4) { Circle().fill(t.primary).frame(width: 20); Circle().fill(t.accent).frame(width: 20) }.frame(height: 20)
            }
            section("App icon", AppIconChoice.allCases, selection: Binding(get: { icon }, set: { choice in
                icon = choice
                UIApplication.shared.setAlternateIconName(choice.iconName)
            })) { choice in
                Image(systemName: choice == .gold ? "seal.fill" : "seal").foregroundStyle(choice == .gold ? Color.orange : theme.primary).font(.title2)
            }
        }
    }

    private func section<R: Reward & RawRepresentable, Preview: View>(_ title: LocalizedStringKey, _ items: [R], selection: Binding<R>,
                                                                      @ViewBuilder preview: @escaping (R) -> Preview) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.footnote.weight(.semibold)).textCase(.uppercase).foregroundStyle(theme.muted).padding(.leading, 4)
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 100), spacing: 8)], spacing: 8) {
                ForEach(items, id: \.self) { item in
                    let unlocked = app.isUnlocked(item)
                    let selected = selection.wrappedValue == item
                    Button { if unlocked { selection.wrappedValue = item } } label: {
                        VStack(alignment: .leading, spacing: 6) {
                            preview(item)
                                .frame(maxWidth: .infinity, minHeight: 46)
                                .background(theme.background, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                                .opacity(unlocked ? 1 : 0.45)
                            Text(item.title).font(.subheadline.weight(.semibold)).foregroundStyle(theme.ink)
                            if let achievement = item.achievement, !unlocked {
                                Label { Text(achievement.title) } icon: { Image(systemName: "lock.fill") }
                                    .font(.caption2).foregroundStyle(theme.muted)
                            } else {
                                Text("Earned").font(.caption2).foregroundStyle(theme.muted)
                            }
                        }
                        .padding(10)
                        .background { SurfaceShape(shape: RoundedRectangle(cornerRadius: 18, style: .continuous)) }
                        .overlay { RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(selected ? theme.primary : .clear, lineWidth: 2.5) }
                    }
                    .buttonStyle(.plain)
                    .accessibilityElement(children: .combine)
                    .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
                }
            }
        }
    }
}

private struct MarkerPreview: View {
    let marker: Marker
    @Environment(\.quietTheme) private var theme
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        ZStack {
            Canvas { context, size in
                let rect = CGRect(x: 4, y: size.height / 2 - 10, width: size.width - 8, height: 20)
                var local = context
                marker.draw(Path(roundedRect: rect, cornerRadius: 10), rect: rect, theme: theme, dark: colorScheme == .dark, in: &local)
            }
            .frame(width: 90, height: 30)
            Text(verbatim: "MAREA").font(.system(size: 11, weight: .semibold, design: .rounded)).tracking(4).foregroundStyle(theme.ink)
        }
    }
}
