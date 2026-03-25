package github.jhkoder.aiblog.sqlviz.simulation;

import github.jhkoder.aiblog.sqlviz.domain.IsolationLevel;
import github.jhkoder.aiblog.sqlviz.domain.SqlVizScenario;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 직접 실행 없이 시나리오 타입 + 격리 수준 조합으로 가상 Timeline 이벤트를 생성한다.
 */
@Service
public class SqlVizSimulationEngine {

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

    private SimulationResult buildDeadlock(String t1Sql, String t2Sql, IsolationLevel level) {
        List<SimulationStep> steps = new ArrayList<>();
        int i = 1;
        steps.add(new SimulationStep(i++, "T1", "BEGIN", "", "success", "T1 트랜잭션 시작", 200));
        steps.add(new SimulationStep(i++, "T2", "BEGIN", "", "success", "T2 트랜잭션 시작", 200));
        steps.add(new SimulationStep(i++, "T1", "LOCK", t1Sql, "success", "T1이 Row A에 배타 잠금 획득", 400));
        steps.add(new SimulationStep(i++, "T2", "LOCK", t2Sql, "success", "T2가 Row B에 배타 잠금 획득", 400));
        steps.add(new SimulationStep(i++, "T1", "LOCK_WAIT", "SELECT ... FOR UPDATE (Row B)", "blocked",
                "T1이 Row B 잠금 대기 — T2가 보유 중", 600));
        steps.add(new SimulationStep(i++, "T2", "LOCK_WAIT", "SELECT ... FOR UPDATE (Row A)", "blocked",
                "T2가 Row A 잠금 대기 — T1이 보유 중 → 순환 대기 발생", 600));
        steps.add(new SimulationStep(i++, "DB", "DEADLOCK", "", "deadlock",
                "DB가 데드락 감지 → T2 강제 롤백 (victim 선택)", 800));
        steps.add(new SimulationStep(i++, "T2", "ROLLBACK", "", "rollback", "T2 롤백 완료", 300));
        steps.add(new SimulationStep(i++, "T1", "LOCK", "Row B 잠금 획득", "success",
                "T2 롤백 후 T1이 Row B 잠금 획득", 400));
        steps.add(new SimulationStep(i++, "T1", "COMMIT", "", "success", "T1 커밋 완료", 300));

        String summary = "T1↔T2 순환 잠금 대기로 데드락 발생. DB가 T2를 victim으로 선택하여 강제 롤백.";
        return new SimulationResult(steps, summary, true, "DEADLOCK");
    }

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
            steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "non_repeatable",
                    "T1 두 번째 SELECT → balance = 800 (T2 커밋 반영) → Non-Repeatable Read!", 400));
            return new SimulationResult(steps, level + ": 같은 쿼리를 두 번 실행했을 때 결과가 다름 — Non-Repeatable Read 발생.", true, "NON_REPEATABLE_READ");
        } else {
            steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "success",
                    "T1 두 번째 SELECT → balance = 1000 (스냅샷 유지) → 방지됨", 400));
            steps.add(new SimulationStep(i++, "T1", "COMMIT", "", "success", "T1 커밋", 300));
            return new SimulationResult(steps, level + ": Non-Repeatable Read 방지됨. T1은 일관된 스냅샷을 읽음.", false, null);
        }
    }

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
            steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "phantom",
                    "T1 재조회 → 결과 6건 (T2 삽입 행 포함) → Phantom Read!", 400));
            return new SimulationResult(steps, level + ": 범위 재조회 시 새로운 행이 보임 — Phantom Read 발생.", true, "PHANTOM_READ");
        } else {
            steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "success",
                    "T1 재조회 → 결과 5건 (범위 잠금으로 T2 삽입 차단됨) → 방지됨", 400));
            steps.add(new SimulationStep(i++, "T1", "COMMIT", "", "success", "T1 커밋", 300));
            return new SimulationResult(steps, "SERIALIZABLE: Phantom Read 방지됨. 범위 잠금(Gap Lock)으로 새 행 삽입 차단.", false, null);
        }
    }

    private SimulationResult buildLostUpdate(String t1Sql, String t2Sql, IsolationLevel level) {
        List<SimulationStep> steps = new ArrayList<>();
        int i = 1;

        steps.add(new SimulationStep(i++, "T1", "BEGIN", "", "success", "T1 시작", 200));
        steps.add(new SimulationStep(i++, "T2", "BEGIN", "", "success", "T2 시작", 200));
        steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "success",
                "T1: balance = 1000 읽기", 400));
        steps.add(new SimulationStep(i++, "T2", "SELECT", t2Sql, "success",
                "T2: balance = 1000 읽기 (동일 값)", 400));
        steps.add(new SimulationStep(i++, "T1", "UPDATE", "UPDATE accounts SET balance = 1200",
                "success", "T1: balance = 1200으로 UPDATE (+200)", 400));
        steps.add(new SimulationStep(i++, "T1", "COMMIT", "", "success", "T1 커밋", 300));
        steps.add(new SimulationStep(i++, "T2", "UPDATE", "UPDATE accounts SET balance = 1500",
                "lost_update", "T2: balance = 1500으로 UPDATE (+500) → T1의 +200 변경이 유실!", 400));
        steps.add(new SimulationStep(i++, "T2", "COMMIT", "", "success", "T2 커밋 — 최종 balance = 1500 (T1 업데이트 소실)", 300));

        String summary = "T1과 T2가 동시에 같은 값을 읽어 각자 업데이트 → T1의 변경(+200)이 T2에 의해 덮어써져 소실됨.";
        return new SimulationResult(steps, summary, true, "LOST_UPDATE");
    }

    private SimulationResult buildMvcc(String t1Sql, String t2Sql, IsolationLevel level) {
        List<SimulationStep> steps = new ArrayList<>();
        int i = 1;

        steps.add(new SimulationStep(i++, "T1", "BEGIN", "", "success", "T1 시작 (snapshot at T=100)", 200));
        steps.add(new SimulationStep(i++, "T2", "BEGIN", "", "success", "T2 시작 (snapshot at T=100)", 200));
        steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "success",
                "T1: 스냅샷(T=100) 기준 데이터 읽기 — balance = 1000", 400));
        steps.add(new SimulationStep(i++, "T2", "UPDATE", t2Sql, "success",
                "T2: balance를 1500으로 UPDATE → 새 버전(T=101) 생성", 400));
        steps.add(new SimulationStep(i++, "T2", "COMMIT", "", "success",
                "T2 커밋 — DB에 버전 T=101 확정", 300));
        steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "success",
                "T1: 여전히 스냅샷(T=100) 기준 읽기 → balance = 1000 (T2 변경 영향 없음)", 400));
        steps.add(new SimulationStep(i++, "T1", "COMMIT", "", "success",
                "T1 커밋 — 각 트랜잭션은 독립된 스냅샷을 유지함", 300));

        String summary = "MVCC: T1과 T2가 동시에 동작하지만 각자의 스냅샷을 읽어 잠금 없이 충돌 없이 처리됨.";
        return new SimulationResult(steps, summary, false, null);
    }
}
