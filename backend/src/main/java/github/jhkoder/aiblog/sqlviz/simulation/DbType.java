package github.jhkoder.aiblog.sqlviz.simulation;

/**
 * SQL 방언(Dialect) 타입.
 * SqlParser.DEFAULT_DB가 기본값으로 사용된다.
 * -- DB:[mysql] 주석으로 SQL 블록별로 오버라이드 가능.
 */
public enum DbType {
    POSTGRESQL,
    MYSQL,
    ORACLE,
    GENERIC;

    /** 문자열 → DbType 변환. 인식 불가 시 GENERIC 반환. */
    public static DbType from(String s) {
        if (s == null) return GENERIC;
        return switch (s.trim().toLowerCase()) {
            case "postgresql", "postgres", "pg" -> POSTGRESQL;
            case "mysql"                        -> MYSQL;
            case "oracle"                       -> ORACLE;
            default                             -> GENERIC;
        };
    }
}
