import Foundation
import QuietWordsCore
import SwiftData

/// A game in progress, stored as the encoded `GameSession`.
@Model
final class StoredGame {
    @Attribute(.unique) var slot: String
    var data: Data
    var updatedAt: Date

    init(slot: String, data: Data, updatedAt: Date = .now) {
        self.slot = slot
        self.data = data
        self.updatedAt = updatedAt
    }
}

/// One day's challenge in one language: its best result and how many attempts it had.
@Model
final class StoredDaily {
    /// "2026-11-08#es".
    @Attribute(.unique) var key: String
    var score: Int
    var isCompleted: Bool
    var finishedAt: Date?
    /// Time played when it was won (leaderboard tie-break).
    var seconds: Int?
    /// Attempts started; any after the first is a replay.
    var attempts: Int = 0

    init(key: String) {
        self.key = key
        score = 0
        isCompleted = false
    }
}

/// Lifetime progress and the album, stored as one encoded record.
@Model
final class StoredProfile {
    var data: Data

    init(data: Data) {
        self.data = data
    }
}

/// What's saved besides games: counters, achievements and every word found.
struct Profile: Codable, Equatable {
    var progress = PlayerProgress()
    var album = Album()

    func merged(with other: Profile) -> Profile {
        Profile(progress: progress.merged(with: other.progress), album: album.merged(with: other.album))
    }
}

/// Read-only snapshot of a daily attempt for the UI.
struct DailyRecord: Equatable, Codable {
    let score: Int
    let isCompleted: Bool
    var seconds: Int?
}

/// Day and language of a daily record ("2026-11-08#es").
struct DailyID: Hashable {
    let dayKey: String
    let language: WordLanguage

    var key: String { "\(dayKey)#\(language.rawValue)" }

    init(_ dayKey: String, _ language: WordLanguage) {
        self.dayKey = dayKey
        self.language = language
    }

    init?(key: String) {
        let parts = key.split(separator: "#")
        guard parts.count == 2, let language = WordLanguage(rawValue: String(parts[1])) else { return nil }
        self.init(String(parts[0]), language)
    }
}

/// Local storage. SwiftData is the source of truth; Game Center only mirrors it.
@MainActor
@Observable
final class PersistenceService {
    enum Slot: Equatable {
        case free
        /// The attempt in progress at one day's challenge in one language: always the latest,
        /// kept until it's won or leaves the replay window.
        case daily(DailyID)

        static let dailyPrefix = "daily-"

        var rawValue: String {
            switch self {
            case .free: "free"
            case .daily(let id): Self.dailyPrefix + id.key
            }
        }
    }

    /// "2026-11-08#es" → best result.
    private(set) var dailies: [String: DailyRecord] = [:]
    /// Day-and-language attempts saved halfway.
    private(set) var dailiesInProgress: Set<DailyID> = []

    let container: ModelContainer
    private var context: ModelContext { container.mainContext }

    enum Store {
        case standard
        case inMemory
        /// A separate file, so tests never touch the player's data.
        case named(String, reset: Bool)
    }

    convenience init(store: Store = .standard) {
        let configuration: ModelConfiguration
        // Local only. With iCloud entitlements SwiftData would turn on CloudKit by default,
        // which this model doesn't support; progress syncs through Game Center saved games.
        switch store {
        case .standard:
            configuration = ModelConfiguration(cloudKitDatabase: .none)
        case .inMemory:
            configuration = ModelConfiguration(isStoredInMemoryOnly: true, cloudKitDatabase: .none)
        case .named(let name, let reset):
            let url = URL.applicationSupportDirectory.appending(path: "\(name).store")
            if reset {
                for suffix in ["", "-shm", "-wal"] {
                    try? FileManager.default.removeItem(atPath: url.path(percentEncoded: false) + suffix)
                }
            }
            configuration = ModelConfiguration(url: url, cloudKitDatabase: .none)
        }
        try? FileManager.default.createDirectory(at: .applicationSupportDirectory, withIntermediateDirectories: true)
        do {
            try self.init(container: ModelContainer(for: StoredGame.self, StoredDaily.self, StoredProfile.self, configurations: configuration))
        } catch {
            fatalError("Couldn't open the local store: \(error)")
        }
    }

    init(container: ModelContainer) {
        self.container = container
        reloadDailies()
        reloadInProgress()
    }

    /// Days completed in either language: the streak and the calendar are one.
    var completedDays: Set<String> {
        Set(dailies.filter(\.value.isCompleted).keys.compactMap { DailyID(key: $0)?.dayKey })
    }

    func record(_ id: DailyID) -> DailyRecord? { dailies[id.key] }

    /// Days with an attempt halfway in either language (the calendar's dashed ring).
    var daysInProgress: Set<String> { Set(dailiesInProgress.map(\.dayKey)) }

    // MARK: Games in progress

    func loadGame(_ slot: Slot) -> GameSession? {
        guard let stored = storedGame(slot) else { return nil }
        return try? JSONDecoder().decode(GameSession.self, from: stored.data)
    }

    func saveGame(_ session: GameSession, in slot: Slot) {
        guard let data = try? JSONEncoder().encode(session) else { return }
        if let stored = storedGame(slot) {
            stored.data = data
            stored.updatedAt = .now
        } else {
            context.insert(StoredGame(slot: slot.rawValue, data: data))
        }
        save()
        if case .daily = slot { reloadInProgress() }
    }

    func deleteGame(_ slot: Slot) {
        guard let stored = storedGame(slot) else { return }
        context.delete(stored)
        save()
        if case .daily = slot { reloadInProgress() }
    }

    /// Drops daily games for days that can no longer be replayed.
    func deleteDailyGames(before oldestDayKey: String) {
        for game in dailyGames() {
            if let id = DailyID(key: String(game.slot.dropFirst(Slot.dailyPrefix.count))), id.dayKey < oldestDayKey {
                context.delete(game)
            }
        }
        save()
        reloadInProgress()
    }

    // MARK: Daily challenge

    func startAttempt(_ id: DailyID) {
        if let stored = storedDaily(id) {
            stored.attempts += 1
        } else {
            let new = StoredDaily(key: id.key)
            new.attempts = 1
            context.insert(new)
        }
        save()
        reloadDailies()
    }

    func attempts(_ id: DailyID) -> Int { storedDaily(id)?.attempts ?? 0 }

    /// A win: keeps the best of the day in that language (higher score, then faster).
    /// Returns whether it's that language's first win of the day.
    @discardableResult
    func recordWin(_ id: DailyID, score: Int, seconds: Int) -> Bool {
        let stored = storedDaily(id) ?? {
            let new = StoredDaily(key: id.key)
            context.insert(new)
            return new
        }()
        let isFirst = !stored.isCompleted
        if isFirst || score > stored.score || (score == stored.score && seconds < stored.seconds ?? .max) {
            stored.score = score
            stored.seconds = seconds
            stored.finishedAt = .now
        }
        stored.isCompleted = true
        save()
        reloadDailies()
        return isFirst
    }

    /// Adds daily results from another device, keeping the better result per day and language.
    func mergeDailies(_ remote: [String: DailyRecord]) {
        for (key, record) in remote {
            if let local = dailies[key], local.better(than: record) == local { continue }
            let stored = (try? context.fetch(FetchDescriptor<StoredDaily>(predicate: #Predicate { $0.key == key })).first) ?? {
                let new = StoredDaily(key: key)
                context.insert(new)
                return new
            }()
            stored.score = record.score
            stored.isCompleted = record.isCompleted
            stored.seconds = record.seconds
        }
        save()
        reloadDailies()
    }

    // MARK: Profile

    func loadProfile() -> Profile {
        guard let stored = try? context.fetch(FetchDescriptor<StoredProfile>()).first else { return Profile() }
        return (try? JSONDecoder().decode(Profile.self, from: stored.data)) ?? Profile()
    }

    func saveProfile(_ profile: Profile) {
        guard let data = try? JSONEncoder().encode(profile) else { return }
        if let stored = try? context.fetch(FetchDescriptor<StoredProfile>()).first {
            stored.data = data
        } else {
            context.insert(StoredProfile(data: data))
        }
        save()
    }

    // MARK: Reset

    func deleteEverything() {
        try? context.delete(model: StoredGame.self)
        try? context.delete(model: StoredDaily.self)
        try? context.delete(model: StoredProfile.self)
        save()
        reloadDailies()
        reloadInProgress()
    }

    // MARK: Private

    private func storedGame(_ slot: Slot) -> StoredGame? {
        let key = slot.rawValue
        return try? context.fetch(FetchDescriptor<StoredGame>(predicate: #Predicate { $0.slot == key })).first
    }

    private func storedDaily(_ id: DailyID) -> StoredDaily? {
        let key = id.key
        return try? context.fetch(FetchDescriptor<StoredDaily>(predicate: #Predicate { $0.key == key })).first
    }

    private func reloadDailies() {
        let all = (try? context.fetch(FetchDescriptor<StoredDaily>())) ?? []
        dailies = Dictionary(all.map { ($0.key, DailyRecord(score: $0.score, isCompleted: $0.isCompleted, seconds: $0.seconds)) },
                             uniquingKeysWith: { a, _ in a })
    }

    private func dailyGames() -> [StoredGame] {
        let prefix = Slot.dailyPrefix
        return (try? context.fetch(FetchDescriptor<StoredGame>(predicate: #Predicate { $0.slot.starts(with: prefix) }))) ?? []
    }

    private func reloadInProgress() {
        dailiesInProgress = Set(dailyGames().compactMap { DailyID(key: String($0.slot.dropFirst(Slot.dailyPrefix.count))) })
    }

    private func save() {
        try? context.save()
    }
}

extension DailyRecord {
    /// Completed beats started; then the higher score, then the faster time.
    func better(than other: DailyRecord) -> DailyRecord {
        func rank(_ record: DailyRecord) -> (Int, Int, Int) {
            (record.isCompleted ? 1 : 0, record.score, -(record.seconds ?? .max))
        }
        return rank(self) >= rank(other) ? self : other
    }
}
