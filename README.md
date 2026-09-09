# DEPL Push Gateway

폐쇄 업무망의 Spring Boot 시스템에서 Firebase Cloud Messaging(FCM)을 통해 iOS·Android로 **정해진 종류의 알림만** 전달하는 포트폴리오 프로젝트입니다. 업무 상세 내용과 FCM 등록 토큰은 Gateway로 전달하지 않으며, 앱은 서명된 QR을 촬영해 사용자·부서·공지 Topic을 직접 구독합니다.

## 프로젝트 구성

| 디렉터리 | 기술 | 역할 |
|---|---|---|
| [`push-gateway`](push-gateway/) | Spring Boot 3.5, Java 21, MariaDB, Firebase Admin SDK | DB Outbox 조회, Topic Push 발송, QR 및 테스트 화면 |
| [`android-app`](android-app/) | Kotlin, Jetpack Compose, FCM, CameraX, ML Kit, Room | QR 등록, 알림 수신, 최대 3,000건 내역 표시 |
| [`ios-app`](ios-app/) | SwiftUI, Firebase Messaging | QR 등록, 알림 수신 및 로컬 내역 표시 |
| [`docs`](docs/) | Markdown | 아키텍처, API, 보안·공개 체크리스트 |

## 통신 구조

```text
업무 트랜잭션 ── INSERT ──▶ push_queue (MariaDB)
                                  │ 30초 polling / SKIP LOCKED
                                  ▼
                            Push Gateway ── HTTPS 443 ──▶ FCM
                                                             ├─▶ APNs ──▶ iOS
                                                             └──────────▶ Android

업무 화면 ── 서명 QR ──▶ 앱에서 서명·만료 검증 ──▶ 사용자·부서·공지 Topic 구독
```

- 앱과 폐쇄망 Gateway 사이의 직접 네트워크 연결은 필요하지 않습니다.
- Gateway에서 Google 인증·FCM 방향의 outbound HTTPS 443만 허용합니다.
- 성공 시 `push_send_history` INSERT와 `push_queue` DELETE를 한 DB 트랜잭션으로 처리합니다.
- 전달은 at-least-once이며 앱이 `eventId`로 중복 표시를 방지합니다.
- RabbitMQ나 Redis 없이 기존 MariaDB를 Outbox로 사용합니다.

## 지원 알림

| 앱 Payload 값 | 업무 시스템 값 | 표시 문구 | 행위자 필요 |
|---|---|---|---|
| `COMMENT_ADDED` | `TASK_COMMENT_CREATED` | 본인 업무에 댓글이 작성되었습니다. | 아니요 |
| `TASK_MENTIONED` | `TASK_MENTIONED` | `{이름}` 님이 업무에 당신을 언급하였습니다. | 예 |
| `COMMENT_MENTIONED` | `COMMENT_MENTIONED` | `{이름}` 님이 댓글에 당신을 언급하였습니다. | 예 |
| `SOURCE_CONFLICT` | `SOURCE_OVERLAP` | 소스 겹침 알림 | 아니요 |
| `NOTICE_REGISTERED` | `NOTICE_CREATED` | 공지 사항이 등록되었습니다. | 아니요 |
| `TASK_ARRIVED` | `TASK_ARRIVED` | 업무가 도착했습니다. | 아니요 |
| `APPROVAL_TASK_ARRIVED` | `APPROVAL_TASK_ARRIVED` | 결재할 업무가 도착했습니다. | 아니요 |
| `MENTIONED_TASK_DEPLOYED` | `MENTIONED_TASK_DEPLOYED` | 당신이 언급된 업무가 운영에 반영되었습니다. | 아니요 |

Push Gateway가 위 표의 문구를 생성하며 앱은 문구를 자체 조립하지 않습니다. FCM data에는 `eventId`, `notificationType`, `title`, `body`, `templateVersion`이 포함됩니다. 언급 알림의 행위자 이름은 Gateway가 `body`에 반영하고 별도 필드로 전송하지 않습니다. 업무명, 문서번호, 댓글 내용 등은 전송하지 않습니다.

## 시작하기

1. Firebase 프로젝트에 Android/iOS 앱을 등록합니다.
2. 플랫폼별 Firebase 구성 파일을 **저장소 밖에서** 준비합니다.
3. QR 서명용 P-256 키를 생성하고 개인키는 Gateway Secret으로, 공개키만 앱에 넣습니다.
4. MariaDB에 Flyway V1·V2 마이그레이션을 적용합니다.
5. Gateway를 실행하고 앱에서 발급 QR을 촬영합니다.
6. 관리자 테스트 화면에서 사용자·부서·공지 알림을 발송합니다.

구체적인 실행 방법은 다음 문서를 참고하세요.

현재 Gateway Java 패키지와 Maven groupId는 `com.sangwoo.push`입니다. `output/pdf/`의 기존 PDF는 작성 당시 스냅샷으로 이전 패키지 표기가 남아 있을 수 있습니다. 최신 코드 위치·정식 QR 발급 명세는 아래 Markdown 문서를 기준으로 확인하세요.

- [Gateway 설치·운영](push-gateway/README.md)
- [Gateway 개발·기능 변경 검토 가이드](docs/PUSH-GATEWAY-DEVELOPMENT.md)
- [Gateway 개발 가이드 PDF](output/pdf/DEPL-PushGateway-Development-Guide.pdf)
- [폐쇄망 방화벽·회사 Firebase 전환](docs/COMPANY-DEPLOYMENT.md)
- [Gateway 운영 가이드 PDF](output/pdf/DEPL-PushGateway-Guide.pdf)
- [Android 앱 설정·실기기 테스트](android-app/README.md)
- [iOS 앱 설정·실기기 테스트](ios-app/README.md)
- [API 및 Payload 명세](docs/API.md)
- [정식 QR 발급 API·회사 인증 연동](docs/QR-ENROLLMENT-API.md)
- [업무 시스템 QR 자체 발급·Java 예제](docs/BUSINESS-SYSTEM-QR-ISSUANCE.md)
- [아키텍처와 장애 처리](docs/ARCHITECTURE.md)
- [보안 설정과 공개 저장소 체크리스트](docs/SECURITY.md)

## 검증 명령

```bash
cd push-gateway && mvn test
cd ../android-app && ./gradlew testDebugUnitTest lintDebug assembleDebug
```

iOS는 macOS와 Xcode가 필요합니다. 자세한 명령은 [iOS README](ios-app/README.md)를 확인하세요.

## 현재 검증 범위

- Spring Boot Gateway: Queue 처리, 재시도/DEAD, QR 발급, 관리자 페이지 자동 테스트
- Android: QR Topic 등록, 잠금 화면 알림 채널, Room/Paging 최대 3,000건 저장
- iOS: 유료 Apple Developer 서명, APNs development entitlement, Firebase FID 등록, QR Topic 등록
- Firebase: HTTP v1 발송 및 iOS 개발 APNs 인증 키 연결

Debug iPhone은 Firebase의 **개발 APNs 인증 키**를 사용합니다. TestFlight, Ad Hoc 또는 운영 배포 전에 동일 Apple APNs 키를 Firebase의 **프로덕션 APNs 인증 키** 영역에도 등록해야 합니다.

## 저장소에 포함되지 않는 파일

다음 파일은 `.gitignore`로 차단되어 있으며 각 개발·운영 환경에서 별도로 주입해야 합니다.

- Firebase 서비스 계정 JSON
- `google-services.json`, `GoogleService-Info.plist`
- APNs `.p8`, 인증서, provisioning profile
- QR 서명 개인키와 keystore
- `.env` 및 로컬 DB 비밀번호
- APK/IPA/ZIP/JAR 등 빌드 산출물

이 저장소의 예제 ID와 공개키는 인증 비밀이 아닙니다. 실제 배포에서는 조직별 Firebase 프로젝트, Bundle ID/Application ID, 공개키로 교체하세요.
