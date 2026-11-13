import Foundation
import Observation
import QuietCore
import QuietUI
import QuietWordsCore
import UIKit

/// Which game is on screen.
enum GameKind: Equatable {
    case free
    /// A day's challenge in one language, today's or one of the last 30, saved per day and
    /// language (the family's daily rule). `isReplay`: not that day's first attempt.
    case daily(DailyID, isReplay: Bool)
}

/// Leaderboards: one daily per language (comparing times across lists isn't fair), the best
/// Hard score per language, and one streak.
enum Leaderboard: String, CaseIterable {
    case dailyEN = "qw_daily_en"
    case dailyES = "qw_daily_es"
    case bestHardEN = "qw_best_hard_en"
    case bestHardES = "qw_best_hard_es"
    case longestStreak = "qw_longest_streak"

    static func daily(_ language: WordLanguage) -> Leaderboard { language == .en ? .dailyEN : .dailyES }
    static func bestHard(_ language: WordLanguage) -> Leaderboard { language == .en ? .bestHardEN : .bestHardES }
}

/// Progress, album and daily results shared across the player's devices (Game Center saved game).
struct Snapshot: Codable {
    var profile: Profile
    var dailies: [String: DailyRecord]

    func merged(with other: Snapshot) -> Snapshot {
        var dailies = self.dailies
        for (key, record) in other.dailies {
            dailies[key] = dailies[key].map { $0.better(than: record) } ?? record
        }
        return Snapshot(profile: profile.merged(with: other.profile), dailies: dailies)
    }
}

/// App-wide state: the words' language, the active game, the saved free game, the daily
/// challenge, progress and the album.
@MainActor
@Observable
final class AppModel {
    let persistence: PersistenceService
    let gameCenter: GameCenterService
    let reminders: DailyReminders
    let library = ThemeLibrary.shared

    private(set) var activeGame: GameModel?
    private(set) var activeKind: GameKind?
    private(set) var profile: Profile
    /// Best score of the active game's level and language when it started ("New best").
    private(set) var bestAtStart = 0
    /// Album words of the active game's theme before it started (summary: "7 new").
    private(set) var albumAtStart = 0
    private(set) var unlockedThisGame: [Achievement] = []
    private(set) var pendingUnlocks: [Achievement] = []
    /// The saved free game, if one is in progress.
    private(set) var freeInProgress: GameSession?
    private(set) var isPreparing = false
    private var completedDailyThisGame = false
    var offersReminders = false

    /// The language of the words: chosen in the tutorial, switched with the EN · ES pill.
    /// Remembered; never deduced from the system after the first choice.
    var wordLanguage: WordLanguage {
        didSet { UserDefaults.standard.set(wordLanguage.rawValue, forKey: PreferenceKey.wordLanguage) }
    }

    private var dailyBoards: [String: Board] = [:]

    var progress: PlayerProgress { profile.progress }

    init(persistence: PersistenceService,
         gameCenter: GameCenterService = GameCenterService(subsystem: "com.tavoohoh.quietwords"),
         reminders: DailyReminders = .quietWords()) {
        self.persistence = persistence
        self.gameCenter = gameCenter
        self.reminders = reminders
        profile = persistence.loadProfile()
        wordLanguage = UserDefaults.standard.string(forKey: PreferenceKey.wordLanguage).flatMap(WordLanguage.init) ?? .systemSuggestion
        #if DEBUG
        let arguments = ProcessInfo.processInfo.arguments
        if arguments.contains("-resetStore") {
            UserDefaults.standard.removeObject(forKey: PreferenceKey.tutorialSeen)
            UserDefaults.standard.removeObject(forKey: PreferenceKey.wordLanguage)
            UserDefaults.standard.removeObject(forKey: PreferenceKey.appearanceMode)
            UserDefaults.standard.removeObject(forKey: PreferenceKey.cheapHints)
        }
        if arguments.contains("-unlockAll") {
            profile.progress = PlayerProgress(unlocked: Set(Achievement.allCases))
        }
        if let i = arguments.firstIndex(of: "-words"), i + 1 < arguments.count, let language = WordLanguage(rawValue: arguments[i + 1]) {
            wordLanguage = language
        }
        if DemoData.isActive {
            profile = DemoData.profile
            DemoData.seed(persistence, language: wordLanguage)
        }
        #endif
        freeInProgress = persistence.loadGame(.free).flatMap { $0.isWon ? nil : $0 }
        if let oldest = DailyKey.dayKey(todayKey, addingDays: -DailyKey.replayWindow) {
            persistence.deleteDailyGames(before: oldest)
        }
        gameCenter.onAuthenticated = { [weak self] in self?.syncGameCenter() }
        reminders.clock = { AppClock.now }
        reminders.state = { [weak self] in
            guard let self else { return (false, 0) }
            return (persistence.completedDays.contains(todayKey), currentStreak)
        }
    }

    /// The player chose a language at least once (the tutorial's first step).
    var hasChosenLanguage: Bool { UserDefaults.standard.string(forKey: PreferenceKey.wordLanguage) != nil }

    func chooseLanguage(_ language: WordLanguage) {
        wordLanguage = language
    }

    // MARK: Rewards

    func isUnlocked<R: Reward>(_ reward: R) -> Bool { reward.isUnlocked(in: progress) }

    /// Free play only: "In the Red" makes hints cheaper unless the player turned it off in
    /// Rewards (2026-11-09). Daily challenges, today's or past, always use `.standard` (the
    /// family's daily rule). Each game keeps the scoring it started with.
    var freeScoring: Scoring {
        let enabled = UserDefaults.standard.object(forKey: PreferenceKey.cheapHints) as? Bool ?? true
        return progress.unlocked.contains(.inTheRed) && enabled ? .cheapHints : .standard
    }

    // MARK: Achievements

    func record(_ event: ProgressEvent) {
        let unlocked = profile.progress.record(event)
        persistence.saveProfile(profile)
        pendingUnlocks += unlocked
        unlockedThisGame += unlocked
        if !unlocked.isEmpty {
            let percentages = achievementPercentages
            Task { await gameCenter.report(percentages) }
        }
    }

    func dismissUnlock(_ achievement: Achievement) {
        pendingUnlocks.removeAll { $0 == achievement }
    }

    private var achievementPercentages: [String: Double] {
        Dictionary(uniqueKeysWithValues: Achievement.allCases.map { ($0.gameCenterID, progress.progress(of: $0) * 100) })
    }

    // MARK: Reminders

    func refreshFriendsReminder() async {
        await reminders.refreshFriends {
            await self.gameCenter.friendsToday(dailyID: Leaderboard.daily(self.wordLanguage).rawValue)
        }
    }

    // MARK: Game Center

    /// On sign-in: pull what other devices saved, merge it into local data, then push everything.
    func syncGameCenter() {
        Task {
            if UserDefaults.standard.bool(forKey: PreferenceKey.pendingCloudReset) {
                guard await gameCenter.resetCloud() else { return }
                UserDefaults.standard.removeObject(forKey: PreferenceKey.pendingCloudReset)
            } else if let remote = await gameCenter.fetchSnapshot(Snapshot.self, merge: { $0.merged(with: $1) }) {
                profile = profile.merged(with: remote.profile)
                persistence.saveProfile(profile)
                persistence.mergeDailies(remote.dailies)
            }
            await pushToGameCenter()
        }
    }

    private func pushToGameCenter() async {
        await gameCenter.report(achievementPercentages)
        for language in WordLanguage.allCases {
            let best = progress.bestScore(.hard, language)
            if best > 0 { await gameCenter.submit(best, to: Leaderboard.bestHard(language).rawValue) }
            // Today's best in each language, after every attempt: the board keeps the most
            // recent score, so sending the best keeps the best there; the time breaks ties.
            if let today = persistence.record(DailyID(todayKey, language)), today.isCompleted {
                await gameCenter.submit(today.score, to: Leaderboard.daily(language).rawValue, always: true,
                                        context: GameCenterService.dailyContext(seconds: today.seconds))
            }
        }
        await gameCenter.submit(longestStreak, to: Leaderboard.longestStreak.rawValue)
        await gameCenter.save(Snapshot(profile: profile, dailies: persistence.dailies))
    }

    // MARK: Reset

    func resetProgress() {
        persistence.deleteEverything()
        profile = Profile()
        persistence.saveProfile(profile)
        activeGame = nil
        activeKind = nil
        pendingUnlocks = []
        freeInProgress = nil
        for key in PreferenceKey.rewards + [PreferenceKey.tutorialSeen] {
            UserDefaults.standard.removeObject(forKey: key)
        }
        UIApplication.shared.setAlternateIconName(nil)
        UserDefaults.standard.set(true, forKey: PreferenceKey.pendingCloudReset)
        syncGameCenter()
        Task { await reminders.reschedule() }
    }

    // MARK: Free play

    var lastLevel: Level {
        UserDefaults.standard.string(forKey: PreferenceKey.lastLevel).flatMap(Level.init) ?? .gentle
    }

    var lastThemeID: String? { UserDefaults.standard.string(forKey: PreferenceKey.lastTheme) }

    func continueFree() {
        guard let session = freeInProgress else { return }
        start(GameModel(session: session), as: .free)
    }

    /// A new board of the theme (nil: "Surprise", any theme) in the current language.
    func newFree(themeID: String?, level: Level) async {
        if let abandoned = freeInProgress { record(.abandoned(played: abandoned.hasPlayed)) }
        UserDefaults.standard.set(level.rawValue, forKey: PreferenceKey.lastLevel)
        let language = wordLanguage
        let themes = library.themes(for: language)
        guard !themes.isEmpty else { return }
        let id = themeID ?? themes[Int.random(in: 0..<themes.count)].id
        if themeID != nil { UserDefaults.standard.set(id, forKey: PreferenceKey.lastTheme) }
        guard let words = library.theme(id)?.words(language) else { return }
        isPreparing = true
        let key = "free-\(UInt64.random(in: .min ... .max))"
        let blocklist = library.blocklist[language] ?? []
        let board = await Task.detached(priority: .userInitiated) {
            Generator.board(key: key, pool: words.all, hidden: words.hidden, language: language, level: level, blocklist: blocklist)
        }.value
        isPreparing = false
        guard let board else { return }
        let session = GameSession(mode: .free, themeID: id, level: level, board: board, scoring: freeScoring)
        persistence.saveGame(session, in: .free)
        freeInProgress = session
        start(GameModel(session: session), as: .free)
    }

    /// The same board again from the start. A daily starts a new attempt, which replaces the saved one.
    func restart() {
        guard let game = activeGame, let kind = activeKind else { return }
        let old = game.session
        // Free play takes the current hint price; a daily is always `.standard`.
        let scoring = kind == .free ? freeScoring : .standard
        let session = GameSession(mode: old.mode, themeID: old.themeID, level: old.level, board: old.board, scoring: scoring)
        switch kind {
        case .free:
            persistence.saveGame(session, in: .free)
            freeInProgress = session
            start(GameModel(session: session), as: .free)
        case .daily(let id, _):
            startAttempt(session, id)
        }
    }

    // MARK: Tutorial

    /// Before the first game: the tutorial (its first step is the words' language).
    var needsTutorial: Bool {
        #if DEBUG
        let arguments = ProcessInfo.processInfo.arguments
        if arguments.contains("-uiTesting"), !arguments.contains("-tutorial") { return false }
        if DemoData.isActive { return false }
        #endif
        return !UserDefaults.standard.bool(forKey: PreferenceKey.tutorialSeen) || !hasChosenLanguage
    }

    func finishTutorial() {
        UserDefaults.standard.set(true, forKey: PreferenceKey.tutorialSeen)
        if !hasChosenLanguage { chooseLanguage(wordLanguage) }
    }

    // MARK: Daily

    var todayKey: String { DailyKey.dayKey(for: AppClock.now) }

    var currentStreak: Int { Streaks.current(completed: persistence.completedDays, today: todayKey) }
    var longestStreak: Int { Streaks.longest(completed: persistence.completedDays) }

    func theme(forDay dayKey: String) -> WordTheme? {
        DailyChallenge.themeID(forDayKey: dayKey, library: library).flatMap(library.theme)
    }

    func isCompleted(_ dayKey: String, _ language: WordLanguage) -> Bool {
        persistence.record(DailyID(dayKey, language))?.isCompleted == true
    }

    func isInProgress(_ dayKey: String, _ language: WordLanguage) -> Bool {
        persistence.dailiesInProgress.contains(DailyID(dayKey, language))
    }

    /// Resumes the attempt in progress, or starts a new one. The calendar asks "Continue /
    /// Start over" first (`startOver`).
    func playDaily(_ dayKey: String, language: WordLanguage) async {
        let id = DailyID(dayKey, language)
        if let saved = persistence.loadGame(.daily(id)), !saved.isWon {
            return start(GameModel(session: saved), as: dailyKind(id))
        }
        guard let board = await dailyBoard(id) else { return }
        let session = GameSession(mode: .daily(dayKey: dayKey), themeID: theme(forDay: dayKey)?.id ?? "",
                                  level: DailyChallenge.level(forDayKey: dayKey), board: board)
        startAttempt(session, id)
    }

    func startOver(_ dayKey: String, language: WordLanguage) {
        persistence.deleteGame(.daily(DailyID(dayKey, language)))
    }

    /// A new attempt, saved right away in its slot. The slot holds only the attempt in
    /// progress: never an earlier one (bug found in Quiet Numbers, 2026-11-02).
    private func startAttempt(_ session: GameSession, _ id: DailyID) {
        persistence.startAttempt(id)
        persistence.saveGame(session, in: .daily(id))
        start(GameModel(session: session), as: dailyKind(id))
    }

    private func dailyKind(_ id: DailyID) -> GameKind {
        .daily(id, isReplay: persistence.attempts(id) > 1)
    }

    private func dailyBoard(_ id: DailyID) async -> Board? {
        if let board = dailyBoards[id.key] { return board }
        isPreparing = true
        let library = library
        let board = await Task.detached(priority: .userInitiated) {
            DailyChallenge.board(forDayKey: id.dayKey, language: id.language, library: library)
        }.value
        isPreparing = false
        dailyBoards[id.key] = board
        return board
    }

    /// Prepares today's board in the current language so "Play" starts right away.
    func warmUpDaily() {
        let id = DailyID(todayKey, wordLanguage)
        guard dailyBoards[id.key] == nil else { return }
        let library = library
        Task {
            let board = await Task.detached(priority: .utility) {
                DailyChallenge.board(forDayKey: id.dayKey, language: id.language, library: library)
            }.value
            dailyBoards[id.key] = board
        }
    }

    // MARK: Active game

    func start(_ model: GameModel, as kind: GameKind) {
        model.onEvent = { [weak self] event in self?.handle(event) }
        model.onChange = { [weak self] in self?.sessionChanged() }
        let session = model.session
        bestAtStart = progress.bestScore(session.level, session.language)
        albumAtStart = library.theme(session.themeID).map { profile.album.count($0, session.language).found } ?? 0
        unlockedThisGame = []
        completedDailyThisGame = false
        activeGame = model
        activeKind = kind
    }

    private func handle(_ event: GameSession.Event) {
        switch event {
        case .found:
            record(.wordsFound(1))
        case .hint:
            if let score = activeGame?.session.score { record(.scoreChanged(score)) }
        case .won(let summary):
            let isFree = activeKind == .free
            record(.won(summary, isFree: isFree))
            if let theme = library.theme(summary.themeID) {
                let wasComplete = profile.album.isComplete(theme, summary.language)
                profile.album.add(summary.foundLetters, theme: theme.id, language: summary.language)
                persistence.saveProfile(profile)
                if !wasComplete, profile.album.isComplete(theme, summary.language) { record(.themeCompleted) }
            }
        default:
            break
        }
    }

    /// New words the active game added to its theme's album (the summary).
    var newAlbumWords: Int {
        guard let session = activeGame?.session, let theme = library.theme(session.themeID) else { return 0 }
        return profile.album.count(theme, session.language).found - albumAtStart
    }

    private func sessionChanged() {
        guard let game = activeGame, let kind = activeKind else { return }
        persistence.saveGame(game.session, in: slot(for: kind))
        if case .free = kind { freeInProgress = game.session.isWon ? nil : game.session }
        if game.session.isWon { finish(game.session, kind) }
    }

    func leftGame() {
        saveActiveGame()
        if completedDailyThisGame, reminders.shouldOffer { offersReminders = true }
        completedDailyThisGame = false
        Task { await pushToGameCenter() }
    }

    func saveActiveGame() {
        guard let game = activeGame, let kind = activeKind, !game.session.isWon else { return }
        persistence.saveGame(game.session, in: slot(for: kind))
        if case .free = kind { freeInProgress = game.session }
    }

    private func slot(for kind: GameKind) -> PersistenceService.Slot {
        switch kind {
        case .free: .free
        case .daily(let id, _): .daily(id)
        }
    }

    private func finish(_ session: GameSession, _ kind: GameKind) {
        switch kind {
        case .free:
            persistence.deleteGame(.free)
            freeInProgress = nil
        case .daily(let id, _):
            persistence.deleteGame(.daily(id))
            let dayWasDone = persistence.completedDays.contains(id.dayKey)
            if persistence.recordWin(id, score: session.score, seconds: session.elapsedSeconds) {
                // The streak counts the day once, whichever language came first.
                if !dayWasDone {
                    if id.dayKey != todayKey { record(.completedLate(dayKey: id.dayKey)) }
                    record(.dailyCompleted(longestStreak: longestStreak))
                    completedDailyThisGame = true
                }
                if WordLanguage.allCases.allSatisfy({ isCompleted(id.dayKey, $0) }) { record(.bothLanguages) }
            }
        }
        Task {
            await reminders.reschedule()
            await pushToGameCenter()
        }
    }
}
