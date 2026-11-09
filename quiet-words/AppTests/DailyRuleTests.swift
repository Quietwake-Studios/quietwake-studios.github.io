import QuietCore
import QuietWordsCore
import SwiftData
import Testing
@testable import Quiet_Words

/// The family's daily rule, with one board and one leaderboard per language and one streak.
@MainActor
@Suite(.serialized) struct DailyRuleTests {
    let today = DailyKey.dayKey(for: .now)

    func makeApp(_ persistence: PersistenceService = PersistenceService(store: .inMemory)) -> AppModel {
        AppModel(persistence: persistence)
    }

    /// Finds every word of the game on screen.
    func win(_ app: AppModel) {
        let game = app.activeGame!
        for placement in game.board.placements {
            let cells = placement.cells(side: game.board.side)
            game.tap(cells.first!)
            game.tap(cells.last!)
        }
    }

    @Test func todayCountsOnceForBothLanguages() async {
        let app = makeApp()
        await app.playDaily(today, language: .en)
        win(app)
        #expect(app.isCompleted(today, .en))
        #expect(app.currentStreak == 1)
        #expect(app.progress.dailiesCompleted == 1)
        await app.playDaily(today, language: .es)
        win(app)
        #expect(app.isCompleted(today, .es))
        #expect(app.currentStreak == 1)
        #expect(app.progress.dailiesCompleted == 1)
        #expect(app.progress.unlocked.contains(.bilingual))
    }

    @Test func eachLanguageHasItsOwnBoard() async {
        let app = makeApp()
        await app.playDaily(today, language: .en)
        let en = app.activeGame!.board
        await app.playDaily(today, language: .es)
        let es = app.activeGame!.board
        #expect(en.language == .en && es.language == .es && en.letters != es.letters)
    }

    @Test func leavingKeepsTheLatestAttempt() async {
        let persistence = PersistenceService(store: .inMemory)
        let app = makeApp(persistence)
        await app.playDaily(today, language: .es)
        let first = app.activeGame!.board.placements[0].cells(side: app.activeGame!.board.side)
        app.activeGame!.tap(first.first!)
        app.activeGame!.tap(first.last!)
        app.leftGame()
        #expect(app.isInProgress(today, .es))
        // Start over: the next attempt starts clean and replaces the saved one.
        app.startOver(today, language: .es)
        await app.playDaily(today, language: .es)
        #expect(app.activeGame!.session.found.isEmpty)
        #expect(app.activeKind == .daily(DailyID(today, .es), isReplay: true))
        app.restart()
        let reopened = makeApp(persistence)
        await reopened.playDaily(today, language: .es)
        #expect(reopened.activeGame!.session.found.isEmpty)
    }

    @Test func aPastDayFillsTheStreak() async {
        let app = makeApp()
        let yesterday = DailyKey.dayKey(today, addingDays: -1)!
        await app.playDaily(today, language: .en)
        win(app)
        await app.playDaily(yesterday, language: .es)
        win(app)
        #expect(app.currentStreak == 2)
        #expect(app.progress.lateDays.contains(yesterday))
    }

    @Test func bestOfTheDayStays() async {
        let app = makeApp()
        await app.playDaily(today, language: .en)
        win(app)
        let best = app.persistence.record(DailyID(today, .en))!.score
        await app.playDaily(today, language: .en)
        app.activeGame!.requestHint()
        app.activeGame!.requestHint()
        win(app)
        #expect(app.persistence.record(DailyID(today, .en))!.score == best)
    }

    @Test func freeGameContinuesAfterRelaunch() async {
        let persistence = PersistenceService(store: .inMemory)
        let app = makeApp(persistence)
        await app.newFree(themeID: "space", level: .medium)
        let cells = app.activeGame!.board.placements[0].cells(side: 11)
        app.activeGame!.tap(cells.first!)
        app.activeGame!.tap(cells.last!)
        app.leftGame()
        let reopened = makeApp(persistence)
        #expect(reopened.freeInProgress?.found.count == 1)
        #expect(reopened.freeInProgress?.themeID == "space")
    }

    @Test func winningFillsTheAlbum() async {
        let app = makeApp()
        await app.newFree(themeID: "botany", level: .gentle)
        let count = app.activeGame!.board.placements.count
        win(app)
        let botany = app.library.theme("botany")!
        #expect(app.profile.album.count(botany, app.wordLanguage).found == count)
        #expect(app.newAlbumWords == count)
        #expect(app.progress.wordsFound == count)
        #expect(app.unlockedThisGame.contains(.firstPuzzle))
    }
}
