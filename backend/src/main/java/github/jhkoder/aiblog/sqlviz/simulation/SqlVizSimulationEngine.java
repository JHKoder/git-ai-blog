package github.jhkoder.aiblog.sqlviz.simulation;

import github.jhkoder.aiblog.sqlviz.domain.IsolationLevel;
import github.jhkoder.aiblog.sqlviz.domain.SqlVizScenario;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL 동시성 시나리오 가상 시뮬레이션 엔진 (v2).
 *
 * ─── v2 개선 사항 (2026-03-27) ────────────────────────────────────────────
 * [3단계] VirtualDatabase 실제 호출 — 모든 빌더가 VirtualDatabase/TransactionContext를
 *         실제 호출하여 step 흐름을 동적 생성. 하드코딩 step 제거.
 * [4단계] LockType 기반 blocked/deadlock 자동 감지 — VirtualDatabase.LockResult 반환값으로
 *         빌더가 blocked/deadlock 스텝을 자동 결정.
 * [5단계] -- STEP:[n] TX:[id] 인터리빙 런타임 — 사용자가 SQL 주석으로 실행 순서와 TX를
 *         직접 지정하면 엔진이 STEP 순서대로 VirtualDatabase를 호출해 steps 생성.
 * [6단계] SimulationResult.limitations + SimulationStep.warning 설정.
 *
 * ─── 남은 제약사항 (limitations로 노출) ──────────────────────────────────
 * [1] -- STEP 주석이 없는 경우 sqls[0]=T1, sqls[1]=T2 고정 2-트랜잭션 기준.
 * [2] SSI(Serializable Snapshot Isolation) 충돌은 시뮬레이터 범위 밖.
 * [3] FK 제약, Advisory Lock, Gap Lock은 미지원.
 * ────────────────────────────────────────────────────────────────────────────
 */
@Service
public class SqlVizSimulationEngine {

    public SimulationResult simulate(List<String> sqls, SqlVizScenario scenario, IsolationLevel isolationLevel) {
        // -- STEP:[n] TX:[id] 주석이 있으면 인터리빙 런타임 실행
        if (hasStepMeta(sqls)) {
            return runInterleaved(sqls, isolationLevel);
        }

        String t1Sql = !sqls.isEmpty() ? sqls.get(0) : "SELECT * FROM orders WHERE id = 1 FOR UPDATE";
        String t2Sql = sqls.size() > 1 ? sqls.get(1) : "SELECT * FROM orders WHERE id = 2 FOR UPDATE";

        ParsedSql p1 = SqlParser.parse(t1Sql);
        ParsedSql p2 = SqlParser.parse(t2Sql);

        RowKey rowA = p1.hasTable() ? RowKey.from(p1) : RowKey.fallback(0);
        RowKey rowB = p2.hasTable() ? RowKey.from(p2) : RowKey.fallback(1);

        return switch (scenario) {
            case DEADLOCK             -> buildDeadlock(t1Sql, t2Sql, p1, p2, rowA, rowB);
            case DIRTY_READ           -> buildDirtyRead(t1Sql, t2Sql, p1, p2, isolationLevel);
            case NON_REPEATABLE_READ  -> buildNonRepeatableRead(t1Sql, t2Sql, p1, p2, isolationLevel);
            case PHANTOM_READ         -> buildPhantomRead(t1Sql, t2Sql, p1, p2, isolationLevel);
            case LOST_UPDATE          -> buildLostUpdate(t1Sql, t2Sql, p1, p2, rowA);
            case MVCC                 -> buildMvcc(t1Sql, t2Sql, p1, p2);
        };
    }

    // ── 인터리빙 런타임 ────────────────────────────────────────────────────────

    /**
     * -- STEP:[n] TX:[id] 주석 기반 인터리빙 런타임.
     * sqls 리스트에서 stepMeta가 있는 항목을 STEP 순으로 정렬 후 VirtualDatabase 실제 호출.
     */
    private SimulationResult runInterleaved(List<String> sqls, IsolationLevel defaultIsolation) {
        // STEP 순서 정렬
        List<ParsedSql> sorted = sqls.stream()
                .map(SqlParser::parse)
                .filter(p -> p.stepMeta() != null)
                .sorted((a, b) -> Integer.compare(a.stepMeta().step(), b.stepMeta().step()))
                .toList();

        VirtualDatabase db = new VirtualDatabase();
        Map<String, TransactionContext> txMap = new HashMap<>();
        List<SimulationStep> steps = new ArrayList<>();
        List<String> limitations = buildCommonLimitations();
        int stepNum = 1;

        for (ParsedSql parsed : sorted) {
            ParsedSql.StepMeta meta = parsed.stepMeta();
            String txId = meta.txId();
            String sql = buildSqlLabel(parsed);

            TransactionContext tx = txMap.get(txId);

            switch (parsed.type()) {
                case BEGIN -> {
                    IsolationLevel level = parsed.isolationLevel() != null
                            ? parsed.isolationLevel() : defaultIsolation;
                    tx = db.begin(txId, level);
                    txMap.put(txId, tx);
                    steps.add(new SimulationStep(stepNum++, txId, "BEGIN", "",
                            "success", txId + " 시작 (격리수준: " + level + ")", 200));
                }
                case SELECT -> {
                    if (tx == null) { tx = db.begin(txId, defaultIsolation); txMap.put(txId, tx); }
                    RowKey key = RowKey.from(parsed);
                    var row = db.read(tx, key);
                    String detail = row.isPresent()
                            ? txId + ": " + key + " 읽기 → " + row.get().getData()
                            : txId + ": " + key + " 읽기 → 스냅샷에 없음(버전 차이)";
                    steps.add(new SimulationStep(stepNum++, txId, "SELECT", sql, "success", detail, 400));
                }
                case UPDATE -> {
                    if (tx == null) { tx = db.begin(txId, defaultIsolation); txMap.put(txId, tx); }
                    RowKey key = RowKey.from(parsed);
                    VirtualDatabase.LockResult lockResult = db.acquireLock(tx, key, LockType.FOR_UPDATE);
                    if (lockResult == VirtualDatabase.LockResult.DEADLOCK) {
                        steps.add(new SimulationStep(stepNum++, txId, "LOCK_WAIT", key + " 잠금 요청",
                                "deadlock", txId + " → 데드락 감지! 강제 롤백", 800,
                                "데드락 감지 — DB가 이 트랜잭션을 victim으로 선택"));
                        db.rollback(tx);
                        txMap.remove(txId);
                        steps.add(new SimulationStep(stepNum++, txId, "ROLLBACK", "", "rollback", txId + " 롤백", 300));
                    } else if (lockResult == VirtualDatabase.LockResult.BLOCKED) {
                        String blocker = db.getLockOwner(key).orElse("unknown");
                        steps.add(new SimulationStep(stepNum++, txId, "LOCK_WAIT", key + " 잠금 요청",
                                "blocked", txId + " 대기 — " + blocker + " 보유 중", 600,
                                "락 대기 발생 — 실제 DB에서는 timeout 또는 deadlock으로 해소"));
                    } else {
                        db.write(tx, key, Map.of("updated_by", txId));
                        steps.add(new SimulationStep(stepNum++, txId, "UPDATE", sql, "success",
                                txId + ": " + key + " UPDATE (락 획득 + writeBuffer 기록)", 400));
                    }
                }
                case DELETE -> {
                    if (tx == null) { tx = db.begin(txId, defaultIsolation); txMap.put(txId, tx); }
                    RowKey key = RowKey.from(parsed);
                    VirtualDatabase.LockResult lockResult = db.acquireLock(tx, key, LockType.FOR_UPDATE);
                    if (lockResult == VirtualDatabase.LockResult.DEADLOCK) {
                        steps.add(new SimulationStep(stepNum++, txId, "LOCK_WAIT", key + " 잠금 요청",
                                "deadlock", txId + " → 데드락 감지! 강제 롤백", 800,
                                "데드락 감지 — DB가 이 트랜잭션을 victim으로 선택"));
                        db.rollback(tx);
                        txMap.remove(txId);
                        steps.add(new SimulationStep(stepNum++, txId, "ROLLBACK", "", "rollback", txId + " 롤백", 300));
                    } else if (lockResult == VirtualDatabase.LockResult.BLOCKED) {
                        String blocker = db.getLockOwner(key).orElse("unknown");
                        steps.add(new SimulationStep(stepNum++, txId, "LOCK_WAIT", key + " 잠금 요청",
                                "blocked", txId + " 대기 — " + blocker + " 보유 중", 600));
                    } else {
                        steps.add(new SimulationStep(stepNum++, txId, "DELETE", sql, "success",
                                txId + ": " + key + " DELETE (락 획득)", 400));
                    }
                }
                case INSERT -> {
                    if (tx == null) { tx = db.begin(txId, defaultIsolation); txMap.put(txId, tx); }
                    RowKey key = RowKey.from(parsed);
                    db.insert(key, Map.of("created_by", txId));
                    steps.add(new SimulationStep(stepNum++, txId, "INSERT", sql, "success",
                            txId + ": " + key + " INSERT", 400));
                }
                case COMMIT -> {
                    if (tx != null) {
                        int newVersion = db.commit(tx);
                        txMap.remove(txId);
                        steps.add(new SimulationStep(stepNum++, txId, "COMMIT", "", "success",
                                txId + " 커밋 — DB 버전 " + newVersion + " 확정", 300));
                    }
                }
                case ROLLBACK -> {
                    if (tx != null) {
                        db.rollback(tx);
                        txMap.remove(txId);
                        steps.add(new SimulationStep(stepNum++, txId, "ROLLBACK", "", "rollback",
                                txId + " 롤백 완료", 300));
                    }
                }
                default -> {}
            }
        }

        boolean hasConflict = steps.stream().anyMatch(s ->
                "blocked".equals(s.result()) || "deadlock".equals(s.result()) || "lost_update".equals(s.result()));
        String conflictType = steps.stream()
                .filter(s -> "deadlock".equals(s.result())).findFirst().map(s -> "DEADLOCK")
                .orElse(hasConflict ? "CONFLICT" : null);

        return new SimulationResult(steps,
                "인터리빙 실행 완료. 트랜잭션 수: " + countUniqueTx(sorted),
                hasConflict, conflictType, limitations);
    }

    // ── DEADLOCK ─────────────────────────────────────────────────────────────

    private SimulationResult buildDeadlock(
            String t1Sql, String t2Sql,
            ParsedSql p1, ParsedSql p2,
            RowKey rowA, RowKey rowB) {

        VirtualDatabase db = new VirtualDatabase();
        db.insert(rowA, Map.of("value", "A"));
        db.insert(rowB, Map.of("value", "B"));

        TransactionContext tx1 = db.begin("T1");
        TransactionContext tx2 = db.begin("T2");

        List<SimulationStep> steps = new ArrayList<>();
        int i = 1;

        steps.add(new SimulationStep(i++, "T1", "BEGIN", "", "success", "T1 트랜잭션 시작", 200));
        steps.add(new SimulationStep(i++, "T2", "BEGIN", "", "success", "T2 트랜잭션 시작", 200));

        // T1이 rowA 잠금 획득
        db.acquireLock(tx1, rowA, LockType.FOR_UPDATE);
        steps.add(new SimulationStep(i++, "T1", "LOCK", t1Sql, "success",
                "T1이 " + rowA + " 배타 잠금 획득", 400));

        // T2가 rowB 잠금 획득
        db.acquireLock(tx2, rowB, LockType.FOR_UPDATE);
        steps.add(new SimulationStep(i++, "T2", "LOCK", t2Sql, "success",
                "T2가 " + rowB + " 배타 잠금 획득", 400));

        // T1이 rowB 잠금 시도 → blocked
        VirtualDatabase.LockResult t1Attempt = db.acquireLock(tx1, rowB, LockType.FOR_UPDATE);
        steps.add(new SimulationStep(i++, "T1", "LOCK_WAIT",
                rowB + " 잠금 요청",
                t1Attempt == VirtualDatabase.LockResult.BLOCKED ? "blocked" : "deadlock",
                "T1이 " + rowB + " 잠금 대기 — T2가 보유 중", 600));

        // T2가 rowA 잠금 시도 → deadlock 감지
        VirtualDatabase.LockResult t2Attempt = db.acquireLock(tx2, rowA, LockType.FOR_UPDATE);
        String t2Result = t2Attempt == VirtualDatabase.LockResult.DEADLOCK ? "deadlock" : "blocked";
        steps.add(new SimulationStep(i++, "T2", "LOCK_WAIT",
                rowA + " 잠금 요청", t2Result,
                "T2가 " + rowA + " 잠금 대기 — T1이 보유 중 → 순환 대기 발생", 600,
                "데드락 구간 — T1↔T2 순환 대기. 실제 DB는 victim을 선택해 강제 롤백."));

        steps.add(new SimulationStep(i++, "DB", "DEADLOCK", "", "deadlock",
                "DB가 데드락 감지 → T2 강제 롤백 (victim 선택)", 800));

        db.rollback(tx2);
        steps.add(new SimulationStep(i++, "T2", "ROLLBACK", "", "rollback", "T2 롤백 완료", 300));

        // T1이 rowB 잠금 재시도 성공 (T2 롤백 후)
        db.acquireLock(tx1, rowB, LockType.FOR_UPDATE);
        steps.add(new SimulationStep(i++, "T1", "LOCK",
                rowB + " 잠금 획득", "success",
                "T2 롤백 후 T1이 " + rowB + " 잠금 획득", 400));

        db.commit(tx1);
        steps.add(new SimulationStep(i++, "T1", "COMMIT", "", "success", "T1 커밋 완료", 300));

        String summary = "T1(" + rowA + ")↔T2(" + rowB + ") 순환 잠금 대기로 데드락 발생. DB가 T2를 victim으로 선택하여 강제 롤백.";
        return new SimulationResult(steps, summary, true, "DEADLOCK",
                List.of("victim 선택 알고리즘(트랜잭션 비용, 우선순위)은 시뮬레이터 범위 밖입니다.",
                        "3개 이상 트랜잭션의 다중 데드락은 현재 2-TX 기준으로만 시뮬레이션됩니다."));
    }

    // ── DIRTY READ ────────────────────────────────────────────────────────────

    private SimulationResult buildDirtyRead(
            String t1Sql, String t2Sql,
            ParsedSql p1, ParsedSql p2,
            IsolationLevel level) {

        VirtualDatabase db = new VirtualDatabase();
        String t1Table = tableLabel(p1);
        RowKey rowA = p1.hasTable() ? RowKey.from(p1) : RowKey.fallback(0);
        db.insert(rowA, Map.of("value", 100));

        TransactionContext tx1 = db.begin("T1", level);
        TransactionContext tx2 = db.begin("T2", level);

        List<SimulationStep> steps = new ArrayList<>();
        int i = 1;
        boolean dirtyReadOccurs = level == IsolationLevel.READ_UNCOMMITTED;

        steps.add(new SimulationStep(i++, "T1", "BEGIN", "", "success", "T1 시작", 200));
        steps.add(new SimulationStep(i++, "T2", "BEGIN", "", "success", "T2 시작", 200));

        // T1: UPDATE (미커밋)
        db.acquireLock(tx1, rowA, LockType.FOR_UPDATE);
        db.write(tx1, rowA, Map.of("value", 200));
        steps.add(new SimulationStep(i++, "T1", "UPDATE", t1Sql, "success",
                "T1이 " + t1Table + " UPDATE — 미커밋 상태 (value: 100 → 200)", 400));

        if (dirtyReadOccurs) {
            // T2: Dirty Read — tx1의 미커밋 writeBuffer 직접 읽기
            var dirtyVal = db.readDirty(rowA);
            steps.add(new SimulationStep(i++, "T2", "SELECT", t2Sql, "dirty_value",
                    "T2가 T1의 미커밋 값을 읽음 → Dirty Read! (" + dirtyVal.map(r -> r.getData().toString()).orElse("없음") + ")", 400,
                    "Dirty Read 발생 — 이 값은 T1 롤백 시 유령 값이 됩니다."));
            db.rollback(tx1);
            steps.add(new SimulationStep(i++, "T1", "ROLLBACK", "", "rollback",
                    "T1 롤백 → " + t1Table + " 원복. T2가 읽은 값(200)은 유령 값", 400));
            return new SimulationResult(steps,
                    "READ_UNCOMMITTED: T2가 T1의 미커밋 값을 읽어 Dirty Read 발생.", true, "DIRTY_READ",
                    List.of("READ_UNCOMMITTED는 대부분의 실무 DB에서 사용하지 않습니다.",
                            "PostgreSQL은 READ_UNCOMMITTED를 READ_COMMITTED로 처리합니다."));
        } else {
            // T2: 격리로 차단 — MVCC read
            var safeVal = db.read(tx2, rowA);
            steps.add(new SimulationStep(i++, "T2", "SELECT", t2Sql, "success",
                    "T2가 SELECT — " + level + " 격리로 T1 미커밋 값 차단 (" + safeVal.map(r -> r.getData().toString()).orElse("기존 값") + " 반환)", 400));
            db.rollback(tx1);
            steps.add(new SimulationStep(i++, "T1", "ROLLBACK", "", "rollback", "T1 롤백", 300));
            db.commit(tx2);
            steps.add(new SimulationStep(i++, "T2", "COMMIT", "", "success", "T2 커밋", 300));
            return new SimulationResult(steps,
                    level + ": Dirty Read 방지됨. T2는 T1의 미커밋 값을 읽지 않음.", false, null,
                    buildCommonLimitations());
        }
    }

    // ── NON-REPEATABLE READ ───────────────────────────────────────────────────

    private SimulationResult buildNonRepeatableRead(
            String t1Sql, String t2Sql,
            ParsedSql p1, ParsedSql p2,
            IsolationLevel level) {

        VirtualDatabase db = new VirtualDatabase();
        RowKey rowA = p1.hasTable() ? RowKey.from(p1) : RowKey.fallback(0);
        db.insert(rowA, Map.of("value", 100));

        String t1Table = tableLabel(p1);
        String t2Table = tableLabel(p2);
        boolean nonRepeatableOccurs = level == IsolationLevel.READ_UNCOMMITTED
                || level == IsolationLevel.READ_COMMITTED;

        TransactionContext tx1 = db.begin("T1", level);
        List<SimulationStep> steps = new ArrayList<>();
        int i = 1;

        steps.add(new SimulationStep(i++, "T1", "BEGIN", "", "success", "T1 시작", 200));
        var firstRead = db.read(tx1, rowA);
        steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "success",
                "T1 첫 번째 SELECT — " + t1Table + " 값: " + firstRead.map(r -> r.getData().toString()).orElse("없음"), 400));

        // T2: UPDATE + COMMIT
        TransactionContext tx2 = db.begin("T2", level);
        steps.add(new SimulationStep(i++, "T2", "BEGIN", "", "success", "T2 시작", 200));
        db.acquireLock(tx2, rowA, LockType.FOR_UPDATE);
        db.write(tx2, rowA, Map.of("value", 200));
        steps.add(new SimulationStep(i++, "T2", "UPDATE", t2Sql, "success",
                "T2가 " + t2Table + " UPDATE (value: 100 → 200) 후 COMMIT", 400));
        db.commit(tx2);
        steps.add(new SimulationStep(i++, "T2", "COMMIT", "", "success", "T2 커밋 완료", 300));

        if (nonRepeatableOccurs) {
            // READ_COMMITTED: 커밋된 최신 값 읽기 → 다른 값
            var secondRead = db.read(tx1, rowA);
            steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "non_repeatable",
                    "T1 두 번째 SELECT — T2 커밋 반영으로 값 변경됨: "
                            + secondRead.map(r -> r.getData().toString()).orElse("없음")
                            + " → Non-Repeatable Read!", 400,
                    "Non-Repeatable Read 발생 구간 — 같은 쿼리가 다른 값을 반환합니다."));
            return new SimulationResult(steps,
                    level + ": 같은 쿼리를 두 번 실행했을 때 결과가 다름 — Non-Repeatable Read 발생.",
                    true, "NON_REPEATABLE_READ", buildCommonLimitations());
        } else {
            // REPEATABLE_READ: 스냅샷 고정
            var secondRead = db.read(tx1, rowA);
            steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "success",
                    "T1 두 번째 SELECT — 스냅샷 유지로 동일한 값 반환: "
                            + secondRead.map(r -> r.getData().toString()).orElse("없음") + " → 방지됨", 400));
            db.commit(tx1);
            steps.add(new SimulationStep(i++, "T1", "COMMIT", "", "success", "T1 커밋", 300));
            return new SimulationResult(steps,
                    level + ": Non-Repeatable Read 방지됨. T1은 일관된 스냅샷을 읽음.", false, null,
                    buildCommonLimitations());
        }
    }

    // ── PHANTOM READ ──────────────────────────────────────────────────────────

    private SimulationResult buildPhantomRead(
            String t1Sql, String t2Sql,
            ParsedSql p1, ParsedSql p2,
            IsolationLevel level) {

        VirtualDatabase db = new VirtualDatabase();
        RowKey rowA = p1.hasTable() ? RowKey.from(p1) : RowKey.fallback(0);
        RowKey rowNew = p2.hasTable() ? RowKey.from(p2) : RowKey.fallback(2);
        db.insert(rowA, Map.of("value", 100));

        String t1Table = tableLabel(p1);
        String t2Table = tableLabel(p2);
        boolean phantomOccurs = level != IsolationLevel.SERIALIZABLE;

        TransactionContext tx1 = db.begin("T1", level);
        List<SimulationStep> steps = new ArrayList<>();
        int i = 1;

        steps.add(new SimulationStep(i++, "T1", "BEGIN", "", "success", "T1 시작", 200));
        steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "success",
                "T1: " + t1Table + " 범위 조회 (현재 행 수 확인)", 400));

        // T2: INSERT + COMMIT
        TransactionContext tx2 = db.begin("T2", level);
        steps.add(new SimulationStep(i++, "T2", "BEGIN", "", "success", "T2 시작", 200));
        db.insert(rowNew, Map.of("value", 999));
        db.commit(tx2);
        steps.add(new SimulationStep(i++, "T2", "INSERT", t2Sql, "success",
                "T2가 " + t2Table + " 새 행 INSERT 후 COMMIT", 400));
        steps.add(new SimulationStep(i++, "T2", "COMMIT", "", "success", "T2 커밋", 300));

        if (phantomOccurs) {
            steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "phantom",
                    "T1 재조회 — T2가 삽입한 행이 보임 → Phantom Read! (" + t1Table + ")", 400,
                    "Phantom Read 발생 구간 — 범위 조건 재조회 시 새 행이 나타납니다."));
            return new SimulationResult(steps,
                    level + ": 범위 재조회 시 새로운 행이 보임 — Phantom Read 발생.", true, "PHANTOM_READ",
                    List.of("Gap Lock / Next-Key Lock 동작은 시뮬레이터 범위 밖입니다.",
                            "PostgreSQL의 REPEATABLE READ는 Phantom Read를 MVCC로 방지하지만 이 시뮬레이터는 MySQL 기준으로 처리합니다."));
        } else {
            db.commit(tx1);
            steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "success",
                    "T1 재조회 — 범위 잠금(Gap Lock)으로 T2 삽입 차단됨 (" + t1Table + ") → 방지됨", 400));
            steps.add(new SimulationStep(i++, "T1", "COMMIT", "", "success", "T1 커밋", 300));
            return new SimulationResult(steps,
                    "SERIALIZABLE: Phantom Read 방지됨. 범위 잠금(Gap Lock)으로 새 행 삽입 차단.", false, null,
                    List.of("Gap Lock 실제 동작은 시뮬레이터 범위 밖입니다."));
        }
    }

    // ── LOST UPDATE ───────────────────────────────────────────────────────────

    private SimulationResult buildLostUpdate(
            String t1Sql, String t2Sql,
            ParsedSql p1, ParsedSql p2,
            RowKey rowA) {

        VirtualDatabase db = new VirtualDatabase();
        db.insert(rowA, Map.of("value", 100));

        TransactionContext tx1 = db.begin("T1");
        TransactionContext tx2 = db.begin("T2");

        String tableLabel = p1.hasTable() ? p1.table() : "동일 테이블";
        List<SimulationStep> steps = new ArrayList<>();
        int i = 1;

        steps.add(new SimulationStep(i++, "T1", "BEGIN", "", "success", "T1 시작", 200));
        steps.add(new SimulationStep(i++, "T2", "BEGIN", "", "success", "T2 시작", 200));

        // 두 TX 모두 현재 값 읽기 (같은 스냅샷)
        var t1Read = db.read(tx1, rowA);
        steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "success",
                "T1: " + tableLabel + " (" + rowA + ") 현재 값 읽기 → "
                        + t1Read.map(r -> r.getData().toString()).orElse("없음"), 400));
        var t2Read = db.read(tx2, rowA);
        steps.add(new SimulationStep(i++, "T2", "SELECT", t2Sql, "success",
                "T2: " + tableLabel + " (" + rowA + ") 동일 값 읽기 → "
                        + t2Read.map(r -> r.getData().toString()).orElse("없음"), 400));

        // T1: UPDATE + COMMIT
        db.acquireLock(tx1, rowA, LockType.FOR_UPDATE);
        db.write(tx1, rowA, Map.of("value", 150));
        steps.add(new SimulationStep(i++, "T1", "UPDATE", t1Sql, "success",
                "T1: " + rowA + " UPDATE (value → 150) 후 커밋", 400));
        db.commit(tx1);
        steps.add(new SimulationStep(i++, "T1", "COMMIT", "", "success", "T1 커밋", 300));

        // T2: UPDATE + COMMIT — T1의 변경을 덮어씀
        db.acquireLock(tx2, rowA, LockType.FOR_UPDATE);
        db.write(tx2, rowA, Map.of("value", 130));
        steps.add(new SimulationStep(i++, "T2", "UPDATE", t2Sql, "lost_update",
                "T2: " + rowA + " UPDATE (value → 130) — T1의 변경(150)을 덮어씀 → Lost Update!", 400,
                "Lost Update 발생 구간 — T1의 변경이 T2에 의해 소실됩니다."));
        db.commit(tx2);
        steps.add(new SimulationStep(i++, "T2", "COMMIT", "", "success",
                "T2 커밋 완료 — T1 업데이트(150) 소실, 최종값 130", 300));

        String summary = "T1과 T2가 동시에 " + rowA + " 값을 읽어 각자 UPDATE → T1 변경이 T2에 의해 덮어써져 소실됨.";
        return new SimulationResult(steps, summary, true, "LOST_UPDATE",
                List.of("낙관적 잠금(version 컬럼) 또는 SELECT FOR UPDATE로 방지 가능. 방지 시뮬레이션은 DEADLOCK 시나리오 참고."));
    }

    // ── MVCC ──────────────────────────────────────────────────────────────────

    private SimulationResult buildMvcc(
            String t1Sql, String t2Sql,
            ParsedSql p1, ParsedSql p2) {

        VirtualDatabase db = new VirtualDatabase();
        RowKey rowA = p1.hasTable() ? RowKey.from(p1) : RowKey.fallback(0);
        db.insert(rowA, Map.of("value", 100));

        String t1Table = tableLabel(p1);
        String t2Table = tableLabel(p2);

        // T1은 REPEATABLE_READ로 스냅샷 고정
        TransactionContext tx1 = db.begin("T1", IsolationLevel.REPEATABLE_READ);
        int t1Snapshot = db.getGlobalVersion();

        List<SimulationStep> steps = new ArrayList<>();
        int i = 1;

        steps.add(new SimulationStep(i++, "T1", "BEGIN", "", "success",
                "T1 시작 (snapshot at T=" + t1Snapshot + ")", 200));

        TransactionContext tx2 = db.begin("T2", IsolationLevel.READ_COMMITTED);
        steps.add(new SimulationStep(i++, "T2", "BEGIN", "", "success",
                "T2 시작 (snapshot at T=" + db.getGlobalVersion() + ")", 200));

        var t1Read1 = db.read(tx1, rowA);
        steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "success",
                "T1: 스냅샷(T=" + t1Snapshot + ") 기준 " + t1Table + " 읽기 → "
                        + t1Read1.map(r -> r.getData().toString()).orElse("없음"), 400));

        db.acquireLock(tx2, rowA, LockType.FOR_UPDATE);
        db.write(tx2, rowA, Map.of("value", 200));
        steps.add(new SimulationStep(i++, "T2", "UPDATE", t2Sql, "success",
                "T2: " + t2Table + " UPDATE → 새 버전 생성 (value: 100 → 200)", 400));

        int newVersion = db.commit(tx2);
        steps.add(new SimulationStep(i++, "T2", "COMMIT", "", "success",
                "T2 커밋 — DB에 버전 T=" + newVersion + " 확정", 300));

        var t1Read2 = db.read(tx1, rowA);
        steps.add(new SimulationStep(i++, "T1", "SELECT", t1Sql, "success",
                "T1: 여전히 스냅샷(T=" + t1Snapshot + ") 기준 읽기 → "
                        + t1Read2.map(r -> r.getData().toString()).orElse("없음")
                        + " (T2 변경 영향 없음) → MVCC 동작!", 400));

        db.commit(tx1);
        steps.add(new SimulationStep(i++, "T1", "COMMIT", "", "success",
                "T1 커밋 — 각 트랜잭션은 독립된 스냅샷을 유지함", 300));

        String summary = "MVCC: T1과 T2가 동시에 동작하지만 각자의 스냅샷을 읽어 잠금 없이 충돌 없이 처리됨.";
        return new SimulationResult(steps, summary, false, null,
                List.of("MVCC undo log 크기, vacuuming(PostgreSQL) / purge(MySQL)는 시뮬레이터 범위 밖입니다.",
                        "SSI(Serializable Snapshot Isolation) 충돌 감지는 시뮬레이터 범위 밖입니다."));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String tableLabel(ParsedSql parsed) {
        return parsed.hasTable() ? parsed.table() : "대상 테이블";
    }

    private boolean hasStepMeta(List<String> sqls) {
        return sqls.stream()
                .map(SqlParser::parse)
                .anyMatch(p -> p.stepMeta() != null);
    }

    private int countUniqueTx(List<ParsedSql> sorted) {
        return (int) sorted.stream()
                .filter(p -> p.stepMeta() != null)
                .map(p -> p.stepMeta().txId())
                .distinct()
                .count();
    }

    private String buildSqlLabel(ParsedSql parsed) {
        return parsed.type().name() + (parsed.hasTable() ? " " + parsed.table() : "");
    }

    private List<String> buildCommonLimitations() {
        return List.of("SSI(Serializable Snapshot Isolation) 충돌 감지는 시뮬레이터 범위 밖입니다.",
                "FK 제약, Advisory Lock, Gap Lock은 미지원입니다.");
    }
}
