# 정식 QR 발급 API

업무 시스템 자체에서 QR을 생성하는 경우에는 [업무 시스템 QR 자체 발급 가이드 및 Java 첨부 코드](BUSINESS-SYSTEM-QR-ISSUANCE.md)를 참고하세요. 아래 API 방식과 병행할 수 있으며 동일한 Topic 매핑·프로젝트·서명 신뢰 설정이 필요합니다.

## 요청

```http
GET /internal/push/enrollment/qr?userId=user001&departmentId=dev
```

테스트 기능 설정과 무관하게 등록됩니다. 요청 주체는 회사 인증으로 만들어진 Spring Security Authentication 또는 기존 로그인 세션을 사용해야 합니다. 권한은 ROLE_PUSH_ADMIN 또는 ROLE_PUSH_ENROLLMENT_ISSUER입니다. ROLE_PUSH_TESTER만 가진 계정과 익명 요청은 허용하지 않습니다. 앱이 호출하는 API가 아니라 업무망의 권한 있는 발급 화면에서 호출합니다.

| Query parameter | 조건 |
|---|---|
| userId | 필수, 원문 1~128자, 앞뒤 공백 제거, 공백만 있는 값·제어문자 금지 |
| departmentId | 필수, 원문 1~128자, 앞뒤 공백 제거, 공백만 있는 값·제어문자 금지 |

사용자·부서의 활성 상태 및 정확한 ID 일치를 확인하며, 사용자의 실제 departmentId와 요청 부서가 일치해야 발급합니다. 공지는 notice_all로 자동 포함됩니다.

## 성공 응답

```http
HTTP/1.1 200 OK
Content-Type: image/png
Cache-Control: no-store

<PNG 이미지 바이너리>
```

기존 QR 서명 형식과 동일합니다. version, firebaseProjectId, topics, issuedAt, expiresAt, nonce, signature를 QR에 담습니다. 기본 TTL은 3분, 최대 10분입니다. 앱 수정 없이 같은 프로젝트·공개키의 QR을 등록할 수 있습니다. 발급 시 Topic binding이 없으면 생성하므로 GET 재호출도 DB 변경이 발생할 수 있습니다. 프리페치·공유 캐시는 사용하지 않습니다.

## 오류 응답

| HTTP | code / 설명 |
|---|---|
| 400 | INVALID_REQUEST: 필수값 누락·빈 값·길이·제어문자 오류 |
| 401 | 미인증. 로그인 페이지로 redirect하지 않음 |
| 403 | 발급 권한 없음 |
| 404 | USER_NOT_FOUND 또는 DEPARTMENT_NOT_FOUND: 미존재 또는 비활성 |
| 409 | DEPARTMENT_MISMATCH: 실제 소속 불일치 |
| 503 | DIRECTORY_UNAVAILABLE: 조회 미연동·조회 실패·정확하지 않은 조회 결과 |
| 503 | QR_ISSUANCE_FAILED: 키·프로젝트·Topic 저장소 등 발급 실패 |

Controller에서 처리하는 오류는 application/problem+json 및 Cache-Control: no-store를 반환합니다. 401/403은 Spring Security 응답으로 동일 JSON 본문 계약을 제공하지 않습니다.

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "사용자의 실제 소속 부서와 일치하지 않습니다.",
  "code": "DEPARTMENT_MISMATCH"
}
```

서버 내부 예외, SQL, 키 파일 경로는 응답으로 반환하지 않습니다.

## 회사 업무 시스템 연동

BusinessDirectory Bean에 다음 정확 조회 메서드를 구현합니다. 검색 목록에서 첫 항목을 사용하는 방식으로 대체하지 마세요.

```java
Optional<BusinessDirectory.Entry> findUserById(String userId);
Optional<BusinessDirectory.Entry> findDepartmentById(String departmentId);
```

- 사용자 조회: 활성 사용자만 반환. Entry(id, displayName, departmentId)의 departmentId는 서버 DB의 실제 소속을 사용합니다.
- 부서 조회: 활성 부서만 반환. Entry의 id는 요청 ID와 정확히 일치해야 합니다.
- 미존재·비활성은 Optional.empty()로 반환합니다.
- 두 메서드의 기본 구현은 미연동 예외를 발생시킵니다. 기존 검색 전용 구현은 계속 컴파일되지만 정식 발급은 503으로 거부됩니다.
- 기본 BusinessDirectory는 회사 테이블을 알지 못하므로 정식 발급 성공까지는 실제 회사 조회 구현이 필요합니다.

정식 SecurityFilterChain은 우선순위 1로 /internal/push/enrollment/**를 처리합니다. 회사 인증 필터를 사용하는 경우 이 체인에서도 인증이 실행되도록 연동해야 합니다. 별도 서버의 JSESSIONID는 자동으로 공유되지 않습니다. 헤더에 사용자 ID를 넣는 것만으로 인증되지 않습니다.

테스트 기능을 끈 기본 독립 실행에는 회사 로그인 수단이 포함되어 있지 않습니다. 회사 인증 세션/필터와 위 조회 Bean을 연동해야 합니다. 테스트 기능이 켜진 개발 환경에서는 기존 PUSH_ADMIN 로그인 세션으로 권한 검증은 가능하지만 정확 조회 연동은 여전히 필요합니다.

동일 출처·로그인 세션이 있는 업무 화면에서 이미지로 표시할 수 있습니다. ID는 URL 인코딩하고 화면·프록시 접근 로그의 사용자 정보 보관 정책을 적용합니다.

```html
<img src="/internal/push/enrollment/qr?userId=user001&amp;departmentId=dev"
     alt="DEPL 기기 등록 QR">
```

## 기존 테스트 API와의 차이

기존 /internal/push-test/qr는 개발 검증용으로 유지합니다. 테스트 비활성 시 없어지고, 기존 입력 방식으로 발급합니다. 정식 API는 테스트 무인증 옵션으로 인증이 우회되지 않으며, 사용자·부서 검증이 완료된 경우에만 공통 EnrollmentQrService를 호출합니다.

정식 API도 QR 발급만 수행합니다. 앱은 Firebase에 직접 구독하며 Gateway에 등록 완료를 보내지 않습니다. nonce 일회 사용 검증과 개별 단말 강제 구독 해제 기능은 추가하지 않았습니다.
