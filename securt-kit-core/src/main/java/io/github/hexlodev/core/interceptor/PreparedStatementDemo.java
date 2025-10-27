package io.github.hexlodev.core.interceptor;

import java.sql.*;
import java.util.Properties;

/**
 * 演示PreparedStatement的SQL打印功能
 * 展示参数化查询的拦截和日志记录
 */
public class PreparedStatementDemo {
    
    public static void main(String[] args) {
        System.out.println("=== PreparedStatement SQL Printing Demo ===");
        
        try {
            // 确保拦截器驱动被加载
            Class.forName("io.github.hexlodev.core.interceptor.SimpleInterceptorDriver");
            
            // 演示PreparedStatement的SQL打印
            demonstratePreparedStatement();
            
        } catch (Exception e) {
            System.err.println("Demo failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void demonstratePreparedStatement() throws SQLException {
        System.out.println("\n--- PreparedStatement Demo ---");
        
        // 使用拦截器URL连接数据库
        String interceptorUrl = "jdbc:interceptor:h2:mem:testdb;DB_CLOSE_DELAY=-1";
        Properties props = new Properties();
        props.setProperty("user", "sa");
        props.setProperty("password", "");
        
        System.out.println("Connecting to: " + interceptorUrl);
        
        try (Connection conn = DriverManager.getConnection(interceptorUrl, props)) {
            System.out.println("SUCCESS: Connected through interceptor driver");
            
            // 创建测试表
            setupTestTable(conn);
            
            // 演示PreparedStatement的各种操作
            demonstratePreparedStatementOperations(conn);
            
        } catch (SQLException e) {
            System.err.println("FAILED: Could not connect through interceptor driver");
            System.err.println("Error: " + e.getMessage());
            throw e;
        }
    }
    
    private static void setupTestTable(Connection conn) throws SQLException {
        System.out.println("\n--- Setting up test table ---");
        
        try (Statement stmt = conn.createStatement()) {
            // 删除表（如果存在）
            stmt.execute("DROP TABLE IF EXISTS users");
            
            // 创建用户表
            stmt.execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(50), email VARCHAR(100), age INT)");
            
            // 插入一些测试数据
            stmt.executeUpdate("INSERT INTO users VALUES (1, 'Alice', 'alice@example.com', 25)");
            stmt.executeUpdate("INSERT INTO users VALUES (2, 'Bob', 'bob@example.com', 30)");
            stmt.executeUpdate("INSERT INTO users VALUES (3, 'Charlie', 'charlie@example.com', 35)");
            
            System.out.println("SUCCESS: Test table created with sample data");
        }
    }
    
    private static void demonstratePreparedStatementOperations(Connection conn) throws SQLException {
        System.out.println("\n--- PreparedStatement Operations Demo ---");
        
        // 1. 演示PreparedStatement查询
        demonstratePreparedQuery(conn);
        
        // 2. 演示PreparedStatement插入
        demonstratePreparedInsert(conn);
        
        // 3. 演示PreparedStatement更新
        demonstratePreparedUpdate(conn);
        
        // 4. 演示PreparedStatement删除
        demonstratePreparedDelete(conn);
    }
    
    private static void demonstratePreparedQuery(Connection conn) throws SQLException {
        System.out.println("\n--- PreparedStatement Query Demo ---");
        
        String sql = "SELECT * FROM users WHERE age > ?";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            // 设置参数
            pstmt.setInt(1, 28);
            
            // 执行查询
            try (ResultSet rs = pstmt.executeQuery()) {
                System.out.println("Query results:");
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String name = rs.getString("name");
                    String email = rs.getString("email");
                    int age = rs.getInt("age");
                    System.out.println("  ID: " + id + ", Name: " + name + ", Email: " + email + ", Age: " + age);
                }
            }
        }
    }
    
    private static void demonstratePreparedInsert(Connection conn) throws SQLException {
        System.out.println("\n--- PreparedStatement Insert Demo ---");
        
        String sql = "INSERT INTO users (id, name, email, age) VALUES (?, ?, ?, ?)";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            // 设置参数
            pstmt.setInt(1, 4);
            pstmt.setString(2, "David");
            pstmt.setString(3, "david@example.com");
            pstmt.setInt(4, 28);
            
            // 执行插入
            int rowsAffected = pstmt.executeUpdate();
            System.out.println("SUCCESS: Inserted " + rowsAffected + " row(s)");
        }
    }
    
    private static void demonstratePreparedUpdate(Connection conn) throws SQLException {
        System.out.println("\n--- PreparedStatement Update Demo ---");
        
        String sql = "UPDATE users SET age = ? WHERE name = ?";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            // 设置参数
            pstmt.setInt(1, 26);
            pstmt.setString(2, "Alice");
            
            // 执行更新
            int rowsAffected = pstmt.executeUpdate();
            System.out.println("SUCCESS: Updated " + rowsAffected + " row(s)");
        }
    }
    
    private static void demonstratePreparedDelete(Connection conn) throws SQLException {
        System.out.println("\n--- PreparedStatement Delete Demo ---");
        
        String sql = "DELETE FROM users WHERE age < ?";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            // 设置参数
            pstmt.setInt(1, 30);
            
            // 执行删除
            int rowsAffected = pstmt.executeUpdate();
            System.out.println("SUCCESS: Deleted " + rowsAffected + " row(s)");
        }
    }
}
