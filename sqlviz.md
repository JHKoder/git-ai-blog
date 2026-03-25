# SQLViz — 설계 / UX / AI 연동

> SQL 동시성 문제를 인터랙티브 타임라인/플로우로 시각화하고 Hashnode에 임베드하는 기능.
> **보안**: SQL 직접 실행 절대 금지 — 순수 Java 가상 시뮬레이션만 사용.
>
> 코드 레벨 상세 → [`backend/claude.md`](backend/claude.md) (API, 엔티티, 시뮬레이션 엔진 패키지)
> 프론트 레벨 상세 → [`frontend/CLAUDE.md`](frontend/CLAUDE.md) (컴포넌트, 타입, 스토어)

---

## 시뮬레이션 엔진 개선 설계 (피드백 2026-03-25)

> **피드백 결론:** "시각화 엔진으로는 충분히 좋은 수준인데, '사용자 SQL 기반으로 유연하게 동작하는 엔진'으로 보기엔 아직 한 단계 부족하다."

### 현재 상태 평가

| 항목 | 현재 | 비고 |
|------|:----:|------|
| 교육용 시각화 | ✅ | 강점, 건드리지 않음 |
| Timeline UX | ✅ | step + delay + 상태값 잘 설계됨 |
| 격리 수준 분기 | ✅ | 교육용으로 정확 |
| 실제 SQL 반영 | ✅ | JSQLParser 1단계 완료 |
| 락 대상 정확도 | ✅ | RowKey 시스템 2단계 완료 |
| 확장성 | ✅ | Virtual DB 3단계 완료 |

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

| 단계 | 내용 | 상태 | 파일 |
|------|------|:----:|------|
| 1단계 | SQL Parser Layer (JSQLParser) — `ParsedSql { type, table, columns, whereClause }` | ✅ | `SqlParser.java`, `ParsedSql.java` |
| 2단계 | RowKey 기반 Lock 시스템 — `"table:id"`, `Map<RowKey, LockInfo>` | ✅ | `RowKey.java` |
| 3단계 | Virtual DB in-memory 실행 엔진 — `VirtualRow`, `TransactionContext`, `VirtualDatabase` | ✅ | `VirtualRow.java`, `TransactionContext.java`, `VirtualDatabase.java` |
| 4단계 | Scenario → "힌트"로 축소 — 실제 steps는 SQL에서 동적 생성 | ✅ | `SqlVizSimulationEngine.java` |

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

| 종류 | 스타일 |
|------|--------|
| Transaction 노드 (T1, T2) | 파란 박스 |
| Resource 노드 (Row A, Row B) | 회색 박스 |
| Lock 획득 성공 엣지 | 초록 실선 + ✅ 아이콘 |
| Lock 대기 엣지 | 주황 점선 + ⌛ 아이콘 |
| Deadlock 엣지 | 빨간 실선 + 💀 아이콘 |
| Deadlock 배너 | 중앙 빨간 오버레이 배너 |

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

AI(Claude/Grok/GPT)는 텍스트만 반환한다. SQLViz 위젯 자체는 사용자가 `/sqlviz` 페이지에서 직접 생성하고 임베드 코드를 복사해서 게시글에 붙여넣는 흐름이다. AI의 역할은 **"여기에 SQLViz 위젯을 넣어라"는 마커(placeholder)를 본문에 심어주는 것**이다.

### SQLViz 마커 형식

````
```sql visualize [dialect] [옵션...]
-- SQL 코드
```
````

**dialect (필수, 첫 번째 위치):** `mysql` / `postgresql` / `oracle` / `generic`

**옵션 (최대 2개):** `deadlock`, `lost-update`, `dirty-read`, `non-repeatable`, `phantom-read`, `mvcc`, `locking`, `timeline`

### PromptBuilder 지시문 (구현 완료 — `PromptBuilder.java` 다이어그램 섹션 아래)

```
### SQL 시각화
- DB, 트랜잭션, 동시성, 격리 수준 관련 내용을 설명할 때는 아래 형식의 SQLViz 마커를 사용한다.
- 마커 형식: ```sql visualize [dialect] [옵션...]
- dialect는 항상 첫 번째 옵션으로 넣는다 (mysql / postgresql / oracle / generic).
- 마커 블록 바로 아래에 1~2줄의 한국어 설명을 반드시 추가한다.
- 한 응답당 SQLViz 마커는 최대 3개까지만 사용한다.
- 실제 DB 실행이 아닌 교육용 가상 시나리오만 생성한다.
```

### Few-shot 예시

**PostgreSQL 데드락:**
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

**MySQL Lost Update:**
````
```sql visualize mysql lost-update
UPDATE accounts SET balance = balance + 300 WHERE id = 1;
```
→ MySQL의 기본 READ COMMITTED 격리 수준에서 발생하는 Lost Update 현상입니다.
````

**Oracle Phantom Read:**
````
```sql visualize oracle phantom-read
SELECT * FROM accounts WHERE balance > 500;
```
→ Oracle에서 REPEATABLE READ 격리 수준에서도 Phantom Read가 발생할 수 있는 예시입니다.
````

### 사용자 흐름

```
1. 사용자가 게시글 AI 개선 요청
2. AI가 본문에 ```sql visualize [dialect] [옵션] 마커 삽입 + 한국어 설명 1~2줄
3. 사용자가 PostDetailPage/PostEditPage에서 마커 확인
4. 수동으로 /sqlviz 페이지 이동
5. 마커의 dialect/옵션/SQL을 입력창에 채워서 위젯 생성
6. 생성된 %%[sqlviz-{id}] 또는 iframe 코드를 본문의 마커 위치에 교체
```

### 지원 시나리오 × 격리 수준 매트릭스

| 시나리오 | READ_UNCOMMITTED | READ_COMMITTED | REPEATABLE_READ | SERIALIZABLE |
|----------|:---:|:---:|:---:|:---:|
| DEADLOCK | 항상 충돌 | 항상 충돌 | 항상 충돌 | 항상 충돌 |
| DIRTY_READ | 충돌 | 방지 | 방지 | 방지 |
| NON_REPEATABLE_READ | 충돌 | 충돌 | 방지 | 방지 |
| PHANTOM_READ | 충돌 | 충돌 | 충돌 | 방지 |
| LOST_UPDATE | 항상 충돌 | 항상 충돌 | 항상 충돌 | 항상 충돌 |
| MVCC | 충돌 없음 | 충돌 없음 | 충돌 없음 | 충돌 없음 |

### ContentType별 SQLViz 추천 시나리오

| ContentType | 추천 시나리오 |
|-------------|-------------|
| CS | DEADLOCK, MVCC, PHANTOM_READ — 개념 설명 시 |
| CODING | LOST_UPDATE, DIRTY_READ — 코드 버그 분석 시 |
| TEST | NON_REPEATABLE_READ — 트랜잭션 테스트 케이스 설명 시 |
| ALGORITHM | 해당 없음 |
| 기타 | 판단에 따라 선택적 사용 |
