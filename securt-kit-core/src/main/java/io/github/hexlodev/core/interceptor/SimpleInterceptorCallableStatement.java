package io.github.hexlodev.core.interceptor;

import java.sql.*;
import java.util.logging.Logger;

/**
 * 简化的CallableStatement包装器 - 只拦截核心执行方法
 */
public class SimpleInterceptorCallableStatement implements CallableStatement {
    
    private static final Logger logger = Logger.getLogger(SimpleInterceptorCallableStatement.class.getName());
    private final CallableStatement delegate;
    private final String sql;
    
    public SimpleInterceptorCallableStatement(CallableStatement delegate, String sql) {
        this.delegate = delegate;
        this.sql = sql;
    }
    
    // 拦截执行方法
    @Override
    public ResultSet executeQuery() throws SQLException {
        logger.info("🔍 [CALLABLE QUERY] " + sql);
        long startTime = System.currentTimeMillis();
        ResultSet resultSet = delegate.executeQuery();
        long endTime = System.currentTimeMillis();
        logger.info("✅ [CALLABLE QUERY RESULT] Executed in " + (endTime - startTime) + "ms");
        return resultSet;
    }
    
    @Override
    public int executeUpdate() throws SQLException {
        logger.info("📝 [CALLABLE UPDATE] " + sql);
        long startTime = System.currentTimeMillis();
        int result = delegate.executeUpdate();
        long endTime = System.currentTimeMillis();
        logger.info("✅ [CALLABLE UPDATE RESULT] Executed in " + (endTime - startTime) + "ms, affected rows: " + result);
        return result;
    }
    
    @Override
    public boolean execute() throws SQLException {
        logger.info("⚡ [CALLABLE EXECUTE] " + sql);
        long startTime = System.currentTimeMillis();
        boolean result = delegate.execute();
        long endTime = System.currentTimeMillis();
        logger.info("✅ [CALLABLE EXECUTE RESULT] Executed in " + (endTime - startTime) + "ms, result: " + result);
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
    
    // 其他CallableStatement方法直接委托（简化实现）
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
        logger.info("Adding callable statement to batch");
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
    
    // CallableStatement特有的方法
    @Override
    public java.net.URL getURL(int parameterIndex) throws SQLException {
        return delegate.getURL(parameterIndex);
    }
    
    @Override
    public void setURL(String parameterName, java.net.URL val) throws SQLException {
        logger.fine("Setting URL parameter " + parameterName + " = " + val);
        delegate.setURL(parameterName, val);
    }
    
    @Override
    public void setNull(String parameterName, int sqlType) throws SQLException {
        logger.fine("Setting null parameter " + parameterName + " with type " + sqlType);
        delegate.setNull(parameterName, sqlType);
    }
    
    @Override
    public void setBoolean(String parameterName, boolean x) throws SQLException {
        logger.fine("Setting boolean parameter " + parameterName + " = " + x);
        delegate.setBoolean(parameterName, x);
    }
    
    @Override
    public void setByte(String parameterName, byte x) throws SQLException {
        logger.fine("Setting byte parameter " + parameterName + " = " + x);
        delegate.setByte(parameterName, x);
    }
    
    @Override
    public void setShort(String parameterName, short x) throws SQLException {
        logger.fine("Setting short parameter " + parameterName + " = " + x);
        delegate.setShort(parameterName, x);
    }
    
    @Override
    public void setInt(String parameterName, int x) throws SQLException {
        logger.fine("Setting int parameter " + parameterName + " = " + x);
        delegate.setInt(parameterName, x);
    }
    
    @Override
    public void setLong(String parameterName, long x) throws SQLException {
        logger.fine("Setting long parameter " + parameterName + " = " + x);
        delegate.setLong(parameterName, x);
    }
    
    @Override
    public void setFloat(String parameterName, float x) throws SQLException {
        logger.fine("Setting float parameter " + parameterName + " = " + x);
        delegate.setFloat(parameterName, x);
    }
    
    @Override
    public void setDouble(String parameterName, double x) throws SQLException {
        logger.fine("Setting double parameter " + parameterName + " = " + x);
        delegate.setDouble(parameterName, x);
    }
    
    @Override
    public void setBigDecimal(String parameterName, java.math.BigDecimal x) throws SQLException {
        logger.fine("Setting BigDecimal parameter " + parameterName + " = " + x);
        delegate.setBigDecimal(parameterName, x);
    }
    
    @Override
    public void setString(String parameterName, String x) throws SQLException {
        logger.fine("Setting string parameter " + parameterName + " = " + x);
        delegate.setString(parameterName, x);
    }
    
    @Override
    public void setBytes(String parameterName, byte[] x) throws SQLException {
        logger.fine("Setting bytes parameter " + parameterName);
        delegate.setBytes(parameterName, x);
    }
    
    @Override
    public void setDate(String parameterName, Date x) throws SQLException {
        logger.fine("Setting date parameter " + parameterName + " = " + x);
        delegate.setDate(parameterName, x);
    }
    
    @Override
    public void setTime(String parameterName, Time x) throws SQLException {
        logger.fine("Setting time parameter " + parameterName + " = " + x);
        delegate.setTime(parameterName, x);
    }
    
    @Override
    public void setTimestamp(String parameterName, Timestamp x) throws SQLException {
        logger.fine("Setting timestamp parameter " + parameterName + " = " + x);
        delegate.setTimestamp(parameterName, x);
    }
    
    @Override
    public void setAsciiStream(String parameterName, java.io.InputStream x, int length) throws SQLException {
        delegate.setAsciiStream(parameterName, x, length);
    }
    
    @Override
    public void setBinaryStream(String parameterName, java.io.InputStream x, int length) throws SQLException {
        delegate.setBinaryStream(parameterName, x, length);
    }
    
    @Override
    public void setObject(String parameterName, Object x, int targetSqlType, int scale) throws SQLException {
        delegate.setObject(parameterName, x, targetSqlType, scale);
    }
    
    @Override
    public void setObject(String parameterName, Object x, int targetSqlType) throws SQLException {
        delegate.setObject(parameterName, x, targetSqlType);
    }
    
    @Override
    public void setObject(String parameterName, Object x) throws SQLException {
        delegate.setObject(parameterName, x);
    }
    
    @Override
    public void setCharacterStream(String parameterName, java.io.Reader reader, int length) throws SQLException {
        delegate.setCharacterStream(parameterName, reader, length);
    }
    
    @Override
    public void setDate(String parameterName, Date x, java.util.Calendar cal) throws SQLException {
        delegate.setDate(parameterName, x, cal);
    }
    
    @Override
    public void setTime(String parameterName, Time x, java.util.Calendar cal) throws SQLException {
        delegate.setTime(parameterName, x, cal);
    }
    
    @Override
    public void setTimestamp(String parameterName, Timestamp x, java.util.Calendar cal) throws SQLException {
        delegate.setTimestamp(parameterName, x, cal);
    }
    
    @Override
    public void setNull(String parameterName, int sqlType, String typeName) throws SQLException {
        delegate.setNull(parameterName, sqlType, typeName);
    }
    
    @Override
    public void setRowId(String parameterName, RowId x) throws SQLException {
        delegate.setRowId(parameterName, x);
    }
    
    @Override
    public void setNString(String parameterName, String value) throws SQLException {
        delegate.setNString(parameterName, value);
    }
    
    @Override
    public void setNCharacterStream(String parameterName, java.io.Reader value, long length) throws SQLException {
        delegate.setNCharacterStream(parameterName, value, length);
    }
    
    @Override
    public void setNClob(String parameterName, NClob value) throws SQLException {
        delegate.setNClob(parameterName, value);
    }
    
    @Override
    public void setClob(String parameterName, java.io.Reader reader, long length) throws SQLException {
        delegate.setClob(parameterName, reader, length);
    }
    
    @Override
    public void setBlob(String parameterName, java.io.InputStream inputStream, long length) throws SQLException {
        delegate.setBlob(parameterName, inputStream, length);
    }
    
    @Override
    public void setNClob(String parameterName, java.io.Reader reader, long length) throws SQLException {
        delegate.setNClob(parameterName, reader, length);
    }
    
    @Override
    public void setSQLXML(String parameterName, SQLXML xmlObject) throws SQLException {
        delegate.setSQLXML(parameterName, xmlObject);
    }
    
    @Override
    public void setBlob(String parameterName, Blob x) throws SQLException {
        delegate.setBlob(parameterName, x);
    }
    
    @Override
    public void setClob(String parameterName, Clob x) throws SQLException {
        delegate.setClob(parameterName, x);
    }
    
    @Override
    public void setAsciiStream(String parameterName, java.io.InputStream x, long length) throws SQLException {
        delegate.setAsciiStream(parameterName, x, length);
    }
    
    @Override
    public void setBinaryStream(String parameterName, java.io.InputStream x, long length) throws SQLException {
        delegate.setBinaryStream(parameterName, x, length);
    }
    
    @Override
    public void setCharacterStream(String parameterName, java.io.Reader reader, long length) throws SQLException {
        delegate.setCharacterStream(parameterName, reader, length);
    }
    
    @Override
    public void setAsciiStream(String parameterName, java.io.InputStream x) throws SQLException {
        delegate.setAsciiStream(parameterName, x);
    }
    
    @Override
    public void setBinaryStream(String parameterName, java.io.InputStream x) throws SQLException {
        delegate.setBinaryStream(parameterName, x);
    }
    
    @Override
    public void setCharacterStream(String parameterName, java.io.Reader reader) throws SQLException {
        delegate.setCharacterStream(parameterName, reader);
    }
    
    @Override
    public void setNCharacterStream(String parameterName, java.io.Reader value) throws SQLException {
        delegate.setNCharacterStream(parameterName, value);
    }
    
    @Override
    public void setClob(String parameterName, java.io.Reader reader) throws SQLException {
        delegate.setClob(parameterName, reader);
    }
    
    @Override
    public void setBlob(String parameterName, java.io.InputStream inputStream) throws SQLException {
        delegate.setBlob(parameterName, inputStream);
    }
    
    @Override
    public void setNClob(String parameterName, java.io.Reader reader) throws SQLException {
        delegate.setNClob(parameterName, reader);
    }
    
    // 获取结果的方法
    @Override
    public NClob getNClob(int parameterIndex) throws SQLException {
        return delegate.getNClob(parameterIndex);
    }
    
    @Override
    public NClob getNClob(String parameterName) throws SQLException {
        return delegate.getNClob(parameterName);
    }
    
    @Override
    public SQLXML getSQLXML(int parameterIndex) throws SQLException {
        return delegate.getSQLXML(parameterIndex);
    }
    
    @Override
    public SQLXML getSQLXML(String parameterName) throws SQLException {
        return delegate.getSQLXML(parameterName);
    }
    
    @Override
    public String getNString(int parameterIndex) throws SQLException {
        return delegate.getNString(parameterIndex);
    }
    
    @Override
    public String getNString(String parameterName) throws SQLException {
        return delegate.getNString(parameterName);
    }
    
    @Override
    public java.io.Reader getNCharacterStream(int parameterIndex) throws SQLException {
        return delegate.getNCharacterStream(parameterIndex);
    }
    
    @Override
    public java.io.Reader getNCharacterStream(String parameterName) throws SQLException {
        return delegate.getNCharacterStream(parameterName);
    }
    
    @Override
    public java.io.Reader getCharacterStream(int parameterIndex) throws SQLException {
        return delegate.getCharacterStream(parameterIndex);
    }
    
    @Override
    public java.io.Reader getCharacterStream(String parameterName) throws SQLException {
        return delegate.getCharacterStream(parameterName);
    }
    
    @Override
    public String getString(String parameterName) throws SQLException {
        return delegate.getString(parameterName);
    }
    
    @Override
    public boolean getBoolean(String parameterName) throws SQLException {
        return delegate.getBoolean(parameterName);
    }
    
    @Override
    public byte getByte(String parameterName) throws SQLException {
        return delegate.getByte(parameterName);
    }
    
    @Override
    public short getShort(String parameterName) throws SQLException {
        return delegate.getShort(parameterName);
    }
    
    @Override
    public int getInt(String parameterName) throws SQLException {
        return delegate.getInt(parameterName);
    }
    
    @Override
    public long getLong(String parameterName) throws SQLException {
        return delegate.getLong(parameterName);
    }
    
    @Override
    public float getFloat(String parameterName) throws SQLException {
        return delegate.getFloat(parameterName);
    }
    
    @Override
    public double getDouble(String parameterName) throws SQLException {
        return delegate.getDouble(parameterName);
    }
    
    @Override
    public byte[] getBytes(String parameterName) throws SQLException {
        return delegate.getBytes(parameterName);
    }
    
    @Override
    public Date getDate(String parameterName) throws SQLException {
        return delegate.getDate(parameterName);
    }
    
    @Override
    public Time getTime(String parameterName) throws SQLException {
        return delegate.getTime(parameterName);
    }
    
    @Override
    public Timestamp getTimestamp(String parameterName) throws SQLException {
        return delegate.getTimestamp(parameterName);
    }
    
    @Override
    public Object getObject(String parameterName) throws SQLException {
        return delegate.getObject(parameterName);
    }
    
    @Override
    public java.math.BigDecimal getBigDecimal(String parameterName) throws SQLException {
        return delegate.getBigDecimal(parameterName);
    }
    
    @Override
    public Object getObject(String parameterName, java.util.Map<String, Class<?>> map) throws SQLException {
        return delegate.getObject(parameterName, map);
    }
    
    @Override
    public Ref getRef(String parameterName) throws SQLException {
        return delegate.getRef(parameterName);
    }
    
    @Override
    public Blob getBlob(String parameterName) throws SQLException {
        return delegate.getBlob(parameterName);
    }
    
    @Override
    public Clob getClob(String parameterName) throws SQLException {
        return delegate.getClob(parameterName);
    }
    
    @Override
    public Array getArray(String parameterName) throws SQLException {
        return delegate.getArray(parameterName);
    }
    
    @Override
    public Date getDate(String parameterName, java.util.Calendar cal) throws SQLException {
        return delegate.getDate(parameterName, cal);
    }
    
    @Override
    public Time getTime(String parameterName, java.util.Calendar cal) throws SQLException {
        return delegate.getTime(parameterName, cal);
    }
    
    @Override
    public Timestamp getTimestamp(String parameterName, java.util.Calendar cal) throws SQLException {
        return delegate.getTimestamp(parameterName, cal);
    }
    
    @Override
    public java.net.URL getURL(String parameterName) throws SQLException {
        return delegate.getURL(parameterName);
    }
    
    @Override
    public RowId getRowId(int parameterIndex) throws SQLException {
        return delegate.getRowId(parameterIndex);
    }
    
    @Override
    public RowId getRowId(String parameterName) throws SQLException {
        return delegate.getRowId(parameterName);
    }
    
    // 注册输出参数
    @Override
    public void registerOutParameter(int parameterIndex, int sqlType) throws SQLException {
        logger.info("Registering out parameter " + parameterIndex + " with type " + sqlType);
        delegate.registerOutParameter(parameterIndex, sqlType);
    }
    
    @Override
    public void registerOutParameter(int parameterIndex, int sqlType, int scale) throws SQLException {
        logger.info("Registering out parameter " + parameterIndex + " with type " + sqlType + " and scale " + scale);
        delegate.registerOutParameter(parameterIndex, sqlType, scale);
    }
    
    @Override
    public boolean wasNull() throws SQLException {
        return delegate.wasNull();
    }
    
    @Override
    public String getString(int parameterIndex) throws SQLException {
        return delegate.getString(parameterIndex);
    }
    
    @Override
    public boolean getBoolean(int parameterIndex) throws SQLException {
        return delegate.getBoolean(parameterIndex);
    }
    
    @Override
    public byte getByte(int parameterIndex) throws SQLException {
        return delegate.getByte(parameterIndex);
    }
    
    @Override
    public short getShort(int parameterIndex) throws SQLException {
        return delegate.getShort(parameterIndex);
    }
    
    @Override
    public int getInt(int parameterIndex) throws SQLException {
        return delegate.getInt(parameterIndex);
    }
    
    @Override
    public long getLong(int parameterIndex) throws SQLException {
        return delegate.getLong(parameterIndex);
    }
    
    @Override
    public float getFloat(int parameterIndex) throws SQLException {
        return delegate.getFloat(parameterIndex);
    }
    
    @Override
    public double getDouble(int parameterIndex) throws SQLException {
        return delegate.getDouble(parameterIndex);
    }
    
    @Override
    public java.math.BigDecimal getBigDecimal(int parameterIndex, int scale) throws SQLException {
        return delegate.getBigDecimal(parameterIndex, scale);
    }
    
    @Override
    public byte[] getBytes(int parameterIndex) throws SQLException {
        return delegate.getBytes(parameterIndex);
    }
    
    @Override
    public Date getDate(int parameterIndex) throws SQLException {
        return delegate.getDate(parameterIndex);
    }
    
    @Override
    public Time getTime(int parameterIndex) throws SQLException {
        return delegate.getTime(parameterIndex);
    }
    
    @Override
    public Timestamp getTimestamp(int parameterIndex) throws SQLException {
        return delegate.getTimestamp(parameterIndex);
    }
    
    @Override
    public Object getObject(int parameterIndex) throws SQLException {
        return delegate.getObject(parameterIndex);
    }
    
    @Override
    public java.math.BigDecimal getBigDecimal(int parameterIndex) throws SQLException {
        return delegate.getBigDecimal(parameterIndex);
    }
    
    @Override
    public Object getObject(int parameterIndex, java.util.Map<String, Class<?>> map) throws SQLException {
        return delegate.getObject(parameterIndex, map);
    }
    
    @Override
    public Ref getRef(int parameterIndex) throws SQLException {
        return delegate.getRef(parameterIndex);
    }
    
    @Override
    public Blob getBlob(int parameterIndex) throws SQLException {
        return delegate.getBlob(parameterIndex);
    }
    
    @Override
    public Clob getClob(int parameterIndex) throws SQLException {
        return delegate.getClob(parameterIndex);
    }
    
    @Override
    public Array getArray(int parameterIndex) throws SQLException {
        return delegate.getArray(parameterIndex);
    }
    
    @Override
    public Date getDate(int parameterIndex, java.util.Calendar cal) throws SQLException {
        return delegate.getDate(parameterIndex, cal);
    }
    
    @Override
    public Time getTime(int parameterIndex, java.util.Calendar cal) throws SQLException {
        return delegate.getTime(parameterIndex, cal);
    }
    
    @Override
    public Timestamp getTimestamp(int parameterIndex, java.util.Calendar cal) throws SQLException {
        return delegate.getTimestamp(parameterIndex, cal);
    }
    
    @Override
    public void registerOutParameter(int parameterIndex, int sqlType, String typeName) throws SQLException {
        logger.info("Registering out parameter " + parameterIndex + " with type " + sqlType + " and typeName " + typeName);
        delegate.registerOutParameter(parameterIndex, sqlType, typeName);
    }
    
    @Override
    public void registerOutParameter(String parameterName, int sqlType) throws SQLException {
        logger.info("Registering out parameter " + parameterName + " with type " + sqlType);
        delegate.registerOutParameter(parameterName, sqlType);
    }
    
    @Override
    public void registerOutParameter(String parameterName, int sqlType, int scale) throws SQLException {
        logger.info("Registering out parameter " + parameterName + " with type " + sqlType + " and scale " + scale);
        delegate.registerOutParameter(parameterName, sqlType, scale);
    }
    
    @Override
    public void registerOutParameter(String parameterName, int sqlType, String typeName) throws SQLException {
        logger.info("Registering out parameter " + parameterName + " with type " + sqlType + " and typeName " + typeName);
        delegate.registerOutParameter(parameterName, sqlType, typeName);
    }
    
    @Override
    public <T> T getObject(int parameterIndex, Class<T> type) throws SQLException {
        return delegate.getObject(parameterIndex, type);
    }
    
    @Override
    public <T> T getObject(String parameterName, Class<T> type) throws SQLException {
        return delegate.getObject(parameterName, type);
    }
    
    // 继承自Statement的方法（简化实现，直接委托）
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
        logger.info("CallableStatement closed");
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
        logger.info("Executing callable statement with SQL: " + sql);
        long startTime = System.currentTimeMillis();
        boolean result = delegate.execute(sql);
        long endTime = System.currentTimeMillis();
        logger.info("Callable statement executed in " + (endTime - startTime) + "ms, result: " + result);
        return result;
    }
    
    @Override
    public int executeUpdate(String sql) throws SQLException {
        logger.info("Executing callable update with SQL: " + sql);
        long startTime = System.currentTimeMillis();
        int result = delegate.executeUpdate(sql);
        long endTime = System.currentTimeMillis();
        logger.info("Callable update executed in " + (endTime - startTime) + "ms, affected rows: " + result);
        return result;
    }
    
    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        logger.info("Executing callable query with SQL: " + sql);
        long startTime = System.currentTimeMillis();
        ResultSet resultSet = delegate.executeQuery(sql);
        long endTime = System.currentTimeMillis();
        logger.info("Callable query executed in " + (endTime - startTime) + "ms");
        return resultSet;
    }
}