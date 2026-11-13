import QuietUI
import QuietWordsCore
import SwiftUI

/// One of the two languages, big, with a sample of its words: the tutorial's first step and
/// Settings › Word language.
struct LanguageChoice: View {
    let language: WordLanguage
    let sample: String
    let note: LocalizedStringKey
    @Binding var selected: WordLanguage

    @Environment(\.quietTheme) private var theme

    var body: some View {
        let isSelected = language == selected
        Button { selected = language } label: {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text(verbatim: language.wordsTitle).font(.system(.title3, design: .rounded, weight: .heavy)).foregroundStyle(theme.ink)
                    Spacer()
                    Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                        .font(.title3)
                        .foregroundStyle(isSelected ? theme.primary : theme.ghost)
                }
                Text(verbatim: sample).font(.system(.headline, design: .rounded)).tracking(1.5).foregroundStyle(theme.primary)
                Text(note).font(.footnote).foregroundStyle(theme.muted)
            }
            .padding(18)
            .background {
                SurfaceShape(shape: RoundedRectangle(cornerRadius: 24, style: .continuous),
                             fill: isSelected ? theme.primary.opacity(0.08) : nil)
            }
            .overlay {
                RoundedRectangle(cornerRadius: 24, style: .continuous).strokeBorder(isSelected ? theme.primary : .clear, lineWidth: 2.5)
            }
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
        .accessibilityIdentifier("language-\(language.rawValue)")
    }
}
