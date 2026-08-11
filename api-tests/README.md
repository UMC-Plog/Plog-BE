# API E2E Tests

실행 중인 API 서버를 외부 클라이언트 관점에서 검증하는 REST Assured 프로젝트입니다. 테스트 코드의 Javadoc과 assertion을 실행 가능한 API 명세로 사용합니다.

## 요구 사항

- Java 21
- 테스트 대상 API 서버
- Gradle Wrapper

## 폴더 구조

```text
api-tests/
├── build.gradle
├── gradlew
├── run-api-tests.sh
├── stop-api-tests.sh
└── src/test/java/<base-package>/
    ├── <domain>/
    │   └── <Feature>ApiTest.java
    ├── smoke/
    │   └── ApiAvailabilityTest.java
    └── support/
        ├── <config-and-auth>
        └── client/
            ├── ApiClient.java
            └── <Domain>ApiClient.java
```

- 도메인 패키지는 API 계약과 테스트를 보관합니다.
- `smoke`는 서버의 핵심 가용성을 빠르게 검사합니다.
- `support`는 환경 설정과 인증을 담당합니다.
- `support.client`는 HTTP 요청을 전송하는 도메인별 Client를 보관합니다.

## 실행

서버 없이 컴파일과 자체 테스트를 확인합니다.

```bash
./gradlew test
```

실행 중인 격리 서버를 대상으로 모든 API 테스트를 실행합니다. 생성·수정·삭제 등 상태 변경 테스트도
함께 실행되므로 운영 서버나 공유 개발 서버를 대상으로 사용하지 않습니다.

```bash
./gradlew apiTest
```

다른 환경이나 인증 정보를 사용할 때는 프로젝트에서 정한 환경변수를 전달합니다.

```bash
PLOG_API_URL=https://api.example.com \
PLOG_TEST_EMAIL=<test-email> \
PLOG_TEST_PASSWORD=<test-password> \
PLOG_PROJECT_ID=<test-project-id> \
./gradlew apiTest
```

| 작업 | 용도 |
|---|---|
| `./gradlew test` | 실제 서버를 호출하지 않는 자체 테스트 |
| `./gradlew apiTest` | 실행 중인 격리 서버를 호출하는 모든 `live` 테스트 |

로컬 격리 환경의 서버 실행, 테스트 데이터 준비와 전체 API 테스트를 한 번에 수행합니다.
실행 중에는 Docker 준비 단계를 요약해 보여주고, 테스트 실패가 있으면 실패한 항목과 결과 리포트
경로를 출력합니다. 통과한 테스트 함수는 나열하지 않으며, `@Disabled` 등으로 실행하지 않은
테스트만 `SKIPPED`로 표시합니다. 실행이 끝나면 성공·실패·스킵 테스트 수를 요약합니다.

```bash
./run-api-tests.sh
```

테스트가 끝난 뒤 애플리케이션·PostgreSQL 컨테이너, Compose 네트워크와 테스트 DB 볼륨을 모두
정리합니다. Docker 이미지와 빌드 캐시는 유지되므로 다음 빌드에서 재사용됩니다.

```bash
./stop-api-tests.sh
```

## 테스트 컨벤션

### 블랙박스 원칙

- 공개 API만 사용해 테스트 데이터를 준비하고 정리합니다.
- 데이터베이스, 서버 내부 클래스와 관리용 내부 API에 의존하지 않는 것을 원칙으로 합니다.
- 공개 API로 재현할 수 없거나 준비 비용이 지나치게 큰 상태는 아래의 제한된 DB 상태 조작 원칙을 따릅니다.
- 대상 주소, 계정과 토큰은 환경변수로 전달합니다.
- 비밀정보를 소스, 리소스, 로그와 커밋에 기록하지 않습니다.
- Authorization, Cookie와 응답 토큰을 출력하는 전역 로깅을 사용하지 않습니다.

### 패키지와 클래스

- 패키지는 `<base-package>.<domain>` 형식으로 도메인별로 나눕니다.
- 테스트 클래스는 `<Feature>ApiTest`로 이름 짓습니다.
- 서버 내부 구현명인 `Controller`를 테스트 클래스 이름에 사용하지 않습니다.
- 한 테스트 클래스 안에서는 `@Nested`로 API별 테스트를 묶습니다.
- 실제 서버를 호출하는 테스트에는 `@Tag("live")`를 붙입니다.
- 빠른 핵심 검증은 `smoke`, 전체 회귀 검증은 `regression`, 상태 변경이나 삭제는 `destructive` 태그를 사용합니다.

### API Client

- API Client는 `<base-package>.support.client`에 도메인별로 둡니다.
- 하나의 공개 API 엔드포인트를 하나의 Client 메서드로 표현합니다.
- Client는 전달받은 매개변수로 HTTP 요청을 전송하고 `Response`만 반환합니다.
- Client는 기본값 적용, 입력값 변환, 응답 추출과 가공을 하지 않습니다.
- Client는 상태 코드나 응답 Body를 검증하지 않습니다.
- Client는 여러 API 호출을 조합하거나 데이터 준비·정리 정책을 숨기지 않습니다.
- 각 공개 메서드에는 `/** HTTP_METHOD /api/... */` 형식으로 대상 API를 기록합니다.

```java
public final class ResourceApiClient extends ApiClient {

    public ResourceApiClient(String baseUrl, String accessToken) {
        super(baseUrl, accessToken);
    }

    /** GET /api/resources/{resourceId} */
    public Response getResource(Object resourceId) {
        return request().get(
                "/api/resources/{resourceId}",
                resourceId
        );
    }

    /** POST /api/resources */
    public Response createResource(Object body) {
        return request()
                .body(body)
                .post("/api/resources");
    }

    /** DELETE /api/resources/{resourceId} */
    public Response deleteResource(Object resourceId) {
        return request().delete(
                "/api/resources/{resourceId}",
                resourceId
        );
    }
}
```

Client 내부에는 다음 코드를 작성하지 않습니다.

```text
.then()
statusCode()
jsonPath()
extract()
여러 API 요청의 순차 호출
```

### API 계약 Javadoc

- 최상위 테스트 클래스의 Javadoc에는 클래스에서 검증하는 모든 HTTP 메서드와 URL을 나열합니다.
- 최상위 클래스에는 API 목록만 기록합니다.
- 각 `@Nested` Javadoc에는 API, 인증, 요청, 요청 Body, 성공 응답, 응답 Body, 핵심 계약과 상태 변경을 기록합니다.
- 항목 설명은 `<p>`, API 경로와 응답 코드는 `{@code ...}`로 작성합니다.
- 요청과 응답 Body는 `<pre>{@code ...}</pre>`로 감싸 JSON의 줄바꿈과 들여쓰기를 보존합니다.
- 핵심 계약은 `<ul>`과 `<li>`를 사용해 목록으로 작성합니다.
- Path와 Query parameter는 `요청`에 기록하고 JSON Body에 섞지 않습니다.
- 경로 변수는 실제 값 대신 `{resourceId}`처럼 표기합니다.
- 요청과 응답 Body는 유효한 JSON으로 작성합니다.
- JSON 필드와 중첩 객체·배열은 줄바꿈하고 들여씁니다.
- 필드 생략을 의미하는 `...`는 사용하지 않습니다.
- 요청 Body가 없으면 `요청 Body: 없음`으로 기록합니다.
- 권한 조건과 권한이 없는 요청의 거부 동작은 별도의 `권한` 항목으로 분리하지 않고 `핵심 계약`에 기록합니다.
- 권한에 관한 핵심 계약도 다른 계약과 동일하게 최소 하나의 테스트 메서드로 검증합니다.
- `핵심 계약`에는 현재 테스트가 실제로 검증하는 내용만 기록합니다.
- 핵심 계약 한 항목에는 최소 하나의 테스트 메서드가 대응되어야 합니다.
- 아직 테스트하지 않는 계약은 완료된 핵심 계약처럼 기록하지 않습니다.

```java
/**
 * 리소스 API
 *
 * APIs:
 * - GET /api/resources/{resourceId}
 * - POST /api/resources
 * - DELETE /api/resources/{resourceId}
 */
@Tag("live")
class ResourceApiTest {

    /**
     * 리소스 상세 조회
     *
     * <p>API: {@code GET /api/resources/{resourceId}}</p>
     * <p>인증: Access Token 필요</p>
     * <p>요청: resourceId 경로 변수</p>
     * <p>요청 Body: 없음</p>
     * <p>성공: HTTP 200, {@code SUCCESS_CODE}</p>
     *
     * <p>응답 Body:</p>
     * <pre>{@code
     * {
     *   "success": true,
     *   "code": "SUCCESS_CODE",
     *   "result": {
     *     "resourceId": 123,
     *     "name": "Example"
     *   }
     * }
     * }</pre>
     *
     * <p>핵심 계약:</p>
     * <ul>
     *   <li>조회 권한이 있는 사용자에게 리소스 상세를 반환한다.</li>
     *   <li>인증되지 않은 요청을 거부한다.</li>
     * </ul>
     *
     * <p>상태 변경: 없음</p>
     */
    @Nested
    class 리소스_상세_조회 {
    }
}
```

### 테스트 이름

- 테스트 메서드는 한글 스네이크 케이스로 행위와 기대 결과가 드러나게 작성합니다.
- `성공()`, `실패()`처럼 주변 문맥에 의존하는 이름을 사용하지 않습니다.
- 별도의 `@DisplayName`을 사용하지 않습니다.
- `@Nested` 클래스도 한글 스네이크 케이스로 대상 기능을 표현합니다.

```java
@Test
void 조회_권한이_있는_사용자는_리소스_상세를_조회할_수_있다() {
}
```

### 테스트 본문

- 테스트는 `// 준비 과정`, `// 실행 및 검증`, `// 마무리 정리` 구역으로 나눕니다.
- 필요한 준비나 정리가 없으면 해당 구역을 생략할 수 있습니다.
- 준비와 정리에서도 API Client를 직접 호출합니다.
- 별도의 범용 Fixture나 준비 helper에 Client 호출 순서를 숨기지 않습니다.
- 상태를 변경하는 테스트는 `try/finally`를 사용해 검증 실패 시에도 정리합니다.
- 자신이 공개 API로 생성하거나 변경한 상태만 정리합니다.
- 응답은 HTTP 상태, 공통 응답 코드, 핵심 필드 순으로 검증합니다.
- 응답 전체 JSON 문자열 대신 필요한 필드만 JSON Path로 검증합니다.
- 동일한 입력의 경계값은 parameterized test를 우선 고려합니다.

```java
// 준비 과정
Response createResponse = resourceApiClient.createResource(
        Map.of("name", "E2E Resource")
);
long resourceId = createResponse.then()
        .statusCode(201)
        .body("code", equalTo("CREATED_CODE"))
        .extract()
        .jsonPath()
        .getLong("result.resourceId");

try {
    // 실행 및 검증
    resourceApiClient.getResource(resourceId)
            .then()
            .statusCode(200)
            .body("code", equalTo("SUCCESS_CODE"))
            .body("result.resourceId", equalTo((int) resourceId));
} finally {
    // 마무리 정리
    resourceApiClient.deleteResource(resourceId);
}
```

### 테스트 데이터와 상태

- 생성 데이터에는 UUID 같은 실행별 고유 식별자를 사용합니다.
- 로그인 토큰과 테스트 전용 상위 리소스 같은 기반 상태만 클래스 수준에서 공유할 수 있습니다.
- 시나리오가 변경하는 리소스는 테스트 간에 공유하지 않습니다.
- 삭제 테스트에는 다른 테스트가 사용하지 않는 전용 리소스를 준비합니다.
- 테스트 실행 순서에 의존하지 않습니다.

### API로 재현할 수 없는 상태

- 일반 데이터와 상태는 공개 API로 준비하는 것을 우선합니다.
- 공개 API로 상태를 만들 수 없거나 실행 비용이 지나치게 큰 경우에만 격리 테스트 DB를 직접 조작할 수 있습니다.
- 시간 경과, 외부 시스템 결과, 비동기 작업의 중간·실패 상태, 대용량 경계 데이터,
  장애 복구 상태, 레거시 데이터와 동시성·멱등성 사전 상태 등이 여기에 해당할 수 있습니다.
- 가능한 부분은 먼저 공개 API로 생성하고, DB 헬퍼는 현재 테스트가 생성하거나 전용으로 확보한 데이터에서
  API로 만들 수 없는 최소 상태만 변경합니다.
- DB 조작은 `TimeTravelSupport`, `AsyncStateSupport`처럼 역할이 분명한 목적별 헬퍼에 모으고
  테스트 본문에 SQL을 직접 작성하지 않습니다.
- 헬퍼는 목적별 메서드만 공개하며 임의 SQL 실행 기능, API 호출과 결과 검증을 포함하지 않습니다.
- 허용된 격리 테스트 DB가 아니면 실행을 중단하고, 운영 환경이나 공유 환경의 DB를 변경하지 않습니다.
- 기능 실행과 결과 검증은 DB 상태 조작 후에도 반드시 공개 API를 통해 수행합니다.

```java
long resourceId = resourceApiClient.createResource(request)
        .then()
        .statusCode(201)
        .extract()
        .jsonPath()
        .getLong("result.resourceId");

timeTravelSupport.makeRetentionPeriodExpired(resourceId);

resourceApiClient.processExpiredResource(resourceId)
        .then()
        .statusCode(200);
```

### 검증

- 새 테스트를 추가한 뒤 최소한 `./gradlew test`로 컴파일과 자체 테스트를 확인합니다.
- 실제 API 테스트 결과에는 대상 환경을 기록하되 비밀정보를 포함하지 않습니다.
