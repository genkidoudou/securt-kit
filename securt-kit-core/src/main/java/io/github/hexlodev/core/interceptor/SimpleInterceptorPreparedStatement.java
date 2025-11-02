package io.github.hexlodev.core.interceptor;

import cn.hutool.core.lang.Pair;
import cn.hutool.extra.spring.SpringUtil;
import io.github.hexlodev.core.TableCache;
import io.github.hexlodev.core.parser.SecurtkitUtils;
import io.github.hexlodev.core.parser.dto.ColumnTableDto;
import io.github.hexlodev.core.parser.dto.FieldEncryptorInfoDto;
import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;
import io.github.hexlodev.core.utils.TableNameParser;
import net.sf.jsqlparser.JSQLParserException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.sql.Date;
import java.util.*;
import java.util.logging.Logger;

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
 * @since 1.0.0
 * @see PreparedStatement
 * @see FieldEncryptorStrategy
 * @see ResultSetDecryptingProxy
 */
public class SimpleInterceptorPreparedStatement implements PreparedStatement {

    /** 日志记录器 */
    private static final Logger logger = Logger.getLogger(SimpleInterceptorPreparedStatement.class.getName());
    
    /** 被包装的真实PreparedStatement对象 */
    private final PreparedStatement delegate;
    
    /** 原始SQL语句 */
    private final String sql;
    
    /** 是否为更新类操作（INSERT/UPDATE/DELETE） */
    private boolean isUpdate;
    
    /**
     * SQL语句中涉及的表名集合
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
     * 构造函数
     * 
     * <p>创建PreparedStatement拦截器，初始化时：
     * <ol>
     *   <li>解析SQL语句中的表名</li>
     *   <li>判断是否需要加密处理</li>
     *   <li>如果需要加密，则解析SQL获取字段映射关系</li>
     * </ol>
     * 
     * @param delegate 真实的PreparedStatement对象，不能为null
     * @param sql 原始SQL语句，不能为null或空
     * @throws IllegalArgumentException 如果delegate或sql为null
     * @throws RuntimeException 如果SQL解析失败
     */
    public SimpleInterceptorPreparedStatement(PreparedStatement delegate, String sql) {
        if (delegate == null) {
            throw new IllegalArgumentException("Delegate PreparedStatement cannot be null");
        }
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL statement cannot be null or empty");
        }
        
        this.delegate = delegate;
        this.sql = sql.trim();
        
        // 判断是否为更新类操作（INSERT/UPDATE/DELETE）
        String lowerSql = this.sql.toLowerCase();
        this.isUpdate = lowerSql.startsWith("update") || lowerSql.startsWith("insert") || lowerSql.startsWith("delete");
        
        // 解析SQL中的表名
        try {
            TableNameParser tableNameParser = new TableNameParser(this.sql);
            this.tables = tableNameParser.tables();
            logger.fine("Parsed tables from SQL: " + this.tables);
        } catch (Exception e) {
            logger.warning("Failed to parse table names from SQL: " + this.sql + ", error: " + e.getMessage());
            this.tables = new HashSet<>();
        }
        
        // 如果表需要加密，则解析SQL获取字段映射关系
        if (SecurtkitUtils.needEncrypt(this.tables)) {
            try {
                this.pair = SecurtkitUtils.parseSql(this.sql);
                logger.info("SQL requires encryption, parsed " + (this.pair != null ? this.pair.getValue().size() : 0) + " fields");
            } catch (JSQLParserException e) {
                logger.severe("Failed to parse SQL for encryption: " + this.sql + ", error: " + e.getMessage());
                throw new RuntimeException("SQL parsing failed for encryption", e);
            }
        } else {
            logger.fine("Tables do not require encryption: " + this.tables);
        }
    }

    /**
     * 生成最终执行的SQL（将占位符替换为实际参数值）
     */
    private String buildFinalSql() {
        if (parameterValues.isEmpty()) {
            return sql;
        }

        StringBuilder finalSql = new StringBuilder();
        int paramIndex = 0; // 占位符索引（从1开始，对应JDBC参数索引）

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '?') {
                paramIndex++;
                Object value = parameterValues.get(paramIndex);
                if (value != null) {
                    finalSql.append(formatSqlValue(value));
                } else {
                    // 如果没有设置该参数，保留?占位符
                    finalSql.append('?');
                }
            } else {
                finalSql.append(c);
            }
        }

        return finalSql.toString();
    }

    /**
     * 格式化SQL值（添加引号等）
     */
    private String formatSqlValue(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof String) {
            // 转义单引号
            String str = ((String) value).replace("'", "''");
            return "'" + str + "'";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        // 其他类型转为字符串并转义
        return "'" + String.valueOf(value).replace("'", "''") + "'";
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
        String finalSql = buildFinalSql();
        logger.info("[PREPARED QUERY] " + sql);
        if (!sql.equals(finalSql)) {
            logger.fine("[FINAL SQL] " + finalSql);
        }
        
        long startTime = System.currentTimeMillis();
        try {
            ResultSet resultSet = delegate.executeQuery();
            long endTime = System.currentTimeMillis();
            logger.info("[PREPARED QUERY RESULT] Executed in " + (endTime - startTime) + "ms");
            
            // 包装结果集以实现自动解密
            return ResultSetDecryptingProxy.wrap(resultSet, this.tables, this.pair, this.sql);
        } catch (SQLException e) {
            long endTime = System.currentTimeMillis();
            logger.severe("[PREPARED QUERY ERROR] Failed after " + (endTime - startTime) + "ms: " + e.getMessage());
            throw e;
        }
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
        String finalSql = buildFinalSql();
        logger.info("[PREPARED UPDATE] " + sql);
        if (!sql.equals(finalSql)) {
            logger.fine("[FINAL SQL] " + finalSql);
        }
        
        long startTime = System.currentTimeMillis();
        try {
            int result = delegate.executeUpdate();
            long endTime = System.currentTimeMillis();
            logger.info("[PREPARED UPDATE RESULT] Executed in " + (endTime - startTime) + "ms, affected rows: " + result);
            return result;
        } catch (SQLException e) {
            long endTime = System.currentTimeMillis();
            logger.severe("[PREPARED UPDATE ERROR] Failed after " + (endTime - startTime) + "ms: " + e.getMessage());
            throw e;
        }
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
        String finalSql = buildFinalSql();
        logger.info("[PREPARED EXECUTE] " + sql);
        if (!sql.equals(finalSql)) {
            logger.fine("[FINAL SQL] " + finalSql);
        }
        
        long startTime = System.currentTimeMillis();
        try {
            boolean result = delegate.execute();
            long endTime = System.currentTimeMillis();
            logger.info("[PREPARED EXECUTE RESULT] Executed in " + (endTime - startTime) + "ms, result: " + result);
            return result;
        } catch (SQLException e) {
            long endTime = System.currentTimeMillis();
            logger.severe("[PREPARED EXECUTE ERROR] Failed after " + (endTime - startTime) + "ms: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 设置字符串参数 - 拦截方法
     * 
     * <p>如果该参数对应需要加密的字段，则会在设置前进行加密处理。
     * 加密后的值会被缓存，用于后续的SQL日志输出。</p>
     * 
     * @param parameterIndex 参数索引，从1开始
     * @param x 参数值
     * @throws SQLException 如果设置参数失败
     */
    @Override
    public void setString(int parameterIndex, String x) throws SQLException {
        String newValue = x;

        if (SecurtkitUtils.needEncrypt(this.tables) && null != this.pair) {

            Optional<ColumnTableDto> first = null;
            first = pair.getKey().values().stream()
                    .filter(a -> a.getInsertFieldIndex() == parameterIndex)
                    .findFirst();
            // TODO 暂时注释
              /*  if (isUpdate) {
                    first = pair.getKey().values().stream()
                            .filter(a -> a.getInsertFieldIndex() == parameterIndex)
                            .findFirst();
                } else {
                    // 支持delete/where自动加密（根据表加密配置+参数名/列名/SQL实际解析，可拓展，否则条件参数直接跳过加密）
                    first = Optional.empty();
                }*/

            if (first != null && first.isPresent()) {
                ColumnTableDto columnTableDto = first.get();
                String sourceColumn = columnTableDto.getSourceColumn();
                Class<? extends FieldEncryptorStrategy> fieldEncryptorStrategy = 
                    TableCache.getTableFieldEncryptInfo(columnTableDto.getSourceTableName(), sourceColumn);
                
                if (fieldEncryptorStrategy != null) {
                    try {
                        FieldEncryptorStrategy strategy = SpringUtil.getBean(fieldEncryptorStrategy);
                        newValue = strategy.encryption(x);
                        logger.fine("Encrypted field: " + sourceColumn + " in table: " + columnTableDto.getSourceTableName());
                    } catch (Exception e) {
                        logger.warning("Failed to encrypt field " + sourceColumn + ": " + e.getMessage());
                        // 加密失败时使用原始值，避免SQL执行失败
                    }
                }
            }
        }
        
        delegate.setString(parameterIndex, newValue);
        parameterValues.put(parameterIndex, newValue);
    }

    /**
     * 设置整数参数
     * 
     * @param parameterIndex 参数索引，从1开始
     * @param x 参数值
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
     * @param x 参数值
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
     * @param x 参数值
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
     * @param x 参数值
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
        delegate.setObject(parameterIndex, x, targetSqlType);
        parameterValues.put(parameterIndex, x);
    }

    @Override
    public void setObject(int parameterIndex, Object x) throws SQLException {
        delegate.setObject(parameterIndex, x);
        parameterValues.put(parameterIndex, x);
    }

    /**
     * 将当前PreparedStatement添加到批处理
     * 
     * @throws SQLException 如果添加失败
     */
    @Override
    public void addBatch() throws SQLException {
        logger.fine("Adding prepared statement to batch");
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

    /**
     * 对字符串进行MD5十六进制编码
     */
    private static String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                String hex = Integer.toHexString((b & 0xFF) | 0x100).substring(1);
                sb.append(hex);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
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
        logger.fine("PreparedStatement closed");
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
        return ResultSetDecryptingProxy.wrap(delegate.getResultSet(),  this.tables,this.pair,this.sql);
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
        logger.warning("Using execute(String) on PreparedStatement - this bypasses prepared statement optimization");
        long startTime = System.currentTimeMillis();
        boolean result = delegate.execute(sql);
        long endTime = System.currentTimeMillis();
        logger.info("[EXECUTE] Executed in " + (endTime - startTime) + "ms, result: " + result);
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
        logger.warning("Using executeUpdate(String) on PreparedStatement - this bypasses prepared statement optimization");
        long startTime = System.currentTimeMillis();
        int result = delegate.executeUpdate(sql);
        long endTime = System.currentTimeMillis();
        logger.info("[EXECUTE UPDATE] Executed in " + (endTime - startTime) + "ms, affected rows: " + result);
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
        logger.warning("Using executeQuery(String) on PreparedStatement - this bypasses prepared statement optimization");
        long startTime = System.currentTimeMillis();
        ResultSet resultSet = delegate.executeQuery(sql);
        long endTime = System.currentTimeMillis();
        logger.info("[EXECUTE QUERY] Executed in " + (endTime - startTime) + "ms");
        return resultSet;
    }
}
