/// One of the 8 straight directions a word can run.
public struct Direction: Hashable, Codable, Sendable {
    public let dr: Int
    public let dc: Int

    public static let east = Direction(dr: 0, dc: 1)
    public static let south = Direction(dr: 1, dc: 0)
    public static let southEast = Direction(dr: 1, dc: 1)
    public static let northEast = Direction(dr: -1, dc: 1)
    public static let west = Direction(dr: 0, dc: -1)
    public static let north = Direction(dr: -1, dc: 0)
    public static let northWest = Direction(dr: -1, dc: -1)
    public static let southWest = Direction(dr: 1, dc: -1)

    /// Fixed order: generation must not depend on anything but the seed.
    public static let all: [Direction] = [.east, .south, .southEast, .northEast, .west, .north, .northWest, .southWest]

    public var reversed: Direction { Direction(dr: -dr, dc: -dc) }
}

/// Board size, directions, word groups and hidden word length of each level.
public enum Level: String, Codable, CaseIterable, Sendable, Comparable {
    case gentle, medium, hard

    public static func < (lhs: Self, rhs: Self) -> Bool { lhs.order < rhs.order }
    private var order: Int { Self.allCases.firstIndex(of: self)! }

    public var side: Int {
        switch self {
        case .gentle: 9
        case .medium: 11
        case .hard: 12
        }
    }

    public var directions: [Direction] {
        switch self {
        case .gentle: [.east, .south, .southEast]
        case .medium: [.east, .south, .southEast, .northEast]
        case .hard: Direction.all
        }
    }

    public var groups: [WordGroup] {
        switch self {
        case .gentle: [.common]
        case .medium: [.common, .crafted, .regional]
        case .hard: [.common, .crafted, .rare, .regional]
        }
    }

    /// Letters of the hidden word. Sunday's daily asks for a long one (`longHidden`).
    public var hiddenLength: ClosedRange<Int> {
        switch self {
        case .gentle: 4...6
        case .medium: 5...7
        case .hard: 6...9
        }
    }

    /// Words a board usually has (it depends on crossings).
    public var typicalWords: Int {
        switch self {
        case .gentle: 14
        case .medium: 17
        case .hard: 19
        }
    }
}

/// A word on the board: where it starts and where it runs.
public struct Placement: Hashable, Codable, Sendable {
    public let word: Word
    public let row: Int
    public let column: Int
    public let direction: Direction

    public init(word: Word, row: Int, column: Int, direction: Direction) {
        self.word = word
        self.row = row
        self.column = column
        self.direction = direction
    }

    public func cells(side: Int) -> [Int] {
        (0..<word.count).map { (row + direction.dr * $0) * side + column + direction.dc * $0 }
    }
}

/// A finished board: every cell belongs to a word, except the cells of the hidden word.
public struct Board: Hashable, Codable, Sendable {
    public let side: Int
    public let language: WordLanguage
    /// One letter per cell, row by row.
    public let letters: [String]
    /// Sorted as shown in the list (alphabetical by the displayed text).
    public let placements: [Placement]
    /// Board form of the hidden word.
    public let hidden: String
    /// Cells left over for the hidden word, in reading order.
    public let hiddenCells: [Int]

    public init(side: Int, language: WordLanguage, letters: [String], placements: [Placement], hidden: String, hiddenCells: [Int]) {
        self.side = side
        self.language = language
        self.letters = letters
        self.placements = placements
        self.hidden = hidden
        self.hiddenCells = hiddenCells
    }

    public func letter(row: Int, column: Int) -> String { letters[row * side + column] }

    /// The placement a straight stroke from `start` to `end` spells (either way round).
    public func placement(from start: Int, to end: Int) -> Int? {
        placements.firstIndex { placement in
            let cells = placement.cells(side: side)
            return (cells.first == start && cells.last == end) || (cells.first == end && cells.last == start)
        }
    }

    /// The cells of a straight line in one of the 8 directions, or nil if it isn't one.
    public func line(from start: Int, to end: Int) -> [Int]? {
        let (r0, c0) = (start / side, start % side)
        let (r1, c1) = (end / side, end % side)
        let dr = r1 - r0, dc = c1 - c0
        guard dr == 0 || dc == 0 || abs(dr) == abs(dc) else { return nil }
        let steps = max(abs(dr), abs(dc))
        let sr = dr.signum(), sc = dc.signum()
        return (0...steps).map { (r0 + sr * $0) * side + c0 + sc * $0 }
    }
}
