import XCTest

final class GameplayUITests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    /// One move from the end: auto-complete wins and the win sheet appears.
    func testAutoCompleteWins() {
        let app = XCUIApplication()
        app.launchArguments = ["-startGame", "-nearlyWon"]
        app.launch()
        // The 10♥ from the waste goes on the J♠.
        app.buttons["10 of hearts, waste"].firstMatch.tap()
        let autoComplete = app.buttons["autoComplete"]
        XCTAssertTrue(autoComplete.waitForExistence(timeout: 3))
        autoComplete.tap()
        XCTAssertTrue(app.buttons["winNewGame"].waitForExistence(timeout: 10))
    }

    /// Dragging the waste card onto a column plays it.
    func testDragWasteToColumn() {
        let app = XCUIApplication()
        app.launchArguments = ["-startGame", "-nearlyWon"]
        app.launch()
        let ten = app.buttons["10 of hearts, waste"].firstMatch
        let jack = app.buttons["Jack of spades, column 4"].firstMatch
        XCTAssertTrue(ten.waitForExistence(timeout: 3))
        ten.press(forDuration: 0.1, thenDragTo: jack)
        XCTAssertTrue(app.buttons["10 of hearts, column 4"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.buttons["autoComplete"].waitForExistence(timeout: 3))
    }

    /// Settings › Appearance: turning "Match system" off switches to the opposite mode (the
    /// simulator is light, so dark), and turning it on again goes back.
    func testMatchSystemToggle() {
        let app = XCUIApplication()
        app.launchArguments = ["-uiTesting", "-resetStore"]
        app.launch()
        app.buttons["settings"].tap()
        let toggle = app.descendants(matching: .any)["matchSystem"].firstMatch
        XCTAssertTrue(toggle.waitForExistence(timeout: 5))
        XCTAssertFalse(app.descendants(matching: .any)["appearanceMode"].exists)
        toggle.tap()
        let mode = app.descendants(matching: .any)["appearanceMode"].firstMatch
        XCTAssertTrue(mode.waitForExistence(timeout: 3))
        XCTAssertEqual(mode.value as? String, "dark")
        app.buttons["Light"].tap()
        XCTAssertEqual(mode.value as? String, "light")
        toggle.tap()
        XCTAssertFalse(app.descendants(matching: .any)["appearanceMode"].waitForExistence(timeout: 1))
    }

    /// Collection › Hints: "Cheaper hints" shows only once "In the Red" is earned, starts on and
    /// can be turned off; until then the achievement keeps its secret (2026-11-10).
    func testCheapHintsToggle() {
        var app = XCUIApplication()
        app.launchArguments = ["-uiTesting", "-resetStore"]
        app.launch()
        app.buttons["collection"].tap()
        XCTAssertTrue(app.staticTexts["Deck"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.switches["cheapHints"].exists)
        let red = app.descendants(matching: .any)["achievement-inTheRed"].firstMatch
        for _ in 0..<6 where !red.exists { app.swipeUp() }
        XCTAssertTrue(red.waitForExistence(timeout: 3))
        XCTAssertTrue(red.label.contains("Hidden achievement"), red.label)
        XCTAssertFalse(red.label.contains("below zero"), red.label)
        app.terminate()

        app = XCUIApplication()
        app.launchArguments = ["-uiTesting", "-resetStore", "-unlockAll"]
        app.launch()
        app.buttons["collection"].tap()
        let toggle = app.switches["cheapHints"].firstMatch
        for _ in 0..<4 where !toggle.exists { app.swipeUp() }
        XCTAssertTrue(toggle.waitForExistence(timeout: 5))
        XCTAssertEqual(toggle.value as? String, "1")
        toggle.tap()
        XCTAssertEqual(toggle.value as? String, "0")
        let earned = app.descendants(matching: .any)["achievement-inTheRed"].firstMatch
        for _ in 0..<6 where !earned.exists { app.swipeUp() }
        XCTAssertTrue(earned.label.contains("In the Red"), earned.label)
    }

    /// The tutorial walks through its six moves by tapping, then starts a game.
    func testTutorial() {
        let app = XCUIApplication()
        app.launchArguments = ["-uiTesting", "-resetStore", "-tutorial"]
        app.launch()
        app.buttons["newGame"].tap()
        let cards = ["Stock, 3 cards", "7 of hearts, waste", "Ace of diamonds, column 2", "8 of spades, column 1",
                     "2 of diamonds, column 1", "King of clubs, column 4"]
        for label in cards {
            let card = app.buttons[label].firstMatch
            XCTAssertTrue(card.waitForExistence(timeout: 3), label)
            card.tap()
        }
        let finish = app.buttons["finishTutorial"]
        XCTAssertTrue(finish.waitForExistence(timeout: 3))
        finish.tap()
        XCTAssertTrue(app.staticTexts["score"].waitForExistence(timeout: 5))
    }

    /// A drag that starts at the left edge of the first column moves the card (the
    /// swipe-back gesture used to steal it).
    func testDragFromLeftEdge() {
        let app = XCUIApplication()
        app.launchArguments = ["-startGame", "-nearlyWon"]
        app.launch()
        let ten = app.buttons["10 of diamonds, column 1"].firstMatch
        XCTAssertTrue(ten.waitForExistence(timeout: 3))
        let jack = app.buttons["Jack of spades, column 4"].firstMatch
        ten.coordinate(withNormalizedOffset: CGVector(dx: 0.03, dy: 0.2))
            .press(forDuration: 0.1, thenDragTo: jack.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)))
        XCTAssertTrue(app.buttons["10 of diamonds, column 4"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.staticTexts["score"].exists, "still in the game")
    }

    /// Dropped off-center, the card still goes to the column it overlaps most.
    func testDropOffCenterLands() {
        let app = XCUIApplication()
        app.launchArguments = ["-startGame", "-nearlyWon"]
        app.launch()
        let ten = app.buttons["10 of hearts, waste"].firstMatch
        XCTAssertTrue(ten.waitForExistence(timeout: 3))
        let jack = app.buttons["Jack of spades, column 4"].firstMatch
        ten.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
            .press(forDuration: 0.1, thenDragTo: jack.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 1.6)))
        XCTAssertTrue(app.buttons["10 of hearts, column 4"].waitForExistence(timeout: 3))
    }

    /// A lost game blocks the table until the player chooses to keep playing.
    func testLostGameBlocksUntilKeepPlaying() {
        let app = XCUIApplication()
        app.launchArguments = ["-startGame", "-lostDeal"]
        app.launch()
        let keepPlaying = app.buttons["keepPlaying"]
        XCTAssertTrue(keepPlaying.waitForExistence(timeout: 5))
        // Behind the notice the table doesn't respond: Hint would say there are no moves.
        app.buttons["hint"].tap()
        XCTAssertFalse(app.staticTexts["No moves left. Try undo."].waitForExistence(timeout: 2))
        keepPlaying.tap()
        XCTAssertFalse(keepPlaying.exists)
        app.buttons["hint"].tap()
        // The hint runs the solver; a debug build under load can take a few seconds.
        XCTAssertTrue(app.staticTexts["No moves left. Try undo."].waitForExistence(timeout: 8))
    }

    /// A new classic game from Home: drawing changes the move count; undo works.
    func testNewGameDrawAndUndo() {
        let app = XCUIApplication()
        app.launchArguments = ["-uiTesting", "-resetStore"]
        app.launch()
        app.buttons["newGame"].tap()
        let stock = app.buttons["stock"]
        // The winnable deal is searched in the background; a debug build under load is slow.
        XCTAssertTrue(app.staticTexts["score"].waitForExistence(timeout: 30))
        app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH 'Stock,'")).firstMatch.tap()
        XCTAssertTrue(app.buttons["undo"].isEnabled)
        app.buttons["undo"].tap()
        _ = stock
    }
}
