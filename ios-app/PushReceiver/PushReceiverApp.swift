import SwiftUI

@main
struct PushReceiverApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @StateObject private var registration = NotificationRegistrationManager.shared

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(registration)
        }
    }
}

