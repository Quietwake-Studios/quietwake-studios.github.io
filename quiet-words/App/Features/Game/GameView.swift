import QuietUI
import QuietWordsCore
import SwiftUI

/// The game screen. iPhone: board, hidden word, list below, actions at the bottom. iPad
/// (regular width): the list beside the board.
struct GameView: View {
    let model: GameModel
    let kind: GameKind
    let bestScore: Int?
    let unlocked: [Achievement]
    var newAlbumWords = 0
    let onNewGame: () -> Void
    let onRestart: () -> Void
    var onHome: (() -> Void)?

    @Environment(\.quietTheme) private var theme
    @Environment(\.horizontalSizeClass) private var sizeClass
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(AppModel.self) private var app
    @State private var guessing = false
    @State private var confirmsRestart = false

    private var session: GameSession { model.session }

    var body: some View {
        GeometryReader { proxy in
            Group {
                if sizeClass == .regular, proxy.size.width > proxy.size.height * 0.9 || proxy.size.width > 900 {
                    wide(proxy.size)
                } else {
                    compact(proxy.size)
                }
            }
            .frame(width: proxy.size.width, height: proxy.size.height)
        }
        .background(theme.background)
        .toolbar(.hidden, for: .navigationBar)
        .task(id: scenePhase) {
            guard scenePhase == .active else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(1))
                if !session.isWon { model.tick() }
            }
        }
        .sheet(isPresented: $guessing) {
            GuessSheet(model: model)
                .presentationDetents([.height(330)])
                .presentationCornerRadius(32)
        }
        .sheet(isPresented: Binding(get: { model.showsSummary }, set: { if !$0 { model.dismissSummary() } })) {
            SummarySheet(session: session, kind: kind, bestScore: bestScore, unlocked: unlocked, newAlbumWords: newAlbumWords,
                         onNewGame: onNewGame, onHome: { model.dismissSummary(); (onHome ?? { dismiss() })() })
                .presentationDetents([.large])
                .presentationCornerRadius(32)
                .interactiveDismissDisabled()
        }
        .overlay {
            if model.showsSummary, case .daily = kind, !reduceMotion {
                Fireworks(colors: [theme.primary, theme.accent, theme.suitRed, Color(red: 0.95, green: 0.75, blue: 0.3), theme.ink])
                    .allowsHitTesting(false)
                    .ignoresSafeArea()
            }
        }
        .confirmationDialog("Start this puzzle over?", isPresented: $confirmsRestart, titleVisibility: .visible) {
            Button("Start over", role: .destructive, action: onRestart)
        }
        // iPad keyboard: H for a hint.
        .background {
            Button("Hint", action: model.requestHint).keyboardShortcut("h", modifiers: []).hidden()
        }
    }

    // MARK: Layouts

    private func compact(_ size: CGFloat2) -> some View {
        let boardSize = min(size.width - 32, Layout.boardWidth(sizeClass), size.height * 0.48)
        return VStack(spacing: 0) {
            topBar
                .padding(.horizontal, 20)
                .padding(.top, 4)
            BoardView(model: model, size: boardSize)
                .padding(.top, 12)
            VStack(spacing: 10) {
                HiddenWordRow(session: session)
                ScrollView {
                    VStack(alignment: .leading, spacing: 6) {
                        wordsHeader
                        WordListView(session: session)
                    }
                    .padding(.horizontal, 4)
                }
                .scrollBounceBehavior(.basedOnSize)
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            actions
                .padding(.horizontal, 20)
                .padding(.bottom, 8)
        }
    }

    private func wide(_ size: CGFloat2) -> some View {
        let boardSize = min(size.height - 80, size.width * 0.58, 680)
        return HStack(alignment: .center, spacing: 36) {
            BoardView(model: model, size: boardSize)
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    backButton
                    Spacer()
                    menu
                }
                VStack(alignment: .leading, spacing: 4) {
                    scoreText
                    contextText
                }
                HiddenWordRow(session: session, size: 30)
                ScrollView {
                    VStack(alignment: .leading, spacing: 8) {
                        wordsHeader
                        WordListView(session: session, columns: 2)
                    }
                }
                actions
            }
            .frame(maxWidth: 420)
        }
        .padding(32)
    }

    // MARK: Parts

    private var topBar: some View {
        HStack {
            backButton
            Spacer()
            VStack(spacing: 4) {
                scoreText
                contextText
            }
            Spacer()
            menu
        }
    }

    private var backButton: some View {
        Button { dismiss() } label: { RoundIconLabel(systemImage: "chevron.backward") }
            .buttonStyle(.plain)
            .accessibilityLabel(Text("Back"))
            .accessibilityIdentifier("back")
    }

    private var menu: some View {
        Menu {
            Button { confirmsRestart = true } label: { Label("Start over", systemImage: "arrow.counterclockwise") }
            if kind == .free {
                Button(action: onNewGame) { Label("New Game", systemImage: "plus") }
            }
        } label: {
            RoundIconLabel(systemImage: "ellipsis")
        }
        .accessibilityLabel(Text("Menu"))
        .accessibilityIdentifier("menu")
    }

    private var scoreText: some View {
        Text(session.score, format: .number)
            .font(.score(size: 40))
            .foregroundStyle(theme.ink)
            .contentTransition(.numericText(value: Double(session.score)))
            .animation(.quiet, value: session.score)
            .accessibilityLabel(Text("Score \(session.score)"))
            .accessibilityIdentifier("score")
    }

    private var contextText: some View {
        let theme = app.library.theme(session.themeID)?.localizedName ?? ""
        let time = Duration.seconds(session.elapsedSeconds).formatted(.time(pattern: .minuteSecond))
        return Group {
            switch kind {
            case .free:
                Text("\(theme) · \(Text(session.level.title)) · \(time)")
            case .daily(_, let isReplay):
                if isReplay {
                    Text("Replay · \(theme) · \(time)")
                } else {
                    Text("Daily · \(theme) · \(time)")
                }
            }
        }
        .font(.subheadline.monospacedDigit())
        .foregroundStyle(self.theme.muted)
        .lineLimit(1)
    }

    private var wordsHeader: some View {
        HStack {
            Text("Words")
                .font(.caption.weight(.bold))
                .textCase(.uppercase)
                .foregroundStyle(theme.muted)
            Spacer()
            Text(verbatim: "\(session.found.count)/\(session.board.placements.count)")
                .font(.caption.monospacedDigit())
                .foregroundStyle(theme.muted)
                .accessibilityLabel(Text("\(session.found.count) of \(session.board.placements.count) found"))
        }
    }

    private var actions: some View {
        HStack(spacing: 10) {
            Button(action: model.requestHint) {
                HStack(spacing: 6) {
                    Image(systemName: "lightbulb")
                    Text("Hint")
                    Text(verbatim: "−\(session.scoring.hint)").foregroundStyle(theme.muted)
                }
                .lineLimit(1)
                .minimumScaleFactor(0.75)
            }
            .buttonStyle(CapsuleButtonStyle(isProminent: false))
            .disabled(session.isWon)
            .accessibilityLabel(Text("Hint, costs \(session.scoring.hint) points"))
            .accessibilityIdentifier("hint")
            Button { guessing = true } label: {
                HStack(spacing: 6) {
                    Image(systemName: "eye")
                    Text("Guess")
                    Text(verbatim: "+\(session.scoring.hiddenGuessed)").foregroundStyle(theme.accent)
                }
                .lineLimit(1)
                .minimumScaleFactor(0.75)
            }
            .buttonStyle(CapsuleButtonStyle(isProminent: false))
            .disabled(!session.canGuess)
            .accessibilityLabel(Text("Guess the hidden word, \(session.scoring.hiddenGuessed) points"))
            .accessibilityIdentifier("guess")
        }
    }
}

typealias CGFloat2 = CGSize

/// "Guess the hidden word": slots, the keyboard, 3 tries; a miss costs nothing.
struct GuessSheet: View {
    let model: GameModel

    @Environment(\.quietTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var text = ""
    @State private var wrong = false
    @FocusState private var focused: Bool

    private var session: GameSession { model.session }
    private var length: Int { session.board.hidden.count }

    var body: some View {
        VStack(spacing: 14) {
            Text("Guess the hidden word")
                .font(.system(.title3, design: .rounded, weight: .heavy))
                .foregroundStyle(theme.ink)
                .padding(.top, 28)
            Text("A word of the theme made of the letters no word uses. Get it now for +\(session.scoring.hiddenGuessed).")
                .font(.subheadline)
                .foregroundStyle(theme.muted)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
            HiddenSlots(length: length, letters: typed, state: wrong ? .wrong : .typing, size: length > 7 ? 34 : 42)
                .modifier(Shake(amount: CGFloat(model.guessShake)))
                .animation(reduceMotion ? nil : .linear(duration: 0.24), value: model.guessShake)
                .onTapGesture { focused = true }
            Text("\(session.guessesLeft) tries left · a miss costs nothing")
                .font(.footnote)
                .foregroundStyle(theme.muted)
            TextField("", text: $text)
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
                .keyboardType(.alphabet)
                .focused($focused)
                .frame(width: 1, height: 1)
                .opacity(0.01)
                .accessibilityLabel(Text("Hidden word"))
                .accessibilityIdentifier("guessField")
                .onChange(of: text) { _, value in
                    wrong = false
                    let letters = WordText.normalize(value, language: session.language)
                    let clipped = String(letters.prefix(length))
                    if clipped != value { text = clipped }
                    if clipped.count == length { submit(clipped) }
                }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity)
        .background(theme.surface)
        .onAppear { focused = true }
    }

    private var typed: String { String(WordText.normalize(text, language: session.language).prefix(length)) }

    private func submit(_ guess: String) {
        if model.guess(guess) {
            dismiss()
        } else {
            wrong = true
            Task {
                try? await Task.sleep(for: .milliseconds(450))
                text = ""
                wrong = false
                if !session.canGuess { dismiss() }
            }
        }
    }
}

/// A short horizontal shake (±4 pt × 3) driven by a counter.
struct Shake: GeometryEffect {
    var amount: CGFloat
    var animatableData: CGFloat {
        get { amount }
        set { amount = newValue }
    }

    func effectValue(size: CGSize) -> ProjectionTransform {
        ProjectionTransform(CGAffineTransform(translationX: 4 * sin(amount * .pi * 6), y: 0))
    }
}
