package io.github.hexlodev.core.interceptor;

import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.StrUtil;
import io.github.hexlodev.core.parser.SecurtkitUtils;
import io.github.hexlodev.core.parser.dto.ColumnTableDto;
import io.github.hexlodev.core.parser.dto.FieldEncryptorInfoDto;
import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;

import java.sql.*;
import java.sql.Date;
import java.util.*;

/**
 * PreparedStatement拦截器 - 实现字段加密解密功能
 *
 * <p>该类实现了{@link PreparedStatement}接口，通过装饰器模式包装真实的PreparedStatement对象。
 * 主要功能是在SQL执行前后进行字段的自动加密和解密处理。</p>
 *
 * <p>主要功能：</p>
 * <ul>
 *   <li>拦截PreparedStatement的参数设置方法，对需要加密的字段值进行加密处理</li>
 *   <li>拦截SQL执行方法（executeQuery、executeUpdate、execute），记录SQL日志和性能指标</li>
 *   <li>对查询结果集进行包装，实现字段的自动解密</li>
 *   <li>支持INSERT、UPDATE、DELETE等SQL语句的字段加密</li>
 * </ul>
 *
 * <p>加密流程：</p>
 * <ol>
 *   <li>解析SQL语句，识别需要加密的表和字段</li>
 *   <li>在设置参数时，如果参数对应需要加密的字段，则调用加密策略进行加密</li>
 *   <li>执行SQL时，使用加密后的参数值</li>
 *   <li>查询结果返回时，自动解密加密字段的值</li>
 * </ol>
 *
 * @author hexlodev
 * @see PreparedStatement
 * @see FieldEncryptorStrategy
 * @see ResultSetDecryptingProxy
 * @since 1.0.0
 */
@Slf4j
public class SimpleInterceptorPreparedStatement implements PreparedStatement {

    /** 日志记录器 */

    /**
     * 被包装的真实PreparedStatement对象
     */
    private final PreparedStatement delegate;

    /**
     * 原始SQL语句
     */
    private final String sql;

    /**
     * 数据源标识（多数据源场景）
     */
    private final String datasourceId;

    /**
     * SQL语句中涉及的表名集合
     *
     * @since 1.0.0
     */
    private HashSet<String> tables;

    /**
     * 参数值缓存，用于生成最终执行的SQL和日志输出
     * key: 参数索引（从1开始，对应JDBC规范）
     * value: 参数值（已加密处理的值）
     */
    private final Map<Integer, Object> parameterValues = new LinkedHashMap<>();

    /**
     * SQL解析结果，包含占位符到表字段的映射和需要加密的字段信息
     * 仅在表需要加密时才会进行解析
     */
    private Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> pair;

    /**
     * 参数加密器
     */
    private final ParameterEncryptor parameterEncryptor;

    /**
     * SQL 执行器
     */
    private final SqlExecutor sqlExecutor;

    @FunctionalInterface
    private interface SqlStringConsumer {
        void accept(String value) throws SQLException;
    }

    private boolean applyEncryptedString(int parameterIndex,
                                         String encryptedValue,
                                         SqlStringConsumer applier,
                                         String context) throws SQLException {
        if (encryptedValue == null) {
            return false;
        }
        try {
            applier.accept(encryptedValue);
            parameterValues.put(parameterIndex, encryptedValue);
            return true;
        } catch (SQLException ex) {
            log.warn("Failed to apply encrypted value for {} at parameter index {} (datasource-id: {}): {}",
                    context, parameterIndex, datasourceId, ex.getMessage());
            return false;
        }
    }

    private Clob toClob(String value) throws SQLException {
        Clob clob = delegate.getConnection().createClob();
        clob.setString(1, value);
        return clob;
    }

    private NClob toNClob(String value) throws SQLException {
        NClob nClob = delegate.getConnection().createNClob();
        nClob.setString(1, value);
        return nClob;
    }

    private void cacheOriginalClobValue(int parameterIndex, Clob clob) {
        if (clob == null) {
            return;
        }
        try {
            String value = clobToString(clob);
            if (value != null) {
                parameterValues.put(parameterIndex, value);
            }
        } catch (Exception ignored) {
            // 读取失败不影响执行，日志在调用处已经处理
        }
    }

    private void cacheOriginalNClobValue(int parameterIndex, NClob nClob) {
        if (nClob == null) {
            return;
        }
        try {
            String value = nClobToString(nClob);
            if (value != null) {
                parameterValues.put(parameterIndex, value);
            }
        } catch (Exception ignored) {
            // 读取失败不影响执行
        }
    }

    /**
     * 构造函数（向后兼容）
     *
     * <p>创建PreparedStatement拦截器，初始化时：
     * <ol>
     *   <li>解析SQL语句中的表名</li>
     *   <li>判断是否需要加密处理</li>
     *   <li>如果需要加密，则解析SQL获取字段映射关系</li>
     * </ol>
     *
     * @param delegate 真实的PreparedStatement对象，不能为null
     * @param sql      原始SQL语句，不能为null或空
     * @throws IllegalArgumentException 如果delegate或sql为null
     * @throws RuntimeException         如果SQL解析失败
     */
    public SimpleInterceptorPreparedStatement(PreparedStatement delegate, String sql) {
        this(delegate, sql, null);
    }

    /**
     * 构造函数（支持多数据源）
     *
     * <p>创建PreparedStatement拦截器，初始化时：
     * <ol>
     *   <li>解析SQL语句中的表名</li>
     *   <li>判断是否需要加密处理（根据数据源标识）</li>
     *   <li>如果需要加密，则解析SQL获取字段映射关系</li>
     * </ol>
     *
     * @param delegate 真实的PreparedStatement对象，不能为null
     * @param sql      原始SQL语句，不能为null或空
     * @param datasourceId 数据源标识，如果为null则使用默认数据源
     * @throws IllegalArgumentException 如果delegate或sql为null
     * @throws RuntimeException         如果SQL解析失败
     */
    public SimpleInterceptorPreparedStatement(PreparedStatement delegate, String sql, String datasourceId) {
        if (delegate == null) {
            throw new IllegalArgumentException("Delegate PreparedStatement cannot be null");
        }
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL statement cannot be null or empty");
        }

        this.delegate = delegate;
        this.sql = sql.trim();
        this.datasourceId = StrUtil.isBlank(datasourceId) ? "default" : datasourceId;
        
        // 调试日志：记录 datasource-id 的来源
        if (StrUtil.isBlank(datasourceId)) {
            log.warn("PreparedStatement created with blank datasource-id, using default. SQL: {}", sql);
        } else {
            log.debug("PreparedStatement created with datasource-id: {} for SQL: {}", datasourceId, sql);
        }

        // 解析SQL中的表名（使用缓存优化）
        try {
            // 使用 SqlParseCache 的表名解析方法，优先从缓存获取
            Set<String> parsedTables = io.github.hexlodev.core.parser.SqlParseCache.parseTableNames(this.sql);
            this.tables = parsedTables != null ? new HashSet<>(parsedTables) : new HashSet<>();
            log.debug("Parsed tables from SQL: {} (datasource-id: {})", this.tables, this.datasourceId);
        } catch (Exception e) {
            log.warn("Failed to parse table names from SQL [sql={}, sqlLength={}], error: {}", 
                    this.sql != null ? this.sql : "null",
                    this.sql != null ? this.sql.length() : 0,
                    e.getMessage(), e);
            this.tables = new HashSet<>();
        }

        // 如果表需要加密，则解析SQL获取字段映射关系（使用数据源标识）
        if (SecurtkitUtils.needEncrypt(this.tables, this.datasourceId)) {
            try {
                this.pair = SecurtkitUtils.parseSql(this.sql, this.datasourceId);
                
                if (log.isDebugEnabled()) {
                    log.debug("SQL requires encryption [sql={}, tables={}, datasource-id={}, fieldsCount={}]", 
                            this.sql != null ? this.sql : "null",
                            this.tables,
                            this.datasourceId,
                            this.pair != null ? this.pair.getValue().size() : 0);
                }
            } catch (JSQLParserException e) {
                log.error("Failed to parse SQL for encryption [sql={}, sqlLength={}, tables={}, datasource-id={}], error: {}", 
                        this.sql != null ? this.sql : "null",
                        this.sql != null ? this.sql.length() : 0,
                        this.tables,
                        this.datasourceId,
                        e.getMessage(), e);
                throw new RuntimeException("SQL parsing failed for encryption", e);
            }
        } else {
            log.debug("Tables do not require encryption: {} (datasource-id: {})", this.tables, this.datasourceId);
            this.pair = null;
        }

        // 初始化参数加密器和 SQL 执行器
        this.parameterEncryptor = new ParameterEncryptor(this.datasourceId, this.tables, this.pair);
        this.sqlExecutor = new SqlExecutor(this.delegate, this.sql, this.datasourceId, this.tables, this.pair);
    }

    /**
     * 执行查询SQL - 拦截方法
     *
     * <p>拦截查询操作，记录SQL语句和执行时间，并对结果集进行包装以实现自动解密。</p>
     *
     * @return 查询结果集，已包装为支持自动解密的ResultSet
     * @throws SQLException 如果SQL执行失败
     */
    @Override
    public ResultSet executeQuery() throws SQLException {
        return sqlExecutor.executeQuery(parameterValues);
    }

    /**
     * 执行更新SQL（INSERT/UPDATE/DELETE） - 拦截方法
     *
     * <p>拦截更新操作，记录SQL语句、执行时间和影响的行数。</p>
     *
     * @return 受影响的行数
     * @throws SQLException 如果SQL执行失败
     */
    @Override
    public int executeUpdate() throws SQLException {
        return sqlExecutor.executeUpdate(parameterValues);
    }

    /**
     * 执行SQL - 拦截方法
     *
     * <p>通用执行方法，可用于执行任何类型的SQL语句。记录SQL语句、执行时间和结果。</p>
     *
     * @return 如果第一个结果是ResultSet对象则返回true，否则返回false
     * @throws SQLException 如果SQL执行失败
     */
    @Override
    public boolean execute() throws SQLException {
        return sqlExecutor.execute(parameterValues);
    }


    /**
     * 设置字符串参数 - 拦截方法
     *
     * <p>如果该参数对应需要加密的字段，则会在设置前进行加密处理。
     * 加密后的值会被缓存，用于后续的SQL日志输出。</p>
     *
     * @param parameterIndex 参数索引，从1开始
     * @param x              参数值
     * @throws SQLException 如果设置参数失败
     */
    @Override
    public void setString(int parameterIndex, String x) throws SQLException {
        String newValue = parameterEncryptor.encryptString(parameterIndex, x);
        delegate.setString(parameterIndex, newValue);
        parameterValues.put(parameterIndex, newValue);
    }

    /**
     * 设置整数参数
     *
     * @param parameterIndex 参数索引，从1开始
     * @param x              参数值
     * @throws SQLException 如果设置参数失败
     */
    @Override
    public void setInt(int parameterIndex, int x) throws SQLException {
        delegate.setInt(parameterIndex, x);
        parameterValues.put(parameterIndex, x);
    }

    /**
     * 设置长整数参数
     *
     * @param parameterIndex 参数索引，从1开始
     * @param x              参数值
     * @throws SQLException 如果设置参数失败
     */
    @Override
    public void setLong(int parameterIndex, long x) throws SQLException {
        delegate.setLong(parameterIndex, x);
        parameterValues.put(parameterIndex, x);
    }

    /**
     * 设置双精度浮点数参数
     *
     * @param parameterIndex 参数索引，从1开始
     * @param x              参数值
     * @throws SQLException 如果设置参数失败
     */
    @Override
    public void setDouble(int parameterIndex, double x) throws SQLException {
        delegate.setDouble(parameterIndex, x);
        parameterValues.put(parameterIndex, x);
    }

    /**
     * 设置布尔值参数
     *
     * @param parameterIndex 参数索引，从1开始
     * @param x              参数值
     * @throws SQLException 如果设置参数失败
     */
    @Override
    public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        delegate.setBoolean(parameterIndex, x);
        parameterValues.put(parameterIndex, x);
    }

    // 其他PreparedStatement方法直接委托
    @Override
    public void setNull(int parameterIndex, int sqlType) throws SQLException {
        delegate.setNull(parameterIndex, sqlType);
        parameterValues.put(parameterIndex, null);
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
        parameterValues.clear();
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
        Object newValue = parameterEncryptor.encryptObject(parameterIndex, x);
        delegate.setObject(parameterIndex, newValue, targetSqlType);
        parameterValues.put(parameterIndex, newValue);
    }

    @Override
    public void setObject(int parameterIndex, Object x) throws SQLException {
        Object newValue = parameterEncryptor.encryptObject(parameterIndex, x);
        delegate.setObject(parameterIndex, newValue);
        parameterValues.put(parameterIndex, newValue);
    }

    /**
     * 将当前PreparedStatement添加到批处理
     *
     * @throws SQLException 如果添加失败
     */
    @Override
    public void addBatch() throws SQLException {
        log.debug("Adding prepared statement to batch");
        delegate.addBatch();
        parameterValues.clear();
    }

    @Override
    public void setCharacterStream(int parameterIndex, java.io.Reader reader, int length) throws SQLException {
        if (reader != null) {
            String processed = parameterEncryptor.encryptReader(parameterIndex, reader, length);
            if (applyEncryptedString(parameterIndex, processed,
                    value -> delegate.setCharacterStream(parameterIndex, new java.io.StringReader(value), value.length()),
                    "characterStream(length)")) {
                return;
            }
        }
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
        if (x != null) {
            String processed = parameterEncryptor.encryptClob(parameterIndex, x);
            if (applyEncryptedString(parameterIndex, processed,
                    value -> delegate.setClob(parameterIndex, toClob(value)),
                    "clob")) {
                return;
            }
            cacheOriginalClobValue(parameterIndex, x);
        }
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
        if (value != null) {
            String processed = parameterEncryptor.encryptReader(parameterIndex, value, length);
            if (applyEncryptedString(parameterIndex, processed,
                    str -> delegate.setNCharacterStream(parameterIndex, new java.io.StringReader(str), str.length()),
                    "nCharacterStream(long)")) {
                return;
            }
        }
        delegate.setNCharacterStream(parameterIndex, value, length);
    }

    @Override
    public void setNClob(int parameterIndex, NClob value) throws SQLException {
        if (value != null) {
            String processed = parameterEncryptor.encryptNClob(parameterIndex, value);
            if (applyEncryptedString(parameterIndex, processed,
                    text -> delegate.setNClob(parameterIndex, toNClob(text)),
                    "nClob")) {
                return;
            }
            cacheOriginalNClobValue(parameterIndex, value);
        }
        delegate.setNClob(parameterIndex, value);
    }

    @Override
    public void setClob(int parameterIndex, java.io.Reader reader, long length) throws SQLException {
        if (reader != null) {
            String processed = parameterEncryptor.encryptReader(parameterIndex, reader, length);
            if (applyEncryptedString(parameterIndex, processed,
                    value -> delegate.setClob(parameterIndex, toClob(value)),
                    "clob(reader,long)")) {
                return;
            }
        }
        delegate.setClob(parameterIndex, reader, length);
    }

    @Override
    public void setBlob(int parameterIndex, java.io.InputStream inputStream, long length) throws SQLException {
        delegate.setBlob(parameterIndex, inputStream, length);
    }

    @Override
    public void setNClob(int parameterIndex, java.io.Reader reader, long length) throws SQLException {
        if (reader != null) {
            String processed = parameterEncryptor.encryptReader(parameterIndex, reader, length);
            if (applyEncryptedString(parameterIndex, processed,
                    text -> delegate.setNClob(parameterIndex, toNClob(text)),
                    "nClob(reader,long)")) {
                return;
            }
        }
        delegate.setNClob(parameterIndex, reader, length);
    }

    @Override
    public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
        delegate.setSQLXML(parameterIndex, xmlObject);
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
        delegate.setObject(parameterIndex, x, targetSqlType, scaleOrLength);
        parameterValues.put(parameterIndex, x);
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
        if (reader != null) {
            String processed = parameterEncryptor.encryptReader(parameterIndex, reader, length);
            if (applyEncryptedString(parameterIndex, processed,
                    value -> delegate.setCharacterStream(parameterIndex, new java.io.StringReader(value), value.length()),
                    "characterStream(long)")) {
                return;
            }
        }
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
        if (reader != null) {
            String processed = parameterEncryptor.encryptReader(parameterIndex, reader, Long.MAX_VALUE);
            if (applyEncryptedString(parameterIndex, processed,
                    value -> delegate.setCharacterStream(parameterIndex, new java.io.StringReader(value)),
                    "characterStream()")) {
                return;
            }
        }
        delegate.setCharacterStream(parameterIndex, reader);
    }

    @Override
    public void setNCharacterStream(int parameterIndex, java.io.Reader value) throws SQLException {
        if (value != null) {
            String processed = parameterEncryptor.encryptReader(parameterIndex, value, Long.MAX_VALUE);
            if (applyEncryptedString(parameterIndex, processed,
                    str -> delegate.setNCharacterStream(parameterIndex, new java.io.StringReader(str)),
                    "nCharacterStream()")) {
                return;
            }
        }
        delegate.setNCharacterStream(parameterIndex, value);
    }

    @Override
    public void setClob(int parameterIndex, java.io.Reader reader) throws SQLException {
        if (reader != null) {
            String processed = parameterEncryptor.encryptReader(parameterIndex, reader, Long.MAX_VALUE);
            if (applyEncryptedString(parameterIndex, processed,
                    value -> delegate.setClob(parameterIndex, toClob(value)),
                    "clob(reader)")) {
                return;
            }
        }
        delegate.setClob(parameterIndex, reader);
    }

    @Override
    public void setBlob(int parameterIndex, java.io.InputStream inputStream) throws SQLException {
        delegate.setBlob(parameterIndex, inputStream);
    }

    @Override
    public void setNClob(int parameterIndex, java.io.Reader reader) throws SQLException {
        if (reader != null) {
            String processed = parameterEncryptor.encryptReader(parameterIndex, reader, Long.MAX_VALUE);
            if (applyEncryptedString(parameterIndex, processed,
                    text -> delegate.setNClob(parameterIndex, toNClob(text)),
                    "nClob(reader)")) {
                return;
            }
        }
        delegate.setNClob(parameterIndex, reader);
    }

    /**
     * 将 Clob 转换为 String
     *
     * @param clob Clob 对象
     * @return 字符串内容，如果转换失败返回 null
     */
    private String clobToString(Clob clob) {
        try {
            long length = clob.length();
            if (length > Integer.MAX_VALUE) {
                log.warn("Clob length {} exceeds Integer.MAX_VALUE, truncating", length);
                length = Integer.MAX_VALUE;
            }
            return clob.getSubString(1, (int) length);
        } catch (SQLException e) {
            log.error("Failed to convert Clob to String", e);
            return null;
        }
    }

    /**
     * 将 NClob 转换为 String
     *
     * @param nClob NClob 对象
     * @return 字符串内容，如果转换失败返回 null
     */
    private String nClobToString(NClob nClob) {
        try {
            long length = nClob.length();
            if (length > Integer.MAX_VALUE) {
                log.warn("NClob length {} exceeds Integer.MAX_VALUE, truncating", length);
                length = Integer.MAX_VALUE;
            }
            return nClob.getSubString(1, (int) length);
        } catch (SQLException e) {
            log.error("Failed to convert NClob to String", e);
            return null;
        }
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

    /**
     * 关闭PreparedStatement
     *
     * @throws SQLException 如果关闭失败
     */
    @Override
    public void close() throws SQLException {
        log.debug("PreparedStatement closed");
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
        return ResultSetDecryptingProxy.wrap(delegate.getResultSet(), this.tables, this.pair, this.sql, this.datasourceId);
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

    /**
     * 执行给定的SQL语句（PreparedStatement通常不应使用此方法）
     *
     * @param sql SQL语句
     * @return 执行结果
     * @throws SQLException 如果执行失败
     */
    @Override
    public boolean execute(String sql) throws SQLException {
        log.warn("Using execute(String) on PreparedStatement - this bypasses prepared statement optimization");
        long startTime = System.currentTimeMillis();
        boolean result = delegate.execute(sql);
        long endTime = System.currentTimeMillis();
        if (log.isDebugEnabled()) {
            log.debug("[EXECUTE] Executed in {} ms, result: {}", (endTime - startTime), result);
        }
        return result;
    }

    /**
     * 执行更新SQL（PreparedStatement通常不应使用此方法）
     *
     * @param sql SQL语句
     * @return 受影响的行数
     * @throws SQLException 如果执行失败
     */
    @Override
    public int executeUpdate(String sql) throws SQLException {
        log.warn("Using executeUpdate(String) on PreparedStatement - this bypasses prepared statement optimization");
        long startTime = System.currentTimeMillis();
        int result = delegate.executeUpdate(sql);
        long endTime = System.currentTimeMillis();
        if (log.isDebugEnabled()) {
            log.debug("[EXECUTE UPDATE] Executed in {} ms, affected rows: {}", (endTime - startTime), result);
        }
        return result;
    }

    /**
     * 执行查询SQL（PreparedStatement通常不应使用此方法）
     *
     * @param sql SQL语句
     * @return 查询结果集
     * @throws SQLException 如果执行失败
     */
    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        log.warn("Using executeQuery(String) on PreparedStatement - this bypasses prepared statement optimization");
        long startTime = System.currentTimeMillis();
        ResultSet resultSet = delegate.executeQuery(sql);
        long endTime = System.currentTimeMillis();
        if (log.isDebugEnabled()) {
            log.debug("[EXECUTE QUERY] Executed in {} ms", (endTime - startTime));
        }
        return resultSet;
    }
}
