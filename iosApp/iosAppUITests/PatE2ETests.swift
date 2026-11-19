import XCTest

/// Simulator E2E: PAT sign-in -> browse -> detail -> restart (Keychain restore) -> sign-out.
/// Run with `TEST_RUNNER_MAXXING_PAT=<token>`; without it
/// the tests that need a session are skipped. Tests are numbered because they share one session.
final class PatE2ETests: XCTestCase {
    override func setUp() { continueAfterFailure = false }

    private func launchSignedIn() throws -> XCUIApplication {
        let app = XCUIApplication()
        app.launch()
        try signInWithPatIfNeeded(app)
        XCTAssertTrue(app.buttons["Home"].waitForExistence(timeout: 30), "Home tab bar did not appear after sign-in")
        return app
    }

    func test1_signInBrowseAndOpenDetails() throws {
        let app = try launchSignedIn()
        sleep(4) // let Home content and avatars load
        shot("01-home")

        app.buttons["Repos"].tap()
        let repo = app.buttons.containing(NSPredicate(format: "label CONTAINS 'soramikan/github-mobile-maxxing'")).firstMatch
        XCTAssertTrue(repo.waitForExistence(timeout: 20), "repository list did not load")
        shot("02-repos")
        repo.tap()
        let back = app.buttons["Back"]
        XCTAssertTrue(back.waitForExistence(timeout: 20), "repository detail did not open")
        sleep(3)
        shot("03-repo-detail")
        back.tap()

        // The issue detail (and the external-link hand-off to Safari) is covered by DeepLinkE2ETests: the live
        // activity feed has no stable issue/PR row to tap.
    }

    /// Best effort: open an issue/PR row of the live activity feed (skipped when none is fully visible).
    func test1b_openIssueFromActivityFeedIfAvailable() throws {
        let app = try launchSignedIn()
        app.buttons["Home"].tap()
        let itemQuery = app.buttons.matching(NSPredicate(format: "label CONTAINS ' issue #' OR label CONTAINS ' pull request #'"))
        _ = itemQuery.firstMatch.waitForExistence(timeout: 20)
        func visibleItem() -> XCUIElement? {
            itemQuery.allElementsBoundByIndex.first { $0.isHittable && $0.frame.minY > 160 && $0.frame.maxY < 680 }
        }
        var scrolls = 0
        while visibleItem() == nil && scrolls < 8 { app.swipeUp(); scrolls += 1 }
        guard let item = visibleItem() else { throw XCTSkip("no fully visible issue/PR row in the live activity feed") }
        item.tap()
        // Not every feed row is an issue row that opens a detail (stars, forks, ...), so this stays best effort.
        try XCTSkipUnless(app.buttons["Open in browser"].waitForExistence(timeout: 10), "the tapped feed row does not open an issue detail")
        shot("04-issue-detail")
        app.buttons["Back"].tap()
    }

    func test2_sessionIsRestoredFromKeychainAfterRelaunch() throws {
        let app = try launchSignedIn()
        forgetTestCredentials(app) // prove the Keychain restore, not the debug sign-in
        app.terminate()
        app.launch()
        XCTAssertTrue(app.buttons["Home"].waitForExistence(timeout: 30), "Home did not appear after relaunch")
        XCTAssertFalse(app.buttons[signInButtonLabel].exists, "the sign-in screen was shown after relaunch")
        shot("06-restored")
    }

    func test3_signOutThenRelaunchShowsSignIn() throws {
        let app = try launchSignedIn()
        app.buttons["Settings"].tap()
        let signOut = app.buttons["Sign Out"]
        XCTAssertTrue(signOut.waitForExistence(timeout: 15))
        signOut.tap()
        XCTAssertTrue(app.buttons[signInButtonLabel].waitForExistence(timeout: 15), "sign-in screen did not appear after sign-out")
        shot("07-signed-out")
        forgetTestCredentials(app)
        app.terminate()
        app.launch()
        XCTAssertTrue(app.buttons[signInButtonLabel].waitForExistence(timeout: 15), "signed-out state was not persisted")
        XCTAssertFalse(app.buttons["Home"].exists)
    }
}
