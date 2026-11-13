import Testing
@testable import QuietSudokuCore
import QuietCore

/// Every puzzle the generator gives has one solution and exactly the level it declares.
@Suite struct GradingTests {
    static let samples = 40

    @Test(arguments: Difficulty.allCases)
    func puzzlesAreUniqueAndGradedRight(_ difficulty: Difficulty) {
        for i in 0..<Self.samples {
            let puzzle = Generator.puzzle(key: "grading-\(i)", difficulty: difficulty)
            check(puzzle, difficulty)
        }
    }

    @Test func everyTechniqueIsUsedAndSound() {
        var used: Set<Technique> = []
        var i = 0
        while used.count < Technique.allCases.count, i < 3_000 {
            let level = Difficulty.allCases[i % 4]
            let (givens, solution) = Generator.carve(seed: FNV1a.hash("techniques-\(i)"), difficulty: level)
            for step in LogicSolver.solve(givens).steps {
                used.insert(step.technique)
                if let p = step.placement { #expect(solution[p.cell] == p.digit, "\(step.technique)") }
                for e in step.eliminations { #expect(solution[e.cell] != e.digit, "\(step.technique)") }
                #expect(step.placement != nil || !step.eliminations.isEmpty)
            }
            i += 1
        }
        #expect(used == Set(Technique.allCases), "never seen: \(Set(Technique.allCases).subtracting(used))")
    }

    @Test func techniqueLevelsFollowTheirOrder() {
        let levels = Technique.allCases.map(\.difficulty)
        #expect(levels == levels.sorted())
        #expect(Set(levels) == Set(Difficulty.allCases))
    }

    @Test func sameKeySamePuzzle() {
        for difficulty in Difficulty.allCases {
            #expect(Generator.puzzle(key: "same", difficulty: difficulty) == Generator.puzzle(key: "same", difficulty: difficulty))
        }
    }

    @Test func clueCountsByLevel() {
        for i in 0..<10 {
            #expect((38...40).contains(Generator.puzzle(key: "clues-\(i)", difficulty: .easy).clueCount))
            #expect(Generator.puzzle(key: "clues-\(i)", difficulty: .medium).clueCount >= 31)
            #expect(Generator.puzzle(key: "clues-\(i)", difficulty: .hard).clueCount >= 28)
            #expect(Generator.puzzle(key: "clues-\(i)", difficulty: .expert).clueCount >= 22)
        }
    }

    /// Easy and medium are singles-only, with moves to choose from (scale of 2026-11-13).
    @Test func easyAndMediumLeaveMovesToChoose() {
        for i in 0..<20 {
            let easy = LogicSolver.playSingles(Generator.puzzle(key: "moves-\(i)", difficulty: .easy).givens)
            #expect(easy.solved && easy.fewestMoves >= 4 && easy.mediumSteps <= 1)
            let medium = LogicSolver.playSingles(Generator.puzzle(key: "moves-\(i)", difficulty: .medium).givens)
            #expect(medium.solved && medium.fewestMoves >= 2 && medium.mediumSteps >= 1)
        }
    }

    @Test func splitHiddenSingles() {
        #expect(Technique.hiddenSingleBox.difficulty == .easy)
        #expect(Technique.hiddenSingleLine.difficulty == .medium)
        #expect(Technique.nakedSingle.difficulty == .medium)
        #expect(Technique.pointing.difficulty == .hard)
        #expect(Technique.xWing.difficulty == .expert)
        #expect(Technique.allCases.filter(\.isSingle) == [.fullHouse, .hiddenSingleBox, .hiddenSingleLine, .nakedSingle])
    }

    @Test func symmetricPairsBelowExpert() {
        for difficulty in [Difficulty.easy, .medium, .hard] {
            let givens = Generator.puzzle(key: "symmetry", difficulty: difficulty).givens
            #expect((0..<81).allSatisfy { (givens[$0] == 0) == (givens[80 - $0] == 0) })
        }
    }
}

/// Unique (two independent solvers agree), solvable by logic within its level's limits, and
/// needing at least one step of its level. Easy and medium are both singles-only (scale of
/// 2026-11-13): medium needs a single outside a box at least once, which easy allows at most
/// once, with fewer clues and fewer moves to choose from.
func check(_ puzzle: Puzzle, _ difficulty: Difficulty, sourceLocation: SourceLocation = #_sourceLocation) {
    #expect(puzzle.difficulty == difficulty, sourceLocation: sourceLocation)
    #expect(puzzle.solution.isSolved, sourceLocation: sourceLocation)
    #expect((0..<81).allSatisfy { puzzle.givens[$0] == 0 || puzzle.givens[$0] == puzzle.solution[$0] }, sourceLocation: sourceLocation)
    #expect(DancingLinks.solutions(of: puzzle.givens) == [puzzle.solution], sourceLocation: sourceLocation)
    #expect(Backtracker.countSolutions(puzzle.givens) == 1, sourceLocation: sourceLocation)
    // Easy may need one single outside a box (all singles are medium techniques).
    let solved = LogicSolver.solve(puzzle.givens, upTo: max(difficulty, .medium))
    #expect(solved.solved && solved.grid == puzzle.solution, sourceLocation: sourceLocation)
    #expect(Generator.fits(puzzle.givens, difficulty), sourceLocation: sourceLocation)
    let play = LogicSolver.playSingles(puzzle.givens)
    switch difficulty {
    case .easy:
        #expect(play.solved && play.fewestMoves >= 4 && play.mediumSteps <= 1, sourceLocation: sourceLocation)
        #expect((38...40).contains(puzzle.clueCount), sourceLocation: sourceLocation)
    case .medium:
        // A step of its level: somewhere only a line or a naked single moves on.
        #expect(play.solved && play.fewestMoves >= 2 && play.mediumSteps >= 1, sourceLocation: sourceLocation)
        #expect(!LogicSolver.solve(puzzle.givens, upTo: .easy).solved, sourceLocation: sourceLocation)
    case .hard, .expert:
        // A technique of its level: with the level below, logic gets stuck.
        #expect(solved.hardest?.difficulty == difficulty, sourceLocation: sourceLocation)
        let easier = Difficulty(rawValue: difficulty.rawValue - 1)!
        #expect(!LogicSolver.solve(puzzle.givens, upTo: easier).solved, "\(puzzle.givens.string) solvable at \(easier)", sourceLocation: sourceLocation)
    }
}
