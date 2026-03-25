package github.jhkoder.aiblog.sqlviz.simulation;

import java.util.HashMap;
import java.util.Map;

/**
 * in-memory 가상 행(row) — 실제 DB 대신 순수 Java 객체로 데이터를 표현한다.
 *
 * version: MVCC 스냅샷 버전 번호. 트랜잭션이 커밋할 때마다 증가.
 * data: 컬럼명 → 값 맵 (String, Long, Object 등 혼용 허용)
 */
public class VirtualRow {

    private final Map<String, Object> data;
    private int version;

    public VirtualRow(Map<String, Object> initialData, int version) {
        this.data = new HashMap<>(initialData);
        this.version = version;
    }

    public Object get(String column) {
        return data.get(column);
    }

    public void put(String column, Object value) {
        data.put(column, value);
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Map<String, Object> getData() {
        return Map.copyOf(data);
    }

    /** 현재 상태의 복사본 반환 (스냅샷용) */
    public VirtualRow snapshot() {
        return new VirtualRow(new HashMap<>(data), version);
    }
}
