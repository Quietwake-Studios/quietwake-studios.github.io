/// The family's store-review rule (2026-11-13): the system's review prompt is asked for once
/// per install, when the summary of the third finished game is closed (free or daily, won or
/// not). Never mid-game, never with a dialog of our own first. Closing a summary with "New
/// Game" counts the game but never asks over the fresh board: the prompt waits for the next
/// return to Home or the Daily Challenge. Pure value: `StoreReview` (QuietUI) keeps it in
/// UserDefaults, which "Reset progress" leaves alone.
public struct ReviewPrompt: Equatable, Sendable {
    /// Finished games before the prompt shows.
    public static let gamesBeforeAsking = 3

    public private(set) var finishedGames: Int
    public private(set) var asked: Bool

    public init(finishedGames: Int = 0, asked: Bool = false) {
        self.finishedGames = max(0, finishedGames)
        self.asked = asked
    }

    /// A finished game's summary was closed. `startsNewGame`: with "New Game", straight into
    /// a fresh board. True: ask now (only ever once).
    public mutating func summaryClosed(startsNewGame: Bool = false) -> Bool {
        guard !asked else { return false }
        finishedGames += 1
        return startsNewGame ? false : askIfDue()
    }

    /// Home or the Daily Challenge is on screen again. True: a prompt that waited (the third
    /// summary closed with "New Game") is asked now.
    public mutating func returnedToMenu() -> Bool {
        guard !asked else { return false }
        return askIfDue()
    }

    private mutating func askIfDue() -> Bool {
        guard finishedGames >= Self.gamesBeforeAsking else { return false }
        asked = true
        return true
    }
}
