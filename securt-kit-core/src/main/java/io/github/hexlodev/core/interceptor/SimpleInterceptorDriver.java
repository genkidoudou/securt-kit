package io.github.hexlodev.core.interceptor;

import java.sql.*;
import java.util.Enumeration;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * 简化的JDBC拦截驱动 - 参考P6Spy实现
 * 只实现核心拦截功能，不包装所有JDBC对象
 */
public class SimpleInterceptorDriver implements Driver {
    
    private static final Logger logger = Logger.getLogger(SimpleInterceptorDriver.class.getName());
    private static final String JDBC_INTERCEPTOR_PREFIX = "jdbc:interceptor:";
    
    static {
        try {
            DriverManager.registerDriver(new SimpleInterceptorDriver());
            logger.info("SimpleInterceptorDriver registered successfully");
        } catch (SQLException e) {
            logger.severe("Failed to register SimpleInterceptorDriver: " + e.getMessage());
        }
    }
    
    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }
        
        // 提取真实的JDBC URL
        String realUrl = "jdbc:" + url.substring(JDBC_INTERCEPTOR_PREFIX.length());
        logger.info("Intercepting connection to: " + realUrl);
        
        // 查找底层的JDBC驱动
        Driver underlyingDriver = findUnderlyingDriver(realUrl);
        if (underlyingDriver == null) {
            throw new SQLException("No suitable driver found for " + realUrl);
        }
        
        // 建立真实连接
        Connection realConnection = underlyingDriver.connect(realUrl, info);
        if (realConnection == null) {
            return null;
        }
        
        // 返回包装的连接
        return new SimpleInterceptorConnection(realConnection);
    }
    
    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return url != null && url.startsWith(JDBC_INTERCEPTOR_PREFIX);
    }
    
    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        String realUrl = "jdbc:" + url.substring(JDBC_INTERCEPTOR_PREFIX.length());
        Driver underlyingDriver = findUnderlyingDriver(realUrl);
        return underlyingDriver != null ? underlyingDriver.getPropertyInfo(realUrl, info) : new DriverPropertyInfo[0];
    }
    
    @Override
    public int getMajorVersion() {
        return 1;
    }
    
    @Override
    public int getMinorVersion() {
        return 0;
    }
    
    @Override
    public boolean jdbcCompliant() {
        return false; // 不是完全JDBC兼容的，因为它是包装器
    }
    
    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("getParentLogger not supported");
    }
    
    /**
     * 查找底层的JDBC驱动
     */
    private Driver findUnderlyingDriver(String realUrl) throws SQLException {
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            if (driver != this && driver.acceptsURL(realUrl)) {
                return driver;
            }
        }
        return null;
    }
}
