package github.jhkoder.aiblog.sqlviz.simulation;

import github.jhkoder.aiblog.sqlviz.domain.IsolationLevel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 트랜잭션 실행 컨텍스트 — 스냅샷 버전 + 보유 락 + 커밋 전 변경 버퍼를 관리한다.
 *
 * snapshotVersion : 트랜잭션 시작 시점의 DB 버전 (MVCC 스냅샷 격리)
 * isolationLevel  : BEGIN ISOLATION LEVEL ... 로 설정된 격리 수준. 기본 READ_COMMITTED.
 * locks           : 이 트랜잭션이 보유 중인 RowKey 집합
 * lockTypes       : RowKey → 보유 LockType
 * writeBuffer     : 커밋 전 변경사항 (RowKey → 변경된 VirtualRow)
 */
public class TransactionContext {

    private final String txId;
    private final int snapshotVersion;
    private final IsolationLevel isolationLevel;
    private final Set<RowKey> locks;
    private final Map<RowKey, LockType> lockTypes;
    private final Map<RowKey, VirtualRow> writeBuffer;

    public TransactionContext(String txId, int snapshotVersion) {
        this(txId, snapshotVersion, IsolationLevel.READ_COMMITTED);
    }

    public TransactionContext(String txId, int snapshotVersion, IsolationLevel isolationLevel) {
        this.txId = txId;
        this.snapshotVersion = snapshotVersion;
        this.isolationLevel = isolationLevel != null ? isolationLevel : IsolationLevel.READ_COMMITTED;
        this.locks = new HashSet<>();
        this.lockTypes = new HashMap<>();
        this.writeBuffer = new HashMap<>();
    }

    public String getTxId() {
        return txId;
    }

    public int getSnapshotVersion() {
        return snapshotVersion;
    }

    public IsolationLevel getIsolationLevel() {
        return isolationLevel;
    }

    public boolean holdsLock(RowKey key) {
        return locks.contains(key);
    }

    public void acquireLock(RowKey key, LockType lockType) {
        locks.add(key);
        lockTypes.put(key, lockType);
    }

    /** 하위 호환 — LockType 없이 호출 시 FOR_UPDATE(배타) 처리 */
    public void acquireLock(RowKey key) {
        acquireLock(key, LockType.FOR_UPDATE);
    }

    public LockType getLockType(RowKey key) {
        return lockTypes.getOrDefault(key, LockType.FOR_UPDATE);
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
