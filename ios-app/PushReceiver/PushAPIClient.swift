import Foundation

struct PushHistoryItem: Codable, Identifiable, Equatable, Sendable {
    let eventId: UUID
    let notificationType: NotificationType
    let actorName: String?
    let sentAt: Date

    var id: UUID { eventId }

    var message: String {
        switch notificationType {
        case .commentAdded: "본인 업무에 댓글이 작성 되었습니다."
        case .taskMentioned: "\(actorName ?? "담당자") 님이 업무에 당신을 언급하였습니다."
        case .commentMentioned: "\(actorName ?? "담당자") 님이 댓글에 당신을 언급 하였습니다."
        case .sourceConflict: "소스 겹침 알림"
        case .noticeRegistered: "공지 사항이 등록 되었습니다."
        case .taskArrived: "업무가 도착 했습니다."
        case .approvalTaskArrived: "결재할 업무가 도착 했습니다."
        case .mentionedTaskDeployed: "당신이 언급된 업무가 운영에 반영 되었습니다."
        }
    }

    enum NotificationType: String, Codable, Sendable {
        case commentAdded = "COMMENT_ADDED"
        case taskMentioned = "TASK_MENTIONED"
        case commentMentioned = "COMMENT_MENTIONED"
        case sourceConflict = "SOURCE_CONFLICT"
        case noticeRegistered = "NOTICE_REGISTERED"
        case taskArrived = "TASK_ARRIVED"
        case approvalTaskArrived = "APPROVAL_TASK_ARRIVED"
        case mentionedTaskDeployed = "MENTIONED_TASK_DEPLOYED"

        init?(payloadValue: String) {
            switch payloadValue {
            case "COMMENT_ADDED", "TASK_COMMENT_CREATED": self = .commentAdded
            case "TASK_MENTIONED": self = .taskMentioned
            case "COMMENT_MENTIONED": self = .commentMentioned
            case "SOURCE_CONFLICT", "SOURCE_OVERLAP": self = .sourceConflict
            case "NOTICE_REGISTERED", "NOTICE_CREATED": self = .noticeRegistered
            case "TASK_ARRIVED": self = .taskArrived
            case "APPROVAL_TASK_ARRIVED": self = .approvalTaskArrived
            case "MENTIONED_TASK_DEPLOYED": self = .mentionedTaskDeployed
            default: return nil
            }
        }
    }
}

@MainActor
final class LocalNotificationHistoryStore {
    private enum Backend {
        case file(URL)
        case defaults(UserDefaults)
    }

    private let backend: Backend
    private let storageKey = "depl.notification-history.v2"
    private let maximumCount: Int
    private var cachedItems: [PushHistoryItem]?
    private var cachedEventIDs = Set<UUID>()
    private var persistedLineCount = 0

    init(maximumCount: Int = 3_000) {
        let baseURL = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("DEPL", isDirectory: true)
        self.backend = .file(baseURL.appendingPathComponent("notification-history.jsonl"))
        self.maximumCount = maximumCount
    }

    // UserDefaults 백엔드는 기존 데이터 마이그레이션과 단위 테스트에만 사용한다.
    init(defaults: UserDefaults, maximumCount: Int = 3_000) {
        self.backend = .defaults(defaults)
        self.maximumCount = maximumCount
    }

    func load() -> [PushHistoryItem] {
        if let cachedItems { return cachedItems }

        let items: [PushHistoryItem]
        switch backend {
        case .defaults(let defaults):
            guard let data = defaults.data(forKey: storageKey) else {
                cache([])
                return []
            }
            items = Array(((try? decoder.decode([PushHistoryItem].self, from: data)) ?? []).prefix(maximumCount))
        case .file(let url):
            items = loadFromFile(url)
            if items.isEmpty,
               let legacyData = UserDefaults.standard.data(forKey: storageKey),
               let legacyItems = try? decoder.decode([PushHistoryItem].self, from: legacyData) {
                let migrated = Array(legacyItems.prefix(maximumCount))
                try? rewriteFile(at: url, with: migrated)
                cache(migrated)
                return migrated
            }
        }
        cache(items)
        return items
    }

    @discardableResult
    func ingest(userInfo: [AnyHashable: Any]) -> [PushHistoryItem] {
        guard
            let rawEventId = userInfo["eventId"] as? String,
            let eventId = UUID(uuidString: rawEventId),
            let rawType = userInfo["notificationType"] as? String,
            let type = PushHistoryItem.NotificationType(payloadValue: rawType)
        else { return load() }

        var items = load()
        guard !cachedEventIDs.contains(eventId) else { return items }
        let item = PushHistoryItem(
            eventId: eventId,
            notificationType: type,
            actorName: userInfo["actorName"] as? String,
            sentAt: Date()
        )
        items.insert(item, at: 0)
        cachedEventIDs.insert(eventId)
        if items.count > maximumCount, let removed = items.popLast() {
            cachedEventIDs.remove(removed.eventId)
        }
        cachedItems = items

        switch backend {
        case .defaults(let defaults):
            if let data = try? encoder.encode(items) {
                defaults.set(data, forKey: storageKey)
            }
        case .file(let url):
            do {
                try append(item, to: url)
                persistedLineCount += 1
                if persistedLineCount > maximumCount + 250 {
                    try rewriteFile(at: url, with: items)
                }
            } catch {
                // 다음 알림 수신 또는 앱 재실행 시 다시 저장할 수 있도록 메모리 목록은 유지한다.
            }
        }
        return items
    }

    private func loadFromFile(_ url: URL) -> [PushHistoryItem] {
        guard let data = try? Data(contentsOf: url), !data.isEmpty else { return [] }
        let lines = data.split(separator: 0x0A)
        persistedLineCount = lines.count
        return lines.suffix(maximumCount).reversed().compactMap { line in
            try? decoder.decode(PushHistoryItem.self, from: Data(line))
        }
    }

    private func append(_ item: PushHistoryItem, to url: URL) throws {
        try FileManager.default.createDirectory(
            at: url.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        var line = try encoder.encode(item)
        line.append(0x0A)
        if !FileManager.default.fileExists(atPath: url.path) {
            try line.write(to: url, options: .atomic)
            return
        }
        let handle = try FileHandle(forWritingTo: url)
        defer { try? handle.close() }
        try handle.seekToEnd()
        try handle.write(contentsOf: line)
    }

    private func rewriteFile(at url: URL, with items: [PushHistoryItem]) throws {
        try FileManager.default.createDirectory(
            at: url.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        var data = Data()
        for item in items.reversed() {
            data.append(try encoder.encode(item))
            data.append(0x0A)
        }
        try data.write(to: url, options: .atomic)
        persistedLineCount = items.count
    }

    private func cache(_ items: [PushHistoryItem]) {
        cachedItems = items
        cachedEventIDs = Set(items.map(\.eventId))
    }

    private var encoder: JSONEncoder {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        return encoder
    }

    private var decoder: JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }
}
