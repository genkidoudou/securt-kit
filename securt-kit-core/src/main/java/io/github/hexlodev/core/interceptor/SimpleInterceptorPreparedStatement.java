package io.github.hexlodev.core.interceptor;

import io.github.hexlodev.core.utils.TableNameParser;
import java.sql.*;
import java.util.Collection;
import java.util.logging.Logger;

/**
 * 简化的PreparedStatement包装器 - 只拦截核心执行方法
 */
public class SimpleInterceptorPreparedStatement implements PreparedStatement {
    
    private static final Logger logger = Logger.getLogger(SimpleInterceptorPreparedStatement.class.getName());
    private final PreparedStatement delegate;
    private final String sql;
    
    public SimpleInterceptorPreparedStatement(PreparedStatement delegate, String sql) {
        this.delegate = delegate;
        this.sql = sql;
    }
    
    /**
     * 解析SQL中的表名并打印
     */
    private void logTableNames() {
        try {
            TableNameParser parser = new TableNameParser(sql);
            Collection<String> tables = parser.tables();
            if (!tables.isEmpty()) {
                logger.info("📋 [TABLES] " + String.join(", ", tables));
            }
        } catch (Exception e) {
            logger.warning("Failed to parse table names from SQL: " + e.getMessage());
        }
    }
    
    // 拦截执行方法
    @Override
    public ResultSet executeQuery() throws SQLException {
        logger.info("🔍 [PREPARED QUERY] " + sql);
        logTableNames();
        long startTime = System.currentTimeMillis();
        ResultSet resultSet = delegate.executeQuery();
        long endTime = System.currentTimeMillis();
        logger.info("✅ [PREPARED QUERY RESULT] Executed in " + (endTime - startTime) + "ms");
        return resultSet;
    }
    
    @Override
    public int executeUpdate() throws SQLException {
        logger.info("📝 [PREPARED UPDATE] " + sql);
        logTableNames();
        long startTime = System.currentTimeMillis();
        int result = delegate.executeUpdate();
        long endTime = System.currentTimeMillis();
        logger.info("✅ [PREPARED UPDATE RESULT] Executed in " + (endTime - startTime) + "ms, affected rows: " + result);
        return result;
    }
    
    @Override
    public boolean execute() throws SQLException {
        logger.info("⚡ [PREPARED EXECUTE] " + sql);
        logTableNames();
        long startTime = System.currentTimeMillis();
        boolean result = delegate.execute();
        long endTime = System.currentTimeMillis();
        logger.info("✅ [PREPARED EXECUTE RESULT] Executed in " + (endTime - startTime) + "ms, result: " + result);
        return result;
    }
    
    // 拦截参数设置方法
    @Override
    public void setString(int parameterIndex, String x) throws SQLException {
        logger.fine("Setting string parameter " + parameterIndex + " = " + x);
        delegate.setString(parameterIndex, x);
    }
    
    @Override
    public void setInt(int parameterIndex, int x) throws SQLException {
        logger.fine("Setting int parameter " + parameterIndex + " = " + x);
        delegate.setInt(parameterIndex, x);
    }
    
    @Override
    public void setLong(int parameterIndex, long x) throws SQLException {
        logger.fine("Setting long parameter " + parameterIndex + " = " + x);
        delegate.setLong(parameterIndex, x);
    }
    
    @Override
    public void setDouble(int parameterIndex, double x) throws SQLException {
        logger.fine("Setting double parameter " + parameterIndex + " = " + x);
        delegate.setDouble(parameterIndex, x);
    }
    
    @Override
    public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        logger.fine("Setting boolean parameter " + parameterIndex + " = " + x);
        delegate.setBoolean(parameterIndex, x);
    }
    
    // 其他PreparedStatement方法直接委托
    @Override
    public void setNull(int parameterIndex, int sqlType) throws SQLException {
        delegate.setNull(parameterIndex, sqlType);
    }
    
    @Override
    public void setByte(int parameterIndex, byte x) throws SQLException {
        delegate.setByte(parameterIndex, x);
    }
    
    @Override
    public void setShort(int parameterIndex, short x) throws SQLException {
        delegate.setShort(parameterIndex, x);
    }
    
    @Override
    public void setFloat(int parameterIndex, float x) throws SQLException {
        delegate.setFloat(parameterIndex, x);
    }
    
    @Override
    public void setBigDecimal(int parameterIndex, java.math.BigDecimal x) throws SQLException {
        delegate.setBigDecimal(parameterIndex, x);
    }
    
    @Override
    public void setBytes(int parameterIndex, byte[] x) throws SQLException {
        delegate.setBytes(parameterIndex, x);
    }
    
    @Override
    public void setDate(int parameterIndex, Date x) throws SQLException {
        delegate.setDate(parameterIndex, x);
    }
    
    @Override
    public void setTime(int parameterIndex, Time x) throws SQLException {
        delegate.setTime(parameterIndex, x);
    }
    
    @Override
    public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
        delegate.setTimestamp(parameterIndex, x);
    }
    
    @Override
    public void setAsciiStream(int parameterIndex, java.io.InputStream x, int length) throws SQLException {
        delegate.setAsciiStream(parameterIndex, x, length);
    }
    
    @Override
    public void setUnicodeStream(int parameterIndex, java.io.InputStream x, int length) throws SQLException {
        delegate.setUnicodeStream(parameterIndex, x, length);
    }
    
    @Override
    public void setBinaryStream(int parameterIndex, java.io.InputStream x, int length) throws SQLException {
        delegate.setBinaryStream(parameterIndex, x, length);
    }
    
    @Override
    public void clearParameters() throws SQLException {
        delegate.clearParameters();
    }
    
    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
        delegate.setObject(parameterIndex, x, targetSqlType);
    }
    
    @Override
    public void setObject(int parameterIndex, Object x) throws SQLException {
        delegate.setObject(parameterIndex, x);
    }
    
    @Override
    public void addBatch() throws SQLException {
        logger.info("Adding prepared statement to batch");
        delegate.addBatch();
    }
    
    @Override
    public void setCharacterStream(int parameterIndex, java.io.Reader reader, int length) throws SQLException {
        delegate.setCharacterStream(parameterIndex, reader, length);
    }
    
    @Override
    public void setRef(int parameterIndex, Ref x) throws SQLException {
        delegate.setRef(parameterIndex, x);
    }
    
    @Override
    public void setBlob(int parameterIndex, Blob x) throws SQLException {
        delegate.setBlob(parameterIndex, x);
    }
    
    @Override
    public void setClob(int parameterIndex, Clob x) throws SQLException {
        delegate.setClob(parameterIndex, x);
    }
    
    @Override
    public void setArray(int parameterIndex, Array x) throws SQLException {
        delegate.setArray(parameterIndex, x);
    }
    
    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        return delegate.getMetaData();
    }
    
    @Override
    public void setDate(int parameterIndex, Date x, java.util.Calendar cal) throws SQLException {
        delegate.setDate(parameterIndex, x, cal);
    }
    
    @Override
    public void setTime(int parameterIndex, Time x, java.util.Calendar cal) throws SQLException {
        delegate.setTime(parameterIndex, x, cal);
    }
    
    @Override
    public void setTimestamp(int parameterIndex, Timestamp x, java.util.Calendar cal) throws SQLException {
        delegate.setTimestamp(parameterIndex, x, cal);
    }
    
    @Override
    public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
        delegate.setNull(parameterIndex, sqlType, typeName);
    }
    
    @Override
    public void setURL(int parameterIndex, java.net.URL x) throws SQLException {
        delegate.setURL(parameterIndex, x);
    }
    
    @Override
    public ParameterMetaData getParameterMetaData() throws SQLException {
        return delegate.getParameterMetaData();
    }
    
    @Override
    public void setRowId(int parameterIndex, RowId x) throws SQLException {
        delegate.setRowId(parameterIndex, x);
    }
    
    @Override
    public void setNString(int parameterIndex, String value) throws SQLException {
        delegate.setNString(parameterIndex, value);
    }
    
    @Override
    public void setNCharacterStream(int parameterIndex, java.io.Reader value, long length) throws SQLException {
        delegate.setNCharacterStream(parameterIndex, value, length);
    }
    
    @Override
    public void setNClob(int parameterIndex, NClob value) throws SQLException {
        delegate.setNClob(parameterIndex, value);
    }
    
    @Override
    public void setClob(int parameterIndex, java.io.Reader reader, long length) throws SQLException {
        delegate.setClob(parameterIndex, reader, length);
    }
    
    @Override
    public void setBlob(int parameterIndex, java.io.InputStream inputStream, long length) throws SQLException {
        delegate.setBlob(parameterIndex, inputStream, length);
    }
    
    @Override
    public void setNClob(int parameterIndex, java.io.Reader reader, long length) throws SQLException {
        delegate.setNClob(parameterIndex, reader, length);
    }
    
    @Override
    public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
        delegate.setSQLXML(parameterIndex, xmlObject);
    }
    
    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
        delegate.setObject(parameterIndex, x, targetSqlType, scaleOrLength);
    }
    
    @Override
    public void setAsciiStream(int parameterIndex, java.io.InputStream x, long length) throws SQLException {
        delegate.setAsciiStream(parameterIndex, x, length);
    }
    
    @Override
    public void setBinaryStream(int parameterIndex, java.io.InputStream x, long length) throws SQLException {
        delegate.setBinaryStream(parameterIndex, x, length);
    }
    
    @Override
    public void setCharacterStream(int parameterIndex, java.io.Reader reader, long length) throws SQLException {
        delegate.setCharacterStream(parameterIndex, reader, length);
    }
    
    @Override
    public void setAsciiStream(int parameterIndex, java.io.InputStream x) throws SQLException {
        delegate.setAsciiStream(parameterIndex, x);
    }
    
    @Override
    public void setBinaryStream(int parameterIndex, java.io.InputStream x) throws SQLException {
        delegate.setBinaryStream(parameterIndex, x);
    }
    
    @Override
    public void setCharacterStream(int parameterIndex, java.io.Reader reader) throws SQLException {
        delegate.setCharacterStream(parameterIndex, reader);
    }
    
    @Override
    public void setNCharacterStream(int parameterIndex, java.io.Reader value) throws SQLException {
        delegate.setNCharacterStream(parameterIndex, value);
    }
    
    @Override
    public void setClob(int parameterIndex, java.io.Reader reader) throws SQLException {
        delegate.setClob(parameterIndex, reader);
    }
    
    @Override
    public void setBlob(int parameterIndex, java.io.InputStream inputStream) throws SQLException {
        delegate.setBlob(parameterIndex, inputStream);
    }
    
    @Override
    public void setNClob(int parameterIndex, java.io.Reader reader) throws SQLException {
        delegate.setNClob(parameterIndex, reader);
    }
    
    // 继承自Statement的方法
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
        delegate.addBatch(sql);
    }
    
    @Override
    public void clearBatch() throws SQLException {
        delegate.clearBatch();
    }
    
    @Override
    public int[] executeBatch() throws SQLException {
        return delegate.executeBatch();
    }
    
    @Override
    public void close() throws SQLException {
        logger.info("PreparedStatement closed");
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
        return delegate.execute(sql, autoGeneratedKeys);
    }
    
    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        return delegate.execute(sql, columnIndexes);
    }
    
    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        return delegate.execute(sql, columnNames);
    }
    
    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return delegate.executeUpdate(sql, autoGeneratedKeys);
    }
    
    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return delegate.executeUpdate(sql, columnIndexes);
    }
    
    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        return delegate.executeUpdate(sql, columnNames);
    }
    
    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        return delegate.getGeneratedKeys();
    }
    
    @Override
    public boolean getMoreResults(int current) throws SQLException {
        return delegate.getMoreResults(current);
    }
    
    @Override
    public boolean execute(String sql) throws SQLException {
        logger.info("Executing prepared statement with SQL: " + sql);
        long startTime = System.currentTimeMillis();
        boolean result = delegate.execute(sql);
        long endTime = System.currentTimeMillis();
        logger.info("Prepared statement executed in " + (endTime - startTime) + "ms, result: " + result);
        return result;
    }
    
    @Override
    public int executeUpdate(String sql) throws SQLException {
        logger.info("Executing prepared update with SQL: " + sql);
        long startTime = System.currentTimeMillis();
        int result = delegate.executeUpdate(sql);
        long endTime = System.currentTimeMillis();
        logger.info("Prepared update executed in " + (endTime - startTime) + "ms, affected rows: " + result);
        return result;
    }
    
    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        logger.info("Executing prepared query with SQL: " + sql);
        long startTime = System.currentTimeMillis();
        ResultSet resultSet = delegate.executeQuery(sql);
        long endTime = System.currentTimeMillis();
        logger.info("Prepared query executed in " + (endTime - startTime) + "ms");
        return resultSet;
    }
}
