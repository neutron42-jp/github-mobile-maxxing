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

        XCTAssertTrue(
            openRepositoryViaExplore(app, beforeOpenRepo: { self.shot("02-repos") }),
            "upstream repository detail did not open"
        )
        sleep(3)
        shot("03-repo-detail")
        app.buttons["Back"].tap()
    }

    /// Opens a pull request from the upstream repository (using the All filter so merged/closed PRs work too).
    func test1b_openPullRequestFromUpstreamRepo() throws {
        let app = try launchSignedIn()
        XCTAssertTrue(openRepositoryViaExplore(app), "upstream repository detail did not open")
        XCTAssertTrue(openFirstPullRequestFromRepoDetail(app), "PR detail from upstream repository did not open")
        shot("04-issue-detail")
        app.buttons["Back"].tap()
        if app.buttons["Back"].waitForExistence(timeout: 5) {
            app.buttons["Back"].tap()
        }
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
