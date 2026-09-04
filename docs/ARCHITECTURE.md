# 아키텍처와 처리 보장

## 구성요소

```text
┌──────────────── 폐쇄 업무망 ────────────────┐
│ Spring Boot 업무 시스템                     │
│   └─ 동일 트랜잭션으로 push_queue INSERT    │
│                                             │
│ MariaDB                                     │
│   ├─ push_queue                             │
│   ├─ push_send_history                      │
│   └─ push_topic_binding                     │
│                                             │
│ Push Gateway                                │
│   ├─ Queue Scheduler / Processor            │
│   ├─ Firebase Admin SDK                     │
│   ├─ ECDSA QR 발급                          │
│   └─ 관리자 테스트 화면                     │
└──────────────────┬──────────────────────────┘
                   │ outbound HTTPS 443
                   ▼
                  FCM ──▶ APNs/iOS
                   └────▶ Android
```

앱은 인터넷망에서 Firebase와 통신합니다. 앱이 폐쇄망 Gateway에 접속하거나 토큰을 전달할 필요가 없습니다.

## Outbox 처리 순서

1. 업무 트랜잭션이 업무 데이터와 `push_queue` 행을 함께 commit합니다.
2. Scheduler가 `PENDING`/`RETRY` 행을 `FOR UPDATE SKIP LOCKED`로 선점합니다.
3. 짧은 DB 트랜잭션에서 상태를 `PROCESSING`으로 변경하고 잠금을 해제합니다.
4. Gateway의 중앙 템플릿에서 `title`·`body`를 생성하고, DB 트랜잭션 밖에서 Topic을 찾아 FCM을 호출합니다.
5. FCM 접수 성공 시 별도 트랜잭션에서 history INSERT 후 queue DELETE를 수행합니다.
6. 실패 시 재시도 가능 여부에 따라 `RETRY` 또는 `DEAD`로 전환합니다.

## Queue 상태

```text
PENDING ── claim ──▶ PROCESSING ── FCM accepted ──▶ history INSERT + queue DELETE
   ▲                     │
   │                     ├─ temporary error ──▶ RETRY ──┐
   │                     └─ permanent error ──▶ DEAD    │
   └────────────────────────────────────────────────────┘
```

- stale `PROCESSING`: 설정된 timeout 이후 `RETRY`로 복구
- 기본 최대 시도: 5회
- 기본 재시도 간격: 30초 → 2분 → 5분 → 15분
- 인증 오류, 잘못된 요청, Topic 누락 등 영구 오류: `DEAD`

## 전달 보장과 중복

FCM 호출과 MariaDB commit은 하나의 분산 트랜잭션이 될 수 없으므로 전달 의미는 **at-least-once**입니다. FCM이 요청을 접수한 직후 Gateway가 종료되어 history 기록이 실패하면 같은 `eventId`가 재전송될 수 있습니다.

대응 방법:

- Queue와 history의 `event_id` unique constraint
- 앱 로컬 DB의 `eventId` unique index
- Android는 최대 3,000건을 Paging으로 표시
- FCM `FCM_ACCEPTED`는 단말 표시 완료가 아니라 Firebase 접수 완료로 정의
- Android와 iOS는 Gateway가 보낸 `title`·`body`를 그대로 저장·표시하며 8종 문구를 앱 코드에 중복 정의하지 않음

## Topic 등록

1. Gateway가 USER/DEPARTMENT ID에 대응하는 추측 불가능한 Topic을 생성합니다.
2. 사용자 Topic, 부서 Topic, `notice_all`과 만료시각을 QR에 담습니다.
3. Gateway 개인키로 canonical 문자열을 서명합니다.
4. 앱이 내장 공개키, Firebase Project ID, 시간, Topic 형식, 서명을 검증합니다.
5. 앱이 Firebase에 세 Topic을 직접 구독합니다.
6. 재등록 시 새 Topic 구독이 모두 성공한 후 이전 사용자·부서 Topic을 해제합니다.

## DB 테이블

| 테이블 | 역할 | 주요 무결성 |
|---|---|---|
| `push_queue` | 발송 대기·재시도·DEAD | `event_id` unique, 상태/type check, claim index |
| `push_send_history` | FCM 접수 완료 기록 | `event_id` unique, 대상/시간 index |
| `push_topic_binding` | 업무 ID와 불투명 Topic 매핑 | `(target_type,target_id)`와 `topic_name` unique |
| `push_device` | Topic 이전 호환 테이블 | deprecated, 신규 앱 미사용 |

스키마 변경은 Flyway V1·V2로 관리합니다. V2는 기존 사용자 Queue를 USER 대상으로 backfill합니다.

## 확장 기준

- 여러 Gateway 인스턴스: `SKIP LOCKED`로 동일 행 동시 발송 방지
- 처리량 증가: `PUSH_BATCH_SIZE`, DB connection pool, Scheduler 주기를 계측 후 조정
- RabbitMQ 도입: DB polling이 병목이 되거나 독립 소비자·복잡한 fan-out이 필요할 때 검토
- 업무 디렉터리: `BusinessDirectory` Bean을 기존 사용자·부서 테이블의 읽기 전용 조회로 교체
