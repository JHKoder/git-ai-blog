package github.jhkoder.aiblog.sqlviz.simulation;

import github.jhkoder.aiblog.sqlviz.domain.IsolationLevel;

import java.util.List;

/**
 * JSQLParser로 파싱한 SQL 정보.
 * SqlVizSimulationEngine에서 detail/sql 필드 동적 생성에 사용한다.
 *
 * 추가 메타데이터 (SQL 주석에서 추출):
 *   dbType        : -- DB:[mysql] 주석 → DbType. 없으면 SqlParser.DEFAULT_DB.
 *   isolationLevel: BEGIN ISOLATION LEVEL ... 파싱 결과. 없으면 null.
 *   stepMeta      : -- STEP:[n] TX:[id] 주석 → StepMeta. 없으면 null.
 */
public record ParsedSql(
        SqlType type,
        String table,
        List<String> columns,
        String whereClause,
        DbType dbType,
        IsolationLevel isolationLevel,
        StepMeta stepMeta
) {

    public enum SqlType {
        SELECT, UPDATE, INSERT, DELETE, BEGIN, COMMIT, ROLLBACK, UNKNOWN
    }

    /** -- STEP:[n] TX:[id] 주석에서 추출한 인터리빙 메타데이터 */
    public record StepMeta(int step, String txId) {}

    /** 파싱 실패 or 지원하지 않는 구문일 때 반환하는 기본값 */
    public static ParsedSql unknown() {
        return new ParsedSql(SqlType.UNKNOWN, "", List.of(), "", SqlParser.DEFAULT_DB, null, null);
    }

    /** dbType/isolationLevel/stepMeta 없는 기본 생성자 (하위 호환) */
    public static ParsedSql of(SqlType type, String table, List<String> columns, String whereClause) {
        return new ParsedSql(type, table, columns, whereClause, SqlParser.DEFAULT_DB, null, null);
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
        String where = whereClause.replaceAll("\\s+", "");
        String id = extractIdValue(where);
        return table + ":" + id;
    }

    private String extractIdValue(String where) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)\\bid\\s*=\\s*(\\d+|'[^']+'|\"[^\"]+\")")
                .matcher(where);
        if (m.find()) {
            return m.group(1).replaceAll("['\"]", "");
        }
        return "?";
    }
}
