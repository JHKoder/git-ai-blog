# AI Blog Automation — 프로젝트 계획서

> 작성일: 2026-03-20 / 최종 수정: 2026-03-26 (SSE 스트리밍 전체 파이프라인 버그 해결 — ObjectMapper, done race condition, bodyToFlux data 접두사,
> 실시간 pre 렌더링)

/*

ai 개선시 제목도 바꺼줘 제목후보가 내용에 들어가면 안됨

예시로
제목 후보

1) 톰캣 maxThreads는 튜닝 포인트가 아니라 장애 밸브다 — 실전 트러블슈팅
2) Apache Tomcat 스레드 풀의 진짜 역할: 성능이 아니라 장애 전파 속도 제어
3) 톰캣 완전 정복: 구조·스레드 풀·장애 격리까지 주니어 실전 가이드

위 세가지가 있어

완전 정복은 아니고 특정 부분이니까 전체가 아닌 한곳에 집중 한다는 제목으로 완전 정복보다는  ~ 의(는) ~다 와 같은 거 를 정해서 제목후보도 나열하지말고 직접 제목후보에 넣어줘

# 블로그 글 개선

[평가 기준]

다음 6가지 기준으로 각각 점수(10점 만점) + 이유 + 개선안을 작성하라.

1. 구조 설계 (Structure)
   문제 → 원인 → 해결 → 검증 흐름이 논리적으로 이어지는가?
   중간에 독자가 길을 잃는 구간이 있는가?
2. 실무 적합성 (Practicality)
   실제 장애 상황에서 바로 적용 가능한가?
   “이론 설명” vs “실전 대응력” 비율이 적절한가?
3. 깊이 (Depth)
   단순 설정 설명인지, 아니면 “왜 그렇게 되는지”까지 설명하는가?
   내부 동작 (Thread, Queue, OS, JVM)까지 연결되는가?
4. 데이터 기반 설득력 (Evidence)
   수치, 실험, Before/After가 충분한가?
   “느낌”이 아니라 “증명”이 있는가?
5. 가독성 (Readability)
   글 흐름이 자연스러운가?
   불필요하게 긴 구간, 반복, 난독 구간이 있는가?
6. 차별성 (Originality)
   이 글만의 “경험 기반 인사이트”가 있는가?
   구글 검색 결과와 차별화되는가?

[필수 피드백 항목]

1. 가장 치명적인 문제 TOP 3
   이 글이 실무에서 위험할 수 있는 부분
2. 반드시 추가해야 할 내용
   빠져있는 핵심 (예: OS backlog, keepalive, GC 영향 등)
3. 삭제하거나 줄여야 할 부분
   가치 대비 길이가 긴 구간
4. 전문가 느낌을 만드는 핵심 한 줄 추가
   글의 “급”을 올리는 문장 1개 작성
5. 구조 개선안 (리라이팅 제안)
   목차를 더 좋게 재구성

[피드백]

1. 전체 구조
   1단계: 설계 (아키텍처)
   2단계: 작성 (초안 생성)
   3단계: 리뷰 (실무 검증)
   4단계: 압축 (밀도 최적화)

너는 30년차 고성능 튜닝 엔지니어이자 실무 아키텍트다.

나는 단순한 설명 글이 아니라
실제 장애를 줄이는 실무형 글을 쓰고 싶다.

다음 조건을 만족하는 글 구조를 설계하라.

설계 프롬프트

1단계: 설계 프롬프트 (이게 70% 먹는다)
글쓰기

너는 30년차 고성능 튜닝 엔지니어이자 실무 아키텍트다.

나는 단순한 설명 글이 아니라
실제 장애를 줄이는 실무형 글을 쓰고 싶다.

다음 조건을 만족하는 글 구조를 설계하라.

[목표]
독자가 “아 이거 내 서비스 문제다”라고 느끼게 만들 것
읽고 나서 바로 설정을 바꾸게 만들 것
[설계 요구사항]
반드시 “문제 → 원인 → 해결 → 검증” 구조로 설계
각 섹션은 “독자가 겪는 실제 상황” 기준으로 작성
이론 설명은 최소화하고 “왜 장애가 나는지” 중심
중간에 독자의 사고를 끊는 구간 제거
[출력 형식]
글 전체 목차 (스토리 흐름 포함)
각 섹션에서 반드시 들어가야 할 핵심 포인트
독자를 끌어들이는 Hook 문장 3개
반드시 포함해야 할 실무 사례 1개
[주제]

{주제 입력}

👉 이 단계에서 망하면 끝이다
👉 글이 아니라 “설계도”를 뽑는 단계

✍️ 2단계: 작성 프롬프트 (초안 생성)
글쓰기

너는 대규모 트래픽 시스템을 운영하는 시니어 엔지니어다.

아래 설계를 기반으로 글을 작성하되,
“설명”이 아니라 “문제 해결 문서”처럼 작성하라.

[작성 규칙]
불필요한 이론 금지
바로 써먹을 수 있는 내용만 작성
모든 설명은 “왜 장애가 나는지” 기준으로 작성
코드/설정은 실무 기준으로 작성
[강제 포함 요소]
실제 장애 시나리오
잘못된 설정 vs 개선 설정
Before / After 비교
실무에서 바로 적용 가능한 팁
[스타일]
짧고 강하게 (문장 길이 제한)
핵심 먼저 → 설명 나중
“~일 수 있다” 금지 → 단정적으로 작성
[입력 설계]

{1단계 결과 붙여넣기}

👉 이 단계는 그냥 “재료 생산”이다
👉 완벽하려고 하지 마라

🔥 3단계: 리뷰 프롬프트 (여기서 급이 갈린다)

👉 아까 내가 준 프롬프트 그대로 사용

핵심:

점수화
삭제 지적
치명적 문제
⚡ 4단계: 압축 프롬프트 (상위 1% 핵심)

👉 이거 안 하면 글 망한다

글쓰기

너는 책 저자이자 기술 강사다.

아래 글을 “밀도 높은 글”로 재작성하라.

[목표]
글 길이는 20~30% 줄이되
정보량은 그대로 유지
[압축 규칙]
중복 설명 제거
약한 문장 삭제
한 문장 = 하나의 메시지
의미 없는 접속사 제거
독자가 이미 아는 설명 제거
[추가 요구]
각 섹션에 “핵심 한 줄 요약” 추가
글 전체를 관통하는 “전문가 한 문장” 삽입
[입력 글]

{리뷰 반영된 글}

👉 이 단계가 “조회수 + 전문가 느낌” 만든다

💣 이걸 안 하면 절대 못 올라간다

너 포함 대부분이 하는 실수:

❌ 잘못된 방식
한 번에 잘 쓰려고 함
프롬프트 하나로 끝냄
리뷰 안 함
삭제 안 함
✅ 진짜 방식
설계 따로
작성 따로
리뷰 따로
압축 따로

👉 이게 “엔지니어 방식 글쓰기”

---

[Claude 피드백]

## 제목 생성 메모에 대해

제안한 방향("~의(는) ~다" 형식, 후보 나열 금지, 직접 제목 결정)은 구현 관점에서 명확하다.

다만 구현 시 고려할 점:

1. **제목 교체 타이밍 문제**
    - 현재 `suggestedContent`에 본문만 있음. 제목을 바꾸려면 AI 응답 파싱 구조 변경 필요
    - 예: 첫 줄이 `# 제목` 형식이면 분리 추출, 아니면 별도 `suggestedTitle` 필드 추가
    - `accept` 액션 시 `Post.title`도 함께 업데이트해야 하는데 현재 accept는 `content`만 반영함

2. **제목 품질 안정성**
    - "~의(는) ~다" 형식을 프롬프트로 강제하면 AI가 그 패턴에 끼워 맞추는 부자연스러운 제목이 나올 수 있음
    - 후보를 내지 말고 하나만 결정하라는 지시도 AI가 잘 따르지 않는 모델이 있음 (특히 GPT-4o)
    - 검증 로직 추가 고려: 제목에 "후보" "다음 중" 같은 단어가 들어오면 다시 요청하거나 파싱 필터링

3. **기존 제목 vs AI 제안 제목 충돌**
    - 사용자가 직접 지은 제목을 덮어쓰는 것은 민감한 UX 결정
    - "제목도 바꿀지 선택" 옵션 UI를 두는 게 더 안전할 수 있음

---

## 블로그 평가 기준 6개에 대해

기준 자체는 잘 설계되어 있다. 실무 블로그 품질 평가에 적합한 항목들.

보완하면 좋을 점:

1. **점수 인플레이션 문제**
    - AI에게 "10점 만점" 점수를 주게 하면 대부분 6-8점대에 몰리는 경향이 있음
    - 상대 비교("동일 주제 블로그 상위 10%와 비교하면 몇 점인가?") 형식이 더 날카로운 피드백을 유도함

2. **평가 기준 간 상관관계**
    - `Depth`와 `Evidence`는 높게 받기 어렵지 않지만 `Originality`는 AI가 낮게 주기 어려운 항목
    - AI 입장에서 "이 글만의 경험 기반 인사이트"를 판단하는 게 실제로 어렵기 때문
    - 오히려 "구글 검색 1페이지 글과 문장 유사도가 높은가?" 같은 검증 가능한 지시가 낫다

3. **ContentType별 가중치**
    - `ALGORITHM` 글에는 `Evidence` (벤치마크, 복잡도)가 중요하고
    - `CS` 이론 글에는 `Depth`가 더 중요함
    - 동일한 기준을 모든 ContentType에 적용하면 `CS` 글이 `Evidence` 점수에서 불이익을 받을 수 있음

---

## 4단계 글쓰기 프롬프트 파이프라인에 대해

설계 자체는 탄탄하다. "설계 → 작성 → 리뷰 → 압축" 흐름은 실제로 고품질 기술 글쓰기에서 효과적인 방법론.

AI 연동 시 고려할 점:

1. **단계별 컨텍스트 전달이 핵심**
    - 4단계가 각각 별도 AI 호출이 되려면 1단계 결과를 2단계에 넘기는 구조가 필요
    - 현재 시스템은 단일 `extraPrompt` + `content` 구조라 4단계 파이프라인을 직접 자동화하려면 설계 변경 필요
    - 단기적으로는 "1-4단계 합친 단일 프롬프트"로 구현하고, 장기적으로 멀티턴 대화 구조로 확장 고려

2. **3단계 리뷰가 핵심**
    - 위에서 정의한 평가 기준 6개 + 필수 피드백 5개는 3단계 리뷰 프롬프트 그 자체
    - AI 개선 요청 시 이 평가를 먼저 수행하고, 그 결과를 기반으로 글을 재작성하게 하는 것이 핵심 가치
    - 즉, 현재 `PromptBuilder`에 3단계 리뷰 로직을 통합하는 것이 가장 임팩트 있는 구현 방향
    - **예외 조건**: `content` 길이 30자 미만인 경우 (예: "데드락"처럼 짧은 요청)
      → 게시글 개선이 아닌 "설명해줘" 의도에 가깝기 때문에 평가 기준 + 4단계 파이프라인 적용 불필요
      → 이 경우 기존 단순 개선 프롬프트만 사용

3. **압축 단계 주의**
    - "20~30% 줄이되 정보량 유지" 지시는 AI가 잘 수행하지만, 기술 글에서 코드 블록이나 수치를 삭제하는 경우가 있음
    - 압축 프롬프트에 "코드 블록, 벤치마크 수치, Before/After 표는 절대 삭제 금지" 명시 필요

*/

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
- [x] `PromptBuilder` 평가 기준 통합 — 6가지 평가 기준(Structure/Practicality/Depth/Evidence/Readability/Originality) + 필수 피드백 5개를 리뷰 단계로 적용 (content 30자 미만 예외)
- [x] `PromptBuilder` 4단계 파이프라인 통합 — 설계→작성→리뷰→압축을 단일 프롬프트로 통합, 압축 시 코드블록·수치 삭제 금지 명시
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
