import XCTest

/// Phase 5 acceptance: each reward can be applied from the Collection.
final class CollectionUITests: XCTestCase {
    @MainActor
    func testApplyingRewards() {
        let app = XCUIApplication()
        app.launchArguments = ["-uiTesting", "-resetStore", "-unlockAll"]
        app.launch()

        app.buttons["Collection"].tap()
        app.buttons["Sage & Coral"].tap()
        app.buttons["Circle"].tap()
        app.buttons["New York"].tap()
        XCTAssertTrue(app.buttons["Sage & Coral"].isSelected)
        snapshot(app, "collection")

        app.buttons["back"].tap()
        app.buttons["New Game"].tap()
        XCTAssertTrue(app.buttons["cell-0"].waitForExistence(timeout: 2))
        snapshot(app, "game-sage-coral")

        app.buttons["back"].tap()
        app.buttons["Settings"].tap()
        snapshot(app, "settings")

        // Leave the simulator as it was.
        app.buttons["back"].tap()
        app.buttons["Collection"].tap()
        app.buttons["Teal & Coral"].tap()
        app.buttons["Plain"].tap()
        app.buttons["SF Rounded"].tap()
    }

    /// Settings › Appearance: turning "Match system" off switches to the opposite mode (the
    /// simulator is light, so dark), and turning it on again goes back (2026-11-08).
    @MainActor
    func testMatchSystemToggle() {
        let app = XCUIApplication()
        app.launchArguments = ["-uiTesting", "-resetStore", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        app.buttons["Settings"].tap()
        let toggle = app.descendants(matching: .any)["matchSystem"].firstMatch
        XCTAssertTrue(toggle.waitForExistence(timeout: 5))
        XCTAssertFalse(app.descendants(matching: .any)["appearanceMode"].exists)
        toggle.tap()
        let mode = app.descendants(matching: .any)["appearanceMode"].firstMatch
        XCTAssertTrue(mode.waitForExistence(timeout: 3))
        XCTAssertEqual(mode.value as? String, "dark")
        snapshot(app, "settings-dark")
        app.buttons["Light"].tap()
        XCTAssertEqual(mode.value as? String, "light")
        snapshot(app, "settings-light")
        toggle.tap()
        XCTAssertFalse(app.descendants(matching: .any)["appearanceMode"].waitForExistence(timeout: 1))
    }

    /// "Cheaper hints" shows in Collection only once "In the Red" is earned, starts on, and
    /// turns off; until earned, the achievement reads "Hidden achievement" (2026-11-10).
    @MainActor
    func testCheapHintsToggle() {
        var app = XCUIApplication()
        app.launchArguments = ["-uiTesting", "-resetStore", "-AppleLanguages", "(es)", "-AppleLocale", "es_CO"]
        app.launch()
        app.buttons["collection"].tap()
        XCTAssertTrue(app.buttons["Petróleo y coral"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.descendants(matching: .any)["cheapHints"].exists)
        let hidden = inTheRedRow(app, tab: "Logros")
        XCTAssertTrue(hidden.label.contains("Logro oculto"), hidden.label)
        XCTAssertTrue(hidden.label.contains("Sigue jugando"), hidden.label)
        XCTAssertFalse(hidden.label.contains("Números rojos"), hidden.label)
        app.terminate()

        app = XCUIApplication()
        app.launchArguments = ["-uiTesting", "-resetStore", "-unlockAll", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        app.buttons["Collection"].tap()
        let toggle = app.switches["cheapHints"].firstMatch
        XCTAssertTrue(toggle.waitForExistence(timeout: 5))
        XCTAssertEqual(toggle.value as? String, "1")
        snapshot(app, "collection-cheap-hints")
        toggle.tap()
        XCTAssertEqual(toggle.value as? String, "0")
        // Leave the simulator as it was.
        toggle.tap()
        XCTAssertEqual(toggle.value as? String, "1")
        app.terminate()

        // In Spanish, the section and the rewards read in Spanish.
        app = XCUIApplication()
        app.launchArguments = ["-uiTesting", "-resetStore", "-unlockAll", "-AppleLanguages", "(es)", "-AppleLocale", "es_CO"]
        app.launch()
        app.buttons["collection"].tap()
        XCTAssertTrue(app.switches["cheapHints"].firstMatch.waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Pistas más baratas"].exists)
        XCTAssertTrue(app.buttons["Petróleo y coral"].exists)
        snapshot(app, "collection-cheap-hints-es")
        let earned = inTheRedRow(app, tab: "Logros")
        XCTAssertTrue(earned.label.contains("Números rojos"), earned.label)
        XCTAssertFalse(earned.label.contains("Logro oculto"), earned.label)
    }

    /// "In the Red" in Collection › Achievements, at the bottom of the list.
    @MainActor
    private func inTheRedRow(_ app: XCUIApplication, tab: String) -> XCUIElement {
        app.buttons[tab].tap()
        let row = app.descendants(matching: .any)["achievement-inTheRed"].firstMatch
        for _ in 0..<8 where !row.isHittable { app.swipeUp() }
        XCTAssertTrue(row.waitForExistence(timeout: 3))
        snapshot(app, "collection-in-the-red")
        return row
    }

    /// Collection › Achievements (2026-11-13, like Quiet Words): "N earned of M", each
    /// achievement with its condition, progress when it counts something and its reward; the
    /// hidden one says only "Hidden achievement" until earned.
    @MainActor
    func testAchievementsTab() {
        let app = XCUIApplication()
        app.launchArguments = ["-uiTesting", "-resetStore", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        app.buttons["collection"].tap()
        XCTAssertTrue(app.buttons["Rewards"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Teal & Coral"].exists)
        app.buttons["Achievements"].tap()
        let header = app.descendants(matching: .any)["achievementsHeader"].firstMatch
        XCTAssertTrue(header.waitForExistence(timeout: 3))
        XCTAssertTrue(header.label.contains("0 earned"), header.label)
        XCTAssertTrue(header.label.contains("of 11"), header.label)
        let first = app.descendants(matching: .any)["achievement-firstStep"].firstMatch
        XCTAssertTrue(first.label.contains("Clear your first board or finish your first game."), first.label)
        let sweeper = app.descendants(matching: .any)["achievement-sweeper"].firstMatch
        for _ in 0..<4 where !sweeper.isHittable { app.swipeUp() }
        XCTAssertTrue(sweeper.label.contains("0 of 100 rows"), sweeper.label)
        XCTAssertTrue(sweeper.label.contains("Wave row clear"), sweeper.label)
        XCTAssertFalse(app.buttons["Teal & Coral"].exists)
        let red = app.descendants(matching: .any)["achievement-inTheRed"].firstMatch
        for _ in 0..<6 where !red.isHittable { app.swipeUp() }
        XCTAssertTrue(red.label.contains("Hidden achievement"), red.label)
        XCTAssertFalse(red.label.contains("below zero"), red.label)
        XCTAssertFalse(red.label.contains("Cheaper hints"), red.label)
        snapshot(app, "collection-achievements")
    }

    /// Both Collection tabs with the demo state, for design review. The language comes from
    /// `COLLECTION_LANGUAGE` (`TEST_RUNNER_COLLECTION_LANGUAGE=es xcodebuild test …`), English by default.
    @MainActor
    func testCollectionTabsDemo() {
        let spanish = ProcessInfo.processInfo.environment["COLLECTION_LANGUAGE"] == "es"
        let app = XCUIApplication()
        app.launchArguments = ["-demo"] + (spanish ? ["-AppleLanguages", "(es)", "-AppleLocale", "es_CO"]
                                                   : ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"])
        app.launch()
        app.buttons["collection"].tap()
        let achievements = app.buttons[spanish ? "Logros" : "Achievements"]
        XCTAssertTrue(achievements.waitForExistence(timeout: 5))
        snapshot(app, "collection-rewards")
        achievements.tap()
        XCTAssertTrue(app.descendants(matching: .any)["achievementsHeader"].waitForExistence(timeout: 3))
        snapshot(app, "collection-achievements")
    }

    /// Signed out of Game Center, leaderboards explain how to sign in instead of failing.
    @MainActor
    func testLeaderboardsSignedOut() {
        let app = XCUIApplication()
        app.launchArguments = ["-uiTesting", "-resetStore"]
        app.launch()
        app.buttons["Leaderboards"].tap()
        XCTAssertTrue(app.staticTexts["Play with friends"].waitForExistence(timeout: 3))
        snapshot(app, "leaderboards-signed-out")
    }

    @MainActor
    private func snapshot(_ app: XCUIApplication, _ name: String) {
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = name
        shot.lifetime = .keepAlways
        add(shot)
    }

    /// Settings › About: "Rate Quiet Numbers" (2026-11-13). `TEST_RUNNER_SHOTS_DIR=<dir>` saves a capture.
    @MainActor
    func testRateRowInSettings() {
        let app = XCUIApplication()
        app.launchArguments = ["-uiTesting", "-resetStore", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        app.buttons["Settings"].tap()
        let rate = app.buttons["rateApp"]
        XCTAssertTrue(rate.waitForExistence(timeout: 5))
        var swipes = 0
        while !rate.isHittable, swipes < 6 {
            app.swipeUp()
            swipes += 1
        }
        XCTAssertTrue(rate.isHittable)
        XCTAssertEqual(rate.label, "Rate Quiet Numbers")
        saveShot(app, "settings-numbers")
    }

    /// A capture for review, only when `TEST_RUNNER_SHOTS_DIR` is set.
    private func saveShot(_ app: XCUIApplication, _ name: String) {
        guard let dir = ProcessInfo.processInfo.environment["SHOTS_DIR"], !dir.isEmpty else { return }
        try? app.screenshot().pngRepresentation.write(to: URL(fileURLWithPath: dir).appendingPathComponent("\(name).png"))
    }
}
