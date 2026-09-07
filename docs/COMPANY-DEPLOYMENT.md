# 폐쇄망 설치와 회사 Firebase 전환

확인일: 2026-09-07. 대상은 서비스 계정 JSON을 ADC로 읽고 Firebase Admin Java SDK의 `messaging.send()`로 HTTP v1 Topic 메시지를 보내는 현재 Gateway입니다. 스마트폰은 인터넷망에서 직접 구독하고 수신합니다.

## Gateway 외부 통신 허용 목록

| 구분 | 목적지 FQDN | URL / 용도 | 방향·포트 |
|---|---|---|---|
| 현재 구성 필수 | `fcm.googleapis.com` | `https://fcm.googleapis.com/v1/projects/{PROJECT_ID}/messages:send` — Push 발송 | Gateway → 외부 TCP 443 |
| 현재 구성 필수 | `oauth2.googleapis.com` | `https://oauth2.googleapis.com/token` — 서비스 계정 OAuth 토큰 발급·갱신 | Gateway → 외부 TCP 443 |
| 인증 방식에 따라 추가 | `accounts.google.com` | Google 공식 FCM 네트워크 문서에 기재된 인증 호스트. 현재 서비스 계정의 토큰 요청은 위 OAuth2 주소 사용 | Gateway → 외부 TCP 443 |
| 서버 구독 관리 도입 시 추가 | `iid.googleapis.com` | `https://iid.googleapis.com/iid/v1:batchAdd`, `https://iid.googleapis.com/iid/v1:batchRemove` — 서버에서 Topic 구독·해지 | Gateway → 외부 TCP 443 |

`setTopic()`은 메시지의 수신 대상을 지정하는 동작입니다. 현재 서버에는 `subscribeToTopic()` 호출이 없으므로 Topic 발송만을 위해 `iid.googleapis.com`을 열 필요는 없습니다.

위 두 필수 호스트는 현재 소스와 서비스 계정 인증 방식에 한정한 목록입니다. ADC를 Workload Identity Federation이나 서비스 계정 impersonation으로 바꾸면 `sts.googleapis.com`, `iamcredentials.googleapis.com` 및 외부 IdP 등 추가 목적지를 다시 산정해야 합니다. 현재 구성에는 필요하지 않습니다.

방화벽은 URL 경로보다 FQDN/SNI 기준 허용을 사용하고 응답 트래픽을 허용합니다. Google은 FCM 송신 엔드포인트의 고정 IP 허용 목록을 제공하지 않습니다. SDK·인증 방식 변경 시 방화벽 로그로 실제 목적지를 다시 확인하세요.

DNS는 사내 DNS로 UDP/TCP 53, 시간 동기화는 사내 NTP로 UDP 123을 사용하도록 인프라에 요청합니다. 이는 Google HTTPS와 별도인 내부 인프라 연결입니다. MariaDB도 사내 DB 주소·설정 포트(기본 TCP 3306)로 연결합니다. 프록시/TLS 검사 환경에서는 Java 신뢰 저장소와 프록시 설정을 검증해야 하며 인증서 검증을 끄지 않습니다.

## Gateway에서 열 필요 없는 연결

- FCM 수신용 `mtalk.google.com` 계열과 TCP 5228~5230은 Android 단말 쪽 요구사항입니다.
- APNs는 Firebase와 Apple 및 iPhone 사이에서 연결됩니다. Gateway가 Apple APNs 서버에 직접 접속하지 않습니다.
- `firebaseinstallations.googleapis.com` 등 앱 등록 통신은 외부망 앱에서 발생합니다.
- Firebase/Google Cloud/Apple 관리 콘솔은 관리자 PC에서 접속합니다. Gateway 런타임용 허용 목록과 별개입니다.
- Maven Central, GitHub, Docker Registry는 외부 빌드 환경에서 사용하고, 빌드된 JAR와 JDK 21을 반입하면 실행 서버에서 접근할 필요가 없습니다.
- 인터넷에서 Gateway로 새로 들어오는 inbound 연결은 필요하지 않습니다. 업무망 테스트 페이지는 내부 관리자 접근만 허용합니다.

## 배포와 확인 순서

1. 외부 빌드 환경에서 JDK 21로 `mvn clean verify`를 실행하고 실행 JAR를 반입합니다. 서비스 계정 JSON, QR 개인키, DB 비밀번호는 JAR에 넣지 않고 별도 경로에 배치합니다.
2. `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `FIREBASE_ENABLED=true`, `FIREBASE_PROJECT_ID`, `GOOGLE_APPLICATION_CREDENTIALS`, `PUSH_QR_PRIVATE_KEY_PATH`를 설정합니다. QR 공개키 파일은 개인키 경로 뒤에 `.pub`를 붙인 위치에도 배치합니다.
3. 위 FQDN의 DNS 및 TLS 연결을 실제 Gateway 서버에서 확인합니다. HTTP 400/401/404 같은 응답은 연결 여부만 보여주며 발송 성공 검증은 아닙니다.
4. Gateway를 시작하고 새 QR로 앱을 등록한 뒤 개인·부서·공지 대상과 고정 8개 타입을 테스트합니다. `FCM_ACCEPTED`와 실제 단말 표시를 각각 확인합니다.
5. 토큰 갱신 시점에도 OAuth 연결이 허용되는지 확인합니다. 최초 발송이 성공했어도 인증 서버 차단 시 이후 갱신에서 실패할 수 있습니다.
6. 회사 공유 환경에서는 테스트 페이지 인증을 켭니다. 무인증 옵션은 loopback 전용이며 역방향 프록시로 외부에 노출하지 않습니다.

## 회사 소유로 바꾸는 두 가지 방법

### A. 기존 Firebase 프로젝트를 회사에 인계

같은 프로젝트를 유지하므로 Project ID와 앱 등록 설정은 유지됩니다. 관리권한 인계만으로 앱의 FCM 등록과 Topic 구독을 새 프로젝트로 옮기는 작업은 발생하지 않습니다.

1. Firebase 프로젝트 설정의 사용자 및 권한 또는 Google Cloud IAM에 회사 관리 계정을 추가하고 필요한 소유·관리 권한을 부여합니다. 회사 계정으로 실제 접근을 확인한 후 개인 계정 권한을 정리합니다.
2. 회사 Google Cloud 조직이 있다면 프로젝트의 조직 이전을 별도로 진행합니다. IAM 소유자 추가와 조직 이전은 같은 작업이 아닙니다. 대상 조직의 역할과 조직 정책을 먼저 확인합니다.
3. 결제 계정이 연결되어 있으면 회사 결제 계정으로 변경하고, Analytics를 사용한다면 해당 계정의 관리권한도 확인합니다.
4. 회사 관리 서비스 계정과 키로 Gateway 인증을 교체합니다. FCM 발송 권한은 `cloudmessaging.messages.create`를 포함해야 하며 공식 제공 역할인 Firebase Cloud Messaging API Admin을 검토할 수 있습니다. 발송 검증 후 이전 키를 폐기합니다.
5. APNs 키와 Apple Developer 관리권한도 인계 범위를 확인합니다. Firebase 소유권 변경은 Apple Developer Team이나 Bundle ID를 자동으로 이전하지 않습니다.
6. Project ID, QR 공개키, 앱 식별자를 유지한다면 소유권 변경 자체로 앱 재배포나 QR 재등록을 할 필요는 없습니다. QR 키나 Apple 서명 구성을 교체하는 경우에는 관련 앱 변경을 별도로 처리합니다.

### B. 회사 전용 Firebase 프로젝트를 새로 생성

개인 포트폴리오와 회사 운영을 별도로 유지하려는 현재 목적에는 이 방법을 권장합니다.

1. 회사 계정·조직 아래 프로젝트를 생성하고 Android/iOS 앱을 등록합니다. 등록할 값은 실제 Android `applicationId`와 iOS Bundle ID입니다. Gateway Java 패키지명은 Firebase 앱 식별자가 아닙니다.
2. HTTP v1 API를 활성화하고 회사 전용 서비스 계정과 FCM 발송 권한을 구성합니다. 회사 정책이 JSON 키 발급을 금지하면 승인된 인증 방식과 필요한 방화벽 목적지를 다시 설계합니다.
3. Gateway의 `FIREBASE_PROJECT_ID`와 `GOOGLE_APPLICATION_CREDENTIALS`를 회사 프로젝트 값으로 변경합니다.
4. Android의 `app/google-services.json`을 회사 앱 설정 파일로 교체합니다. iOS의 `PushReceiver/GoogleService-Info.plist`와 `Info.plist`의 `FIREBASE_PROJECT_ID`를 함께 변경합니다.
5. 회사용 QR P-256 키를 새로 발급하고 Gateway 개인키 및 Android/iOS QR 검증 공개키를 함께 교체합니다. 키 파일과 실제 회사 설정은 Git에 올리지 않습니다.
6. iOS 앱의 서명 Team·Bundle ID에 대응하는 APNs 인증 키를 새 Firebase 프로젝트에 연결합니다. 개발/배포 환경별 APNs 설정을 확인합니다. Firebase 프로젝트 교체만으로 Apple Team 변경이 필수인 것은 아니지만, 회사가 Apple 자산도 소유하려면 별도로 처리해야 합니다.
7. 앱을 재빌드·배포하고 새 QR로 사용자·부서·공지 Topic을 재구독합니다. 토큰/FID와 Topic 구독은 새 Firebase 프로젝트로 자동 복사되지 않습니다. Topic 문자열이 같아도 프로젝트가 다르면 수신 대상은 별개입니다.
8. DB Topic binding을 유지할지 회사용 DB에 새로 생성할지 결정합니다. 회사 운영에는 별도 DB/스키마를 권장합니다. 같은 DB로 전환한다면 스케줄러를 중지하고 대기·재시도 큐의 처리 정책을 정한 후 전환하세요. 현재 큐에는 Firebase 프로젝트 구분 컬럼이 없어 단순 설정 교체 시 기존 큐도 새 프로젝트로 발송될 수 있습니다.
9. 개인·부서·공지와 8개 타입을 양쪽 기기에서 검증한 뒤 이전 환경 사용을 종료합니다. Android 앱 업데이트는 기존 앱과 같은 applicationId 및 호환 서명이 필요하고, 식별자를 바꾸면 별도 앱으로 설치됩니다.

## 참고 자료

- [Firebase FCM 네트워크 구성](https://firebase.google.com/docs/cloud-messaging/network-configuration)
- [Google 서비스 계정 OAuth 2.0](https://developers.google.com/identity/protocols/oauth2/service-account)
- [FCM HTTP v1 서버 인증과 발송](https://firebase.google.com/docs/cloud-messaging/send/v1-api)
- [Firebase 프로젝트 구성원 관리](https://support.google.com/firebase/answer/7000272?hl=en)
- [Google Cloud 조직 간 프로젝트 이전](https://cloud.google.com/resource-manager/docs/project-migration)
