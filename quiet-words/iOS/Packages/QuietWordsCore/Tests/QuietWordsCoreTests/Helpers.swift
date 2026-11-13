import QuietCore
@testable import QuietWordsCore

/// Checks every rule of a finished board; returns what's wrong (empty when valid).
func problems(_ board: Board, level: Level, blocklist: [String]) -> [String] {
    var issues: [String] = []
    let side = board.side
    if side != level.side { issues.append("side \(side)") }
    if board.letters.count != side * side { issues.append("cells") }
    var covered = Set<Int>()
    let letters = board.letters.map { Character($0) }
    for placement in board.placements {
        let cells = placement.cells(side: side)
        if !level.directions.contains(placement.direction) { issues.append("\(placement.word.letters) runs \(placement.direction)") }
        if String(cells.map { letters[$0] }) != placement.word.letters { issues.append("\(placement.word.letters) misspelled") }
        let hits = Generator.occurrences(of: Array(placement.word.letters), in: letters, side: side).count
        if hits != 1 { issues.append("\(placement.word.letters) reads \(hits) times") }
        covered.formUnion(cells)
    }
    let leftover = (0..<side * side).filter { !covered.contains($0) }
    if leftover != board.hiddenCells { issues.append("leftover cells aren't the hidden cells") }
    if String(leftover.map { letters[$0] }) != board.hidden { issues.append("leftover letters aren't \(board.hidden)") }
    if !level.hiddenLength.contains(board.hidden.count) { issues.append("hidden \(board.hidden) length") }
    let lines = board.placements.map { (Set($0.cells(side: side)), $0.direction) }
    for blocked in blocklist {
        for hit in Generator.occurrences(of: Array(blocked), in: letters, side: side) {
            let inside = lines.contains { ($0.1 == hit.direction || $0.1 == hit.direction.reversed) && Set(hit.cells).isSubset(of: $0.0) }
            if !inside { issues.append("blocked \(blocked)") }
        }
    }
    return issues
}

/// A short fingerprint of a board for reference values.
func fingerprint(_ board: Board) -> String {
    String(FNV1a.hash(board.letters.joined() + board.hidden), radix: 16)
}
