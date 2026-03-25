# AI Blog Automation — 프로젝트 계획서

> 작성일: 2026-03-20 / 최종 수정: 2026-03-25
> 개발자: 1인 개인 프로젝트 / 목표 사용자: 최대 100명

**상세 문서:**

- 백엔드 → [`backend/claude.md`](backend/claude.md)
- 프론트엔드 → [`frontend/claude.md`](frontend/claude.md)
- 운영/모니터링 → [`monitoring.md`](monitoring.md)

---

## 1. 프로젝트 개요

GitHub 활동(커밋, PR, README 등)을 자동 수집해 Claude / Grok / GPT / Gemini AI로 블로그 글을 개선하고 Hashnode에 발행하는 자동화 시스템.

**두 가지 흐름:**

1. GitHub 레포
   → 데이터 수집
   → 글 초안
   → AI 개선
   → Hashnode 발행
2. 직접 글 작성
   → AI 개선
   → Hashnode 발행

---

## 2. 기술 스택 요약

| 영역                                            | 기술                                                     |
|-----------------------------------------------|--------------------------------------------------------|
| 백엔드                                           | Spring Boot 4.0.3, Java 25, Gradle 9.3.1               |
| 프론트                                           | React 18 + TypeScript + Vite 5                         |
| DB                                            | H2 (local) / Docker PostgreSQL (dev) / Supabase (prod) |
| 캐시                                            | Redis (AI 사용량, Rate Limit, JWT blacklist)              |
| 암호화                                           | Jasypt `PBEWithMD5AndDES` + AES-256-GCM (DB 컬럼)        |
| 인증                                            | GitHub OAuth2 + JWT (Access 24h / Refresh 30일)         |
| 컨테이너                                          | Docker Compose (backend, frontend, redis, certbot)     |
| CI/CD                                         | GitHub Actions                                         
 → OCI 서버 롤링 배포                                |
| 인프라                                           | OCI 단일 서버 (2CPU/16GB), 도메인: `git-ai-blog.kr`           |
| 웹서버                                           | Nginx                                                  
 — HTTPS (Let's Encrypt 자동 갱신) + reverse proxy |

---

## 3. 인프라 / 배포

### 서버 정보

- IP: `168.107.26.27` / 도메인: `git-ai-blog.kr`
- OS: Ubuntu 20.04 / 사양: 2CPU / 16GB RAM / SSH 키: `/private`

### Docker Compose 구성

```

backend — Spring Boot prod, JASYPT_ENCRYPTOR_PASSWORD, prepareThreshold=0
frontend — Nginx + React, 80/443, certbot_data(ro) + certbot_www 마운트
docker-entrypoint.sh: 6시간마다 인증서 변경 감지 → nginx reload
redis — AOF 영속성, 헬스체크 포함
certbot — 12시간마다 certbot renew --webroot (frontend 무중단)

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

## 4. 구현 현황

### 환경별 설정

- [x] **local** — `JASYPT_ENCRYPTOR_PASSWORD` 없이 H2로 기동 (기본값 내장)
- [x] **dev** — `./gradlew serverRun` 으로 Redis + PostgreSQL Docker 자동 기동, dev 프로파일 실행
- [x] **GitHub Actions** — local 프로파일, Redis 제외 (`DataRedisAutoConfiguration` exclude)
- [x] **mock 로그인** — `@Profile({"local", "dev"})` 확장, prod 빌드에서 자동 제거 (`import.meta.env.DEV`)
- [x] **Hashnode 발행** — prod 프로파일에서만 실제 발행 허용, local/dev는 422 반환

### 인프라 / 배포

- [x] backend Docker Compose 설정 경로 오류 수정
- [x] CI 스마트 재빌드 정책 구현 (`check-prev-result` job)
- [x] GitHub OAuth `redirect_uri` 오류 해결 (prod yml 명시)
- [x] backend `unhealthy` 해결 (Actuator + SecurityConfig permitAll)
- [x] backend Dockerfile 레이어 캐시 최적화 + `.dockerignore`
- [x] PostgreSQL prepared statement 충돌 해결 (`prepareThreshold: 0`)

### 기능

- [x] AI 사용량 제한 — 전체 + 모델별 일일 한도, Redis 기반, 초과 시 429
- [x] AI 모델 선택 — Claude/Grok/GPT/Gemini 수동 선택 또는 ContentType 기반 자동 라우팅
- [x] 커스텀 프롬프트 — 사용자당 최대 30개, 공개/비공개, 인기순 탐색, AI 개선 요청 시 선택
- [x] 기본 프롬프트 교체 — SEO 최적화 블로그 작성 가이드 (`PromptBuilder`)
- [x] API 키 연동 검증 — 저장 시 실제 API 호출로 유효성 확인
- [x] Hashnode 발행 시 AI 메타정보 자동 추가 (모델, 생성일, 개선 횟수)
- [x] 게시글 태그 정규화 (소문자, 특수문자 제거)
- [x] Swagger UI (`/swagger-ui/index.html`) — springdoc-openapi 2.8.8

> **이미지 생성 기능 방향 변경**: AI 개선 플로우에서 이미지 자동 생성 제거.
> AI 개선 실패 시 이전에 생성된 이미지가 DB에 남을 수 있는 문제 방지.
> 이미지 생성은 사용자가 수동으로 별도 버튼(`ImageGenButton`)으로만 사용.

- [x] **AI 개선 플로우에서 이미지 생성 로직 제거** — `RequestAiSuggestionUseCase`에서 `imageGenerationService.resolveImagePlaceholders()` 호출 제거. `ImageGenButton`은 별도 독립 기능으로 유지.

### 내부 기능 최적화

- [x] **기존 기능 회귀 검증** — 백엔드 전체 테스트 통과 확인 (BUILD SUCCESSFUL)

### 게시글 뷰어

- [x] GFM 전체 지원 (`remark-gfm`: 테이블, 체크박스, 취소선, autolinks)
- [x] Mermaid 다이어그램 렌더링 (`MermaidBlock` + `MarkdownRenderer` 공통 컴포넌트)
- [x] DRAFT 상태에서 발행 버튼 활성화

### 버그 수정

- [x] 이미지 생성 모델 라우팅 버그 — `ImageGenerationService`에서 GPT 전용 강제
- [x] Broken pipe `HttpMessageNotWritableException` — `GlobalExceptionHandler`에서 WARN 처리

### API 문서화

- [x] Swagger UI 적용 (`/swagger-ui/index.html`), JWT Bearer 인증 스킴 포함
- [x] Swagger UI 라우팅 충돌 수정 (nginx.conf에 `/swagger-ui/`, `/v3/` → backend proxy 추가)
- [ ] REST Docs + Redocly / Stoplight / Slate — Spring Boot 4 호환 라이브러리 출시 후 구현 예정

### SQL Visualization Widget

> SQL 동시성 문제(데드락, Dirty Read 등)를 인터랙티브 타임라인/플로우로 시각화하고 Hashnode에 임베드하는 기능.
> **보안**: SQL 직접 실행 절대 금지 — 순수 Java 가상 시뮬레이션만 사용

**사용 흐름:** `/sqlviz` → 제목/SQL/시나리오/격리수준 입력 → 타임라인/실행흐름 미리보기 → `%%[sqlviz-{id}]` 복사 → Hashnode 붙여넣기

**구현 항목 (기본 구현 완료):**

- [x] 백엔드: `POST/GET/DELETE /api/sqlviz`, `GET /api/embed/sqlviz/{id}` (공개, 인증 불필요)
- [x] 시뮬레이션 엔진 — 6개 시나리오(DEADLOCK/DIRTY_READ/NON_REPEATABLE_READ/PHANTOM_READ/LOST_UPDATE/MVCC), 격리 수준 분기
- [x] 프론트: `SqlVizPage`, `SqlVizEmbedPage`, `ConcurrencyTimeline`, `ExecutionFlow`, `SqlEditor`, `EmbedGenerator`
- [x] Layout 네비게이션 "SQL Viz" 링크

> 상세 스펙(API, 엔티티, 시뮬레이션 엔진): `backend/claude.md`, `frontend/CLAUDE.md` 참고

#### ExecutionFlow 시각화 스타일 개선 (미구현)

> 목표: "가장 흔한 패턴: 락 획득 순서 불일치" 이미지 스타일처럼 직관적이고 인터랙티브한 시각화.
> 현재 기본 구현에서 아래 스타일 가이드로 개선 필요.

**레이아웃 구조:**
```
상단: Transaction 1 | Row A (Lock) | Row B (Lock) | Transaction 2  ← 4개 컬럼 헤더
      세로 시간 흐름 (위 → 아래)
      T1이 Row A Lock 획득 (초록 체크)
      T2가 Row B Lock 획득 (초록 체크)
      T1이 Row B 요청 → 대기 (모래시계)
      T2가 Row A 요청 → 대기 (모래시계)
      ──── DEADLOCK 발생 배너 ────
하단: 동일 컬럼 반복
```

**React Flow 노드/엣지 명세:**

| 종류 | 스타일 |
|------|--------|
| Transaction 노드 (T1, T2) | 파란 박스 |
| Resource 노드 (Row A, Row B) | 회색 박스 |
| Lock 획득 성공 엣지 | 초록 실선 + ✅ 체크 아이콘 |
| Lock 대기 엣지 | 주황 점선 + ⌛ 모래시계 아이콘 |
| Deadlock 엣지 | 빨간 실선 + 💀 skull 아이콘 |
| Deadlock 배너 | 중앙 빨간 오버레이 배너 |

**필수 인터랙션:**
- Timeline 슬라이더 — 시간 순서대로 단계별 재생/정지/이동
- 노드 클릭 → 해당 단계 상세 설명 툴팁 또는 사이드 패널
- Isolation Level 토글 (READ_COMMITTED / REPEATABLE_READ / SERIALIZABLE 등) → 시뮬레이션 결과 실시간 변경

**디자인 요구사항:**
- 다크 모드 최적화 (프로젝트 기본 다크 테마)
- Hashnode iframe 안에서도 responsive + 자동 height 조정
- 교육용으로 직관적이고 전문적인 톤

**확장성:**
- DEADLOCK 패턴 먼저 완성 후 Lost Update / Dirty Read / Phantom Read / MVCC를 동일 컴포넌트 구조로 확장
- 각 시나리오별 "해결 방법 제안" 텍스트 표시 (예: "락 획득 순서를 통일하세요")

- [x] `ExecutionFlow` 컴포넌트 스타일 가이드 기준으로 개선 — 노드/엣지 색상(초록/주황/빨강), 아이콘(✅⌛🔒💀), Deadlock 배너 추가
- [x] Isolation Level 토글 UI — SqlVizPage 미리보기 상단에 READ_UNCOMMITTED/READ_COMMITTED/REPEATABLE_READ/SERIALIZABLE 버튼 토글 추가
- [x] 노드 클릭 툴팁 — ExecutionFlow 노드 클릭 시 txId/operation/sql/result/detail 상세 패널 표시

---

### SQLViz + AI 프롬프트 연동 가이드

> AI가 게시글을 작성/개선할 때 SQL 흐름이 필요한 부분을 SQLViz 위젯으로 유도하는 방법 정리.
> **기능 추가 없이 기존 시스템만으로 연동 가능.**

#### 개념: AI가 직접 SQLViz를 만드는 게 아니다

AI(Claude/Grok/GPT)는 텍스트만 반환한다. SQLViz 위젯 자체는 사용자가 `/sqlviz` 페이지에서 직접 생성하고 임베드 코드를 복사해서 게시글에 붙여넣는 흐름이다. AI의 역할은 **"여기에
SQLViz 위젯을 넣어라"는 마커(placeholder)를 본문에 심어주는 것**이다.

#### 현재 이미지 마커 방식 (참고)

`PromptBuilder.java`에 이미 이미지 마커 패턴이 적용되어 있다:

```
[IMAGE: architecture diagram showing microservices]
```

→ AI가 이 형식을 본문에 삽입하면 프론트에서 이미지 생성 버튼으로 전환.

#### SQLViz 마커 형식 (코드 블록 방식)

이미지 마커의 인라인 방식과 달리, SQLViz는 SQL 코드를 포함하므로 **코드 블록 방식**을 사용한다:

````
```sql visualize [dialect] [옵션...]
-- SQL 코드
```
````

**dialect (필수, 첫 번째 위치):**

- `mysql`
- `postgresql` 또는 `postgres`
- `oracle`
- `generic` (기본값)

**옵션 (dialect 뒤에 공백으로 구분, 최대 2개):**

- `deadlock`, `lost-update`, `dirty-read`, `non-repeatable`, `phantom-read`, `mvcc`, `locking`, `timeline`

#### PromptBuilder에 추가할 지시문 (미구현 — `PromptBuilder.java` `base` 지시문 다이어그램 섹션 아래에 추가 예정)

`PromptBuilder.java`의 `base` 지시문 **다이어그램 섹션** 바로 아래에 아래 내용을 추가한다:

```
### SQL 시각화
- DB, 트랜잭션, 동시성, 격리 수준 관련 내용을 설명할 때는 반드시 아래 형식의 SQLViz 마커를 사용한다.
- 마커 형식: ```sql visualize [dialect] [옵션...]
- dialect는 항상 첫 번째 옵션으로 넣는다 (mysql / postgresql / oracle / generic).
- SQL 코드는 선택한 dialect에 맞는 정확한 문법으로 작성한다.
- 마커 블록 바로 아래에 1~2줄의 자연스러운 한국어 설명을 반드시 추가한다.
- 한 응답당 SQLViz 마커는 최대 3개까지만 사용한다.
- 실제 DB 실행이 아닌 교육용 가상 시나리오만 생성한다.
```

#### Few-shot 예시 (PromptBuilder 지시문에 포함)

**예시 1 — PostgreSQL 데드락:**

````
```sql visualize postgresql deadlock
-- T1
BEGIN;
UPDATE accounts SET balance = balance - 100 WHERE id = 1;

-- T2
BEGIN;
UPDATE accounts SET balance = balance - 100 WHERE id = 2;
```
→ PostgreSQL에서 두 트랜잭션이 서로의 행을 Lock 잡고 발생하는 데드락 시나리오입니다.
````

**예시 2 — MySQL Lost Update:**

````
```sql visualize mysql lost-update
UPDATE accounts SET balance = balance + 300 WHERE id = 1;
```
→ MySQL의 기본 READ COMMITTED 격리 수준에서 발생하는 Lost Update 현상입니다.
````

**예시 3 — Oracle Phantom Read:**

````
```sql visualize oracle phantom-read
SELECT * FROM accounts WHERE balance > 500;
```
→ Oracle에서 REPEATABLE READ 격리 수준에서도 Phantom Read가 발생할 수 있는 예시입니다.
````

#### 사용자 흐름 (AI 마커 → 실제 위젯 삽입)

```
1. 사용자가 게시글 AI 개선 요청
2. AI가 본문에 ```sql visualize [dialect] [옵션] 마커 삽입 + 한국어 설명 1~2줄
3. 사용자가 PostDetailPage/PostEditPage에서 마커 확인
4. 수동으로 /sqlviz 페이지 이동 (게시글 내 버튼 연동은 미구현)
5. 마커의 dialect/옵션/SQL을 입력창에 채워서 위젯 생성
6. 생성된 hashnodeWidgetCode(%%[sqlviz-{id}]) 또는 iframe 코드를 본문의 마커 위치에 교체
```

#### 지원 시나리오 × 격리 수준 매트릭스 (현재 구현 상태)

| 시나리오                | READ_UNCOMMITTED | READ_COMMITTED | REPEATABLE_READ | SERIALIZABLE |
|---------------------|:----------------:|:--------------:|:---------------:|:------------:|
| DEADLOCK            |     ✅ 항상 충돌      |    ✅ 항상 충돌     |     ✅ 항상 충돌     |   ✅ 항상 충돌    |
| DIRTY_READ          |     ✅ 충돌 발생      |     ✅ 방지됨      |      ✅ 방지됨      |    ✅ 방지됨     |
| NON_REPEATABLE_READ |     ✅ 충돌 발생      |    ✅ 충돌 발생     |      ✅ 방지됨      |    ✅ 방지됨     |
| PHANTOM_READ        |     ✅ 충돌 발생      |    ✅ 충돌 발생     |     ✅ 충돌 발생     |    ✅ 방지됨     |
| LOST_UPDATE         |     ✅ 항상 충돌      |    ✅ 항상 충돌     |     ✅ 항상 충돌     |   ✅ 항상 충돌    |
| MVCC                |     ✅ 충돌 없음      |    ✅ 충돌 없음     |     ✅ 충돌 없음     |   ✅ 충돌 없음    |

> 시뮬레이션 로직: `SqlVizSimulationEngine.java` — 격리 수준 조합에 따라 충돌/방지 분기 처리

#### ContentType별 프롬프트 추가 권장 (미구현 — PromptBuilder ContentType 분기에 추가 예정)

| ContentType | SQLViz 추천 시나리오                          |
|-------------|-----------------------------------------|
| CS          | DEADLOCK, MVCC, PHANTOM_READ — 개념 설명 시  |
| CODING      | LOST_UPDATE, DIRTY_READ — 코드 버그 분석 시    |
| TEST        | NON_REPEATABLE_READ — 트랜잭션 테스트 케이스 설명 시 |
| ALGORITHM   | 해당 없음                                   |
| 기타          | 판단에 따라 선택적 사용                           |

---

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
