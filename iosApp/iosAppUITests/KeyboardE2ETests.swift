import XCTest

/// On the sign-in screen the custom Client ID field stays reachable above the keyboard (the form
/// shrinks to the space above it). A dummy value is typed; the test signs out first when needed.
final class KeyboardE2ETests: XCTestCase {
    override func setUp() { continueAfterFailure = false }

    func testCustomClientIdFieldIsReachableWithKeyboardShown() throws {
        let app = XCUIApplication()
        app.launch()
        if !app.buttons[signInButtonLabel].waitForExistence(timeout: 8) {
            app.buttons["Settings"].tap()
            XCTAssertTrue(app.buttons["Sign Out"].waitForExistence(timeout: 15))
            app.buttons["Sign Out"].tap()
            XCTAssertTrue(app.buttons[signInButtonLabel].waitForExistence(timeout: 15))
        }
        let toggle = app.buttons["Use custom OAuth Client ID"]
        XCTAssertTrue(toggle.waitForExistence(timeout: 10))
        toggle.tap()
        let field = app.textViews.containing(NSPredicate(format: "label CONTAINS 'GitHub OAuth App Client ID'")).firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.tap()
        let tip = app.buttons["Continue"]
        if tip.waitForExistence(timeout: 2) { tip.tap() }
        field.typeText("abc")
        XCTAssertGreaterThan(app.keyboards.count, 0)
        sleep(1)
        shot("kb-client-id")
        XCTAssertTrue(field.isHittable, "the Client ID field is hidden by the keyboard")
        let keyboardTop = app.keyboards.firstMatch.frame.minY
        XCTAssertLessThanOrEqual(field.frame.maxY, keyboardTop + 1, "the field overlaps the keyboard")
    }
}
