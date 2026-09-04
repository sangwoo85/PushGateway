# 보안 설정과 공개 저장소 체크리스트

## 저장소에 넣지 않는 정보

| 정보 | 저장 위치 | Git 포함 여부 |
|---|---|---|
| Firebase 서비스 계정 JSON | Secret Manager 또는 읽기 전용 mount | 금지 |
| QR ECDSA 개인키 | Secret Manager/HSM 또는 읽기 전용 mount | 금지 |
| APNs `.p8`, 인증서 | Apple/Firebase Console 및 Secret Manager | 금지 |
| DB 계정·비밀번호 | 배포 환경변수/Secret | 금지 |
| 테스트 관리자 비밀번호·BCrypt hash | 배포 Secret | 금지 |
| `google-services.json` | Android 개발·빌드 환경 | 금지 |
| `GoogleService-Info.plist` | iOS 개발·빌드 환경 | 금지 |
| QR 검증 공개키 | 앱 리소스 | 허용 |
| Firebase Project ID, Bundle ID | 앱 설정 | 공개 식별자 |

Firebase 클라이언트 구성 파일은 일반적으로 서버 개인키는 아니지만 프로젝트 식별자와 API key를 포함하므로 이 포트폴리오 저장소에서는 제외합니다.

## 네트워크

- Gateway → Google OAuth/FCM/DNS: outbound TCP 443
- 모바일 앱 → Firebase/FCM/APNs: 일반 인터넷
- 앱 → Gateway: 연결 없음
- 관리자 테스트 화면: 업무망 관리자 IP에서만 접근 허용
- MariaDB: Gateway와 업무 시스템에 필요한 내부 구간만 허용

## 관리자 화면

- 기본값은 `PUSH_TEST_PAGE_ENABLED=false`
- 기본값은 로그인 사용이며 BCrypt hash와 별도 관리자 계정 필수
- localhost 개발 환경에서만 `PUSH_TEST_PAGE_AUTHENTICATION_ENABLED=false`와 `SERVER_ADDRESS=127.0.0.1` 사용 가능
- `ROLE_PUSH_ADMIN` 또는 `ROLE_PUSH_TESTER`만 접근
- POST는 CSRF 보호
- 관리자별 분당 발송 제한
- 부서·전체 공지는 별도 확인 체크
- 감사 로그에는 대상 ID를 마스킹하고 Topic/토큰/QR 원문을 기록하지 않음

운영에서는 in-memory 계정 대신 기존 Spring Security/SSO와 연결하고, reverse proxy 또는 방화벽에서도 관리자 경로를 제한하세요.

## 애플리케이션 데이터 최소화

- Push에는 승인된 고정 알림 문구와 종류만 포함하고 업무 상세는 제외
- FCM 토큰을 업무 DB에 저장하지 않는 Topic 방식
- USER/DEPARTMENT Topic에 사번·이름·부서 코드를 직접 사용하지 않음
- Android 자동 백업 비활성화
- 알 수 없는 Payload 및 위조·만료 QR 거부
- Android 알림 이력은 최대 3,000건으로 제한

## 공개 전 점검

```bash
git status --ignored
git grep -n -I -E 'BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|private_key_id|client_email|AIza[0-9A-Za-z_-]{20,}'
git ls-files | grep -E '(^|/)(google-services.json|GoogleService-Info.plist|.*\.(p8|p12|pem|jks|keystore|mobileprovision))$'
```

마지막 명령은 결과가 없어야 합니다. 과거 커밋에 비밀이 들어간 적이 있다면 파일 삭제만으로 충분하지 않으므로 키를 즉시 폐기·재발급하고 Git history에서도 제거해야 합니다.

## 운영 배포 체크리스트

- [ ] Firebase 서비스 계정 최소권한 및 주기적 교체
- [ ] QR 개인키 파일 권한과 Secret mount 확인
- [ ] 앱의 공개키와 Gateway 개인키 쌍 일치
- [ ] 테스트 페이지 비활성 또는 관리자망 한정
- [ ] DB TLS/계정 권한/백업 정책 확인
- [ ] Queue `DEAD`, retry, latency, FCM 오류율 알림 설정
- [ ] 로그에 Topic, 토큰, 업무 내용이 없는지 점검
- [ ] 퇴사·부서이동 시 새 QR 발급 및 Topic 회전 절차 운영
- [ ] APNs `.p8`를 Firebase 외 저장소에 업로드하지 않고, Apple Key ID/Team ID와 함께 접근 제한된 Secret 보관소에 백업
- [ ] Debug는 개발 APNs 키, TestFlight/Ad Hoc/운영은 프로덕션 APNs 키가 등록됐는지 확인
