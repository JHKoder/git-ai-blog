# Backend — AI Blog Automation

> Spring Boot 4.0.3 · Java 25 · Gradle 9.3.1

---

## 기술 스택

| 영역        | 기술                                                                               |
|-----------|----------------------------------------------------------------------------------|
| 프레임워크     | Spring Boot 4.0.3, Java 25, Gradle 9.3.1                                         |
| 인증        | Spring Security 7.x + GitHub OAuth2 + JWT (Access 24h / Refresh 30일 HttpOnly 쿠키) |
| DB        | H2 (local) / PostgreSQL Supabase (dev/prod) + JPA + Hibernate 7.2                |
| 외부 API    | WebClient (WebFlux) — Claude, Grok, GPT, Gemini, GitHub, Hashnode, Cloudinary    |
| 캐시        | Redis — AI 사용량, Rate Limit, JWT Refresh blacklist                                |
| 암호화       | Jasypt `PBEWithMD5AndDES` — dev/prod yml 값 암호화                                   |
| DB 컬럼 암호화 | `@Convert` + AES-256-GCM — Member 민감 필드                                          |
| 탄력성       | Resilience4j (재시도, 서킷브레이커)                                                       |
| API 문서    | springdoc-openapi 2.8.8 — `/swagger-ui/index.html`, JWT Bearer 인증 스킴             |

---

## 패키지 구조

```
github.jhkoder.aiblog/
├── common/               ApiResponse, GlobalExceptionHandler, DbMigrationRunner
│   exception/            NotFoundException, InvalidStateException, BusinessRuleException
│                         ExternalApiException, RateLimitException
├── config/               SecurityConfig, WebClientConfig, WebMvcConfig, JasyptConfig
│                         RedisConfig, AesGcmEncryptionConverter, OpenApiConfig, AsyncConfig
├── security/             JwtProvider, JwtAuthenticationFilter, RefreshTokenService
│                         CustomOAuth2UserService, OAuth2SuccessHandler, AuthController
│                         MockLoginController
├── infra/ai/             AiClient(interface), AiClientRouter, AiUsageLimiter
│                         ClaudeClient, GrokClient, GptClient, GeminiClient
│                         RateLimitCache, TokenUsageTracker, ImageUsageLimiter
│                         prompt/PromptBuilder
├── infra/github/         GitHubClient, WebhookSignatureVerifier
├── infra/hashnode/       HashnodeClient, HashnodeGraphqlBuilder
├── infra/image/          CloudinaryClient, GptImageClient, GeminiImageClient
│                         ImageGenerationService
├── post/                 Post, PostStatus, ContentType, PostRepository
│                         PostController, 9개 UseCase
│                         dto/PostPageResponse (Page<T> 직렬화 DTO)
├── suggestion/           AiSuggestion (durationMs 컬럼), AiSuggestionRepository
│                         AiSuggestionController, 7개 UseCase
│                         (RequestAiSuggestionUseCase → 202 즉시 반환)
│                         (AsyncAiSuggestionService → @Async AI 처리 + DB 저장)
│                         (StreamAiSuggestionUseCase → SSE Flux<String> 스트리밍)
├── member/               Member, MemberRepository
│                         MemberController, 4개 UseCase
├── repo/                 Repo, RepoCollectHistory, RepoRepository, CollectType
│                         RepoController, GitHubWebhookController, 5개 UseCase
├── prompt/               Prompt, PromptRepository
│                         PromptController, 5개 UseCase (내 프롬프트 CRUD + 인기 프롬프트)
└── sqlviz/               SqlVizWidget, SqlVizScenario, IsolationLevel
                          SqlVizController, SqlVizEmbedController, 4개 UseCase
                          simulation/SqlVizSimulationEngine, SqlParser, ParsedSql
                                     RowKey, VirtualDatabase, VirtualRow, TransactionContext
                                     SimulationResult, SimulationStep (6개 시나리오 가상 시뮬레이션)
```

---

## 핵심 도메인 정책

### Post 상태 머신

```
DRAFT → AI_SUGGESTED → ACCEPTED → PUBLISHED
             ↓ (거절)
            DRAFT
```

- 상태 전이는 도메인 메서드로만: `markAiSuggested()`, `accept()`, `markPublished()`, `revertFromAiSuggested()`
- PUBLISHED에서도 재발행·AI 개선 가능. 모든 상태에서 삭제 가능

### Hashnode 발행 분기

- `hashnodeId` 없음 → `publishPost` (신규) / 있음 → `updatePost` (수정)
- **prod 프로파일에서만 실제 발행 허용** — local/dev는 `BusinessRuleException` (422) 반환
  (`PublishPostUseCase`에서 `@Value("${spring.profiles.active:local}")` 체크)

**발행 전 사전 검증 (PublishPostUseCase)**:
- 제목 6자 미만 → 422 (Hashnode API 요구사항)
- 본문 비어있음 → 422
- Hashnode 미연동 (token null/blank) → 422
- Hashnode Publication ID null/blank → 422

**Hashnode API 통신 방식**:
- GraphQL variables 분리 방식 사용 (`HashnodeGraphqlBuilder` → `GqlRequest(query, variables)`)
- `escapeGraphql()` 사용 금지 — `objectMapper`가 단일 직렬화 담당 (이중 이스케이프 방지)
- 4xx 응답 시 `onStatus`로 바디 읽어 GraphQL `errors[0].message` 추출 후 로깅

### AI 요청 비동기 처리 (연결 끊김 방지)

**방안 A — @Async + 폴링:**
- `POST /api/ai-suggestions/{postId}` → 202 Accepted 즉시 반환
- `AsyncAiSuggestionService.executeAsync()`: `@Async("aiTaskExecutor")` 별도 스레드에서 AI 처리 → DB 저장
- `AsyncConfig`: `aiTaskExecutor` (core=4, max=20, queue=50, prefix=`ai-async-`)
- 클라이언트는 `/latest` 를 3초 간격 폴링 → 새 제안 감지 시 중단

**방안 B — SSE 스트리밍:**
- `POST /api/ai-suggestions/{postId}/stream` → `text/event-stream` 반환
- `StreamAiSuggestionUseCase.stream()`: 4개 AI 클라이언트 `streamComplete()` 호출 → `Flux<String>`
- 각 토큰은 `event: token` / `data: <텍스트>` SSE로 emit, 완료 시 `event: done` / `data: [DONE]`
- 스트리밍 완료 시 `doOnComplete` 콜백에서 DB 저장 + durationMs 기록 + 토큰 사용량 기록
- 클라이언트가 끊겨도 백엔드는 계속 실행 → 완료 후 DB 저장 보장
- nginx: `/api/ai-suggestions/*/stream` 경로에 `proxy_buffering off`, `proxy_read_timeout 300s`

**AI 스트리밍 API 형식 (클라이언트별):**
| 클라이언트 | 엔드포인트 | 텍스트 추출 경로 |
|---------|---------|-------------|
| Claude  | `POST /v1/messages` + `"stream": true` | `type == content_block_delta` → `delta.text` |
| GPT     | `POST /v1/chat/completions` + `"stream": true` | `choices[0].delta.content` |
| Grok    | OpenAI 호환 (GPT와 동일) | `choices[0].delta.content` |
| Gemini  | `POST /v1beta/models/{model}:streamGenerateContent?alt=sse` | `candidates[0].content.parts[0].text` |

**durationMs:**
- `AiSuggestion` 테이블에 `durationMs BIGINT` 컬럼 (nullable — 이전 데이터는 null)
- `AiSuggestion.createWithDuration()` 팩토리 메서드로 저장
- `findAvgDurationMsByModel(model)`: `WHERE durationMs IS NOT NULL AND durationMs > 0` 평균 조회

---

### AI 사용량 제한

- Redis 기반. 한도 초과 시 즉시 429 반환. Redis TTL로 자정 자동 초기화
- 우선순위: 모델별 한도 > 전체 한도(`aiDailyLimit`) > 서버 기본값(`ai.daily-limit`, 기본 5회)
- 사용자별 한도는 `Member` 필드로 관리: `aiDailyLimit`(전체), `claudeDailyLimit`, `grokDailyLimit`, `gptDailyLimit`, `geminiDailyLimit`
- Redis 키: 전체 `ai_usage:{memberId}:{date}` / 모델별 `ai_usage:{memberId}:{model}:{date}`

### AI 클라이언트 라우팅

| ContentType | 기본 모델             |
|-------------|-------------------|
| ALGORITHM   | Grok 3            |
| 나머지 전체      | Claude Sonnet 4.6 |

선택 가능 모델: `claude-sonnet-4-6`, `claude-opus-4-5`, `grok-3`, `gpt-4o`, `gpt-4o-mini`, `gemini-2.0-flash`
**이미지 생성은 GPT 모델 전용** (gpt-4o, gpt-4o-mini)

### JWT 인증

- Access Token: 24h, 응답 바디 / Refresh Token: 30일, HttpOnly 쿠키
- 로그아웃 시 Redis blacklist 등록 (TTL = 잔여 유효시간), Rotation 적용

### DB 컬럼 암호화

- `claudeApiKey`, `grokApiKey`, `gptApiKey`, `geminiApiKey`, `githubToken`, `hashnodeToken` 등 AES-256-GCM 암호화
- Response DTO에 절대 포함 금지 → boolean 여부만 응답

### 삭제 정책

- Hashnode 연동 글: Hashnode 삭제 실패해도 DB 삭제 반드시 진행
- 관련 AI 제안 함께 삭제. `deleteById()` 직접 노출 금지

---

## 구현된 API 엔드포인트

### Post

| 기능            | 엔드포인트                                 | 비고                       |
|---------------|---------------------------------------|--------------------------|
| 생성            | `POST /api/posts`                     |                          |
| 목록            | `GET /api/posts`                      | 페이징, 본인 글만               |
| 상세            | `GET /api/posts/{id}`                 | 조회수 자동 증가                |
| 수정            | `PUT /api/posts/{id}`                 |                          |
| 삭제            | `DELETE /api/posts/{id}`              | Hashnode 연동 삭제 포함        |
| 발행            | `POST /api/posts/{id}/publish`        | hashnodeId 유무로 분기        |
| Hashnode 동기화  | `POST /api/posts/sync-hashnode`       | added/updated/deleted 반환 |
| Hashnode 가져오기 | `POST /api/posts/import-hashnode`     | 연동된 글 전체 DB 저장           |
| 이미지 생성        | `POST /api/posts/{id}/generate-image` | GPT 전용, Cloudinary 업로드   |
| AI 사용량        | `GET /api/posts/ai-usage`             | 호출 횟수 + Rate Limit + 토큰  |

### AI Suggestion

| 기능           | 엔드포인트                                               | 비고                             |
|--------------|-----------------------------------------------------|--------------------------------|
| AI 요청 (비동기)  | `POST /api/ai-suggestions/{postId}`                 | 202 Accepted 즉시 반환, @Async 처리  |
| AI 스트리밍 요청   | `POST /api/ai-suggestions/{postId}/stream`          | `text/event-stream`, 토큰 단위 실시간 |
| 최신 제안        | `GET /api/ai-suggestions/{postId}/latest`           |                                |
| 히스토리         | `GET /api/ai-suggestions/{postId}/history`          |                                |
| 수락           | `POST /api/ai-suggestions/{postId}/{id}/accept`     |                                |
| 거절           | `POST /api/ai-suggestions/{postId}/{id}/reject`     |                                |

### Member

| 기능          | 엔드포인트                                  |
|-------------|----------------------------------------|
| 프로필 조회      | `GET /api/members/me`                  |
| Hashnode 연동 | `POST /api/members/hashnode-connect`   |
| Hashnode 해제 | `DELETE /api/members/hashnode-connect` |
| API 키 설정    | `PATCH /api/members/api-keys`          |

### Repo / Webhook

| 기능       | 엔드포인트                                               |
|----------|-----------------------------------------------------|
| 목록/추가/삭제 | `GET/POST/DELETE /api/repos`                        |
| 수집       | `POST /api/repos/{id}/collect`                      |
| 웹훅       | `POST /api/webhook/github` (X-Hub-Signature-256 검증) |
| 인증       | `POST /api/auth/refresh`, `POST /api/auth/logout`   |

### Prompt (커스텀 프롬프트)

| 기능               | 엔드포인트                                          |
|------------------|------------------------------------------------|
| 내 프롬프트 목록        | `GET /api/prompts`                             |
| 생성               | `POST /api/prompts`                            |
| 수정/삭제            | `PUT/DELETE /api/prompts/{id}`                 |
| 공개 인기 프롬프트       | `GET /api/prompts/popular`                     |
| 특정 사용자 인기 프롬프트   | `GET /api/prompts/members/{id}/popular`        |

- 사용자당 최대 30개 / 사용 횟수 내림차순 정렬 / `isPublic` 플래그로 공개/비공개 설정
- 입력 제한: `title` 100자, `content` 2000자 (`@Size` 검증, 400 반환)
- `AiSuggestionRequest.extraPrompt` 500자 제한
- `AiSuggestionRequest`에 `promptId` 포함 시 해당 프롬프트 적용 후 `usageCount` 증가

### SQL Visualization Widget

| 기능         | 엔드포인트                      | 인증 |
|------------|----------------------------|----|
| 위젯 생성      | `POST /api/sqlviz`         | ✅  |
| 내 위젯 목록   | `GET /api/sqlviz`          | ✅  |
| 위젯 삭제      | `DELETE /api/sqlviz/{id}`  | ✅  |
| 공개 임베드 조회  | `GET /api/embed/sqlviz/{id}` | ❌  |

**보안 원칙**: SQL 직접 실행 절대 금지 — 순수 Java 로직 가상 시뮬레이션만 사용
**Jackson 주의**: Spring Boot 4 + Jackson 3.x에서 `tools.jackson.databind.ObjectMapper` / `tools.jackson.core.JacksonException` / `tools.jackson.core.type.TypeReference` 사용 (`com.fasterxml.jackson` 아님)
**JPA Repository 주의**: `JpaSqlVizWidgetRepository`는 반드시 `public interface`여야 Spring Data JPA 빈 등록됨

---

## 프로파일 / 환경변수 정책

| 프로파일    | DB       | Jasypt     | Redis  | 비고                                                   |
|---------|----------|------------|--------|------------------------------------------------------|
| `local` | H2       | 기본값(dummy) | 불필요    | CI도 local 사용. `JASYPT_ENCRYPTOR_PASSWORD` 없어도 기동 가능  |
| `dev`   | Docker PostgreSQL (localhost:5432) | 필수 | 로컬 필수 | `./gradlew serverRun` 으로 Redis + PostgreSQL 자동 기동 |
| `prod`  | Supabase | 필수         | Docker | `application-prod.yml` + `JASYPT_ENCRYPTOR_PASSWORD` |

**prod 전용:** `spring.datasource.hikari.data-source-properties.prepareThreshold: 0`
→ Supabase PgBouncer 트랜잭션 모드 prepared statement 충돌 방지

> **Jasypt 주의**: AI가 직접 암호화하지 않는다. jasypt online tool에서 수동 암호화 후 yml에 붙여넣기.
> 알고리즘: `PBEWithMD5AndDES`, bean: `jasyptStringEncryptor`

**Jasypt ENC() 암호화 대상:**
`db.encryption.key`, `jwt.secret`, `cloudinary.*`, `spring.datasource.url/username/password`,
`spring.security.oauth2.client.registration.github.client-id/client-secret`

---

## 개발 환경 실행

```bash
# local (H2, 환경변수 불필요 — Java 25는 build.gradle toolchain이 자동 처리)
cd backend && ./gradlew bootRun

# dev (Docker PostgreSQL + Redis 자동 기동, JASYPT는 해당 PC에 이미 설정됨)
cd backend && ./gradlew serverRun

# 테스트
./gradlew test
```

> `JASYPT_ENCRYPTOR_PASSWORD`는 각 PC의 환경변수로 직접 설정. `.env` 파일 사용 금지.
> Java 25는 `build.gradle`의 `java.toolchain.languageVersion = 25` 설정으로 Gradle이 자동 관리.



---

## 테스트 현황

| 파일                                                                                               | 유형                        |
|--------------------------------------------------------------------------------------------------|---------------------------|
| `PostControllerTest`, `MemberControllerTest`                                                     | @WebMvcTest (Security 포함) |
| `PostRepositoryTest`, `MemberRepositoryTest`, `AiSuggestionRepositoryTest`, `RepoRepositoryTest` | @SpringBootTest + H2      |
| `PostDomainTest`, `MemberDomainTest`, `WebhookSignatureVerifierTest`                             | 도메인 단위                    |
| `CreatePostUseCaseTest`, `ImportHashnodePostUseCaseTest`, `AiClientRouterTest`                   | UseCase 단위                |
| `StreamAiSuggestionUseCaseTest`                                                                  | UseCase 단위 (StepVerifier) |
| `AiSuggestionControllerSseTest`                                                                  | SSE Controller 통합 (MockMvc) |
| `StreamAiSuggestionSaveTest`                                                                     | DB 저장 통합 (dump_post.txt 활용) |

테스트 설정: `test/resources/application.yml` — OAuth2 mock, Jasypt bean, Redis 제외

---

## SSE 스트리밍 테스트 설계

> 목표: 실 AI API 호출 없이 SSE 파이프라인 전체(`estimated` → `token` × N → `done` → DB 저장)를 검증.
> 토큰 낭비 방지 + 긴 대기 없이 로컬에서 빠르게 반복 실행 가능.

### 핵심 아이디어: `MockAiClient` (프로파일 분기)

```java
// @Profile("local") 전용 mock 빈 — AiClient 인터페이스 구현
// AiClientRouter가 실제 환경에서는 ClaudeClient 등을 주입하지만,
// local 프로파일에서는 이 MockAiClient가 우선 등록됨
@Profile("local")
@Component("mockAiClient")
public class MockAiClient implements AiClient {

    // local=100ms × 100청크 ≈ 10초 / dev·prod=600ms × 100청크 ≈ 60초
    @Value("${ai.test.chunk-delay-ms:100}")
    private long chunkDelayMs;

    // src/test/resources/dump_post.txt 를 청크로 분할해 실제 블로그 글과 유사한 스트리밍 시뮬레이션
    @Value("classpath:dump_post.txt")
    private Resource dumpPostResource;

    @Override
    public Flux<String> streamComplete(String prompt, String model, String apiKey) {
        String content = loadDumpPost();  // dump_post.txt 전체 텍스트
        List<String> chunks = splitIntoChunks(content, 50);  // 50자씩 분할
        return Flux.fromIterable(chunks)
                   .delayElements(Duration.ofMillis(chunkDelayMs));
    }

    @Override
    public String complete(String prompt, String model, String apiKey) {
        try { Thread.sleep(chunkDelayMs * 10); } catch (InterruptedException ignored) {}
        return "MockAiClient: 테스트 응답";
    }
}
```

**`application-local.yml`에 추가:**
```yaml
ai:
  test:
    chunk-delay-ms: 100   # local: 10초 (100ms × 100청크)
```

**`application-dev.yml` / `application-prod.yml`에 추가:**
```yaml
ai:
  test:
    chunk-delay-ms: 600   # dev/prod: 60초 (600ms × 100청크)
```

**`dump_post.txt` 활용:**
- `src/test/resources/dump_post.txt` — 실제 블로그 글 수준의 마크다운 내용 (DB 동시성 가이드)
- 50자 단위로 청크 분할 → 실제 AI 토큰 스트리밍과 유사한 패턴 재현
- 총 텍스트 크기 약 4KB → 100ms delay × ~80청크 ≈ 8초 (local 목표 10초에 근접)

> **주의**: MockAiClient를 `@Profile("local")`로 제한하더라도, `AiClientRouter.route()`가 실제 API 키 없이 mock을 직접 반환하도록 local 프로파일 분기 추가 필요.

---

### 테스트 전략 — 3단계

#### 1단계: UseCase 단위 테스트 (`StreamAiSuggestionUseCaseTest`)

```java
// MockitoExtension, MockAiClient를 직접 주입
// 검증: Flux 첫 요소가 "__estimated__:N" 인지, 이후 token emit 순서, saveResult 호출 여부
@Test
void stream_emitsEstimatedThenTokens() {
    MockAiClient mockClient = new MockAiClient();  // chunkDelayMs=0 (즉시)
    StreamAiSuggestionUseCase useCase = new StreamAiSuggestionUseCase(/* ... mockClient ... */);

    Flux<String> result = useCase.stream(postId, memberId, request);

    StepVerifier.create(result)
        .assertNext(s -> assertThat(s).startsWith("__estimated__:"))
        .thenConsumeWhile(s -> !s.isEmpty())  // 토큰 스트림
        .verifyComplete();
}
```

#### 2단계: Controller SSE 통합 테스트 (`AiSuggestionControllerSseTest`)

```java
// @SpringBootTest(webEnvironment = RANDOM_PORT) + @ActiveProfiles("local")
// WebTestClient로 text/event-stream 소비
@Test
void stream_sseEventSequence() {
    webTestClient.post()
        .uri("/api/ai-suggestions/{postId}/stream", postId)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .bodyValue(new AiSuggestionRequest())
        .accept(MediaType.TEXT_EVENT_STREAM)
        .exchange()
        .expectStatus().isOk()
        .returnResult(ServerSentEvent.class)
        .getResponseBody()
        .as(StepVerifier::create)
        .assertNext(e -> assertThat(e.event()).isEqualTo("estimated"))
        .thenConsumeWhile(e -> "token".equals(e.event()))
        .assertNext(e -> assertThat(e.event()).isEqualTo("done"))
        .verifyComplete();
}
```

#### 3단계: DB 저장 검증 (`StreamAiSuggestionSaveTest`)

```java
// @SpringBootTest + H2, local 프로파일
// stream 호출 후 block() 대기 → aiSuggestionRepository.findAll() 으로 저장 확인
// durationMs > 0, model, suggestedContent not null 검증
```

---

### 파일 위치 계획

```
src/test/java/.../suggestion/
├── StreamAiSuggestionUseCaseTest.java      (단위 — StepVerifier)
├── AiSuggestionControllerSseTest.java      (통합 — WebTestClient SSE)
└── StreamAiSuggestionSaveTest.java         (통합 — DB 저장 검증)

src/test/java/.../infra/ai/
└── MockAiClient.java                       (or src/main/java @Profile("local"))
```

---

### 지연 시간 매트릭스

| 프로파일      | `chunk-delay-ms` | 청크 수 (예시) | 총 소요 |
|-----------|-----------------|-----------|------|
| `local`   | 100ms           | 100개      | 10초  |
| `dev`     | 600ms           | 100개      | 60초  |
| `prod`    | 600ms           | 100개      | 60초  |
| `test` (단위) | 0ms          | 5개        | 즉시   |

> `MockAiClient`의 fake 토큰 개수와 delay를 분리하면 "빠른 단위 테스트 / 느린 통합 테스트" 두 가지 시나리오를 하나의 구현으로 커버할 수 있음.

---

## 주요 이슈 해결 기록

| 문제                                        | 원인                         | 해결                                  |
|-------------------------------------------|----------------------------|-------------------------------------|
| `prepared statement "S_1" already exists` | Supabase PgBouncer 트랜잭션 모드 | `prepareThreshold: 0`               |
| `JASYPT_ENCRYPTOR_PASSWORD` 특수문자 오염       | shell `$` 해석               | 작은따옴표로 감쌈                           |
| backend `unhealthy`                       | Actuator 미포함               | `spring-boot-starter-actuator` 추가   |
| QEMU arm64 curl segfault                  | apt curl + QEMU 비호환        | wget으로 HEALTHCHECK 변경               |
| Hashnode `INVALID_QUERY`                  | Stellate CDN variables 거부  | GraphQL 쿼리에 값 직접 인라인                |
| `Post.tags` LazyInitializationException   | 트랜잭션 종료 후 직렬화              | `List.copyOf(post.getTags())`       |
| SyncHashnode Duplicate key                | DB에 동일 hashnodeId 중복       | `Collectors.toMap` mergeFunction 추가 |
| 테스트 34개 실패 (ClientRegistrationRepository) | test yml에 OAuth2 설정 누락     | mock OAuth2 설정 추가                   |
| `--no-daemon` 등 플래그가 태스크명으로 파싱됨           | gradlew eval `"$@"` 버그     | `"$@"` → `$@` 으로 수정 (gradlew 157번 줄) |
| Hashnode 발행 400 Bad Request                   | `escapeGraphql()` 후 `objectMapper.writeValueAsString()` 이중 이스케이프 | GraphQL variables 방식으로 전환 (`HashnodeGraphqlBuilder` 전면 교체). `deletePost` 호출 시 token 누락도 수정 |
| 테스트 21개 실패 (`NoSuchBeanDefinitionException`) | 1) `JpaSqlVizWidgetRepository` package-private → Spring Data JPA 빈 등록 실패. 2) Spring Boot 4 / Jackson 3.x에서 `com.fasterxml.jackson.databind.ObjectMapper`로 주입 불가 | 1) `public interface` 추가. 2) `tools.jackson.databind.ObjectMapper` 임포트로 교체 |
| AI 응답이 글 중간에서 잘림 | `ClaudeClient.max_tokens: 4096` 고정 — Sonnet 4.6 최대 16,384 대비 부족 | `max_tokens` 를 `16000`으로 상향 (`ClaudeClient.java:46`) |
| prod SSE `Access Denied` + `response already committed` 반복 (1차) | nginx SSE location에 `proxy_set_header` 지정 시 **기본 헤더 상속 끊김** — `Authorization` 헤더가 백엔드에 미전달 → JWT 인증 실패 | `nginx.conf` SSE·`/api/` location에 `proxy_set_header Authorization $http_authorization` 추가 |
| AI 클라이언트 스트리밍 토큰 0개 반환 (1) | `ObjectMapper`를 `new ObjectMapper()` (`com.fasterxml`)로 직접 생성 → Spring Boot 4 + Jackson 3.x 환경에서 JSON 파싱 실패 → `catch ignored` → `Flux.empty()` | `tools.jackson.databind.ObjectMapper`를 `@RequiredArgsConstructor` 주입으로 교체 (Claude/Grok/GPT/Gemini 4개) |
| AI 클라이언트 스트리밍 토큰 0개 반환 (2) | `WebClient.bodyToFlux(String.class)`는 SSE `data:` 접두사를 자동 제거 후 JSON만 전달 → 기존 `line.startsWith("data: ")` 조건에서 전부 `Flux.empty()` | `data:` 유무 무관하게 파싱하도록 수정 |
| SSE `done` 수신 시 `fetchLatest` 404 | `concatWith(done)` 방식은 `doOnComplete(saveResult)` 이전에 `done` emit → 프론트가 DB 저장 전에 조회 | `Flux.defer(saveResult → __done__)` 로 변경 — DB 저장 완료 후 `done` emit 보장 |
| prod SSE `Access Denied` + `response already committed` 반복 (2차, 근본 원인) | Spring MVC `Flux<ServerSentEvent>` 반환 시 Tomcat이 `AsyncContextImpl.dispatch()`로 **async dispatch 재진입** — `JwtAuthenticationFilter`만 스킵해도 `SecurityContextHolderFilter` → `AnonymousAuthenticationFilter` → `AuthorizationFilter`가 빈 SecurityContext로 재실행 → Access Denied | `SecurityConfig`에 `dispatcherTypeMatchers(ASYNC).permitAll()` 추가 — ASYNC dispatch 자체를 인가 체크에서 제외. `JwtAuthenticationFilter.shouldNotFilterAsyncDispatch() = true`는 JWT 재인증 시도 방지 보조 역할로 유지 |
| `Page<T>` 직렬화 경고 (`Serializing PageImpl instances as-is`) | `PostController.list()`가 `Page<PostListResponse>` 직접 반환 | `PostPageResponse` record DTO 도입, `GetPostListUseCase` 반환 타입 변경 |
| 거절 후 수락 불가 | `Post.accept()`가 `AI_SUGGESTED` 상태만 허용 → 거절(`DRAFT` 복귀) 후 히스토리 제안 수락 시 `InvalidStateException` | `accept()` 상태 검사 제거 — 모든 상태에서 수락 허용 |
| AI 요청 클라이언트 연결 끊김 | `POST /api/ai-suggestions/{postId}` 동기 처리 — 30~60초 HTTP 연결 유지 필요 → 브라우저/프록시 타임아웃으로 끊김 | 방안 A: `@Async` + 202 즉시 반환 + 폴링 / 방안 B: SSE 스트리밍 (`Flux<ServerSentEvent>`) |
| Gemini `streamComplete()` 컴파일 오류 | `HttpHeaders` import 누락 | `import org.springframework.http.HttpHeaders` 추가 (`GeminiClient.java`) |
| `[StreamAI] 완료 후 저장 실패: 이미 AI 제안 상태입니다.` | `doOnComplete`는 리액터 스레드에서 실행 → `@Transactional` 미적용. `post.markAiSuggested()` 가 이미 `AI_SUGGESTED` 상태에서 예외 | `saveResult()` 메서드로 분리해 별도 `@Transactional` 적용. `markAiSuggested()` 호출 전 현재 상태 체크 — 멱등 처리 |
| `AuthorizationDeniedException` + `response has already been committed` | SSE 응답 flush 후 예외가 컨테이너로 전파 → Spring이 error page 렌더링 시도하지만 응답이 이미 커밋됨 | `onErrorResume`으로 에러를 `event: error` SSE 이벤트로 클라이언트에 전달 후 스트림 종료 |

