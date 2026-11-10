import XCTest

/// Plays the real app: hints, notes, a win, and the tutorial from start to end.
final class GameplayUITests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    private func launch(_ arguments: [String]) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = arguments + ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        return app
    }

    func testHintExplainsAndScores() {
        let app = launch(["-startGame", "-seedKey", "ui-hint", "-level", "1"])
        XCTAssertTrue(app.buttons["hint"].waitForExistence(timeout: 10))
        app.buttons["hint"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["hintCard"].waitForExistence(timeout: 3))
        XCTAssertEqual(app.staticTexts["score"].label, "Score -10")
    }

    func testNotesGoInTheSelectedCell() {
        let app = launch(["-startGame", "-seedKey", "ui-notes"])
        XCTAssertTrue(app.buttons["notes"].waitForExistence(timeout: 10))
        // An empty cell: its label says so.
        let empty = app.buttons.matching(NSPredicate(format: "label ENDSWITH 'empty'")).firstMatch
        empty.tap()
        app.buttons["notes"].tap()
        app.buttons["digit-3"].tap()
        app.buttons["digit-7"].tap()
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label CONTAINS 'notes 3 and 7'")).firstMatch.exists)
    }

    func testLastDigitWins() {
        let app = launch(["-startGame", "-seedKey", "ui-win", "-nearlyWon"])
        XCTAssertTrue(app.buttons["hint"].waitForExistence(timeout: 10))
        let empty = app.buttons.matching(NSPredicate(format: "label ENDSWITH 'empty'")).firstMatch
        empty.tap()
        // Try digits until the right one: wrong ones stay red and get replaced. The win disables
        // the keypad before the summary shows (it waits for the win animation); a tap after that
        // could land on the summary's Home button.
        for digit in 1...9 where app.buttons["digit-\(digit)"].isEnabled {
            app.buttons["digit-\(digit)"].tap()
        }
        XCTAssertTrue(app.staticTexts["Solved!"].waitForExistence(timeout: 15))
    }

    /// Appearance: turning "Match system" off switches to the opposite mode (the simulator is
    /// light, so dark), and turning it on again goes back.
    func testMatchSystemToggle() throws {
        let app = launch(["-uiTesting", "-resetStore", "-open", "settings"])
        let toggle = app.descendants(matching: .any)["matchSystem"].firstMatch
        XCTAssertTrue(toggle.waitForExistence(timeout: 10))
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

    /// "Cheaper hints" shows in the Collection only once "In the Red" is earned, starts on and
    /// turns off; until earned, the achievement says only "Hidden achievement" (2026-11-10).
    func testCheapHintsToggle() throws {
        var app = launch(["-uiTesting", "-resetStore", "-open", "collection"])
        let hidden = app.descendants(matching: .any)["achievement-inTheRed"].firstMatch
        XCTAssertTrue(hidden.waitForExistence(timeout: 10))
        XCTAssertTrue(hidden.label.contains("Hidden achievement"), hidden.label)
        XCTAssertFalse(hidden.label.contains("below zero"), hidden.label)
        XCTAssertFalse(app.descendants(matching: .any)["cheapHints"].exists)
        app.terminate()

        app = launch(["-uiTesting", "-resetStore", "-open", "collection", "-unlockAll"])
        let toggle = app.descendants(matching: .any)["cheapHints"].firstMatch
        XCTAssertTrue(toggle.waitForExistence(timeout: 10))
        XCTAssertEqual(toggle.value as? String, "1")
        toggle.tap()
        XCTAssertEqual(toggle.value as? String, "0")
        let earned = app.descendants(matching: .any)["achievement-inTheRed"].firstMatch
        XCTAssertTrue(earned.label.contains("In the Red"), earned.label)
        app.terminate()

        // It stays off.
        app = launch(["-uiTesting", "-open", "collection", "-unlockAll"])
        let stillOff = app.descendants(matching: .any)["cheapHints"].firstMatch
        XCTAssertTrue(stillOff.waitForExistence(timeout: 10))
        XCTAssertEqual(stillOff.value as? String, "0")
    }

    func testTutorialFromScratch() {
        let app = launch(["-uiTesting", "-resetStore", "-tutorial"])
        app.buttons["newGame"].tap()
        XCTAssertTrue(app.buttons["skipTutorial"].waitForExistence(timeout: 5))
        for step in 1...7 {
            XCTAssertTrue(app.staticTexts["Step \(step) of 7"].waitForExistence(timeout: 5), "step \(step)")
            if app.buttons["hint"].exists {
                app.buttons["hint"].tap()
            } else {
                // Only the right answers count: the others are refused without cost.
                for digit in 1...9 where app.staticTexts["Step \(step) of 7"].exists {
                    app.buttons["digit-\(digit)"].tap()
                }
            }
        }
        XCTAssertTrue(app.buttons["finishTutorial"].waitForExistence(timeout: 8))
        app.buttons["finishTutorial"].tap()
        XCTAssertTrue(app.buttons["hint"].waitForExistence(timeout: 10))
    }
}
