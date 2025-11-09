package io.github.test;

import io.github.hexlodev.core.TableCache;
import io.github.hexlodev.core.config.FieldEncryptorProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 带数据库名的增删改查测试类
 * 
 * 测试使用数据库名.表名格式（如 testdb.user）的 SQL 语句，确保 SQL 执行成功
 * 
 * @author hexlodev
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = TestApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CrudWithAliasTest {

    private static final String DATABASE_NAME = "testdb";
    private static final String TABLE_NAME = "user";
    private static final String FULL_TABLE_NAME = DATABASE_NAME + "." + TABLE_NAME;

    @Autowired
    private DataSource dataSource;

    @BeforeAll
    void setUp() {
        // 加载拦截器驱动
        try {
            Class.forName("io.github.hexlodev.core.interceptor.SimpleInterceptorDriver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load SimpleInterceptorDriver", e);
        }

        // 初始化加密配置
        FieldEncryptorProperties props = new FieldEncryptorProperties();
        props.setEnable(true);

        FieldEncryptorProperties.TableConfig userTable = new FieldEncryptorProperties.TableConfig();
        // 注意：TableCache 中存储的是纯表名，不带数据库名
        userTable.setTableName(TABLE_NAME);

        // 配置需要加密的字段
        FieldEncryptorProperties.FieldConfig nameField = new FieldEncryptorProperties.FieldConfig();
        nameField.setFieldName("name");

        FieldEncryptorProperties.FieldConfig phoneField = new FieldEncryptorProperties.FieldConfig();
        phoneField.setFieldName("phone");

        FieldEncryptorProperties.FieldConfig emailField = new FieldEncryptorProperties.FieldConfig();
        emailField.setFieldName("email");

        userTable.setFields(java.util.Arrays.asList(nameField, phoneField, emailField));
        props.setTables(java.util.Collections.singletonList(userTable));

        TableCache.init(props);
    }

    /**
     * 创建测试数据库和表
     */
    @BeforeEach
    void setUpTable() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                // 创建 schema（数据库）
                stmt.execute("CREATE SCHEMA IF NOT EXISTS " + DATABASE_NAME);
                
                // 删除表（如果存在）
                stmt.execute("DROP TABLE IF EXISTS " + FULL_TABLE_NAME);
                
                // 创建表（在指定 schema 中）
                stmt.execute("CREATE TABLE " + FULL_TABLE_NAME + " (" +
                        "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                        "name VARCHAR(100), " +
                        "phone VARCHAR(100), " +
                        "age INT, " +
                        "email VARCHAR(100)" +
                        ")");
            }
        }
    }

    /**
     * 测试 INSERT - 带数据库名
     */
    @Test
    void testInsert() throws SQLException {
        String sql = "INSERT INTO " + FULL_TABLE_NAME + " (id, name, phone, age, email) VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            // 设置参数
            pstmt.setLong(1, 1L);
            pstmt.setString(2, "张三");
            pstmt.setString(3, "13800138000");
            pstmt.setInt(4, 25);
            pstmt.setString(5, "zhangsan@example.com");
            
            // 执行插入
            int result = pstmt.executeUpdate();
            assertEquals(1, result, "应该插入1条记录");
            
            // 获取生成的主键
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    System.out.println("testInsert -> inserted id: " + id);
                    assertNotNull(id, "ID 应该被生成");
                }
            }
        }
    }

    /**
     * 测试 INSERT - 使用 INSERT INTO ... SELECT 格式（带别名）
     */
    @Test
    void testInsertWithSelectAlias() throws SQLException {
        // 先插入一条数据
        insertTestData();
        
        String sql = "INSERT INTO " + FULL_TABLE_NAME + " (id, name, phone, age, email) " +
                "SELECT u.id + 10, u.name, u.phone, u.age, u.email " +
                "FROM " + FULL_TABLE_NAME + " u WHERE u.id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setLong(1, 1L);
            
            int result = pstmt.executeUpdate();
            assertEquals(1, result, "应该插入1条记录");
            
            System.out.println("testInsertWithSelectAlias -> inserted successfully");
        }
    }

    /**
     * 测试 SELECT - 带表别名
     */
    @Test
    void testSelectWithAlias() throws SQLException {
        // 先插入测试数据
        insertTestData();
        
        String sql = "SELECT u.id, u.name, u.phone, u.age, u.email FROM " + FULL_TABLE_NAME + " u WHERE u.id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setLong(1, 1L);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                assertTrue(rs.next(), "应该能查询到数据");
                
                Long id = rs.getLong("id");
                String name = rs.getString("name");
                String phone = rs.getString("phone");
                Integer age = rs.getInt("age");
                String email = rs.getString("email");
                
                System.out.println("testSelectWithAlias -> id: " + id + 
                        ", name: " + name + 
                        ", phone: " + phone + 
                        ", age: " + age + 
                        ", email: " + email);
                
                assertEquals(1L, id, "ID 应该匹配");
                assertEquals("张三", name, "姓名应该被正确解密");
                assertEquals("13800138000", phone, "手机号应该被正确解密");
                assertEquals("zhangsan@example.com", email, "邮箱应该被正确解密");
            }
        }
    }

    /**
     * 测试 SELECT * - 带表别名
     */
    @Test
    void testSelectStarWithAlias() throws SQLException {
        // 先插入测试数据
        insertTestData();
        
        String sql = "SELECT * FROM " + FULL_TABLE_NAME + " u WHERE u.id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setLong(1, 1L);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                assertTrue(rs.next(), "应该能查询到数据");
                
                Long id = rs.getLong("id");
                String name = rs.getString("name");
                String phone = rs.getString("phone");
                
                System.out.println("testSelectStarWithAlias -> id: " + id + 
                        ", name: " + name + 
                        ", phone: " + phone);
                
                assertEquals(1L, id, "ID 应该匹配");
                assertEquals("张三", name, "姓名应该被正确解密");
                assertEquals("13800138000", phone, "手机号应该被正确解密");
            }
        }
    }

    /**
     * 测试 SELECT - 多表 JOIN 带别名
     */
    @Test
    void testSelectWithJoinAlias() throws SQLException {
        // 创建订单表
        createOrderTable();
        
        // 插入用户数据
        insertTestData();
        
        // 插入订单数据
        insertOrderData();
        
        String sql = "SELECT u.id as user_id, u.name as user_name, u.phone as user_phone, " +
                "o.id as order_id, o.order_no " +
                "FROM " + FULL_TABLE_NAME + " u " +
                "LEFT JOIN " + DATABASE_NAME + ".orders o ON u.id = o.user_id " +
                "WHERE u.id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setLong(1, 1L);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                assertTrue(rs.next(), "应该能查询到数据");
                
                Long userId = rs.getLong("user_id");
                String userName = rs.getString("user_name");
                String userPhone = rs.getString("user_phone");
                
                System.out.println("testSelectWithJoinAlias -> user_id: " + userId + 
                        ", user_name: " + userName + 
                        ", user_phone: " + userPhone);
                
                assertEquals(1L, userId, "用户ID应该匹配");
                assertEquals("张三", userName, "用户名应该被正确解密");
                assertEquals("13800138000", userPhone, "用户手机号应该被正确解密");
            }
        }
    }

    /**
     * 测试 UPDATE - 带表别名
     */
    @Test
    void testUpdateWithAlias() throws SQLException {
        // 先插入测试数据
        insertTestData();
        
        String sql = "UPDATE " + FULL_TABLE_NAME + " u SET u.name = ?, u.phone = ?, u.age = ? WHERE u.id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, "张三(更新)");
            pstmt.setString(2, "13800138001");
            pstmt.setInt(3, 26);
            pstmt.setLong(4, 1L);
            
            int result = pstmt.executeUpdate();
            assertEquals(1, result, "应该更新1条记录");
            
            System.out.println("testUpdateWithAlias -> updated successfully");
            
            // 验证更新结果
            try (PreparedStatement selectStmt = conn.prepareStatement(
                    "SELECT u.name, u.phone, u.age FROM " + FULL_TABLE_NAME + " u WHERE u.id = ?")) {
                selectStmt.setLong(1, 1L);
                try (ResultSet rs = selectStmt.executeQuery()) {
                    assertTrue(rs.next(), "应该能查询到更新后的数据");
                    assertEquals("张三(更新)", rs.getString("name"), "姓名应该被更新并正确解密");
                    assertEquals("13800138001", rs.getString("phone"), "手机号应该被更新并正确解密");
                    assertEquals(26, rs.getInt("age"), "年龄应该被更新");
                }
            }
        }
    }

    /**
     * 测试 UPDATE - 不带表别名（标准格式）
     */
    @Test
    void testUpdateWithoutAlias() throws SQLException {
        // 先插入测试数据
        insertTestData();
        
        String sql = "UPDATE " + FULL_TABLE_NAME + " SET name = ?, phone = ?, age = ? WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, "李四(更新)");
            pstmt.setString(2, "13900139001");
            pstmt.setInt(3, 31);
            pstmt.setLong(4, 2L);
            
            int result = pstmt.executeUpdate();
            assertEquals(1, result, "应该更新1条记录");
            
            System.out.println("testUpdateWithoutAlias -> updated successfully");
        }
    }

    /**
     * 测试 DELETE - 带表别名
     */
    @Test
    void testDeleteWithAlias() throws SQLException {
        // 先插入测试数据
        insertTestData();
        
        String sql = "DELETE FROM " + FULL_TABLE_NAME + " u WHERE u.id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setLong(1, 1L);
            
            int result = pstmt.executeUpdate();
            assertEquals(1, result, "应该删除1条记录");
            
            System.out.println("testDeleteWithAlias -> deleted successfully");
            
            // 验证删除结果
            try (PreparedStatement selectStmt = conn.prepareStatement(
                    "SELECT COUNT(*) as cnt FROM " + FULL_TABLE_NAME + " u WHERE u.id = ?")) {
                selectStmt.setLong(1, 1L);
                try (ResultSet rs = selectStmt.executeQuery()) {
                    assertTrue(rs.next(), "应该能查询到计数结果");
                    assertEquals(0, rs.getInt("cnt"), "记录应该已被删除");
                }
            }
        }
    }

    /**
     * 测试 DELETE - 不带表别名（标准格式）
     */
    @Test
    void testDeleteWithoutAlias() throws SQLException {
        // 先插入测试数据
        insertTestData();
        
        String sql = "DELETE FROM " + FULL_TABLE_NAME + " WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setLong(1, 2L);
            
            int result = pstmt.executeUpdate();
            assertEquals(1, result, "应该删除1条记录");
            
            System.out.println("testDeleteWithoutAlias -> deleted successfully");
        }
    }

    /**
     * 测试 SELECT - 多条件查询带别名
     */
    @Test
    void testSelectWithMultipleConditions() throws SQLException {
        // 先插入测试数据
        insertTestData();
        
        String sql = "SELECT u.id, u.name, u.phone, u.age, u.email " +
                "FROM " + FULL_TABLE_NAME + " u " +
                "WHERE u.age > ? AND u.phone LIKE ? " +
                "ORDER BY u.id";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, 20);
            pstmt.setString(2, "13%");
            
            try (ResultSet rs = pstmt.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    Long id = rs.getLong("id");
                    String name = rs.getString("name");
                    String phone = rs.getString("phone");
                    
                    System.out.println("testSelectWithMultipleConditions -> id: " + id + 
                            ", name: " + name + 
                            ", phone: " + phone);
                    
                    assertTrue(phone.startsWith("13"), "手机号应该以13开头");
                }
                
                assertTrue(count > 0, "应该查询到至少1条记录");
            }
        }
    }

    /**
     * 测试 SELECT - 子查询带别名
     */
    @Test
    void testSelectWithSubQueryAlias() throws SQLException {
        // 先插入测试数据
        insertTestData();
        
        String sql = "SELECT * FROM (" +
                "SELECT u.id, u.name, u.phone, u.age, u.email FROM " + FULL_TABLE_NAME + " u WHERE u.age > ?" +
                ") tmp WHERE tmp.id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, 20);
            pstmt.setLong(2, 1L);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                assertTrue(rs.next(), "应该能查询到数据");
                
                Long id = rs.getLong("id");
                String name = rs.getString("name");
                String phone = rs.getString("phone");
                
                System.out.println("testSelectWithSubQueryAlias -> id: " + id + 
                        ", name: " + name + 
                        ", phone: " + phone);
                
                assertEquals(1L, id, "ID 应该匹配");
                assertEquals("张三", name, "姓名应该被正确解密");
                assertEquals("13800138000", phone, "手机号应该被正确解密");
            }
        }
    }

    /**
     * 插入测试数据
     */
    private void insertTestData() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // 插入两条测试数据
            stmt.executeUpdate("INSERT INTO " + FULL_TABLE_NAME + " (id, name, phone, age, email) VALUES " +
                    "(1, '张三', '13800138000', 25, 'zhangsan@example.com'), " +
                    "(2, '李四', '13900139000', 30, 'lisi@example.com')");
        }
    }

    /**
     * 创建订单表（用于 JOIN 测试）
     */
    private void createOrderTable() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            String ordersTable = DATABASE_NAME + ".orders";
            stmt.execute("DROP TABLE IF EXISTS " + ordersTable);
            stmt.execute("CREATE TABLE " + ordersTable + " (" +
                    "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                    "user_id BIGINT, " +
                    "order_no VARCHAR(50)" +
                    ")");
        }
    }

    /**
     * 插入订单数据
     */
    private void insertOrderData() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO " + DATABASE_NAME + ".orders (id, user_id, order_no) VALUES " +
                    "(1, 1, 'ORDER001')");
        }
    }
}

