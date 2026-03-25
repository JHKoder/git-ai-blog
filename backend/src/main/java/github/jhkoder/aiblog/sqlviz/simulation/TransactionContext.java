package github.jhkoder.aiblog.sqlviz.simulation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 트랜잭션 실행 컨텍스트 — 스냅샷 버전 + 보유 락 + 커밋 전 변경 버퍼를 관리한다.
 *
 * snapshotVersion: 트랜잭션 시작 시점의 DB 버전 (MVCC 스냅샷 격리)
 * locks: 이 트랜잭션이 보유 중인 RowKey 집합
 * writeBuffer: 커밋 전 변경사항 (RowKey → 변경된 VirtualRow)
 */
public class TransactionContext {

    private final String txId;
    private final int snapshotVersion;
    private final Set<RowKey> locks;
    private final Map<RowKey, VirtualRow> writeBuffer;

    public TransactionContext(String txId, int snapshotVersion) {
        this.txId = txId;
        this.snapshotVersion = snapshotVersion;
        this.locks = new HashSet<>();
        this.writeBuffer = new HashMap<>();
    }

    public String getTxId() {
        return txId;
    }

    public int getSnapshotVersion() {
        return snapshotVersion;
    }

    public boolean holdsLock(RowKey key) {
        return locks.contains(key);
    }

    public void acquireLock(RowKey key) {
        locks.add(key);
    }

    public Set<RowKey> getLocks() {
        return Set.copyOf(locks);
    }

    public void writeRow(RowKey key, VirtualRow row) {
        writeBuffer.put(key, row);
    }

    public Map<RowKey, VirtualRow> getWriteBuffer() {
        return Map.copyOf(writeBuffer);
    }
}
