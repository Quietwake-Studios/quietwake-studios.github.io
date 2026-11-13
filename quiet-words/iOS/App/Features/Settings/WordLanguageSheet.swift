import QuietUI
import QuietWordsCore
import SwiftUI

/// Settings › Word language: the two languages, big, like the tutorial's first step. A tap
/// switches right away (the EN · ES pill where play starts does the same); "Done" closes.
struct WordLanguageSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.quietTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        @Bindable var app = app
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("Word language")
                    .font(.system(.title2, design: .rounded, weight: .heavy))
                    .foregroundStyle(theme.ink)
                    .accessibilityAddTraits(.isHeader)
                    .padding(.top, 28)
                LanguageChoice(language: .en, sample: "TIDE · CORAL · ABYSSAL", note: "Themed lists in English",
                               selected: selection($app.wordLanguage))
                LanguageChoice(language: .es, sample: "MAREA · CORAL · ÑANDÚ", note: "Themed lists in Spanish, with Ñ",
                               selected: selection($app.wordLanguage))
                Text("Words only; the app stays in your language. Each language keeps its own album.")
                    .font(.footnote)
                    .foregroundStyle(theme.muted)
                    .padding(.horizontal, 4)
                Button("Done") { dismiss() }
                    .buttonStyle(CapsuleButtonStyle())
                    .padding(.top, 8)
                    .accessibilityIdentifier("wordLanguageDone")
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 16)
        }
        .scrollBounceBehavior(.basedOnSize)
        .background(theme.surface)
        .accessibilityIdentifier("wordLanguageSheet")
    }

    /// Writes only on a change, with the pill's tick.
    private func selection(_ binding: Binding<WordLanguage>) -> Binding<WordLanguage> {
        Binding(get: { binding.wrappedValue }, set: { language in
            guard language != binding.wrappedValue else { return }
            withAnimation(.quiet) { binding.wrappedValue = language }
            FeedbackService.shared.play(.letter)
        })
    }
}
