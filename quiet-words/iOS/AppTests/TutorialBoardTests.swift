import QuietWordsCore
import Testing
@testable import Quiet_Words

/// The tutorial's hand-made boards spell what they say.
@MainActor
@Suite struct TutorialBoardTests {
    @Test func boardsSpellTheirWords() {
        for language in WordLanguage.allCases {
            let board = TutorialView.session(language).board
            #expect(board.letters.count == 49)
            for placement in board.placements {
                let letters = placement.cells(side: 7).map { board.letters[$0] }.joined()
                #expect(letters == placement.word.letters, "\(language) \(placement.word.text)")
            }
        }
    }
}
