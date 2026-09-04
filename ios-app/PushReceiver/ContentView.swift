import SwiftUI
import UIKit
import UserNotifications

struct ContentView: View {
    @EnvironmentObject private var registration: NotificationRegistrationManager
    @State private var isShowingTopicRegistration = false

    var body: some View {
        ZStack {
            Color.deplYellow.ignoresSafeArea()
            ScrollView {
                VStack(spacing: 0) {
                    header
                    statusCard.padding(.top, 24)
                    registrationButton.padding(.top, 14)
                    historySection.padding(.top, 24)
                    footer
                }
                .padding(.horizontal, 20)
            }
            .refreshable { await registration.refreshHistory() }
        }
        .preferredColorScheme(.light)
        .task { await registration.refreshHistory() }
        .sheet(isPresented: $isShowingTopicRegistration) {
            TopicRegistrationView()
        }
    }

    private var registrationButton: some View {
        Button {
            isShowingTopicRegistration = true
        } label: {
            HStack(spacing: 10) {
                Image(systemName: "qrcode.viewfinder")
                    .font(.system(size: 19, weight: .semibold))
                VStack(alignment: .leading, spacing: 2) {
                    Text("QR로 기기 등록")
                        .font(.system(size: 16, weight: .bold))
                    Text("개인 · 부서 · 공지 알림 연결")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(.white.opacity(0.66))
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(.white.opacity(0.55))
            }
            .foregroundStyle(.white)
            .padding(17)
            .background(.black, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
    }

    private var header: some View {
        HStack {
            DEPLWordmark()
            Spacer()
            Text("알림 전용")
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(Color.black.opacity(0.56))
                .padding(.horizontal, 10)
                .padding(.vertical, 7)
                .background(.white.opacity(0.48), in: Capsule())
        }
        .padding(.top, 10)
        .padding(.horizontal, 5)
    }

    private var statusCard: some View {
        HStack(spacing: 16) {
            ZStack(alignment: .topTrailing) {
                Circle().fill(Color.deplSoftYellow).frame(width: 64, height: 64)
                Image(systemName: statusIcon)
                    .font(.system(size: 27, weight: .semibold))
                    .foregroundStyle(.black)
                    .frame(width: 64, height: 64)
                if registration.registrationState == .ready {
                    Circle()
                        .fill(Color.deplBlue)
                        .frame(width: 16, height: 16)
                        .overlay(Circle().stroke(.white, lineWidth: 3))
                }
            }
            VStack(alignment: .leading, spacing: 6) {
                Text(registration.registrationState.message)
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(.black)
                Text("알림 권한  ·  \(authorizationText)")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Color.black.opacity(0.48))
                if registration.registrationState == .permissionDenied {
                    Button("시스템 설정 열기") {
                        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
                        UIApplication.shared.open(url)
                    }
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 9)
                    .background(.black, in: RoundedRectangle(cornerRadius: 10))
                    .padding(.top, 4)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(18)
        .frame(maxWidth: .infinity)
        .background(.white, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .shadow(color: .black.opacity(0.08), radius: 16, y: 7)
    }

    private var historySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("알림 내역").font(.system(size: 21, weight: .bold))
                Spacer()
                if !registration.history.isEmpty {
                    Text("\(registration.history.count.formatted())개")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(Color.black.opacity(0.48))
                }
                if registration.isRefreshingHistory { ProgressView().tint(.black) }
            }
            .padding(.horizontal, 5)

            Group {
                if registration.history.isEmpty {
                    VStack(spacing: 10) {
                        Image(systemName: "tray").font(.system(size: 25, weight: .medium))
                        Text("도착한 알림이 없습니다.").font(.system(size: 14, weight: .semibold))
                    }
                    .foregroundStyle(Color.black.opacity(0.42))
                    .frame(maxWidth: .infinity, minHeight: 104)
                } else {
                    LazyVStack(spacing: 0) {
                        ForEach(registration.history) { item in
                            NotificationHistoryRow(item: item)
                            Divider().padding(.leading, 58)
                        }
                    }
                }
            }
            .background(.white, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .shadow(color: .black.opacity(0.07), radius: 16, y: 7)
        }
    }

    private var footer: some View {
        HStack(spacing: 7) {
            Circle().fill(Color.deplRed).frame(width: 7, height: 7)
            Text("업무 상세 없이 알림 유형과 수신 시각만 표시합니다.")
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(Color.black.opacity(0.52))
            Circle().fill(Color.deplBlue).frame(width: 7, height: 7)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 26)
    }

    private var authorizationText: String {
        switch registration.authorizationStatus {
        case .authorized: "허용"
        case .denied: "거부"
        case .provisional: "임시 허용"
        case .ephemeral: "일시 허용"
        case .notDetermined: "결정되지 않음"
        @unknown default: "알 수 없음"
        }
    }

    private var statusIcon: String {
        registration.registrationState == .ready ? "bell.badge.fill" : "bell.slash"
    }
}

private struct NotificationHistoryRow: View {
    let item: PushHistoryItem

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: iconName)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(.black)
                .frame(width: 38, height: 38)
                .background(iconColor.opacity(0.17), in: Circle())
            VStack(alignment: .leading, spacing: 5) {
                Text(item.message)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(.black)
                    .fixedSize(horizontal: false, vertical: true)
                Text(item.sentAt.formatted(date: .abbreviated, time: .shortened))
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Color.black.opacity(0.42))
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 15)
    }

    private var iconName: String {
        switch item.notificationType {
        case .commentAdded, .commentMentioned: "text.bubble.fill"
        case .taskMentioned: "at"
        case .sourceConflict: "arrow.triangle.branch"
        case .noticeRegistered: "megaphone.fill"
        case .taskArrived: "tray.and.arrow.down.fill"
        case .approvalTaskArrived: "checkmark.seal.fill"
        case .mentionedTaskDeployed: "shippingbox.fill"
        }
    }

    private var iconColor: Color {
        switch item.notificationType {
        case .sourceConflict, .approvalTaskArrived: .deplRed
        default: .deplBlue
        }
    }
}

extension Color {
    static let deplYellow = Color(red: 0.97, green: 0.84, blue: 0.27)
    static let deplSoftYellow = Color(red: 1.0, green: 0.95, blue: 0.75)
    static let deplRed = Color(red: 0.91, green: 0.18, blue: 0.20)
    static let deplBlue = Color(red: 0.14, green: 0.50, blue: 0.75)
}

private struct DEPLWordmark: View {
    var body: some View {
        HStack(alignment: .center, spacing: 7) {
            Text("DEPL")
                .font(.system(size: 25, weight: .bold, design: .rounded))
                .tracking(0.2)
                .foregroundStyle(.black)
            E9paySymbol()
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("DEPL")
    }
}

private struct E9paySymbol: View {
    private var symbolImage: UIImage? {
        guard let url = Bundle.main.url(forResource: "E9pay-Dots-Extracted", withExtension: "png") else {
            return nil
        }
        return UIImage(contentsOfFile: url.path)
    }

    var body: some View {
        Group {
            if let symbolImage {
                Image(uiImage: symbolImage)
                    .resizable()
                    .scaledToFit()
            } else {
                // 리소스가 누락되어도 브랜드 심볼의 기본 형태는 유지한다.
                ZStack {
                    Circle().fill(Color.deplBlue).frame(width: 6, height: 6).offset(x: -7, y: 8)
                    Circle().fill(Color.deplBlue).frame(width: 8, height: 8)
                    Circle().fill(Color.deplRed).frame(width: 11, height: 11).offset(x: 8, y: -9)
                }
            }
        }
        .frame(width: 27, height: 27)
    }
}
