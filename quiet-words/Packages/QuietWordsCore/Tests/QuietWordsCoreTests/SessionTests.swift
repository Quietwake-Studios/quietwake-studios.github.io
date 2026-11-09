import Testing
@testable import QuietWordsCore

@Suite struct SessionTests {
    func session(_ level: Level = .medium) -> GameSession {
        let words = ThemeLibrary.shared.theme("ocean")!.words(.en)!
        let board = Generator.board(key: "session", pool: words.all, hidden: words.hidden, language: .en, level: level,
                                    blocklist: ThemeLibrary.shared.blocklist[.en]!)!
        return GameSession(mode: .free, themeID: "ocean", level: level, board: board)
    }

    func ends(_ s: GameSession, _ i: Int) -> (Int, Int) {
        let cells = s.board.placements[i].cells(side: s.board.side)
        return (cells.first!, cells.last!)
    }

    @Test func findsAWordEitherWay() {
        var s = session()
        let (a, b) = ends(s, 0)
        let points = s.board.placements[0].word.count * 10
        #expect(s.select(from: b, to: a) == [.found(index: 0, points: points)])
        #expect(s.select(from: a, to: b) == [.alreadyFound(index: 0)])
        #expect(s.score == points)
    }

    @Test func missesAndBadLines() {
        var s = session()
        let side = s.board.side
        #expect(s.select(from: 0, to: 2 * side + 1) == nil)          // not straight
        #expect(s.select(from: 0, to: 0) == nil)
        let (a, b) = ends(s, 0)
        let shorter = s.board.line(from: a, to: b)!.dropLast().last!
        #expect(s.select(from: a, to: shorter) == [.miss])
        #expect(s.found.isEmpty)
    }

    @Test func hintsCostButNeverGoBelowZero() {
        var s = session()
        guard case .hint(let index, let cell) = s.hint() else { Issue.record("no hint"); return }
        #expect(cell == s.board.placements[index].cells(side: s.board.side).first)
        #expect(s.hintCells == [cell])
        #expect(s.score == 0)
        let (a, b) = ends(s, index)
        s.select(from: a, to: b)
        #expect(s.hintCells.isEmpty)
        #expect(s.score == s.board.placements[index].word.count * 10 - 30)
    }

    @Test func threeGuessesForTheHiddenWord() {
        var s = session()
        #expect(s.guess("nope") == .guessWrong(triesLeft: 2))
        #expect(s.guess(s.board.hidden.lowercased()) == .guessRight(points: 100))
        #expect(s.hiddenState == .guessed)
        #expect(s.guess("again") == nil)
        var t = session()
        for left in [2, 1, 0] { #expect(t.guess("x") == .guessWrong(triesLeft: left)) }
        #expect(!t.canGuess)
    }

    @Test func lastWordRevealsAndScores() {
        var s = session(.gentle)
        for _ in 0..<60 { s.tick() }                      // 1 min of a 3 min target
        var last: [GameSession.Event] = []
        for i in s.board.placements.indices {
            let (a, b) = ends(s, i)
            last = s.select(from: a, to: b)!
        }
        #expect(s.isWon && s.hiddenState == .revealed)
        let words = s.board.placements.reduce(0) { $0 + $1.word.count * 10 }
        let bonus = words * 50 * 120 / (180 * 100)
        #expect(s.timeBonus == bonus)
        #expect(s.score == words + bonus + 30)
        guard case .won(let summary) = last.last else { Issue.record("no win"); return }
        #expect(summary.score == s.score && !summary.hiddenGuessed && summary.foundLetters.count == s.board.placements.count)
        s.tick()
        #expect(s.elapsedSeconds == 60)
    }

    @Test func roundTripsThroughCodable() throws {
        var s = session()
        let (a, b) = ends(s, 1)
        s.select(from: a, to: b)
        s.hint()
        let data = try JSONEncoder().encode(s)
        #expect(try JSONDecoder().decode(GameSession.self, from: data) == s)
    }
}

import Foundation
