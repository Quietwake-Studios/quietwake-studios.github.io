import QuietUI
import QuietWordsCore
import SwiftUI

/// How to play. Step 1 is the language of the words (both choices in view, the system's
/// preselected, never deduced). Then a hand-made board: find a word by dragging, one by
/// tapping first and last letter, and the hidden word. "Skip" is always there.
struct TutorialView: View {
    let onFinish: () -> Void

    @Environment(AppModel.self) private var app
    @Environment(\.quietTheme) private var theme
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var step = 0
    @State private var language = WordLanguage.systemSuggestion
    @State private var model: GameModel?

    private static let steps = 4

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                HStack(spacing: 6) {
                    ForEach(0..<Self.steps, id: \.self) { i in
                        Capsule().fill(i == step ? theme.primary : theme.ghost).frame(width: i == step ? 18 : 7, height: 7)
                    }
                }
                .accessibilityElement()
                .accessibilityLabel(Text("Step \(step + 1) of \(Self.steps)"))
                Spacer()
                Button("Skip") { finish() }
                    .font(.headline)
                    .foregroundStyle(theme.primary)
                    .accessibilityIdentifier("skip")
            }
            .padding(.top, 8)
            if step == 0 { languageStep } else if let model { playStep(model) }
        }
        .padding(.horizontal, 24)
        .frame(maxWidth: Layout.column(sizeClass))
        .frame(maxWidth: .infinity)
        .background(theme.background)
        .toolbar(.hidden, for: .navigationBar)
        .onAppear { if app.hasChosenLanguage { language = app.wordLanguage } }
    }

    // MARK: Step 1: the words' language

    private var languageStep: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Which words do you want to find?")
                .font(.system(.largeTitle, design: .rounded, weight: .heavy))
                .foregroundStyle(theme.ink)
                .padding(.top, 36)
            Text("The app stays in your language. You can change this anytime where you start a game, or in Settings.")
                .foregroundStyle(theme.muted)
            VStack(spacing: 14) {
                LanguageChoice(language: .en, sample: "TIDE · CORAL · ABYSSAL", note: "Themed lists in English", selected: $language)
                LanguageChoice(language: .es, sample: "MAREA · CORAL · ÑANDÚ", note: "Themed lists in Spanish, with Ñ", selected: $language)
            }
            .padding(.top, 14)
            Spacer()
            Button("Continue") {
                app.chooseLanguage(language)
                model = GameModel(session: Self.session(language), isTutorial: true)
                model?.allowedWord = 0
                withAnimation(.quiet) { step = 1 }
            }
            .buttonStyle(CapsuleButtonStyle())
            .padding(.bottom, 16)
            .accessibilityIdentifier("tutorialContinue")
        }
    }

    // MARK: Steps 2–4: playing

    private func playStep(_ model: GameModel) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title).font(.system(.title, design: .rounded, weight: .heavy)).foregroundStyle(theme.ink).padding(.top, 28)
            Text(message).foregroundStyle(theme.muted).fixedSize(horizontal: false, vertical: true)
            HStack { Spacer(); BoardView(model: model, size: min(330, Layout.column(sizeClass) - 48)); Spacer() }
                .padding(.top, 18)
            if step == 3 {
                HiddenWordRow(session: model.session).padding(.top, 8)
            }
            Spacer()
            if step == 3 {
                Button("Start playing") { finish() }
                    .buttonStyle(CapsuleButtonStyle())
                    .padding(.bottom, 16)
                    .accessibilityIdentifier("startPlaying")
            }
        }
        .onChange(of: model.session.found) { _, found in
            withAnimation(.quiet) {
                if found.count == 1 { step = 2; model.allowedWord = 1 }
                if found.count == 2 { step = 3; model.allowedWord = nil }
            }
        }
    }

    private var words: [String] { language == .en ? ["CORAL", "WAVES"] : ["CORAL", "MEDUSA"] }

    private var title: LocalizedStringResource {
        switch step {
        case 1: "Find \(words[0])"
        case 2: "Now \(words[1])"
        default: "The hidden word"
        }
    }

    private var message: LocalizedStringResource {
        switch step {
        case 1: "Drag from the first letter to the last."
        case 2: "Or tap its first letter, then its last. Words can run down and diagonally too."
        default: "The letters no word uses spell a word of the theme. Guess it early for +100, or it shows up at the end."
        }
    }

    private func finish() {
        if step == 0 { app.chooseLanguage(language) }
        onFinish()
    }

    /// A 7×7 board made by hand: CORAL across and a second word down (Waves / Medusa).
    static func session(_ language: WordLanguage) -> GameSession {
        let rows: [String]
        let second: (text: String, row: Int, column: Int)
        if language == .en {
            rows = ["SHELLTA", "WTDIVER", "ACORALE", "VKDLFIC", "EAEPINS", "SLPOOLL", "WAVELLT"]
            second = ("Waves", 1, 0)
        } else {
            rows = ["OLASALT", "PERLASM", "ACORALE", "RDUNASD", "EUBOYAU", "NSARESS", "AAMEJAA"]
            second = ("Medusa", 1, 6)
        }
        let letters = rows.flatMap { $0.map(String.init) }
        let coral = Placement(word: Word(text: "Coral", language: language, group: .common), row: 2, column: 1, direction: .east)
        let down = Placement(word: Word(text: second.text, language: language, group: .common), row: second.row, column: second.column, direction: .south)
        let board = Board(side: 7, language: language, letters: letters, placements: [coral, down],
                          hidden: language == .en ? "SWELL" : "MAREA", hiddenCells: [])
        return GameSession(mode: .free, themeID: "ocean", level: .gentle, board: board)
    }
}
