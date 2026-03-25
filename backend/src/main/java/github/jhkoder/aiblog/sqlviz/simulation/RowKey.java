package github.jhkoder.aiblog.sqlviz.simulation;

/**
 * "table:id" 형식의 Lock 대상 식별자.
 * WHERE 절을 파싱해 추출한 테이블명 + id 값으로 구성한다.
 *
 * 예: UPDATE orders SET ... WHERE id=1 → RowKey("orders", "1") → "orders:1"
 */
public record RowKey(String table, String id) {

    @Override
    public String toString() {
        return table + ":" + id;
    }

    /** ParsedSql에서 RowKey 추출 */
    public static RowKey from(ParsedSql parsed) {
        String raw = parsed.toRowKey();
        int sep = raw.indexOf(':');
        if (sep < 0) return new RowKey(raw, "?");
        return new RowKey(raw.substring(0, sep), raw.substring(sep + 1));
    }

    /** 폴백용 — 파싱 실패 시 순번 기반 레이블 */
    public static RowKey fallback(int index) {
        char label = (char) ('A' + index);
        return new RowKey("Row", String.valueOf(label));
    }
}
