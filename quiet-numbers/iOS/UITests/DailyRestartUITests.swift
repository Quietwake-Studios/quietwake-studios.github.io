import XCTest

/// Bug of 2026-11-02: start a daily, start over, play differently, leave the app and come
/// back: the first attempt came back instead of the latest one.
final class DailyRestartUITests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    @MainActor
    func testStartingOverAPastDayKeepsTheLatestAttempt() {
        let app = XCUIApplication()
        app.launchArguments = ["-uiTesting", "-resetStore", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()

        // First attempt: one +.
        openPastDay(app)
        app.buttons["Add numbers"].tap()
        let first = cellCount(app)
        app.buttons["back"].tap()

        // Start over and play differently: two +.
        pastDay(app).tap()
        app.alerts.buttons["Start over"].tap()
        XCTAssertTrue(app.staticTexts["score"].waitForExistence(timeout: 2))
        app.buttons["Add numbers"].tap()
        app.buttons["Add numbers"].tap()
        let latest = cellCount(app)
        XCTAssertNotEqual(first, latest)

        // Leave the app and come back.
        app.terminate()
        app.launchArguments = ["-uiTesting", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        dailyCard(app).tap()
        pastDay(app).tap()
        app.alerts.buttons["Continue"].tap()
        XCTAssertTrue(app.staticTexts["score"].waitForExistence(timeout: 2))
        XCTAssertEqual(cellCount(app), latest)
    }

    /// 2026-11-13: "Done" on a daily's summary goes back to the Daily Challenge (the
    /// calendar), today's or a past day's.
    @MainActor
    func testDoneAfterAPastDayReturnsToTheCalendar() {
        let app = finishDaily(["-summaryDay", pastDayKey])
        XCTAssertTrue(app.buttons["Previous month"].waitForExistence(timeout: 5))
    }

    @MainActor
    func testDoneAfterTodayReturnsToTheCalendar() {
        let app = finishDaily([])
        XCTAssertTrue(app.buttons["Previous month"].waitForExistence(timeout: 5))
    }

    /// The challenge one pair from its goal (`-summaryScene`): the pair, then "Done".
    @MainActor
    private func finishDaily(_ arguments: [String]) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-uiTesting", "-resetStore", "-startGame", "-summaryScene", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"] + arguments
        app.launch()
        XCTAssertTrue(app.buttons["cell-0"].waitForExistence(timeout: 5))
        app.buttons["cell-0"].tap()
        app.buttons["cell-1"].tap()
        let done = app.buttons["Done"]
        XCTAssertTrue(done.waitForExistence(timeout: 8))
        done.tap()
        return app
    }

    /// The day before today, a month back on the 1st.
    private var pastDayKey: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: Calendar.current.date(byAdding: .day, value: -1, to: .now)!)
    }

    @MainActor
    private func pastDay(_ app: XCUIApplication) -> XCUIElement {
        let day = app.buttons["day-\(pastDayKey)"]
        if !day.waitForExistence(timeout: 2) { app.buttons["Previous month"].tap() }
        XCTAssertTrue(day.waitForExistence(timeout: 2))
        return day
    }

    @MainActor
    private func openPastDay(_ app: XCUIApplication) {
        dailyCard(app).tap()
        pastDay(app).tap()
        XCTAssertTrue(app.staticTexts["score"].waitForExistence(timeout: 2))
    }

    @MainActor
    private func cellCount(_ app: XCUIApplication) -> Int {
        app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'cell-'")).count
    }

    @MainActor
    private func dailyCard(_ app: XCUIApplication) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Daily Challenge'")).firstMatch
    }
}
