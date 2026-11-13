import QuietCore

/// A puzzle ready to play: its givens, its one solution and its level.
public struct Puzzle: Hashable, Codable, Sendable {
    public let givens: Grid
    public let solution: Grid
    public let difficulty: Difficulty
    /// The seed that made it (0 for hand-made ones).
    public let seed: UInt64

    public init(givens: Grid, solution: Grid, difficulty: Difficulty, seed: UInt64 = 0) {
        self.givens = givens
        self.solution = solution
        self.difficulty = difficulty
        self.seed = seed
    }

    public var clueCount: Int { givens.filledCount }
}

/// Seed → puzzle, the same on every device: only `SeededRandom`, never the system generator,
/// `shuffled()` or `Hasher`, and budgets counted in attempts rather than time.
public enum Generator {
    /// Removal stops at this many clues (scale of 2026-11-13).
    static func clueFloor(_ difficulty: Difficulty) -> Int {
        switch difficulty {
        case .easy: 38
        case .medium: 31
        case .hard: 28
        case .expert: 22
        }
    }

    /// Easy and medium are played with singles only. While more than 10 cells are empty, a
    /// player always has at least this many to choose from (counted by `LogicSolver.playSingles`).
    static func fewestMoves(_ difficulty: Difficulty) -> Int {
        switch difficulty {
        case .easy: 4
        case .medium: 2
        case .hard, .expert: 0
        }
    }

    /// An easy puzzle may need a single outside a box (on a line, or a naked single) only
    /// when nothing else is left, at most this many times.
    static let easyMediumSteps = 1

    /// Whether logic up to `difficulty` still solves `givens` within the level's limits: the
    /// rule clues are removed by.
    static func stillSolves(_ givens: Grid, _ difficulty: Difficulty) -> Bool {
        switch difficulty {
        case .easy, .medium:
            let play = LogicSolver.playSingles(givens)
            return play.solved && play.fewestMoves >= fewestMoves(difficulty)
                && (difficulty != .easy || play.mediumSteps <= easyMediumSteps)
        case .hard, .expert:
            return LogicSolver.solve(givens, upTo: difficulty).solved
        }
    }

    /// Whether `givens` is a puzzle of exactly `difficulty`: solvable within its limits and
    /// needing something of the level (medium: a single outside a box; hard and expert: one of
    /// their techniques). Uniqueness follows (logic only deduces); `attempt` confirms it.
    public static func fits(_ givens: Grid, _ difficulty: Difficulty) -> Bool {
        guard givens.filledCount >= clueFloor(difficulty), stillSolves(givens, difficulty) else { return false }
        switch difficulty {
        case .easy: return true
        case .medium: return LogicSolver.playSingles(givens).mediumSteps > 0
        case .hard, .expert: return LogicSolver.grade(givens) == difficulty
        }
    }

    /// One try from `seed`: a puzzle of exactly `difficulty`, or nil.
    public static func attempt(seed: UInt64, difficulty: Difficulty) -> Puzzle? {
        let (givens, solution) = carve(seed: seed, difficulty: difficulty)
        guard fits(givens, difficulty), DancingLinks.hasUniqueSolution(givens) else { return nil }
        return Puzzle(givens: givens, solution: solution, difficulty: difficulty, seed: seed)
    }

    /// A random full grid from `seed`, then clues removed in symmetric pairs (in a random
    /// order) while `stillSolves` holds. Logic only ever deduces, so a grid it completes has a
    /// single solution. Expert puzzles rarely appear with pairs alone: then the symmetry
    /// breaks, one cell at a time.
    public static func carve(seed: UInt64, difficulty: Difficulty) -> (givens: Grid, solution: Grid) {
        var random = SeededRandom(seed: seed)
        let solution = DancingLinks.randomSolution(using: &random)
        var order = Array(0...40)
        for i in stride(from: order.count - 1, to: 0, by: -1) {
            order.swapAt(i, random.nextInt(in: 0...i))
        }
        var givens = solution
        let floor = clueFloor(difficulty)
        for cell in order {
            let pair = cell == 40 ? [40] : [cell, 80 - cell]
            guard givens.filledCount - pair.count >= floor else { continue }
            var candidate = givens
            for c in pair { candidate[c] = 0 }
            if stillSolves(candidate, difficulty) {
                givens = candidate
            }
        }
        if difficulty == .expert, LogicSolver.grade(givens) != difficulty {
            for cell in order.flatMap({ $0 == 40 ? [40] : [$0, 80 - $0] }) where givens[cell] != 0 && givens.filledCount > floor {
                var candidate = givens
                candidate[cell] = 0
                if stillSolves(candidate, difficulty) {
                    givens = candidate
                    if LogicSolver.grade(givens) == difficulty { break }
                }
            }
        }
        return (givens, solution)
    }

    /// Tries `"<key>#0"`, `"<key>#1"`… and keeps the first puzzle of the wanted level. If
    /// none matches within `attempts`, the closest easier one found (never harder).
    public static func puzzle(key: String, difficulty: Difficulty, attempts: Int = 400) -> Puzzle {
        var fallback: Puzzle?
        for attempt in 0..<attempts {
            let seed = FNV1a.hash("\(key)#\(attempt)")
            if let puzzle = Generator.attempt(seed: seed, difficulty: difficulty) { return puzzle }
            if fallback == nil, difficulty > .easy {
                fallback = Generator.attempt(seed: seed, difficulty: Difficulty(rawValue: difficulty.rawValue - 1)!)
            }
        }
        return fallback ?? Generator.attempt(seed: FNV1a.hash(key), difficulty: .easy)!
    }
}
