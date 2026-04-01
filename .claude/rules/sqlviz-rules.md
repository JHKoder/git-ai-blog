# SQLViz Rules (2026-04)

## 절대 준수해야 할 핵심 제약사항

- SQL 직접 실행은 **절대 금지** → 순수 Java in-memory 가상 시뮬레이션만 사용 (SimulationEngine)
- 모든 시뮬레이션은 교육용 가상 시나리오만 생성 (실제 DB 영향 없음)
- 보안: 실제 DB connection이나 외부 실행 없이 lock simulation만 허용

## 지원 시나리오 (7개)

DEADLOCK, DIRTY_READ, NON_REPEATABLE_READ, PHANTOM_READ, LOST_UPDATE, MVCC, LOCK_WAIT

격리 수준(READ_UNCOMMITTED ~ SERIALIZABLE)과 시나리오 매트릭스를 항상 고려하여 동작 구현

## 시각화 스타일 규칙 (토스스러운 느낌 + 일관성)

- 색상 체계 (5단계):
    - opNormal: 회색 (일반 진행)
    - opSuccess: 초록 (COMMIT 성공)
    - opWarning: 주황 (rollback, lost_update 등)
    - opDeadlock: 빨강 (데드락)
    - opBlocked: 보라 + blockedPulse 애니메이션 (락 대기)
- ExecutionFlow:
    - Transaction 노드: 파란 박스
    - Resource 노드 (Row): 회색 박스
    - Lock 성공: 초록 실선 + ✅
    - Lock 대기: 주황 점선 + ⌛
    - Deadlock: 빨간 실선 + 💀 + 중앙 deadlockBanner
- BLOCKED 상태: 자동 pause + lockZoneBadge / lockZoneBanner 표시
- 격리 수준 변경 시: 미리보기 모드 (POST /api/sqlviz/preview) 사용

## SQL 파싱 및 메타데이터 규칙

- STEP 주석: `-- STEP:n TX:T1` 또는 `-- STEP:n` (TX 없으면 T{step} fallback)
- DB dialect: `-- DB:[postgresql|mysql]` (기본: PostgreSQL)
- Locking read 지원: FOR UPDATE, FOR KEY SHARE, FOR SHARE, FOR NO KEY SHARE 파싱 → ParsedSql.lockType 사용
- COMMIT 후 pendingLocks 자동 재획득 처리 필수

## SQLViz 마커 규칙 (AI 프롬프트 연동)

- 형식: `--SQLViz: [dialect] [scenario]` (dialect 먼저)
- 예: `--SQLViz: postgresql deadlock`
- 한 응답당 최대 3개
- 마커 바로 아래에 한국어 설명 1~2줄 필수
- MarkdownRenderer에서 전처리하여 SqlVizMarker 컴포넌트로 렌더링

## ContentType별 추천 시나리오

- CS: DEADLOCK, MVCC, PHANTOM_READ
- CODING: LOST_UPDATE, DIRTY_READ
- TEST: NON_REPEATABLE_READ

## 알려진 한계 (항상 limitations 필드에 노출)

- FK 제약, Gap Lock, Advisory Lock 미지원
- 3개 이상 트랜잭션 데드락은 2-TX 기준으로만 시뮬레이션
- SSI(Serializable Snapshot Isolation) 전체 충돌 감지 미지원

## 구현 시 추가 지침

- SQLViz 관련 코드 수정 시 위 규칙 + java-spring-rules.md + react-rules.md를 동시에 준수
- UI 변경 시 토스 디자인 철학 (react-rules.md) 100% 반영
- 새로운 기능 추가 시 plan.md와 architecture.md를 즉시 업데이트