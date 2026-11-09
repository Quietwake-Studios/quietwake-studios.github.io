import QuietUI
import QuietWordsCore
import SwiftUI

/// Cell math for a square board of `side` letters drawn `size` points wide.
struct BoardGeometry {
    let side: Int
    let size: CGFloat

    var cell: CGFloat { size / CGFloat(side) }

    func center(_ index: Int) -> CGPoint {
        CGPoint(x: (CGFloat(index % side) + 0.5) * cell, y: (CGFloat(index / side) + 0.5) * cell)
    }

    /// The cell under a point, clamped to the board.
    func index(at point: CGPoint) -> Int {
        let column = min(max(Int(point.x / cell), 0), side - 1)
        let row = min(max(Int(point.y / cell), 0), side - 1)
        return row * side + column
    }

    /// The stroke from `start` towards the finger: the nearest of the 8 directions (45°
    /// sectors), as many letters as the finger has gone, never off the board.
    func snap(from start: Int, to point: CGPoint) -> Int {
        let origin = center(start)
        let dx = point.x - origin.x, dy = point.y - origin.y
        let distance = (dx * dx + dy * dy).squareRoot()
        guard distance > cell * 0.4 else { return start }
        let sector = Int((atan2(dy, dx) / (.pi / 4)).rounded())
        let angle = CGFloat(sector) * .pi / 4
        let dc = Int(cos(angle).rounded()), dr = Int(sin(angle).rounded())
        let step = cell * ((dr != 0 && dc != 0) ? 2.squareRoot() : 1)
        var steps = Int((distance / step).rounded())
        let row = start / side, column = start % side
        while steps > 0 {
            let r = row + dr * steps, c = column + dc * steps
            if (0..<side).contains(r), (0..<side).contains(c) { return r * side + c }
            steps -= 1
        }
        return start
    }
}

/// The letter grid with the marker layer under it: found words in the player's marker, the
/// stroke under the finger, hints and, at the end, the leftover letters of the hidden word.
struct BoardView: View {
    let model: GameModel
    let size: CGFloat

    @Environment(\.quietTheme) private var theme
    @Environment(\.appearance) private var appearance
    @Environment(\.colorScheme) private var colorScheme
    @State private var dragStart: Int?

    private var board: Board { model.board }
    private var geometry: BoardGeometry { BoardGeometry(side: board.side, size: size) }

    var body: some View {
        ZStack(alignment: .topLeading) {
            markers
            letters
        }
        .frame(width: size, height: size)
        .contentShape(Rectangle())
        .gesture(drag)
        .accessibilityElement(children: .contain)
        .accessibilityLabel(Text("Board"))
    }

    // MARK: Layers

    private var markers: some View {
        Canvas { context, _ in
            let found = model.session.found
            for index in found {
                let style: Marker.Ink = index == model.flashing ? .flash : .found
                draw(board.placements[index].cells(side: board.side), ink: style, in: &context)
            }
            if let flashing = model.flashing, !found.contains(flashing) {
                draw(board.placements[flashing].cells(side: board.side), ink: .flash, in: &context)
            }
            if let stroke = model.stroke, let cells = board.line(from: stroke.start, to: stroke.end) {
                draw(cells, ink: .stroke, in: &context)
            }
            if let first = model.firstTap {
                let r = geometry.cell * 0.4
                let c = geometry.center(first)
                let circle = Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r))
                context.fill(circle, with: .color(theme.primary.opacity(0.14)))
                context.stroke(circle, with: .color(theme.primary), lineWidth: 2)
            }
            for cell in model.session.hintCells {
                let r = geometry.cell * 0.44
                let c = geometry.center(cell)
                context.stroke(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)),
                               with: .color(theme.accent), style: StrokeStyle(lineWidth: 2, dash: [3, 3]))
            }
            for cell in board.hiddenCells.prefix(model.revealed) {
                let inset = geometry.cell * 0.08
                let c = geometry.center(cell)
                let side = geometry.cell - 2 * inset
                context.fill(Path(roundedRect: CGRect(x: c.x - side / 2, y: c.y - side / 2, width: side, height: side), cornerRadius: side * 0.24),
                             with: .color(theme.accent.opacity(0.16)))
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func draw(_ cells: [Int], ink: Marker.Ink, in context: inout GraphicsContext) {
        guard let first = cells.first, let last = cells.last else { return }
        let a = geometry.center(first), b = geometry.center(last)
        let height = geometry.cell * 0.8
        let length = hypot(b.x - a.x, b.y - a.y) + height
        let rect = CGRect(x: -length / 2, y: -height / 2, width: length, height: height)
        var local = context
        local.translateBy(x: (a.x + b.x) / 2, y: (a.y + b.y) / 2)
        local.rotate(by: .radians(atan2(b.y - a.y, b.x - a.x)))
        let shape = Path(roundedRect: rect, cornerRadius: height / 2)
        let dark = colorScheme == .dark
        switch ink {
        case .stroke:
            local.fill(shape, with: .color(theme.primary.opacity(0.3)))
            local.stroke(shape, with: .color(theme.primary), lineWidth: 2)
        case .flash:
            local.fill(shape, with: .color(theme.accent.opacity(0.26)))
        case .found:
            appearance.marker.draw(shape, rect: rect, theme: theme, dark: dark, in: &local)
        }
    }

    private var letters: some View {
        let cell = geometry.cell
        let fontSize = cell * (appearance.largerLetters ? 0.6 : 0.5)
        let hidden = Set(board.hiddenCells.prefix(model.revealed))
        return ZStack { ForEach(0..<board.letters.count, id: \.self) { index in
            Text(verbatim: board.letters[index])
                .font(appearance.boardFont.font(size: fontSize))
                .foregroundStyle(hidden.contains(index) ? theme.accent : theme.ink)
                .frame(width: cell, height: cell)
                // `position`, not `offset`: VoiceOver and UI tests use the real frame.
                .position(geometry.center(index))
                .accessibilityElement()
                .accessibilityLabel(accessibilityText(index))
                .accessibilityAddTraits(model.firstTap == index ? [.isButton, .isSelected] : .isButton)
                .accessibilityAction { model.tap(index) }
                .accessibilityIdentifier("cell-\(index)")
                #if DEBUG
                .accessibilityValue(Text(verbatim: Self.answerMark(index, model.session)))
                #endif
        } }
        .frame(width: size, height: size)
    }

    #if DEBUG
    /// UI tests (`-uiTesting`): "start" / "end" on the first word not found yet.
    static func answerMark(_ index: Int, _ session: GameSession) -> String {
        guard ProcessInfo.processInfo.arguments.contains("-uiTesting"),
              let next = session.board.placements.indices.first(where: { !session.isFound($0) })
        else { return "" }
        let cells = session.board.placements[next].cells(side: session.board.side)
        return index == cells.first ? "start" : index == cells.last ? "end" : ""
    }
    #endif

    /// "S, row 3, column 5", plus what it's part of.
    private func accessibilityText(_ index: Int) -> Text {
        let row = index / board.side + 1, column = index % board.side + 1
        let letter = board.letters[index]
        if model.firstTap == index {
            return Text("\(letter), row \(row), column \(column), first letter")
        }
        if model.session.foundCells.contains(index) {
            return Text("\(letter), row \(row), column \(column), in a found word")
        }
        return Text("\(letter), row \(row), column \(column)")
    }

    // MARK: Gesture

    /// One gesture for both ways of playing: a drag draws the stroke; a touch that doesn't
    /// move is a tap (first letter, then last).
    private var drag: some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .local)
            .onChanged { value in
                let start = dragStart ?? geometry.index(at: value.startLocation)
                dragStart = start
                model.drag(to: .init(start: start, end: geometry.snap(from: start, to: value.location)))
            }
            .onEnded { _ in
                dragStart = nil
                model.endDrag()
            }
    }
}

/// The marker a found word stays in (a cosmetic reward).
enum Marker: String, CaseIterable, Codable {
    case highlighter, pencil, ink, underline

    enum Ink { case found, flash, stroke }

    func draw(_ shape: Path, rect: CGRect, theme: Theme, dark: Bool, in context: inout GraphicsContext) {
        switch self {
        case .highlighter:
            context.fill(shape, with: .color(theme.primary.opacity(dark ? 0.24 : 0.18)))
        case .pencil:
            context.stroke(shape, with: .color(theme.ink.opacity(0.55)), lineWidth: 1.5)
        case .ink:
            context.fill(shape, with: .color(theme.primary.opacity(0.1)))
            context.stroke(shape, with: .color(theme.primary.opacity(0.75)), lineWidth: 2)
        case .underline:
            var line = Path()
            line.move(to: CGPoint(x: rect.minX + rect.height * 0.3, y: rect.maxY - 1.5))
            line.addLine(to: CGPoint(x: rect.maxX - rect.height * 0.3, y: rect.maxY - 1.5))
            context.stroke(line, with: .color(theme.primary), style: StrokeStyle(lineWidth: 3, lineCap: .round))
        }
    }
}
