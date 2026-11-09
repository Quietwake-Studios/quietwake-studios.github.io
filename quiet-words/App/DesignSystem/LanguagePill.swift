import QuietUI
import QuietWordsCore
import SwiftUI

/// The compact language picker (EN · ES) where play starts: the daily card and New Game.
/// One tap switches and it's remembered; nobody is asked before each game. A check marks a
/// language whose daily is done today.
struct LanguagePill: View {
    @Binding var language: WordLanguage
    var done: Set<WordLanguage> = []
    /// On the daily card (primary background).
    var onPrimary = false

    @Environment(\.quietTheme) private var theme

    var body: some View {
        HStack(spacing: 0) {
            ForEach(WordLanguage.allCases, id: \.self) { option in
                let selected = option == language
                Button {
                    guard !selected else { return }
                    withAnimation(.quiet) { language = option }
                    FeedbackService.shared.play(.letter)
                } label: {
                    HStack(spacing: 3) {
                        Text(verbatim: option.code)
                        if done.contains(option) {
                            Image(systemName: "checkmark").font(.caption2.weight(.heavy))
                        }
                    }
                    .font(.footnote.weight(.bold))
                    .tracking(0.5)
                    .foregroundStyle(selected ? selectedText : idleText)
                    .padding(.horizontal, 12)
                    .frame(minWidth: 44, minHeight: 30)
                    .background {
                        if selected {
                            Capsule().fill(fill).shadow(color: .black.opacity(0.1), radius: 1.5, y: 1)
                        }
                    }
                    .contentShape(Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(label(option))
                .accessibilityAddTraits(selected ? .isSelected : [])
                .accessibilityIdentifier("words-\(option.rawValue)")
            }
        }
        .padding(3)
        .background(track, in: Capsule())
        .fixedSize()
    }

    private var track: Color { onPrimary ? theme.onPrimary.opacity(0.18) : theme.surface2 }
    private var fill: Color { onPrimary ? theme.onPrimary : theme.surface }
    private var selectedText: Color { onPrimary ? theme.primary : theme.ink }
    private var idleText: Color { onPrimary ? theme.onPrimary : theme.muted }

    private func label(_ option: WordLanguage) -> Text {
        let name = Text(option.name)
        if done.contains(option) { return Text("Words in \(name), today's challenge done") }
        return Text("Words in \(name)")
    }
}
