package io.github.genkidoudou.core.interceptor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import io.github.genkidoudou.core.TableCache;
import io.github.genkidoudou.core.config.FieldEncryptorProperties;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.Enumeration;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 简化的JDBC拦截驱动 - 参考P6Spy实现
 *
 * <p>这个驱动实现了JDBC拦截的核心功能，通过代理模式拦截数据库连接请求。
 * 当应用程序使用 {@code jdbc:interceptor:} 前缀的URL时，此驱动会被激活，
 * 然后提取真实的数据库URL并转发给底层驱动，同时包装返回的连接对象。</p>
 *
 * <p>主要功能：</p>
 * <ul>
 *   <li>自动注册到 {@link DriverManager}</li>
 *   <li>识别拦截器URL格式：{@code jdbc:interceptor:&lt;real-url&gt;}</li>
 *   <li>提取真实数据库URL并查找对应的底层驱动</li>
 *   <li>包装数据库连接以支持拦截功能</li>
 *   <li>提供详细的日志记录</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 原始URL
 * String originalUrl = "jdbc:h2:mem:testdb";
 *
 * // 使用拦截器URL
 * String interceptorUrl = "jdbc:interceptor:h2:mem:testdb";
 *
 * // 连接数据库
 * Connection conn = DriverManager.getConnection(interceptorUrl, props);
 * }</pre>
 *
 * @author hexlodev
 * @see Driver
 * @see DriverManager
 * @see SimpleInterceptorConnection
 * @since 1.0.0
 */
@Slf4j
public class SimpleInterceptorDriver implements Driver {

    /**
     * 拦截器URL前缀，用于识别需要拦截的数据库连接
     */
    private static final String JDBC_INTERCEPTOR_PREFIX = "jdbc:interceptor:";

    /**
     * 标记是否已经尝试从 Spring 上下文完成初始化
     */
    private static final AtomicBoolean INITIALIZATION_IN_PROGRESS = new AtomicBoolean(false);

    /**
     * 静态初始化块 - 自动注册驱动
     *
     * <p>当类被加载时，自动将此驱动注册到 {@link DriverManager} 中。
     * 这样应用程序就可以通过 {@code jdbc:interceptor:} 前缀的URL来使用拦截功能。</p>
     *
     * <p>注册失败时会记录严重错误日志，但不会抛出异常，避免影响应用程序启动。</p>
     */
    static {
        try {
            DriverManager.registerDriver(new SimpleInterceptorDriver());
            log.debug("SimpleInterceptorDriver registered successfully");
        } catch (SQLException e) {
            log.error("Failed to register SimpleInterceptorDriver: " + e.getMessage());
        }
    }

    /**
     * 建立数据库连接 - 核心拦截方法z
     *
     * <p>这是拦截器的核心方法，负责：</p>
     * <ol>
     *   <li>验证URL是否被此驱动接受</li>
     *   <li>提取真实的数据库URL（去掉 {@code interceptor:} 前缀）</li>
     *   <li>查找并调用底层数据库驱动</li>
     *   <li>包装返回的连接对象以支持拦截功能</li>
     * </ol>
     *
     * @param url  数据库连接URL，必须以 {@code jdbc:interceptor:} 开头
     * @param info 连接属性，包含用户名、密码等信息
     * @return 包装后的数据库连接，如果URL不被接受则返回null
     * @throws SQLException 如果无法找到合适的底层驱动或连接失败
     * @see #acceptsURL(String)
     * @see #findUnderlyingDriver(String)
     * @see SimpleInterceptorConnection
     */
    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        // 检查URL是否被此驱动接受
        if (!acceptsURL(url)) {
            return null;
        }

        // 提取数据源标识（优先从 URL 参数，其次从连接属性）
        String datasourceId = extractDatasourceId(url, info);

        // 提取真实的JDBC URL（去掉interceptor前缀和datasource-id参数）
        String realUrl = extractRealUrl(url);
        if (log.isDebugEnabled()) {
            log.debug("Intercepting connection to: {} (datasource-id: {}) [original-url: {}]", realUrl, datasourceId, url);
        }

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

        // 尝试在连接建立时初始化配置（懒加载，避免静态块提前触发）
        ensureConfigurationInitialized();

        // 创建包装连接，传递数据源标识
        return new SimpleInterceptorConnection(realConnection, datasourceId);
    }

    /**
     * 提取数据源标识
     * <p>
     * 支持多种方式：
     * 1. 从连接属性中获取（适用于 HikariCP 的 dataSourceProperty）
     * 2. 从 URL 参数中获取（通用方式，适用于所有连接池）
     * 3. 如果都未指定，则返回默认值 "default"
     * </p>
     *
     * @param url 连接URL
     * @param info 连接属性
     * @return 数据源标识
     */
    private String extractDatasourceId(String url, Properties info) {
        // 1. 优先从连接属性中获取（适用于 HikariCP 的 addDataSourceProperty）
        if (info != null) {
            String datasourceId = info.getProperty("datasource-id");
            if (StrUtil.isNotBlank(datasourceId)) {
                log.debug("Extracted datasource-id from connection properties: {}", datasourceId);
                return datasourceId;
            }
        }

        // 2. 从 URL 参数中获取（通用方式，适用于所有连接池）
        String datasourceId = extractDatasourceIdFromUrl(url);
        if (StrUtil.isNotBlank(datasourceId)) {
            log.debug("Extracted datasource-id from URL: {}", datasourceId);
            return datasourceId;
        }

        // 3. 返回默认值
        log.debug("No datasource-id found, using default");
        return "default";
    }

    /**
     * 从 URL 中提取数据源标识
     * 支持格式：jdbc:interceptor:xxx?datasource-id=xxx 或 jdbc:interceptor:xxx;datasource-id=xxx
     *
     * @param url 连接URL
     * @return 数据源标识，如果未找到则返回 null
     */
    private String extractDatasourceIdFromUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return null;
        }

        // 查找 datasource-id 参数（支持 ? 和 ; 两种分隔符）
        // 注意：H2 数据库使用 ; 作为参数分隔符，MySQL 等使用 ? 或 &
        String[] patterns = {"?datasource-id=", "&datasource-id=", ";datasource-id="};
        for (String pattern : patterns) {
            int index = url.indexOf(pattern);
            if (index >= 0) {
                int start = index + pattern.length();
                // 查找参数结束位置（& 或 ; 或 ? 或 字符串结尾）
                int end = url.length();
                for (int i = start; i < url.length(); i++) {
                    char c = url.charAt(i);
                    if (c == '&' || c == ';' || c == '?' || c == ' ') {
                        end = i;
                        break;
                    }
                }
                String value = url.substring(start, end);
                if (StrUtil.isNotBlank(value)) {
                    log.debug("Extracted datasource-id '{}' from URL using pattern '{}'", value, pattern);
                    return value;
                }
            }
        }

        log.debug("No datasource-id found in URL: {}", url);
        return null;
    }

    /**
     * 提取真实的 JDBC URL（去掉 interceptor 前缀和 datasource-id 参数）
     *
     * @param url 原始URL
     * @return 真实URL（已移除 datasource-id 参数）
     */
    private String extractRealUrl(String url) {
        // 去掉 interceptor 前缀
        String realUrl = "jdbc:" + url.substring(JDBC_INTERCEPTOR_PREFIX.length());
        
        // 移除 datasource-id 参数（如果存在）
        realUrl = removeDatasourceIdFromUrl(realUrl);
        
        return realUrl;
    }

    /**
     * 从 URL 中移除 datasource-id 参数
     *
     * @param url 原始URL
     * @return 移除参数后的URL
     */
    private String removeDatasourceIdFromUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return url;
        }

        // 查找并移除 datasource-id 参数（支持 ? 和 ; 两种分隔符）
        String[] patterns = {"?datasource-id=", "&datasource-id=", ";datasource-id="};
        for (String pattern : patterns) {
            int index = url.indexOf(pattern);
            if (index >= 0) {
                int start = index + pattern.length();
                // 查找参数结束位置
                int end = url.length();
                for (int i = start; i < url.length(); i++) {
                    char c = url.charAt(i);
                    if (c == '&' || c == ';' || c == '?' || c == ' ') {
                        end = i;
                        break;
                    }
                }
                
                // 移除参数
                String before = url.substring(0, index);
                String after = url.substring(end);
                
                // 如果移除的是第一个参数（?），需要确保后续参数使用正确的分隔符
                if (pattern.startsWith("?") && !after.isEmpty() && !after.startsWith("&") && !after.startsWith(";")) {
                    // 如果后面还有参数，需要添加分隔符
                    if (!after.isEmpty() && !after.startsWith("&") && !after.startsWith(";")) {
                        // 检查 after 是否包含其他参数
                        if (after.contains("=")) {
                            after = (after.startsWith("?") ? "" : "&") + after;
                        }
                    }
                }
                
                // 如果移除的是中间参数（&），需要确保前面有参数
                if (pattern.startsWith("&")) {
                    // 移除 & 分隔符
                    url = before + after;
                } else if (pattern.startsWith("?")) {
                    // 移除 ? 分隔符，如果后面还有参数，用 & 或 ; 替换
                    if (!after.isEmpty() && !after.startsWith("&") && !after.startsWith(";")) {
                        // 检查是否需要添加分隔符
                        if (after.contains("=")) {
                            url = before + "?" + after;
                        } else {
                            url = before + after;
                        }
                    } else {
                        url = before + after;
                    }
                } else if (pattern.startsWith(";")) {
                    // 移除 ; 分隔符
                    url = before + after;
                }
                
                // 递归处理，确保移除所有 datasource-id 参数
                return removeDatasourceIdFromUrl(url);
            }
        }

        return url;
    }

    /**
     * 确保表缓存初始化仅在 Spring 上下文准备就绪后执行
     */
    private void ensureConfigurationInitialized() {
        if (TableCache.isInit()) {
            return;
        }

        if (!INITIALIZATION_IN_PROGRESS.compareAndSet(false, true)) {
            // 其他线程正在尝试初始化，直接返回等待下一次机会
            return;
        }

        try {
            FieldEncryptorProperties properties = null;
            try {
                properties = SpringUtil.getBean(FieldEncryptorProperties.class);
            } catch (Exception ex) {
                if (log.isDebugEnabled()) {
                    log.debug("FieldEncryptorProperties bean not ready yet: {}", ex.getMessage());
                }
            }

            if (properties != null && properties.isEnable()) {
                TableCache.init(properties);
                log.debug("TableCache initialized via SimpleInterceptorDriver");
            } else if (log.isTraceEnabled()) {
                log.trace("FieldEncryptorProperties bean unavailable or disabled, skip TableCache init");
            }
        } finally {
            // 允许后续连接在未初始化成功时重试
            if (!TableCache.isInit()) {
                INITIALIZATION_IN_PROGRESS.set(false);
            }
        }
    }

    /**
     * 检查是否接受指定的URL
     *
     * <p>此方法用于判断给定的URL是否应该由此驱动处理。
     * 只有当URL以 {@code jdbc:interceptor:} 开头时才返回true。</p>
     *
     * @param url 要检查的数据库URL
     * @return 如果URL以 {@code jdbc:interceptor:} 开头则返回true，否则返回false
     * @throws SQLException 如果发生错误（此实现中不会抛出异常）
     */
    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return url != null && url.startsWith(JDBC_INTERCEPTOR_PREFIX);
    }

    /**
     * 获取驱动属性信息
     *
     * <p>将属性查询请求转发给底层驱动，这样可以保持与原始驱动相同的属性支持。</p>
     *
     * @param url  数据库URL
     * @param info 连接属性
     * @return 驱动属性信息数组
     * @throws SQLException 如果发生错误
     */
    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        String realUrl = "jdbc:" + url.substring(JDBC_INTERCEPTOR_PREFIX.length());
        Driver underlyingDriver = findUnderlyingDriver(realUrl);
        return underlyingDriver != null ? underlyingDriver.getPropertyInfo(realUrl, info) : new DriverPropertyInfo[0];
    }

    /**
     * 获取驱动主版本号
     *
     * @return 驱动主版本号，固定返回1
     */
    @Override
    public int getMajorVersion() {
        return 1;
    }

    /**
     * 获取驱动次版本号
     *
     * @return 驱动次版本号，固定返回0
     */
    @Override
    public int getMinorVersion() {
        return 0;
    }

    /**
     * 检查驱动是否完全符合JDBC规范
     *
     * <p>由于这是一个包装器驱动，不是完全JDBC兼容的，所以返回false。</p>
     *
     * @return 固定返回false
     */
    @Override
    public boolean jdbcCompliant() {
        return false; // 不是完全JDBC兼容的，因为它是包装器
    }

    /**
     * 获取父日志记录器
     *
     * <p>JDBC 4.0规范要求的方法，用于日志记录。</p>
     *
     * @return 父日志记录器
     * @throws SQLFeatureNotSupportedException 如果驱动不支持此功能
     */
    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("getParentLogger not supported");
    }

    /**
     * 查找底层的JDBC驱动
     *
     * <p>遍历所有已注册的JDBC驱动，找到能够处理指定URL的驱动。
     * 这个方法实现了驱动的自动发现机制，确保能够找到合适的底层驱动来处理真实的数据库连接。</p>
     *
     * <p>查找过程：</p>
     * <ol>
     *   <li>获取所有已注册的驱动</li>
     *   <li>遍历每个驱动，调用其 {@code acceptsURL} 方法</li>
     *   <li>返回第一个接受该URL的驱动（排除自身）</li>
     *   <li>如果没有找到合适的驱动，返回null</li>
     * </ol>
     *
     * @param realUrl 真实的数据库URL（已去掉interceptor前缀）
     * @return 能够处理该URL的底层驱动，如果没有找到则返回null
     * @throws SQLException 如果发生错误
     */
    private Driver findUnderlyingDriver(String realUrl) throws SQLException {
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            // 排除自身，避免无限递归
            if (driver != this && driver.acceptsURL(realUrl)) {
                return driver;
            }
        }
        return null;
    }
}
