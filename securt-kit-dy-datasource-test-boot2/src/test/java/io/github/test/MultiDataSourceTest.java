package io.github.test;

import com.zaxxer.hikari.HikariDataSource;
import io.github.hexlodev.core.TableCache;
import io.github.hexlodev.core.config.FieldEncryptorProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 多数据源测试类
 * 
 * <p>测试多数据源场景下的字段加密功能：</p>
 * <ul>
 *   <li>不同数据源可以配置不同的加密策略</li>
 *   <li>主数据源加密 phone 字段，从数据源不加密</li>
 *   <li>验证配置隔离和正确性</li>
 * </ul>
 * 
 * @author hexlodev
 * @since 1.1.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MultiDataSourceTest {

    private static final String PRIMARY_DB_URL = "jdbc:interceptor:h2:mem:primary_db;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final String SECONDARY_DB_URL = "jdbc:interceptor:h2:mem:secondary_db;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final String THIRD_DB_URL = "jdbc:interceptor:h2:mem:third_db;DB_CLOSE_DELAY=-1;MODE=MySQL";

    private DataSource primaryDataSource;
    private DataSource secondaryDataSource;
    private DataSource thirdDataSource;

    @BeforeAll
    void setUp() throws ClassNotFoundException {
        // 加载拦截器驱动
        Class.forName("io.github.hexlodev.core.interceptor.SimpleInterceptorDriver");

        // 初始化多数据源加密配置（新配置方式：在 tables 中直接指定 datasource-id）
        FieldEncryptorProperties props = new FieldEncryptorProperties();
        props.setEnable(true);

        // 主数据源配置：加密 name 和 phone
        FieldEncryptorProperties.TableConfig primaryUserTable = new FieldEncryptorProperties.TableConfig();
        primaryUserTable.setTableName("user");
        primaryUserTable.setDatasourceId("primary");
        FieldEncryptorProperties.FieldConfig primaryNameField = new FieldEncryptorProperties.FieldConfig();
        primaryNameField.setFieldName("name");
        FieldEncryptorProperties.FieldConfig primaryPhoneField = new FieldEncryptorProperties.FieldConfig();
        primaryPhoneField.setFieldName("phone");
        primaryUserTable.setFields(java.util.Arrays.asList(primaryNameField, primaryPhoneField));

        // 从数据源配置：只加密 name
        FieldEncryptorProperties.TableConfig secondaryUserTable = new FieldEncryptorProperties.TableConfig();
        secondaryUserTable.setTableName("user");
        secondaryUserTable.setDatasourceId("secondary");
        FieldEncryptorProperties.FieldConfig secondaryNameField = new FieldEncryptorProperties.FieldConfig();
        secondaryNameField.setFieldName("name");
        secondaryUserTable.setFields(java.util.Arrays.asList(secondaryNameField));

        // 第三方数据源不配置（等于关闭加密）

        // 设置表配置列表
        props.setTables(java.util.Arrays.asList(primaryUserTable, secondaryUserTable));

        // 初始化 TableCache
        TableCache.init(props);

        // 创建数据源
        primaryDataSource = createDataSource("primary", PRIMARY_DB_URL);
        secondaryDataSource = createDataSource("secondary", SECONDARY_DB_URL);
        thirdDataSource = createDataSource("third", THIRD_DB_URL);

        // 初始化表结构
        initTables();
    }

    /**
     * 创建数据源
     * 通过连接属性传递数据源标识（推荐方式）
     */
    private DataSource createDataSource(String datasourceId, String baseUrl) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName("io.github.hexlodev.core.interceptor.SimpleInterceptorDriver");
        // URL 中不再包含 datasource-id 参数，保持 URL 干净
        dataSource.setJdbcUrl(baseUrl);
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setMaximumPoolSize(5);
        // 通过连接属性传递数据源标识（推荐方式）
        dataSource.addDataSourceProperty("datasource-id", datasourceId);
        return dataSource;
    }

    /**
     * 初始化表结构
     */
    private void initTables() {
        String createTableSql = "CREATE TABLE IF NOT EXISTS user (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                "name VARCHAR(100), " +
                "phone VARCHAR(50), " +
                "email VARCHAR(100)" +
                ")";

        try {
            try (Connection conn = primaryDataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(createTableSql);
            }

            try (Connection conn = secondaryDataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(createTableSql);
            }

            try (Connection conn = thirdDataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(createTableSql);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize tables", e);
        }
    }

    /**
     * 测试主数据源加密功能
     * 主数据源应该加密 name 和 phone 字段
     */
    @Test
    void testPrimaryDataSourceEncryption() throws SQLException {
        String originalName = "张三";
        String originalPhone = "13800138000";

        // 插入数据
        try (Connection conn = primaryDataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT INTO user (name, phone) VALUES (?, ?)")) {
            pstmt.setString(1, originalName);
            pstmt.setString(2, originalPhone);
            pstmt.executeUpdate();
        }

        // 查询数据（应该自动解密）
        try (Connection conn = primaryDataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT name, phone FROM user WHERE name = ?")) {
            pstmt.setString(1, originalName);
            try (ResultSet rs = pstmt.executeQuery()) {
                assertTrue(rs.next(), "应该查询到数据");
                String decryptedName = rs.getString("name");
                String decryptedPhone = rs.getString("phone");
                
                // 验证解密后的值应该等于原始值
                assertEquals(originalName, decryptedName, "name 字段应该正确解密");
                assertEquals(originalPhone, decryptedPhone, "phone 字段应该正确解密");
            }
        }

        // 直接查询数据库（验证数据确实是加密存储的）
        try (Connection conn = primaryDataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name, phone FROM user")) {
            assertTrue(rs.next());
            String encryptedName = rs.getString("name");
            String encryptedPhone = rs.getString("phone");
            
            // 验证数据是加密的（不等于原始值）
            assertNotEquals(originalName, encryptedName, "name 字段应该被加密存储");
            assertNotEquals(originalPhone, encryptedPhone, "phone 字段应该被加密存储");
        }
    }

    /**
     * 测试从数据源加密功能
     * 从数据源应该只加密 name 字段，不加密 phone 字段
     */
    @Test
    void testSecondaryDataSourceEncryption() throws SQLException {
        String originalName = "李四";
        String originalPhone = "13900139000";

        // 插入数据
        try (Connection conn = secondaryDataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT INTO user (name, phone) VALUES (?, ?)")) {
            pstmt.setString(1, originalName);
            pstmt.setString(2, originalPhone);
            pstmt.executeUpdate();
        }

        // 查询数据
        try (Connection conn = secondaryDataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT name, phone FROM user WHERE name = ?")) {
            pstmt.setString(1, originalName);
            try (ResultSet rs = pstmt.executeQuery()) {
                assertTrue(rs.next());
                String decryptedName = rs.getString("name");
                String phone = rs.getString("phone");
                
                // name 字段应该被解密（因为配置了加密）
                assertEquals(originalName, decryptedName, "name 字段应该正确解密");
                // phone 字段应该保持原样（因为未配置加密）
                assertEquals(originalPhone, phone, "phone 字段应该保持原样（未加密）");
            }
        }

        // 直接查询数据库验证
        try (Connection conn = secondaryDataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name, phone FROM user")) {
            assertTrue(rs.next());
            String encryptedName = rs.getString("name");
            String plainPhone = rs.getString("phone");
            
            // name 应该被加密
            assertNotEquals(originalName, encryptedName, "name 字段应该被加密存储");
            // phone 应该未加密
            assertEquals(originalPhone, plainPhone, "phone 字段应该未加密存储");
        }
    }

    /**
     * 测试第三方数据源（关闭加密）
     * 第三方数据源应该不加密任何字段
     */
    @Test
    void testThirdDataSourceNoEncryption() throws SQLException {
        String originalName = "王五";
        String originalPhone = "13700137000";

        // 插入数据
        try (Connection conn = thirdDataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT INTO user (name, phone) VALUES (?, ?)")) {
            pstmt.setString(1, originalName);
            pstmt.setString(2, originalPhone);
            pstmt.executeUpdate();
        }

        // 查询数据
        try (Connection conn = thirdDataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT name, phone FROM user WHERE name = ?")) {
            pstmt.setString(1, originalName);
            try (ResultSet rs = pstmt.executeQuery()) {
                assertTrue(rs.next());
                String name = rs.getString("name");
                String phone = rs.getString("phone");
                
                // 所有字段都应该保持原样（未加密）
                assertEquals(originalName, name, "name 字段应该保持原样（未加密）");
                assertEquals(originalPhone, phone, "phone 字段应该保持原样（未加密）");
            }
        }

        // 直接查询数据库验证
        try (Connection conn = thirdDataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name, phone FROM user")) {
            assertTrue(rs.next());
            String plainName = rs.getString("name");
            String plainPhone = rs.getString("phone");
            
            // 所有字段都应该未加密
            assertEquals(originalName, plainName, "name 字段应该未加密存储");
            assertEquals(originalPhone, plainPhone, "phone 字段应该未加密存储");
        }
    }

    /**
     * 测试配置隔离
     * 验证不同数据源的配置不会相互影响
     */
    @Test
    void testConfigurationIsolation() {
        // 验证主数据源的配置
        assertTrue(TableCache.concatTable("user", "primary"), 
                "主数据源的 user 表应该需要加密");
        
        // 验证从数据源的配置
        assertTrue(TableCache.concatTable("user", "secondary"), 
                "从数据源的 user 表应该需要加密");
        
        // 验证第三方数据源的配置
        assertFalse(TableCache.concatTable("user", "third"), 
                "第三方数据源的 user 表不应该需要加密（enable=false）");

        // 验证字段级别的配置
        assertNotNull(TableCache.getTableFieldEncryptStrategy("user", "phone", "primary"),
                "主数据源的 phone 字段应该配置了加密策略");
        assertNull(TableCache.getTableFieldEncryptStrategy("user", "phone", "secondary"),
                "从数据源的 phone 字段不应该配置加密策略");
        assertNull(TableCache.getTableFieldEncryptStrategy("user", "phone", "third"),
                "第三方数据源的 phone 字段不应该配置加密策略");
    }

    /**
     * 测试数据源标识提取
     * 验证从连接属性中正确提取数据源标识
     */
    @Test
    void testDatasourceIdExtraction() throws SQLException {
        // 测试从连接属性中提取数据源标识
        String url = "jdbc:interceptor:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=MySQL";
        java.util.Properties props = new java.util.Properties();
        props.setProperty("user", "sa");
        props.setProperty("password", "");
        props.setProperty("datasource-id", "test-ds");  // 通过连接属性传递
        
        try (Connection conn = java.sql.DriverManager.getConnection(url, props)) {
            // 连接应该成功创建
            assertNotNull(conn, "应该成功创建连接");
            assertFalse(conn.isClosed(), "连接不应该是关闭状态");
            
            // 验证连接是包装的连接，并且包含数据源标识
            if (conn instanceof io.github.hexlodev.core.interceptor.SimpleInterceptorConnection) {
                io.github.hexlodev.core.interceptor.SimpleInterceptorConnection interceptorConn = 
                    (io.github.hexlodev.core.interceptor.SimpleInterceptorConnection) conn;
                assertEquals("test-ds", interceptorConn.getDatasourceId(), 
                    "数据源标识应该正确提取");
            }
        }
    }
}

