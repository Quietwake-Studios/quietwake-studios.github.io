import QuietCore

/// Builds dense boards: words cover every cell except the letters of the hidden word, which
/// fill the leftover cells in reading order. No random filler. Everything comes from
/// `SeededRandom`, so a key gives the same board on every device.
public enum Generator {
    /// Tries `"<key>#0"`, `"<key>#1"`… until a board passes every check.
    /// - Parameters:
    ///   - pool: candidate words; the order matters only through the seeded shuffle.
    ///   - hidden: candidate hidden words (board form).
    ///   - longHidden: Sunday's daily: only the longest hidden lengths of the level.
    public static func board(key: String, pool: [Word], hidden: [String], language: WordLanguage, level: Level,
                             longHidden: Bool = false, blocklist: [String], maxAttempts: Int = 600) -> Board? {
        var lengths = level.hiddenLength
        if longHidden { lengths = max(lengths.lowerBound, lengths.upperBound - 2)...lengths.upperBound }
        let hiddenOptions = hidden.filter { lengths.contains($0.count) && WordText.isBoardText($0, language: language) }
        guard !hiddenOptions.isEmpty else { return nil }
        let words = eligible(pool, level: level, language: language)
        let block = blocklist.map(Array.init)
        for attempt in 0..<maxAttempts {
            var rng = SeededRandom(seed: FNV1a.hash("\(key)#\(attempt)"))
            if let board = build(words: words, hiddenOptions: hiddenOptions, language: language, level: level, block: block, rng: &rng) {
                return board
            }
        }
        return nil
    }

    /// Words that can go on a board of this level: right group and length, board letters, no
    /// palindromes (they'd read twice), one copy of each.
    static func eligible(_ pool: [Word], level: Level, language: WordLanguage) -> [Word] {
        var seen = Set<String>()
        return pool.filter { word in
            level.groups.contains(word.group) && (3...level.side).contains(word.count)
                && WordText.isBoardText(word.letters, language: language)
                && String(word.letters.reversed()) != word.letters
                && seen.insert(word.letters).inserted
        }
    }

    private struct Placed {
        let word: Word
        let letters: [Character]
        let row: Int, column: Int
        let direction: Direction
    }

    private static func build(words: [Word], hiddenOptions: [String], language: WordLanguage, level: Level,
                              block: [[Character]], rng: inout SeededRandom) -> Board? {
        let side = level.side
        let directions = level.directions
        let hiddenLengths = Set(hiddenOptions.map(\.count))
        let minHidden = hiddenLengths.min()!
        var grid = [Character?](repeating: nil, count: side * side)
        var free = side * side
        var placed: [Placed] = []
        var directionUse = [Int](repeating: 0, count: directions.count)

        // Longest first (they're the hardest to fit), with jitter so boards vary.
        let order = shuffled(words, &rng)
            .map { ($0, $0.count + rng.nextInt(in: 0...3)) }
            .sorted { $0.1 > $1.1 }
            .map(\.0)

        for word in order {
            if hiddenLengths.contains(free) { break }
            // Side-long words can only run along a full row, column or diagonal: a few of
            // them turn the board into stacked rows of words.
            if word.count >= side - 1, placed.count(where: { $0.letters.count >= side - 1 }) >= 1 { continue }
            let letters = Array(word.letters)
            let reversed = String(letters.reversed())
            // A word inside another ("MAR" in "MARINO") would be found twice.
            if placed.contains(where: { p in
                let other = String(p.letters)
                return other.contains(word.letters) || word.letters.contains(other) || other.contains(reversed)
            }) { continue }

            // Best option per direction; then the least used direction that has one wins
            // (dense boards otherwise collapse into stacked rows of words).
            var bestPer = [[(row: Int, column: Int)]](repeating: [], count: directions.count)
            var scorePer = [Int](repeating: Int.min, count: directions.count)
            for row in 0..<side {
                for column in 0..<side {
                    for (d, direction) in directions.enumerated() {
                        let endRow = row + direction.dr * (letters.count - 1)
                        let endColumn = column + direction.dc * (letters.count - 1)
                        guard (0..<side).contains(endRow), (0..<side).contains(endColumn) else { continue }
                        var overlap = 0
                        var fits = true
                        for (i, letter) in letters.enumerated() {
                            if let existing = grid[(row + direction.dr * i) * side + column + direction.dc * i] {
                                if existing != letter { fits = false; break }
                                overlap += 1
                            }
                        }
                        guard fits else { continue }
                        let newCells = letters.count - overlap
                        let remaining = free - newCells
                        guard newCells > 0, remaining >= minHidden else { continue }
                        // Crossings make boards hard to scan; finishing (a hidden word fits the
                        // cells left) beats everything.
                        var score = overlap
                        if hiddenLengths.contains(remaining) { score += 1_000 }
                        if score > scorePer[d] {
                            scorePer[d] = score
                            bestPer[d] = [(row, column)]
                        } else if score == scorePer[d] {
                            bestPer[d].append((row, column))
                        }
                    }
                }
            }
            let usable = directions.indices.filter { !bestPer[$0].isEmpty }
            guard !usable.isEmpty else { continue }
            let finishing = usable.filter { scorePer[$0] >= 1_000 }
            let pool = finishing.isEmpty ? usable : finishing
            let fewest = pool.map { directionUse[$0] }.min()!
            let candidates = pool.filter { directionUse[$0] == fewest }
            let d = candidates.max { scorePer[$0] < scorePer[$1] }!
            let spots = bestPer[d]
            let spot = spots[rng.nextInt(in: 0...(spots.count - 1))]
            let best = [(row: spot.row, column: spot.column, direction: d)]
            guard !best.isEmpty else { continue }
            let choice = best[rng.nextInt(in: 0...(best.count - 1))]
            let direction = directions[choice.direction]
            for (i, letter) in letters.enumerated() {
                let cell = (choice.row + direction.dr * i) * side + choice.column + direction.dc * i
                if grid[cell] == nil { free -= 1 }
                grid[cell] = letter
            }
            directionUse[choice.direction] += 1
            placed.append(Placed(word: word, letters: letters, row: choice.row, column: choice.column, direction: direction))
        }

        guard hiddenLengths.contains(free) else { return nil }
        if level == .hard, placed.filter({ $0.word.group != .common }).count < 3 { return nil }
        let placedLetters = Set(placed.map { String($0.letters) })
        let options = hiddenOptions.filter { $0.count == free && !placedLetters.contains($0) }
        guard !options.isEmpty else { return nil }
        let hidden = options[rng.nextInt(in: 0...(options.count - 1))]

        // The hidden word in the leftover cells, in reading order.
        var hiddenCells: [Int] = []
        var hiddenLetters = Array(hidden).makeIterator()
        for cell in grid.indices where grid[cell] == nil {
            grid[cell] = hiddenLetters.next()
            hiddenCells.append(cell)
        }
        let letters = grid.map { $0! }
        guard valid(letters, side: side, placed: placed, block: block) else { return nil }

        let placements = placed
            .map { Placement(word: $0.word, row: $0.row, column: $0.column, direction: $0.direction) }
            .sorted { ($0.word.letters, $0.word.text) < ($1.word.letters, $1.word.text) }
        return Board(side: side, language: language, letters: letters.map(String.init), placements: placements,
                     hidden: hidden, hiddenCells: hiddenCells)
    }

    /// Every word reads exactly once in the 8 directions, and no blocked word reads anywhere
    /// unless it's inside a placed word along the same line (GLASS, CÁLCULO).
    private static func valid(_ letters: [Character], side: Int, placed: [Placed], block: [[Character]]) -> Bool {
        for word in placed where occurrences(of: word.letters, in: letters, side: side).count != 1 { return false }
        let lines = placed.map { p in
            (cells: Set((0..<p.letters.count).map { (p.row + p.direction.dr * $0) * side + p.column + p.direction.dc * $0 }), direction: p.direction)
        }
        for blocked in block {
            for hit in occurrences(of: blocked, in: letters, side: side) {
                let cells = Set(hit.cells)
                let inside = lines.contains { line in
                    (line.direction == hit.direction || line.direction == hit.direction.reversed) && cells.isSubset(of: line.cells)
                }
                if !inside { return false }
            }
        }
        return true
    }

    static func occurrences(of word: [Character], in letters: [Character], side: Int) -> [(cells: [Int], direction: Direction)] {
        guard let first = word.first else { return [] }
        var hits: [(cells: [Int], direction: Direction)] = []
        for start in letters.indices where letters[start] == first {
            let row = start / side, column = start % side
            for direction in Direction.all {
                let endRow = row + direction.dr * (word.count - 1)
                let endColumn = column + direction.dc * (word.count - 1)
                guard (0..<side).contains(endRow), (0..<side).contains(endColumn) else { continue }
                var cells: [Int] = []
                var match = true
                for (i, letter) in word.enumerated() {
                    let cell = (row + direction.dr * i) * side + column + direction.dc * i
                    if letters[cell] != letter { match = false; break }
                    cells.append(cell)
                }
                if match { hits.append((cells, direction)) }
            }
        }
        return hits
    }

    /// Fisher–Yates with our own generator (never `shuffled()` from the stdlib).
    static func shuffled<T>(_ items: [T], _ rng: inout SeededRandom) -> [T] {
        var items = items
        guard items.count > 1 else { return items }
        for i in stride(from: items.count - 1, to: 0, by: -1) {
            items.swapAt(i, rng.nextInt(in: 0...i))
        }
        return items
    }
}
