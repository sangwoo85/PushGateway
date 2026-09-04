import CryptoKit
import FirebaseCore
import FirebaseMessaging
import Foundation
import Security

struct EnrollmentTopics: Codable, Equatable, Sendable {
    let user: String
    let department: String
    let notice: String

    var all: [(TopicKind, String)] {
        [(.user, user), (.department, department), (.notice, notice)]
    }
}

struct EnrollmentQRPayload: Codable, Equatable, Sendable {
    let version: Int
    let firebaseProjectId: String
    let topics: EnrollmentTopics
    let issuedAt: String
    let expiresAt: String
    let nonce: String
    let signature: String

    var canonicalMessage: Data {
        let value = [
            String(version), firebaseProjectId, topics.user, topics.department,
            topics.notice, issuedAt, expiresAt, nonce
        ].joined(separator: "|")
        return Data(value.utf8)
    }
}

enum TopicKind: String, CaseIterable, Sendable {
    case user
    case department
    case notice

    var title: String {
        switch self {
        case .user: "개인 알림"
        case .department: "부서 알림"
        case .notice: "전체 공지"
        }
    }
}

enum EnrollmentError: LocalizedError, Equatable {
    case malformedQR
    case wrongVersion
    case wrongProject
    case invalidDate
    case expired
    case issuedInFuture
    case invalidNonce
    case invalidTopic
    case publicKeyMissing
    case invalidPublicKey
    case invalidSignature
    case firebaseUnavailable
    case subscriptionFailed(TopicKind)
    case resetFailed

    var errorDescription: String? {
        switch self {
        case .malformedQR: "QR 형식이 올바르지 않습니다."
        case .wrongVersion, .wrongProject: "다른 시스템에서 발급한 QR 코드입니다."
        case .invalidDate: "QR 코드의 발급 시간을 확인할 수 없습니다."
        case .expired: "등록 QR의 유효시간이 만료되었습니다."
        case .issuedInFuture: "QR 코드의 발급 시간이 올바르지 않습니다."
        case .invalidNonce, .invalidTopic: "QR 형식이 올바르지 않습니다."
        case .publicKeyMissing: "등록용 보안키가 설정되지 않았습니다."
        case .invalidPublicKey, .invalidSignature: "QR 코드의 전자서명을 확인할 수 없습니다."
        case .firebaseUnavailable: "Firebase 설정 파일이 필요합니다."
        case .subscriptionFailed(let kind): "\(kind.title) 구독에 실패했습니다."
        case .resetFailed: "기기 등록 초기화에 실패했습니다. 네트워크 연결을 확인해 주세요."
        }
    }
}

protocol EnrollmentSignatureVerifying: Sendable {
    func verify(message: Data, signatureBase64: String, publicKeyBase64: String) throws -> Bool
}

struct P256EnrollmentSignatureVerifier: EnrollmentSignatureVerifying {
    func verify(message: Data, signatureBase64: String, publicKeyBase64: String) throws -> Bool {
        guard let publicKeyData = Data(base64Encoded: publicKeyBase64) else {
            throw EnrollmentError.invalidPublicKey
        }
        let publicKey: P256.Signing.PublicKey
        do {
            publicKey = try P256.Signing.PublicKey(x963Representation: publicKeyData)
        } catch {
            throw EnrollmentError.invalidPublicKey
        }
        guard let signatureData = Data(base64Encoded: signatureBase64) else {
            throw EnrollmentError.invalidSignature
        }
        let signature: P256.Signing.ECDSASignature
        do {
            signature = try P256.Signing.ECDSASignature(derRepresentation: signatureData)
        } catch {
            throw EnrollmentError.invalidSignature
        }
        return publicKey.isValidSignature(signature, for: message)
    }
}

struct EnrollmentQRValidator {
    private let signatureVerifier: any EnrollmentSignatureVerifying
    private let decoder = JSONDecoder()
    private let formatter = ISO8601DateFormatter()

    init(signatureVerifier: any EnrollmentSignatureVerifying = P256EnrollmentSignatureVerifier()) {
        self.signatureVerifier = signatureVerifier
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    }

    func validate(
        _ rawValue: String,
        expectedProjectID: String?,
        publicKeyBase64: String?,
        now: Date = Date()
    ) throws -> EnrollmentQRPayload {
        guard let data = rawValue.data(using: .utf8),
              let payload = try? decoder.decode(EnrollmentQRPayload.self, from: data) else {
            throw EnrollmentError.malformedQR
        }
        guard payload.version == 1 else { throw EnrollmentError.wrongVersion }
        guard let expectedProjectID, !expectedProjectID.isEmpty,
              payload.firebaseProjectId == expectedProjectID else {
            throw EnrollmentError.wrongProject
        }
        guard let issued = parseDate(payload.issuedAt), let expires = parseDate(payload.expiresAt),
              issued <= expires else { throw EnrollmentError.invalidDate }
        guard issued <= now.addingTimeInterval(60) else { throw EnrollmentError.issuedInFuture }
        guard expires > now else { throw EnrollmentError.expired }
        guard expires.timeIntervalSince(issued) <= 600 else { throw EnrollmentError.invalidDate }
        guard payload.nonce.count >= 16 else { throw EnrollmentError.invalidNonce }
        guard isValidTopic(payload.topics.user, prefix: "usr_", minimumSuffixLength: 32),
              isValidTopic(payload.topics.department, prefix: "dept_", minimumSuffixLength: 32),
              payload.topics.notice == "notice_all" else {
            throw EnrollmentError.invalidTopic
        }
        guard let publicKeyBase64, !publicKeyBase64.isEmpty else {
            throw EnrollmentError.publicKeyMissing
        }
        guard try signatureVerifier.verify(
            message: payload.canonicalMessage,
            signatureBase64: payload.signature,
            publicKeyBase64: publicKeyBase64
        ) else { throw EnrollmentError.invalidSignature }
        return payload
    }

    private func parseDate(_ value: String) -> Date? {
        if let date = formatter.date(from: value) { return date }
        let fallback = ISO8601DateFormatter()
        fallback.formatOptions = [.withInternetDateTime]
        return fallback.date(from: value)
    }

    private func isValidTopic(_ topic: String, prefix: String, minimumSuffixLength: Int) -> Bool {
        guard topic.hasPrefix(prefix), topic.count >= prefix.count + minimumSuffixLength else { return false }
        return topic.range(of: "^[A-Za-z0-9._~-]+$", options: .regularExpression) != nil
    }
}

@MainActor
protocol TopicSubscribing: AnyObject {
    func ensureRegistrationToken() async throws
    func subscribe(to topic: String) async throws
    func unsubscribe(from topic: String) async throws
}

@MainActor
final class FCMTopicSubscriptionService: TopicSubscribing {
    func ensureRegistrationToken() async throws {
        guard FirebaseApp.app() != nil else { throw EnrollmentError.firebaseUnavailable }
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            Messaging.messaging().register { error in
                if let error { continuation.resume(throwing: error) }
                else { continuation.resume(returning: ()) }
            }
        }
    }

    func subscribe(to topic: String) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            Messaging.messaging().subscribe(toTopic: topic) { error in
                if let error { continuation.resume(throwing: error) }
                else { continuation.resume(returning: ()) }
            }
        }
    }

    func unsubscribe(from topic: String) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            Messaging.messaging().unsubscribe(fromTopic: topic) { error in
                if let error { continuation.resume(throwing: error) }
                else { continuation.resume(returning: ()) }
            }
        }
    }
}

struct TopicRegistrationRecord: Codable, Equatable, Sendable {
    var current: EnrollmentTopics?
    var pending: EnrollmentTopics?
    var cleanup: [String]

    static let empty = TopicRegistrationRecord(current: nil, pending: nil, cleanup: [])
}

@MainActor
protocol TopicRegistrationStoring: AnyObject {
    func load() -> TopicRegistrationRecord
    func save(_ record: TopicRegistrationRecord) throws
    func clear() throws
}

@MainActor
final class KeychainTopicRegistrationStore: TopicRegistrationStoring {
    private let service = "kr.co.e9pay.depl.topic-registration"
    private let account = "active-topics"

    func load() -> TopicRegistrationRecord {
        var query = baseQuery
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data,
              let record = try? JSONDecoder().decode(TopicRegistrationRecord.self, from: data) else {
            return .empty
        }
        return record
    }

    func save(_ record: TopicRegistrationRecord) throws {
        let data = try JSONEncoder().encode(record)
        let status = SecItemUpdate(
            baseQuery as CFDictionary,
            [kSecValueData as String: data] as CFDictionary
        )
        if status == errSecItemNotFound {
            var query = baseQuery
            query[kSecValueData as String] = data
            let addStatus = SecItemAdd(query as CFDictionary, nil)
            guard addStatus == errSecSuccess else { throw KeychainError.status(addStatus) }
        } else if status != errSecSuccess {
            throw KeychainError.status(status)
        }
    }

    func clear() throws {
        let status = SecItemDelete(baseQuery as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw KeychainError.status(status)
        }
    }

    private var baseQuery: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
    }

    private enum KeychainError: Error { case status(OSStatus) }
}

@MainActor
final class TopicRegistrationCoordinator {
    private let service: any TopicSubscribing
    private let store: any TopicRegistrationStoring

    init(service: any TopicSubscribing, store: any TopicRegistrationStoring) {
        self.service = service
        self.store = store
    }

    var record: TopicRegistrationRecord { store.load() }

    func register(
        topics: EnrollmentTopics,
        status: @escaping (TopicKind, Bool?) -> Void
    ) async throws {
        try await service.ensureRegistrationToken()
        var record = store.load()
        record.pending = topics
        try store.save(record)

        var firstFailure: TopicKind?
        for (kind, topic) in topics.all {
            status(kind, nil)
            do {
                try await retry { try await self.service.subscribe(to: topic) }
                status(kind, true)
            } catch {
                status(kind, false)
                if firstFailure == nil { firstFailure = kind }
            }
        }
        if let firstFailure { throw EnrollmentError.subscriptionFailed(firstFailure) }

        let previous = record.current
        let newValues = Set(topics.all.map(\.1))
        let obsolete = previous?.all.map(\.1).filter { !newValues.contains($0) } ?? []
        record.current = topics
        record.pending = nil
        record.cleanup = Array(Set(record.cleanup + obsolete))
        try store.save(record)
        await cleanObsoleteTopics()
    }

    func resumePending(status: @escaping (TopicKind, Bool?) -> Void) async throws {
        let record = store.load()
        if let pending = record.pending {
            try await register(topics: pending, status: status)
        } else {
            await cleanObsoleteTopics()
        }
    }

    func reset() async throws {
        let record = store.load()
        let topics = Set((record.current?.all.map(\.1) ?? []) + (record.pending?.all.map(\.1) ?? []) + record.cleanup)
        var failed = false
        for topic in topics {
            do { try await retry { try await self.service.unsubscribe(from: topic) } }
            catch { failed = true }
        }
        guard !failed else { throw EnrollmentError.resetFailed }
        try store.clear()
    }

    private func cleanObsoleteTopics() async {
        var record = store.load()
        guard !record.cleanup.isEmpty else { return }
        var remaining: [String] = []
        for topic in record.cleanup {
            do { try await retry { try await self.service.unsubscribe(from: topic) } }
            catch { remaining.append(topic) }
        }
        record.cleanup = remaining
        try? store.save(record)
    }

    private func retry(_ operation: () async throws -> Void) async throws {
        var lastError: Error = EnrollmentError.firebaseUnavailable
        for attempt in 0..<3 {
            do { try await operation(); return }
            catch {
                lastError = error
                if attempt < 2 {
                    try? await Task.sleep(for: .seconds(pow(2.0, Double(attempt))))
                }
            }
        }
        throw lastError
    }
}

@MainActor
final class TopicRegistrationViewModel: ObservableObject {
    enum ScreenState: Equatable {
        case ready
        case validating
        case subscribing
        case success
        case failure(String)
    }

    enum SubscriptionStatus: Equatable {
        case notStarted
        case working
        case success
        case failure
    }

    @Published private(set) var state: ScreenState = .ready
    @Published private(set) var topicStatus: [TopicKind: SubscriptionStatus] = [:]
    @Published private(set) var hasRegistration = false
    @Published private(set) var isScanLocked = false

    private let validator: EnrollmentQRValidator
    private let coordinator: TopicRegistrationCoordinator
    private let bundle: Bundle

    init(
        validator: EnrollmentQRValidator = EnrollmentQRValidator(),
        service: any TopicSubscribing = FCMTopicSubscriptionService(),
        store: any TopicRegistrationStoring = KeychainTopicRegistrationStore(),
        bundle: Bundle = .main
    ) {
        self.validator = validator
        self.coordinator = TopicRegistrationCoordinator(service: service, store: store)
        self.bundle = bundle
        self.hasRegistration = coordinator.record.current != nil
        TopicKind.allCases.forEach { topicStatus[$0] = .notStarted }
    }

    func process(scannedValue: String) async {
        guard !isScanLocked else { return }
        isScanLocked = true
        state = .validating
        do {
            let projectID = FirebaseApp.app()?.options.projectID
                ?? bundle.object(forInfoDictionaryKey: "FIREBASE_PROJECT_ID") as? String
            let publicKey = bundle.object(forInfoDictionaryKey: "ENROLLMENT_PUBLIC_KEY_X963_BASE64") as? String
            let payload = try validator.validate(
                scannedValue,
                expectedProjectID: projectID,
                publicKeyBase64: publicKey
            )
            state = .subscribing
            try await coordinator.register(topics: payload.topics) { [weak self] kind, result in
                self?.topicStatus[kind] = result.map { $0 ? .success : .failure } ?? .working
            }
            hasRegistration = true
            state = .success
        } catch {
            state = .failure((error as? LocalizedError)?.errorDescription ?? "네트워크 연결을 확인한 후 다시 시도해 주세요.")
        }
        isScanLocked = false
    }

    func resumePending() async {
        guard coordinator.record.pending != nil else { return }
        state = .subscribing
        do {
            try await coordinator.resumePending { [weak self] kind, result in
                self?.topicStatus[kind] = result.map { $0 ? .success : .failure } ?? .working
            }
            hasRegistration = coordinator.record.current != nil
            state = .success
        } catch {
            state = .failure((error as? LocalizedError)?.errorDescription ?? "네트워크 연결을 확인한 후 다시 시도해 주세요.")
        }
    }

    func reset() async {
        state = .subscribing
        do {
            try await coordinator.reset()
            hasRegistration = false
            TopicKind.allCases.forEach { topicStatus[$0] = .notStarted }
            state = .ready
        } catch {
            state = .failure((error as? LocalizedError)?.errorDescription ?? EnrollmentError.resetFailed.localizedDescription)
        }
    }

    func scanAgain() {
        state = .ready
        isScanLocked = false
    }
}
