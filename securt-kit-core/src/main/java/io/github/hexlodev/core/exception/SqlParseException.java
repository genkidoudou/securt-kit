package io.github.hexlodev.core.exception;

/**
 * SQL 解析异常
 * 
 * <p>当 SQL 语句解析失败时抛出此异常。
 * 通常包装 {@link net.sf.jsqlparser.JSQLParserException}，提供更友好的错误信息。</p>
 * 
 * <p>使用场景：</p>
 * <ul>
 *   <li>SQL 语法错误</li>
 *   <li>不支持的 SQL 语句类型</li>
 *   <li>SQL 解析器无法识别的语法</li>
 *   <li>SQL 语句格式不正确</li>
 * </ul>
 * 
 * <p>异常处理：</p>
 * <pre>{@code
 * try {
 *     Pair<...> result = SecurtkitUtils.parseSql(sql);
 * } catch (SqlParseException e) {
 *     log.error("SQL 解析失败 [sql={}]: {}", 
 *             e.getSql(), e.getMessage(), e);
 *     // 根据失败策略处理：FAIL_FAST 抛出异常，FALLBACK 跳过加密
 * }
 * }</pre>
 *
 * @author hexlodev
 * @since 1.0.0
 * @see SecurtKitException
 * @see net.sf.jsqlparser.JSQLParserException
 * @see SecurtkitUtils
 */
public class SqlParseException extends SecurtKitException {

    /**
     * 导致解析失败的 SQL 语句
     */
    private final String sql;

    /**
     * 构造函数
     * 
     * @param message 异常消息
     * @param cause 导致此异常的异常（通常是 JSQLParserException）
     * @param sql 导致解析失败的 SQL 语句
     */
    public SqlParseException(String message, Throwable cause, String sql) {
        super(message, cause);
        this.sql = sql;
    }

    /**
     * 构造函数
     * 
     * @param message 异常消息
     * @param sql 导致解析失败的 SQL 语句
     */
    public SqlParseException(String message, String sql) {
        super(message);
        this.sql = sql;
    }

    /**
     * 获取导致解析失败的 SQL 语句
     * 
     * <p>注意：在生产环境日志中，SQL 可能包含敏感信息，应该脱敏处理。</p>
     * 
     * @return SQL 语句
     */
    public String getSql() {
        return sql;
    }

    /**
     * 返回异常的字符串表示
     * 
     * <p>包含 SQL 语句的摘要信息（可能截断），避免日志过长。</p>
     * 
     * @return 异常的字符串表示
     */
    @Override
    public String toString() {
        String sqlPreview = sql != null && sql.length() > 100 
                ? sql.substring(0, 100) + "..." 
                : sql;
        return String.format("SqlParseException{sql='%s', message='%s'}", 
                sqlPreview, getMessage());
    }
}

