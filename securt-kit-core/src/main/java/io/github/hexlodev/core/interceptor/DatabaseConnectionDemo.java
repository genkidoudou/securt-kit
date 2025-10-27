package io.github.hexlodev.core.interceptor;

import java.sql.*;
import java.util.Properties;

/**
 * 演示数据库连接拦截功能
 * 这个演示展示了如何使用拦截器驱动连接到H2数据库
 */
public class DatabaseConnectionDemo {
    
    public static void main(String[] args) {
        System.out.println("=== Database Connection Interceptor Demo ===");
        
        try {
            // 确保拦截器驱动被加载
            Class.forName("io.github.hexlodev.core.interceptor.SimpleInterceptorDriver");
            
            // 演示拦截器驱动的数据库连接
            demonstrateDatabaseConnection();
            
        } catch (Exception e) {
            System.err.println("Demo failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void demonstrateDatabaseConnection() throws SQLException {
        System.out.println("\n--- Database Connection Demo ---");
        
        // 使用拦截器URL连接数据库
        String interceptorUrl = "jdbc:interceptor:h2:mem:testdb;DB_CLOSE_DELAY=-1";
        Properties props = new Properties();
        props.setProperty("user", "sa");
        props.setProperty("password", "");
        
        System.out.println("Connecting to: " + interceptorUrl);
        
        try (Connection conn = DriverManager.getConnection(interceptorUrl, props)) {
            System.out.println("SUCCESS: Connected through interceptor driver");
            System.out.println("Connection class: " + conn.getClass().getName());
            
            // 测试基本的数据库操作
            testBasicOperations(conn);
            
        } catch (SQLException e) {
            System.err.println("FAILED: Could not connect through interceptor driver");
            System.err.println("Error: " + e.getMessage());
            throw e;
        }
    }
    
    private static void testBasicOperations(Connection conn) throws SQLException {
        System.out.println("\n--- Testing Basic Database Operations ---");
        
        // 创建测试表
        try (Statement stmt = conn.createStatement()) {
            System.out.println("Creating test table...");
            stmt.execute("CREATE TABLE IF NOT EXISTS test_table (id INT PRIMARY KEY, name VARCHAR(50))");
            System.out.println("SUCCESS: Test table created");
            
            // 插入数据
            System.out.println("Inserting test data...");
            int insertCount = stmt.executeUpdate("INSERT INTO test_table VALUES (1, 'Test User')");
            System.out.println("SUCCESS: Inserted " + insertCount + " row(s)");
            
            // 查询数据
            System.out.println("Querying test data...");
            try (ResultSet rs = stmt.executeQuery("SELECT * FROM test_table")) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String name = rs.getString("name");
                    System.out.println("SUCCESS: Retrieved data - ID: " + id + ", Name: " + name);
                }
            }
            
            // 更新数据
            System.out.println("Updating test data...");
            int updateCount = stmt.executeUpdate("UPDATE test_table SET name = 'Updated User' WHERE id = 1");
            System.out.println("SUCCESS: Updated " + updateCount + " row(s)");
            
            // 删除数据
            System.out.println("Deleting test data...");
            int deleteCount = stmt.executeUpdate("DELETE FROM test_table WHERE id = 1");
            System.out.println("SUCCESS: Deleted " + deleteCount + " row(s)");
            
        } catch (SQLException e) {
            System.err.println("FAILED: Database operation failed");
            System.err.println("Error: " + e.getMessage());
            throw e;
        }
    }
}
