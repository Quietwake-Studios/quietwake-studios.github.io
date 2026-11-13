import Foundation

/// One theme in one language: its name, hidden words and the three groups of words.
public struct ThemeWords: Hashable, Sendable {
    public let name: String
    /// Board form (upper case), 4–9 letters.
    public let hidden: [String]
    public let common: [Word]
    public let crafted: [Word]
    public let rare: [Word]
    public let regional: [Word]

    public var all: [Word] { common + crafted + rare + regional }

    public func words(in group: WordGroup) -> [Word] {
        switch group {
        case .common: common
        case .crafted: crafted
        case .rare: rare
        case .regional: regional
        }
    }

    /// The first words of each list as the daily lock counted them; all of them without a lock.
    public func prefix(_ counts: DailyLock.Counts?) -> ThemeWords {
        guard let counts else { return self }
        return ThemeWords(name: name, hidden: Array(hidden.prefix(counts.hidden)), common: Array(common.prefix(counts.common)),
                          crafted: Array(crafted.prefix(counts.crafted)), rare: Array(rare.prefix(counts.rare)),
                          regional: Array(regional.prefix(counts.regional)))
    }

    init(name: String, hidden: [String], common: [Word], crafted: [Word], rare: [Word], regional: [Word]) {
        self.name = name
        self.hidden = hidden
        self.common = common
        self.crafted = crafted
        self.rare = rare
        self.regional = regional
    }
}

/// A theme: one JSON file in `Resources/Themes/` (its name is the id). Nothing else to register.
public struct WordTheme: Hashable, Sendable, Identifiable {
    public let id: String
    /// SF Symbol for cards and the album.
    public let symbol: String
    public let languages: [WordLanguage: ThemeWords]

    public func words(_ language: WordLanguage) -> ThemeWords? { languages[language] }
    public func name(_ language: WordLanguage) -> String { languages[language]?.name ?? id }
    /// Both languages written: only then it can join the daily challenge.
    public var isBilingual: Bool { languages.count == WordLanguage.allCases.count }

    public init(id: String, json: Data) throws {
        let raw = try JSONDecoder().decode(RawTheme.self, from: json)
        self.id = id
        symbol = raw.symbol
        var languages: [WordLanguage: ThemeWords] = [:]
        for (language, part) in [(WordLanguage.en, raw.en), (.es, raw.es)] {
            guard let part else { continue }
            func words(_ list: [String]?, _ group: WordGroup) -> [Word] {
                (list ?? []).map { Word(text: $0, language: language, group: group) }
            }
            languages[language] = ThemeWords(
                name: part.name,
                hidden: (part.hidden ?? []).map { WordText.normalize($0, language: language) },
                common: words(part.common, .common), crafted: words(part.crafted, .crafted), rare: words(part.rare, .rare),
                regional: words(part.regional, .regional))
        }
        self.languages = languages
    }

    private struct RawTheme: Decodable {
        struct Part: Decodable {
            let name: String
            let hidden: [String]?
            let common: [String]?
            let crafted: [String]?
            let rare: [String]?
            let regional: [String]?
        }
        let symbol: String
        let en: Part?
        let es: Part?
    }
}

/// Every theme in the package, sorted by id, plus the blocklists and the daily lock.
public struct ThemeLibrary: Sendable {
    public let themes: [WordTheme]
    public let blocklist: [WordLanguage: [String]]
    public let lock: DailyLock

    public static let shared: ThemeLibrary = {
        do { return try ThemeLibrary(bundle: .module) } catch { fatalError("Themes failed to load: \(error)") }
    }()

    public init(themes: [WordTheme], blocklist: [WordLanguage: [String]], lock: DailyLock) {
        self.themes = themes.sorted { $0.id < $1.id }
        self.blocklist = blocklist
        self.lock = lock
    }

    init(bundle: Bundle) throws {
        guard let folder = bundle.url(forResource: "Themes", withExtension: nil) else { throw CocoaError(.fileNoSuchFile) }
        let files = try FileManager.default.contentsOfDirectory(at: folder, includingPropertiesForKeys: nil)
        var themes: [WordTheme] = []
        var lock = DailyLock(entries: [])
        for file in files where file.pathExtension == "json" {
            let id = file.deletingPathExtension().lastPathComponent
            let data = try Data(contentsOf: file)
            if id == DailyLock.fileName {
                lock = try JSONDecoder().decode(DailyLock.self, from: data)
            } else {
                themes.append(try WordTheme(id: id, json: data))
            }
        }
        var blocklist: [WordLanguage: [String]] = [:]
        for language in WordLanguage.allCases {
            let url = bundle.url(forResource: "Blocklist/\(language.rawValue)", withExtension: "txt")
                ?? bundle.url(forResource: language.rawValue, withExtension: "txt", subdirectory: "Blocklist")
            let text = url.flatMap { try? String(contentsOf: $0, encoding: .utf8) } ?? ""
            blocklist[language] = text.split(separator: "\n").compactMap { line in
                let word = line.split(separator: "#", maxSplits: 1, omittingEmptySubsequences: false).first?.trimmingCharacters(in: .whitespaces) ?? ""
                return word.isEmpty ? nil : WordText.normalize(word, language: language)
            }
        }
        self.init(themes: themes, blocklist: blocklist, lock: lock)
    }

    public func theme(_ id: String) -> WordTheme? { themes.first { $0.id == id } }

    /// Themes with words in this language (free play).
    public func themes(for language: WordLanguage) -> [WordTheme] { themes.filter { $0.languages[language] != nil } }
}
