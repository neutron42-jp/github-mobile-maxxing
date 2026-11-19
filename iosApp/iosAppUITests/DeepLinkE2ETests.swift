import XCTest

/// Deep-link and external-link E2E assertions. The URL is delivered from outside by
/// `xcrun simctl openurl booted githubmaxxing://thread/...`, so these
/// tests attach to the already-running app with `activate()` instead of relaunching it.
final class DeepLinkE2ETests: XCTestCase {
    override func setUp() { continueAfterFailure = false }

    /// After a link was opened: the detail is shown and Back returns to the app shell.
    func testDetailIsShownAndBackWorks() throws {
        acceptOpenPromptIfShown()
        let app = XCUIApplication()
        app.activate()
        XCTAssertTrue(app.buttons["Open in browser"].waitForExistence(timeout: 20), "linked detail screen is not shown")
        shot("dl-detail")
        // External links hand the URL to the OS (Safari comes to the foreground).
        app.buttons["Open in browser"].tap()
        let safari = XCUIApplication(bundleIdentifier: "com.apple.mobilesafari")
        XCTAssertTrue(safari.wait(for: .runningForeground, timeout: 15), "Safari did not open")
        shot("dl-safari")
        app.activate()
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS 'utmapp/UTM #8202'")).firstMatch.exists)
        app.buttons["Back"].tap()
        XCTAssertTrue(app.buttons["Home"].waitForExistence(timeout: 15), "Back did not return to the shell")
    }

    /// A link that arrives while signed out stays pending and opens after sign-in. The app is launched signed
    /// out with a delayed debug sign-in; the orchestrating script opens the URL in between (handshake).
    func testPendingLinkOpensAfterSignIn() throws {
        try XCTSkipIf(E2E.pat.isEmpty, "MAXXING_PAT is not set")
        let app = XCUIApplication()
        app.launchEnvironment["MAXXING_UI_TEST_PAT"] = E2E.pat
        app.launchEnvironment["MAXXING_UI_TEST_PAT_DELAY_MS"] = "30000"
        app.launch()
        XCTAssertTrue(app.buttons[signInButtonLabel].waitForExistence(timeout: 15), "expected the sign-in screen")
        handshake("link-signed-out")
        acceptOpenPromptIfShown()
        XCTAssertTrue(app.buttons["Open in browser"].waitForExistence(timeout: 60), "pending link was not applied after sign-in")
        shot("dl-after-signin")
    }

    /// After an invalid link: still on the Home shell, no detail screen.
    func testInvalidLinkChangesNothing() throws {
        acceptOpenPromptIfShown()
        let app = XCUIApplication()
        app.activate()
        XCTAssertTrue(app.buttons["Home"].waitForExistence(timeout: 15))
        sleep(2)
        XCTAssertFalse(app.buttons["Open in browser"].exists, "an invalid link opened a detail screen")
        shot("dl-invalid")
    }
}
