# SQLViz — 설계 / UX / AI 연동

> SQL 동시성 문제를 인터랙티브 타임라인/플로우로 시각화하고 Hashnode에 임베드하는 기능.
> **보안**: SQL 직접 실행 절대 금지 — 순수 Java 가상 시뮬레이션만 사용.
>
> 코드 레벨 상세 → [`backend/claude.md`](backend/claude.md) (API, 엔티티, 시뮬레이션 엔진 패키지)
> 프론트 레벨 상세 → [`frontend/CLAUDE.md`](frontend/CLAUDE.md) (컴포넌트, 타입, 스토어)

---

## UX 이슈 기록

### 문제 1 — 왼쪽 패널 공간 부족 + 위젯 목록 페이징 ✅

**증상:** SQL 입력 칸이 좁고, 위젯 목록이 페이징 없이 전부 노출됨.

**해결:**

- `SqlVizPage.module.css` grid `420px` → `560px` 확대
- 위젯 목록 `(n/총)` 페이지네이션 + pageSize 10/20/30 버튼
- `sqlvizStore`에 `page`, `pageSize`, `totalPages`, `totalElements` 상태 추가
- 백엔드 `GetSqlVizListUseCase` + `SqlVizController` → `Page<SqlVizWidget>` + `SqlVizPageResponse` 반환
- `sqlvizApi.getList(page, size)` 파라미터화

### 문제 2 — STEP 주석 인터리빙 순서 오작동 ✅

**증상:** 사용자가 원하는 방식은 TX별 에디터 안에 `-- STEP:n` 주석으로 글로벌 실행 순서를 지정하는 패러다임이나, 현재 UI는 행(row) 단위로 TX를 지정하고 `-- STEP:n TX:id` 를
자동 조립하는 방식이라 결과가 맞지 않음.

**사용자 기대 패러다임:**

```
SQL (#1)[T1]  ← TX 에디터 단위
{
-- STEP:1   ← 전역 실행 순서
BEGIN ISOLATION LEVEL READ COMMITTED;
-- STEP:3
SELECT * FROM parent WHERE id = 1 FOR KEY SHARE;
}

SQL (#2)[T2]
{
-- STEP:2
BEGIN ISOLATION LEVEL READ COMMITTED;
-- STEP:4
DELETE FROM parent WHERE id = 1;
}
```

**현재 UI 결과:** 행 단위 조립 시 TX 전체가 하나의 STEP(`-- STEP:1 TX:T1`)으로 묶여 전달 → 인터리빙 순서 무시됨.

**해결:**

- UI 패러다임을 **TX별 에디터 카드** 방식으로 전환 (T1~T4, 최대 4개)
- 각 에디터 내부에 `-- STEP:n` 주석을 직접 작성 (TX ID는 에디터 단위로 지정)
- `buildSqls()`: `-- STEP:n` 주석에 `TX:id`가 없으면 에디터 단위 TX ID로 자동 매핑 (`-- STEP:n TX:T1` 변환)
- ISO 삽입 버튼: `-- STEP:n\nBEGIN ISOLATION LEVEL ...` 형식으로 삽입, `-- STEP:n`은 전체 에디터 최대 STEP+1로 자동 계산

### 문제 3 — ISO 버튼 클릭 시 중복 텍스트 ✅

**증상:** `UNCOMMITTED` / `COMMITTED` / `READ` / `SERIALIZABLE` 버튼 클릭 시 `BEGIN ISOLATION LEVEL ...` 텍스트가 중복으로 삽입됨.

**원인:** `insertIsoBegin()` 함수가 현재 에디터 내용과 무관하게 무조건 앞에 삽입함.

**해결:**

- `startsWithBegin()` 헬퍼: `--` 주석 줄 건너뛰고 첫 비주석 줄이 `BEGIN`이면 `true`
- `insertIsoBegin()` 시작 시 `startsWithBegin()` 체크 → `true`이면 삽입 스킵 + toast 안내
- 삽입 위치: `-- STEP:n\nBEGIN ISOLATION LEVEL ...` 형식, `n`은 전체 에디터 최대 STEP+1 자동 계산

---

## 시뮬레이션 엔진 개선 설계 (피드백 2026-03-25)

> **피드백 결론:** "시각화 엔진으로는 충분히 좋은 수준인데, '사용자 SQL 기반으로 유연하게 동작하는 엔진'으로 보기엔 아직 한 단계 부족하다."

### 이전 UX 피드백 요약 (구현 완료)

| 피드백                       | 해결책                                                          | 상태 |
|---------------------------|--------------------------------------------------------------|:--:|
| 사용법 안내 없음                 | `SqlVizHelpPanel` 4단계 가이드 (접기/펼치기)                           | ✅  |
| ISO Level 설정 어려움          | `BEGIN ISOLATION LEVEL ...` 삽입 버튼 (BEGIN 중복 방지 + STEP 자동)   | ✅  |
| 시나리오 자동 감지 불가             | 규칙 기반 패턴 매칭 + "추천 + 사용자 확인" 드롭다운 자동 선택                       | ✅  |
| 쿼리 실행 순서 오작동 (행 단위 조립)    | TX별 에디터 카드 + 에디터 내부 `-- STEP:n` 직접 작성 + TX 자동 매핑            | ✅  |
| 왼쪽 패널 좁음 + 위젯 목록 페이징 없음  | 패널 560px 확대 + `SqlVizPageResponse` + pageSize 10/20/30 UI   | ✅  |
| ISO 버튼 중복 텍스트              | `startsWithBegin()` 중복 방지                                    | ✅  |

### 현재 상태 평가

| 항목          | 현재 | 비고                       |
|-------------|:--:|--------------------------|
| 교육용 시각화     | ✅  | 강점, 건드리지 않음              |
| Timeline UX | ✅  | step + delay + 상태값 잘 설계됨 |
| 격리 수준 분기    | ✅  | 교육용으로 정확                 |
| 실제 SQL 반영   | ✅  | JSQLParser 1단계 완료        |
| 락 대상 정확도    | ✅  | RowKey 시스템 2단계 완료        |
| 확장성         | ✅  | Virtual DB 3단계 완료        |

### 문제 분석 (구현 완료)

#### 문제 1 — SQL이 텍스트로만 취급됨

```java
// 기존: 사용자가 어떤 SQL을 넣어도 시나리오 흐름이 동일
String t1Sql = sqls.get(0);  // 파싱 없이 텍스트 전달만
// detail 설명도 SQL 내용과 무관하게 하드코딩
```

**해결:** `SqlParser.java` + `ParsedSql` 레코드 도입 — SQL 타입/테이블/WHERE 파싱 후 detail 동적 생성.

#### 문제 2 — Row 개념 하드코딩

```java
// 기존: WHERE id=1이든 WHERE id=999이든 동일하게 "Row A", "Row B"로 표현
```

**해결:** `RowKey = "table:id"` — `"orders:1 vs orders:2"` 처럼 실제 의미 있는 락 대상 표시. 파싱 실패 시 "Row A/B" 폴백.

#### 문제 3 — 시나리오가 고정 스크립트

```java
// 기존: steps.add() 하드코딩 → SELECT * FROM orders 넣어도 DEADLOCK 흐름 고정 출력
```

**해결:** Scenario는 `switch` 진입점(힌트) 역할만. 실제 step 흐름은 SQL 파싱 결과에서 동적 생성.

### 구현 로드맵

| 단계  | 내용                                                                                 | 상태 | 파일                                                                   |
|-----|------------------------------------------------------------------------------------|:--:|----------------------------------------------------------------------|
| 1단계 | SQL Parser Layer (JSQLParser) — `ParsedSql { type, table, columns, whereClause }`  | ✅  | `SqlParser.java`, `ParsedSql.java`                                   |
| 2단계 | RowKey 기반 Lock 시스템 — `"table:id"`, `Map<RowKey, LockInfo>`                         | ✅  | `RowKey.java`                                                        |
| 3단계 | Virtual DB in-memory 실행 엔진 — `VirtualRow`, `TransactionContext`, `VirtualDatabase` | ✅  | `VirtualRow.java`, `TransactionContext.java`, `VirtualDatabase.java` |
| 4단계 | Scenario → "힌트"로 축소 — 실제 steps는 SQL에서 동적 생성                                        | ✅  | `SqlVizSimulationEngine.java`                                        |

> 라이브러리: JSQLParser 5.0 (Maven Central, Apache Calcite보다 경량, Spring Boot 호환)

---

## ExecutionFlow 시각화 스타일 가이드

> 목표: 락 획득 순서 불일치 패턴을 직관적이고 인터랙티브하게 시각화.

### 레이아웃 구조

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

### React Flow 노드/엣지 명세

| 종류                         | 스타일            |
|----------------------------|----------------|
| Transaction 노드 (T1, T2)    | 파란 박스          |
| Resource 노드 (Row A, Row B) | 회색 박스          |
| Lock 획득 성공 엣지              | 초록 실선 + ✅ 아이콘  |
| Lock 대기 엣지                 | 주황 점선 + ⌛ 아이콘  |
| Deadlock 엣지                | 빨간 실선 + 💀 아이콘 |
| Deadlock 배너                | 중앙 빨간 오버레이 배너  |

### 필수 인터랙션

- Timeline 슬라이더 — 시간 순서대로 단계별 재생/정지/이동
- 노드 클릭 → txId/operation/sql/result/detail 상세 툴팁 패널
- Isolation Level 토글 (READ_UNCOMMITTED / READ_COMMITTED / REPEATABLE_READ / SERIALIZABLE)

### 디자인 요구사항

- 다크 모드 최적화 (프로젝트 기본 다크 테마)
- Hashnode iframe 안에서도 responsive + 자동 height 조정
- 교육용으로 직관적이고 전문적인 톤

### 확장성

- DEADLOCK 패턴 기준으로 Lost Update / Dirty Read / Phantom Read / MVCC를 동일 컴포넌트 구조로 확장
- 각 시나리오별 "해결 방법 제안" 텍스트 표시 (예: "락 획득 순서를 통일하세요")

---

## AI 프롬프트 연동 가이드

> AI가 게시글을 작성/개선할 때 SQL 흐름이 필요한 부분을 SQLViz 위젯으로 유도하는 방법.
> **기능 추가 없이 기존 시스템만으로 연동 가능.**

### 개념

AI(Claude/Grok/GPT)는 텍스트만 반환한다. SQLViz 위젯 자체는 사용자가 `/sqlviz` 페이지에서 직접 생성하고 임베드 코드를 복사해서 게시글에 붙여넣는 흐름이다. AI의 역할은 **"여기에
SQLViz 위젯을 넣어라"는 마커(placeholder)를 본문에 심어주는 것**이다.

### SQLViz 마커 형식 (확정)

> 이전 `` ```sql visualize [dialect] `` 형식은 remark/rehype가 `language-sql`만 추출하고 뒤 토큰을 버려서 일반 코드블록으로 렌더링됨 → **`--SQLViz:`
주석 방식으로 변경 확정.**

```sql
--SQLViz: postgresql deadlock
-- SQL 코드
```

- `--SQLViz:` 이후 **첫 토큰 = dialect**, **두 번째 토큰 = 시나리오**
- 대소문자 무시 처리
- 일반 마크다운 엔진은 SQL 주석으로 무시, 커스텀 파서만 인식 → 모든 마크다운 환경에서 안전

**dialect:** `postgresql` / `mysql` / `oracle` / `generic` (없으면 `postgresql` 기본값 — `SqlParser.DEFAULT_DB`)

**시나리오:** `deadlock`, `lost-update`, `dirty-read`, `non-repeatable`, `phantom-read`, `mvcc`, `locking`, `timeline`

### PromptBuilder 지시문 (변경 필요 — `PromptBuilder.java` 다이어그램 섹션 아래)

> `--SQLViz:` 방식 확정으로 기존 지시문 교체 필요. 상세 → `prompt.md` (신규 파일 예정)

```
### SQL 시각화
- DB, 트랜잭션, 동시성, 격리 수준 관련 내용을 설명할 때는 --SQLViz: 마커를 SQL 블록 첫 줄에 사용한다.
- 마커 형식: --SQLViz: [dialect] [시나리오]
- dialect는 항상 첫 번째 위치 (postgresql / mysql / oracle / generic).
- 마커 블록 바로 아래에 1~2줄의 한국어 설명을 반드시 추가한다.
- 한 응답당 SQLViz 마커는 최대 3개까지만 사용한다.
- 실제 DB 실행이 아닌 교육용 가상 시나리오만 생성한다.
```

### Few-shot 예시

**PostgreSQL 데드락:**

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

**MySQL Lost Update:**

```sql
--SQLViz: mysql lost-update
UPDATE accounts
SET balance = balance + 300
WHERE id = 1;
```

→ MySQL의 기본 READ COMMITTED 격리 수준에서 발생하는 Lost Update 현상입니다.

**Oracle Phantom Read:**

```sql
--SQLViz: oracle phantom-read
SELECT *
FROM accounts
WHERE balance > 500;
```

→ Oracle에서 REPEATABLE READ 격리 수준에서도 Phantom Read가 발생할 수 있는 예시입니다.

### 사용자 흐름

```
1. 사용자가 게시글 AI 개선 요청
2. AI가 본문에 --SQLViz: [dialect] [시나리오] 마커 삽입 + 한국어 설명 1~2줄
3. 사용자가 PostDetailPage/PostEditPage에서 마커 확인
4. 수동으로 /sqlviz 페이지 이동
5. 마커의 dialect/시나리오/SQL을 입력창에 채워서 위젯 생성
6. 생성된 embed URL을 본문의 마커 위치에 교체
```

### 지원 시나리오 × 격리 수준 매트릭스

| 시나리오                | READ_UNCOMMITTED | READ_COMMITTED | REPEATABLE_READ | SERIALIZABLE |
|---------------------|:----------------:|:--------------:|:---------------:|:------------:|
| DEADLOCK            |      항상 충돌       |     항상 충돌      |      항상 충돌      |    항상 충돌     |
| DIRTY_READ          |        충돌        |       방지       |       방지        |      방지      |
| NON_REPEATABLE_READ |        충돌        |       충돌       |       방지        |      방지      |
| PHANTOM_READ        |        충돌        |       충돌       |       충돌        |      방지      |
| LOST_UPDATE         |      항상 충돌       |     항상 충돌      |      항상 충돌      |    항상 충돌     |
| MVCC                |      충돌 없음       |     충돌 없음      |      충돌 없음      |    충돌 없음     |

### ContentType별 SQLViz 추천 시나리오

| ContentType | 추천 시나리오                                 |
|-------------|-----------------------------------------|
| CS          | DEADLOCK, MVCC, PHANTOM_READ — 개념 설명 시  |
| CODING      | LOST_UPDATE, DIRTY_READ — 코드 버그 분석 시    |
| TEST        | NON_REPEATABLE_READ — 트랜잭션 테스트 케이스 설명 시 |
| ALGORITHM   | 해당 없음                                   |
| 기타          | 판단에 따라 선택적 사용                           |

---

## 시뮬레이션 엔진 v2 설계

> 근본 문제: `VirtualDatabase` / `TransactionContext` / Lock 인프라는 갖춰져 있지만
> `SqlVizSimulationEngine` 빌더들이 이를 전혀 호출하지 않고 step을 하드코딩으로 생성 중.

### 핵심 문제 5개

| # | 문제                                                   | 현재 코드 위치                                                         |
|---|------------------------------------------------------|------------------------------------------------------------------|
| 1 | FOR KEY SHARE 잠금 무시 — T2 DELETE가 block 없이 success    | `buildDeadlock()` step 하드코딩                                      |
| 2 | FK 제약 미반영 — parent DELETE / child INSERT 둘 다 success | `VirtualDatabase`에 FK 개념 없음                                      |
| 3 | Non-Repeatable Read 판정: 입력 SQL이 시나리오와 맞지 않으면 잘못 판정   | 시나리오별 입력 SQL 타입 검증 없음                                            |
| 4 | 트랜잭션 interleaving 순서 하드코딩                            | 모든 빌더가 T1→T2 고정                                                  |
| 5 | READ COMMITTED = lock 없음처럼 동작                        | `buildDirtyRead()` 격리 수준별 분기만 있고 실제 `VirtualDatabase.read()` 미호출 |

### 설계 방향

#### 1. DbType 지원

- `DbType` enum: `POSTGRESQL`, `MYSQL`, `ORACLE`, `GENERIC`
- `SqlParser.java` 상단 상수로 기본값 선언 (개발자가 가장 찾기 쉬운 위치):
  ```java
  /** -- DB:[...] 주석이 없을 때 적용. 변경 시 이 상수만 수정. */
  public static final DbType DEFAULT_DB = DbType.POSTGRESQL;
  ```
- SQL 입력에 `-- DB:[mysql]` 주석이 있으면 그 값 사용, 없으면 `DEFAULT_DB` 적용
- `--`로 시작하는 줄은 JSQLParser에 넘기기 전에 raw 스캔으로 메타데이터만 추출, 나머지만 JSQLParser 전달

#### 2. DB별 락 모델

| DbType     | 지원 락 타입                                                          |
|------------|------------------------------------------------------------------|
| PostgreSQL | FOR KEY SHARE / FOR SHARE / FOR NO KEY UPDATE / FOR UPDATE (4단계) |
| MySQL      | FOR UPDATE / LOCK IN SHARE MODE (2단계), gap lock 기본 활성            |

- `VirtualDatabase.acquireLock(tx, key, lockType)` — LockType 파라미터 추가
- Table Lock: table 이름 단위 별도 `tableLockOwner` 맵
- Record Lock: 기존 `RowKey` 기반 (이미 구조 있음)
- Advisory Lock: `advisoryLockOwner: Map<Long, String>` 별도 맵

#### 3. 데드락 / 락 대기 자동 감지

```
waitFor: Map<String, String>  // txId → 기다리는 상대 txId

acquireLock() 실패
  → waitFor.put(requester, owner)
  → detectDeadlock() 호출 (DFS 사이클 탐색)
  → 사이클 있으면: SimulationStep.status = "deadlock"
  → 사이클 없으면: SimulationStep.status = "blocked"
```

빌더가 `acquireLock()` 반환 결과만 보고 blocked/deadlock 자동 구분 가능.

#### 4. `-- STEP:[n] TX:[id]` 인터리빙 런타임

- 사용자가 SQL별로 실행 순서와 트랜잭션을 직접 지정:
  ```sql
  -- STEP:1 TX:T1
  BEGIN ISOLATION LEVEL READ COMMITTED;
  -- STEP:2 TX:T2
  BEGIN;
  -- STEP:3 TX:T1
  SELECT * FROM orders WHERE id = 1 FOR UPDATE;
  -- STEP:4 TX:T2
  DELETE FROM orders WHERE id = 1;
  -- STEP:5 TX:T1
  COMMIT;
  ```
- `sqls: List<String>` 구조 유지 (Request 변경 없음) — 메타데이터는 주석 안에 포함
- SqlParser가 STEP/TX 추출 → 엔진이 STEP 순서대로 정렬 후 VirtualDatabase 실제 호출
- `BEGIN ISOLATION LEVEL ...` 파싱 → `ParsedSql`에 `isolationLevel` 추가 → `TransactionContext` 생성자에 전달
- `VirtualDatabase.read()`에서 `tx.getIsolationLevel()`에 따라 가시성 분기:
    - READ COMMITTED → 커밋된 최신 값
    - REPEATABLE READ → 트랜잭션 시작 시점 스냅샷 고정

#### 5. 한계 명시 UI

- `SimulationResult`에 `limitations: List<String>` 필드 추가
  → 프론트 결과 하단 회색 작은 텍스트로 표시 (핵심 시각화 방해 안 함)
  → 예: "SSI 충돌은 시뮬레이터 범위 밖입니다. 실제 DB에서 확인하세요."
- `SimulationStep`에 `warning: String` (nullable) 필드 추가
  → race condition 가능 구간 표시: 프론트에서 ⚠️ 아이콘 + 툴팁

#### 6. Hashnode 발행 연동

- 직접 embed 불가 (Hashnode iFrame/스크립트 차단)
- `GET /api/embed/sqlviz/{id}` 공개 URL 이미 구현됨 — 로그인 불필요
- `git-ai-blog.kr/embed/sqlviz/{id}` 링크를 글에 삽입하는 방식 사용
- `ConcurrencyTimeline`이 이미 step별 애니메이션 지원

### 구현 순서 (FK 제외)

| 단계 | 내용                                                                  |
|----|---------------------------------------------------------------------|
| 1  | `DbType` enum + `SqlParser.DEFAULT_DB` 상수 선언                        |
| 2  | `SqlParser.parse()`에서 `-- DB:[...]` / `-- STEP:[n] TX:[id]` 주석 추출   |
| 3  | `VirtualDatabase`에 `LockType` + `waitFor` 맵 + `detectDeadlock()` 추가 |
| 4  | `acquireLock()` 실패 시 waitFor 기록 → blocked/deadlock 자동 반환            |
| 5  | `ParsedSql`에 `isolationLevel` / `dbType` / `stepMeta` 필드 추가         |
| 6  | `TransactionContext` 생성자에 `IsolationLevel` 추가 + `read()` 가시성 분기     |
| 7  | `SqlVizSimulationEngine` 빌더들을 VirtualDatabase 실제 호출 방식으로 교체         |
| 8  | `SimulationResult.limitations` + `SimulationStep.warning` 필드 추가     |
| 9  | dbType에 따라 lock 충돌 규칙 분기 (PG 4단계 vs MySQL 2단계)                      |

---

## 이슈 해결 기록

### 마커 렌더링 미작동 → 해결 완료 (2026-03-25)

**현상**: AI가 생성한 `` ```sql visualize mysql deadlock `` 마커가 `PostDetailPage`에서 SQLViz 위젯으로 렌더링되지 않고 코드 블록 원문 출력.

**재현 케이스** (`https://jhkoder.hashnode.dev/ai-api-test-db-1` 기준):

- `` ```sql visualize mysql deadlock `` → 원문 출력
- `` ```sql visualize mysql lost-update `` → `` ``` `` 닫힘 태그 누락 + 다음 코드블록과 충돌

**원인 분석**:

1. `MarkdownRenderer.tsx`의 `code` 컴포넌트가 `className`에서 첫 단어만 추출
   ```
   language-sql visualize mysql deadlock
         ↓ 정규식 /language-(\w+)/ 매칭
   "sql"  ← dialect/옵션 전부 소실
   ```
2. `sql` 언어로만 처리되어 SQLViz 분기 로직이 없음 → 일반 코드블록 출력
3. `` ```sql visualize ... ``` `` 뒤에 다른 코드블록이 이어지면 remark 파서가 닫힘 태그를 잘못 처리하여 블록 병합

**해결 방향**:

- `MarkdownRenderer`에서 `className` 전체 파싱: `language-sql` + 나머지 토큰에서 `visualize`, dialect, 옵션 추출
- 추출된 dialect + 옵션으로 `SqlVizInlineWidget` 컴포넌트 렌더링 (시뮬레이션 API 호출 또는 직접 파싱)
- 또는 remark 커스텀 플러그인으로 마커를 전처리 후 ReactMarkdown에 전달 → 블록 충돌 방지
- 상세 → [`frontend/CLAUDE.md`](frontend/CLAUDE.md) 미해결 이슈 #1, #3
