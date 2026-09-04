import FirebaseCore
import FirebaseMessaging
import UIKit
import UserNotifications

@MainActor
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        if Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil {
            FirebaseApp.configure()
            Messaging.messaging().delegate = self
            Task { await NotificationRegistrationManager.shared.start() }
        } else {
            NotificationRegistrationManager.shared.setConfigurationMissing()
        }
        return true
    }

    func application(_ application: UIApplication,
                     didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        print("[DEPL] APNs registration succeeded")
        Messaging.messaging().apnsToken = deviceToken
    }

    func application(_ application: UIApplication,
                     didFailToRegisterForRemoteNotificationsWithError error: Error) {
        let nsError = error as NSError
        print("[DEPL] APNs registration failed: \(nsError.domain) \(nsError.code) \(nsError.localizedDescription)")
        NotificationRegistrationManager.shared.setRegistrationError(error)
    }

    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        NotificationRegistrationManager.shared.ingestNotification(userInfo)
        completionHandler(.newData)
    }
}

extension AppDelegate: @preconcurrency MessagingDelegate {
    func messaging(_ messaging: Messaging, didReceiveRegistration installationID: String?) {
        guard installationID != nil else { return }
        print("[DEPL] FCM installation registration succeeded")
        NotificationRegistrationManager.shared.receivedFCMRegistrationToken()
    }

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let fcmToken else { return }
        _ = fcmToken
        print("[DEPL] FCM registration succeeded")
        NotificationRegistrationManager.shared.receivedFCMRegistrationToken()
    }
}

extension AppDelegate: @preconcurrency UNUserNotificationCenterDelegate {
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        NotificationRegistrationManager.shared.ingestNotification(notification.request.content.userInfo)
        return [.banner, .sound, .list]
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        // 알림 선택 시 상세 화면이나 외부 URL로 이동하지 않는다.
        NotificationRegistrationManager.shared.ingestNotification(response.notification.request.content.userInfo)
    }
}
