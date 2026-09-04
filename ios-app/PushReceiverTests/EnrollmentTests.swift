import XCTest
@testable import PushReceiver

final class EnrollmentQRValidatorTests: XCTestCase {
    private let now = Date(timeIntervalSince1970: 1_800_000_000)

    func testValidQR() throws {
        let payload = makePayload()
        let validator = EnrollmentQRValidator(signatureVerifier: MockSignatureVerifier(isValid: true))
        let decoded = try validator.validate(
            encode(payload), expectedProjectID: "depl-test", publicKeyBase64: "configured", now: now
        )
        XCTAssertEqual(decoded.topics, payload.topics)
    }

    func testMalformedQR() {
        assertError(.malformedQR, raw: "not-json")
    }

    func testMissingRequiredField() {
        assertError(.malformedQR, raw: "{\"version\":1}")
    }

    func testWrongProject() {
        assertError(.wrongProject, payload: makePayload(projectID: "another-project"))
    }

    func testExpiredQR() {
        assertError(
            .expired,
            payload: makePayload(issuedAt: now.addingTimeInterval(-300), expiresAt: now.addingTimeInterval(-1))
        )
    }

    func testFutureQR() {
        assertError(
            .issuedInFuture,
            payload: makePayload(issuedAt: now.addingTimeInterval(61), expiresAt: now.addingTimeInterval(180))
        )
    }

    func testInvalidUserTopic() {
        let topics = EnrollmentTopics(
            user: "usr_10025",
            department: "dept_abcdefghijklmnopqrstuvwxyz123456",
            notice: "notice_all"
        )
        assertError(.invalidTopic, payload: makePayload(topics: topics))
    }

    func testInvalidDepartmentTopic() {
        let topics = EnrollmentTopics(
            user: "usr_abcdefghijklmnopqrstuvwxyz123456",
            department: "dept_finance",
            notice: "notice_all"
        )
        assertError(.invalidTopic, payload: makePayload(topics: topics))
    }

    func testInvalidNoticeTopic() {
        let topics = EnrollmentTopics(
            user: "usr_abcdefghijklmnopqrstuvwxyz123456",
            department: "dept_abcdefghijklmnopqrstuvwxyz123456",
            notice: "notice_admin"
        )
        assertError(.invalidTopic, payload: makePayload(topics: topics))
    }

    func testInvalidSignature() {
        let validator = EnrollmentQRValidator(signatureVerifier: MockSignatureVerifier(isValid: false))
        XCTAssertThrowsError(
            try validator.validate(
                encode(makePayload()), expectedProjectID: "depl-test", publicKeyBase64: "configured", now: now
            )
        ) { XCTAssertEqual($0 as? EnrollmentError, .invalidSignature) }
    }

    func testMissingPublicKey() {
        let validator = EnrollmentQRValidator(signatureVerifier: MockSignatureVerifier(isValid: true))
        XCTAssertThrowsError(
            try validator.validate(
                encode(makePayload()), expectedProjectID: "depl-test", publicKeyBase64: "", now: now
            )
        ) { XCTAssertEqual($0 as? EnrollmentError, .publicKeyMissing) }
    }

    private func assertError(_ expected: EnrollmentError, payload: EnrollmentQRPayload? = nil, raw: String? = nil) {
        let validator = EnrollmentQRValidator(signatureVerifier: MockSignatureVerifier(isValid: true))
        XCTAssertThrowsError(
            try validator.validate(
                raw ?? encode(payload ?? makePayload()),
                expectedProjectID: "depl-test",
                publicKeyBase64: "configured",
                now: now
            )
        ) { XCTAssertEqual($0 as? EnrollmentError, expected) }
    }

    private func makePayload(
        projectID: String = "depl-test",
        issuedAt: Date? = nil,
        expiresAt: Date? = nil,
        topics: EnrollmentTopics? = nil
    ) -> EnrollmentQRPayload {
        EnrollmentQRPayload(
            version: 1,
            firebaseProjectId: projectID,
            topics: topics ?? EnrollmentTopics(
                user: "usr_abcdefghijklmnopqrstuvwxyz123456",
                department: "dept_abcdefghijklmnopqrstuvwxyz123456",
                notice: "notice_all"
            ),
            issuedAt: iso(issuedAt ?? now.addingTimeInterval(-10)),
            expiresAt: iso(expiresAt ?? now.addingTimeInterval(180)),
            nonce: "0123456789abcdef",
            signature: "signature"
        )
    }

    private func iso(_ date: Date) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.string(from: date)
    }

    private func encode(_ payload: EnrollmentQRPayload) -> String {
        String(data: try! JSONEncoder().encode(payload), encoding: .utf8)!
    }
}

@MainActor
final class TopicRegistrationCoordinatorTests: XCTestCase {
    func testSubscribesAllTopics() async throws {
        let service = MockTopicService()
        let store = MockTopicStore()
        let coordinator = TopicRegistrationCoordinator(service: service, store: store)
        let topics = makeTopics("new")

        try await coordinator.register(topics: topics) { _, _ in }

        XCTAssertEqual(Set(service.subscribed), Set(topics.all.map(\.1)))
        XCTAssertEqual(store.record.current, topics)
        XCTAssertNil(store.record.pending)
    }

    func testRetriesFailedSubscription() async throws {
        let service = MockTopicService()
        service.failuresRemaining["notice_all"] = 1
        let store = MockTopicStore()
        let coordinator = TopicRegistrationCoordinator(service: service, store: store)

        try await coordinator.register(topics: makeTopics("retry")) { _, _ in }

        XCTAssertEqual(service.attempts["notice_all"], 2)
    }

    func testSubscribesNewBeforeUnsubscribingOld() async throws {
        let service = MockTopicService()
        let old = makeTopics("old")
        let store = MockTopicStore(record: TopicRegistrationRecord(current: old, pending: nil, cleanup: []))
        let coordinator = TopicRegistrationCoordinator(service: service, store: store)
        let new = makeTopics("new")

        try await coordinator.register(topics: new) { _, _ in }

        XCTAssertEqual(Set(service.unsubscribed), Set([old.user, old.department]))
        XCTAssertEqual(store.record.current, new)
    }

    func testPartialFailureKeepsPendingRegistration() async {
        let service = MockTopicService()
        service.permanentFailureTopics.insert("notice_all")
        let store = MockTopicStore()
        let coordinator = TopicRegistrationCoordinator(service: service, store: store)

        do {
            try await coordinator.register(topics: makeTopics("partial")) { _, _ in }
            XCTFail("Expected registration failure")
        } catch {
            XCTAssertEqual(error as? EnrollmentError, .subscriptionFailed(.notice))
            XCTAssertNotNil(store.record.pending)
            XCTAssertNil(store.record.current)
        }
    }

    func testResetUnsubscribesAndClearsRegistration() async throws {
        let service = MockTopicService()
        let topics = makeTopics("reset")
        let store = MockTopicStore(record: TopicRegistrationRecord(current: topics, pending: nil, cleanup: []))
        let coordinator = TopicRegistrationCoordinator(service: service, store: store)

        try await coordinator.reset()

        XCTAssertEqual(Set(service.unsubscribed), Set(topics.all.map(\.1)))
        XCTAssertEqual(store.record, .empty)
    }

    private func makeTopics(_ marker: String) -> EnrollmentTopics {
        let suffix = marker + String(repeating: "x", count: 36)
        return EnrollmentTopics(user: "usr_\(suffix)", department: "dept_\(suffix)", notice: "notice_all")
    }
}

@MainActor
final class LocalNotificationHistoryStoreTests: XCTestCase {
    func testDuplicateEventIsStoredOnce() {
        let suiteName = "LocalNotificationHistoryStoreTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let store = LocalNotificationHistoryStore(defaults: defaults)
        let eventId = UUID().uuidString
        let payload: [AnyHashable: Any] = ["eventId": eventId, "notificationType": "TASK_ARRIVED"]

        _ = store.ingest(userInfo: payload)
        _ = store.ingest(userInfo: payload)

        XCTAssertEqual(store.load().count, 1)
    }

    func testUnknownNotificationTypeIsIgnored() {
        let suiteName = "LocalNotificationHistoryStoreTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let store = LocalNotificationHistoryStore(defaults: defaults)

        _ = store.ingest(userInfo: ["eventId": UUID().uuidString, "notificationType": "UNKNOWN"])

        XCTAssertTrue(store.load().isEmpty)
    }

    func testHistoryKeepsNewestItemsWithinConfiguredLimit() {
        let suiteName = "LocalNotificationHistoryStoreTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let store = LocalNotificationHistoryStore(defaults: defaults, maximumCount: 3)
        let eventIds = (0..<4).map { _ in UUID() }

        for eventId in eventIds {
            _ = store.ingest(userInfo: [
                "eventId": eventId.uuidString,
                "notificationType": "TASK_ARRIVED"
            ])
        }

        XCTAssertEqual(store.load().map(\.eventId), Array(eventIds.reversed().prefix(3)))
    }
}

private struct MockSignatureVerifier: EnrollmentSignatureVerifying {
    let isValid: Bool
    func verify(message: Data, signatureBase64: String, publicKeyBase64: String) throws -> Bool { isValid }
}

@MainActor
private final class MockTopicService: TopicSubscribing {
    var subscribed: [String] = []
    var unsubscribed: [String] = []
    var attempts: [String: Int] = [:]
    var failuresRemaining: [String: Int] = [:]
    var permanentFailureTopics: Set<String> = []

    func ensureRegistrationToken() async throws {}

    func subscribe(to topic: String) async throws {
        attempts[topic, default: 0] += 1
        if permanentFailureTopics.contains(topic) { throw TestError.expected }
        if failuresRemaining[topic, default: 0] > 0 {
            failuresRemaining[topic, default: 0] -= 1
            throw TestError.expected
        }
        subscribed.append(topic)
    }

    func unsubscribe(from topic: String) async throws { unsubscribed.append(topic) }
    private enum TestError: Error { case expected }
}

@MainActor
private final class MockTopicStore: TopicRegistrationStoring {
    var record: TopicRegistrationRecord
    init(record: TopicRegistrationRecord = .empty) { self.record = record }
    func load() -> TopicRegistrationRecord { record }
    func save(_ record: TopicRegistrationRecord) throws { self.record = record }
    func clear() throws { record = .empty }
}
