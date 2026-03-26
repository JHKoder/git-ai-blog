# AI Blog Automation — 프로젝트 계획서

> 작성일: 2026-03-20 / 최종 수정: 2026-03-26 (AI 태그 자동 추출, AI 평가 GFM 렌더링, 반응형 레이아웃, 2컬럼 레이아웃, 본문 폭 유지, AI 개선 중복 제거)

## 문서 구조

| 파일                                         | 역할                               |
|--------------------------------------------|----------------------------------|
| [`backend/claude.md`](backend/claude.md)   | 백엔드 코드 레벨 상세 (API, 도메인, 이슈 기록)   |
| [`frontend/CLAUDE.md`](frontend/CLAUDE.md) | 프론트엔드 코드 레벨 상세 (컴포넌트, 타입, 이슈 기록) |
| [`sqlviz.md`](sqlviz.md)                   | SQLViz 설계 / UX / AI 연동 가이드       |
| [`infra.md`](infra.md)                     | 배포 / 인프라 셋업 절차                   |
| [`monitoring.md`](monitoring.md)           | 운영 / 장애 대응                       |
| [`research.md`](research.md)               | 리서치 내용                           |

---

## Mermaid 다이어그램 사용 기준

> **역할**: 복잡한 시스템 장애 흐름을 가독성 높고 직관적인 다이어그램으로 재구성한다.
> **핵심**: 내용에 따라 타입을 구분해서 사용한다. `flowchart TD` 단독 나열은 금지.

---

### 타입 선택 기준

| 상황                                                  | 사용 타입                       |
|-----------------------------------------------------|-----------------------------|
| 트랜잭션 간 상호작용, 시간 순서, Lock 획득/대기 상태 전이, 여러 주체 간 통신 흐름 | `sequenceDiagram`           |
| 단순 인과관계, 한 방향 원인→결과 체인, 프로세스 단계 나열                  | `flowchart LR`              |
| 노드 6개 이상이고 단계 그룹화가 의미 있을 때                          | `flowchart LR` + `subgraph` |

---

### 1. sequenceDiagram — 주체 간 상호작용

트랜잭션이 DB와 어떤 순서로 Lock을 주고받는지처럼 **시간축 + 교차 흐름**이 핵심일 때 사용.
`flowchart`로는 "T1이 T2의 Lock을 기다린다"는 교차 관계를 표현하기 어렵다.

```mermaid
sequenceDiagram
    participant T1 as 트랜잭션1
    participant DB as Database
    participant T2 as 트랜잭션2

    T1->>DB: 1. orders 테이블 Lock
    T2->>DB: 2. coupons 테이블 Lock
    T1-->>DB: 3. coupons 대기 (T2가 보유)
    T2-->>DB: 4. orders 대기 (T1이 보유)
    Note over T1,T2: 💥 Deadlock!
```

---

### 2. flowchart LR — 단순 인과 체인

"A가 일어나서 B가 되고 C가 된다"처럼 **한 방향 흐름**이 전부일 때 사용.
5단계 이하 단순 체인에는 `subgraph` 없이도 충분하다.

```mermaid
flowchart LR
    A[TPS 급증] --> B[Deadlock] --> C[Lock 대기] --> D[Pool 고갈] --> E[503 에러]
```

단계가 복잡하거나(6개 이상) 구간별 의미 구분이 필요한 경우에만 `subgraph` 추가:

```mermaid
flowchart LR
    subgraph 트리거
        A[TPS 50→300]
    end
    subgraph 락 충돌
        B[동시 결제] --> C[Deadlock 200건/h]
    end
    subgraph 리소스 고갈
        D[Lock 대기 50초] --> E[Connection 점유] --> F[HikariCP 포화]
    end
    subgraph 결과
        G[503 전체 실패]
    end
    트리거 --> 락 충돌 --> 리소스 고갈 --> 결과
```

---

### 공통 규칙

- 다이어그램 위에 **한 줄 핵심 요약** 항상 선행
- 노드 텍스트는 명사형 키워드 위주, 한 박스에 한 개 의미
- "읽는 다이어그램"이 아닌 "보는 다이어그램" — 주니어가 5초 안에 이해 가능한 수준

---

## 1. 프로젝트 개요

GitHub 활동(커밋, PR, README 등)을 자동 수집해 Claude / Grok / GPT / Gemini AI로 블로그 글을 개선하고 Hashnode에 발행하는 자동화 시스템.

두 가지 흐름: **GitHub 레포 → 수집 → 초안 → AI 개선 → 발행** / **직접 작성 → AI 개선 → 발행**

---

## 2. 기술 스택

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

---

## 3. 구현 현황

### 환경별 설정

- [x] local — H2, `JASYPT_ENCRYPTOR_PASSWORD` 없이 기동
- [x] dev — `./gradlew serverRun` (Redis + PostgreSQL Docker 자동 기동)
- [x] GitHub Actions — local 프로파일, Redis 제외
- [x] mock 로그인 — `@Profile({"local","dev"})`, prod 빌드에서 자동 제거
- [x] Hashnode 발행 — prod 프로파일에서만 실제 발행 허용

### 인프라 / 배포

> 상세 → [`infra.md`](infra.md)

- [x] CI 스마트 재빌드 정책 (`check-prev-result` job)
- [x] backend Dockerfile 레이어 캐시 최적화
- [x] PostgreSQL prepared statement 충돌 해결 (`prepareThreshold: 0`)

### 기능

- [x] AI 사용량 제한 — 전체 + 모델별 일일 한도, Redis 기반, 초과 시 429
- [x] AI 모델 선택 — Claude/Grok/GPT/Gemini 수동 또는 ContentType 자동 라우팅
- [x] 커스텀 프롬프트 — 사용자당 최대 30개, 공개/비공개, 인기순 탐색 (제목 100자 / 내용 2000자 제한)
- [x] 기본 프롬프트 교체 — SEO 최적화 가이드 (`PromptBuilder`)
- [x] API 키 연동 검증 — 저장 시 실제 API 호출로 유효성 확인
- [x] 이미지 생성 — AI 개선 플로우에서 분리, `ImageGenButton` 수동 전용
- [x] GFM + Mermaid 렌더링 (`MarkdownRenderer` + `MermaidBlock`)
- [x] Swagger UI (`/swagger-ui/index.html`)
- [x] Claude `max_tokens` 4096 → 16000 상향 — 긴 글 중간 잘림 방지
- [x] `Page<T>` 직렬화 경고 제거 — `PostPageResponse` DTO 도입
- [x] AI 요청 연결 끊김 해결 — `@Async` + 202 Accepted + 3s 폴링 (방안 A)
- [x] AI SSE 스트리밍 — 토큰 단위 실시간 렌더링, `POST /api/ai-suggestions/{postId}/stream` (방안 B)
- [x] `durationMs` 컬럼 — AI 응답 소요 시간 저장, 모델별 평균 조회
- [x] nginx SSE 지원 — `/api/ai-suggestions/*/stream` 경로에 `proxy_buffering off`
- [x] SSE 버그 수정 — `이미 AI 제안 상태` 오류, `Access Denied` + response committed 오류, 에러 이벤트 미처리
- [x] nginx `Authorization` 헤더 누락 수정 — SSE location에 `proxy_set_header` 지정 시 기본 헤더 상속 끊김 → JWT 인증 실패 → `Access Denied`
  prod 버그 해결
- [x] `SecurityConfig.dispatcherTypeMatchers(ASYNC).permitAll()` — Tomcat async dispatch 시 Security Filter Chain 전체 인가
  체크 제외 → `Access Denied` 근본 원인 해결
- [x] AI 클라이언트 `ObjectMapper` `com.fasterxml` → `tools.jackson` 교체 — Spring Boot 4 Jackson 3.x 환경에서 파싱 실패로 토큰 0개 문제 해결
- [x] SSE `done` race condition 해결 — `concatWith(done)` 방식에서 `Flux.defer(saveResult → __done__)` 방식으로 변경, DB 저장 완료 후
  프론트에 done 전달
- [x] `bodyToFlux(String.class)` `data:` 접두사 자동 제거 대응 — Claude/Grok/GPT/Gemini 파싱 수정
- [x] 스트리밍 중 실시간 텍스트 `<pre>` 렌더링 — 불완전 마크다운 파싱 오류 방지, 완료 후 `MarkdownRenderer` 전환
- [x] AI SSE 예상 완료 시간 표시 — `estimated` 첫 이벤트로 전달, 프론트 카운트다운 UI
- [x] `@Async` SecurityContext 전파 — `DelegatingSecurityContextAsyncTaskExecutor` 래핑,
  `WebMvcConfigurer.configureAsyncSupport` 등록
- [x] AI 개선 시 제목 생성 — `suggestedTitle` 분리, "~의(는) ~다" 형식, 후보 나열 금지, `accept` 시 `Post.title` 함께 교체
- [x] `PromptBuilder` 평가 기준 통합 — 6가지 평가 기준(Structure/Practicality/Depth/Evidence/Readability/Originality) + 필수 피드백 5개를
  리뷰 단계로 적용 (content 30자 미만 예외)
- [x] `PromptBuilder` 4단계 파이프라인 통합 — 설계→작성→리뷰→압축을 단일 프롬프트로 통합, 압축 시 코드블록·수치 삭제 금지 명시
- [x] AI 평가 패널 — 발행 전 우측 `fixed` 고정 패널, 모델 선택 → 6가지 기준 평가 → 추가 요청사항 자동 생성 → AI 개선 재요청 (Hashnode 전달 X)
- [x] AI 평가/제안 패널 본문 하단 배치 + 앵커 링크 — fixed 사이드바 제거, 본문 하단 `#ai-panel`에 풀 너비 배치, 상단 "▼ AI 패널" 앵커 버튼 추가
- [x] "Hashnode에서 보기" 링크 상단 이동 — PDF 내보내기 오른쪽, `PUBLISHED` + `hashnodeUrl` 조건부 표시
- [x] Hashnode 태그 장기 대응 — Member `hashnodeTags` TEXT 컬럼, ProfilePage 태그명↔ID 매핑 UI, 발행 시 로컬 태그명 → Hashnode tag ID 자동 변환
- [x] 자가성장 프롬프트 구상 — `Prompt` 엔티티 `metaPrompt TEXT` 컬럼 예약 (로직 없음)
- [x] AI 평가 패널 마크다운 렌더링 — GFM 테이블 포함 완전 렌더링 (스트리밍 중 `<pre>`, 완료 후 `MarkdownRenderer` 전환)
- [x] AI 개선 시 태그 자동 추출 — `TAGS:` 형식 파싱, `AiSuggestion.suggestedTags` 저장, `accept` 시 `Post.tags` 교체 (3~10개), 수락 버튼에 적용 항목
  표시
- [x] 반응형 레이아웃 — 모바일/태블릿 대응 미디어 쿼리 (Layout 네비, PostDetailPage, PostListPage, PostCreatePage, PostEditPage)
- [x] AI 평가 결과 localStorage 저장 — `ai_eval_{postId}` 키, 재방문 시 자동 복원, 저장 시각·지우기 버튼
- [x] PostDetailPage 2컬럼 레이아웃 — 우측 사이드바(요청 폼) + 본문 하단(결과), 앵커 버튼으로 결과 섹션 이동
- [x] PostDetailPage 본문 폭 유지 — `Layout wide` prop + `mainWide` (max-width: 1320px), 2컬럼 grid `minmax(0, 960px) 320px`으로
  본문 폭 유지
- [x] AI 개선 요청 중복 제거 — `AiSuggestionResult` 전용 컴포넌트 분리, 사이드바=폼 전용(`hideResult`), 본문 하단=결과 전용
- [x] AI 평가 결과 마크다운 렌더링 — `AiEvaluationPanel` 내부 + `PostDetailPage` `evalText` 모두 `MarkdownRenderer` 적용 완료
- [x] AI 평가 결과 레이아웃 리뉴얼 — 평가 결과를 본문 하단(`#eval-result`)으로 이동, 사이드바는 폼 + "평가 결과 보기 ↓" 앵커 버튼만 유지. `evalContent` overflow-x:
  auto로 테이블 가로 스크롤 보장
- [x] AI 평가 결과 `#` 헤더 렌더링 안됨 — 빈 `data:` 라인(AI `\n` 토큰)을 `\n`으로 처리하도록 수정. AiEvaluationPanel·AiSuggestionPanel 동일 적용
- [x] `PromptBuilder.buildEvaluation()` 출력 형식 리뉴얼 — 섹션 순서 재구성(🔥총평→📊점수표→🚨TOP3→🧠개선→✂️제거→🏗구조→💎전문가한줄), 강조·가독성·Java 코드블록 언어태그 규칙 적용
- [ ] REST Docs — Spring Boot 4 호환 라이브러리 출시 후 구현 예정

### SQL Visualization Widget

> 상세 → [`sqlviz.md`](sqlviz.md)

- [x] 백엔드: `POST/GET/DELETE /api/sqlviz`, `GET /api/embed/sqlviz/{id}` (공개)
- [x] 시뮬레이션 엔진 — 6개 시나리오, 격리 수준 분기, JSQLParser + RowKey + VirtualDB
- [x] 프론트: `SqlVizPage`, `SqlVizEmbedPage`, `ConcurrencyTimeline`, `ExecutionFlow`, `EmbedGenerator`
- [x] PromptBuilder SQLViz 마커 지시문 추가 (ContentType별 추천 포함)
- [x] `sql visualize` 마커 렌더링 — `MarkdownRenderer` 전처리 + `SqlVizMarker` 컴포넌트
- [x] `[IMAGE: ...]` 플레이스홀더 처리 — 이미지 없으면 본문에서 제거
- [x] AI 작성 메타 정보 통합 표시 — PostDetailPage 하단 카드 (모델·날짜·개선횟수), 본문 인용 줄 제거

### 운영 / 모니터링

- [x] 모니터링 가이드 문서 작성 (`monitoring.md`)

### 테스트

- [x] Controller 테스트 (`PostControllerTest`, `MemberControllerTest`)
- [x] Repository 통합 테스트 (4개 — H2 기반)
- [x] 도메인 단위 테스트 (`PostDomainTest`, `MemberDomainTest`, `WebhookSignatureVerifierTest`)
- [x] UseCase 단위 테스트 (`CreatePostUseCaseTest`, `ImportHashnodePostUseCaseTest`, `AiClientRouterTest`)
- [x] SSE 스트리밍 통합 테스트 — `MockAiClient` + `StepVerifier` / `MockMvc` SSE 이벤트 순서 검증 (단위 4개 + Controller 4개 + DB 저장 3개)

---

## 4. 개발 규칙

- 발견한 내용(버그, 설계 결정)은 해당 도메인 `claude.md`에 기록
- Jasypt 암호화는 AI가 직접 수행하지 않음 — jasypt online tool에서 수동 암호화 후 yml에 붙여넣기
- `any` / `unknown` 타입 사용 금지 (프론트엔드)
- 작업 완료 시 해당 문서의 체크박스 완료 표시
