package github.jhkoder.aiblog.sqlviz.simulation;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;

import java.util.ArrayList;
import java.util.List;

/**
 * JSQLParser를 사용해 SQL 문자열을 ParsedSql로 변환하는 유틸 클래스.
 * 파싱 실패 시 ParsedSql.unknown()을 반환한다 — 예외를 던지지 않는다.
 */
public final class SqlParser {

    private SqlParser() {}

    public static ParsedSql parse(String sql) {
        if (sql == null || sql.isBlank()) return ParsedSql.unknown();

        String trimmed = sql.trim();

        // BEGIN / COMMIT / ROLLBACK은 JSQLParser가 파싱하지 않음 — 직접 처리
        String upper = trimmed.toUpperCase();
        if (upper.startsWith("BEGIN")) return new ParsedSql(ParsedSql.SqlType.BEGIN, "", List.of(), "");
        if (upper.startsWith("COMMIT")) return new ParsedSql(ParsedSql.SqlType.COMMIT, "", List.of(), "");
        if (upper.startsWith("ROLLBACK")) return new ParsedSql(ParsedSql.SqlType.ROLLBACK, "", List.of(), "");

        try {
            Statement stmt = CCJSqlParserUtil.parse(trimmed);
            return switch (stmt) {
                case Select s -> parseSelect(s);
                case Update u -> parseUpdate(u);
                case Insert ins -> parseInsert(ins);
                case Delete d -> parseDelete(d);
                default -> ParsedSql.unknown();
            };
        } catch (Exception e) {
            return ParsedSql.unknown();
        }
    }

    private static ParsedSql parseSelect(Select select) {
        if (!(select instanceof PlainSelect plain)) return ParsedSql.unknown();
        String table = extractTableName(plain.getFromItem());
        List<String> columns = extractSelectColumns(plain);
        String where = whereStr(plain.getWhere());
        return new ParsedSql(ParsedSql.SqlType.SELECT, table, columns, where);
    }

    private static ParsedSql parseUpdate(Update update) {
        String table = tableName(update.getTable());
        List<String> columns = update.getUpdateSets().stream()
                .flatMap(s -> s.getColumns().stream())
                .map(c -> c.getColumnName())
                .toList();
        String where = whereStr(update.getWhere());
        return new ParsedSql(ParsedSql.SqlType.UPDATE, table, columns, where);
    }

    private static ParsedSql parseInsert(Insert insert) {
        String table = tableName(insert.getTable());
        List<String> columns = insert.getColumns() != null
                ? insert.getColumns().stream().map(c -> c.getColumnName()).toList()
                : List.of();
        return new ParsedSql(ParsedSql.SqlType.INSERT, table, columns, "");
    }

    private static ParsedSql parseDelete(Delete delete) {
        String table = tableName(delete.getTable());
        String where = whereStr(delete.getWhere());
        return new ParsedSql(ParsedSql.SqlType.DELETE, table, List.of(), where);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String extractTableName(net.sf.jsqlparser.statement.select.FromItem fromItem) {
        if (fromItem instanceof Table t) return t.getName();
        return "";
    }

    private static List<String> extractSelectColumns(PlainSelect plain) {
        if (plain.getSelectItems() == null) return List.of();
        List<String> cols = new ArrayList<>();
        for (var item : plain.getSelectItems()) {
            cols.add(item.toString());
        }
        return cols;
    }

    private static String tableName(Table table) {
        return table != null ? table.getName() : "";
    }

    private static String whereStr(Expression where) {
        return where != null ? where.toString() : "";
    }
}
