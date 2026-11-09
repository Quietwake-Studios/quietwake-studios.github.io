import Testing
@testable import QuietWordsCore

@Suite struct WordsTests {
    @Test func normalizesForTheBoard() {
        #expect(WordText.normalize("Estrella de mar", language: .es) == "ESTRELLADEMAR")
        #expect(WordText.normalize("Pingüino", language: .es) == "PINGUINO")
        #expect(WordText.normalize("Ñandú", language: .es) == "ÑANDU")
        #expect(WordText.normalize("Café au lait", language: .en) == "CAFEAULAIT")
        #expect(WordText.normalize("Piña", language: .en) == "PINA")
        #expect(WordText.normalize("Pu-erh", language: .en) == "PUERH")
    }

    @Test func libraryLoadsEveryTheme() {
        let library = ThemeLibrary.shared
        #expect(library.themes.map(\.id) == ["biology", "botany", "coffee_tea", "food", "geography", "modern_age", "mountain", "music", "ocean", "renaissance_industry", "space"])
        for theme in library.themes {
            #expect(theme.isBilingual, "\(theme.id)")
            for language in WordLanguage.allCases {
                let words = theme.words(language)!
                #expect(words.all.count >= 200, "\(theme.id) \(language)")
                #expect(words.hidden.count >= 15, "\(theme.id) \(language)")
                #expect(words.all.allSatisfy { WordText.isBoardText($0.letters, language: language) && (3...12).contains($0.count) })
                #expect(words.regional.count <= 20)
            }
        }
        #expect(library.blocklist[.en]!.contains("FUCK"))
        #expect(library.blocklist[.es]!.contains("MIERDA"))
    }

    @Test func listKeepsTheSpelling() {
        let ocean = ThemeLibrary.shared.theme("ocean")!.words(.es)!
        let word = ocean.all.first { $0.text == "León marino" }!
        #expect(word.letters == "LEONMARINO")
        #expect(word.group == .common)
    }
}
