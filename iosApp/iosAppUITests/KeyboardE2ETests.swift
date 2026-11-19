import XCTest

/// On the sign-in screen the custom Client ID field stays reachable above the keyboard (the form
/// shrinks to the space above it). A dummy value is typed; the test signs out first when needed.
final class KeyboardE2ETests: XCTestCase {
    override func setUp() { continueAfterFailure = false }
    override func tearDown() {
        XCUIDevice.shared.orientation = .portrait
    }

    func testCustomClientIdFieldIsReachableWithKeyboardShown() throws {
        XCUIDevice.shared.orientation = .portrait
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
        shot("kb-client-id-portrait")

        let keyboardTop = app.keyboards.firstMatch.frame.minY
        XCTAssertTrue(field.isHittable, "the Client ID field is hidden by the keyboard")
        XCTAssertLessThanOrEqual(field.frame.maxY, keyboardTop + 1, "the field overlaps the keyboard")

        // The "Reset to default" button appears below the field once edited and must also stay above the keyboard.
        let reset = app.buttons["Reset to default"]
        XCTAssertTrue(reset.waitForExistence(timeout: 5), "Reset to default button did not appear")
        XCTAssertTrue(reset.isHittable, "Reset to default is hidden by the keyboard")
        XCTAssertLessThanOrEqual(reset.frame.maxY, keyboardTop + 1, "Reset to default overlaps the keyboard")
        reset.tap()
        XCTAssertFalse(reset.waitForExistence(timeout: 2), "Reset to default did not reset the Client ID")

        // Landscape: vertical space above the keyboard is much tighter; the focused field must remain above it.
        XCUIDevice.shared.orientation = .landscapeLeft
        sleep(2)
        field.tap()
        sleep(1)
        XCTAssertGreaterThan(app.keyboards.count, 0)
        shot("kb-client-id-landscape")
        let landscapeKeyboardTop = app.keyboards.firstMatch.frame.minY
        XCTAssertTrue(field.isHittable, "the Client ID field is hidden by the keyboard in landscape")
        XCTAssertLessThanOrEqual(field.frame.maxY, landscapeKeyboardTop + 1, "the field overlaps the keyboard in landscape")

        // Keyboard Done key dismisses focus so the Sign In action remains reachable.
        dismissKeyboardWithReturn(app)
        sleep(1)
        let signInButton = app.buttons[signInButtonLabel]
        XCTAssertTrue(signInButton.waitForExistence(timeout: 5))
        XCTAssertTrue(signInButton.isHittable, "Sign in with GitHub button is not reachable after dismissing keyboard")
    }
}
