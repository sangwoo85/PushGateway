# 업무 시스템에서 직접 QR 발급하기

업무 시스템이 QR을 만들고 화면에 표시하면 외부망 앱은 카메라로 읽어 Firebase Topic 3개를 구독합니다. 앱에서 업무 시스템/Gateway로 접속할 필요는 없습니다. QR 생성 자체에는 Firebase Admin SDK, 서비스 계정 JSON, Google 외부 통신이 필요하지 않습니다.

## 방식 선택

| 방식 | 업무 시스템에 필요한 것 | 주의점 |
|---|---|---|
| Gateway 정식 API 호출 후 PNG 표시 | 업무망 내 Gateway 연결, 회사 인증 연동 | 서명 키와 Topic 생성은 Gateway에 유지 |
| 업무 시스템 자체 QR 생성 | QR 서명 개인키, 프로젝트 ID, Gateway와 동일한 Topic 매핑 | 개인키 관리 책임이 업무 시스템에도 추가 |

기존 [정식 발급 API](QR-ENROLLMENT-API.md)는 그대로 유지됩니다. API를 호출할 수 있다면 중앙 관리 방식이 간단합니다. 아래 코드는 자체 생성이 필요한 경우의 독립 Java 예제입니다. 실제 회사 인증 및 사용자 테이블 연동은 포함하지 않습니다.

## 첨부 코드와 의존성

- [StandaloneEnrollmentQrIssuer.java](examples/StandaloneEnrollmentQrIssuer.java): Java 21 독립 생성기. Spring/Gateway 클래스에 의존하지 않습니다.
- [StandaloneEnrollmentQrIssuerCheck.java](examples/StandaloneEnrollmentQrIssuerCheck.java): 임시 키로 PNG 디코딩·서명·입력 검증을 수행하는 실행형 테스트.

업무 시스템 소스에 `com/sangwoo/push/example/` 경로로 복사하고 회사 패키지 규칙에 맞게 바꿀 수 있습니다. Spring Boot 3의 dependency management를 적용한 Maven 예시입니다. Jackson 버전은 사용 중인 Boot BOM을 따릅니다.

```xml
<dependency>
  <groupId>com.fasterxml.jackson.core</groupId>
  <artifactId>jackson-databind</artifactId>
</dependency>
<dependency>
  <groupId>com.google.zxing</groupId>
  <artifactId>core</artifactId>
  <version>3.5.3</version>
</dependency>
<dependency>
  <groupId>com.google.zxing</groupId>
  <artifactId>javase</artifactId>
  <version>3.5.3</version>
</dependency>
```

## 반드시 서버에서 처리할 순서

1. 기존 회사 로그인으로 발급자를 인증하고 본인 발급 또는 관리자 발급 권한을 확인합니다. 요청의 userId를 신뢰해서는 안 됩니다.
2. 실제 업무 DB에서 활성 사용자와 실제 소속 부서를 정확 조회합니다. 요청 부서와 실제 부서가 다르면 거부합니다.
3. Gateway의 `push_topic_binding`에서 사용자·부서·공지의 활성 매핑을 조회합니다. 별도 DB를 사용하면 동일 매핑을 신뢰 가능한 내부 연동으로 제공해야 합니다.
4. 세 매핑이 모두 존재할 때만 아래 생성기를 호출합니다. 없는 매핑은 오류 처리하고 중앙 매핑 관리 절차로 먼저 생성합니다.
5. PNG를 `Content-Type: image/png`, `Cache-Control: no-store`로 응답합니다. 브라우저 캐시, CDN, 프록시 캐시도 사용하지 않습니다.

현재 Gateway 저장소의 조회 SQL은 다음과 같습니다. 회사 DB에는 prepared statement/JdbcTemplate로 바인딩합니다. QR 발급용 조회 계정은 가능하면 읽기 전용으로 분리합니다.

```sql
SELECT topic_name FROM push_topic_binding
WHERE target_type = ? AND target_id = ? AND enabled = TRUE;
```

| target_type | target_id |
|---|---|
| USER | 정확 조회한 사용자 ID |
| DEPARTMENT | 사용자 DB에서 확인한 실제 부서 ID |
| NOTICE | ALL |

`usr_사용자ID`/`dept_부서ID`를 직접 만들지 마세요. 현재 프로토콜은 난수형 Topic이며, 자체 발급 코드가 새 난수를 만들기만 하면 Gateway 전송 대상과 달라집니다. 사용자·부서 매핑은 기존 unique 제약과 동시성 처리를 가진 중앙 관리자를 통해 생성해야 합니다. 비활성 매핑도 자동 재활성화하지 않습니다.

## 업무 서비스에서 호출하는 코드

다음은 **인증·권한·실제 소속·활성 매핑 검증을 마친 뒤** 실행하는 부분입니다. `projectId`, `signingKeyPath`, `userTopicFromDb`, `departmentTopicFromDb`, `noticeTopicFromDb`는 서버 설정/조회 결과이며 HTTP 요청에서 그대로 받는 값이 아닙니다.

```java
import com.sangwoo.push.example.StandaloneEnrollmentQrIssuer;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

// 생성기는 서버 시작 시 한 번 구성해서 재사용합니다.
var issuer = new StandaloneEnrollmentQrIssuer(
    projectId,
    StandaloneEnrollmentQrIssuer.loadPkcs8Pem(Path.of(signingKeyPath)),
    Clock.systemUTC());

// 인증/권한/소속 확인 후 서버 DB에서 얻은 활성 매핑만 전달합니다.
var result = issuer.issue(new StandaloneEnrollmentQrIssuer.Topics(
    userTopicFromDb, departmentTopicFromDb, noticeTopicFromDb),
    Duration.ofMinutes(3));

return ResponseEntity.ok()
    .contentType(MediaType.IMAGE_PNG)
    .cacheControl(CacheControl.noStore())
    .body(result.png());
```

이 코드는 업무 시스템 Controller/Service에 삽입하는 코드 조각이며 완성된 HTTP 엔드포인트가 아닙니다. 회사 인증 방식과 DB 스키마를 확정한 뒤 엔드포인트를 구성하세요. 예외는 서버에서 처리하되 응답/로그에 QR 본문, Topic, 개인키, 키 파일 경로를 노출하지 않습니다. 감사 로그에는 발급자·대상 내부 식별자·발급 시각·결과만 최소한으로 남기고 접근/보관 정책을 적용합니다.

## 앱과 동일해야 하는 QR 규격

QR 내용은 URL이나 PNG의 Base64가 아니라 JSON 문자열입니다. HTTP 응답만 PNG 바이너리입니다.

| 필드 | 형식 |
|---|---|
| version | 숫자 1 |
| firebaseProjectId | 앱 및 Gateway의 실제 Firebase 프로젝트 ID. 표시 이름 DEPL과 다를 수 있음 |
| topics.user | 기존 매핑의 usr_ + URL-safe 난수 문자열(접미사 최소 32자) |
| topics.department | 기존 매핑의 dept_ + URL-safe 난수 문자열(접미사 최소 32자) |
| topics.notice | notice_all |
| issuedAt / expiresAt | UTC ISO-8601 문자열. 기본 3분, 최대 10분 |
| nonce | 18바이트 난수의 URL-safe Base64, padding 없음 |
| signature | ECDSA P-256 / SHA-256 DER 서명의 표준 Base64 |

서명 대상은 JSON 전체가 아니라 아래 필드들을 원문 그대로 `|`로 연결한 UTF-8 바이트입니다. 공백/개행을 추가하지 않으며 서명 후 날짜 문자열도 변환하지 않습니다.

```text
1|firebaseProjectId|topics.user|topics.department|topics.notice|issuedAt|expiresAt|nonce
```

서명 개인키는 PKCS#8 PEM `BEGIN PRIVATE KEY` 형식이며 앱 공개키와 일치하는 P-256 키여야 합니다. 앱의 공개키 형식은 X9.63 비압축 65바이트(`04 || X || Y`)의 표준 Base64입니다. 새로운 개인키를 임의로 생성하면 기존 앱에서 서명 오류가 납니다. Firebase 서비스 계정 키 및 APNs .p8 키는 QR 서명 키와 별개입니다.

업무 시스템에서 기존 신뢰 키를 사용할 수 없다면 Gateway 서명 API를 사용하거나, 신규 발급 키를 신뢰하도록 앱의 키 배포/검증 구조부터 변경해야 합니다. 개인키는 외부 Secret/권한 제한 파일로 주입하고 Git, 문서, QR, 앱에 넣지 않습니다.

## 보안 및 운영 한계

- nonce가 있어도 현재 구조는 서버 차원의 일회용 등록이 아닙니다. 유효시간 내 다른 기기에서 같은 QR을 재사용할 수 있으므로 QR 공유/저장을 제한합니다.
- QR 만료는 새 등록 제한입니다. 이미 완료한 Topic 구독을 만료시키지 않습니다. 퇴사/부서 이동에 따른 구독 해제·Topic 교체 정책은 별도로 필요합니다.
- 서명은 정상 앱의 QR 검증 장치이며 FCM Topic 자체의 구독 권한을 강제하지 않습니다. Topic을 아는 다른 클라이언트의 구독을 막는 사용자 인증 수단으로 취급하지 마세요. 민감한 업무 내용은 보내지 않는 기존 정책을 유지합니다.
- 서버·기기 시계를 동기화합니다. 실제 등록/수신 테스트는 같은 프로젝트와 공개키를 설정한 기기로 수행합니다.

## 검증 방법

첨부 두 Java 파일을 위 의존성이 있는 classpath로 컴파일한 뒤 `com.sangwoo.push.example.StandaloneEnrollmentQrIssuerCheck`의 main을 실행합니다. 테스트는 메모리에서 임시 키를 만들며 실제 키/Topic/FCM을 사용하지 않습니다. PNG 디코딩, Gateway canonical 규칙 서명 검증, 변조 거부, TTL 경계, Topic 형식, nonce 중복 여부를 확인합니다. 기기 등록 및 실 Push 수신 검증은 별도입니다.
