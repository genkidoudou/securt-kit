package io.github.hexlodev.core.interceptor;

import cn.hutool.core.lang.Pair;
import io.github.hexlodev.core.parser.SecurtkitUtils;
import io.github.hexlodev.core.parser.dto.ColumnTableDto;
import io.github.hexlodev.core.parser.dto.FieldEncryptorInfoDto;
import io.github.hexlodev.core.utils.TableNameParser;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;

import java.sql.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 简化的Statement包装器 - 拦截SQL执行
 *
 * <p>这个类实现了 {@link Statement} 接口，通过装饰器模式包装真实的Statement对象。
 * 主要功能是拦截SQL执行操作，提供SQL日志记录、表名解析和性能监控功能。</p>
 *
 * <p>主要功能：</p>
 * <ul>
 *   <li>拦截 {@code executeQuery()} 方法，记录查询SQL和执行时间</li>
 *   <li>拦截 {@code executeUpdate()} 方法，记录更新SQL和执行时间</li>
 *   <li>拦截 {@code execute()} 方法，记录执行SQL和执行时间</li>
 *   <li>使用 {@link TableNameParser} 解析SQL中的表名</li>
 *   <li>提供详细的日志记录，包括SQL语句、表名和执行性能</li>
 *   <li>其他Statement方法直接委托给底层Statement</li>
 * </ul>
 *
 * <p>日志格式示例：</p>
 * <pre>{@code
 * 🔍 [SQL QUERY] SELECT * FROM users WHERE age > 18
 * 📋 [TABLES] users
 * ✅ [QUERY RESULT] Executed in 5ms
 *
 * 📝 [SQL UPDATE] UPDATE users SET name = 'John' WHERE id = 1
 * 📋 [TABLES] users
 * ✅ [UPDATE RESULT] Executed in 2ms, affected rows: 1
 * }</pre>
 *
 * <p>使用场景：</p>
 * <pre>{@code
 * // 通过拦截器连接创建Statement
 * Statement stmt = conn.createStatement();
 *
 * // 执行查询会被拦截和记录
 * ResultSet rs = stmt.executeQuery("SELECT * FROM users");
 *
 * // 执行更新会被拦截和记录
 * int rows = stmt.executeUpdate("UPDATE users SET name = 'John'");
 * }</pre>
 *
 * @author hexlodev
 * @since 1.0.0
 * @see Statement
 * @see TableNameParser
 * @see SimpleInterceptorConnection
 */
@Slf4j
public class SimpleInterceptorStatement implements Statement {

    /** 被包装的真实Statement对象 */
    private final Statement delegate;

    /**
     * 构造函数
     *
     * <p>创建一个新的Statement包装器，包装真实的Statement对象。</p>
     *
     * @param delegate 真实的Statement对象，不能为null
     * @throws IllegalArgumentException 如果delegate为null
     */
    public SimpleInterceptorStatement(Statement delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("Delegate statement cannot be null");
        }
        this.delegate = delegate;
    }

    /**
     * 解析SQL中的表名并记录日志
     * 
     * <p>使用 {@link TableNameParser} 解析SQL语句中的表名，并将结果记录到日志中。
     * 解析失败时不会影响SQL执行，只会记录警告日志。</p>
     *
     * @param sql 要解析的SQL语句
     */
    private void logTableNames(String sql) {
        try {
            TableNameParser parser = new TableNameParser(sql);
            Collection<String> tables = parser.tables();
            if (!tables.isEmpty()) {
                log.debug("[TABLES] " + String.join(", ", tables));
            }
        } catch (Exception e) {
            log.warn("Failed to parse table names from SQL: " + e.getMessage());
        }
    }

    /**
     * 执行查询SQL - 拦截方法
     *
     * <p>拦截查询SQL的执行，记录SQL语句、解析表名、监控执行时间。
     * 使用 🔍 图标标识查询操作。</p>
     *
     * @param sql 查询SQL语句
     * @return 查询结果集
     * @throws SQLException 如果SQL执行失败
     */
    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        log.info("[SQL QUERY] " + sql);
        logTableNames(sql);
        long startTime = System.currentTimeMillis();
        
        // 解析表集合用于解密
        java.util.Set<String> tables = new java.util.HashSet<>();
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> mapListPair = null;

        try {
            TableNameParser parser = new TableNameParser(sql);
            tables.addAll(parser.tables());
        } catch (Exception ignore) {
            // 解析失败不影响执行
        }
        
        if (SecurtkitUtils.needEncrypt(tables)) {
            try {
                mapListPair = SecurtkitUtils.parseSql(sql);
            } catch (JSQLParserException e) {
                log.warn("Failed to parse SQL for decryption: " + e.getMessage());
            }
        }
        
        try {
            ResultSet resultSet = delegate.executeQuery(sql);
            long endTime = System.currentTimeMillis();
            log.info("[QUERY RESULT] Executed in " + (endTime - startTime) + "ms");
            return ResultSetDecryptingProxy.wrap(resultSet, tables, mapListPair, sql);
        } catch (SQLException e) {
            long endTime = System.currentTimeMillis();
            log.error("[QUERY ERROR] Failed after " + (endTime - startTime) + "ms: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 执行更新SQL - 拦截方法
     *
     * <p>拦截更新SQL的执行，记录SQL语句、解析表名、监控执行时间和影响行数。
     * 使用 📝 图标标识更新操作。</p>
     *
     * @param sql 更新SQL语句（INSERT、UPDATE、DELETE等）
     * @return 受影响的行数
     * @throws SQLException 如果SQL执行失败
     */
    @Override
    public int executeUpdate(String sql) throws SQLException {
        log.info("[SQL UPDATE] " + sql);
        logTableNames(sql);
        long startTime = System.currentTimeMillis();
        try {
            int result = delegate.executeUpdate(sql);
            long endTime = System.currentTimeMillis();
            log.info("[UPDATE RESULT] Executed in " + (endTime - startTime) + "ms, affected rows: " + result);
            return result;
        } catch (SQLException e) {
            long endTime = System.currentTimeMillis();
            log.error("[UPDATE ERROR] Failed after " + (endTime - startTime) + "ms: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 执行SQL - 拦截方法
     *
     * <p>拦截SQL的执行，记录SQL语句、解析表名、监控执行时间和执行结果。
     * 使用 ⚡ 图标标识执行操作。</p>
     *
     * @param sql SQL语句
     * @return 如果第一个结果是ResultSet对象则返回true，否则返回false
     * @throws SQLException 如果SQL执行失败
     */
    @Override
    public boolean execute(String sql) throws SQLException {
        log.info("[SQL EXECUTE] " + sql);
        logTableNames(sql);
        long startTime = System.currentTimeMillis();
        try {
            boolean result = delegate.execute(sql);
            long endTime = System.currentTimeMillis();
            log.info("[EXECUTE RESULT] Executed in " + (endTime - startTime) + "ms, result: " + result);
            return result;
        } catch (SQLException e) {
            long endTime = System.currentTimeMillis();
            log.error("[EXECUTE ERROR] Failed after " + (endTime - startTime) + "ms: " + e.getMessage());
            throw e;
        }
    }

    // 其他Statement方法直接委托
    @Override
    public int getMaxFieldSize() throws SQLException {
        return delegate.getMaxFieldSize();
    }

    @Override
    public void setMaxFieldSize(int max) throws SQLException {
        delegate.setMaxFieldSize(max);
    }

    @Override
    public int getMaxRows() throws SQLException {
        return delegate.getMaxRows();
    }

    @Override
    public void setMaxRows(int max) throws SQLException {
        delegate.setMaxRows(max);
    }

    @Override
    public void setEscapeProcessing(boolean enable) throws SQLException {
        delegate.setEscapeProcessing(enable);
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        return delegate.getQueryTimeout();
    }

    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
        delegate.setQueryTimeout(seconds);
    }

    @Override
    public void cancel() throws SQLException {
        delegate.cancel();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return delegate.getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        delegate.clearWarnings();
    }

    @Override
    public void setCursorName(String name) throws SQLException {
        delegate.setCursorName(name);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return delegate.getConnection();
    }

    @Override
    public int getUpdateCount() throws SQLException {
        return delegate.getUpdateCount();
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        return delegate.getMoreResults();
    }

    @Override
    public void setFetchDirection(int direction) throws SQLException {
        delegate.setFetchDirection(direction);
    }

    @Override
    public int getFetchDirection() throws SQLException {
        return delegate.getFetchDirection();
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {
        delegate.setFetchSize(rows);
    }

    @Override
    public int getFetchSize() throws SQLException {
        return delegate.getFetchSize();
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        return delegate.getResultSetConcurrency();
    }

    @Override
    public int getResultSetType() throws SQLException {
        return delegate.getResultSetType();
    }

    @Override
    public void addBatch(String sql) throws SQLException {
        log.info("Adding to batch: " + sql);
        delegate.addBatch(sql);
    }

    @Override
    public void clearBatch() throws SQLException {
        log.info("Clearing batch");
        delegate.clearBatch();
    }

    @Override
    public int[] executeBatch() throws SQLException {
        log.info("Executing batch");
        long startTime = System.currentTimeMillis();
        int[] result = delegate.executeBatch();
        long endTime = System.currentTimeMillis();
        log.info("Batch executed in " + (endTime - startTime) + "ms");
        return result;
    }

    @Override
    public void close() throws SQLException {
        log.info("Statement closed");
        delegate.close();
    }

    @Override
    public boolean isClosed() throws SQLException {
        return delegate.isClosed();
    }

    @Override
    public void setPoolable(boolean poolable) throws SQLException {
        delegate.setPoolable(poolable);
    }

    @Override
    public boolean isPoolable() throws SQLException {
        return delegate.isPoolable();
    }

    @Override
    public void closeOnCompletion() throws SQLException {
        delegate.closeOnCompletion();
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        return delegate.isCloseOnCompletion();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return delegate.isWrapperFor(iface);
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        return delegate.getResultSetHoldability();
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        return delegate.getResultSet();
    }

    // 添加所有缺失的execute方法
    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        log.info("Executing statement with autoGeneratedKeys: " + sql);
        long startTime = System.currentTimeMillis();
        boolean result = delegate.execute(sql, autoGeneratedKeys);
        long endTime = System.currentTimeMillis();
        log.info("Statement executed in " + (endTime - startTime) + "ms, result: " + result);
        return result;
    }

    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        log.info("Executing statement with column indexes: " + sql);
        long startTime = System.currentTimeMillis();
        boolean result = delegate.execute(sql, columnIndexes);
        long endTime = System.currentTimeMillis();
        log.info("Statement executed in " + (endTime - startTime) + "ms, result: " + result);
        return result;
    }

    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        log.info("Executing statement with column names: " + sql);
        long startTime = System.currentTimeMillis();
        boolean result = delegate.execute(sql, columnNames);
        long endTime = System.currentTimeMillis();
        log.info("Statement executed in " + (endTime - startTime) + "ms, result: " + result);
        return result;
    }

    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        log.info("Executing update with autoGeneratedKeys: " + sql);
        long startTime = System.currentTimeMillis();
        int result = delegate.executeUpdate(sql, autoGeneratedKeys);
        long endTime = System.currentTimeMillis();
        log.info("Update executed in " + (endTime - startTime) + "ms, affected rows: " + result);
        return result;
    }

    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        log.info("Executing update with column indexes: " + sql);
        long startTime = System.currentTimeMillis();
        int result = delegate.executeUpdate(sql, columnIndexes);
        long endTime = System.currentTimeMillis();
        log.info("Update executed in " + (endTime - startTime) + "ms, affected rows: " + result);
        return result;
    }

    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        log.info("Executing update with column names: " + sql);
        long startTime = System.currentTimeMillis();
        int result = delegate.executeUpdate(sql, columnNames);
        long endTime = System.currentTimeMillis();
        log.info("Update executed in " + (endTime - startTime) + "ms, affected rows: " + result);
        return result;
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        return delegate.getGeneratedKeys();
    }

    @Override
    public boolean getMoreResults(int current) throws SQLException {
        return delegate.getMoreResults(current);
    }
}
