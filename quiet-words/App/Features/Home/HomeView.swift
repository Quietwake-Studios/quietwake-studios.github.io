import GameKit
import QuietCore
import QuietUI
import QuietWordsCore
import SwiftUI

struct HomeView: View {
    enum Route: Hashable {
        case game
        case newGame
        case daily
        case collection
        case album
        /// Collection on its Achievements tab (DEBUG `-open achievements`).
        case achievements
        case settings
        case leaderboards
        case tutorial
    }

    @Environment(AppModel.self) private var app
    @Environment(\.quietTheme) private var theme
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var path: [Route] = []

    var body: some View {
        @Bindable var app = app
        NavigationStack(path: $path) {
            ScrollView {
                VStack(spacing: 0) {
                    menu
                    VStack(spacing: 14) {
                        Image(.homeIcon)
                            .resizable()
                            .frame(width: 84, height: 84)
                            .clipShape(RoundedRectangle(cornerRadius: 84 * 0.225, style: .continuous))
                            .accessibilityHidden(true)
                        Text(verbatim: "Quiet Words")
                            .font(.system(.largeTitle, design: .rounded, weight: .heavy))
                            .foregroundStyle(theme.ink)
                            .accessibilityAddTraits(.isHeader)
                        Text("Your 5-minute break.")
                            .font(.body)
                            .foregroundStyle(theme.muted)
                    }
                    .padding(.top, 32)
                    VStack(spacing: 12) {
                        DailyCard(language: $app.wordLanguage) {
                            if app.needsTutorial { path.append(.tutorial) } else { path.append(.daily) }
                        }
                        .accessibilityIdentifier("daily")
                        if let saved = app.freeInProgress {
                            Button {
                                app.continueFree()
                                path.append(.game)
                            } label: {
                                Text("Continue · \(app.library.theme(saved.themeID)?.localizedName ?? "") · \(Text(saved.level.title))")
                                    .lineLimit(1)
                            }
                            .buttonStyle(CapsuleButtonStyle(isProminent: false))
                            .accessibilityIdentifier("continue")
                        }
                        Button {
                            path.append(app.needsTutorial ? .tutorial : .newGame)
                        } label: {
                            Text("New Game")
                        }
                        .buttonStyle(CapsuleButtonStyle())
                        .accessibilityIdentifier("newGame")
                    }
                    .padding(.top, 28)
                    stats
                        .padding(.top, 28)
                }
                .frame(maxWidth: Layout.buttons(sizeClass))
                .padding(.horizontal, 20)
                .padding(.bottom, 24)
                .frame(maxWidth: .infinity)
            }
            .background(theme.background)
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: Route.self) { route in
                switch route {
                case .game:
                    ActiveGameView(onNewGame: { path = [.newGame] }, onHome: { path = [] })
                case .newGame:
                    NewGameView { themeID, level in
                        Task {
                            await app.newFree(themeID: themeID, level: level)
                            path.append(.game)
                        }
                    }
                case .daily:
                    DailyChallengeView { dayKey, language in
                        Task {
                            await app.playDaily(dayKey, language: language)
                            path.append(.game)
                        }
                    }
                case .collection:
                    CollectionView(startsOn: .rewards)
                case .album:
                    CollectionView(startsOn: .album)
                case .achievements:
                    CollectionView(startsOn: .achievements)
                case .settings:
                    SettingsView { path.append(.tutorial) }
                case .leaderboards:
                    LeaderboardsView()
                case .tutorial:
                    TutorialView {
                        app.finishTutorial()
                        path = [.newGame]
                    }
                }
            }
            .onChange(of: app.gameCenter.isAuthenticated, initial: true) { updateAccessPoint() }
            .dailyReminderOffer(app.reminders, isPresented: $app.offersReminders)
            .onChange(of: path) { updateAccessPoint() }
            .onChange(of: app.wordLanguage) { app.warmUpDaily() }
        }
        #if DEBUG
        .onAppear(perform: startDebugGameIfRequested)
        #endif
    }

    /// Leaderboards · Collection · Settings, in a capsule at the top right.
    private var menu: some View {
        HStack {
            Spacer()
            HStack(spacing: 0) {
                menuButton("trophy", "Leaderboards", .leaderboards)
                menuButton("square.grid.2x2", "Collection", .collection)
                menuButton("gearshape", "Settings", .settings)
            }
            .padding(.horizontal, 6)
            .background { SurfaceShape(shape: Capsule()) }
        }
        .padding(.top, 8)
    }

    private func menuButton(_ image: String, _ label: LocalizedStringKey, _ route: Route) -> some View {
        Button { path.append(route) } label: {
            Image(systemName: image)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(theme.ink)
                .frame(width: 44, height: 44)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(label))
        .accessibilityIdentifier("\(route)")
    }

    /// Puzzles · Album (opens it) · Best streak.
    private var stats: some View {
        let album = app.profile.album.total(app.wordLanguage, in: app.library)
        return HStack {
            stat(Text(app.progress.gamesWon, format: .number), "Puzzles")
            Button { path.append(.album) } label: { stat(Text(album.found, format: .number), "Album") }
                .buttonStyle(.plain)
                .accessibilityHint(Text("Opens the album"))
                .accessibilityIdentifier("albumStat")
            stat(Text(app.longestStreak, format: .number), "Best streak")
        }
    }

    private func stat(_ value: Text, _ label: LocalizedStringKey) -> some View {
        VStack(spacing: 2) {
            value.font(.stat).foregroundStyle(theme.ink)
            Text(label).font(.footnote).foregroundStyle(theme.muted)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
    }

    private func updateAccessPoint() {
        GKAccessPoint.shared.location = .topLeading
        GKAccessPoint.shared.isActive = app.gameCenter.isAuthenticated && path.isEmpty
    }

    #if DEBUG
    /// `-startGame` opens a free game (`-seedKey <text>`, `-level gentle|medium|hard`,
    /// `-theme <id>`, `-words en|es`, `-nearlyWon` leaves one word, `-found <n>` finds n words,
    /// `-hint` lights a hint, `-won` finds the last word); `-open <daily|leaderboards|collection|album|achievements|settings|tutorial|newGame>`.
    private func startDebugGameIfRequested() {
        let arguments = ProcessInfo.processInfo.arguments
        func value(after flag: String) -> String? {
            guard let i = arguments.firstIndex(of: flag), i + 1 < arguments.count else { return nil }
            return arguments[i + 1]
        }
        if let name = value(after: "-open"), path.isEmpty {
            let routes: [String: Route] = ["daily": .daily, "leaderboards": .leaderboards, "collection": .collection, "album": .album, "achievements": .achievements,
                                           "settings": .settings, "tutorial": .tutorial, "newGame": .newGame]
            if let route = routes[name] { return path = [route] }
        }
        guard arguments.contains("-startGame"), path.isEmpty else { return }
        let level = value(after: "-level").flatMap(Level.init) ?? .medium
        let themeID = value(after: "-theme") ?? "ocean"
        let language = app.wordLanguage
        guard let words = app.library.theme(themeID)?.words(language),
              let board = Generator.board(key: value(after: "-seedKey") ?? "debug-\(UInt64.random(in: .min ... .max))", pool: words.all,
                                          hidden: words.hidden, language: language, level: level,
                                          blocklist: app.library.blocklist[language] ?? [])
        else { return }
        var found: [Int] = []
        if arguments.contains("-nearlyWon") { found = Array(board.placements.indices.dropLast()) }
        if let n = value(after: "-found").flatMap(Int.init) { found = Array(board.placements.indices.prefix(n)) }
        let session = GameSession(mode: .free, themeID: themeID, level: level, board: board, found: found, elapsedSeconds: 134)
        let model = GameModel(session: session)
        app.start(model, as: .free)
        if arguments.contains("-hint") { model.requestHint() }
        // `-won`: finds the last word (with `-nearlyWon`), for the summary.
        if arguments.contains("-won"), let last = board.placements.indices.first(where: { !session.isFound($0) }) {
            let cells = board.placements[last].cells(side: board.side)
            model.tap(cells.first!)
            model.tap(cells.last!)
        }
        path = [.game]
    }
    #endif
}

/// Today's challenge on Home: theme, level, the EN · ES pill (a check on a language done
/// today) and Play.
private struct DailyCard: View {
    @Binding var language: WordLanguage
    let onOpen: () -> Void

    @Environment(AppModel.self) private var app
    @Environment(\.quietTheme) private var theme

    var body: some View {
        let today = app.todayKey
        let done = Set(WordLanguage.allCases.filter { app.isCompleted(today, $0) })
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Daily challenge")
                        .font(.caption.weight(.bold))
                        .textCase(.uppercase)
                        .opacity(0.8)
                    Text("\(app.theme(forDay: today)?.localizedName ?? "") · \(Text(DailyChallenge.level(forDayKey: today).title))")
                        .font(.system(.title3, design: .rounded, weight: .heavy))
                    status(done)
                        .font(.footnote)
                        .opacity(0.85)
                }
                .accessibilityElement(children: .combine)
                Spacer(minLength: 8)
                VStack(alignment: .trailing, spacing: 8) {
                    HStack(spacing: 4) {
                        Image(systemName: "flame.fill")
                        Text(app.currentStreak, format: .number)
                    }
                    .font(.system(.title3, design: .rounded, weight: .heavy))
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(Text("Streak \(app.currentStreak)"))
                    LanguagePill(language: $language, done: done, onPrimary: true)
                }
            }
            Button(action: onOpen) {
                Text(done.contains(language) ? "Play again" : app.isInProgress(today, language) ? "Continue" : "Play")
                    .font(.headline)
                    .foregroundStyle(theme.primary)
                    .frame(maxWidth: .infinity, minHeight: 46)
                    .background(theme.onPrimary, in: Capsule())
            }
            .buttonStyle(.plain)
        }
        .foregroundStyle(theme.onPrimary)
        .padding(18)
        .background(theme.primary, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }

    private func status(_ done: Set<WordLanguage>) -> Text {
        if done.count == 2 { return Text("Done in both languages") }
        if let one = done.first, let other = WordLanguage.allCases.first(where: { $0 != one }) {
            return Text("\(Text(one.name)) done · \(Text(other.name)) to go")
        }
        return Text(language.wordsTitle)
    }
}

/// The game `AppModel` has on screen.
private struct ActiveGameView: View {
    @Environment(AppModel.self) private var app
    let onNewGame: () -> Void
    let onHome: () -> Void

    var body: some View {
        if let game = app.activeGame, let kind = app.activeKind {
            GameView(model: game, kind: kind, bestScore: app.bestAtStart, unlocked: app.unlockedThisGame,
                     newAlbumWords: app.newAlbumWords, onNewGame: onNewGame, onRestart: { app.restart() }, onHome: onHome)
                .id(ObjectIdentifier(game))
                .onDisappear { app.leftGame() }
                .overlay(alignment: .top) {
                    if let achievement = app.pendingUnlocks.first, !game.session.isWon {
                        UnlockBanner(achievement: achievement)
                            .id(achievement)
                            .transition(.move(edge: .top).combined(with: .opacity))
                            .task(id: achievement) {
                                try? await Task.sleep(for: .seconds(2.5))
                                withAnimation(.quiet) { app.dismissUnlock(achievement) }
                            }
                    }
                }
                .animation(.quiet, value: app.pendingUnlocks)
                .onChange(of: game.session.isWon) { _, won in
                    if won { app.pendingUnlocks.forEach(app.dismissUnlock) }
                }
        }
    }
}

/// "Achievement unlocked" during play.
private struct UnlockBanner: View {
    let achievement: Achievement
    @Environment(\.quietTheme) private var theme

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: achievement.systemImage)
                .foregroundStyle(theme.onPrimary)
                .frame(width: 36, height: 36)
                .background(theme.primary, in: Circle())
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text("Achievement unlocked").font(.caption).foregroundStyle(theme.muted)
                Text(achievement.title).font(.headline).foregroundStyle(theme.ink)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(theme.surface, in: Capsule())
        .shadow(color: .black.opacity(0.12), radius: 12, y: 4)
        .padding(.top, 8)
        .accessibilityElement(children: .combine)
    }
}
