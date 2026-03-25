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
│                         RedisConfig, AesGcmEncryptionConverter
├── security/             JwtProvider, JwtAuthenticationFilter, RefreshTokenService
│                         CustomOAuth2UserService, OAuth2SuccessHandler, AuthController
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
├── suggestion/           AiSuggestion, AiSuggestionRepository
│                         AiSuggestionController, 5개 UseCase
├── member/               Member, MemberRepository
│                         MemberController, 4개 UseCase
└── repo/                 Repo, RepoCollectHistory, RepoRepository, CollectType
                          RepoController, GitHubWebhookController, 5개 UseCase
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

| 기능    | 엔드포인트                                           |
|-------|-------------------------------------------------|
| AI 요청 | `POST /api/ai-suggestions/{postId}`             |
| 최신 제안 | `GET /api/ai-suggestions/{postId}/latest`       |
| 히스토리  | `GET /api/ai-suggestions/{postId}/history`      |
| 수락    | `POST /api/ai-suggestions/{postId}/{id}/accept` |
| 거절    | `POST /api/ai-suggestions/{postId}/{id}/reject` |

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

테스트 설정: `test/resources/application.yml` — OAuth2 mock, Jasypt bean, Redis 제외

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
