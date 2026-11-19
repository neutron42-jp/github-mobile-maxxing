import XCTest

/// Shared helpers for the simulator UI E2E tests.
/// The PAT is read from the environment (`TEST_RUNNER_MAXXING_PAT` on the xcodebuild command
/// line) and is never written to disk, logged or put in an attachment.
enum E2E {
    static var pat: String { ProcessInfo.processInfo.environment["MAXXING_PAT"] ?? "" }
    static var artifactDir: String? { ProcessInfo.processInfo.environment["MAXXING_ARTIFACT_DIR"] }
    static var targetRepo: String {
        if let configured = ProcessInfo.processInfo.environment["MAXXING_E2E_REPO"], !configured.isEmpty {
            return configured
        }
        return "neutron42-jp/github-mobile-maxxing"
    }
}

extension XCTestCase {
    func shot(_ name: String) {
        guard let dir = E2E.artifactDir else { return }
        // XCUIScreen screenshots are always portrait-oriented; the app's own screenshot follows the orientation.
        let png = XCUIApplication().screenshot().pngRepresentation
        try? png.write(to: URL(fileURLWithPath: dir).appendingPathComponent("\(name).png"))
    }

    func dump(_ app: XCUIApplication, _ name: String) {
        guard let dir = E2E.artifactDir else { return }
        try? app.debugDescription.write(
            to: URL(fileURLWithPath: dir).appendingPathComponent("\(name).txt"),
            atomically: true, encoding: .utf8
        )
    }

    /// The label of the sign-in screen's main button; its presence means "signed out".
    var signInButtonLabel: String { "Sign in with GitHub" }

    /// Signs in with the PAT when the sign-in screen is shown. The sign-in screen only offers Device Flow
    /// (the Personal Access Token form was removed upstream), so the token goes in through a debug-only
    /// hook: the app is relaunched with `MAXXING_UI_TEST_PAT` in its launch environment (see
    /// `SceneDelegate.swift`, compiled into Debug builds only). `delayMs` postpones the sign-in so a test
    /// can deliver a link / notification while the app is still signed out.
    func signInWithPatIfNeeded(_ app: XCUIApplication, delayMs: Int = 0) throws {
        guard app.buttons[signInButtonLabel].waitForExistence(timeout: 8) else { return } // already signed in
        try XCTSkipIf(E2E.pat.isEmpty, "MAXXING_PAT is not set")
        app.terminate()
        app.launchEnvironment["MAXXING_UI_TEST_PAT"] = E2E.pat
        if delayMs > 0 { app.launchEnvironment["MAXXING_UI_TEST_PAT_DELAY_MS"] = String(delayMs) }
        app.launch()
        if delayMs == 0 {
            XCTAssertTrue(app.buttons["Home"].waitForExistence(timeout: 40), "sign-in with the test token did not complete")
        }
    }

    /// Stops the debug sign-in for later launches of `app` (so a relaunch really shows the stored state).
    func forgetTestCredentials(_ app: XCUIApplication) {
        app.launchEnvironment.removeValue(forKey: "MAXXING_UI_TEST_PAT")
        app.launchEnvironment.removeValue(forKey: "MAXXING_UI_TEST_PAT_DELAY_MS")
    }

    /// `simctl openurl` for a custom scheme shows SpringBoard's "Open in <app>?" confirmation.
    /// Confirms it when it appears (the right-hand button is "Open" in every localization).
    func acceptOpenPromptIfShown() {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let alert = springboard.alerts.firstMatch
        if alert.waitForExistence(timeout: 6) {
            let buttons = alert.buttons
            buttons.element(boundBy: buttons.count - 1).tap()
        }
    }

    /// Taps the keyboard's return/done key (its label differs between iPhone and iPad keyboards).
    func dismissKeyboardWithReturn(_ app: XCUIApplication) {
        guard app.keyboards.count > 0 else { return }
        for label in ["return", "Return", "Done", "done", "Go", "go"] where app.keyboards.buttons[label].exists {
            app.keyboards.buttons[label].tap()
            return
        }
        XCTFail("no return key found on the keyboard")
    }

    func handshake(_ name: String, timeout: TimeInterval = 90) {
        guard let dir = E2E.artifactDir else { return XCTFail("MAXXING_ARTIFACT_DIR is not set") }
        let ready = URL(fileURLWithPath: dir).appendingPathComponent("ready-\(name)")
        let go = URL(fileURLWithPath: dir).appendingPathComponent("go-\(name)")
        try? FileManager.default.removeItem(at: go)
        FileManager.default.createFile(atPath: ready.path, contents: Data())
        let deadline = Date().addingTimeInterval(timeout)
        while !FileManager.default.fileExists(atPath: go.path), Date() < deadline { usleep(200_000) }
        XCTAssertTrue(FileManager.default.fileExists(atPath: go.path), "orchestrator did not answer \(name)")
        try? FileManager.default.removeItem(at: ready)
    }

    /// Opens a repository (defaulting to the upstream project repository) via Repos -> Explore -> Search,
    /// avoiding any dependency on the signed-in account's own repositories or forks.
    @discardableResult
    func openRepositoryViaExplore(
        _ app: XCUIApplication,
        repoFullName: String = E2E.targetRepo,
        beforeOpenRepo: (() -> Void)? = nil
    ) -> Bool {
        let reposTab = app.buttons["Repos"]
        guard reposTab.waitForExistence(timeout: 15) else { return false }
        reposTab.tap()

        let exploreTab = app.buttons["Explore"]
        guard exploreTab.waitForExistence(timeout: 10) else { return false }
        exploreTab.tap()

        let clearButton = app.buttons["Clear"]
        if clearButton.exists {
            clearButton.tap()
        }

        let predicate = NSPredicate(format: "label CONTAINS 'Search repositories'")
        let textView = app.textViews.containing(predicate).firstMatch
        let textField = app.textFields.containing(predicate).firstMatch
        let searchInput: XCUIElement
        if textView.waitForExistence(timeout: 8) {
            searchInput = textView
        } else if textField.waitForExistence(timeout: 3) {
            searchInput = textField
        } else {
            return false
        }

        searchInput.tap()
        let tip = app.buttons["Continue"]
        if tip.waitForExistence(timeout: 1) { tip.tap() }
        searchInput.typeText(repoFullName)

        let searchButton = app.buttons["Search"]
        guard searchButton.waitForExistence(timeout: 5) else { return false }
        searchButton.tap()

        let repoCard = app.buttons.containing(NSPredicate(format: "label CONTAINS %@", repoFullName)).firstMatch
        guard repoCard.waitForExistence(timeout: 20) else { return false }
        beforeOpenRepo?()
        repoCard.tap()
        return app.buttons["Back"].waitForExistence(timeout: 20)
    }

    /// Opens the first pull request (via the "All" filter so closed/merged PRs work too) from the open repository detail.
    @discardableResult
    func openFirstPullRequestFromRepoDetail(
        _ app: XCUIApplication,
        repoFullName: String = E2E.targetRepo
    ) -> Bool {
        let pullsTab = app.buttons.containing(NSPredicate(format: "label == 'PRs' OR label BEGINSWITH 'PRs ('")).firstMatch
        guard pullsTab.waitForExistence(timeout: 10) else { return false }
        pullsTab.tap()

        let allFilter = app.buttons["All"]
        if allFilter.waitForExistence(timeout: 10) {
            allFilter.tap()
        }

        let itemCard = app.buttons.containing(NSPredicate(format: "label CONTAINS %@", "\(repoFullName) #")).firstMatch
        guard itemCard.waitForExistence(timeout: 20) else { return false }
        itemCard.tap()
        return app.buttons["Open in browser"].waitForExistence(timeout: 20)
    }
}
