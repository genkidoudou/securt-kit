package io.github.hexlodev.core.exception;

/**
 * SQL 解析异常
 * <p>
 * 当 SQL 语句解析失败时抛出此异常。
 * 通常包装 JSQLParserException。
 * </p>
 *
 * @author hexlodev
 * @since 1.0.0
 */
public class SqlParseException extends SecurtKitException {

    private final String sql;

    public SqlParseException(String message, Throwable cause, String sql) {
        super(message, cause);
        this.sql = sql;
    }

    public SqlParseException(String message, String sql) {
        super(message);
        this.sql = sql;
    }

    public String getSql() {
        return sql;
    }

    @Override
    public String toString() {
        return String.format("SqlParseException{sql='%s', message='%s'}", 
                sql != null ? sql : "null", 
                getMessage());
    }
}

