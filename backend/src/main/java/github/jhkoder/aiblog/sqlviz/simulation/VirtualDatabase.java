package github.jhkoder.aiblog.sqlviz.simulation;

import github.jhkoder.aiblog.sqlviz.domain.IsolationLevel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * in-memory 가상 DB — RowKey 기반 VirtualRow 저장소.
 *
 * MVCC 동작:
 *   - 각 TransactionContext는 snapshotVersion을 가짐
 *   - read(): READ_COMMITTED → 커밋된 최신 값 / REPEATABLE_READ 이상 → 스냅샷 고정
 *   - readDirty(): READ_UNCOMMITTED용 — 미커밋 포함 최신 값
 *   - commit(): writeBuffer를 store에 반영 후 globalVersion 증가
 *
 * Lock 동작:
 *   - acquireLock(tx, key, lockType): LockType 충돌 여부 검사 후 잠금 시도
 *   - 충돌 시: waitFor에 기록 → detectDeadlock() 자동 호출 → blocked/deadlock 구분
 *
 * 주의: 시뮬레이션 전용, thread-safe 보장 안 함.
 *       SqlVizSimulationEngine 내부에서 시나리오당 1회 생성/사용/폐기.
 */
public class VirtualDatabase {

    /** 현재 커밋된 글로벌 버전 */
    private int globalVersion = 100;

    /** RowKey → 커밋된 최신 VirtualRow */
    private final Map<RowKey, VirtualRow> store = new HashMap<>();

    /** RowKey → (txId → LockType) 현재 락 보유 정보 (다중 공유 잠금 지원) */
    private final Map<RowKey, Map<String, LockType>> lockOwners = new HashMap<>();

    /** 테이블 단위 잠금: table → txId */
    private final Map<String, String> tableLockOwner = new HashMap<>();

    /** 락 대기 그래프: 요청 txId → 블로킹 txId (deadlock 감지용) */
    private final Map<String, String> waitFor = new HashMap<>();

    // ── 초기 데이터 적재 ─────────────────────────────────────────────────────

    public void insert(RowKey key, Map<String, Object> initialData) {
        store.put(key, new VirtualRow(initialData, globalVersion));
    }

    // ── 트랜잭션 시작 ─────────────────────────────────────────────────────────

    public TransactionContext begin(String txId) {
        return new TransactionContext(txId, globalVersion);
    }

    public TransactionContext begin(String txId, IsolationLevel isolationLevel) {
        return new TransactionContext(txId, globalVersion, isolationLevel);
    }

    // ── 읽기 (MVCC 스냅샷) ────────────────────────────────────────────────────

    /**
     * 격리 수준에 따라 가시성 분기:
     *   READ_UNCOMMITTED  → 미커밋 포함 최신 값 (dirty read 허용)
     *   READ_COMMITTED    → 커밋된 최신 값 (스냅샷 무시)
     *   REPEATABLE_READ   → 트랜잭션 시작 시점 스냅샷 고정
     *   SERIALIZABLE      → REPEATABLE_READ와 동일 (gap lock은 시뮬레이션 범위 외)
     */
    public Optional<VirtualRow> read(TransactionContext tx, RowKey key) {
        // 자신의 writeBuffer 먼저 확인 (자신이 변경한 값)
        if (tx.getWriteBuffer().containsKey(key)) {
            return Optional.of(tx.getWriteBuffer().get(key));
        }

        IsolationLevel level = tx.getIsolationLevel();

        if (level == IsolationLevel.READ_UNCOMMITTED) {
            return Optional.ofNullable(store.get(key)).map(VirtualRow::snapshot);
        }

        VirtualRow row = store.get(key);
        if (row == null) return Optional.empty();

        if (level == IsolationLevel.READ_COMMITTED) {
            // 커밋된 최신 값 반환 (버전 무관)
            return Optional.of(row.snapshot());
        }

        // REPEATABLE_READ / SERIALIZABLE: snapshotVersion 이하만 보임
        if (row.getVersion() <= tx.getSnapshotVersion()) {
            return Optional.of(row.snapshot());
        }
        return Optional.empty();
    }

    /**
     * READ_UNCOMMITTED용 — 미커밋 포함 최신 값 읽기 (Dirty Read 시뮬레이션)
     */
    public Optional<VirtualRow> readDirty(RowKey key) {
        return Optional.ofNullable(store.get(key)).map(VirtualRow::snapshot);
    }

    // ── 쓰기 ─────────────────────────────────────────────────────────────────

    public void write(TransactionContext tx, RowKey key, Map<String, Object> newData) {
        VirtualRow base = store.getOrDefault(key, new VirtualRow(new HashMap<>(), globalVersion));
        VirtualRow updated = base.snapshot();
        newData.forEach(updated::put);
        tx.writeRow(key, updated);
    }

    // ── 락 ───────────────────────────────────────────────────────────────────

    /**
     * 잠금 시도. 반환값:
     *   LockResult.SUCCESS  : 획득 성공
     *   LockResult.BLOCKED  : 다른 tx가 보유 중 (단순 대기)
     *   LockResult.DEADLOCK : 순환 대기 감지 → 호출자가 victim 처리
     */
    public LockResult acquireLock(TransactionContext tx, RowKey key, LockType lockType) {
        Map<String, LockType> owners = lockOwners.computeIfAbsent(key, k -> new HashMap<>());

        // 이미 자신이 보유 중이면 업그레이드 또는 재획득
        if (owners.containsKey(tx.getTxId())) {
            owners.put(tx.getTxId(), lockType);
            tx.acquireLock(key, lockType);
            return LockResult.SUCCESS;
        }

        // 충돌 검사: 다른 tx의 잠금과 충돌하는지 확인
        for (Map.Entry<String, LockType> entry : owners.entrySet()) {
            if (lockType.conflictsWith(entry.getValue())) {
                // 충돌 — waitFor 기록
                waitFor.put(tx.getTxId(), entry.getKey());
                if (detectDeadlock(tx.getTxId())) {
                    waitFor.remove(tx.getTxId());
                    return LockResult.DEADLOCK;
                }
                return LockResult.BLOCKED;
            }
        }

        owners.put(tx.getTxId(), lockType);
        tx.acquireLock(key, lockType);
        return LockResult.SUCCESS;
    }

    /** 하위 호환 — FOR_UPDATE(배타) 잠금 시도 */
    public boolean acquireLock(TransactionContext tx, RowKey key) {
        LockResult result = acquireLock(tx, key, LockType.FOR_UPDATE);
        return result == LockResult.SUCCESS;
    }

    /** 현재 RowKey의 락 보유자 txId 반환 (첫 번째 보유자) */
    public Optional<String> getLockOwner(RowKey key) {
        Map<String, LockType> owners = lockOwners.get(key);
        if (owners == null || owners.isEmpty()) return Optional.empty();
        return Optional.of(owners.keySet().iterator().next());
    }

    /**
     * tx가 보유한 모든 잠금 해제 (COMMIT/ROLLBACK 후 호출).
     * waitFor에서도 이 tx 제거.
     */
    public void releaseLocks(TransactionContext tx) {
        for (RowKey key : tx.getLocks()) {
            Map<String, LockType> owners = lockOwners.get(key);
            if (owners != null) {
                owners.remove(tx.getTxId());
                if (owners.isEmpty()) lockOwners.remove(key);
            }
        }
        waitFor.remove(tx.getTxId());
    }

    /**
     * DFS 사이클 탐색 — startTxId에서 시작해 waitFor 그래프에서 사이클 발생 시 true.
     */
    private boolean detectDeadlock(String startTxId) {
        Set<String> visited = new HashSet<>();
        String current = startTxId;
        while (current != null) {
            if (!visited.add(current)) {
                return true; // 사이클 발생 = 데드락
            }
            current = waitFor.get(current);
        }
        return false;
    }

    // ── 커밋 / 롤백 ──────────────────────────────────────────────────────────

    public int commit(TransactionContext tx) {
        globalVersion++;
        tx.getWriteBuffer().forEach((key, row) -> {
            row.setVersion(globalVersion);
            store.put(key, row);
        });
        releaseLocks(tx);
        return globalVersion;
    }

    public void rollback(TransactionContext tx) {
        releaseLocks(tx);
    }

    public int getGlobalVersion() {
        return globalVersion;
    }

    /** acquireLock() 상세 반환 타입 */
    public enum LockResult {
        SUCCESS, BLOCKED, DEADLOCK
    }
}
