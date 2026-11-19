import XCTest

/// Verifies the status bar content colour follows the effective theme.
/// `TEST_RUNNER_MAXXING_THEME` selects Dark / Light / System; the OS appearance is set from
/// outside with `xcrun simctl ui booted appearance light|dark`. Results are checked on screenshots.
final class AppearanceE2ETests: XCTestCase {
    func testSelectTheme() throws {
        let theme = ProcessInfo.processInfo.environment["MAXXING_THEME"] ?? "System"
        let app = XCUIApplication()
        app.launch()
        try signInWithPatIfNeeded(app)
        app.buttons["Settings"].tap()
        XCTAssertTrue(app.buttons["Sign Out"].waitForExistence(timeout: 15))
        let appearanceRow = app.buttons.containing(NSPredicate(format: "label == 'Appearance' OR label BEGINSWITH 'Appearance,'")).firstMatch
        XCTAssertTrue(appearanceRow.waitForExistence(timeout: 10), "Appearance row not found in Settings")
        appearanceRow.tap()
        let selector = app.buttons.containing(NSPredicate(format: "label ENDSWITH ', Select option' AND (label BEGINSWITH 'System' OR label BEGINSWITH 'Dark' OR label BEGINSWITH 'Light')")).firstMatch
        for _ in 0..<4 where !(selector.exists && selector.isHittable) {
            let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.7))
            let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.4))
            start.press(forDuration: 0.1, thenDragTo: end)
            sleep(1)
        }
        XCTAssertTrue(selector.waitForExistence(timeout: 10))
        selector.tap()
        if !app.buttons[theme].waitForExistence(timeout: 3) && !app.staticTexts[theme].waitForExistence(timeout: 1) {
            selector.tap()
        }
        let option = app.buttons[theme].waitForExistence(timeout: 3) ? app.buttons[theme] : app.staticTexts[theme]
        XCTAssertTrue(option.waitForExistence(timeout: 5), "theme option \(theme) not found")
        option.tap()
        sleep(2)
        shot("theme-\(theme)-\(ProcessInfo.processInfo.environment["MAXXING_OS_APPEARANCE"] ?? "os")")
    }
}
