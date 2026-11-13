import Foundation

/// The language of the words (not of the app): chosen in the tutorial, switched with the
/// EN · ES pill. Each one has its own lists, album and daily leaderboard.
public enum WordLanguage: String, Codable, CaseIterable, Sendable, Comparable {
    case en, es

    public static func < (lhs: Self, rhs: Self) -> Bool { lhs.rawValue < rhs.rawValue }

    /// Board letters, in the order of a reading of the alphabet.
    public var alphabet: [Character] {
        let latin = Array("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
        return self == .es ? latin + ["Ñ"] : latin
    }
}

/// Text as it goes on the board: upper case, no accents or diaeresis (Á→A, Ü→U), no spaces,
/// hyphens or apostrophes. Ñ stays in Spanish.
public enum WordText {
    public static func normalize(_ text: String, language: WordLanguage) -> String {
        var out = ""
        for character in text.uppercased() {
            if character == "Ñ", language == .es {
                out.append("Ñ")
                continue
            }
            for scalar in String(character).decomposedStringWithCanonicalMapping.unicodeScalars {
                if scalar.properties.generalCategory == .nonspacingMark { continue }
                if scalar == " " || scalar == "-" || scalar == "'" || scalar == "’" { continue }
                out.unicodeScalars.append(scalar)
            }
        }
        return out
    }

    /// Only board letters (A–Z, plus Ñ in Spanish).
    public static func isBoardText(_ text: String, language: WordLanguage) -> Bool {
        let allowed = Set(language.alphabet)
        return !text.isEmpty && text.allSatisfy(allowed.contains)
    }
}

/// A word of a theme: as shown in the list ("Estrella de mar") and as it goes on the board
/// ("ESTRELLADEMAR"). The album keeps the board form, so lists can be reordered or grown.
public struct Word: Hashable, Codable, Sendable {
    public let text: String
    public let letters: String
    public let group: WordGroup

    public init(text: String, language: WordLanguage, group: WordGroup) {
        self.text = text
        self.letters = WordText.normalize(text, language: language)
        self.group = group
    }

    public var count: Int { letters.count }
}

/// How well known a word is. Gentle boards use common words; Medium adds crafted and
/// regional ones; Hard uses all four.
public enum WordGroup: String, Codable, CaseIterable, Sendable {
    case common, crafted, rare
    /// Words of one region or country ("Pandebono", "Cafezinho"), at most 20 per theme and
    /// language: the lists balance regions instead of avoiding them (2026-11-08).
    case regional
}
