import UIKit
import ComposeApp

final class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        guard let windowScene = scene as? UIWindowScene else { return }
        // Record a cold-start URL before the UI exists; it is applied after sign-in.
        for context in connectionOptions.urlContexts {
            _ = IosDeepLinkBridge.shared.handleUrl(url: context.url.absoluteString)
        }
        #if DEBUG
        // Simulator E2E only: sign in with a token passed in the launch environment (never compiled into Release).
        let env = ProcessInfo.processInfo.environment
        if let token = env["MAXXING_UI_TEST_PAT"], !token.isEmpty {
            let delay = Int64(env["MAXXING_UI_TEST_PAT_DELAY_MS"] ?? "") ?? 0
            IosDebugHooks.shared.signInWithPatForTesting(token: token, delayMillis: delay)
        }
        #endif
        let window = UIWindow(windowScene: windowScene)
        window.rootViewController = ComposeHostController()
        window.makeKeyAndVisible()
        self.window = window
    }

    func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        for context in URLContexts {
            _ = IosDeepLinkBridge.shared.handleUrl(url: context.url.absoluteString)
        }
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
        guard IosPushFeature.shared.isApnsEnabled else { return }
        // Re-evaluate the APNs registration (permission or account may have changed) and ask for a
        // fresh device token, as Apple recommends doing on each launch.
        // The user may have changed the notification permission in the Settings app.
        IosNotificationActions.shared.refreshAuthorizationStatus()
        IosPushBridge.shared.onForegroundResume()
        UIApplication.shared.registerForRemoteNotifications()
    }
}
