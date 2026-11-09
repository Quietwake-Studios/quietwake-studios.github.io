import QuietUI
import QuietWordsCore
import SwiftUI

/// End of a game: score, the hidden word, the stats, new album words and achievements.
struct SummarySheet: View {
    let session: GameSession
    let kind: GameKind
    let bestScore: Int?
    let unlocked: [Achievement]
    let newAlbumWords: Int
    let onNewGame: () -> Void
    let onHome: () -> Void

    @Environment(\.quietTheme) private var theme
    @Environment(AppModel.self) private var app
    @State private var lookUp: LookUpTerm?

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                Text(title)
                    .font(.headline)
                    .foregroundStyle(theme.muted)
                    .padding(.top, 28)
                Text(session.score, format: .number)
                    .font(.score(size: 56))
                    .foregroundStyle(theme.ink)
                    .accessibilityIdentifier("summaryScore")
                if let bestScore, kind == .free, session.score > bestScore {
                    Text("New best")
                        .font(.footnote.weight(.bold))
                        .foregroundStyle(theme.accent)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 4)
                        .background(theme.accent.opacity(0.14), in: Capsule())
                }
                VStack(spacing: 6) {
                    HiddenSlots(length: session.board.hidden.count, letters: session.board.hidden,
                                state: session.hiddenState == .guessed ? .guessed : .revealed, size: 36)
                    Text("The leftover letters")
                        .font(.footnote)
                        .foregroundStyle(theme.muted)
                }
                .padding(.vertical, 6)
                stats
                words
                if !unlocked.isEmpty { achievements }
                buttons
                    .padding(.top, 4)
            }
            .padding(.horizontal, 22)
            .padding(.bottom, 24)
            .frame(maxWidth: 520)
            .frame(maxWidth: .infinity)
        }
        .background(theme.surface)
        .lookUpSheet($lookUp)
    }

    /// The board's words; the ones the dictionary knows open their definition.
    private var words: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Words").font(.caption.weight(.bold)).textCase(.uppercase).foregroundStyle(theme.muted)
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 130), alignment: .leading)], alignment: .leading, spacing: 6) {
                ForEach(session.board.placements, id: \.word.letters) { placement in
                    LookUpWord(text: placement.word.text, lookUp: $lookUp)
                }
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(theme.background, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private var title: LocalizedStringResource {
        switch kind {
        case .free: "Puzzle complete!"
        case .daily: "Challenge complete!"
        }
    }

    private var stats: some View {
        let theme = app.library.theme(session.themeID)
        let album = theme.map { app.profile.album.count($0, session.language) }
        return VStack(spacing: 0) {
            row("Words", "\(session.found.count)/\(session.board.placements.count) · \(session.wordPoints.formatted())")
            row("Time", Duration.seconds(session.elapsedSeconds).formatted(.time(pattern: .minuteSecond)))
            row("Time bonus", "+\(session.timeBonus ?? 0)")
            row("Hidden word", session.hiddenState == .guessed
                ? String(localized: "guessed early · +\(session.scoring.hiddenGuessed)")
                : String(localized: "revealed · +\(session.scoring.hiddenRevealed)"))
            if session.hintsUsed > 0 { row("Hints", "\(session.hintsUsed) · −\(session.hintCost)") }
            if let theme, let album {
                row("New in the album", "\(newAlbumWords) · \(theme.localizedName) \(album.found)/\(album.total)")
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 2)
        .background(self.theme.background, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private func row(_ label: LocalizedStringResource, _ value: String) -> some View {
        HStack {
            Text(label).foregroundStyle(theme.muted)
            Spacer()
            Text(verbatim: value).fontWeight(.semibold).foregroundStyle(theme.ink).monospacedDigit()
        }
        .font(.subheadline)
        .frame(minHeight: 38)
        .overlay(alignment: .top) { Rectangle().fill(theme.separator).frame(height: 1).opacity(0.6) }
        .accessibilityElement(children: .combine)
    }

    private var achievements: some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(unlocked, id: \.self) { achievement in
                HStack(spacing: 12) {
                    Image(systemName: achievement.systemImage)
                        .foregroundStyle(theme.onPrimary)
                        .frame(width: 40, height: 40)
                        .background(theme.primary, in: Circle())
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Achievement").font(.caption.weight(.bold)).textCase(.uppercase).foregroundStyle(theme.muted)
                        if let reward = achievement.reward {
                            Text("\(Text(achievement.title)) · \(Text(reward))").font(.subheadline.weight(.semibold)).foregroundStyle(theme.ink)
                        } else {
                            Text(achievement.title).font(.subheadline.weight(.semibold)).foregroundStyle(theme.ink)
                        }
                    }
                    Spacer()
                }
            }
        }
        .padding(14)
        .background(theme.background, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    @ViewBuilder
    private var buttons: some View {
        switch kind {
        case .free:
            HStack(spacing: 10) {
                Button("Home", action: onHome).buttonStyle(CapsuleButtonStyle(isProminent: false))
                Button("New Game", action: onNewGame).buttonStyle(CapsuleButtonStyle())
                    .accessibilityIdentifier("summaryNewGame")
            }
        case .daily:
            Button("Done", action: onHome).buttonStyle(CapsuleButtonStyle())
                .accessibilityIdentifier("summaryDone")
        }
    }
}
