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

    /// Auto notes fill every empty cell with its candidates; one undo takes them back.
    func testAutoNotesFillAndUndo() {
        let app = launch(["-startGame", "-seedKey", "ui-auto-notes", "-level", "1"])
        let autoNotes = app.buttons["autoNotes"]
        XCTAssertTrue(autoNotes.waitForExistence(timeout: 10))
        XCTAssertEqual(autoNotes.label, "Auto Notes")
        XCTAssertTrue(autoNotes.isEnabled)
        func emptyCells() -> Int { app.buttons.matching(NSPredicate(format: "label ENDSWITH 'empty'")).count }
        XCTAssertGreaterThan(emptyCells(), 0)
        autoNotes.tap()
        // No empty cell is left without notes, so there's nothing more to fill.
        XCTAssertEqual(emptyCells(), 0)
        XCTAssertGreaterThan(app.buttons.matching(NSPredicate(format: "label CONTAINS 'notes'")).count, 30)
        XCTAssertFalse(autoNotes.isEnabled)
        XCTAssertEqual(app.staticTexts["score"].label, "Score 0")
        app.buttons["undo"].tap()
        XCTAssertGreaterThan(emptyCells(), 30)
        XCTAssertTrue(autoNotes.isEnabled)
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
        XCTAssertTrue(app.buttons["Achievements"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.descendants(matching: .any)["cheapHints"].exists)
        app.buttons["Achievements"].tap()
        let hidden = app.descendants(matching: .any)["achievement-inTheRed"].firstMatch
        for _ in 0..<6 where !hidden.isHittable { app.swipeUp() }
        XCTAssertTrue(hidden.waitForExistence(timeout: 3))
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
        app.buttons["Achievements"].tap()
        let earned = app.descendants(matching: .any)["achievement-inTheRed"].firstMatch
        for _ in 0..<6 where !earned.isHittable { app.swipeUp() }
        XCTAssertTrue(earned.label.contains("In the Red"), earned.label)
        app.terminate()

        // It stays off.
        app = launch(["-uiTesting", "-open", "collection", "-unlockAll"])
        let stillOff = app.descendants(matching: .any)["cheapHints"].firstMatch
        XCTAssertTrue(stillOff.waitForExistence(timeout: 10))
        XCTAssertEqual(stillOff.value as? String, "0")
    }

    /// Collection › Achievements (2026-11-13, like Quiet Words): "N earned of M", each
    /// achievement with its condition, progress when it counts something and its reward; the
    /// hidden one says only "Hidden achievement" until earned.
    func testAchievementsTab() {
        let app = launch(["-uiTesting", "-resetStore", "-open", "collection"])
        XCTAssertTrue(app.buttons["Rewards"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Digits"].exists)
        app.buttons["Achievements"].tap()
        let header = app.descendants(matching: .any)["achievementsHeader"].firstMatch
        XCTAssertTrue(header.waitForExistence(timeout: 3))
        XCTAssertTrue(header.label.contains("0 earned"), header.label)
        XCTAssertTrue(header.label.contains("of 12"), header.label)
        XCTAssertFalse(app.staticTexts["Digits"].exists)
        let first = app.descendants(matching: .any)["achievement-firstWin"].firstMatch
        XCTAssertTrue(first.label.contains("Solve your first sudoku."), first.label)
        let ten = app.descendants(matching: .any)["achievement-tenWins"].firstMatch
        for _ in 0..<4 where !ten.isHittable { app.swipeUp() }
        XCTAssertTrue(ten.label.contains("0 of 10 sudokus"), ten.label)
        XCTAssertTrue(ten.label.contains("Notes in color"), ten.label)
        let red = app.descendants(matching: .any)["achievement-inTheRed"].firstMatch
        for _ in 0..<6 where !red.isHittable { app.swipeUp() }
        XCTAssertTrue(red.label.contains("Hidden achievement"), red.label)
        XCTAssertFalse(red.label.contains("below zero"), red.label)
        XCTAssertFalse(red.label.contains("Cheaper hints"), red.label)
    }

    /// Both Collection tabs with the demo state, for design review. The language comes from
    /// `COLLECTION_LANGUAGE` (`TEST_RUNNER_COLLECTION_LANGUAGE=es xcodebuild test …`), English by default.
    func testCollectionTabsDemo() {
        let spanish = ProcessInfo.processInfo.environment["COLLECTION_LANGUAGE"] == "es"
        let app = XCUIApplication()
        app.launchArguments = ["-demo", "-open", "collection"] + (spanish ? ["-AppleLanguages", "(es)", "-AppleLocale", "es_CO"]
                                                                          : ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"])
        app.launch()
        let achievements = app.buttons[spanish ? "Logros" : "Achievements"]
        XCTAssertTrue(achievements.waitForExistence(timeout: 10))
        snapshot(app, "collection-rewards")
        achievements.tap()
        XCTAssertTrue(app.descendants(matching: .any)["achievementsHeader"].waitForExistence(timeout: 3))
        snapshot(app, "collection-achievements")
    }

    private func snapshot(_ app: XCUIApplication, _ name: String) {
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = name
        shot.lifetime = .keepAlways
        add(shot)
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

    /// Settings › About: "Rate Quiet Sudoku" (2026-11-13). `TEST_RUNNER_SHOTS_DIR=<dir>` saves a capture.
    func testRateRowInSettings() {
        let app = launch(["-uiTesting", "-resetStore", "-open", "settings"])
        let rate = app.buttons["rateApp"]
        XCTAssertTrue(rate.waitForExistence(timeout: 5))
        var swipes = 0
        while !rate.isHittable, swipes < 6 {
            app.swipeUp()
            swipes += 1
        }
        XCTAssertTrue(rate.isHittable)
        XCTAssertEqual(rate.label, "Rate Quiet Sudoku")
        saveShot(app, "settings-sudoku")
    }

    /// A capture for review, only when `TEST_RUNNER_SHOTS_DIR` is set.
    private func saveShot(_ app: XCUIApplication, _ name: String) {
        guard let dir = ProcessInfo.processInfo.environment["SHOTS_DIR"], !dir.isEmpty else { return }
        try? app.screenshot().pngRepresentation.write(to: URL(fileURLWithPath: dir).appendingPathComponent("\(name).png"))
    }
}
