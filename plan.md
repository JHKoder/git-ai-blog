# AI Blog Automation — 프로젝트 계획서

> 작성일: 2026-03-20 / 최종 수정: 2026-04-01

## 문서 구조

| 파일                                         | 역할                                    |
|--------------------------------------------|---------------------------------------|
| [`backend/claude.md`](backend/claude.md)   | 백엔드 코드 레벨/기능 상세 (API, 도메인, 이슈 기록)     |
| [`frontend/CLAUDE.md`](frontend/CLAUDE.md) | 프론트엔드 코드 레벨/기능 상세 (컴포넌트, 타입, 이슈 기록)   |
| [`sqlviz.md`](sqlviz.md)                   | SQLViz 설계 / UX / AI 연동 가이드            |
| [`infra.md`](infra.md)                     | 배포 / 인프라 셋업 절차                        |
| [`monitoring.md`](monitoring.md)           | 운영 / 장애 대응                            |
| [`research.md`](research.md)               | 리서치 내용                                |
| [`**/code.md`](research.md)                | 코드 규칙                                 |
| [`prompt.md`](prompt.md)                   | PromptBuilder 아키텍처 — 기능/컨벤션/출력 레이어 구조 |

---

## 피드백: System Prompt 분리 도입 검토 ✅ 완료

> Grok 피드백 요약 + 현재 코드 기준 분석

### 현재 구조의 실제 토큰 비용

`PromptBuilder.buildFull()` 기준으로 매 요청마다 전송되는 내용:

| 항목                                      |          대략적 크기 | 비고        |
|-----------------------------------------|----------------:|-----------|
| 4단계 파이프라인 지시문                           |         ~600 토큰 | 매 요청 반복   |
| `getBaseInstruction()` (공통 규칙 + 타입별 규칙) |         ~700 토큰 | 매 요청 반복   |
| `post.content` (본문)                     | 1,000~10,000 토큰 | 가변        |
| `extraPrompt` (사용자 요청)                  |        0~200 토큰 | 선택        |
| **합계 (반복 고정 부분만)**                      |   **~1,300 토큰** | 매 호출마다 낭비 |

하루 100회 기준 → 고정 지시문만으로 **130,000 토큰 소모**.

---

### System Prompt 분리 시 기대 효과

Claude API는 `system` 필드를 별도로 받는다. 현재 `ClaudeClient`는 `messages[0].content`에 전부 넣는 방식이라 System Prompt 캐싱 혜택을 전혀 못 받고 있음.

다른 ai api 는 system 을 가지고있 는가?

> **A.** GPT(OpenAI) · Grok(OpenAI 호환) · Gemini 모두 `system` 역할 메시지를 지원한다. 다만 **Prompt Caching(캐시 과금 절감)은 Claude만** 공식
> 제공한다. 나머지 모델은 system 분리 자체는 가능하지만 캐시 히트 시 과금 절감 혜택은 없다 — 구조 정리 효과만 있을 뿐 토큰 비용은 동일.

또한 내부 로직 변경으로 system 필드도 최신화를 어떻게 관리하는가?

서비스 내부 system prompt이 변경될때 마다 각 개인의 유저 ai 도 갱신해야하는데 이 방식은 어떤 구조로 해야 절약 을 할수 있는지 아이디어 를 제시해줘

혹은 레디스에서 system prompt 변기 감지시 요청하는 api를 보내는 것도 검토해줘

> **A.** Anthropic Prompt Cache의 실제 동작: `cache_control`은 "이 내용을 캐시해라"는 힌트일 뿐, 클라이언트는 매 요청마다 System Prompt를 그대로 전송한다.
> Anthropic 서버가 내용이 동일하면 캐시에서 읽고, 내용이 바뀌면 자동으로 새 캐시를 생성한다(TTL 5분). 즉 **코드 배포 = 자동 갱신** — 수동 초기화 불필요.
>
> 관리자 페이지에서 캐시 초기화하거나 Redis 변경 감지로 API를 트리거하는 방식은 이 경우엔 **구현할 필요 없다**. 의미가 있는 경우는 DB에 System Prompt를 저장하고 런타임에 동적으로 내용을
> 바꾸는 구조일 때다. 현재처럼 `PromptBuilder` 코드에 하드코딩된 구조에서는 배포로 충분하다.

Anthropic은 **Prompt Caching** 기능을 제공한다 (`cache_control: {"type": "ephemeral"}`). System Prompt에 `cache_control`을 붙이면 동일
내용이 재전송돼도 **캐시 토큰(cache read)으로 처리되어 입력 토큰의 약 10%만 과금**된다.

| 항목             | 현재              | System Prompt 분리 + 캐싱                    |
|----------------|-----------------|------------------------------------------|
| 고정 지시문 과금      | 매 호출 full price | 첫 호출만 full, 이후 ~90% 절감                   |
| User Prompt 길이 | 지시문 + 본문 혼합     | 본문 + extraPrompt만                        |
| 구현 복잡도         | 낮음              | 중간 (`ClaudeClient` + `PromptBuilder` 분리) |

---

### 주의사항 (도입 전 검토 필요)

1. **Prompt Caching은 Claude 전용** — Grok, GPT, Gemini는 동일 구조 미지원. `AiClientRouter`로 라우팅하는 현재 구조에서 클라이언트별 분기가 필요해짐.

grok,gpt,gemini 는 이런 구조를 지원하지않는다면 어떻게 이 토큰 낭비를 방지하는가?

> **A.** 이 세 모델에서 토큰 낭비를 줄이는 현실적인 방법은 캐싱이 아니라 **프롬프트 자체를 짧게 만드는 것**이다. 구체적으로:
> - `getBaseInstruction()` 내 중복 표현 압축 — 현재 공통 규칙이 산문체로 길게 기술되어 있음. 불릿 키워드로 줄이면 30~40% 절감 가능.
> - ContentType별 추가 규칙을 핵심 1~2줄로 압축.
> - `extraPrompt`가 없는 경우 4단계 파이프라인 지시문 일부(1·2단계 설계 지시)를 생략하는 경량 경로 도입.
    > 이 방법은 Claude에도 동일하게 적용되어 전체 모델에 공통 절감 효과가 있다.

2. **System Prompt 캐시 TTL = 5분** — 5분 내 동일 System Prompt로 재호출해야 캐시 히트. 사용 빈도가 낮으면 효과 미미.

3. **현재 PromptBuilder는 ContentType별로 지시문이 달라짐** — `getBaseInstruction(contentType)`이 타입마다 다른 내용을 반환하므로 System Prompt가
   8가지 변형이 된다. 캐시 히트율이 낮아질 수 있음.

이를 특정 분야라고해도 가긱 다른 방식이 있거 변형이 되어야 유역하게 잘 돌아감

> **A.** 맞다. 분야별 변형은 품질상 필수이므로 제거할 수 없다. 대신 **공통 규칙(base)만 System Prompt로 분리하고, ContentType별 추가 규칙은 User Prompt에 붙이는 혼합
전략**이 현실적이다.
> - System Prompt = 공통 규칙(~700 토큰) → 모든 ContentType에서 동일 → 캐시 히트율 100%
> - User Prompt = ContentType별 추가 규칙(~100~150 토큰) + 본문 + extraPrompt
>
> 이렇게 하면 캐싱 혜택(공통 700 토큰)을 온전히 누리면서 분야별 유연성도 유지된다.

4. **JSON 출력 구조 전환 비용** — Grok 피드백의 JSON 출력 방식은 현재 `parseTitle()` / `parseTags()` / `removeFirstHeading()` 파싱 전략을 전면
   교체해야 함. 프론트 SSE 렌더링도 영향 받음.

프론트 SSE 렌더링 은 아주 핵심 기능이라 뺄수 없음

> **A.** JSON 출력 방식은 **도입하지 않는다**. SSE 스트리밍은 토큰 단위로 순서대로 흘러와야 하는데, JSON은 닫는 `}`가 와야 파싱 가능 → 스트리밍 실시간 렌더링과 근본적으로 충돌한다. 현재
`# 제목`, `TAGS:`, 본문 순서로 오는 텍스트 구조가 SSE와 가장 궁합이 좋다. 유지.

5. **Haiku 라우팅** — 단순 수정 요청(`extraPrompt`만 있고 content가 짧은 경우)은 Haiku로 자동 라우팅하는 것도 비용 절감에 효과적. `AiClientRouter`에 조건 추가로
   구현 가능.

클로드 모델에서 이것도 추가해줘

> **A.** 구현 가능하다. `AiClientRouter.route()`에 아래 조건을 추가하면 된다:
> - 사용자가 모델을 명시하지 않은 경우(`model == null`)
> - `content` 길이 1,000자 미만 + `extraPrompt` 존재(단순 수정 요청으로 판단)
    > → `claude-haiku-4-5`로 자동 라우팅
>
> Haiku는 Sonnet 대비 **입력 토큰 ~10배, 출력 토큰 ~8배 저렴**하다. 단 품질이 낮으므로 짧은 요청·빠른 피드백 용도에만 적합하다. 권장 접근 순서 6번으로 추가.
---

### AI 모델 분류 및 용도 정의

| 등급                    | 모델                                    | 용도                                 |
|-----------------------|---------------------------------------|------------------------------------|
| **가성비** (대량/자동화/반복)   | Claude Haiku 4.5, GPT-4o mini         | 로그 분석, 간단 코드 생성, 데이터 가공, 테스트 코드 초안 |
| **표준** (실무 기본)        | Claude Sonnet 4.6, GPT-4o             | API 설계, 리팩토링, 코드 리뷰, 문서화           |
| **고성능** (복잡한 문제/아키텍처) | Claude Opus 4.5                       | 아키텍처 설계, 성능 튜닝, 복잡한 버그 분석, 시스템 디자인 |
| **특화** (옵션)           | Grok 3                                | 알고리즘, 빠른 아이디어, 트렌드                 |

> ✅ **완료** — 모델명은 `prompt.yml`에서 v4/v3 버전 그룹별로 관리.
> `AiClientRouter`, `ClaudeClient`, `GrokClient`, `GptClient`, `GeminiClient` 상수 모두 `@Value`로 교체 완료.

---

### `prompt.yml` 관리 설계 ✅ 완료

**분리 기준**

| `prompt.yml`에 넣을 것 | 코드(`PromptBuilder`)에 유지할 것 |
|---|---|
| 모델명 (버전 그룹별) | 4단계 파이프라인 지시문 |
| threshold 숫자값 (1,000자 등) | `getBaseInstruction()` 규칙 텍스트 |
| Haiku 자동 라우팅 조건값 | ContentType별 추가 규칙 |

> 컨벤션 규칙 텍스트는 yml에 넣지 않는다.
> yml 블록 스칼라 지옥(`|`) + IDE 마크다운 미리보기 불가 + 오타 검출 불가.
> 규칙은 자주 바뀌지 않고 코드 리뷰 대상이어야 하므로 코드에 유지.

**`prompt.yml` 실제 구조**

```yaml
ai:
  model:
    claude:
      v4:
        sonnet: claude-sonnet-4-6
        opus: claude-opus-4-5
        haiku: claude-haiku-4-5-20251001
      v3:
        sonnet: claude-sonnet-3-5-20241022
        haiku: claude-haiku-3-5-20241022
    grok:
      default: grok-3
    gpt:
      mini: gpt-4o-mini
      standard: gpt-4o
    gemini:
      flash: gemini-2.0-flash
  haiku:
    content-length-threshold: 1000
```

**변경 완료 파일**

- ✅ `ClaudeClient` — `SONNET`, `OPUS` 상수 → `@Value("${ai.model.claude.v4.sonnet}")` 등, v3 getter 추가
- ✅ `GrokClient` — `GROK_3` → `@Value("${ai.model.grok.default}")`
- ✅ `GptClient` — `GPT_4O`, `GPT_4O_MINI` → `@Value`
- ✅ `GeminiClient` — `GEMINI_2_FLASH` → `@Value`
- ✅ `AiClientRouter` — `haiku.content-length-threshold` 읽어 자동 라우팅 조건 적용

---

### 프롬프트 토큰 절감 ✅ 완료

`PromptBuilder.buildFull()` 기준 매 요청 ~1,300 토큰 고정 지시문을 아래 3가지 전략으로 절감.

**전략 1 — System Prompt 분리 + Claude Prompt Caching** ✅

- 공통 규칙(base)을 `system` 필드로 분리 (`buildSystemPrompt()` 메서드)
- `cache_control: {"type": "ephemeral"}` + `anthropic-beta: prompt-caching-2024-07-31` 헤더 적용
- 이후 호출은 입력 토큰 **~90% 절감** (캐시 히트 시)
- ContentType별 추가 규칙은 User Prompt에 유지 (분야별 유연성 보존)
- Claude 전용. Grok/GPT/Gemini는 전략 2로 절감

**전략 2 — 공통 규칙 텍스트 압축** ✅

- `getBaseInstruction()` 산문체 → 불릿 키워드로 압축 (~35% 절감)
- ContentType별 추가 규칙 핵심 1~3줄로 압축
- 전체 모델 공통 적용

**전략 3 — 경량 경로 분기** ✅

- content 30자 미만: `buildSimple()` 분기 (기존 유지)
- Claude 모델 미지정 + content 1,000자 미만 + extraPrompt 있음: Haiku 자동 라우팅

> **Prompt Cache TTL = 5분** — 5분 내 동일 System Prompt 재호출 시 캐시 히트.
> System Prompt 변경(코드 배포) = 자동 갱신. Redis 캐시 초기화 불필요.

### 권장 접근 순서 (구현 시)

1. ✅ `prompt.yml` 신규 파일 — 모델명 상수 하드코딩 제거, `@Value`로 교체 (`ClaudeClient`, `GrokClient`, `GptClient`, `GeminiClient`,
   `AiClientRouter`)
2. ✅ `getBaseInstruction()` 텍스트 압축 — 산문체 → 불릿 키워드화 (전체 모델 공통, 즉시 적용 가능)
3. ✅ `PromptBuilder` 공통 규칙(base)과 ContentType별 추가 규칙 분리 — base는 System Prompt, 추가 규칙은 User Prompt로 이동
4. ✅ `ClaudeClient`에 `system` 파라미터 필드 추가 + `cache_control: ephemeral` 헤더 적용 (Claude only)
5. ✅ Grok/GPT/Gemini는 system+user를 합쳐서 기존 방식 유지 (클라이언트 분기)
6. ✅ JSON 출력 전환 **제외** — SSE 스트리밍과 충돌, 현재 텍스트 파싱 구조 유지
7. ✅ `AiClientRouter`에 Haiku 자동 라우팅 조건 추가 (Claude 모델 미지정 + content 1,000자 미만 + extraPrompt 있음)

---



------------------------------------------------------------------------------------------------------------------------

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
- [x] PDF 변환 — 사이드바/태그/발행상태/조회수 제외, 제목+본문만 출력. 다크모드 Mermaid 텍스트 강제 검정 처리
- [x] PDF 페이지 중간 잘림 방지 — `break-inside: avoid` 블록 단위(코드블록·표·Mermaid·목록·제목) 페이지 이동

**기타**

- [x] API 키 연동 검증 — 저장 시 실제 API 호출로 유효성 확인
- [x] 이미지 생성 — `ImageGenButton` 수동 전용
- [x] Hashnode 태그 매핑 — `hashnodeTags` TEXT 컬럼, ProfilePage 태그명↔ID 매핑 UI
- [x] Hashnode 태그 등록 버그 수정 — `com.fasterxml.jackson` → `tools.jackson.*` 교체 (`HashnodeGraphqlBuilder`)
- [x] Hashnode `Variable "$input" was not provided` 버그 수정 — `HashnodeClient`도 동일 문제, `tools.jackson.*` + Spring 주입으로 교체
- [x] AI 개선 수락 후 재요청 400 오류 수정 — AI 저자 줄 누적으로 프롬프트 비대화 → 저장 시 저자 줄 제거 + 600K chars 초과 트리밍
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
