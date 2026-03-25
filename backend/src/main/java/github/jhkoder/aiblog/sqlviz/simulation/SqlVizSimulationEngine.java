package github.jhkoder.aiblog.sqlviz.simulation;

import github.jhkoder.aiblog.sqlviz.domain.IsolationLevel;
import github.jhkoder.aiblog.sqlviz.domain.SqlVizScenario;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 직접 실행 없이 시나리오 타입 + 격리 수준 조합으로 가상 Timeline 이벤트를 생성한다.
 *
 * ─── 유연성 제약사항 (알고 쓸 것) ───────────────────────────────────────────
 *
 * [1] SQL 입력은 t1Sql(첫 번째), t2Sql(두 번째) 2개만 사용됨.
 *     sqls 리스트에 3개 이상 전달해도 3번째부터는 모든 시나리오에서 무시된다.
 *     → 사용자가 여러 SQL을 입력했을 때 "내 SQL이 다 반영됐나?" 오해 가능.
 *
 * [2] DEADLOCK / LOST_UPDATE / MVCC 시나리오는 IsolationLevel을 사용하지 않는다.
 *     어떤 격리 수준을 선택해도 동일한 결과가 반환된다.
 *     → 프론트에서 격리 수준 선택 UI를 이 3개 시나리오에선 비활성화하거나 안내 문구를 표시하는 게 좋다.
 *
 * [3] step의 detail 설명이 "balance", "orders" 같은 고정 도메인 예시를 하드코딩하고 있다.
 *     사용자가 실제로 어떤 SQL을 입력하든 설명 문구는 바뀌지 않는다.
 *     → 시각화는 정확하지만, 설명 텍스트는 입력 SQL과 무관한 예시임을 인지할 것.
 *
 * [4] step 내 sql 필드에 t1Sql/t2Sql 대신 고정 문자열이 들어가는 스텝이 일부 존재한다.
 *     예: DEADLOCK의 "Row B 잠금 획득", LOCK_WAIT의 "SELECT ... FOR UPDATE (Row B)"
 *     → 이 스텝들은 사용자 입력 SQL이 아닌 시나리오 흐름을 설명하는 내부 레이블이다.
 * ────────────────────────────────────────────────────────────────────────────
 */
@Service
public class SqlVizSimulationEngine {

    /**
     * sqls 리스트에서 t1Sql(index 0), t2Sql(index 1)만 추출해서 시나리오 빌더에 넘긴다.
     * 3개 이상 입력되면 index 2부터는 무시된다 — 제약사항 [1] 참고.
     */
    public SimulationResult simulate(List<String> sqls, SqlVizScenario scenario, IsolationLevel isolationLevel) {
        String t1Sql = sqls.size() > 0 ? sqls.get(0) : "SELECT * FROM orders WHERE id = 1 FOR UPDATE";
        String t2Sql = sqls.size() > 1 ? sqls.get(1) : "SELECT * FROM orders WHERE id = 2 FOR UPDATE";

        return switch (scenario) {
            case DEADLOCK -> buildDeadlock(t1Sql, t2Sql, isolationLevel);
            case DIRTY_READ -> buildDirtyRead(t1Sql, t2Sql, isolationLevel);
            case NON_REPEATABLE_READ -> buildNonRepeatableRead(t1Sql, t2Sql, isolationLevel);
            case PHANTOM_READ -> buildPhantomRead(t1Sql, t2Sql, isolationLevel);
            case LOST_UPDATE -> buildLostUpdate(t1Sql, t2Sql, isolationLevel);
            case MVCC -> buildMvcc(t1Sql, t2Sql, isolationLevel);
        };
    }

    /**
     * 데드락 시나리오: T1↔T2가 서로 상대방의 잠금을 대기하다 순환 대기 발생.
     *
     * IsolationLevel 무시 — 데드락은 격리 수준과 무관하게 잠금 순서 문제로 발생한다.
     * t1Sql = T1이 먼저 잠금을 거는 행의 쿼리 (Row A 역할)
     * t2Sql = T2가 먼저 잠금을 거는 행의 쿼리 (Row B 역할)
     *
     * 주의: LOCK_WAIT 스텝의 sql 필드는 사용자 입력이 아닌 고정 레이블 — 제약사항 [4] 참고.
     */
    private SimulationResult buildDeadlock(String t1Sql, String t2Sql, IsolationLevel level) {
        List<SimulationStep> steps = new ArrayList<>();
        int i = 1;
        steps.add(new SimulationStep(i++, "T1", "BEGIN", "", "success", "T1 트랜잭션 시작", 200));
        steps.add(new SimulationStep(i++, "T2", "BEGIN", "", "success", "T2 트랜잭션 시작", 200));
        steps.add(new SimulationStep(i++, "T1", "LOCK", t1Sql, "success", "T1이 Row A에 배타 잠금 획득", 400));
        steps.add(new SimulationStep(i++, "T2", "LOCK", t2Sql, "success", "T2가 Row B에 배타 잠금 획득", 400));
        // 교차 잠금 대기 — 여기서부터 순환 대기 시작. sql 필드는 실제 입력이 아닌 흐름 설명용 레이블.
        steps.add(new SimulationStep(i++, "T1", "LOCK_WAIT", "SELECT ... FOR UPDATE (Row B)", "blocked",
                "T1이 Row B 잠금 대기 — T2가 보유 중", 600));
        steps.add(new SimulationStep(i++, "T2", "LOCK_WAIT", "SELECT ... FOR UPDATE (Row A)", "blocked",
                "T2가 Row A 잠금 대기 — T1이 보유 중 → 순환 대기 발생", 600));
        steps.add(new SimulationStep(i++, "DB", "DEADLOCK", "", "deadlock",
                "DB가 데드락 감지 → T2 강제 롤백 (victim 선택)", 800));
        steps.add(new SimulationStep(i++, "T2", "ROLLBACK", "", "rollback", "T2 롤백 완료", 300));
        // T2 롤백 후 T1이 대기하던 잠금을 획득. sql 필드는 흐름 설명용 레이블.
        steps.add(new SimulationStep(i++, "T1", "LOCK", "Row B 잠금 획득", "success",
                "T2 롤백 후 T1이 Row B 잠금 획득", 400));
        steps.add(new SimulationStep(i++, "T1", "COMMIT", "", "success", "T1 커밋 완료", 300));

        String summary = "T1↔T2 순환 잠금 대기로 데드락 발생. DB가 T2를 victim으로 선택하여 강제 롤백.";
        return new SimulationResult(steps, summary, true, "DEADLOCK");
    }

    /**
     * Dirty Read 시나리오: T1의 미커밋 값을 T2가 읽는 문제.
     *
     * 격리 수준에 따라 분기:
     *   READ_UNCOMMITTED → Dirty Read 발생 (dirtyReadOccurs = true)
     *   READ_COMMITTED / REPEATABLE_READ / SERIALIZABLE → 방지됨
     *
     * t1Sql = T1의 UPDATE 쿼리 (미커밋 변경 발생원)
     * t2Sql = T2의 SELECT 쿼리 (미커밋 값을 읽으려 시도)
     *
     * 주의: detail의 "balance = 500" 설명은 하드코딩된 예시 — 실제 입력 SQL 값과 다를 수 있다.
     */
    private SimulationResult buildDirtyRead(String t1Sql, String t2Sql, IsolationLevel level) {
        List<SimulationStep> steps = new ArrayList<>();
        int i = 1;
        boolean dirtyReadOccurs = level == IsolationLevel.READ_UNCOMMITTED;

        steps.add(new SimulationStep(i++, "T1", "BEGIN", "", "success", "T1 시작", 200));
        steps.add(new SimulationStep(i++, "T2", "BEGIN", "", "success", "T2 시작", 200));
        steps.add(new SimulationStep(i++, "T1", "UPDATE", t1Sql, "success",
                "T1이 balance = 500으로 UPDATE (미커밋 상태)", 400));

        if (dirtyReadOccurs) {
            steps.add(new SimulationStep(i++, "T2", "SELECT", t2Sql, "dirty_value",
                    "T2가 T1의 미커밋 값 500을 읽음 → Dirty Read 발생!", 400));
            steps.add(new SimulationStep(i++, "T1", "ROLLBACK", "", "rollback",
                    "T1 롤백 → balance 원복. T2가 읽은 값 500은 유령 값이 됨", 400));
            return new SimulationResult(steps, "READ_UNCOMMITTED: T2가 T1의 미커밋 값을 읽어 Dirty Read 발생.", true, "DIRTY_READ");
        } else {
            steps.add(new SimulationStep(i++, "T2", "SELECT", t2Sql, "success",
                    "T2가 SELECT — " + level + " 격리로 T1 미커밋 값을 볼 수 없음 (기존 값 반환)", 400));
            steps.add(new SimulationStep(i++, "T1", "ROLLBACK", "", "rollback", "T1 롤백", 300));
            steps.add(new SimulationStep(i++, "T2", "COMMIT", "", "success", "T2 커밋", 300));
            return new SimulationResult(steps, level + ": Dirty Read 방지됨. T2는 T1의 미커밋 값을 읽지 않음.", false, null);
        }
    }

    /**
     * Non-Repeatable Read 시나리오: 같은 쿼리를 두 번 실행했을 때 결과가 달라지는 문제.
     *
     * 격리 수준에 따라 분기:
     *   READ_UNCOMMITTED / READ_COMMITTED → Non-Repeatable Read 발생
     *   REPEATABLE_READ / SERIALIZABLE → 스냅샷 격리로 방지됨
     *
     * t1Sql = T1의 SELECT 쿼리 (두 번 실행)
     * t2Sql = T2의 UPDATE 쿼리 (T1의 두 번째 읽기 전에 커밋)
     *
     * 주의: detail의 "balance = 1000 / 800" 설명은 하드코딩된 예시 — 실제 값과 다를 수 있다.
     */
    private SimulationResult buildNonRepeatableRead(String t1Sql, String t2Sql, IsolationLevel level) {
        List<SimulationStep> steps = new ArrayList<>();
        int i = 1;
        boolean nonRepeatableOccurs = level == IsolationLevel.READ_UNCOMMITTED
                || level == IsolationLevel.READ_COMMITTED;

        steps.add(new SimulationStep(i++, "T1", "BEGIN", "", "success", "T1 시작", 200));
        steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "success",
                "T1 첫 번째 SELECT → balance = 1000", 400));
        steps.add(new SimulationStep(i++, "T2", "BEGIN", "", "success", "T2 시작", 200));
        steps.add(new SimulationStep(i++, "T2", "UPDATE", t2Sql, "success",
                "T2가 balance = 800으로 UPDATE 후 COMMIT", 400));
        steps.add(new SimulationStep(i++, "T2", "COMMIT", "", "success", "T2 커밋 완료", 300));

        if (nonRepeatableOccurs) {
            // T2 커밋 후 T1이 같은 쿼리를 재실행하면 값이 달라짐
            steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "non_repeatable",
                    "T1 두 번째 SELECT → balance = 800 (T2 커밋 반영) → Non-Repeatable Read!", 400));
            return new SimulationResult(steps, level + ": 같은 쿼리를 두 번 실행했을 때 결과가 다름 — Non-Repeatable Read 발생.", true, "NON_REPEATABLE_READ");
        } else {
            // REPEATABLE_READ 이상: 트랜잭션 시작 시점 스냅샷 유지
            steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "success",
                    "T1 두 번째 SELECT → balance = 1000 (스냅샷 유지) → 방지됨", 400));
            steps.add(new SimulationStep(i++, "T1", "COMMIT", "", "success", "T1 커밋", 300));
            return new SimulationResult(steps, level + ": Non-Repeatable Read 방지됨. T1은 일관된 스냅샷을 읽음.", false, null);
        }
    }

    /**
     * Phantom Read 시나리오: 범위 쿼리 재실행 시 새로운 행이 나타나는 문제.
     *
     * 격리 수준에 따라 분기:
     *   READ_UNCOMMITTED / READ_COMMITTED / REPEATABLE_READ → Phantom Read 발생
     *   SERIALIZABLE → Gap Lock으로 방지됨
     *
     * t1Sql = T1의 범위 SELECT 쿼리 (COUNT 또는 WHERE 범위 조건 권장)
     * t2Sql = T2의 INSERT 쿼리 (T1의 범위 조건에 해당하는 행 삽입)
     *
     * 주의: detail의 "결과 5건 / 6건" 설명은 하드코딩된 예시 — 실제 데이터와 다를 수 있다.
     */
    private SimulationResult buildPhantomRead(String t1Sql, String t2Sql, IsolationLevel level) {
        List<SimulationStep> steps = new ArrayList<>();
        int i = 1;
        boolean phantomOccurs = level != IsolationLevel.SERIALIZABLE;

        steps.add(new SimulationStep(i++, "T1", "BEGIN", "", "success", "T1 시작", 200));
        steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "success",
                "T1: SELECT COUNT(*) → 결과 5건", 400));
        steps.add(new SimulationStep(i++, "T2", "BEGIN", "", "success", "T2 시작", 200));
        steps.add(new SimulationStep(i++, "T2", "INSERT", t2Sql, "success",
                "T2가 새 행 INSERT 후 COMMIT", 400));
        steps.add(new SimulationStep(i++, "T2", "COMMIT", "", "success", "T2 커밋", 300));

        if (phantomOccurs) {
            // T2 커밋 후 T1이 같은 범위 쿼리를 재실행하면 새로운 행이 보임
            steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "phantom",
                    "T1 재조회 → 결과 6건 (T2 삽입 행 포함) → Phantom Read!", 400));
            return new SimulationResult(steps, level + ": 범위 재조회 시 새로운 행이 보임 — Phantom Read 발생.", true, "PHANTOM_READ");
        } else {
            // SERIALIZABLE: Gap Lock으로 T2의 INSERT 자체가 차단됨
            steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "success",
                    "T1 재조회 → 결과 5건 (범위 잠금으로 T2 삽입 차단됨) → 방지됨", 400));
            steps.add(new SimulationStep(i++, "T1", "COMMIT", "", "success", "T1 커밋", 300));
            return new SimulationResult(steps, "SERIALIZABLE: Phantom Read 방지됨. 범위 잠금(Gap Lock)으로 새 행 삽입 차단.", false, null);
        }
    }

    /**
     * Lost Update 시나리오: 두 트랜잭션이 같은 값을 읽고 각자 업데이트해 한쪽 변경이 사라지는 문제.
     *
     * IsolationLevel 무시 — Lost Update는 낙관적/비관적 잠금으로만 방지 가능하며,
     * 격리 수준만으로는 막을 수 없다. 어떤 격리 수준을 선택해도 동일한 결과가 반환된다.
     *
     * t1Sql = T1의 초기 SELECT 쿼리
     * t2Sql = T2의 초기 SELECT 쿼리 (동일 행)
     *
     * 주의: UPDATE 스텝의 sql 필드("UPDATE accounts SET balance = 1200/1500")는 하드코딩된 예시.
     *       detail의 "balance" 값도 고정 예시다 — 제약사항 [3], [4] 참고.
     */
    private SimulationResult buildLostUpdate(String t1Sql, String t2Sql, IsolationLevel level) {
        List<SimulationStep> steps = new ArrayList<>();
        int i = 1;

        steps.add(new SimulationStep(i++, "T1", "BEGIN", "", "success", "T1 시작", 200));
        steps.add(new SimulationStep(i++, "T2", "BEGIN", "", "success", "T2 시작", 200));
        steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "success",
                "T1: balance = 1000 읽기", 400));
        steps.add(new SimulationStep(i++, "T2", "SELECT", t2Sql, "success",
                "T2: balance = 1000 읽기 (동일 값)", 400));
        // T1이 먼저 커밋. sql 필드는 시나리오 설명용 하드코딩 쿼리.
        steps.add(new SimulationStep(i++, "T1", "UPDATE", "UPDATE accounts SET balance = 1200",
                "success", "T1: balance = 1200으로 UPDATE (+200)", 400));
        steps.add(new SimulationStep(i++, "T1", "COMMIT", "", "success", "T1 커밋", 300));
        // T2가 T1 커밋을 모른 채 덮어씀 → T1의 변경 유실
        steps.add(new SimulationStep(i++, "T2", "UPDATE", "UPDATE accounts SET balance = 1500",
                "lost_update", "T2: balance = 1500으로 UPDATE (+500) → T1의 +200 변경이 유실!", 400));
        steps.add(new SimulationStep(i++, "T2", "COMMIT", "", "success", "T2 커밋 — 최종 balance = 1500 (T1 업데이트 소실)", 300));

        String summary = "T1과 T2가 동시에 같은 값을 읽어 각자 업데이트 → T1의 변경(+200)이 T2에 의해 덮어써져 소실됨.";
        return new SimulationResult(steps, summary, true, "LOST_UPDATE");
    }

    /**
     * MVCC 시나리오: 각 트랜잭션이 독립된 스냅샷을 유지해 잠금 없이 동시 처리되는 동작.
     *
     * IsolationLevel 무시 — MVCC는 구현 메커니즘이지 격리 수준이 아니다.
     * READ_COMMITTED(문장 단위 스냅샷)와 REPEATABLE_READ(트랜잭션 단위 스냅샷)의 차이가 있지만
     * 이 시뮬레이션은 REPEATABLE_READ 기준 동작을 고정으로 보여준다.
     *
     * t1Sql = T1의 SELECT 쿼리 (스냅샷 읽기 시연용)
     * t2Sql = T2의 UPDATE 쿼리 (T1 스냅샷에 영향을 주지 않음을 시연)
     *
     * 주의: detail의 "balance = 1000/1500", "T=100/T=101" 버전 번호는 하드코딩된 예시.
     */
    private SimulationResult buildMvcc(String t1Sql, String t2Sql, IsolationLevel level) {
        List<SimulationStep> steps = new ArrayList<>();
        int i = 1;

        steps.add(new SimulationStep(i++, "T1", "BEGIN", "", "success", "T1 시작 (snapshot at T=100)", 200));
        steps.add(new SimulationStep(i++, "T2", "BEGIN", "", "success", "T2 시작 (snapshot at T=100)", 200));
        steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "success",
                "T1: 스냅샷(T=100) 기준 데이터 읽기 — balance = 1000", 400));
        // T2가 새 버전을 만들어도 T1의 스냅샷에는 영향 없음
        steps.add(new SimulationStep(i++, "T2", "UPDATE", t2Sql, "success",
                "T2: balance를 1500으로 UPDATE → 새 버전(T=101) 생성", 400));
        steps.add(new SimulationStep(i++, "T2", "COMMIT", "", "success",
                "T2 커밋 — DB에 버전 T=101 확정", 300));
        // T1은 자신의 스냅샷(T=100)을 계속 읽음
        steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "success",
                "T1: 여전히 스냅샷(T=100) 기준 읽기 → balance = 1000 (T2 변경 영향 없음)", 400));
        steps.add(new SimulationStep(i++, "T1", "COMMIT", "", "success",
                "T1 커밋 — 각 트랜잭션은 독립된 스냅샷을 유지함", 300));

        String summary = "MVCC: T1과 T2가 동시에 동작하지만 각자의 스냅샷을 읽어 잠금 없이 충돌 없이 처리됨.";
        return new SimulationResult(steps, summary, false, null);
    }
}
