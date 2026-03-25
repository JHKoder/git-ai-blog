package github.jhkoder.aiblog.sqlviz.simulation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * in-memory 가상 DB — RowKey 기반 VirtualRow 저장소.
 *
 * MVCC 동작:
 *   - 각 TransactionContext는 snapshotVersion을 가짐
 *   - read()는 snapshotVersion 이하의 최신 row를 반환
 *   - commit()은 writeBuffer를 DB에 반영하고 globalVersion 증가
 *
 * Lock 동작:
 *   - acquireLock(): 이미 다른 트랜잭션이 보유 중이면 false 반환 (대기 시뮬레이션용)
 *   - lockOwner 맵으로 현재 보유자 추적
 *
 * 주의: 이 클래스는 시뮬레이션 전용이며 thread-safe를 보장하지 않는다.
 *       SqlVizSimulationEngine 내부에서 시나리오당 1회 생성/사용/폐기된다.
 */
public class VirtualDatabase {

    /** 현재 커밋된 글로벌 버전 */
    private int globalVersion = 100;

    /** RowKey → 커밋된 최신 VirtualRow */
    private final Map<RowKey, VirtualRow> store = new HashMap<>();

    /** RowKey → 현재 락을 보유 중인 txId */
    private final Map<RowKey, String> lockOwner = new HashMap<>();

    // ── 초기 데이터 적재 ─────────────────────────────────────────────────────

    public void insert(RowKey key, Map<String, Object> initialData) {
        store.put(key, new VirtualRow(initialData, globalVersion));
    }

    // ── 트랜잭션 시작 ─────────────────────────────────────────────────────────

    public TransactionContext begin(String txId) {
        return new TransactionContext(txId, globalVersion);
    }

    // ── 읽기 (MVCC 스냅샷) ────────────────────────────────────────────────────

    /**
     * tx의 snapshotVersion 이하 버전의 row를 반환.
     * writeBuffer를 먼저 확인 (dirty read는 이 메서드로는 발생하지 않음).
     */
    public Optional<VirtualRow> read(TransactionContext tx, RowKey key) {
        // 자신의 writeBuffer 먼저 확인 (자신이 변경한 값 읽기)
        if (tx.getWriteBuffer().containsKey(key)) {
            return Optional.of(tx.getWriteBuffer().get(key));
        }
        VirtualRow row = store.get(key);
        if (row == null) return Optional.empty();
        // MVCC: snapshotVersion 이하 버전만 보임
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

    /**
     * tx의 writeBuffer에 변경사항 기록 (커밋 전까지 store에 반영되지 않음).
     */
    public void write(TransactionContext tx, RowKey key, Map<String, Object> newData) {
        VirtualRow base = store.getOrDefault(key, new VirtualRow(new HashMap<>(), globalVersion));
        VirtualRow updated = base.snapshot();
        newData.forEach(updated::put);
        tx.writeRow(key, updated);
    }

    // ── 락 ───────────────────────────────────────────────────────────────────

    /**
     * 배타 잠금 시도. 이미 다른 tx가 보유 중이면 false 반환.
     */
    public boolean acquireLock(TransactionContext tx, RowKey key) {
        String owner = lockOwner.get(key);
        if (owner != null && !owner.equals(tx.getTxId())) {
            return false; // 대기 필요 (blocked)
        }
        lockOwner.put(key, tx.getTxId());
        tx.acquireLock(key);
        return true;
    }

    /**
     * tx가 보유한 모든 잠금 해제 (COMMIT/ROLLBACK 후 호출).
     */
    public void releaseLocks(TransactionContext tx) {
        tx.getLocks().forEach(key -> lockOwner.remove(key, tx.getTxId()));
    }

    /** 현재 RowKey의 락 보유자 txId 반환 */
    public Optional<String> getLockOwner(RowKey key) {
        return Optional.ofNullable(lockOwner.get(key));
    }

    // ── 커밋 / 롤백 ──────────────────────────────────────────────────────────

    /**
     * writeBuffer를 store에 반영하고 globalVersion 증가.
     */
    public int commit(TransactionContext tx) {
        globalVersion++;
        tx.getWriteBuffer().forEach((key, row) -> {
            row.setVersion(globalVersion);
            store.put(key, row);
        });
        releaseLocks(tx);
        return globalVersion;
    }

    /**
     * writeBuffer 폐기, 잠금 해제.
     */
    public void rollback(TransactionContext tx) {
        releaseLocks(tx);
        // writeBuffer는 TransactionContext와 함께 GC됨
    }

    public int getGlobalVersion() {
        return globalVersion;
    }
}
