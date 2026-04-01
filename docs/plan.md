# AI Blog Automation — 작업 체크리스트

> 최종 수정: 2026-04-01

## 진행중 / 예정 작업

> 없으면 비워둔다. 작업 시작 시 여기에 먼저 기록.

---

## 구현 현황 체크리스트

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

### AI 개선 / 평가

- [x] AI 모델 선택 — Claude/Grok/GPT/Gemini 수동 또는 ContentType 자동 라우팅
- [x] AI 사용량 제한 — 전체 + 모델별 일일 한도, Redis 기반, 초과 시 429
- [x] AI SSE 스트리밍 — 토큰 단위 실시간 렌더링, `@Async` + 202 폴백, 예상 완료 시간 카운트다운
- [x] AI 개선 시 제목/태그 자동 생성 — `suggestedTitle` / `TAGS:` 파싱, `accept` 시 Post에 반영
- [x] AI 평가 패널 — 6가지 기준 평가 → 추가 요청사항 자동 생성 → AI 개선 재요청
- [x] `PromptBuilder` 리뉴얼 — 4단계 파이프라인 + System Prompt 분리 + Prompt Caching
- [x] 커스텀 프롬프트 — 사용자당 최대 30개, 공개/비공개, 인기순 탐색
- [x] AI 태그 자동등록 UI — `AiSuggestionResult`에서 "+ 프로필에 추가" 원클릭 등록
- [x] AI 개선 중복 생성 방지 — 내용 동일 시 기존 제안 재사용
- [x] `prompt.yml` — 모델명 상수 `@Value`로 교체, Haiku 자동 라우팅 조건값 관리
- [x] Haiku 자동 라우팅 — 모델 미지정 + content 1,000자 미만 + extraPrompt 있음 → Haiku
- [x] 프롬프트 텍스트 압축 — 산문체 → 불릿 키워드, ~35% 절감

### PostDetailPage UI

- [x] 2컬럼 레이아웃 — 우측 사이드바(폼) + 본문 하단(결과)
- [x] GFM + Mermaid 렌더링, AI 평가 결과 마크다운 완전 렌더링
- [x] PDF 변환 — 제목+본문만 출력, 페이지 중간 잘림 방지
- [x] 반응형 레이아웃 — 모바일/태블릿 미디어 쿼리

### SQL Visualization Widget

> 상세 → [`sqlviz.md`](sqlviz.md)

- [x] 백엔드: `POST/GET/DELETE /api/sqlviz`, `GET /api/embed/sqlviz/{id}`
- [x] 시뮬레이션 엔진 v2 — VirtualDatabase, 격리 수준 분기, `LOCK_WAIT` 시나리오
- [x] `-- STEP:[n] TX:[id]` 주석 기반 인터리빙 런타임
- [x] 재생 애니메이션, BLOCKED 일시정지, 수동 진행 버튼
- [x] embed 페이지 다크모드, SQL 목록 TX별 컬럼 표시

### 기타

- [x] API 키 연동 검증 — 저장 시 실제 API 호출로 유효성 확인
- [x] Hashnode 발행 연동 (태그 매핑, 버그 수정 완료)
- [x] `durationMs` 컬럼 — AI 응답 소요 시간 저장
- [x] Swagger UI (`/swagger-ui/index.html`)
- [ ] REST Docs — Spring Boot 4 호환 라이브러리 출시 후 구현 예정

### DB 개선 (우선순위 순)

> 상세 → [`DBA_Claude.md`](DBA_Claude.md)

- [x] 인덱스 추가 — `posts`, `ai_suggestions`, `repos`, `prompts`, `sqlviz_widgets`, `post_tags` (Critical)
- [x] ElementCollection N+1 해결 — `PostTag` 별도 엔티티 분리 완료 (High)
- [x] Flyway 도입 — `ddl-auto: validate` + `db/migration/` 마이그레이션 파일 관리 (High)
- [ ] 암호화 컬럼 길이 근거 주석 — 각 컬럼에 원문 최대 길이 + 오버헤드 계산 근거 명시 (Medium)
- [ ] `deleteByPostId` 트랜잭션 안전화 — `@Modifying @Query` 벌크 DELETE로 교체 (Medium)
- [ ] `findAvgDurationMsByModel` 반환 타입 — `Double` → `Optional<Double>` (Medium)
- [ ] `findPopularPublic` / `findPopularByMember` 호출 측 LIMIT 강제 확인 — `Pageable.unpaged()` 방지 (Medium)
- [ ] `SqlVizWidget` TEXT 중복 감지 — `sqls_hash` 컬럼(SHA-256) 추가 + 복합 인덱스 (Low)
- [ ] `Member` 암호화 컬럼 분리 — `member_credentials` 별도 테이블로 9개 암호화 컬럼 이전 (Low)
- [ ] HikariCP 운영 설정 추가 — `maximum-pool-size`, `idle-timeout`, `max-lifetime`, `leak-detection-threshold` (Low)

### 테스트

- [x] Controller 테스트 (`PostControllerTest`, `MemberControllerTest`)
- [x] Repository 통합 테스트 (4개 — H2 기반)
- [x] 도메인 단위 테스트 (`PostDomainTest`, `MemberDomainTest`)
- [x] UseCase 단위 테스트 (`AiClientRouterTest` 등)
- [x] SSE 스트리밍 통합 테스트

---

## 개발 규칙

- 발견한 내용(버그, 설계 결정)은 해당 도메인 `claude.md`에 기록
- 새 리서치 내용은 `research.md`에 기록
- 작업 완료 시 해당 체크박스 완료 표시
- Mermaid: `sequenceDiagram`(시간 순서/주체 간 통신) / `flowchart LR`(인과관계) — 다이어그램 위 한 줄 핵심 요약 선행