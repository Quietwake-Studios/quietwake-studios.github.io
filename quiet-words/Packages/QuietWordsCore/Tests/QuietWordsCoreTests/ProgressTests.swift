import Testing
@testable import QuietWordsCore

@Suite struct ProgressTests {
    func summary(level: Level = .gentle, seconds: Int = 200, guessed: Bool = false, hints: Int = 0) -> GameSummary {
        GameSummary(score: 500, seconds: seconds, level: level, words: 10, wordPoints: 400, timeBonus: 0, hiddenGuessed: guessed,
                    hintsUsed: hints, language: .es, themeID: "ocean", foundLetters: [])
    }

    @Test func achievementsUnlockOnce() {
        var p = PlayerProgress()
        #expect(p.record(.won(summary(guessed: true), isFree: true)) == [.firstPuzzle, .inPlainSight])
        #expect(p.record(.won(summary(), isFree: true)).isEmpty)
        #expect(p.bestScore(.gentle, .es) == 500)
        #expect(p.record(.won(summary(level: .hard, seconds: 599), isFree: false)) == [.steadyHand])
        #expect(p.record(.wordsFound(99)) == [])
        #expect(p.record(.wordsFound(1)) == [.words100])
        #expect(p.record(.dailyCompleted(longestStreak: 7)) == [.habit])
        #expect(p.record(.bothLanguages) == [.bilingual])
        #expect(p.progress(of: .words1000) == 0.1)
    }

    @Test func mergeKeepsTheMost() {
        var a = PlayerProgress(), b = PlayerProgress()
        _ = a.record(.wordsFound(50))
        _ = b.record(.wordsFound(120))
        _ = a.record(.won(summary(), isFree: true))
        let merged = a.merged(with: b)
        #expect(merged.wordsFound == 120 && merged.unlocked.contains(.firstPuzzle) && merged.unlocked.contains(.words100))
    }

    @Test func albumCountsByBoardFormPerLanguage() {
        let ocean = ThemeLibrary.shared.theme("ocean")!
        var album = Album()
        #expect(album.add(["CORAL", "OLA", "NOTAWORD"], theme: "ocean", language: .es) == 3)
        #expect(album.add(["CORAL"], theme: "ocean", language: .es) == 0)
        #expect(album.count(ocean, .es).found == 2)          // only words still in the list count
        #expect(album.count(ocean, .en).found == 0)
        let all = ocean.words(.en)!.all.map(\.letters)
        album.add(all, theme: "ocean", language: .en)
        #expect(album.isComplete(ocean, .en) && !album.isComplete(ocean, .es))
        #expect(album.total(.en, in: .shared).badges == 1)
        var other = Album()
        other.add(["MAREA"], theme: "ocean", language: .es)
        #expect(album.merged(with: other).count(ocean, .es).found == 2)
    }
}
