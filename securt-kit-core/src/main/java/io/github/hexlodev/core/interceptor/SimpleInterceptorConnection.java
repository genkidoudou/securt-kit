package io.github.hexlodev.core.interceptor;

import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * 简化的连接包装器 - 只拦截Statement创建
 */
public class SimpleInterceptorConnection implements Connection {
    
    private static final Logger logger = Logger.getLogger(SimpleInterceptorConnection.class.getName());
    private final Connection delegate;
    
    public SimpleInterceptorConnection(Connection delegate) {
        this.delegate = delegate;
        logger.info("Connection intercepted: " + delegate.getClass().getSimpleName());
    }
    
    // 拦截Statement创建方法
    @Override
    public Statement createStatement() throws SQLException {
        Statement statement = delegate.createStatement();
        logger.info("Statement created");
        return new SimpleInterceptorStatement(statement);
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        PreparedStatement statement = delegate.prepareStatement(sql);
        logger.info("PreparedStatement created for SQL: " + sql);
        return new SimpleInterceptorPreparedStatement(statement, sql);
    }
    
    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        CallableStatement statement = delegate.prepareCall(sql);
        logger.info("CallableStatement created for SQL: " + sql);
        return new SimpleInterceptorCallableStatement(statement, sql);
    }
    
    // 其他Connection方法直接委托给底层连接
    @Override
    public String nativeSQL(String sql) throws SQLException {
        return delegate.nativeSQL(sql);
    }
    
    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        delegate.setAutoCommit(autoCommit);
    }
    
    @Override
    public boolean getAutoCommit() throws SQLException {
        return delegate.getAutoCommit();
    }
    
    @Override
    public void commit() throws SQLException {
        delegate.commit();
    }
    
    @Override
    public void rollback() throws SQLException {
        delegate.rollback();
    }
    
    @Override
    public void close() throws SQLException {
        logger.info("Connection closed");
        delegate.close();
    }
    
    @Override
    public boolean isClosed() throws SQLException {
        return delegate.isClosed();
    }
    
    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        return delegate.getMetaData();
    }
    
    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        delegate.setReadOnly(readOnly);
    }
    
    @Override
    public boolean isReadOnly() throws SQLException {
        return delegate.isReadOnly();
    }
    
    @Override
    public void setCatalog(String catalog) throws SQLException {
        delegate.setCatalog(catalog);
    }
    
    @Override
    public String getCatalog() throws SQLException {
        return delegate.getCatalog();
    }
    
    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        delegate.setTransactionIsolation(level);
    }
    
    @Override
    public int getTransactionIsolation() throws SQLException {
        return delegate.getTransactionIsolation();
    }
    
    @Override
    public SQLWarning getWarnings() throws SQLException {
        return delegate.getWarnings();
    }
    
    @Override
    public void clearWarnings() throws SQLException {
        delegate.clearWarnings();
    }
    
    // JDBC 2.0 methods
    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        Statement statement = delegate.createStatement(resultSetType, resultSetConcurrency);
        logger.info("Statement created with type=" + resultSetType + ", concurrency=" + resultSetConcurrency);
        return new SimpleInterceptorStatement(statement);
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        PreparedStatement statement = delegate.prepareStatement(sql, resultSetType, resultSetConcurrency);
        logger.info("PreparedStatement created with type=" + resultSetType + ", concurrency=" + resultSetConcurrency);
        return new SimpleInterceptorPreparedStatement(statement, sql);
    }
    
    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        CallableStatement statement = delegate.prepareCall(sql, resultSetType, resultSetConcurrency);
        logger.info("CallableStatement created with type=" + resultSetType + ", concurrency=" + resultSetConcurrency);
        return new SimpleInterceptorCallableStatement(statement, sql);
    }
    
    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return delegate.getTypeMap();
    }
    
    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        delegate.setTypeMap(map);
    }
    
    // JDBC 3.0 methods
    @Override
    public void setHoldability(int holdability) throws SQLException {
        delegate.setHoldability(holdability);
    }
    
    @Override
    public int getHoldability() throws SQLException {
        return delegate.getHoldability();
    }
    
    @Override
    public Savepoint setSavepoint() throws SQLException {
        return delegate.setSavepoint();
    }
    
    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        return delegate.setSavepoint(name);
    }
    
    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        delegate.rollback(savepoint);
    }
    
    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        delegate.releaseSavepoint(savepoint);
    }
    
    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        Statement statement = delegate.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
        logger.info("Statement created with type=" + resultSetType + ", concurrency=" + resultSetConcurrency + ", holdability=" + resultSetHoldability);
        return new SimpleInterceptorStatement(statement);
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        PreparedStatement statement = delegate.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        logger.info("PreparedStatement created with type=" + resultSetType + ", concurrency=" + resultSetConcurrency + ", holdability=" + resultSetHoldability);
        return new SimpleInterceptorPreparedStatement(statement, sql);
    }
    
    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        CallableStatement statement = delegate.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        logger.info("CallableStatement created with type=" + resultSetType + ", concurrency=" + resultSetConcurrency + ", holdability=" + resultSetHoldability);
        return new SimpleInterceptorCallableStatement(statement, sql);
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        PreparedStatement statement = delegate.prepareStatement(sql, autoGeneratedKeys);
        logger.info("PreparedStatement created with autoGeneratedKeys=" + autoGeneratedKeys);
        return new SimpleInterceptorPreparedStatement(statement, sql);
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        PreparedStatement statement = delegate.prepareStatement(sql, columnIndexes);
        logger.info("PreparedStatement created with columnIndexes");
        return new SimpleInterceptorPreparedStatement(statement, sql);
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        PreparedStatement statement = delegate.prepareStatement(sql, columnNames);
        logger.info("PreparedStatement created with columnNames");
        return new SimpleInterceptorPreparedStatement(statement, sql);
    }
    
    // JDBC 4.0 methods
    @Override
    public Clob createClob() throws SQLException {
        return delegate.createClob();
    }
    
    @Override
    public Blob createBlob() throws SQLException {
        return delegate.createBlob();
    }
    
    @Override
    public NClob createNClob() throws SQLException {
        return delegate.createNClob();
    }
    
    @Override
    public SQLXML createSQLXML() throws SQLException {
        return delegate.createSQLXML();
    }
    
    @Override
    public boolean isValid(int timeout) throws SQLException {
        return delegate.isValid(timeout);
    }
    
    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        delegate.setClientInfo(name, value);
    }
    
    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        delegate.setClientInfo(properties);
    }
    
    @Override
    public String getClientInfo(String name) throws SQLException {
        return delegate.getClientInfo(name);
    }
    
    @Override
    public Properties getClientInfo() throws SQLException {
        return delegate.getClientInfo();
    }
    
    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        return delegate.createArrayOf(typeName, elements);
    }
    
    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        return delegate.createStruct(typeName, attributes);
    }
    
    // JDBC 4.1 methods
    @Override
    public void setSchema(String schema) throws SQLException {
        delegate.setSchema(schema);
    }
    
    @Override
    public String getSchema() throws SQLException {
        return delegate.getSchema();
    }
    
    @Override
    public void abort(Executor executor) throws SQLException {
        delegate.abort(executor);
    }
    
    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        delegate.setNetworkTimeout(executor, milliseconds);
    }
    
    @Override
    public int getNetworkTimeout() throws SQLException {
        return delegate.getNetworkTimeout();
    }
    
    // JDBC 4.2 methods
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return delegate.unwrap(iface);
    }
    
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return delegate.isWrapperFor(iface);
    }
}
