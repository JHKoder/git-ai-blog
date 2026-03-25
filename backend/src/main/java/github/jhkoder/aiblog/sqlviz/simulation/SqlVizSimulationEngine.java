package github.jhkoder.aiblog.sqlviz.simulation;

import github.jhkoder.aiblog.sqlviz.domain.IsolationLevel;
import github.jhkoder.aiblog.sqlviz.domain.SqlVizScenario;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 직접 실행 없이 시나리오 타입 + 격리 수준 조합으로 가상 Timeline 이벤트를 생성한다.
 *
 * ─── 개선 내역 (2026-03-25) ───────────────────────────────────────────────
 * [1단계] JSQLParser 도입 — SQL 문자열을 AST로 파싱해 type/table/whereClause 추출
 *         → detail 설명이 입력 SQL에 따라 동적으로 생성됨 (하드코딩 제거)
 *
 * [2단계] RowKey 기반 Lock 시스템 — "table:id" 형식으로 락 대상 표현
 *         → "Row A / Row B" 하드코딩 대신 "orders:1 vs orders:2" 처럼 실제 의미 있는 시각화
 *         → 파싱 실패 시 기존 "Row A/B" 폴백 유지 (하위 호환)
 *
 * ─── 남은 제약사항 ────────────────────────────────────────────────────────
 * [1] sqls 리스트에서 t1Sql(index 0), t2Sql(index 1)만 사용됨.
 *     3개 이상 입력 시 index 2부터 무시 — 현재 시뮬레이션은 2-트랜잭션 기준.
 *
 * [2] DEADLOCK / LOST_UPDATE / MVCC 시나리오는 IsolationLevel을 사용하지 않는다.
 *     어떤 격리 수준을 선택해도 동일한 결과가 반환된다.
 *
 * [3] step 내 operation="BEGIN/COMMIT/ROLLBACK/LOCK_WAIT/DEADLOCK"은
 *     사용자 SQL이 없는 흐름 제어 스텝이므로 sql 필드가 비거나 레이블만 들어간다.
 * ────────────────────────────────────────────────────────────────────────────
 */
@Service
public class SqlVizSimulationEngine {

    public SimulationResult simulate(List<String> sqls, SqlVizScenario scenario, IsolationLevel isolationLevel) {
        String t1Sql = !sqls.isEmpty() ? sqls.get(0) : "SELECT * FROM orders WHERE id = 1 FOR UPDATE";
        String t2Sql = sqls.size() > 1 ? sqls.get(1) : "SELECT * FROM orders WHERE id = 2 FOR UPDATE";

        ParsedSql p1 = SqlParser.parse(t1Sql);
        ParsedSql p2 = SqlParser.parse(t2Sql);

        // RowKey: 파싱 성공 시 "table:id", 실패 시 "Row A / Row B" 폴백
        RowKey rowA = p1.hasTable() ? RowKey.from(p1) : RowKey.fallback(0);
        RowKey rowB = p2.hasTable() ? RowKey.from(p2) : RowKey.fallback(1);

        return switch (scenario) {
            case DEADLOCK -> buildDeadlock(t1Sql, t2Sql, p1, p2, rowA, rowB);
            case DIRTY_READ -> buildDirtyRead(t1Sql, t2Sql, p1, p2, isolationLevel);
            case NON_REPEATABLE_READ -> buildNonRepeatableRead(t1Sql, t2Sql, p1, p2, isolationLevel);
            case PHANTOM_READ -> buildPhantomRead(t1Sql, t2Sql, p1, p2, isolationLevel);
            case LOST_UPDATE -> buildLostUpdate(t1Sql, t2Sql, p1, p2, rowA);
            case MVCC -> buildMvcc(t1Sql, t2Sql, p1, p2);
        };
    }

    // ── DEADLOCK ─────────────────────────────────────────────────────────────

    private SimulationResult buildDeadlock(
            String t1Sql, String t2Sql,
            ParsedSql p1, ParsedSql p2,
            RowKey rowA, RowKey rowB) {

        List<SimulationStep> steps = new ArrayList<>();
        int i = 1;

        steps.add(new SimulationStep(i++, "T1", "BEGIN", "", "success", "T1 트랜잭션 시작", 200));
        steps.add(new SimulationStep(i++, "T2", "BEGIN", "", "success", "T2 트랜잭션 시작", 200));
        steps.add(new SimulationStep(i++, "T1", "LOCK", t1Sql, "success",
                "T1이 " + rowA + " 배타 잠금 획득", 400));
        steps.add(new SimulationStep(i++, "T2", "LOCK", t2Sql, "success",
                "T2가 " + rowB + " 배타 잠금 획득", 400));
        steps.add(new SimulationStep(i++, "T1", "LOCK_WAIT",
                rowB + " 잠금 요청", "blocked",
                "T1이 " + rowB + " 잠금 대기 — T2가 보유 중", 600));
        steps.add(new SimulationStep(i++, "T2", "LOCK_WAIT",
                rowA + " 잠금 요청", "blocked",
                "T2가 " + rowA + " 잠금 대기 — T1이 보유 중 → 순환 대기 발생", 600));
        steps.add(new SimulationStep(i++, "DB", "DEADLOCK", "", "deadlock",
                "DB가 데드락 감지 → T2 강제 롤백 (victim 선택)", 800));
        steps.add(new SimulationStep(i++, "T2", "ROLLBACK", "", "rollback", "T2 롤백 완료", 300));
        steps.add(new SimulationStep(i++, "T1", "LOCK",
                rowB + " 잠금 획득", "success",
                "T2 롤백 후 T1이 " + rowB + " 잠금 획득", 400));
        steps.add(new SimulationStep(i++, "T1", "COMMIT", "", "success", "T1 커밋 완료", 300));

        String summary = "T1(" + rowA + ")↔T2(" + rowB + ") 순환 잠금 대기로 데드락 발생. DB가 T2를 victim으로 선택하여 강제 롤백.";
        return new SimulationResult(steps, summary, true, "DEADLOCK");
    }

    // ── DIRTY READ ────────────────────────────────────────────────────────────

    private SimulationResult buildDirtyRead(
            String t1Sql, String t2Sql,
            ParsedSql p1, ParsedSql p2,
            IsolationLevel level) {

        List<SimulationStep> steps = new ArrayList<>();
        int i = 1;
        boolean dirtyReadOccurs = level == IsolationLevel.READ_UNCOMMITTED;

        String t1Table = tableLabel(p1);
        String t2Table = tableLabel(p2);

        steps.add(new SimulationStep(i++, "T1", "BEGIN", "", "success", "T1 시작", 200));
        steps.add(new SimulationStep(i++, "T2", "BEGIN", "", "success", "T2 시작", 200));
        steps.add(new SimulationStep(i++, "T1", "UPDATE", t1Sql, "success",
                "T1이 " + t1Table + " UPDATE (미커밋 상태)", 400));

        if (dirtyReadOccurs) {
            steps.add(new SimulationStep(i++, "T2", "SELECT", t2Sql, "dirty_value",
                    "T2가 T1의 미커밋 값을 읽음 → Dirty Read 발생! (" + t1Table + ")", 400));
            steps.add(new SimulationStep(i++, "T1", "ROLLBACK", "", "rollback",
                    "T1 롤백 → " + t1Table + " 원복. T2가 읽은 값은 유령 값이 됨", 400));
            return new SimulationResult(steps,
                    "READ_UNCOMMITTED: T2가 T1의 미커밋 값을 읽어 Dirty Read 발생.", true, "DIRTY_READ");
        } else {
            steps.add(new SimulationStep(i++, "T2", "SELECT", t2Sql, "success",
                    "T2가 SELECT — " + level + " 격리로 T1 미커밋 값 차단 (" + t2Table + " 기존 값 반환)", 400));
            steps.add(new SimulationStep(i++, "T1", "ROLLBACK", "", "rollback", "T1 롤백", 300));
            steps.add(new SimulationStep(i++, "T2", "COMMIT", "", "success", "T2 커밋", 300));
            return new SimulationResult(steps,
                    level + ": Dirty Read 방지됨. T2는 T1의 미커밋 값을 읽지 않음.", false, null);
        }
    }

    // ── NON-REPEATABLE READ ───────────────────────────────────────────────────

    private SimulationResult buildNonRepeatableRead(
            String t1Sql, String t2Sql,
            ParsedSql p1, ParsedSql p2,
            IsolationLevel level) {

        List<SimulationStep> steps = new ArrayList<>();
        int i = 1;
        boolean nonRepeatableOccurs = level == IsolationLevel.READ_UNCOMMITTED
                || level == IsolationLevel.READ_COMMITTED;

        String t1Table = tableLabel(p1);
        String t2Table = tableLabel(p2);

        steps.add(new SimulationStep(i++, "T1", "BEGIN", "", "success", "T1 시작", 200));
        steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "success",
                "T1 첫 번째 SELECT — " + t1Table + " 현재 값 읽기", 400));
        steps.add(new SimulationStep(i++, "T2", "BEGIN", "", "success", "T2 시작", 200));
        steps.add(new SimulationStep(i++, "T2", "UPDATE", t2Sql, "success",
                "T2가 " + t2Table + " UPDATE 후 COMMIT", 400));
        steps.add(new SimulationStep(i++, "T2", "COMMIT", "", "success", "T2 커밋 완료", 300));

        if (nonRepeatableOccurs) {
            steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "non_repeatable",
                    "T1 두 번째 SELECT — T2 커밋 반영으로 값 변경됨 → Non-Repeatable Read! (" + t1Table + ")", 400));
            return new SimulationResult(steps,
                    level + ": 같은 쿼리를 두 번 실행했을 때 결과가 다름 — Non-Repeatable Read 발생.", true, "NON_REPEATABLE_READ");
        } else {
            steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "success",
                    "T1 두 번째 SELECT — 스냅샷 유지로 동일한 값 반환 (" + t1Table + ") → 방지됨", 400));
            steps.add(new SimulationStep(i++, "T1", "COMMIT", "", "success", "T1 커밋", 300));
            return new SimulationResult(steps,
                    level + ": Non-Repeatable Read 방지됨. T1은 일관된 스냅샷을 읽음.", false, null);
        }
    }

    // ── PHANTOM READ ──────────────────────────────────────────────────────────

    private SimulationResult buildPhantomRead(
            String t1Sql, String t2Sql,
            ParsedSql p1, ParsedSql p2,
            IsolationLevel level) {

        List<SimulationStep> steps = new ArrayList<>();
        int i = 1;
        boolean phantomOccurs = level != IsolationLevel.SERIALIZABLE;

        String t1Table = tableLabel(p1);
        String t2Table = tableLabel(p2);

        steps.add(new SimulationStep(i++, "T1", "BEGIN", "", "success", "T1 시작", 200));
        steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "success",
                "T1: " + t1Table + " 범위 조회 (현재 건수 확인)", 400));
        steps.add(new SimulationStep(i++, "T2", "BEGIN", "", "success", "T2 시작", 200));
        steps.add(new SimulationStep(i++, "T2", "INSERT", t2Sql, "success",
                "T2가 " + t2Table + " 새 행 INSERT 후 COMMIT", 400));
        steps.add(new SimulationStep(i++, "T2", "COMMIT", "", "success", "T2 커밋", 300));

        if (phantomOccurs) {
            steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "phantom",
                    "T1 재조회 — T2 삽입 행이 보임 → Phantom Read! (" + t1Table + ")", 400));
            return new SimulationResult(steps,
                    level + ": 범위 재조회 시 새로운 행이 보임 — Phantom Read 발생.", true, "PHANTOM_READ");
        } else {
            steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "success",
                    "T1 재조회 — 범위 잠금(Gap Lock)으로 T2 삽입 차단됨 (" + t1Table + ") → 방지됨", 400));
            steps.add(new SimulationStep(i++, "T1", "COMMIT", "", "success", "T1 커밋", 300));
            return new SimulationResult(steps,
                    "SERIALIZABLE: Phantom Read 방지됨. 범위 잠금(Gap Lock)으로 새 행 삽입 차단.", false, null);
        }
    }

    // ── LOST UPDATE ───────────────────────────────────────────────────────────

    private SimulationResult buildLostUpdate(
            String t1Sql, String t2Sql,
            ParsedSql p1, ParsedSql p2,
            RowKey rowA) {

        List<SimulationStep> steps = new ArrayList<>();
        int i = 1;

        String tableLabel = p1.hasTable() ? p1.table() : "동일 테이블";

        steps.add(new SimulationStep(i++, "T1", "BEGIN", "", "success", "T1 시작", 200));
        steps.add(new SimulationStep(i++, "T2", "BEGIN", "", "success", "T2 시작", 200));
        steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "success",
                "T1: " + tableLabel + " (" + rowA + ") 현재 값 읽기", 400));
        steps.add(new SimulationStep(i++, "T2", "SELECT", t2Sql, "success",
                "T2: " + tableLabel + " (" + rowA + ") 동일 값 읽기", 400));
        steps.add(new SimulationStep(i++, "T1", "UPDATE", t1Sql, "success",
                "T1: " + rowA + " UPDATE 후 커밋", 400));
        steps.add(new SimulationStep(i++, "T1", "COMMIT", "", "success", "T1 커밋", 300));
        steps.add(new SimulationStep(i++, "T2", "UPDATE", t2Sql, "lost_update",
                "T2: " + rowA + " UPDATE — T1의 변경을 덮어씀 → Lost Update!", 400));
        steps.add(new SimulationStep(i++, "T2", "COMMIT", "", "success",
                "T2 커밋 완료 — T1 업데이트 소실", 300));

        String summary = "T1과 T2가 동시에 " + rowA + " 값을 읽어 각자 UPDATE → T1 변경이 T2에 의해 덮어써져 소실됨.";
        return new SimulationResult(steps, summary, true, "LOST_UPDATE");
    }

    // ── MVCC ──────────────────────────────────────────────────────────────────

    private SimulationResult buildMvcc(
            String t1Sql, String t2Sql,
            ParsedSql p1, ParsedSql p2) {

        List<SimulationStep> steps = new ArrayList<>();
        int i = 1;

        String t1Table = tableLabel(p1);
        String t2Table = tableLabel(p2);

        steps.add(new SimulationStep(i++, "T1", "BEGIN", "", "success", "T1 시작 (snapshot at T=100)", 200));
        steps.add(new SimulationStep(i++, "T2", "BEGIN", "", "success", "T2 시작 (snapshot at T=100)", 200));
        steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "success",
                "T1: 스냅샷(T=100) 기준 " + t1Table + " 읽기", 400));
        steps.add(new SimulationStep(i++, "T2", "UPDATE", t2Sql, "success",
                "T2: " + t2Table + " UPDATE → 새 버전(T=101) 생성", 400));
        steps.add(new SimulationStep(i++, "T2", "COMMIT", "", "success",
                "T2 커밋 — DB에 버전 T=101 확정", 300));
        steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "success",
                "T1: 여전히 스냅샷(T=100) 기준 읽기 → T2 변경 영향 없음 (" + t1Table + ")", 400));
        steps.add(new SimulationStep(i++, "T1", "COMMIT", "", "success",
                "T1 커밋 — 각 트랜잭션은 독립된 스냅샷을 유지함", 300));

        String summary = "MVCC: T1과 T2가 동시에 동작하지만 각자의 스냅샷을 읽어 잠금 없이 충돌 없이 처리됨.";
        return new SimulationResult(steps, summary, false, null);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** 파싱 성공 시 테이블명, 실패 시 "대상 테이블" 반환 */
    private String tableLabel(ParsedSql parsed) {
        return parsed.hasTable() ? parsed.table() : "대상 테이블";
    }
}
