package io.github.hexlodev.core.interceptor;

import java.sql.*;
import java.util.logging.Logger;

/**
 * 简化的Statement包装器 - 只拦截核心执行方法
 */
public class SimpleInterceptorStatement implements Statement {
    
    private static final Logger logger = Logger.getLogger(SimpleInterceptorStatement.class.getName());
    private final Statement delegate;
    
    public SimpleInterceptorStatement(Statement delegate) {
        this.delegate = delegate;
    }
    
    // 拦截执行方法
    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        logger.info("🔍 [SQL QUERY] " + sql);
        long startTime = System.currentTimeMillis();
        ResultSet resultSet = delegate.executeQuery(sql);
        long endTime = System.currentTimeMillis();
        logger.info("✅ [QUERY RESULT] Executed in " + (endTime - startTime) + "ms");
        return resultSet;
    }
    
    @Override
    public int executeUpdate(String sql) throws SQLException {
        logger.info("📝 [SQL UPDATE] " + sql);
        long startTime = System.currentTimeMillis();
        int result = delegate.executeUpdate(sql);
        long endTime = System.currentTimeMillis();
        logger.info("✅ [UPDATE RESULT] Executed in " + (endTime - startTime) + "ms, affected rows: " + result);
        return result;
    }
    
    @Override
    public boolean execute(String sql) throws SQLException {
        logger.info("⚡ [SQL EXECUTE] " + sql);
        long startTime = System.currentTimeMillis();
        boolean result = delegate.execute(sql);
        long endTime = System.currentTimeMillis();
        logger.info("✅ [EXECUTE RESULT] Executed in " + (endTime - startTime) + "ms, result: " + result);
        return result;
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
        logger.info("Adding to batch: " + sql);
        delegate.addBatch(sql);
    }
    
    @Override
    public void clearBatch() throws SQLException {
        logger.info("Clearing batch");
        delegate.clearBatch();
    }
    
    @Override
    public int[] executeBatch() throws SQLException {
        logger.info("Executing batch");
        long startTime = System.currentTimeMillis();
        int[] result = delegate.executeBatch();
        long endTime = System.currentTimeMillis();
        logger.info("Batch executed in " + (endTime - startTime) + "ms");
        return result;
    }
    
    @Override
    public void close() throws SQLException {
        logger.info("Statement closed");
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
        logger.info("Executing statement with autoGeneratedKeys: " + sql);
        long startTime = System.currentTimeMillis();
        boolean result = delegate.execute(sql, autoGeneratedKeys);
        long endTime = System.currentTimeMillis();
        logger.info("Statement executed in " + (endTime - startTime) + "ms, result: " + result);
        return result;
    }
    
    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        logger.info("Executing statement with column indexes: " + sql);
        long startTime = System.currentTimeMillis();
        boolean result = delegate.execute(sql, columnIndexes);
        long endTime = System.currentTimeMillis();
        logger.info("Statement executed in " + (endTime - startTime) + "ms, result: " + result);
        return result;
    }
    
    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        logger.info("Executing statement with column names: " + sql);
        long startTime = System.currentTimeMillis();
        boolean result = delegate.execute(sql, columnNames);
        long endTime = System.currentTimeMillis();
        logger.info("Statement executed in " + (endTime - startTime) + "ms, result: " + result);
        return result;
    }
    
    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        logger.info("Executing update with autoGeneratedKeys: " + sql);
        long startTime = System.currentTimeMillis();
        int result = delegate.executeUpdate(sql, autoGeneratedKeys);
        long endTime = System.currentTimeMillis();
        logger.info("Update executed in " + (endTime - startTime) + "ms, affected rows: " + result);
        return result;
    }
    
    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        logger.info("Executing update with column indexes: " + sql);
        long startTime = System.currentTimeMillis();
        int result = delegate.executeUpdate(sql, columnIndexes);
        long endTime = System.currentTimeMillis();
        logger.info("Update executed in " + (endTime - startTime) + "ms, affected rows: " + result);
        return result;
    }
    
    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        logger.info("Executing update with column names: " + sql);
        long startTime = System.currentTimeMillis();
        int result = delegate.executeUpdate(sql, columnNames);
        long endTime = System.currentTimeMillis();
        logger.info("Update executed in " + (endTime - startTime) + "ms, affected rows: " + result);
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
