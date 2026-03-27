package github.jhkoder.aiblog.sqlviz.simulation;

import github.jhkoder.aiblog.sqlviz.domain.IsolationLevel;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JSQLParser를 사용해 SQL 문자열을 ParsedSql로 변환하는 유틸 클래스.
 * 파싱 실패 시 ParsedSql.unknown()을 반환한다 — 예외를 던지지 않는다.
 *
 * 지원하는 SQL 주석 메타데이터 (JSQLParser 전달 전 raw 스캔):
 *   -- DB:[mysql]           → DbType 오버라이드 (없으면 DEFAULT_DB)
 *   -- STEP:[n] TX:[id]     → 인터리빙 실행 순서 지정
 *   BEGIN ISOLATION LEVEL … → ParsedSql.isolationLevel 추출
 */
public final class SqlParser {

    /** -- DB:[...] 주석이 없을 때 적용되는 기본 DB 방언. 변경 시 이 상수만 수정. */
    public static final DbType DEFAULT_DB = DbType.POSTGRESQL;

    private static final Pattern DB_COMMENT    = Pattern.compile("--\\s*DB:\\s*\\[([^]]+)]", Pattern.CASE_INSENSITIVE);
    private static final Pattern STEP_COMMENT  = Pattern.compile("--\\s*STEP:\\s*\\[(\\d+)]\\s*TX:\\s*\\[([^]]+)]", Pattern.CASE_INSENSITIVE);
    private static final Pattern ISO_LEVEL     = Pattern.compile(
            "BEGIN\\s+(?:TRANSACTION\\s+)?ISOLATION\\s+LEVEL\\s+(READ\\s+UNCOMMITTED|READ\\s+COMMITTED|REPEATABLE\\s+READ|SERIALIZABLE)",
            Pattern.CASE_INSENSITIVE);

    private SqlParser() {}

    public static ParsedSql parse(String sql) {
        if (sql == null || sql.isBlank()) return ParsedSql.unknown();

        // ── 1. 주석에서 메타데이터 추출 ────────────────────────────────────────────
        DbType dbType = extractDbType(sql);
        ParsedSql.StepMeta stepMeta = extractStepMeta(sql);

        // ── 2. 주석 제거 후 실제 SQL만 추출 ─────────────────────────────────────────
        String cleanSql = removeLineComments(sql).trim();

        if (cleanSql.isBlank()) return ParsedSql.unknown();

        String upper = cleanSql.toUpperCase();

        // BEGIN / COMMIT / ROLLBACK
        if (upper.startsWith("BEGIN")) {
            IsolationLevel isoLevel = extractIsolationLevel(cleanSql);
            return new ParsedSql(ParsedSql.SqlType.BEGIN, "", List.of(), "", dbType, isoLevel, stepMeta);
        }
        if (upper.startsWith("COMMIT"))   return new ParsedSql(ParsedSql.SqlType.COMMIT,   "", List.of(), "", dbType, null, stepMeta);
        if (upper.startsWith("ROLLBACK")) return new ParsedSql(ParsedSql.SqlType.ROLLBACK, "", List.of(), "", dbType, null, stepMeta);

        try {
            Statement stmt = CCJSqlParserUtil.parse(cleanSql);
            ParsedSql base = switch (stmt) {
                case Select s   -> parseSelect(s);
                case Update u   -> parseUpdate(u);
                case Insert ins -> parseInsert(ins);
                case Delete d   -> parseDelete(d);
                default         -> ParsedSql.unknown();
            };
            // base에 메타데이터 병합
            return new ParsedSql(base.type(), base.table(), base.columns(), base.whereClause(),
                    dbType, null, stepMeta);
        } catch (Exception e) {
            return ParsedSql.unknown();
        }
    }

    // ── 메타데이터 추출 ──────────────────────────────────────────────────────────

    private static DbType extractDbType(String sql) {
        Matcher m = DB_COMMENT.matcher(sql);
        return m.find() ? DbType.from(m.group(1)) : DEFAULT_DB;
    }

    private static ParsedSql.StepMeta extractStepMeta(String sql) {
        Matcher m = STEP_COMMENT.matcher(sql);
        if (!m.find()) return null;
        int step = Integer.parseInt(m.group(1));
        String txId = m.group(2).trim();
        return new ParsedSql.StepMeta(step, txId);
    }

    private static IsolationLevel extractIsolationLevel(String sql) {
        Matcher m = ISO_LEVEL.matcher(sql);
        if (!m.find()) return null;
        String level = m.group(1).trim().toUpperCase().replaceAll("\\s+", "_");
        try {
            return IsolationLevel.valueOf(level);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** -- 로 시작하는 라인 주석 제거 (메타데이터 이미 추출 후 호출) */
    private static String removeLineComments(String sql) {
        StringBuilder sb = new StringBuilder();
        for (String line : sql.lines().toList()) {
            String trimmed = line.stripLeading();
            if (!trimmed.startsWith("--")) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    // ── SQL 타입별 파싱 ──────────────────────────────────────────────────────────

    private static ParsedSql parseSelect(Select select) {
        if (!(select instanceof PlainSelect plain)) return ParsedSql.unknown();
        String table = extractTableName(plain.getFromItem());
        List<String> columns = extractSelectColumns(plain);
        String where = whereStr(plain.getWhere());
        return ParsedSql.of(ParsedSql.SqlType.SELECT, table, columns, where);
    }

    private static ParsedSql parseUpdate(Update update) {
        String table = tableName(update.getTable());
        List<String> columns = update.getUpdateSets().stream()
                .flatMap(s -> s.getColumns().stream())
                .map(c -> c.getColumnName())
                .toList();
        String where = whereStr(update.getWhere());
        return ParsedSql.of(ParsedSql.SqlType.UPDATE, table, columns, where);
    }

    private static ParsedSql parseInsert(Insert insert) {
        String table = tableName(insert.getTable());
        List<String> columns = insert.getColumns() != null
                ? insert.getColumns().stream().map(c -> c.getColumnName()).toList()
                : List.of();
        return ParsedSql.of(ParsedSql.SqlType.INSERT, table, columns, "");
    }

    private static ParsedSql parseDelete(Delete delete) {
        String table = tableName(delete.getTable());
        String where = whereStr(delete.getWhere());
        return ParsedSql.of(ParsedSql.SqlType.DELETE, table, List.of(), where);
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
