import XCTest

/// Captures screenshots of the main screens per device and orientation for visual review
/// (safe area, bottom bar / rail, two-pane, editor with keyboard, Japanese and code).
/// `TEST_RUNNER_MAXXING_DEVICE` labels the files.
final class ScreensE2ETests: XCTestCase {
    override func setUp() { continueAfterFailure = true }

    func testCaptureScreens() throws {
        let device = ProcessInfo.processInfo.environment["MAXXING_DEVICE"] ?? "device"
        let app = XCUIApplication()
        app.launch()
        try signInWithPatIfNeeded(app)
        XCTAssertTrue(app.buttons["Home"].waitForExistence(timeout: 30))

        let orientations: [(String, UIDeviceOrientation)] = [("portrait", .portrait), ("landscape", .landscapeLeft)]
        for (name, orientation) in orientations {
            XCUIDevice.shared.orientation = orientation
            sleep(2)
            for tab in ["Home", "Inbox", "Issues", "Repos", "Settings"] {
                let button = app.buttons[tab]
                if !button.waitForExistence(timeout: 10) { XCTFail("\(tab) tab missing in \(name)"); continue }
                button.tap()
                sleep(4)
                shot("\(device)-\(name)-\(tab)")
            }
            // Repo detail (README / Japanese / code) and an issue/PR detail via upstream Explore.
            if openRepositoryViaExplore(app) {
                sleep(5)
                shot("\(device)-\(name)-repo-detail")
                if openFirstPullRequestFromRepoDetail(app) {
                    sleep(5)
                    shot("\(device)-\(name)-issue-detail")
                    if app.buttons["Back"].exists { app.buttons["Back"].tap() }
                }
                if app.buttons["Back"].waitForExistence(timeout: 5) { app.buttons["Back"].tap() }
            }
            // Editor with the keyboard: Repos -> New.
            app.buttons["Repos"].tap()
            let new = app.buttons["New"]
            if new.waitForExistence(timeout: 10) {
                new.tap()
                sleep(2)
                let field = app.textViews.firstMatch
                if field.waitForExistence(timeout: 8) { field.tap(); sleep(2) }
                shot("\(device)-\(name)-editor-keyboard")
                dismissKeyboardWithReturn(app)
                // Close the "Create repository" dialog without creating anything.
                if app.buttons["Cancel"].waitForExistence(timeout: 5) { app.buttons["Cancel"].tap() }
            }
        }
        XCUIDevice.shared.orientation = .portrait
    }
}
