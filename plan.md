# AI Blog Automation — 프로젝트 계획서

> 작성일: 2026-03-20 / 최종 수정: 2026-03-24
> 개발자: 1인 개인 프로젝트 / 목표 사용자: 최대 100명

**상세 문서:**

- 백엔드 → [`backend/claude.md`](backend/claude.md)
- 프론트엔드 → [`frontend/claude.md`](frontend/claude.md)
- 운영/모니터링 → [`monitoring.md`](monitoring.md)

---

## 1. 프로젝트 개요

GitHub 활동(커밋, PR, README 등)을 자동 수집해 Claude / Grok / GPT / Gemini AI로 블로그 글을 개선하고 Hashnode에 발행하는 자동화 시스템.

**두 가지 흐름:**

1. GitHub 레포 → 데이터 수집 → 글 초안 → AI 개선 → Hashnode 발행
2. 직접 글 작성 → AI 개선 → Hashnode 발행

---

## 2. 기술 스택 요약

| 영역    | 기술                                                     |
|-------|--------------------------------------------------------|
| 백엔드   | Spring Boot 4.0.3, Java 25, Gradle 9.3.1               |
| 프론트   | React 18 + TypeScript + Vite 5                         |
| DB    | H2 (local) / Docker PostgreSQL (dev) / Supabase (prod) |
| 캐시    | Redis (AI 사용량, Rate Limit, JWT blacklist)              |
| 암호화   | Jasypt `PBEWithMD5AndDES` + AES-256-GCM (DB 컬럼)        |
| 인증    | GitHub OAuth2 + JWT (Access 24h / Refresh 30일)         |
| 컨테이너  | Docker Compose (backend, frontend, redis, certbot)     |
| CI/CD | GitHub Actions → OCI 서버 롤링 배포                          |
| 인프라   | OCI 단일 서버 (2CPU/16GB), 도메인: `git-ai-blog.kr`           |
| 웹서버   | Nginx — HTTPS (Let's Encrypt 자동 갱신) + reverse proxy    |

---

## 3. 인프라 / 배포

### 서버 정보

- IP: `168.107.26.27` / 도메인: `git-ai-blog.kr`
- OS: Ubuntu 20.04 / 사양: 2CPU / 16GB RAM / SSH 키: `/private`

### Docker Compose 구성

```
backend  — Spring Boot prod, JASYPT_ENCRYPTOR_PASSWORD, prepareThreshold=0
frontend — Nginx + React, 80/443, certbot_data(ro) + certbot_www 마운트
           docker-entrypoint.sh: 6시간마다 인증서 변경 감지 → nginx reload
redis    — AOF 영속성, 헬스체크 포함
certbot  — 12시간마다 certbot renew --webroot (frontend 무중단)
```

**외부 볼륨 (external: true):** `certbot_data`, `certbot_www`
**내부 볼륨:** `redis_data`

> **주의**: `docker-compose.yml` 변경 시 서버에 수동 복사 필요
> ```bash
> scp -i /path/to/key docker-compose.yml opc@168.107.26.27:/home/opc/app/docker-compose.yml
> ```

### 환경변수 정책

| 프로파일    | 방식                                                                       |
|---------|--------------------------------------------------------------------------|
| `local` | 환경변수 없음, H2, CI도 local                                                   |
| `dev`   | 해당 PC에 `JASYPT_ENCRYPTOR_PASSWORD` 직접 설정, Docker PostgreSQL + Redis 사용   |
| `prod`  | 원격 서버에 `JASYPT_ENCRYPTOR_PASSWORD` + `SPRING_PROFILES_ACTIVE=prod` 직접 설정 |

**환경변수 관리 정책:**

- `.env` 파일 사용 금지 — 각 PC/서버에서 직접 환경변수 설정
- 원격 서버에 `JASYPT_ENCRYPTOR_PASSWORD` 미설정 시 backend 기동 실패로 배포 자체가 차단됨 (fail-fast)
- dev DB 접속 정보(PostgreSQL Docker)는 기본값 공개 가능 (`localhost:5432/aiblog`, `sa/sa` 등)

### GitHub Actions CI/CD 흐름

```
sub branch push → PR 생성
  → [test.yml]      local 프로파일 테스트 (실패 시 merge 차단)
  → [auto-label.yml] feat/fix/hotfix 등 자동 태깅
→ Squash Merge to main
  → [deploy.yml]    Docker build (arm64) → Docker Hub push → OCI 롤링 배포
```

**스마트 재빌드 정책** (`check-prev-result` job):

| 이전 결과   | 이번 실행               |
|---------|---------------------|
| 실패      | 파일 변경 없어도 강제 재빌드    |
| skipped | 가장 최근 실제 결과 조회 후 판단 |
| 성공      | 파일 변경 없으면 skip      |
| 기록 없음   | 무조건 빌드              |

### HTTPS 인증서 초기 발급 (서버 초기 셋업 시)

```bash
docker compose -f /home/opc/app/docker-compose.yml stop frontend
docker run --rm \
  -v certbot_data:/etc/letsencrypt \
  -v certbot_www:/var/www/certbot \
  -p 80:80 \
  certbot/certbot certonly --standalone \
  -d git-ai-blog.kr --email jeonghun.kang.dev@gmail.com \
  --agree-tos --non-interactive
docker compose -f /home/opc/app/docker-compose.yml up -d frontend
```

### GitHub OAuth App 설정

| 환경    | Callback URL                                      |
|-------|---------------------------------------------------|
| local | `http://localhost:8080/login/oauth2/code/github`  |
| dev   | `http://localhost:8080/login/oauth2/code/github`  |
| prod  | `https://git-ai-blog.kr/login/oauth2/code/github` |

---

## 4. 개선 필요 항목

### 환경별 설정 검토

- [x] **local** — `JASYPT_ENCRYPTOR_PASSWORD` 없어도 기동 가능. `application.yml`에 기본값 추가 (
  `${JASYPT_ENCRYPTOR_PASSWORD:local-dummy-jasypt-password}`), `application-local.yml`에 `jwt.secret`, `cloudinary.*` 평문
  오버라이드
- [x] **dev** — 정책 결정: dev 로컬 실행 시 Redis 필수. `docker run -d -p 6379:6379 redis` 로 로컬 Redis 띄운 후 실행. `AiUsageLimiter`,
  `TokenUsageTracker`, `RateLimitCache` 는 이미 Redis 실패 시 graceful degradation 구현됨. `RefreshTokenService` 는 Redis 필수
  의존성이므로 무시 불가
- [x] **GitHub Actions** — CI는 `test/resources/application.yml`에서 Redis autoconfiguration 완전 제외 (
  `DataRedisAutoConfiguration` exclude), local 프로파일 사용. Redis 영향 없음 확인
- [x] **local/dev mock 로그인** — local + dev 프로파일 모두 mock 로그인 지원 필요.
  현재 `MockLoginController`가 `@Profile("local")` 전용이라 dev(`./gradlew serverRun`)에서 엔드포인트 없음.
  → `@Profile({"local", "dev"})` 로 확장, dev용 mock 계정(`dev-test-user`) 별도 사용.
  프론트 버튼은 현재 `import.meta.env.DEV`(Vite 개발 서버 여부)로만 제어 중 — 백엔드 프로파일과 무관.
  prod 비활성화: 백엔드는 `@Profile`로 자동 차단됨. 프론트는 `import.meta.env.DEV`가 prod 빌드에서 `false`로 평가되어 Vite가 dead code 제거 → 버튼이 번들에
  포함되지 않음. **prod 비활성화는 이미 정상 동작**.
- [x] **프로파일별 Hashnode 발행 권한 분리** — local/dev는 Hashnode 실제 발행 불가 (발행 버튼 비활성화 또는 명시적 오류). dev는 발행 흐름까지 테스트 가능하나 실제 전송 차단
  후 롤백. prod만 실제 발행 허용. 백엔드에서 프로파일 조건으로 제어

### 개발 편의

- [x] **Gradle 개발 실행 태스크 (`serverRun`)** — Redis + PostgreSQL Docker 자동 기동 후 dev 프로파일로 백엔드 실행.
  `JASYPT_ENCRYPTOR_PASSWORD`는 각 PC에 이미 설정되어 있으므로 별도 전달 불필요. `./gradlew serverRun` 하나로 완료
- [x] **dev PostgreSQL Docker 분리** — 현재 dev 프로파일이 Supabase(prod DB)를 직접 사용해 prod 데이터와 혼재 위험. `serverRun` 태스크에서 Redis와 함께
  로컬 PostgreSQL Docker 컨테이너도 자동 기동 (`ai-blog-postgres`). `application-dev.yml`은 `localhost:5432` 기본값으로 변경 (접속정보 공개 가능).
  prod는 계속 Supabase 사용
- [x] **build.gradle Java toolchain 자동 관리 확인** — `export JAVA_HOME=...` 하드코딩 제거. `java.toolchain.languageVersion = 25`
  설정으로 Gradle이 JDK 자동 다운로드/관리. 누구든 어느 환경에서든 `./gradlew bootRun` 하나로 local 실행 가능하도록 확인 및 문서 정비

### 인프라 / 배포

- [x] backend Docker Compose 설정 파일 경로 오류 수정
- [x] 배포 서버 GitHub 로그인 502 수정 (nginx `/login/` proxy 추가)
- [x] CI 스마트 재빌드 정책 구현 (`check-prev-result` job)
- [x] GitHub OAuth `redirect_uri` 오류 해결 (prod yml 명시)
- [x] backend `unhealthy` 해결 (Actuator 추가, SecurityConfig permitAll)
- [x] backend Dockerfile 레이어 캐시 최적화 + `.dockerignore` 추가
- [x] `Member.githubClientId/Secret` 필드 제거 (백엔드 + 프론트엔드 완료)
- [x] PostgreSQL prepared statement 충돌 해결 (`prepareThreshold: 0`)

### 기능 개선

- [x] AI 사용량 제한 — 사용자가 직접 일일 한도를 설정하고 도달 시 AI 사용 불가 처리 (Member.aiDailyLimit, AiUsageLimiter 우선순위 적용)
- [x] Hashnode 발행 시 글 하단에 AI 메타정보 자동 추가 (사용 모델, 생성일자, 개선 횟수) — PublishPostUseCase에서 appendAiMeta() 처리
- [x] 메인 페이지 우측 상단에 API 문서 링크 버튼 추가 (Layout.tsx navRight)
- [x] **이미지 생성 GPT 키 미설정 안내** — 이미지 생성은 GPT 전용인데 GPT API 키 미설정 시 이미지 생성 버튼 비활성화 + "GPT API 키를 먼저 설정해 주세요" 안내 표시. 프론트엔드
  `ImageGenButton`에서 `hasGptApiKey` 체크
- [x] **AI 모델별 일일 사용량 한도 설정** — 현재 전체 통합 한도(aiDailyLimit)만 존재. Claude/Grok/GPT/Gemini 각각 개별 한도 설정 가능하도록 변경. `Member`에
  모델별 limit 필드 추가, `AiUsageLimiter`에서 모델별 체크
- [x] **API 키 연동 검증** — 현재 API 키 저장 시 형식만 저장. 저장 시 실제 API를 최소 호출(ping/model 목록 조회 등)해서 유효한 키인지 검증 후 "연동됨" 상태 표시. 유효하지
  않으면 저장 거부 또는 경고. 백엔드 `PATCH /api/members/api-keys`에서 검증 로직 추가

- [x] **기본 프롬프트 교체** — 현재 `PromptBuilder.getInstruction(ContentType)`이 ContentType별 짧은 지시문만 사용.
  아래 SEO 최적화 블로그 작성 가이드를 기본 프롬프트로 교체:
    - 목표: 읽기 쉽고 SEO 최적화된 실무형 블로그 (대상: 백엔드 주니어~미드레벨)
    - 제목 SEO 최적화 + 후보 3개, 썸네일 문구 3개, "이 글에서 얻을 수 있는 것" 3줄 요약
    - 구조: 문제→원인→해결(코드)→Before/After 수치화→자주 하는 실수→3줄 정리
    - h2/h3, Mermaid 다이어그램, 실무 팁 3개, 운영 시나리오, 검색 키워드 5개, 체크리스트, CTA
    - 코드: 실행 가능한 수준(import, 의존성 포함), 잘못된 예시 → 개선 예시 비교, 성능 수치화 필수
    - 톤: 전문적 + 친근 / 출력: 순수 Markdown / 마지막에 사용 AI 모델 표기
    - **변경 범위**: `PromptBuilder` 기본 instruction 전면 교체. ContentType별 세부 지침은 위 규칙에 추가 병합

- [x] **게시글 태그 통일화** — 현재 게시글 태그(`Post.tags: List<String>`)와 AI 응답에서 나오는 태그가 형식 불일치 가능성 있음.
  태그 정규화 로직(소문자, 특수문자 제거, 최대 길이 등) 공통 적용 및 테스트 구성.
  **변경 범위**: `Post.updateTags()` 또는 UseCase 레벨에서 태그 정규화 추가, 단위 테스트 작성

- [x] **커스텀 프롬프트 시스템** — AI 개선 요청 시 기본 프롬프트 외 사용자 정의 프롬프트 지원:
    - 사용자당 최대 30개 커스텀 프롬프트 등록
    - 프롬프트 조회: 본인 사용 횟수 내림차순 정렬
    - 공유/비공유 설정: 커스텀 프롬프트에 `isPublic` 플래그. 기본값 비공개
    - 공개 프롬프트 탐색: 전체 사용자 중 가장 많이 선택된 순 조회 / `{user.name}님이 가장 많이 선택한 프롬프트` 조회
    - **변경 범위 (백엔드)**:
        - `Prompt` 도메인: `id, memberId, title, content, usageCount, isPublic, createdAt`
        - API: `GET/POST/PUT/DELETE /api/prompts` (본인), `GET /api/prompts/popular` (공개 인기순),
          `GET /api/prompts/members/{id}/popular`
        - `AiSuggestionRequest`에 `promptId?: Long` 추가 → `RequestAiSuggestionUseCase`에서 프롬프트 조회 후 적용
    - **변경 범위 (프론트엔드)**:
        - 프롬프트 관리 페이지 또는 ProfilePage 내 섹션
        - AI 개선 요청 모달에서 프롬프트 선택 UI (내 프롬프트 + 인기 프롬프트)

### API 문서화

- [x] springdoc-openapi (Swagger UI) 적용 — `/swagger-ui/index.html` 에서 확인 가능
    - Spring REST Docs 3.0.x는 Spring Boot 4 (Spring Framework 7.x)와 미호환 (HttpHeaders API 변경)
    - springdoc-openapi-starter-webmvc-ui 2.8.8 적용, OpenApiConfig 추가, JWT Bearer 인증 스킴 포함
- [x] 메인 페이지 우측 상단에 API 문서 링크 버튼 추가 (Layout.tsx navRight)
- [x] API 오류 응답 코드 문서화 (GlobalExceptionHandler 기반: 400, 403, 404, 422, 429, 503)
- [x] **Swagger UI 라우팅 충돌 수정** — prod Nginx에서 `/swagger-ui/`, `/v3/` 경로가 React SPA로 라우팅되어 접근 불가하던 문제.
  nginx.conf에 해당 경로를 `proxy_pass http://backend:8080` 으로 추가하여 해결
- [ ] REST Docs + Redocly / Stoplight / Slate 3종 샘플 — Spring Boot 4 호환 REST Docs 라이브러리 출시 후 구현 예정

### 게시글 뷰어 개선

> **검토 결과**: `react-markdown@9` 기본 설치 상태. `remark-gfm` 플러그인 미설치로 GFM 문법 전체 미지원.
> 영향 범위: 테이블, 체크박스(`- [ ]`), 취소선(`~~text~~`), autolinks, 각주.
> `PostDetailPage`와 `AiSuggestionPanel` 두 곳 모두 `<ReactMarkdown>` 사용 → 양쪽 동시 적용 필요.

- [x] **GFM(GitHub Flavored Markdown) 전체 지원** — `remark-gfm` 미설치로 아래 문법이 전혀 렌더링되지 않음.
  `npm install remark-gfm` 후 `PostDetailPage`와 `AiSuggestionPanel`의 `<ReactMarkdown remarkPlugins={[remarkGfm]}>` 적용.
  미지원 항목:
  - 테이블 (`| col | col |`) — 텍스트로만 출력
  - 체크박스 (`- [ ] 항목`, `- [x] 항목`) — 텍스트로만 출력
  - 취소선 (`~~취소~~`) — 텍스트로만 출력
  - autolinks (URL/이메일 자동 링크) — 텍스트로만 출력
- [ ] **Mermaid 다이어그램 렌더링** — AI가 생성하는 `graph LR` 등 Mermaid 코드블록이 코드 원문으로만 표시됨.
  구현 방식: `npm install mermaid` 후 `MermaidBlock` 컴포넌트(동적 import + `mermaid.render()`) 작성,
  `MarkdownRenderer` 공통 컴포넌트에서 ReactMarkdown `components.code` 커스터마이저로 mermaid 언어 감지 시 `MermaidBlock` 렌더링.
  `PostDetailPage`, `AiSuggestionPanel` 양쪽에서 `MarkdownRenderer`로 교체.
  **차단 요인**: npm 캐시 권한 문제(`sudo chown -R 501:20 "/Users/kang/.npm"` 실행 후 설치 가능)
- [x] **DRAFT 상태에서 발행 버튼 활성화** — 현재 발행 버튼은 `ACCEPTED` 상태에서만 표시됨. 게시글 작성 직후(DRAFT) 바로 발행 가능하도록 상태 조건 확대 또는 DRAFT → 직접 발행
  흐름 추가

### 운영 / 모니터링

- [x] 모니터링 가이드 문서 작성 (`monitoring.md`)

### 테스트

- [x] Controller 테스트 (`PostControllerTest`, `MemberControllerTest`)
- [x] Repository 통합 테스트 (4개 — H2 기반)
- [x] 도메인 단위 테스트 (`PostDomainTest`, `MemberDomainTest`, `WebhookSignatureVerifierTest`)
- [x] UseCase 단위 테스트 (`CreatePostUseCaseTest`, `ImportHashnodePostUseCaseTest`, `AiClientRouterTest`)
- [x] Spring Boot 4 테스트 환경 구성 (TestRedisConfig, OAuth2 mock)

---

## 5. 알려진 이슈 & 해결 기록 (주요)

| 문제                                        | 해결                                                                                                                                                                                         |
|-------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `prepared statement "S_1" already exists` | `prepareThreshold: 0` (prod yml)                                                                                                                                                           |
| backend `unhealthy`                       | Actuator 추가                                                                                                                                                                                |
| QEMU arm64 curl segfault                  | wget으로 HEALTHCHECK 변경                                                                                                                                                                      |
| `Post.tags` LazyInitializationException   | `List.copyOf(post.getTags())`                                                                                                                                                              |
| SyncHashnode Duplicate key                | `Collectors.toMap` mergeFunction 추가                                                                                                                                                        |
| frontend conf.d 비어있어 443 거부               | GHA `--no-cache`, 서버 docker-compose.yml scp 복사                                                                                                                                             |
| 최초 인증서 없이 nginx 즉시 종료                     | certbot standalone 발급 후 frontend 재기동 순서 필수                                                                                                                                                 |
| Hashnode INVALID_QUERY                    | GraphQL 쿼리에 변수 직접 인라인                                                                                                                                                                      |
| rollup 바이너리 누락 (반복)                       | CI에서 `rm -f package-lock.json` 후 install                                                                                                                                                   |
| `--no-daemon` 등 플래그가 태스크명으로 파싱됨           | gradlew `eval "$@"` 버그 → `$@` 로 수정 (157번 줄)                                                                                                                                                |
| Hashnode 발행 400 Bad Request               | `escapeGraphql()` 후 `objectMapper.writeValueAsString()` 이중 이스케이프 → GraphQL variables 방식으로 전환. `deletePost` 토큰 누락도 함께 수정. 발행 전 검증: 제목 6자↑, 본문 비어있음, Hashnode 토큰/publicationId null/blank 추가 |

> 전체 이슈 기록 → `backend/claude.md`, `frontend/claude.md` 참고

---

## 6. 개발 규칙

- 구현 중 새로 발견한 내용(버그, 설계 결정, 특이사항)은 `research.md`에 기록
- Jasypt 암호화는 AI가 직접 수행하지 않음 — jasypt online tool에서 수동 암호화 후 yml에 붙여넣기
- `any` / `unknown` 타입 사용 금지 (프론트엔드)
- 작업 완료 시 해당 문서의 체크박스 완료 표시
