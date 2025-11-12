package io.github.hexlodev.core.interceptor;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * 简化的连接包装器 - 拦截Statement创建
 * 
 * <p>这个类实现了 {@link Connection} 接口，通过装饰器模式包装真实的数据库连接。
 * 主要功能是拦截Statement、PreparedStatement和CallableStatement的创建过程，
 * 返回包装后的对象以支持SQL拦截和日志记录功能。</p>
 * 
 * <p>主要功能：</p>
 * <ul>
 *   <li>拦截 {@code createStatement()} 方法，返回 {@link SimpleInterceptorStatement}</li>
 *   <li>拦截 {@code prepareStatement()} 方法，返回 {@link SimpleInterceptorPreparedStatement}</li>
 *   <li>拦截 {@code prepareCall()} 方法，返回 {@link SimpleInterceptorCallableStatement}</li>
 *   <li>记录连接创建和Statement创建的日志</li>
 *   <li>其他Connection方法直接委托给底层连接</li>
 * </ul>
 * 
 * <p>使用场景：</p>
 * <pre>{@code
 * // 通过拦截器驱动获取连接
 * Connection conn = DriverManager.getConnection("jdbc:interceptor:h2:mem:testdb", props);
 * 
 * // 创建Statement会被拦截
 * Statement stmt = conn.createStatement();
 * // 实际返回的是 SimpleInterceptorStatement
 * 
 * // 创建PreparedStatement会被拦截
 * PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users");
 * // 实际返回的是 SimpleInterceptorPreparedStatement
 * }</pre>
 * 
 * @author hexlodev
 * @since 1.0.0
 * @see Connection
 * @see SimpleInterceptorStatement
 * @see SimpleInterceptorPreparedStatement
 * @see SimpleInterceptorCallableStatement
 */
@Slf4j
public class SimpleInterceptorConnection implements Connection {
    
    /** 被包装的真实数据库连接 */
    private final Connection delegate;
    
    /**
     * 数据源标识（多数据源场景）
     */
    private final String datasourceId;
    
    /**
     * 构造函数（向后兼容）
     * 
     * <p>创建一个新的连接包装器，包装真实的数据库连接。
     * 构造函数会记录连接被拦截的日志信息。</p>
     * 
     * @param delegate 真实的数据库连接，不能为null
     * @throws IllegalArgumentException 如果delegate为null
     */
    public SimpleInterceptorConnection(Connection delegate) {
        this(delegate, null);
    }
    
    /**
     * 构造函数（支持多数据源）
     * 
     * <p>创建一个新的连接包装器，包装真实的数据库连接。
     * 构造函数会记录连接被拦截的日志信息。</p>
     * 
     * @param delegate 真实的数据库连接，不能为null
     * @param datasourceId 数据源标识，如果为null则使用默认值"default"
     * @throws IllegalArgumentException 如果delegate为null
     */
    public SimpleInterceptorConnection(Connection delegate, String datasourceId) {
        if (delegate == null) {
            throw new IllegalArgumentException("Delegate connection cannot be null");
        }
        this.delegate = delegate;
        this.datasourceId = cn.hutool.core.util.StrUtil.isBlank(datasourceId) ? "default" : datasourceId;
        if (log.isDebugEnabled()) {
            log.debug("Connection intercepted: {} (datasource-id: {})",
                    delegate.getClass().getSimpleName(), this.datasourceId);
        }
    }
    
    /**
     * 获取数据源标识
     *
     * @return 数据源标识
     */
    public String getDatasourceId() {
        return datasourceId;
    }
    
    /**
     * 创建Statement对象 - 拦截方法
     * 
     * <p>拦截Statement的创建过程，返回包装后的 {@link SimpleInterceptorStatement} 对象。
     * 包装后的Statement支持SQL拦截、表名解析和性能监控功能。</p>
     * 
     * @return 包装后的Statement对象
     * @throws SQLException 如果创建Statement失败
     * @see SimpleInterceptorStatement
     */
    @Override
    public Statement createStatement() throws SQLException {
        Statement statement = delegate.createStatement();
        if (log.isTraceEnabled()) {
            log.trace("Statement created");
        }
        return new SimpleInterceptorStatement(statement);
    }
    
    /**
     * 创建PreparedStatement对象 - 拦截方法
     * 
     * <p>拦截PreparedStatement的创建过程，返回包装后的 {@link SimpleInterceptorPreparedStatement} 对象。
     * 包装后的PreparedStatement支持SQL拦截、参数记录、表名解析和性能监控功能。</p>
     * 
     * @param sql SQL语句模板，包含参数占位符（?）
     * @return 包装后的PreparedStatement对象
     * @throws SQLException 如果创建PreparedStatement失败
     * @see SimpleInterceptorPreparedStatement
     */
    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        PreparedStatement statement = delegate.prepareStatement(sql);
        if (log.isDebugEnabled()) {
            log.debug("PreparedStatement created (datasource-id: {})", datasourceId);
        }
        // 确保传递正确的 datasource-id
        String dsId = StrUtil.isBlank(datasourceId) ? "default" : datasourceId;
        return new SimpleInterceptorPreparedStatement(statement, sql, dsId);
    }
    
    /**
     * 创建CallableStatement对象 - 拦截方法
     * 
     * <p>拦截CallableStatement的创建过程，返回包装后的 {@link SimpleInterceptorCallableStatement} 对象。
     * 包装后的CallableStatement支持SQL拦截、参数记录、表名解析和性能监控功能。</p>
     * 
     * @param sql 存储过程调用SQL语句
     * @return 包装后的CallableStatement对象
     * @throws SQLException 如果创建CallableStatement失败
     * @see SimpleInterceptorCallableStatement
     */
    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        CallableStatement statement = delegate.prepareCall(sql);
        if (log.isTraceEnabled()) {
            log.trace("CallableStatement created");
        }
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
        if (log.isTraceEnabled()) {
            log.trace("Connection closed");
        }
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
        if (log.isTraceEnabled()) {
            log.trace("Statement created with type={}, concurrency={}", resultSetType, resultSetConcurrency);
        }
        return new SimpleInterceptorStatement(statement);
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        PreparedStatement statement = delegate.prepareStatement(sql, resultSetType, resultSetConcurrency);
        if (log.isTraceEnabled()) {
            log.trace("PreparedStatement created with type={}, concurrency={} (datasource-id: {})",
                    resultSetType, resultSetConcurrency, datasourceId);
        }
        String dsId = StrUtil.isBlank(datasourceId) ? "default" : datasourceId;
        return new SimpleInterceptorPreparedStatement(statement, sql, dsId);
    }
    
    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        CallableStatement statement = delegate.prepareCall(sql, resultSetType, resultSetConcurrency);
        if (log.isTraceEnabled()) {
            log.trace("CallableStatement created with type={}, concurrency={}", resultSetType, resultSetConcurrency);
        }
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
        if (log.isTraceEnabled()) {
            log.trace("Statement created with type={}, concurrency={}, holdability={}",
                    resultSetType, resultSetConcurrency, resultSetHoldability);
        }
        return new SimpleInterceptorStatement(statement);
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        PreparedStatement statement = delegate.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        if (log.isTraceEnabled()) {
            log.trace("PreparedStatement created with type={}, concurrency={}, holdability={} (datasource-id: {})",
                    resultSetType, resultSetConcurrency, resultSetHoldability, datasourceId);
        }
        String dsId = StrUtil.isBlank(datasourceId) ? "default" : datasourceId;
        return new SimpleInterceptorPreparedStatement(statement, sql, dsId);
    }
    
    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        CallableStatement statement = delegate.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        if (log.isTraceEnabled()) {
            log.trace("CallableStatement created with type={}, concurrency={}, holdability={}",
                    resultSetType, resultSetConcurrency, resultSetHoldability);
        }
        return new SimpleInterceptorCallableStatement(statement, sql);
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        PreparedStatement statement = delegate.prepareStatement(sql, autoGeneratedKeys);
        if (log.isTraceEnabled()) {
            log.trace("PreparedStatement created with autoGeneratedKeys={} (datasource-id: {})",
                    autoGeneratedKeys, datasourceId);
        }
        String dsId = StrUtil.isBlank(datasourceId) ? "default" : datasourceId;
        return new SimpleInterceptorPreparedStatement(statement, sql, dsId);
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        PreparedStatement statement = delegate.prepareStatement(sql, columnIndexes);
        if (log.isTraceEnabled()) {
            log.trace("PreparedStatement created with columnIndexes (datasource-id: {})", datasourceId);
        }
        String dsId = StrUtil.isBlank(datasourceId) ? "default" : datasourceId;
        return new SimpleInterceptorPreparedStatement(statement, sql, dsId);
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        PreparedStatement statement = delegate.prepareStatement(sql, columnNames);
        if (log.isTraceEnabled()) {
            log.trace("PreparedStatement created with columnNames (datasource-id: {})", datasourceId);
        }
        String dsId = StrUtil.isBlank(datasourceId) ? "default" : datasourceId;
        return new SimpleInterceptorPreparedStatement(statement, sql, dsId);
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
