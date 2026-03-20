# AI Blog Automation — 개발 계획서(라이브 코딩)

> 작성일: 2026-03-20
> 개발자: 1인 개인 프로젝트
> 목표 사용자: 최대 100명

---

## 1. 프로젝트 개요

GitHub 활동(커밋, PR, README 등)을 자동 수집해 Claude / Grok / ChatGPT / Gemini AI로 블로그 글을 개선하고 Hashnode에 발행하는 자동화 시스템.

**두 가지 흐름:**

1. GitHub 레포 → 데이터 수집 → 글 초안 → AI 개선 → Hashnode 발행
2. 직접 글 작성 → AI 개선 → Hashnode 발행

---

## 2. 기술 스택

| 영역        | 기술                                                                                |
|-----------|-----------------------------------------------------------------------------------|
| 백엔드       | Spring Boot 4.0.3, Java 25, Gradle 9.3.1                                          |
| 인증        | Spring Security 7.x + GitHub OAuth2 + JWT (Access 24h / Refresh 30일 HttpOnly 쿠키)  |
| DB        | H2 (local) / PostgreSQL Supabase (dev/prod) + JPA + Hibernate 7.1                 |
| 외부 API    | WebClient (WebFlux) — Claude, Grok, ChatGPT, Gemini, GitHub, Hashnode, Cloudinary |
| 캐시        | Redis — AI 사용량 카운터, Rate Limit 캐시, JWT Refresh Token blacklist                    |
| 암호화       | Jasypt (`PBEWithHMACSHA512AndAES_256`) — prod 전용, `application-prod.yml` 내 암호화 값  |
| DB 컬럼 암호화 | `@Convert` + `AttributeConverter` AES-256-GCM — Member 테이블 민감 필드                  |
| 프론트       | React 18 + TypeScript + Vite 5                                                    |
| 상태관리      | Zustand + immer                                                                   |
| HTTP      | Axios (JWT interceptor 자동 주입)                                                     |
| 스타일       | CSS Modules + CSS 변수 (다크/라이트 모드)                                                  |
| 컨테이너      | Docker + Docker Compose                                                           |
| CI/CD     | GitHub Actions — 롤링 배포 (sub → main PR merge 시 자동 배포)                              |
| 인프라       | OCI 단일 서버 (2CPU / 16GB RAM), IP: `168.107.26.27`, 도메인: `git-ai-blog.kr`           |
| 웹서버       | Nginx — React 정적 파일 서빙 + `/api` reverse proxy + HTTPS (Let's Encrypt 자동 갱신)       |

### 프로파일별 환경변수 정책

| 프로파일    | .env 사용 | Jasypt 암호화 | 비고                                                                               |
|---------|---------|------------|----------------------------------------------------------------------------------|
| `local` | 없음      | 없음         | 중요 암호값 없이 실행 가능, 테스트 포함. GitHub Actions CI도 local로 실행                            |
| `dev`   | 없음      | **필수**     | `application-dev.yml` 에 Jasypt 암호화 값 직접 포함. `JASYPT_ENCRYPTOR_PASSWORD` 환경변수 필요  |
| `prod`  | 없음      | **필수**     | `application-prod.yml` 에 Jasypt 암호화 값 직접 포함. `JASYPT_ENCRYPTOR_PASSWORD` 환경변수 필요 |

> `.env` 파일은 완전히 제거. 모든 민감값은 Jasypt로 암호화 후 yml에 직접 포함.
> `.env.backup`은 관리자(로컬)에서만 보관, 절대 커밋 금지.
> Hashnode 설정은 yml 불필요 — 마이페이지에서 사용자가 직접 등록.

---

## 3. 아키텍처

### 계층 구조

```
Controller (HTTP 파라미터 읽기 + UseCase 호출)
  → UseCase (단일 비즈니스 시나리오, @Transactional 선언)
    → Repository 인터페이스 (Domain 계층)
      ← JpaRepository 구현체 (Infra 계층)
```

### 패키지 구조

최상위 패키지: `github.jhkoder.aiblog`

```
github.jhkoder.aiblog/
├── common/               GlobalExceptionHandler, ApiResponse, DbMigrationRunner
├── common/exception/     NotFoundException, InvalidStateException, BusinessRuleException
│                         ExternalApiException, RateLimitException
├── config/               SecurityConfig, WebClientConfig, WebMvcConfig, JasyptConfig
├── security/             JwtProvider, JwtAuthenticationFilter, RefreshTokenService
│                         CustomOAuth2UserService, OAuth2SuccessHandler
├── infra/ai/             AiClient(interface), AiClientRouter, AiUsageLimiter (Redis)
│                         ClaudeClient, GrokClient, GptClient (추가 예정), GeminiClient (추가 예정)
│                         RateLimitCache (Redis), TokenUsageTracker, ImageUsageLimiter (Redis)
│                         prompt/PromptBuilder
├── infra/github/         GitHubClient, WebhookSignatureVerifier
├── infra/hashnode/       HashnodeClient, HashnodeGraphqlBuilder
├── infra/image/          CloudinaryClient, ImageGenerationService (GPT 전용)
├── post/                 Post(Entity), PostStatus, ContentType, PostRepository
│                         PostController, 8개 UseCase
├── suggestion/           AiSuggestion(Entity), AiSuggestionRepository
│                         AiSuggestionController, 5개 UseCase
├── member/               Member(Entity), MemberRepository
│                         MemberController, 4개 UseCase
└── repo/                 Repo(Entity), RepoRepository, CollectType
                          RepoController, GitHubWebhookController, 5개 UseCase
```

### 프론트엔드 구조

```
src/
├── api/          axiosInstance, postApi, suggestionApi, memberApi, repoApi
├── store/        authStore, postStore, suggestionStore (Zustand)
├── types/        member.ts, post.ts, suggestion.ts, repo.ts
├── hooks/        useDraft.ts, useTheme.ts
├── components/   Layout, PostCard, AiSuggestionPanel, StatusBadge
│                 ConfirmModal, TagInput, ImageGenButton
├── pages/        LoginPage, PostListPage, PostDetailPage
│                 PostCreatePage, PostEditPage, ProfilePage, RepoListPage
└── router/       AppRouter.tsx
```

---

## 4. 핵심 도메인 정책

### Post 상태 머신

```
DRAFT → AI_SUGGESTED → ACCEPTED → PUBLISHED
           ↓ (거절)
          DRAFT
```

- 상태 전이는 반드시 `Post` 도메인 메서드로만: `markAiSuggested()`, `accept()`, `markPublished()`, `revertFromAiSuggested()`
- PUBLISHED 상태에서도 재발행·AI 개선 가능
- 모든 상태에서 삭제 가능 (삭제 막는 상태 체크 없음)

### 삭제 정책

- Hashnode 발행 글 있으면 Hashnode도 함께 삭제
- Hashnode 삭제 실패해도 DB 삭제는 반드시 진행
- 관련 AI 제안 목록도 함께 삭제
- 삭제 버튼은 상세 페이지에만 (목록 페이지에 없음)

### Hashnode 발행 분기 정책

- `hashnodeId` 없음 → `publishPost` (신규)
- `hashnodeId` 있음 → `updatePost` (수정)
- 항상 `publishPost` 호출하면 중복 생성됨

### AI 사용량 제한 (`AiUsageLimiter`)

- **Redis 기반** (In-memory 제거)
- local: 5회/일, dev/prod: 20회/일
- Redis TTL로 자정 자동 초기화
- 한도 초과 시 즉시 429 반환, 재시도 없음

### AI 클라이언트 라우팅 (`AiClientRouter`)

| ContentType | 기본 모델             |
|-------------|-------------------|
| ALGORITHM   | Grok 3            |
| CODE_REVIEW | Claude Sonnet 4.6 |
| 나머지         | Claude Sonnet 4.6 |

**선택 가능 모델 목록:**

| 모델 ID               | 제공사     | 구분      | 비고           |
|---------------------|---------|---------|--------------|
| `claude-sonnet-4-6` | Claude  | 텍스트     | 기본값, 권장      |
| `claude-opus-4-5`   | Claude  | 텍스트     | 선택 가능, 비권장   |
| `grok-3`            | Grok    | 텍스트     | ALGORITHM 기본 |
| `gpt-4o-mini`       | ChatGPT | 텍스트     | 가성비 추천       |
| `gpt-4o`            | ChatGPT | 텍스트·이미지 | 고성능          |
| `gemini-2.0-flash`  | Gemini  | 텍스트     | 추가 예정        |

- `requestedModel` 명시 시 강제 라우팅
- **Sonnet이 기본 모델**. Opus는 선택 가능하나 권장하지 않음
- **이미지 생성은 GPT 모델 (`gpt-4o`, `gpt-4o-mini`) 선택 시에만 활성화**

### Rate Limit 캐시 (`RateLimitCache`)

- **Redis 기반** (In-memory 제거)
- AI API 응답 헤더에서 실시간 파싱
- Claude: `anthropic-ratelimit-tokens-limit/remaining`, `anthropic-ratelimit-requests-limit/remaining`
- Grok: `x-ratelimit-limit-tokens/remaining`, `x-ratelimit-limit-requests/remaining`
- GPT: `x-ratelimit-limit-tokens/remaining`, `x-ratelimit-remaining-tokens/requests`
- 키: `"memberId:provider"`, 값: `RateLimitInfo(tokenLimit, tokenRemaining, requestLimit, requestRemaining)`
- 미조회 시 -1 반환 (프론트에서 "미조회" 안내 표시)

### 이미지 사용 제한 (`ImageUsageLimiter`)

- **GPT 모델 전용** (Claude Opus 이미지 사용 제거)
- 일별: 10장/일, 게시글당: 5장
- Redis TTL로 자동 초기화

### 토큰 누적 추적 (`TokenUsageTracker`)

- 모델별 입력/출력 토큰 누적
- Redis 기반, 월 1일 자동 초기화 (TTL)

### JWT 인증 정책

- **Access Token**: 24시간, 응답 바디로 전달
- **Refresh Token**: 30일, HttpOnly 쿠키로 전달
- **Redis blacklist**: 로그아웃 시 Refresh Token을 blacklist에 등록 (TTL = 잔여 유효시간)
- **Rotation**: Refresh Token 재발급 시 기존 토큰 무효화

### GitHub Webhook 보안 정책

- `POST /api/webhook/github`는 `X-Hub-Signature-256` 헤더 필수 검증
- `WebhookSignatureVerifier`에서 HMAC-SHA256으로 서명 비교
- 서명 불일치 시 즉시 403 반환
- GitHub webhook IP 대역 whitelist 추가 적용

### DB 컬럼 암호화 정책

- `Member` 테이블의 민감 필드(`claudeApiKey`, `grokApiKey`, `gptApiKey`, `githubToken`, `hashnodeToken` 등)는 DB에 평문 저장 금지
- `@Convert` + `AttributeConverter` 구현으로 AES-256-GCM DB 레벨 암호화 적용
- 암호화 키는 환경변수로 관리 (코드에 하드코딩 금지)

### AI 제안 소유권 정책

- 수락/거절 시 `suggestion.postId == postId` 검증 필수
- 불일치 시 404 반환

### Repository 삭제 메서드 정책

- `JpaRepository.deleteById()` 상속 노출 금지
- 모든 Repository에서 `deletePost(Long id)`, `deleteRepo(Long id)` 형태로 별도 선언

### 보안 — 민감 필드 응답 금지

절대 Response DTO에 포함 금지:
`githubToken`, `hashnodeToken`, `claudeApiKey`, `grokApiKey`, `githubClientId`, `githubClientSecret`,
`hashnodePublicationId`
→ boolean 여부만 응답 (`hasHashnodeConnection`, `hasClaudeApiKey` 등)

### API 요청 유효성 검사 정책

- 모든 API 요청마다 `@Valid` + Bean Validation 적용
- 에러 메시지는 명확하고 구체적으로 (예: "제목은 1자 이상 200자 이하여야 합니다")
- GlobalExceptionHandler에서 ValidationException 일관 처리

---

## 5. 구현된 기능 목록

### Post

| 기능           | 엔드포인트                           | 비고                         |
|--------------|---------------------------------|----------------------------|
| 생성           | `POST /api/posts`               |                            |
| 목록           | `GET /api/posts`                | 페이징, 본인 글만                 |
| 상세           | `GET /api/posts/{id}`           | 조회수 자동 증가                  |
| 수정           | `PUT /api/posts/{id}`           | 제목/내용/ContentType          |
| 삭제           | `DELETE /api/posts/{id}`        | Hashnode 연동 삭제 포함          |
| 발행           | `POST /api/posts/{id}/publish`  | hashnodeId 유무로 신규/수정 분기    |
| Hashnode 동기화 | `POST /api/posts/sync-hashnode` | added/updated/deleted 반환   |
| AI 사용량       | `GET /api/posts/ai-usage`       | 호출 횟수 + Rate Limit + 토큰 누적 |
| PDF 내보내기     | 프론트 `window.print()`            | 99MB 초과 시 차단               |

### AI Suggestion

| 기능    | 엔드포인트                                           | 비고                                  |
|-------|-------------------------------------------------|-------------------------------------|
| AI 요청 | `POST /api/ai-suggestions/{postId}`             | model/extraPrompt/tempContent 지정 가능 |
| 최신 제안 | `GET /api/ai-suggestions/{postId}/latest`       |                                     |
| 히스토리  | `GET /api/ai-suggestions/{postId}/history`      | createdAt DESC                      |
| 수락    | `POST /api/ai-suggestions/{postId}/{id}/accept` | Optimistic Update                   |
| 거절    | `POST /api/ai-suggestions/{postId}/{id}/reject` | Post → DRAFT 복원                     |

### Member

| 기능          | 엔드포인트                                  | 비고                              |
|-------------|----------------------------------------|---------------------------------|
| 프로필 조회      | `GET /api/members/me`                  | 민감 필드 제외                        |
| Hashnode 연동 | `POST /api/members/hashnode-connect`   | 연동 시 게시글 자동 import              |
| Hashnode 해제 | `DELETE /api/members/hashnode-connect` |                                 |
| API 키 설정    | `PATCH /api/members/api-keys`          | Claude/Grok/GPT/Gemini/GitHub 키 |

### Repo

| 기능 | 엔드포인트                          | 비고                        |
|----|--------------------------------|---------------------------|
| 목록 | `GET /api/repos`               |                           |
| 추가 | `POST /api/repos`              | owner/repo + 수집 타입        |
| 삭제 | `DELETE /api/repos/{id}`       |                           |
| 수집 | `POST /api/repos/{id}/collect` | GitHub 데이터 → 게시글 초안       |
| 웹훅 | `POST /api/webhook/github`     | X-Hub-Signature-256 검증 필수 |

### ContentType별 AI 프롬프트 전략

| ContentType | 톤       |                     필수 요소 |
|-------------|---------|--------------------------:|
| ALGORITHM   | 엄격·교육적  | 시간/공간복잡도 표, 코드 블록, 단계별 설명 |
| CODING      | 실무적·간결  |   에러 재현 → 해결 → 리팩토링 전후 비교 |
| CS          | 학술적·깊이  |          개념 → 예시 → 트레이드오프 |
| TEST        | 꼼꼼·방어적  |    Given-When-Then 표, 경계값 |
| AUTOMATION  | 실용·튜토리얼 |            단계별 코드 + 실행 결과 |
| DOCUMENT    | 명확·구조화  |              목차 → 본문 → 요약 |
| CODE_REVIEW | 비판적·건설적 |      Good / Bad / 개선안 3단계 |
| ETC         | 유연      |                     자유 형식 |

---

## 6. 알려진 이슈 & 해결 기록

| 문제                                | 원인                                                               | 해결                                                                   |
|-----------------------------------|------------------------------------------------------------------|----------------------------------------------------------------------|
| Hashnode API INVALID_QUERY        | Stellate CDN이 variables 캐시 거부                                    | 쿼리 본문에 값 직접 인라인                                                      |
| 재발행 시 Hashnode 글 중복               | 항상 publishPost 호출                                                | hashnodeId 유무로 publish/update 분기                                     |
| AI 제안 거절 후 AI_SUGGESTED 상태 유지     | reject 시 Post 상태 미복원                                             | `revertFromAiSuggested()` 호출                                         |
| 타인의 AI 제안 수락/거절 가능                | suggestion.postId 소유권 검증 누락                                      | `filter(s -> s.getPostId().equals(postId))`                          |
| README 수집 시 런타임 오류                | raw Accept 헤더로 String 응답을 Map으로 역직렬화                             | `bodyToMono(String.class)`                                           |
| Cloudinary 서명 오류                  | HMAC-SHA256 사용                                                   | SHA-1로 수정                                                            |
| 다크모드 텍스트 안 보임                     | 하드코딩 색상 (`#111827` 등)                                            | CSS 변수(`var(--text)`) 교체                                             |
| Gemini 이미지 생성 실패                  | 무료 티어 할당량 초과 (429)                                               | Gemini 이미지 계획 취소, GPT 전환 예정                                          |
| QEMU arm64 빌드 illegal instruction | `node:20-alpine` musl libc + QEMU 비호환                            | `node:20-slim` (debian)으로 교체                                         |
| rollup 바이너리 모듈 누락                 | npm optional dependency 공식 버그 — `npm ci`가 lock 기반으로 깨진 상태 그대로 재현 | `npm install`로 교체해 dependency 재resolve. `package-lock.json` 삭제 후 재생성 |
| bootJar QEMU 빌드 4분 이상 멈춤          | QEMU arm64 크로스컴파일 시 JVM 에뮬레이션 오버헤드                               | 경로 기반 조건부 빌드로 불필요한 빌드 스킵 (변경된 쪽만 빌드)                               |

---

## 7. 개선 필요 항목

### 보안 (치명적 — 우선 처리)

- [x] **GitHub Webhook 서명 검증** — `X-Hub-Signature-256` 미검증 시 payload 위조로 DB 오염 가능. `WebhookSignatureVerifier` 추가 +
  GitHub IP whitelist
- [x] **DB 컬럼 암호화** — `Member` 테이블 민감 필드 현재 평문 저장 위험. `@Convert` + `AttributeConverter` AES-256-GCM 적용
- [x] **JWT Refresh Token** — 현재 미구현. Refresh Token 30일 (HttpOnly 쿠키) + Redis blacklist + Rotation
- [x] **Jasypt 알고리즘 업그레이드** — 현재 `PBEWithMD5AndDES` → `PBEWithHMACSHA512AndAES_256` (prod 전용)
- [x] **CORS** — `FRONTEND_URL` 환경변수 기반 (wildcard 금지)

### 인프라

- [x] **Redis 도입** — `AiUsageLimiter`, `RateLimitCache`, `ImageUsageLimiter`, `TokenUsageTracker`, JWT blacklist
  In-memory → Redis 전환
- [x] **OCI 서버 배포** — 단일 서버 (2CPU/16GB), 백엔드+프론트 동일 서버 (`168.107.26.27`)
- [x] **HTTPS 설정** — Nginx + Let's Encrypt 자동 갱신, 도메인: `git-ai-blog.kr`
- [x] **환경변수 관리** — dev/prod 모두 `.env` 제거, `application-dev.yml` / `application-prod.yml` + Jasypt 암호화 값 직접 포함

### CI/CD

- [x] **GitHub Actions 롤링 배포** — sub branch → main PR merge 시 자동 배포
- [x] **PR 테스트 검증** — local 프로파일로 실행, commit마다 테스트 자동 실행, 성공해야 merge 허용
- [x] **Squash and Merge** — PR merge 방식 squash and merge 적용
- [x] **PR 자동 라벨링** — `[bug]`, `[hotfix]`, `[release]`, `[feature]`, `[security]` 태그 자동 부착
- [x] **Docker 배포** — Dockerfile (백엔드/프론트) + Docker Compose 구성, `JASYPT_ENCRYPTOR_PASSWORD`만 런타임 주입 (`.env` 제거)
- [x] **GitHub Actions 캐시** — Gradle 의존성, npm 패키지 캐시 추가로 빌드 시간 단축
- [x] **백엔드/프론트 이미지 병렬 빌드** — deploy.yml에서 backend/frontend Docker 빌드를 별도 job으로 분리해 동시 실행
- [x] **프론트엔드 빌드 방식 변경** — rollup npm optional dep 버그 해결: `npm ci` → `npm install` 교체, Actions에서 빌드 후 `dist`만 nginx Docker 이미지에 COPY
- [ ] **경로 기반 조건부 빌드** — `backend/**` 변경 시에만 backend job 실행, `frontend/**` 변경 시에만 frontend job 실행. 변경 없는 쪽은 빌드 스킵해서 불필요한 QEMU 빌드 제거

### 기능

- [x] **ChatGPT 모델 추가** — `GptClient` 구현, `gpt-4o-mini` (가성비) + `gpt-4o` (고성능)
- [x] **Gemini 모델 추가** — `GeminiClient` 구현, `gemini-2.0-flash`
- [x] **이미지 생성 (GPT 전용)** — GPT 모델 선택 시에만 이미지 생성 활성화, Cloudinary 업로드 연동
- [x] **Resilience4j 적용** — Retry, CircuitBreaker (Claude/Grok/GPT/Hashnode API 보호)

### 코드 품질

- [x] **패키지명 변경** — `com.example.aiblog` → `github.jhkoder.aiblog`
- [x] **API 유효성 검사** — 모든 Request DTO에 `@Valid` + Bean Validation, 에러 메시지 명확화
- [x] **테스트 코드** — `@DataJpaTest` + `@WebMvcTest` 80% 커버리지 목표. AI 클라이언트는 WireMock으로 contract test. GitHub webhook은
  signature 검증 테스트 필수

---

## 8. 배포 계획 (OCI 단일 서버)

### 서버 정보

- IP: `168.107.26.27`
- 도메인: `git-ai-blog.kr`
- 사양: 2CPU / 16GB RAM
- SSH 키: `/private` 경로

### 서버 구성

```
OCI (2CPU / 16GB RAM) — git-ai-blog.kr
└── Nginx (80 → 443 redirect, HTTPS)
    ├── /         → React 정적 빌드 서빙
    └── /api      → localhost:8080 (Spring Boot)
Spring Boot (8080, 내부 전용)
Redis (6379, 내부 전용)
```

### Docker Compose 구성

```
services:
  backend   — Spring Boot JAR, application-prod.yml (Jasypt 암호화 값 포함), JASYPT_ENCRYPTOR_PASSWORD 환경변수 주입
  frontend  — Nginx + React 빌드 결과물
  redis     — 사용량 카운터, Rate Limit 캐시, JWT blacklist
```

- `.env` 파일 사용 안 함 (dev/prod 모두)
- 모든 민감값은 Jasypt로 암호화 후 `application-dev.yml` / `application-prod.yml`에 직접 포함
- 서버에서 `JASYPT_ENCRYPTOR_PASSWORD` 환경변수만 별도 관리

### GitHub Actions CI/CD 흐름

```
sub branch push
  → PR 생성
    → [테스트 workflow] local 프로파일로 테스트 자동 실행 (실패 시 merge 차단)
    → [라벨링 workflow] PR 내용 감지 → [bug]/[hotfix]/[release]/[feature]/[security] 태그 부착
  → Squash and Merge to main
    → [배포 workflow] Docker build → OCI 서버 롤링 배포 (SSH: /private 키 사용)
```

### 롤링 배포 흐름 (UML)

```
main push
  │
  ├─[build-backend job]──────────────────────────────────────┐
  │   actions/checkout                                        │
  │   Cache Gradle (~/.gradle)                               │
  │   QEMU + Buildx 설정                                      │
  │   Docker Hub 로그인                                        │
  │   docker buildx build --platform linux/arm64             │
  │     ./backend → jhkoders/aiblog-backend:latest  ──push──►│
  │                                                           │
  ├─[build-frontend job]─────────────────────────────────────┤
  │   actions/checkout                                        │
  │   Node 20 setup                                           │
  │   npm install && npm run build  (x64 러너에서 실행)         │
  │   QEMU + Buildx 설정                                      │
  │   Docker Hub 로그인                                        │
  │   docker buildx build --platform linux/arm64             │
  │     ./frontend (dist 포함) → jhkoders/aiblog-frontend ──►│
  │                                                           │
  └─[deploy job]  needs: [build-backend, build-frontend]◄────┘
      appleboy/ssh-action → opc@168.107.26.27
        cd /home/opc/app
        docker compose pull backend frontend   ← Docker Hub에서 latest pull
        docker compose up -d --no-deps backend frontend
          ├─ backend  컨테이너 교체 (Spring Boot prod 프로파일)
          └─ frontend 컨테이너 교체 (nginx + React dist)
        sleep 15  (헬스체크 대기)
        docker compose ps
        docker image prune -f
```

### 환경변수 관리

| 프로파일    | 방식                                                                                       |
|---------|------------------------------------------------------------------------------------------|
| `local` | 환경변수 없음, 기본값으로 실행 (H2, mock 키). GitHub Actions CI도 local로 실행                             |
| `dev`   | `application-dev.yml` (Jasypt 암호화 값 포함) + `JASYPT_ENCRYPTOR_PASSWORD` 환경변수               |
| `prod`  | `application-prod.yml` (Jasypt 암호화 값 포함) + `JASYPT_ENCRYPTOR_PASSWORD` 환경변수 (서버에서 직접 관리) |

### HTTPS

- Let's Encrypt certbot 설치 (`git-ai-blog.kr`)
- systemd 타이머로 자동 갱신 (Ubuntu 기본 포함)
- Nginx post-renew hook으로 자동 reload
- Nginx 80 → 443 redirect 설정

---

## 9. 개발 환경 실행 방법

```bash
# 백엔드 — local 프로파일 (환경변수 불필요, H2 사용)
export JAVA_HOME=/Users/kang/Library/Java/JavaVirtualMachines/openjdk-25/Contents/Home
cd backend && ./gradlew bootRun

# 백엔드 — dev 프로파일 (JASYPT_ENCRYPTOR_PASSWORD 필요, Supabase 연결)
export JASYPT_ENCRYPTOR_PASSWORD=your_password
export SPRING_PROFILES_ACTIVE=dev
cd backend && ./gradlew bootRun

# 프론트엔드
cd frontend && npm run dev

# 포트 충돌 시
lsof -ti :8080 | xargs kill -9
lsof -ti :5173 | xargs kill -9
```

> **local 프로파일**: 중요 암호값(API 키, DB 비밀번호) 없이 H2 in-memory DB로 실행 가능. 테스트도 local 기준으로 동작.
> **dev 프로파일**: `JASYPT_ENCRYPTOR_PASSWORD` 환경변수 필요. `application-dev.yml`에 암호화 값 포함. `.env` 파일 불필요.
> **prod**: 서버에서 `JASYPT_ENCRYPTOR_PASSWORD` 환경변수만 관리. `application-prod.yml`에 암호화 값 포함. `.env` 파일 불필요.
> **cli** : 구현 중 새로 발견한 내용(버그, 설계 결정, 특이사항 등)은 반드시 research.md에 기록해야 함