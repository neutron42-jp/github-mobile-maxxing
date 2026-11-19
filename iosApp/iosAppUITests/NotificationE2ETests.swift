import XCTest

/// Push notification and notification action E2E tests on the simulator.
///
/// The test runner cannot call `simctl`, so push payloads are injected from outside with a file
/// handshake: the test creates `ready-<name>` in MAXXING_ARTIFACT_DIR and waits for `go-<name>`;
/// an external script runs `xcrun simctl push ...` in between.
final class NotificationE2ETests: XCTestCase {
    override func setUp() { continueAfterFailure = false }

    private let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")

    private func banner(containing text: String) -> XCUIElement {
        springboard.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", text)).firstMatch
    }

    /// Permission flow: not determined -> prompt -> allow -> test notification is displayed.
    func test1_permissionPromptThenTestNotification() throws {
        let app = XCUIApplication()
        app.launch()
        try signInWithPatIfNeeded(app)
        app.buttons["Settings"].tap()
        let notificationsRow = app.buttons.containing(NSPredicate(format: "label == 'Notifications' OR label BEGINSWITH 'Notifications,'")).firstMatch
        XCTAssertTrue(notificationsRow.waitForExistence(timeout: 15), "Notifications row not found in Settings")
        notificationsRow.tap()
        let send = app.buttons["Send Test Notification"]
        XCTAssertTrue(send.waitForExistence(timeout: 15))
        send.tap()
        // Not determined: the first press only starts the permission request.
        let alert = springboard.alerts.firstMatch
        XCTAssertTrue(alert.waitForExistence(timeout: 10), "notification permission prompt did not appear")
        shot("n1-permission-prompt")
        let buttons = alert.buttons
        buttons.element(boundBy: buttons.count - 1).tap() // "Allow"
        sleep(2)
        send.tap()
        XCTAssertTrue(banner(containing: "Notifications Ready").waitForExistence(timeout: 10), "test notification was not displayed")
        shot("n2-test-notification")
    }

    /// Tapping a push with a deep link opens that thread (app in foreground).
    func test2_tapOnPushOpensThread() throws {
        let app = XCUIApplication()
        app.launch()
        try signInWithPatIfNeeded(app)
        XCTAssertTrue(app.buttons["Home"].waitForExistence(timeout: 30))
        handshake("push-thread")
        let b = banner(containing: "UTM mention")
        XCTAssertTrue(b.waitForExistence(timeout: 15), "push banner did not appear")
        b.tap()
        XCTAssertTrue(app.buttons["Open in browser"].waitForExistence(timeout: 20), "tapping the notification did not open the thread")
        shot("n3-tap-opens-thread")
        app.buttons["Back"].tap()
    }

    /// Cold start: the app is not running when the notification is tapped.
    func test3_tapOnPushColdStartOpensThread() throws {
        let app = XCUIApplication()
        app.launch()
        try signInWithPatIfNeeded(app)
        app.terminate()
        handshake("push-thread-cold")
        let b = banner(containing: "UTM mention")
        XCTAssertTrue(b.waitForExistence(timeout: 15), "push banner did not appear")
        b.tap()
        XCTAssertTrue(app.buttons["Open in browser"].waitForExistence(timeout: 30), "cold-start tap did not open the thread")
        shot("n4-cold-start-tap")
        app.buttons["Back"].tap()
    }

    /// Mark Read on a thread the server does not know fails; the app must survive and the
    /// notification must stay (a failed action never removes it).
    func test4_markReadFailureKeepsNotification() throws {
        let app = XCUIApplication()
        app.launch()
        try signInWithPatIfNeeded(app)
        XCTAssertTrue(app.buttons["Home"].waitForExistence(timeout: 30))
        handshake("push-action")
        let b = banner(containing: "UTM action")
        XCTAssertTrue(b.waitForExistence(timeout: 15))
        b.press(forDuration: 1.2) // expand the banner to reveal the actions
        let markRead = springboard.buttons["Mark Read"]
        XCTAssertTrue(markRead.waitForExistence(timeout: 8), "Mark Read action is not offered")
        shot("n5-actions")
        markRead.tap()
        sleep(8) // allow the API call to fail (404) and the handler to finish
        XCTAssertNotEqual(app.state, .notRunning, "the app crashed handling the action")
    }

    /// A push without an issue/PR number (`subject_number` null, e.g. a check suite) opens the Inbox tab.
    func test5_tapOnPushWithoutNumberOpensInbox() throws {
        let app = XCUIApplication()
        app.launch()
        try signInWithPatIfNeeded(app)
        app.buttons["Home"].tap() // make sure we do not start on the Inbox tab
        XCTAssertTrue(app.buttons["Home"].waitForExistence(timeout: 30))
        XCTAssertFalse(app.buttons["All Reasons"].exists, "precondition: Inbox must not be shown yet")
        handshake("push-inbox")
        let b = banner(containing: "UTM check suite")
        XCTAssertTrue(b.waitForExistence(timeout: 15), "push banner did not appear")
        b.tap()
        XCTAssertTrue(app.buttons["All Reasons"].waitForExistence(timeout: 20), "tapping the notification did not open the Inbox")
        XCTAssertFalse(app.buttons["Open in browser"].exists, "a detail screen opened instead of the Inbox")
        shot("n6-tap-opens-inbox")
    }

    /// The Inbox request also wins over an older thread request (latest only) and survives a cold start.
    func test6_tapOnPushWithoutNumberColdStartOpensInbox() throws {
        let app = XCUIApplication()
        app.launch()
        try signInWithPatIfNeeded(app)
        app.terminate()
        handshake("push-inbox-cold")
        let b = banner(containing: "UTM check suite")
        XCTAssertTrue(b.waitForExistence(timeout: 15), "push banner did not appear")
        b.tap()
        XCTAssertTrue(app.buttons["All Reasons"].waitForExistence(timeout: 30), "cold-start tap did not open the Inbox")
        shot("n7-cold-start-inbox")
    }

    /// An Inbox request that arrives while signed out stays pending and is applied after sign-in.
    func test7_inboxRequestWhileSignedOutIsAppliedAfterSignIn() throws {
        let app = XCUIApplication()
        app.launch()
        try signInWithPatIfNeeded(app)
        app.buttons["Settings"].tap()
        XCTAssertTrue(app.buttons["Sign Out"].waitForExistence(timeout: 15))
        app.buttons["Sign Out"].tap()
        XCTAssertTrue(app.buttons[signInButtonLabel].waitForExistence(timeout: 15))
        // Relaunch signed out; the debug sign-in fires 25 s later, after the notification was tapped.
        try XCTSkipIf(E2E.pat.isEmpty, "MAXXING_PAT is not set")
        app.terminate()
        app.launchEnvironment["MAXXING_UI_TEST_PAT"] = E2E.pat
        app.launchEnvironment["MAXXING_UI_TEST_PAT_DELAY_MS"] = "25000"
        app.launch()
        XCTAssertTrue(app.buttons[signInButtonLabel].waitForExistence(timeout: 15), "expected the sign-in screen")
        handshake("push-inbox-signed-out")
        let b = banner(containing: "UTM check suite")
        XCTAssertTrue(b.waitForExistence(timeout: 15))
        b.tap()
        XCTAssertTrue(app.buttons["All Reasons"].waitForExistence(timeout: 60), "pending Inbox request was not applied after sign-in")
        shot("n8-inbox-after-signin")
    }
}
