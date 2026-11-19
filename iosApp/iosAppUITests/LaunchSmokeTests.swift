import XCTest

/// Launch smoke test for a fresh install (also used on a physical device): the app starts and shows
/// the Sign-In screen. The screenshot is kept as an attachment of the result bundle.
final class LaunchSmokeTests: XCTestCase {
    func testSignInScreenIsShownOnFreshInstall() throws {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.buttons[signInButtonLabel].waitForExistence(timeout: 30), "Sign-In screen did not appear")
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "sign-in-screen"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
