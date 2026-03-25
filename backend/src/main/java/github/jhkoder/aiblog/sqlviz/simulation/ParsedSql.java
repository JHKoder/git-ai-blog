package github.jhkoder.aiblog.sqlviz.simulation;

import java.util.List;

/**
 * JSQLParser로 파싱한 SQL 정보.
 * SqlVizSimulationEngine에서 detail/sql 필드 동적 생성에 사용한다.
 */
public record ParsedSql(
        SqlType type,
        String table,
        List<String> columns,
        String whereClause
) {

    public enum SqlType {
        SELECT, UPDATE, INSERT, DELETE, BEGIN, COMMIT, ROLLBACK, UNKNOWN
    }

    /** 파싱 실패 or 지원하지 않는 구문일 때 반환하는 기본값 */
    public static ParsedSql unknown() {
        return new ParsedSql(SqlType.UNKNOWN, "", List.of(), "");
    }

    public boolean hasTable() {
        return table != null && !table.isBlank();
    }

    public boolean hasWhere() {
        return whereClause != null && !whereClause.isBlank();
    }

    /** "table:id" 형식의 RowKey 문자열. WHERE 절이 없으면 table:? 반환 */
    public String toRowKey() {
        if (!hasTable()) return "row:?";
        if (!hasWhere()) return table + ":?";
        // WHERE id=1 → "1", WHERE id = 42 → "42" 단순 추출
        String where = whereClause.replaceAll("\\s+", "");
        String id = extractIdValue(where);
        return table + ":" + id;
    }

    private String extractIdValue(String where) {
        // 패턴: id=숫자 or id=숫자 (대소문자 무관)
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)\\bid\\s*=\\s*(\\d+|'[^']+'|\"[^\"]+\")")
                .matcher(where);
        if (m.find()) {
            return m.group(1).replaceAll("['\"]", "");
        }
        return "?";
    }
}
