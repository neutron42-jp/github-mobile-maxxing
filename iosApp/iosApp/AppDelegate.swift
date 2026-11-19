import UIKit
import UserNotifications
import ComposeApp

@main
final class AppDelegate: UIResponder, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    /// The same build setting (`APNS_ENVIRONMENT`) fills the `aps-environment` entitlement and this
    /// Info.plist value, so the environment reported to the server matches the token's real one.
    private var apnsEnvironment: String {
        (Bundle.main.object(forInfoDictionaryKey: "MaxxingApnsEnvironment") as? String) ?? "development"
    }

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        guard IosPushFeature.shared.isApnsEnabled else {
            return true
        }
        // The delegate must be set before launch finishes so a notification tap or action that
        // cold-starts the app is delivered.
        UNUserNotificationCenter.current().delegate = self
        IosPushBridge.shared.setApnsEnvironment(environment: apnsEnvironment)
        // Does not prompt: notification permission is requested when enabled in Settings. A device token is
        // returned regardless of the alert permission; failures are logged and never block the app.
        NSLog("Requesting APNs registration (environment %@)", apnsEnvironment)
        application.registerForRemoteNotifications()
        return true
    }

    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(name: "Default Configuration", sessionRole: connectingSceneSession.role)
        configuration.delegateClass = SceneDelegate.self
        return configuration
    }

    // MARK: APNs registration

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        guard IosPushFeature.shared.isApnsEnabled else { return }
        let hex = deviceToken.map { String(format: "%02x", $0) }.joined()
        // Length only: the token itself is not logged.
        NSLog("APNs device token received (%d bytes, environment %@)", deviceToken.count, apnsEnvironment)
        IosPushBridge.shared.onDeviceTokenReceived(tokenHex: hex)
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        guard IosPushFeature.shared.isApnsEnabled else { return }
        // Expected on the simulator / without the entitlement; the app stays fully usable.
        NSLog("APNs registration failed: %@", (error as NSError).description)
        IosPushBridge.shared.onRegistrationFailed(errorMessage: error.localizedDescription)
    }

    // MARK: UNUserNotificationCenterDelegate

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        guard IosPushFeature.shared.isApnsEnabled else {
            completionHandler([])
            return
        }
        completionHandler([.banner, .list, .sound])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        guard IosPushFeature.shared.isApnsEnabled else {
            completionHandler()
            return
        }
        let userInfo = response.notification.request.content.userInfo
        switch response.actionIdentifier {
        case UNNotificationDefaultActionIdentifier:
            _ = IosNotificationActions.shared.handleTap(userInfo: userInfo)
            completionHandler()
        case UNNotificationDismissActionIdentifier:
            completionHandler()
        default:
            // Mark Read / Done. The completion handler is invoked exactly once by the Kotlin side.
            let threadId = (userInfo["thread_id"] as? String) ?? ""
            IosNotificationActions.shared.handle(
                actionId: response.actionIdentifier,
                threadId: threadId,
                completion: {
                    completionHandler()
                }
            )
        }
    }
}
