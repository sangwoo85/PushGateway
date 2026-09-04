# DEPL iOS 알림 앱

`PushReceiver.xcodeproj`는 iOS 17+, SwiftUI, Firebase Messaging 12.18.0을 사용합니다. 외부망의 앱이 폐쇄망 Push Gateway에 접속하지 않고, 업무 시스템 화면의 QR을 촬영해 개인·부서·전체 공지 FCM Topic을 직접 구독합니다.

## 통신 구조

```text
폐쇄망 업무 시스템 ── QR ──▶ iPhone 앱 ── HTTPS ──▶ Firebase Topic 구독
폐쇄망 Push Gateway ── HTTPS 443 ──▶ FCM ──▶ APNs ──▶ iPhone 앱
```

앱은 FCM 등록 토큰을 업무 시스템에 전송하지 않습니다. Push Gateway 주소를 호출하는 코드도 없습니다. 알림 내역은 `eventId` 기준으로 중복을 제거해 기기에 최대 50건 저장합니다.

## 필요한 운영 설정

1. Firebase Console에 Apple 앱의 실제 Bundle ID를 등록합니다.
2. 내려받은 `GoogleService-Info.plist`를 `PushReceiver` target에 추가합니다. 가짜 설정 파일을 만들거나 Git에 commit하지 않습니다.
3. Apple Developer에서 App ID의 Push Notifications capability를 켭니다.
4. APNs 인증 키(`.p8`), Key ID, Team ID를 Firebase Console의 Cloud Messaging 설정에 업로드합니다. 개인키는 앱과 저장소에 넣지 않습니다.
5. Xcode Signing & Capabilities에서 정식 Team과 Bundle ID를 설정합니다.
6. `Info.plist`의 `ENROLLMENT_PUBLIC_KEY_X963_BASE64`에 QR 서명 검증용 ECDSA P-256 공개키의 X9.63 representation을 Base64로 넣습니다.
7. `FIREBASE_PROJECT_ID`는 일반적으로 `GoogleService-Info.plist`에서 읽습니다. 별도 빌드 설정을 사용할 때만 동일 값을 지정합니다.

무료 Personal Team은 Push Notifications capability를 지원하지 않습니다. 실제 APNs/FCM 검증에는 유료 Apple Developer Program이 필요합니다.

`GoogleService-Info.plist`, APNs 키와 provisioning profile은 Git에서 제외됩니다. 저장소를 clone한 개발자는 자신의 Firebase/Apple Developer 설정 파일을 별도로 준비해야 합니다.

## QR 형식

```json
{
  "version": 1,
  "firebaseProjectId": "depl-production",
  "topics": {
    "user": "usr_32자이상의난수",
    "department": "dept_32자이상의난수",
    "notice": "notice_all"
  },
  "issuedAt": "2026-09-03T17:00:00+09:00",
  "expiresAt": "2026-09-03T17:03:00+09:00",
  "nonce": "16자이상의일회용난수",
  "signature": "DER_ECDSA_SIGNATURE_BASE64"
}
```

앱이 검증하는 canonical 문자열은 다음과 같습니다. Gateway도 공백이나 JSON 필드 순서가 아니라 이 문자열을 UTF-8로 변환해 `SHA256withECDSA`로 서명해야 합니다.

```text
version|firebaseProjectId|userTopic|departmentTopic|noticeTopic|issuedAt|expiresAt|nonce
```

- 알고리즘: ECDSA P-256 + SHA-256
- Java 서명 알고리즘: `SHA256withECDSA`
- 서명 표현: DER bytes를 Base64 인코딩
- 앱 공개키 표현: uncompressed X9.63 bytes를 Base64 인코딩
- QR 최대 유효시간: 10분, 권장 1~3분
- 개인·부서 Topic: 사번·이름·부서 코드를 사용하지 않고 32자 이상의 난수 사용

새 QR 등록 시 새 Topic 3개를 먼저 구독한 다음 기존 개인·부서 Topic을 해제합니다. 네트워크 실패 시 pending 상태를 Keychain에 남기고 다음 화면 진입에서 재시도합니다. 기기 등록 초기화는 저장된 Topic 구독 해제가 모두 성공한 뒤 Keychain을 지웁니다.

## 알림 Payload

```json
{
  "eventId": "UUID",
  "notificationType": "TASK_ARRIVED"
}
```

지원하는 `notificationType`:

- `COMMENT_ADDED`
- `TASK_MENTIONED`
- `COMMENT_MENTIONED`
- `SOURCE_CONFLICT`
- `NOTICE_REGISTERED`
- `TASK_ARRIVED`
- `APPROVAL_TASK_ARRIVED`
- `MENTIONED_TASK_DEPLOYED`

알 수 없는 유형과 UUID가 아닌 `eventId`는 저장하지 않습니다. 업무 상세, 문서번호 및 민감정보는 Payload에 포함하지 않습니다.

## 빌드와 테스트

### Xcode에서 실행

1. `PushReceiver.xcodeproj`를 엽니다.
2. Firebase 패키지 해석이 끝날 때까지 기다립니다.
3. Signing & Capabilities에서 자신의 Team과 Bundle ID를 선택합니다.
4. `Push Notifications`와 `Background Modes > Remote notifications`를 확인합니다.
5. Firebase 구성 파일을 `PushReceiver` target에 추가합니다.
6. 실제 iPhone을 선택하고 Run합니다.

명령행 빌드:

```bash
xcodebuild -project PushReceiver.xcodeproj -scheme PushReceiver \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath .build/DerivedData \
  -clonedSourcePackagesDirPath .build/SourcePackages \
  CODE_SIGNING_ALLOWED=NO build

xcodebuild -project PushReceiver.xcodeproj -scheme PushReceiver \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -derivedDataPath .build/TestDerivedData \
  -clonedSourcePackagesDirPath .build/SourcePackages \
  CODE_SIGNING_ALLOWED=NO test
```

Debug 빌드에는 카메라가 없는 시뮬레이터에서 사용할 QR 문자열 입력란이 표시됩니다. Release 빌드에는 포함되지 않습니다.

## 앱 사용 순서

1. 앱을 실행해 알림 권한을 허용합니다.
2. Gateway QR 등록 화면에서 사용자 ID와 부서 ID를 입력합니다.
3. 앱의 QR 등록 화면에서 생성된 QR을 촬영합니다.
4. 사용자·부서·공지 Topic 구독 완료를 확인합니다.
5. Gateway 메시지 테스트 화면에서 Push를 발송합니다.
6. iOS 알림 센터와 앱 내 수신 내역을 확인합니다.

APNs 등록은 Simulator가 아닌 실제 기기와 유효한 Apple Developer 서명이 필요합니다.

## 문제 해결

- provisioning profile 생성 실패: 유료 멤버십 상태와 App ID의 Push Notifications capability를 확인합니다.
- APNs token 미생성: 실제 기기, 네트워크, 앱 권한, entitlements를 확인합니다.
- FCM 수신 실패: Firebase Console에 올린 APNs Key ID/Team ID/`.p8` 조합과 Bundle ID를 확인합니다.
- QR 거부: 앱 공개키·Firebase Project ID·기기 시간·QR TTL을 확인합니다.

## 물리 iPhone 체크리스트

- [ ] `GoogleService-Info.plist`, APNs 인증키, 정식 서명 확인
- [ ] 첫 실행 알림·카메라 권한 확인
- [ ] 정상 QR로 개인·부서·공지 Topic 구독 성공
- [ ] 만료·위조·다른 Firebase 프로젝트 QR 거부
- [ ] 새 QR 등록 후 기존 개인·부서 Topic 해제
- [ ] 앱 재시작 시 미완료 Topic 구독 재시도
- [ ] 개인·부서·공지 Topic별 테스트 Push 수신
- [ ] 같은 `eventId` 중복 알림이 내역에 한 번만 표시
- [ ] 기기 등록 초기화 후 더 이상 Topic 알림을 받지 않음
- [ ] 로그에 Topic, QR 원문, 등록 토큰 및 업무 내용이 없음

백그라운드 원격 알림 실행 여부는 iOS가 결정합니다. 사용자가 알림을 선택하거나 앱이 원격 알림 콜백을 받은 경우 내역이 로컬에 저장되며, iOS 시스템 알림 센터의 기존 항목을 앱이 임의로 조회할 수는 없습니다.
