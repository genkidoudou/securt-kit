package io.github.hexlodev.core.interceptor;

import java.sql.*;
import java.util.Properties;

/**
 * 演示SQL表名解析功能
 * 展示如何使用TableNameParser解析拦截的SQL中的表名
 */
public class TableNameParsingDemo {
    
    public static void main(String[] args) {
        System.out.println("=== SQL Table Name Parsing Demo ===");
        
        try {
            // 确保拦截器驱动被加载
            Class.forName("io.github.hexlodev.core.interceptor.SimpleInterceptorDriver");
            
            // 演示表名解析功能
            demonstrateTableNameParsing();
            
        } catch (Exception e) {
            System.err.println("Demo failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void demonstrateTableNameParsing() throws SQLException {
        System.out.println("\n--- Table Name Parsing Demo ---");
        
        // 使用拦截器URL连接数据库
        String interceptorUrl = "jdbc:interceptor:h2:mem:testdb;DB_CLOSE_DELAY=-1";
        Properties props = new Properties();
        props.setProperty("user", "sa");
        props.setProperty("password", "");
        
        System.out.println("Connecting to: " + interceptorUrl);
        
        try (Connection conn = DriverManager.getConnection(interceptorUrl, props)) {
            System.out.println("SUCCESS: Connected through interceptor driver");
            
            // 演示各种SQL语句的表名解析
            demonstrateVariousSqlStatements(conn);
            
        } catch (SQLException e) {
            System.err.println("FAILED: Could not connect through interceptor driver");
            System.err.println("Error: " + e.getMessage());
            throw e;
        }
    }
    
    private static void demonstrateVariousSqlStatements(Connection conn) throws SQLException {
        System.out.println("\n--- Various SQL Statements Demo ---");
        
        try (Statement stmt = conn.createStatement()) {
            
            // 1. CREATE TABLE 语句
            System.out.println("\n1. CREATE TABLE statement:");
            stmt.execute("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(50), email VARCHAR(100))");
            
            // 2. INSERT 语句
            System.out.println("\n2. INSERT statement:");
            stmt.executeUpdate("INSERT INTO users VALUES (1, 'Alice', 'alice@example.com')");
            
            // 3. SELECT 语句
            System.out.println("\n3. SELECT statement:");
            try (ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE name = 'Alice'")) {
                while (rs.next()) {
                    System.out.println("  Found user: " + rs.getString("name"));
                }
            }
            
            // 4. UPDATE 语句
            System.out.println("\n4. UPDATE statement:");
            stmt.executeUpdate("UPDATE users SET email = 'alice.updated@example.com' WHERE id = 1");
            
            // 5. DELETE 语句
            System.out.println("\n5. DELETE statement:");
            stmt.executeUpdate("DELETE FROM users WHERE id = 1");
            
            // 6. 多表JOIN查询
            System.out.println("\n6. Multi-table JOIN query:");
            stmt.execute("CREATE TABLE IF NOT EXISTS orders (id INT PRIMARY KEY, user_id INT, amount DECIMAL(10,2))");
            stmt.executeUpdate("INSERT INTO orders VALUES (1, 1, 99.99)");
            
            try (ResultSet rs = stmt.executeQuery(
                "SELECT u.name, o.amount FROM users u JOIN orders o ON u.id = o.user_id")) {
                while (rs.next()) {
                    System.out.println("  Order: " + rs.getString("name") + " - $" + rs.getBigDecimal("amount"));
                }
            }
            
            // 7. 子查询
            System.out.println("\n7. Subquery statement:");
            try (ResultSet rs = stmt.executeQuery(
                "SELECT * FROM users WHERE id IN (SELECT user_id FROM orders WHERE amount > 50)")) {
                while (rs.next()) {
                    System.out.println("  User with high-value order: " + rs.getString("name"));
                }
            }
            
            // 8. PreparedStatement 演示
            System.out.println("\n8. PreparedStatement demo:");
            demonstratePreparedStatementWithTableNames(conn);
            
        }
    }
    
    private static void demonstratePreparedStatementWithTableNames(Connection conn) throws SQLException {
        // 创建产品表
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS products (id INT PRIMARY KEY, name VARCHAR(100), price DECIMAL(10,2))");
            stmt.executeUpdate("INSERT INTO products VALUES (1, 'Laptop', 999.99)");
            stmt.executeUpdate("INSERT INTO products VALUES (2, 'Mouse', 29.99)");
        }
        
        // 使用PreparedStatement查询
        String sql = "SELECT * FROM products WHERE price > ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, 50.0);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    System.out.println("  Product: " + rs.getString("name") + " - $" + rs.getBigDecimal("price"));
                }
            }
        }
        
        // 使用PreparedStatement插入
        String insertSql = "INSERT INTO products (id, name, price) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            pstmt.setInt(1, 3);
            pstmt.setString(2, "Keyboard");
            pstmt.setDouble(3, 79.99);
            
            int rowsAffected = pstmt.executeUpdate();
            System.out.println("  Inserted " + rowsAffected + " product(s)");
        }
    }
}
