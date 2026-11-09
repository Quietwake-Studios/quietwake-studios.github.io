import QuietUI
import QuietWordsCore
import SwiftUI

/// The theme's words as written (accents, spaces); found ones struck through in muted.
/// Follows Dynamic Type: three columns, two from XL, one at accessibility sizes.
struct WordListView: View {
    let session: GameSession
    var columns = 3

    @Environment(\.quietTheme) private var theme
    @Environment(\.dynamicTypeSize) private var typeSize

    private var count: Int {
        if typeSize.isAccessibilitySize { return 1 }
        if typeSize >= .xLarge { return min(columns, 2) }
        return columns
    }

    var body: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 12, alignment: .leading), count: count),
                  alignment: .leading, spacing: 4) {
            ForEach(Array(session.board.placements.enumerated()), id: \.offset) { index, placement in
                let found = session.isFound(index)
                Text(verbatim: placement.word.text)
                    .font(.subheadline)
                    .foregroundStyle(found ? theme.muted : theme.ink)
                    // Not by color alone: found words are struck through.
                    .strikethrough(found, color: theme.muted)
                    .lineLimit(2)
                    .accessibilityLabel(found ? Text("\(placement.word.text), found") : Text("\(placement.word.text), to find"))
            }
        }
    }
}

/// One box per letter of the hidden word: empty, typed, wrong, guessed (accent) or
/// revealed at the end (primary).
struct HiddenSlots: View {
    enum State { case hidden, typing, wrong, guessed, revealed }

    let length: Int
    var letters = ""
    var state: State = .hidden
    var size: CGFloat = 26

    @Environment(\.quietTheme) private var theme

    var body: some View {
        let chars = Array(letters)
        HStack(spacing: size * 0.2) {
            ForEach(0..<length, id: \.self) { i in
                let letter = i < chars.count ? String(chars[i]) : ""
                Text(verbatim: letter)
                    .font(.system(size: size * 0.6, weight: .heavy, design: .rounded))
                    .foregroundStyle(foreground)
                    .frame(width: size, height: size * 1.18)
                    .background(background(filled: !letter.isEmpty), in: RoundedRectangle(cornerRadius: size * 0.27, style: .continuous))
                    .overlay {
                        RoundedRectangle(cornerRadius: size * 0.27, style: .continuous)
                            .strokeBorder(border(filled: !letter.isEmpty, cursor: state == .typing && i == chars.count), lineWidth: 2)
                    }
            }
        }
        .accessibilityElement()
        .accessibilityLabel(accessibility)
    }

    private var foreground: Color {
        switch state {
        case .guessed: theme.onAccent
        case .revealed: theme.onPrimary
        case .wrong: theme.accent
        default: theme.ink
        }
    }

    private func background(filled: Bool) -> Color {
        switch state {
        case .guessed: theme.accent
        case .revealed: theme.primary
        case .wrong: theme.accent.opacity(0.1)
        default: filled ? theme.surface : theme.surface2
        }
    }

    private func border(filled: Bool, cursor: Bool) -> Color {
        switch state {
        case .wrong: theme.accent
        case .typing: filled || cursor ? theme.primary : .clear
        default: .clear
        }
    }

    private var accessibility: Text {
        switch state {
        case .guessed, .revealed: Text("Hidden word: \(letters)")
        default: Text("Hidden word, \(length) letters")
        }
    }
}

/// The hidden word under the board: its slots, and its letters once guessed or revealed.
struct HiddenWordRow: View {
    let session: GameSession
    var size: CGFloat = 26

    @Environment(\.quietTheme) private var theme

    var body: some View {
        // Side by side when it fits; the slots under the label for long words in a narrow column.
        ViewThatFits(in: .horizontal) {
            HStack {
                label
                Spacer(minLength: 8)
                slots
            }
            VStack(alignment: .leading, spacing: 8) {
                label
                slots
            }
        }
        .fixedSize(horizontal: false, vertical: true)
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("hiddenWord")
        #if DEBUG
        // UI tests read the answer here.
        .accessibilityValue(ProcessInfo.processInfo.arguments.contains("-uiTesting") ? Text(verbatim: session.board.hidden) : Text(verbatim: ""))
        #endif
    }

    private var label: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("Hidden word")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(theme.ink)
            Text(caption)
                .font(.caption)
                .foregroundStyle(theme.muted)
        }
        .layoutPriority(1)
    }

    private var slots: some View {
        HiddenSlots(length: session.board.hidden.count, letters: session.hiddenShown, state: state, size: size)
    }

    private var state: HiddenSlots.State {
        switch session.hiddenState {
        case .unknown: .hidden
        case .guessed: .guessed
        case .revealed: .revealed
        }
    }

    private var caption: LocalizedStringResource {
        switch session.hiddenState {
        case .unknown: "\(session.board.hidden.count) letters · the leftover ones"
        case .guessed: "Guessed · +\(session.scoring.hiddenGuessed)"
        case .revealed: "Revealed · +\(session.scoring.hiddenRevealed)"
        }
    }
}
