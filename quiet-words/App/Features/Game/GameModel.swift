import Observation
import QuietUI
import QuietWordsCore
import SwiftUI

/// The game on screen: wraps `GameSession` with what the board shows around it (the stroke
/// under the finger, the first tapped letter, the flash of a found word, the reveal).
@MainActor
@Observable
final class GameModel {
    /// A straight stroke from one letter to another, snapped to the 8 directions.
    struct Stroke: Equatable {
        let start: Int
        let end: Int
    }

    private(set) var session: GameSession
    /// The stroke while dragging.
    private(set) var stroke: Stroke?
    /// Tap mode: the first letter, waiting for the last.
    private(set) var firstTap: Int?
    /// The word just found, lit in the accent for a moment.
    private(set) var flashing: Int?
    /// Bumped on a wrong guess (the slots shake).
    private(set) var guessShake = 0
    /// End of game: how many leftover cells are lit so far, in reading order.
    private(set) var revealed = 0
    /// The summary can come up (after the reveal).
    private(set) var showsSummary = false

    /// Something that happened (achievements, album, saving).
    var onEvent: ((GameSession.Event) -> Void)?
    /// After every change, to save.
    var onChange: (() -> Void)?

    /// Tutorial: only this word can be found (nil = any).
    var allowedWord: Int?
    let isTutorial: Bool

    init(session: GameSession, isTutorial: Bool = false) {
        self.session = session
        self.isTutorial = isTutorial
        if session.isWon {
            revealed = session.board.hiddenCells.count
            showsSummary = true
        }
    }

    var board: Board { session.board }

    // MARK: Dragging

    /// The finger moved: a new snapped stroke. A soft tick per letter it grows or shrinks.
    func drag(to stroke: Stroke) {
        guard !session.isWon, stroke != self.stroke else { return }
        let length = board.line(from: stroke.start, to: stroke.end)?.count ?? 1
        let previous = self.stroke.flatMap { board.line(from: $0.start, to: $0.end)?.count } ?? 0
        self.stroke = stroke
        // A touch that hasn't moved may be the second tap: keep the first letter until it does.
        if stroke.start != stroke.end { firstTap = nil }
        if length != previous { FeedbackService.shared.play(.letter, step: length - 1) }
    }

    /// The finger lifted. A stroke of one letter is a tap.
    func endDrag() {
        guard let stroke else { return }
        self.stroke = nil
        if stroke.start == stroke.end { return tap(stroke.start) }
        commit(stroke.start, stroke.end)
    }

    func cancelDrag() { stroke = nil }

    // MARK: Tapping (and VoiceOver)

    /// First letter, then last. A second letter out of line becomes the new first.
    func tap(_ cell: Int) {
        guard !session.isWon else { return }
        guard let first = firstTap else {
            firstTap = cell
            return FeedbackService.shared.play(.letter)
        }
        if first == cell { firstTap = nil; return }
        guard board.line(from: first, to: cell) != nil else {
            firstTap = cell
            return FeedbackService.shared.play(.letter)
        }
        firstTap = nil
        commit(first, cell)
    }

    // MARK: Words

    private func commit(_ start: Int, _ end: Int) {
        if let allowedWord, board.placement(from: start, to: end) != allowedWord {
            return FeedbackService.shared.play(.invalid)
        }
        guard let events = session.select(from: start, to: end) else { return }
        handle(events)
    }

    func requestHint() {
        guard let event = session.hint() else { return }
        FeedbackService.shared.play(.hint)
        handle([event])
    }

    /// True if it was the hidden word.
    @discardableResult
    func guess(_ text: String) -> Bool {
        guard let event = session.guess(text) else { return false }
        handle([event])
        if case .guessRight = event { return true }
        return false
    }

    /// One second of play (the game screen ticks it while the app is active). Not saved on
    /// every tick: the next action, leaving the game or going to background saves it.
    func tick() {
        session.tick()
    }

    // MARK: Private

    private func handle(_ events: [GameSession.Event]) {
        for event in events {
            onEvent?(event)
            switch event {
            case .found(let index, _):
                FeedbackService.shared.play(.found)
                flash(index)
            case .alreadyFound(let index):
                flash(index)
            case .miss:
                break
            case .hint:
                break
            case .guessRight:
                FeedbackService.shared.play(.hiddenWord)
            case .guessWrong:
                FeedbackService.shared.play(.invalid)
                guessShake += 1
            case .won:
                FeedbackService.shared.play(.won)
                reveal()
            }
        }
        onChange?()
    }

    private func flash(_ index: Int) {
        withAnimation(.easeOut(duration: 0.12)) { flashing = index }
        Task {
            try? await Task.sleep(for: .milliseconds(350))
            if flashing == index { withAnimation(.easeIn(duration: 0.3)) { flashing = nil } }
        }
    }

    /// The leftover letters light up one by one in reading order, then the summary.
    private func reveal() {
        let count = board.hiddenCells.count
        let reduceMotion = UIAccessibility.isReduceMotionEnabled
        Task {
            try? await Task.sleep(for: .milliseconds(450))
            if reduceMotion {
                withAnimation(.easeInOut(duration: 0.15)) { revealed = count }
            } else {
                for step in 1...max(count, 1) {
                    withAnimation(.quiet) { revealed = step }
                    try? await Task.sleep(for: .milliseconds(Int(80 * EffectTiming.scale)))
                }
            }
            try? await Task.sleep(for: .milliseconds(reduceMotion ? 300 : 900))
            showsSummary = true
        }
    }

    func dismissSummary() { showsSummary = false }
}
