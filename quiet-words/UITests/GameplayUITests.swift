import XCTest

/// Plays with the finger: drag, tap first and last letter, guessing, and the tutorial.
@MainActor
final class GameplayUITests: XCTestCase {
    override func setUp() async throws { continueAfterFailure = false }

    private func launch(_ arguments: [String]) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-uiTesting", "-resetStore"] + arguments
        app.launch()
        return app
    }

    private func cell(_ app: XCUIApplication, value: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH 'cell-' AND value == %@", value)).firstMatch
    }

    private func drag(_ app: XCUIApplication) {
        let start = cell(app, value: "start"), end = cell(app, value: "end")
        XCTAssertTrue(start.waitForExistence(timeout: 5))
        start.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
            .press(forDuration: 0.05, thenDragTo: end.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)))
    }

    /// A whole game by dragging: every word, then the summary.
    func testDragEveryWordToWin() throws {
        let app = launch(["-startGame", "-seedKey", "ui-drag", "-level", "gentle", "-words", "en"])
        let score = app.staticTexts["score"]
        XCTAssertTrue(score.waitForExistence(timeout: 10))
        // Every word, until none is left to find.
        var words = 0
        while words < 40, cell(app, value: "start").waitForExistence(timeout: 2) {
            drag(app)
            words += 1
        }
        XCTAssertGreaterThan(words, 8)
        XCTAssertTrue(app.buttons["summaryNewGame"].waitForExistence(timeout: 8))
        XCTAssertTrue(app.staticTexts["summaryScore"].exists)
    }

    /// First letter, then last letter.
    func testTapFirstAndLast() throws {
        let app = launch(["-startGame", "-seedKey", "ui-tap", "-level", "medium", "-words", "es"])
        XCTAssertTrue(app.staticTexts["score"].waitForExistence(timeout: 10))
        let before = app.staticTexts["score"].label
        cell(app, value: "start").tap()
        cell(app, value: "end").tap()
        let deadline = Date().addingTimeInterval(3)
        while app.staticTexts["score"].label == before, Date() < deadline { usleep(100_000) }
        XCTAssertNotEqual(app.staticTexts["score"].label, before)
    }

    /// A wrong guess keeps the game going; the right one fills the hidden word.
    func testGuessTheHiddenWord() throws {
        let app = launch(["-startGame", "-seedKey", "ui-guess", "-level", "gentle", "-words", "es"])
        let hidden = app.descendants(matching: .any)["hiddenWord"]
        XCTAssertTrue(hidden.waitForExistence(timeout: 10))
        let answer = hidden.value as? String ?? ""
        XCTAssertFalse(answer.isEmpty)
        app.buttons["guess"].tap()
        let field = app.textFields["guessField"]
        XCTAssertTrue(field.waitForExistence(timeout: 3))
        field.typeText(String(repeating: "Z", count: answer.count))
        sleep(1)
        field.typeText(answer)
        XCTAssertTrue(app.buttons["guess"].waitForExistence(timeout: 3))
        XCTAssertFalse(app.buttons["guess"].isEnabled)
    }

    /// A word in the summary opens the system dictionary (forced on: the simulator has none).
    func testLookUpFromTheSummary() throws {
        let app = launch(["-startGame", "-seedKey", "ui-look", "-level", "gentle", "-words", "en", "-nearlyWon", "-won", "-forceLookUp"])
        let word = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'lookUp-'")).firstMatch
        XCTAssertTrue(word.waitForExistence(timeout: 10))
        word.tap()
        // Apple's sheet: it has a Done button even when no dictionary is installed.
        XCTAssertTrue(app.buttons["Done"].waitForExistence(timeout: 5) || app.navigationBars.firstMatch.waitForExistence(timeout: 1))
    }

    /// The tutorial: the words' language first, then drag CORAL and tap the second word.
    func testTutorial() throws {
        let app = launch(["-tutorial", "-open", "tutorial"])
        XCTAssertTrue(app.buttons["language-es"].waitForExistence(timeout: 10))
        app.buttons["language-en"].tap()
        app.buttons["tutorialContinue"].tap()
        // CORAL: row 3, columns 2–6 of the 7×7 board (cells 15–19).
        let c = app.descendants(matching: .any)["cell-15"], l = app.descendants(matching: .any)["cell-19"]
        XCTAssertTrue(c.waitForExistence(timeout: 3))
        c.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
            .press(forDuration: 0.05, thenDragTo: l.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)))
        // WAVES: column 1, rows 2–6 (cells 7 and 35), by tapping.
        app.descendants(matching: .any)["cell-7"].tap()
        app.descendants(matching: .any)["cell-35"].tap()
        XCTAssertTrue(app.buttons["startPlaying"].waitForExistence(timeout: 3))
        app.buttons["startPlaying"].tap()
        XCTAssertTrue(app.buttons["play"].waitForExistence(timeout: 3))
    }
}
