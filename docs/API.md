# DEPL Push Gateway API 명세

## 기본 원칙

- 운영 업무 코드는 HTTP Push 발송 API가 아니라 같은 트랜잭션의 `PushQueueService`를 호출합니다.
- `/internal/push-test/**`는 관리자용 HTML 화면이며 `PUSH_TEST_PAGE_ENABLED=true`일 때만 존재합니다.
- `/api/push/devices`는 Topic 전환 이전의 호환 API로 deprecated 상태입니다.
- 인증된 사용자의 ID는 Spring Security `Principal.getName()`에서 가져옵니다.

## 업무 코드 연동 API

```java
UUID enqueueUser(String userId, NotificationType type, String actorName)
UUID enqueueDepartment(String departmentId, NotificationType type, String actorName)
UUID enqueueNotice(NotificationType type, String actorName)
```

기존 업무 변경 메서드가 `@Transactional`이라면 같은 트랜잭션에서 호출합니다. 업무 처리 롤백 시 Queue INSERT도 함께 롤백됩니다.

```java
@Transactional
public void assignTask(String taskId, String assigneeId) {
    // 기존 업무 변경
    pushQueueService.enqueueUser(assigneeId, NotificationType.TASK_ARRIVED, null);
}
```

제약:

- `userId`, `departmentId`: 공백 제외, 최대 128자
- `TASK_MENTIONED`, `COMMENT_MENTIONED`: `actorName` 필수, 최대 50자
- 나머지 유형: `actorName` 전달 금지
- 공지는 `NOTICE / ALL / notice_all`로 내부 정규화

## 관리자 화면

| Method | Path | 설명 | 응답 |
|---|---|---|---|
| `GET` | `/` | 테스트 기능 활성화 시 관리자 화면으로 이동 | 302 |
| `GET` | `/internal/push-test` | QR 등록 화면으로 이동 | 302 |
| `GET` | `/internal/push-test/enrollment` | QR 등록 화면 | HTML |
| `GET` | `/internal/push-test/messages` | 8종 Push 테스트 화면 | HTML |
| `POST` | `/internal/push-test/send` | CSRF 검증 후 Queue 등록 | 결과 화면으로 302 |
| `GET` | `/internal/push-test/events/{eventId}` | Queue/FCM 처리 상태 | HTML |
| `GET` | `/internal/push-test/qr?userId={id}&departmentId={id}` | 서명된 QR | `image/png` |

관리자 화면은 `ROLE_PUSH_ADMIN` 또는 `ROLE_PUSH_TESTER`가 필요합니다. 부서·전체 공지는 다수 대상 확인 체크가 필수이고 관리자별 분당 요청 제한이 적용됩니다. QR 응답은 `Cache-Control: no-store`입니다.

## 알림 이력 API

### 최근 전송 이력

```http
GET /api/push/history?limit=50
```

- 인증: 필수
- `limit`: 1~100으로 보정, 기본 50
- 조회 대상: 로그인한 사용자의 USER 전송 이력

```json
[
  {
    "eventId": "96a25bc0-4477-4a02-ae4b-d13f0fa9cd03",
    "notificationType": "TASK_ARRIVED",
    "actorName": null,
    "sentAt": "2026-09-04T01:22:58.316976Z"
  }
]
```

응답 코드:

- `200 OK`: 정상
- `401 Unauthorized`: Principal 없음

## 레거시 기기 API

Topic 방식에서는 앱이 Gateway로 토큰을 보내지 않으므로 신규 연동에 사용하지 않습니다.

### 기기 등록

```http
POST /api/push/devices
Content-Type: application/json

{
  "registrationId": "<FCM registration token>",
  "appInstanceId": "<opaque application instance id>",
  "appVersion": "1.0.0"
}
```

- `204 No Content`: 등록/갱신
- `400 Bad Request`: 형식 또는 길이 오류
- `401 Unauthorized`: Principal 없음

### 기기 비활성화

```http
DELETE /api/push/devices/{appInstanceId}
```

- `204 No Content`: 로그인 사용자에게 속한 인스턴스 비활성화
- `401 Unauthorized`: Principal 없음

## FCM Payload 계약

Android는 data-only 메시지로 받고, iOS는 동일 data와 APNs alert를 함께 받습니다.

```json
{
  "eventId": "96a25bc0-4477-4a02-ae4b-d13f0fa9cd03",
  "notificationType": "TASK_MENTIONED",
  "title": "업무 알림",
  "body": "홍길동 님이 업무에 당신을 언급하였습니다.",
  "templateVersion": "1"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `eventId` | UUID 문자열 | 예 | 앱 중복 방지 키 |
| `notificationType` | enum 문자열 | 예 | 루트 README의 8종 Payload 값 |
| `title` | 문자열 | 예 | Gateway가 생성한 알림 제목, 최대 50자 |
| `body` | 문자열 | 예 | Gateway가 생성한 승인된 알림 문구, 최대 200자 |
| `templateVersion` | 문자열 | 예 | Gateway 문구 명세 버전 |

앱은 알 수 없는 유형, UUID가 아닌 `eventId`, 제목이나 본문이 없는 신규 Payload를 저장하지 않습니다. 문구의 단일 관리 지점은 Gateway의 `NotificationTemplateFactory`이며 앱은 `title`과 `body`를 그대로 저장·표시합니다. `actorName`은 Gateway 내부에서 언급 문구를 만들 때만 사용하고 FCM data로 별도 전송하지 않습니다.

## QR Payload 계약

QR은 다음 JSON을 담지만 관리자 화면과 로그에는 원문 Topic을 표시하지 않습니다.

```json
{
  "version": 1,
  "firebaseProjectId": "your-project-id",
  "topics": {
    "user": "usr_<opaque-random-value>",
    "department": "dept_<opaque-random-value>",
    "notice": "notice_all"
  },
  "issuedAt": "2026-09-04T01:00:00Z",
  "expiresAt": "2026-09-04T01:03:00Z",
  "nonce": "<random-value>",
  "signature": "<DER-ECDSA-signature-base64>"
}
```

서명 입력 문자열:

```text
version|firebaseProjectId|userTopic|departmentTopic|noticeTopic|issuedAt|expiresAt|nonce
```

- 알고리즘: ECDSA P-256 + SHA-256 (`SHA256withECDSA`)
- 서명: DER bytes의 Base64
- 앱 공개키: uncompressed X9.63 bytes의 Base64
- QR TTL: 최대 10분
