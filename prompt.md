# PromptBuilder 아키텍처 문서

> 파일: `backend/src/main/java/github/jhkoder/aiblog/infra/ai/prompt/PromptBuilder.java`

---

## 3레이어 아키텍처

단일 프롬프트 안에서 역할을 3레이어로 분리한다.
최종 출력은 세 레이어가 concat되어 AI에 전달된다.

```
[Layer 1] 기능 프롬프트 (buildFull / buildSimple)
          └─ 4단계 파이프라인 지시 (설계 → 작성 → 리뷰 → 압축)
          └─ 출력 규칙 (# 제목, TAGS: 형식, 맨 마지막 모델명 고지)

[Layer 2] 컨벤션 프롬프트 (getBaseInstruction)
          └─ 공통 규칙: 글 구조, 코드, 다이어그램, SQL 코드블록, SQL 시각화, 톤
          └─ ContentType별 추가 규칙 (ALGORITHM / CODING / CS / CODE_REVIEW / 기타)

[Layer 3] 출력 프롬프트 (extraPrompt + content)
          └─ 사용자 추가 요청 (extraPrompt) — 커스텀 프롬프트 or 평가 결과 기반
          └─ 개선할 포스트 원문 (content)
```

### 레이어 흐름 다이어그램

아래는 Layer 1~3이 조립되는 순서.

```flowchart LR
  A[요청 수신\npostId + model + extraPrompt] --> B{content 길이\n30자 이상?}
  B -- 예 --> C[buildFull\nLayer 1: 4단계 파이프라인]
  B -- 아니오 --> D[buildSimple\n단순 개선 지시]
  C --> E[getBaseInstruction\nLayer 2: 컨벤션]
  D --> E
  E --> F[extraPrompt + content\nLayer 3: 출력]
  F --> G[최종 프롬프트\nAI 전달]
```

---

## Layer 1 — 기능 프롬프트

### buildFull (30자 이상)

4단계 파이프라인을 단일 프롬프트로 통합.

| 단계      | 역할                        | 핵심                      |
|---------|---------------------------|-------------------------|
| 1단계: 설계 | "문제 → 원인 → 해결 → 검증" 흐름 설계 | 출력 금지                   |
| 2단계: 작성 | 설계 기반 초안 생성               | 이론 최소화, Before/After 비교 |
| 3단계: 리뷰 | 평가 기준 6개로 점수화             | 출력 금지, 내부 평가만           |
| 4단계: 압축 | 20~30% 분량 축소, 정보량 유지      | 코드블록·수치 삭제 금지           |

**출력 규칙:**

- 첫 줄: `# {제목}` — "~의(는) ~다" 스타일, "완전 정복" 금지, 후보 나열 금지
- 마지막 줄 바로 위: `TAGS: tag1,tag2,tag3` — 영문 소문자, 3~10개
- 맨 마지막 줄: `> 이 글은 {모델명}이 작성을 도왔습니다.`

### buildSimple (30자 미만)

짧은 설명 요청으로 판단 — 4단계 파이프라인 미적용.

---

## Layer 2 — 컨벤션 프롬프트 (`getBaseInstruction`)

ContentType에 따라 두 부분으로 조립:

1. **공통 규칙** — 항상 포함
2. **ContentType별 추가 규칙** — ContentType에 따라 선택

### 공통 규칙 항목

| 항목       | 내용                                               |
|----------|--------------------------------------------------|
| 제목 & SEO | `## 이 글에서 얻을 수 있는 것`, `## 검색 키워드` 섹션 필수          |
| 글 구조     | 문제 → 원인 → 해결(코드) → Before/After → 실수 → 3줄 정리     |
| 코드       | `❌` 잘못된 예 + `✅` 개선 예 비교, 수치 명시                   |
| 다이어그램    | Mermaid 블록 사용, `flowchart TD` 단독 금지, 타입 선택 기준 명시 |
| SQL 코드블록 | ` ```sql ` 만 사용 (dialect 붙이기 금지)                 |
| SQL 시각화  | `--SQLViz:` 마커 (아래 상세)                           |
| 톤        | 전문적+친근, "여러분" 호칭, 핵심 → 근거 → 예시                   |

### SQL 시각화 규칙 (`--SQLViz:` 마커)

> DB, 트랜잭션, 동시성, 격리 수준 관련 내용을 설명할 때 사용.

**마커 형식:**

```sql
--SQLViz: [dialect] [시나리오]
-- SQL 코드
```

| 파라미터    | 값                                                                                      |
|---------|----------------------------------------------------------------------------------------|
| dialect | `postgresql` / `mysql` / `oracle` / `generic` (없으면 `postgresql`)                       |
| 시나리오    | `deadlock` / `lost-update` / `dirty-read` / `non-repeatable` / `phantom-read` / `mvcc` |

**규칙:**

- 마커 바로 아래 1~2줄 한국어 설명 필수
- 한 응답당 최대 3개
- 교육용 가상 시나리오임을 명시

**Few-shot 예시:**

```sql
--SQLViz: postgresql deadlock
-- T1
BEGIN;
UPDATE accounts
SET balance = balance - 100
WHERE id = 1;
-- T2
BEGIN;
UPDATE accounts
SET balance = balance - 100
WHERE id = 2;
```

→ PostgreSQL에서 두 트랜잭션이 서로의 행을 Lock 잡고 발생하는 데드락 시나리오입니다.

```sql
--SQLViz: mysql lost-update
UPDATE accounts
SET balance = balance + 300
WHERE id = 1;
```

→ MySQL의 READ COMMITTED 격리 수준에서 발생하는 Lost Update 현상입니다.

```sql
--SQLViz: oracle phantom-read
SELECT *
FROM accounts
WHERE balance > 500;
```

→ Oracle에서 REPEATABLE READ 격리 수준에서도 Phantom Read가 발생할 수 있는 예시입니다.

### ContentType별 추가 규칙

| ContentType | 추가 규칙 요약                                          |
|-------------|---------------------------------------------------|
| ALGORITHM   | 시간/공간 복잡도 표 (Best/Average/Worst), 단계별 설명, 핵심 줄 주석 |
| CODING      | 에러 재현 → 원인 분석 → 해결 → 리팩토링 Before/After            |
| CS          | 인프라 운영 관점, "왜 내부적으로 그렇게 동작하는지" 설명                 |
| CODE_REVIEW | 코드 품질/가독성/테스트 가능성 초점, AS-IS vs TO-BE 비교           |
| 기타          | 공통 규칙만 적용                                         |

---

## Layer 3 — 출력 프롬프트

### extraPrompt 조립 규칙 (`resolveExtraPrompt`)

1. `request.promptId` 없음 → `request.extraPrompt` 그대로 사용
2. `request.promptId` 있음 → `Prompt.content` 조회 + `usageCount` 증가
    - `extraPrompt` 도 있으면: `customContent + "\n" + extraPrompt`
    - `extraPrompt` 없으면: `customContent` 만 사용

### 평가 프롬프트 (`buildEvaluation`)

개선이 아닌 분석/평가 목적. DB 저장 안 함 — 프론트에만 전달.

출력 순서 (고정):

1. 🔥 한 줄 총평
2. 📊 점수 요약 표 (기준 6개)
3. 🚨 치명적인 문제 TOP 3
4. 🧠 핵심 개선 포인트
5. ✂️ 제거/축소 대상
6. 🏗 구조 개선안
7. 💎 전문가 한 줄
8. 추천 AI 개선 요청사항 (코드블록 안에 복붙 가능한 형태)

---

## 설계 결정 기록

| 결정                           | 이유                                                                                                                         |
|------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| 4단계를 단일 프롬프트로 통합             | 단계별 API 호출은 비용↑, 응답 불일치 위험. 단일 프롬프트가 더 일관된 출력                                                                              |
| `TAGS:` 본문 끝에 별도 줄           | `suggestedTags` 파싱을 위해 본문과 분리. `removeTagsLine()`으로 클라이언트에 숨김                                                              |
| `# 제목` 첫 줄 규칙                | `parseTitle()` + `removeFirstHeading()`으로 `suggestedTitle` 분리 저장                                                           |
| `--SQLViz:` 주석 형식            | `` ```sql visualize [dialect] `` 형식은 remark/rehype가 `language-sql`만 추출하고 뒤 토큰을 버려서 일반 코드블록으로 렌더링됨. SQL 주석은 모든 마크다운 환경에서 안전 |
| content 30자 미만 시 buildSimple | "데드락" 같은 짧은 단어는 개선이 아닌 설명 의도 — 4단계 파이프라인 적용 시 불필요한 구조 생성                                                                   |
