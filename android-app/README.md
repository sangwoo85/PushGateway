# DEPL Android 알림 앱

Kotlin, Jetpack Compose, Firebase Messaging, CameraX, ML Kit, Room/Paging으로 구현한 Android 알림 전용 앱입니다. Gateway와 직접 통신하지 않으며 서명된 QR을 검증한 뒤 개인·부서·공지 FCM Topic을 구독합니다.

## 준비

필요 도구는 Android Studio, Android SDK 35, JDK 17 이상입니다.

1. 자신의 Firebase 프로젝트에 Android 앱을 등록합니다. 현재 예제 Application ID는 `com.sangwoo.push`입니다.
2. 받은 `google-services.json`을 `app/google-services.json`에 둡니다. 이 파일은 Git에서 제외됩니다.
3. `app/src/main/res/values/strings.xml`의 QR 검증 공개키와 Firebase Project ID가 Gateway 설정과 일치하는지 확인합니다.
4. Android 13 이상에서는 알림 권한을 허용합니다.
5. 실기기 설치 시 개발자 옵션과 USB 디버깅을 켭니다.

서비스 계정 JSON과 Gateway QR 개인키는 앱에 포함하면 안 됩니다. QR 검증용 P-256 공개키만 리소스에 포함되어 있습니다.

## 빌드와 검사

```bash
./gradlew clean testDebugUnitTest lintDebug assembleDebug
```

APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다.

Firebase 설정 파일이 없으면 코드 검사와 일부 테스트는 가능하지만, 실제 FCM 수신이 가능한 APK로 판단하면 안 됩니다. 빌드 로그의 `processDebugGoogleServices` 성공 여부를 확인하세요.

## 동작

- Android는 FCM data-only 메시지를 `FirebaseMessagingService`에서 수신합니다.
- 허용된 8종 `notificationType`만 Room에 기록하고 시스템 알림을 표시합니다.
- `eventId` unique index로 중복을 방지합니다.
- 목록은 Paging으로 30개씩 불러오며 최신 3,000건만 보관합니다.
- 개인·부서·공지 Topic이 모두 구독돼야 기기 등록 완료로 처리합니다.
- QR 재등록 시 새 Topic을 먼저 구독하고 이전 개인·부서 Topic을 정리합니다.

## 실기기 확인

```bash
$HOME/Library/Android/sdk/platform-tools/adb devices -l
$HOME/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

기기 등록 후 Gateway 테스트 페이지에서 8종 알림을 전송하고 시스템 알림과 앱 내역을 확인합니다. Topic, FCM 토큰, QR 원문은 로그에 출력하지 않습니다.

## 앱 사용 순서

1. 앱을 처음 열고 알림 권한을 허용합니다.
2. Gateway의 `/internal/push-test/enrollment`에서 사용자 ID와 부서 ID로 QR을 생성합니다.
3. 앱의 **QR 등록** 버튼을 눌러 QR을 촬영합니다.
4. 사용자·부서·공지 Topic 구독 완료 상태를 확인합니다.
5. Gateway의 `/internal/push-test/messages`에서 동일 사용자 또는 부서를 선택해 Push를 보냅니다.
6. 시스템 알림과 앱 내 알림 내역을 확인합니다.

## 저장과 성능

- Room unique index가 같은 `eventId`의 중복 저장을 차단합니다.
- Paging이 화면에 필요한 내역을 30개 단위로 읽어 1,000~3,000건에서도 전체 목록을 한꺼번에 메모리에 올리지 않습니다.
- 저장 완료 시 최신 3,000건을 제외한 이전 행을 정리합니다.
- 알림 수신 DB 기록은 짧은 IO 작업으로 서비스 callback 안에서 완료해 프로세스 종료로 인한 유실 가능성을 줄입니다.

## 문제 해결

- `google-services.json is missing`: Firebase Console에서 같은 Application ID의 파일을 내려받아 `app/`에 배치합니다.
- QR이 거부됨: Firebase Project ID, 공개키, QR 만료시각, 기기 시간이 일치하는지 확인합니다.
- 구독은 됐지만 알림이 없음: 알림 권한, Google Play 서비스, 네트워크, Gateway 결과의 `FCM_ACCEPTED` 여부를 확인합니다.
- USB 설치가 `unauthorized`: 휴대폰의 USB 디버깅 허용 창에서 해당 컴퓨터를 승인합니다.
