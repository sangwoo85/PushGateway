import UIKit
import UserNotifications

@MainActor
final class NotificationRegistrationManager: ObservableObject {
    static let shared = NotificationRegistrationManager()

    enum RegistrationState: Equatable {
        case preparing
        case ready
        case permissionDenied
        case configurationMissing
        case failed(String)

        var message: String {
            switch self {
            case .preparing: "알림 설정을 확인하고 있습니다."
            case .ready: "알림을 받을 준비가 되었습니다."
            case .permissionDenied: "알림 권한이 꺼져 있습니다."
            case .configurationMissing: "알림 서비스 설정이 필요합니다."
            case .failed: "알림을 설정하는 중 문제가 발생했습니다."
            }
        }
    }

    @Published private(set) var authorizationStatus: UNAuthorizationStatus = .notDetermined
    @Published private(set) var registrationState: RegistrationState = .preparing
    @Published private(set) var history: [PushHistoryItem] = []
    @Published private(set) var isRefreshingHistory = false

    private let historyStore: LocalNotificationHistoryStore

    init(historyStore: LocalNotificationHistoryStore = LocalNotificationHistoryStore()) {
        self.historyStore = historyStore
        self.history = historyStore.load()
    }

    func start() async {
        let center = UNUserNotificationCenter.current()
        authorizationStatus = await center.notificationSettings().authorizationStatus
        if authorizationStatus == .notDetermined {
            do {
                _ = try await center.requestAuthorization(options: [.alert, .badge, .sound])
                authorizationStatus = await center.notificationSettings().authorizationStatus
            } catch {
                registrationState = .failed(error.localizedDescription)
                return
            }
        }
        guard authorizationStatus == .authorized || authorizationStatus == .provisional else {
            registrationState = .permissionDenied
            return
        }
        UIApplication.shared.registerForRemoteNotifications()
    }

    func receivedFCMRegistrationToken() {
        registrationState = .ready
    }

    func ingestNotification(_ userInfo: [AnyHashable: Any]) {
        history = historyStore.ingest(userInfo: userInfo)
    }

    func setConfigurationMissing() {
        registrationState = .configurationMissing
    }

    func setRegistrationError(_ error: Error) {
        registrationState = .failed(error.localizedDescription)
    }

    func refreshHistory() async {
        guard !isRefreshingHistory else { return }
        isRefreshingHistory = true
        history = historyStore.load()
        isRefreshingHistory = false
    }
}
