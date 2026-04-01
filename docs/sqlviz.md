# SQLViz — 설계 / UX / AI 연동

> SQL 동시성 문제를 인터랙티브 타임라인/플로우로 시각화하고 Hashnode에 임베드하는 기능.
> **보안**: SQL 직접 실행 절대 금지 — 순수 Java 가상 시뮬레이션만 사용.
>
> 코드 레벨 상세 → [`backend/claude.md`](../backend/claude.md) (API, 엔티티, 시뮬레이션 엔진 패키지)
> 프론트 레벨 상세 → [`frontend/CLAUDE.md`](frontend/CLAUDE.md) (컴포넌트, 타입, 스토어)

---

## 미구현 / 예정 기능

| 기능                      | 상태 | 설명                                                                                                                         |
|-------------------------|:--:|----------------------------------------------------------------------------------------------------------------------------|
| BLOCKED 구간 단계별 수동 진행 버튼 | ✅  | 재생 일시정지(BLOCKED) 상태에서 LOCK ZONE 배지 옆에 "▶ TxN 다음 단계" 버튼 표시 → 클릭 시 블로킹하는 TX 다음 스텝으로 이동 → 커밋 스텝 진행 후 `▶ 재생` 누르면 락 해소된 채 계속 진행 |

---

## 구현 현황 요약

| 기능                                                                                                    | 상태 | 비고                                               |
|-------------------------------------------------------------------------------------------------------|:--:|--------------------------------------------------|
| 시나리오 7개 (DEADLOCK / DIRTY_READ / NON_REPEATABLE_READ / PHANTOM_READ / LOST_UPDATE / MVCC / LOCK_WAIT) | ✅  |                                                  |
| `-- STEP:n TX:id` 인터리빙 런타임                                                                            | ✅  |                                                  |
| `-- STEP:n` (TX 없음) 지원                                                                                | ✅  | null txId fallback `T{step}`                     |
| `-- DB:[mysql\|postgresql]` 파싱 + DbType                                                               | ✅  |                                                  |
| FOR KEY SHARE / FOR UPDATE locking read 파싱                                                            | ✅  | regex 방식 (JSQLParser 5.x API 미지원)                |
| SELECT locking read → acquireLock() + pendingLocks 자동 재획득                                             | ✅  | T1 커밋 후 T2 대기 자동 해소                              |
| DB CHECK 제약 자동 마이그레이션                                                                                 | ✅  | `DbMigrationRunner`                              |
| `SimulationResult.limitations` + `SimulationStep.warning`                                             | ✅  |                                                  |
| TX별 에디터 카드 + buildSqls() STEP 분리                                                                      | ✅  |                                                  |
| 시나리오 자동 감지 + 툴팁                                                                                       | ✅  |                                                  |
| 사용법 패널 + 예시 2개                                                                                        | ✅  |                                                  |
| 위젯 목록 페이지네이션                                                                                          | ✅  | pageSize 10/20/30                                |
| `--SQLViz:` 마커 렌더링                                                                                    | ✅  |                                                  |
| 색상 규칙 통일 (5단계)                                                                                        | ✅  | `resultColor()` + CSS 변수 5단계                     |
| 실선/점선/굵은 선 구분                                                                                         | ✅  | `strokeDasharray` + `strokeWidth`                |
| CONFLICT 중앙 레이어                                                                                       | ✅  | `lockZoneBadge` / `lockZoneBanner`               |
| 재생 애니메이션 BLOCKED 일시정지                                                                                 | ✅  | `blockedPulse` CSS 애니메이션                         |
| 격리 수준 모드 스위치 (즉시 재시뮬레이션)                                                                              | ✅  | `POST /api/sqlviz/preview`                       |
| embed 페이지 다크모드                                                                                        | ✅  | `prefers-color-scheme` + `?theme=dark` URL param |
| SQL 목록 TX별 컬럼 표시                                                                                      | ✅  | `SqlTxColumns` 컴포넌트                              |
| BLOCKED 구간 수동 진행 버튼                                                                                   | ✅  | LOCK ZONE 배지 옆 `▶ TxN 다음 단계` 버튼                  |

---

## 시각화 개선 — 완료 현황

> 피드백 요약: "타임라인이 명확하지 않고, 색 의미가 애매하고, CONFLICT 원인이 직관적으로 안 보임."

### 1. 색상 규칙 통일 ✅

`result` 값 기준 5단계 색상 체계. `getResultColorClass()` 함수 + CSS 클래스 분리.

| 상태      | 색상 | CSS 클래스       | result / operation 조건                                                 |
|---------|----|---------------|-----------------------------------------------------------------------|
| 일반 진행   | 회색 | `.opNormal`   | 기본 (SELECT, INSERT, UPDATE 등 `success`)                               |
| 커밋 완료   | 초록 | `.opSuccess`  | `operation === 'COMMIT' && result === 'success'`                      |
| 롤백/이상현상 | 주황 | `.opWarning`  | `result: rollback, dirty_value, non_repeatable, phantom, lost_update` |
| 데드락     | 빨강 | `.opDeadlock` | `result === 'deadlock'`                                               |
| 잠금 대기   | 보라 | `.opBlocked`  | `result === 'blocked'` — 펄스 애니메이션(`blockedPulse`) 포함                  |

`ExecutionFlow` 노드/엣지도 동일 5단계 기준:

- 데드락: 빨강 굵은 실선 (`strokeWidth: 3`)
- BLOCKED: 보라 점선 (`strokeDasharray: '6,4'`, `strokeWidth: 2`)
- COMMIT: 초록 굵은 실선 (`strokeWidth: 2`)
- ROLLBACK: 주황 점선 (`strokeDasharray: '4,3'`)
- 일반: 회색 가는 실선

---

### 2. CONFLICT 중앙 레이어 ✅

BLOCKED/DEADLOCK 시나리오 분리:

- `lockZoneBadge` (ConcurrencyTimeline) — 현재 스텝이 `blocked`일 때 타임라인 위에 보라 배지 + 펄스
- `lockZoneBanner` (ExecutionFlow) — `conflictType === 'LOCK_WAIT'`일 때 보라 중앙 오버레이
- `deadlockBanner` (ExecutionFlow) — `conflictType === 'DEADLOCK'`일 때 빨강 중앙 오버레이

---

### 3. 재생 애니메이션 BLOCKED 일시정지 ✅

- BLOCKED 스텝 도달 시 `setPlaying(false)` 자동 호출
- `.opBlocked` `blockedPulse` 애니메이션으로 시각 강조 (50% opacity 점멸)
- "▶ 재생" 재클릭하면 다음 스텝부터 재개

---

### 4. 격리 수준 모드 스위치 ✅

- 백엔드: `POST /api/sqlviz/preview` — DB 저장 없이 `SimulationResult`만 반환
- 프론트: 위젯 상세 뷰 상단에 격리 수준 토글 버튼 (READ_UNCOMMITTED / READ_COMMITTED / REPEATABLE_READ / SERIALIZABLE)
- 저장된 격리 수준: ✓ 표시 / 다른 수준 클릭 → preview API 호출 → "미리보기 모드" 배지 표시
- 원래 수준 재클릭 시 미리보기 해제, 저장된 시뮬레이션으로 복원

---

### 5. embed 페이지 다크모드 ✅

- `prefers-color-scheme: dark` 미디어 쿼리 자동 감지 + 변경 이벤트 실시간 반영
- URL `?theme=dark` / `?theme=light` param으로 부모 페이지에서 강제 지정 가능
- 모든 텍스트/배경/배지에 `var(--text)`, `var(--bg)`, `var(--surface)` CSS 변수 적용

---

### 6. SQL 목록 TX별 컬럼 표시 ✅

**피드백**: embed/위젯 상세의 "SQL 목록"이 단순 번호 나열이라 TX 흐름이 안 보임.
`widget.sqls`는 `buildSqls()`가 에디터 단위(TX 기준)로 조립하므로 T1 묶음, T2 묶음이 연속 나열되어
STEP 번호(1, 3, 5 / 2, 4, 6)와 표시 순서(#1~#3 T1, #4~#6 T2)가 달라 혼란스럽게 보임.

**구현**: `SqlTxColumns` 신규 컴포넌트 (`src/components/SqlTxColumns/`)

- `-- STEP:n TX:Tx` 주석 파싱 → TX별로 그루핑 → 각 컬럼 STEP 오름차순 정렬
- STEP 주석 없는 SQL은 "기타" 컬럼으로 분류
- `-- STEP/TX` 주석 줄은 렌더링에서 제거, 실제 SQL만 표시
- TX 컬럼 수에 따라 `grid-template-columns` 동적 계산 (TX 1개면 단일 컬럼)

```
T1                                T2
─────────────────────────────     ─────────────────────────────
STEP 1                            STEP 2
BEGIN ISOLATION LEVEL ...         BEGIN ISOLATION LEVEL ...

STEP 3                            STEP 4
SELECT * FROM ... FOR UPDATE;     SELECT * FROM ... FOR SHARE;

STEP 5                            STEP 6
COMMIT;                           COMMIT;
```

**적용 위치**:

- `SqlVizEmbedPage` — "SQL 목록" 섹션 교체
- `SqlVizPage` 위젯 상세 — "SQL 목록" 탭 신규 추가 (타임라인 / 실행 흐름 / **SQL 목록** / 임베드 코드)

---

## 시뮬레이션 엔진

### 지원 시나리오

| 시나리오                  | 설명               | 핵심 흐름                                                            |
|-----------------------|------------------|------------------------------------------------------------------|
| `DEADLOCK`            | T1↔T2 순환 잠금      | T1이 rowA, T2가 rowB 잠금 후 서로 교차 요청 → 데드락 감지, T2 victim 롤백          |
| `DIRTY_READ`          | 미커밋 값 읽기         | T1 UPDATE 미커밋 → T2 READ_UNCOMMITTED로 읽기 → T1 롤백 → T2가 읽은 값 유령화   |
| `NON_REPEATABLE_READ` | 같은 쿼리 다른 결과      | T1 첫 번째 SELECT → T2 UPDATE + COMMIT → T1 두 번째 SELECT 결과 달라짐      |
| `PHANTOM_READ`        | 범위 재조회 시 새 행 등장  | T1 범위 SELECT → T2 INSERT + COMMIT → T1 재조회 시 새 행 보임              |
| `LOST_UPDATE`         | 변경이 덮어써짐         | T1/T2 동시 읽기 후 각자 UPDATE → T1 변경이 T2에 의해 소실                       |
| `MVCC`                | 잠금 없는 스냅샷 읽기     | T1 REPEATABLE READ 스냅샷 고정 → T2 UPDATE + COMMIT → T1은 여전히 이전 값 읽음 |
| `LOCK_WAIT`           | 단순 락 대기 (데드락 없음) | T1 FOR UPDATE 미커밋 → T2 동일 행 잠금 BLOCKED → T1 커밋 → T2 잠금 획득        |

### 지원 시나리오 × 격리 수준 매트릭스

| 시나리오                | READ_UNCOMMITTED | READ_COMMITTED | REPEATABLE_READ | SERIALIZABLE |
|---------------------|:----------------:|:--------------:|:---------------:|:------------:|
| DEADLOCK            |      항상 충돌       |     항상 충돌      |      항상 충돌      |    항상 충돌     |
| DIRTY_READ          |        충돌        |       방지       |       방지        |      방지      |
| NON_REPEATABLE_READ |        충돌        |       충돌       |       방지        |      방지      |
| PHANTOM_READ        |        충돌        |       충돌       |       충돌        |      방지      |
| LOST_UPDATE         |      항상 충돌       |     항상 충돌      |      항상 충돌      |    항상 충돌     |
| MVCC                |      충돌 없음       |     충돌 없음      |      충돌 없음      |    충돌 없음     |
| LOCK_WAIT           |      항상 충돌       |     항상 충돌      |      항상 충돌      |    항상 충돌     |

### locking read 지원

`SELECT ... FOR UPDATE / FOR KEY SHARE / FOR SHARE / FOR NO KEY UPDATE` 파싱 후 `acquireLock()` 실제 호출.

- `ParsedSql.lockType` 필드 — SELECT 파싱 시 locking read 타입 추출
- `runInterleaved()` SELECT case: `lockType != null`이면 `acquireLock()` 호출, BLOCKED 시 `pendingLocks` 기록
- COMMIT 후 `pendingLocks` 자동 재획득 — T1 커밋 → T2 대기 해소 → T2 success step 자동 생성

**PostgreSQL 락 충돌 규칙 (LockType.conflictsWith)**:

|                   | FOR_KEY_SHARE | FOR_SHARE | FOR_NO_KEY_UPDATE | FOR_UPDATE |
|-------------------|:-------------:|:---------:|:-----------------:|:----------:|
| FOR_KEY_SHARE     |     ✅ 공존      |   ✅ 공존    |       ❌ 충돌        |    ❌ 충돌    |
| FOR_SHARE         |     ✅ 공존      |   ✅ 공존    |       ❌ 충돌        |    ❌ 충돌    |
| FOR_NO_KEY_UPDATE |     ❌ 충돌      |   ❌ 충돌    |       ❌ 충돌        |    ❌ 충돌    |
| FOR_UPDATE        |     ❌ 충돌      |   ❌ 충돌    |       ❌ 충돌        |    ❌ 충돌    |

### 알려진 한계 (limitations 필드로 UI에 노출)

- FK 제약, Advisory Lock, Gap Lock 미지원
- SSI(Serializable Snapshot Isolation) 충돌 감지 범위 밖
- 3개 이상 트랜잭션 데드락은 2-TX 기준으로만 시뮬레이션

---

## SQL 주석 메타데이터

### STEP 인터리빙 주석

```sql
-- STEP:1 TX:T1          ← 실행 순서 1번, 트랜잭션 T1
-- STEP:2 TX:T2          ← 실행 순서 2번, 트랜잭션 T2
-- STEP:3                ← TX 없음 — 에디터 단위 TX 자동 매핑 (buildSqls()가 처리)
```

- 대괄호 선택적: `-- STEP:[1] TX:[T1]` / `-- STEP:1 TX:T1` 둘 다 인식
- `TX:` 부분 없으면 `StepMeta(step, null)` → `runInterleaved()`에서 `T{step}` fallback

### DB 방언 주석

```sql
-- DB:[mysql]            ← MySQL 락 모델 적용
-- DB:[postgresql]       ← PostgreSQL 락 모델 적용 (기본값)
```

기본값: `SqlParser.DEFAULT_DB = DbType.POSTGRESQL`

---

## AI 프롬프트 연동

### SQLViz 마커 형식

```sql
--SQLViz: postgresql deadlock
-- SQL 코드
```

- `--SQLViz:` 이후 첫 토큰 = dialect, 두 번째 토큰 = 시나리오
- 대소문자 무시 처리
- 일반 마크다운 엔진은 SQL 주석으로 무시, 커스텀 파서만 인식 → 안전

**dialect**: `postgresql` / `mysql` / `oracle` / `generic`

**시나리오**: `deadlock`, `lost-update`, `dirty-read`, `non-repeatable`, `phantom-read`, `mvcc`, `locking`, `timeline`

### PromptBuilder 지시문

```
### SQL 시각화
- DB, 트랜잭션, 동시성, 격리 수준 관련 내용을 설명할 때는 --SQLViz: 마커를 SQL 블록 첫 줄에 사용한다.
- 마커 형식: --SQLViz: [dialect] [시나리오]
- dialect는 항상 첫 번째 위치 (postgresql / mysql / oracle / generic).
- 마커 블록 바로 아래에 1~2줄의 한국어 설명을 반드시 추가한다.
- 한 응답당 SQLViz 마커는 최대 3개까지만 사용한다.
- 실제 DB 실행이 아닌 교육용 가상 시나리오만 생성한다.
```

### ContentType별 추천 시나리오

| ContentType | 추천 시나리오                      |
|-------------|------------------------------|
| CS          | DEADLOCK, MVCC, PHANTOM_READ |
| CODING      | LOST_UPDATE, DIRTY_READ      |
| TEST        | NON_REPEATABLE_READ          |
| ALGORITHM   | 해당 없음                        |

---

## ExecutionFlow 시각화 스타일 가이드

### 레이아웃 구조

```
상단: Transaction 1 | Row A (Lock) | Row B (Lock) | Transaction 2  ← 4개 컬럼 헤더
      세로 시간 흐름 (위 → 아래)
      T1이 Row A Lock 획득 (초록 체크)
      T2가 Row B Lock 획득 (초록 체크)
      T1이 Row B 요청 → 대기 (모래시계)
      T2가 Row A 요청 → 대기 (모래시계)
      ──── DEADLOCK 발생 배너 ────
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

---

## UX 이슈 기록

### 문제 1 — 왼쪽 패널 공간 부족 + 위젯 목록 페이징 ✅

- `SqlVizPage.module.css` grid `420px` → `560px` 확대
- 위젯 목록 `(n/총)` 페이지네이션 + pageSize 10/20/30 버튼
- 백엔드 `Page<SqlVizWidget>` + `SqlVizPageResponse` 반환

### 문제 2 — STEP 주석 인터리빙 순서 오작동 ✅

- UI 패러다임을 TX별 에디터 카드 방식으로 전환 (T1~T4, 최대 4개)
- `buildSqls()`: `-- STEP:n` 구분자 기준 에디터 내용 분할, TX 없으면 에디터 단위 TX 자동 매핑
- `SqlParser.STEP_COMMENT` 패턴: 대괄호 선택적 (`\[?(\d+)\]?`)

### 문제 3 — ISO 버튼 중복 텍스트 ✅

- `startsWithBegin()` 헬퍼: `--` 주석 줄 건너뛰고 첫 비주석 줄이 `BEGIN`이면 삽입 스킵
- 삽입 형식: `-- STEP:n\nBEGIN ISOLATION LEVEL ...`, `n`은 최대 STEP+1 자동 계산

### 문제 4 — 에디터 내 STEP별 SQL 분리 안 됨 + 타임라인 주석 노출 ✅

- `buildSqls()` STEP 구분자 기준 분할 + `SqlParser.STEP_COMMENT` 대괄호 선택적 패턴

### 문제 5 — TX 정보 없는 STEP 주석 미지원 ✅

- `SqlParser.STEP_ONLY` 패턴 추가 — `-- STEP:n` (TX 없음) 인식, `StepMeta(step, null)` 반환
- `runInterleaved()` null txId → `T{step}` fallback

---

## 이슈 해결 기록

### DB CHECK 제약 위반 (`sqlviz_widgets_scenario_check`) ✅

- **원인**: `LOCK_WAIT` enum 추가 시 PostgreSQL CHECK 제약이 새 값을 거부
- **해결**: `DbMigrationRunner`에 `scenario_check` / `isolation_level_check` DROP 추가 — 앱 재시작 시 자동 해소
- **정책**: Hibernate `ddl-auto: update`는 CHECK 제약을 재생성하지 않으므로 enum 추가 시 이 패턴 반복 사용

### FOR KEY SHARE locking read 미지원 ✅

- **원인**: `runInterleaved()` SELECT case가 `db.read()`만 호출, 잠금 없이 통과
- **해결**: `ParsedSql.lockType` 추가, `SqlParser` regex locking read 추출, `pendingLocks` 자동 재획득

### 마커 렌더링 미작동 ✅

- **원인**: `` ```sql visualize mysql deadlock `` 형식을 remark가 `language-sql`만 추출하고 뒤 토큰 버림
- **해결**: `--SQLViz:` 주석 방식으로 변경 확정. `MarkdownRenderer` 전처리로 플레이스홀더 치환 → `SqlVizMarker` 컴포넌트 렌더링

### SQLViz 위젯 중복 생성 ✅

- **원인**: AI 개선 시 동일 SQL + 시나리오로 매 요청마다 새 위젯 생성
- **해결**: `memberId + sqlsJson + scenario` 조합 중복 검사 후 기존 위젯 재사용

### `simulationData` vs `simulation` 필드명 불일치 ✅

- **원인**: 백엔드 `SqlVizResponse.simulationData` vs 프론트 `SqlVizWidget.simulation` 불일치
- **해결**: 프론트 타입 `simulation`으로 통일
