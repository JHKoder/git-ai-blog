# AI Blog Automation — 프로젝트 계획서

> 작성일: 2026-03-20 / 최종 수정: 2026-03-27

## 문서 구조

| 파일                                         | 역할                                    |
|--------------------------------------------|---------------------------------------|
| [`backend/claude.md`](backend/claude.md)   | 백엔드 코드 레벨/기능 상세 (API, 도메인, 이슈 기록)     |
| [`frontend/CLAUDE.md`](frontend/CLAUDE.md) | 프론트엔드 코드 레벨/기능 상세 (컴포넌트, 타입, 이슈 기록)   |
| [`sqlviz.md`](sqlviz.md)                   | SQLViz 설계 / UX / AI 연동 가이드            |
| [`infra.md`](infra.md)                     | 배포 / 인프라 셋업 절차                        |
| [`monitoring.md`](monitoring.md)           | 운영 / 장애 대응                            |
| [`research.md`](research.md)               | 리서치 내용                                |
| [`prompt.md`](prompt.md)                   | PromptBuilder 아키텍처 — 기능/컨벤션/출력 레이어 구조 |

---

## Mermaid 다이어그램 사용 기준

> **역할**: 복잡한 시스템 장애 흐름을 가독성 높고 직관적인 다이어그램으로 재구성한다.
> **핵심**: 내용에 따라 타입을 구분해서 사용한다. `flowchart TD` 단독 나열은 금지.

| 상황                                                  | 사용 타입                       |
|-----------------------------------------------------|-----------------------------|
| 트랜잭션 간 상호작용, 시간 순서, Lock 획득/대기 상태 전이, 여러 주체 간 통신 흐름 | `sequenceDiagram`           |
| 단순 인과관계, 한 방향 원인→결과 체인, 프로세스 단계 나열                  | `flowchart LR`              |
| 노드 6개 이상이고 단계 그룹화가 의미 있을 때                          | `flowchart LR` + `subgraph` |

공통 규칙: 다이어그램 위 **한 줄 핵심 요약** 선행 / 노드 텍스트 명사형 키워드 / 주니어가 5초 안에 이해 가능한 수준

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

**AI 개선 / 평가**

- [x] AI 모델 선택 — Claude/Grok/GPT/Gemini 수동 또는 ContentType 자동 라우팅
- [x] AI 사용량 제한 — 전체 + 모델별 일일 한도, Redis 기반, 초과 시 429
- [x] AI SSE 스트리밍 — 토큰 단위 실시간 렌더링 (`POST /api/ai-suggestions/{postId}/stream`), `@Async` + 202 폴백, 예상 완료 시간 카운트다운
- [x] AI 개선 시 제목/태그 자동 생성 — `suggestedTitle` / `TAGS:` 파싱, `accept` 시 Post에 반영
- [x] AI 평가 패널 — 6가지 기준 평가 → 추가 요청사항 자동 생성 → AI 개선 재요청 (Hashnode 전달 X)
- [x] `PromptBuilder` 리뉴얼 — 4단계 파이프라인 통합 + 평가 출력 형식 재구성 (🔥총평→📊점수표→🚨TOP3→🧠개선→✂️제거→🏗구조→💎전문가한줄)
- [x] 커스텀 프롬프트 — 사용자당 최대 30개, 공개/비공개, 인기순 탐색
- [x] AI 태그 자동등록 UI — `AiSuggestionResult`에서 "+ 프로필에 추가" 원클릭 등록
- [x] AI 개선 중복 생성 방지 — 내용 동일 시 기존 제안 재사용 (현재 매 요청마다 새 레코드 생성)

**PostDetailPage UI**

- [x] 2컬럼 레이아웃 — 우측 사이드바(폼) + 본문 하단(결과), `Layout wide` prop, `minmax(0, 960px) 320px`
- [x] 사이드바 발행 버튼 — ghost 스타일, 가로 배열 `[재발행][Hashnode에서 보기]`
- [x] 사이드바 발행 버튼 스타일 분리 — "재발행" 버튼만 `#7c3aed→#6d28d9` gradient + shadow 적용. "Hashnode에서 보기" 버튼은 현재 스타일 유지. 바깥 카드 배경 변경
  없음
- [x] AI 평가/제안 결과 닫기/열기 토글 버튼 — 화면에서만 숨김, 데이터 유지, 섹션 간격 10px
- [x] GFM + Mermaid 렌더링, AI 평가 결과 마크다운 완전 렌더링 (SSE `\n` 토큰 처리 포함)
- [ ] PDF 변환 — 사이드바 제외하고 `[제목 + 게시글 본문]`만 출력, AI 개선 결과는 사이드바에서만 표시

**기타**

- [x] API 키 연동 검증 — 저장 시 실제 API 호출로 유효성 확인
- [x] 이미지 생성 — `ImageGenButton` 수동 전용
- [x] Hashnode 태그 매핑 — `hashnodeTags` TEXT 컬럼, ProfilePage 태그명↔ID 매핑 UI
- [x] Hashnode 태그 등록 버그 수정 — `com.fasterxml.jackson` → `tools.jackson.*` 교체 (`HashnodeGraphqlBuilder`)
- [x] Hashnode `Variable "$input" was not provided` 버그 수정 — `HashnodeClient`도 동일 문제, `tools.jackson.*` + Spring 주입으로 교체
- [x] 반응형 레이아웃 — 모바일/태블릿 미디어 쿼리
- [x] `durationMs` 컬럼 — AI 응답 소요 시간 저장
- [x] Swagger UI (`/swagger-ui/index.html`)
- [ ] REST Docs — Spring Boot 4 호환 라이브러리 출시 후 구현 예정

### SQL Visualization Widget

> 상세 → [`sqlviz.md`](sqlviz.md)

- [x] 백엔드: `POST/GET/DELETE /api/sqlviz`, `GET /api/embed/sqlviz/{id}` (공개)
- [x] 시뮬레이션 엔진 — 6개 시나리오, 격리 수준 분기, JSQLParser + RowKey + VirtualDB
- [x] 프론트: `SqlVizPage`, `SqlVizEmbedPage`, `ConcurrencyTimeline`, `ExecutionFlow`, `EmbedGenerator`
- [x] PromptBuilder SQLViz 마커 지시문 + `--SQLViz:` 마커 렌더링
- [x] SQLViz 시뮬레이션/위젯 목록 미표시 버그 — `SqlVizResponse` 필드명 `simulationData` → `simulation`으로 통일 (프론트 타입
  `SqlVizWidget.simulation`과 불일치가 원인)
- [x] SQLViz AI 개선 시 위젯 중복 생성 방지 — `CreateSqlVizWidgetUseCase`에서 `memberId + sqlsJson + scenario` 조합 중복 검사 후 기존 위젯 재사용
- [x] 시뮬레이션 엔진 v2 — VirtualDatabase 실제 호출 (상세 → `sqlviz.md`)
- [x] `-- STEP:[n] TX:[id]` 주석 기반 인터리빙 런타임 — 사용자 정의 실행 순서 지원
- [x] `-- DB:[mysql|postgresql]` 주석 파싱 + `DbType` enum + 기본값 `SqlParser.DEFAULT_DB = POSTGRESQL`
- [x] `SimulationResult.limitations` 필드 + `SimulationStep.warning` 필드 — 한계 명시 UI
- [x] SQLViz 마커 형식 `--SQLViz:` 방식으로 교체 — `PromptBuilder` 지시문 + few-shot 예시 변경
- [x] `prompt.md` 신규 파일 — PromptBuilder 3레이어 아키텍처 문서화 (기능/컨벤션/출력)
- [x] `SqlVizPage` 사용법 안내 — `<HelpPanel />` 컴포넌트 (4단계 플로우 + 접기/펼치기)
- [x] SQL 에디터 헬퍼 — TX 이름/STEP 번호 자동 삽입 버튼 + `BEGIN ISOLATION LEVEL` 드롭다운
- [x] 시나리오 자동 감지 — SQL 패턴 기반 추정 + 드롭다운 자동 선택 + 추정 근거 툴팁 ("추천 + 사용자 확인" 패턴)
- [x] 쿼리 순서 UI 개선 — 행 단위 `[TX 드롭다운] [SQL 입력]` 리스트 + 위아래 이동, 제출 시 `-- STEP:n TX:id` 형식으로 조립
- [x] SqlVizPage UI 패러다임 전환 — TX별 에디터 카드(T1~T4), 각 에디터 내부에 `-- STEP:n` 직접 작성, `buildSqls()`가 TX 매핑 자동 처리
- [x] ISO 버튼 중복 삽입 방지 — `insertIsoBegin()` 에서 BEGIN 존재 시 스킵 + `-- STEP:n` 자동 포함
- [x] 왼쪽 패널 560px 확대 + 위젯 목록 페이지네이션 — 백엔드 `Page<SqlVizWidget>` + `SqlVizPageResponse`, 프론트 pageSize 10/20/30 + `(n/총)`
- [x] STEP 분리 버그 수정 — `buildSqls()` STEP 구분자 기준 분할, `SqlParser.STEP_COMMENT` 대괄호 선택적 패턴
  UI
- [x] `LOCK_WAIT` 시나리오 신설 — T1 미커밋 → T2 BLOCKED → T1 커밋 → T2 획득 흐름, `buildLockWait()` 빌더, 프론트 라벨/감지 추가
- [x] `SqlParser` `-- STEP:n` (TX 없음) 지원 — `STEP_ONLY` 패턴 추가, `StepMeta(step, null)` 반환, `runInterleaved()` null txId
  fallback (`T{step}`)
- [x] DB CHECK 제약 자동 마이그레이션 — `DbMigrationRunner`에 `sqlviz_widgets_scenario_check` / `isolation_level_check` DROP 추가
- [x] `ParsedSql.lockType` 필드 추가 + `SqlParser` locking read 추출 (`FOR KEY SHARE` / `FOR UPDATE` 등) — regex 방식 (JSQLParser
  5.x API 미지원)
- [x] `runInterleaved()` locking SELECT 처리 — lockType 있으면 `acquireLock()` 호출, BLOCKED 시 `pendingLocks` 기록, COMMIT 시 자동
  재획득
- [x] `SqlVizSimulationEngineTest` 단위 테스트 5개 — FOR KEY SHARE→DELETE BLOCKED→T1 커밋→T2 획득 인터리빙 시나리오 검증
- [x] `SqlVizHelpPanel` 예시 코드 2개 추가 — 데드락 / 락 대기 T1·T2 나란히 표시
- [x] 타임라인 색상 규칙 통일 — 회색/초록/주황/빨강/보라 5단계 (`resultColor()` + CSS 변수)
- [x] ExecutionFlow 실선/점선/굵은 선 구분 — `strokeDasharray` + `strokeWidth` 기반
- [x] CONFLICT 중앙 레이어 — BLOCKED/DEADLOCK 스텝 위치에 `LOCK ZONE` 배지 삽입
- [x] 재생 애니메이션 BLOCKED 일시정지 — BLOCKED 도달 시 pause + 펄스, COMMIT 후 재개
- [x] 격리 수준 모드 스위치 — `POST /api/sqlviz/preview` (저장 없는 미리보기 API)
- [x] embed 페이지 다크모드 — `prefers-color-scheme` 자동 감지 + `?theme=dark` URL param 지원
- [x] SQL 목록 TX별 컬럼 표시 — STEP 번호 기준 정렬, TX1/TX2/TX3 나란히 (상세 → `sqlviz.md`)
- [x] BLOCKED 구간 단계별 수동 진행 버튼 — 일시정지 중 "다른 TX 다음 단계 실행" → 커밋 시 락 해소 후 애니메이션 재개 (상세 → `sqlviz.md`)

### 운영 / 모니터링

- [x] 모니터링 가이드 문서 작성 (`monitoring.md`)

### 테스트

- [x] Controller 테스트 (`PostControllerTest`, `MemberControllerTest`)
- [x] Repository 통합 테스트 (4개 — H2 기반)
- [x] 도메인 단위 테스트 (`PostDomainTest`, `MemberDomainTest`, `WebhookSignatureVerifierTest`)
- [x] UseCase 단위 테스트 (`CreatePostUseCaseTest`, `ImportHashnodePostUseCaseTest`, `AiClientRouterTest`)
- [x] SSE 스트리밍 통합 테스트 — `MockAiClient` + `StepVerifier` / `MockMvc` (단위 4개 + Controller 4개 + DB 저장 3개)

---

## 4. 개발 규칙

- 발견한 내용(버그, 설계 결정)은 해당 도메인 `claude.md`에 기록
- Jasypt 암호화는 AI가 직접 수행하지 않음 — jasypt online tool에서 수동 암호화 후 yml에 붙여넣기
- `any` / `unknown` 타입 사용 금지 (프론트엔드)
- 작업 완료 시 해당 문서의 체크박스 완료 표시
