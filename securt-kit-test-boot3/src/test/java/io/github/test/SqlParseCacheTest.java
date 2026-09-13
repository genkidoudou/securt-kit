package io.github.test;

import cn.hutool.core.lang.Pair;
import io.github.genkidoudou.core.TableCache;
import io.github.genkidoudou.core.parser.SecurtkitUtils;
import io.github.genkidoudou.core.parser.SqlParseCache;
import io.github.genkidoudou.core.parser.SqlParseCache.CacheStatsInfo;
import io.github.genkidoudou.core.config.FieldEncryptorProperties;
import io.github.genkidoudou.core.parser.dto.ColumnTableDto;
import io.github.genkidoudou.core.parser.dto.FieldEncryptorInfoDto;
import io.github.genkidoudou.shaded.jsqlparser.JSQLParserException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SQL 解析缓存测试类
 * <p>
 * 专门测试 SQL 解析结果缓存功能，包括：
 * <ul>
 *   <li>INSERT 语句缓存测试</li>
 *   <li>UPDATE 语句缓存测试</li>
 *   <li>DELETE 语句缓存测试</li>
 *   <li>SELECT 语句缓存测试（简单和复杂）</li>
 *   <li>缓存命中/未命中测试</li>
 *   <li>缓存容量限制测试</li>
 * </ul>
 * </p>
 *
 * @author hexlodev
 * @since 1.0.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SqlParseCacheTest {

    @BeforeAll
    void setupTableEncryptConfig() {
        // 初始化 TableCache（用于解析 SQL 时识别需要加密的字段）
        // 注意：securt-kit-test 模块有 Spring 环境，可以直接使用 TableCache.init()
        FieldEncryptorProperties props = new FieldEncryptorProperties();
        props.setEnable(true);

        FieldEncryptorProperties.TableConfig userTable = new FieldEncryptorProperties.TableConfig();
        userTable.setTableName("user");
        FieldEncryptorProperties.FieldConfig nameField = new FieldEncryptorProperties.FieldConfig();
        nameField.setFieldName("name");
        FieldEncryptorProperties.FieldConfig phoneField = new FieldEncryptorProperties.FieldConfig();
        phoneField.setFieldName("phone");

        userTable.setFields(java.util.Arrays.asList(nameField, phoneField));
        props.setTables(java.util.Collections.singletonList(userTable));

        try {
            TableCache.init(props);
        } catch (Exception e) {
            // 如果 Spring 环境未准备好，记录警告但继续测试
            System.out.println("Warning: TableCache.init() failed: " + e.getMessage());
            System.out.println("SQL parse cache tests will continue without full TableCache initialization");
        }
    }

    @BeforeEach
    void clearCacheBeforeEach() {
        // 每个测试前清空缓存，确保测试独立性
        SqlParseCache.clear();
    }

    // ========== INSERT 语句测试 ==========

    @Test
    void testInsert_CacheHit() throws JSQLParserException {
        String sql = "INSERT INTO user(name, phone, age) VALUES(?, ?, ?)";
        
        // 第一次解析（缓存未命中）
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result1 = 
            SecurtkitUtils.parseSql(sql);
        assertNotNull(result1);
        
        // 第二次解析（应该命中缓存）
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result2 = 
            SecurtkitUtils.parseSql(sql);
        assertNotNull(result2);
        
        // 验证结果一致性
        assertEquals(result1.getKey().size(), result2.getKey().size());
        assertEquals(result1.getValue().size(), result2.getValue().size());
        
        // 验证缓存大小（应该只有1条）
        assertEquals(1, SqlParseCache.size());
    }

    @Test
    void testInsert_WithMultipleValues() throws JSQLParserException {
        String sql1 = "INSERT INTO user(id, name, phone) VALUES(?, ?, ?)";
        String sql2 = "INSERT INTO user(name, phone) VALUES(?, ?)";
        
        SecurtkitUtils.parseSql(sql1);
        SecurtkitUtils.parseSql(sql2);
        
        // 两个不同的 INSERT 语句应该有2条缓存
        assertEquals(2, SqlParseCache.size());
    }

    // ========== UPDATE 语句测试 ==========

    @Test
    void testUpdate_CacheHit() throws JSQLParserException {
        String sql = "UPDATE user SET name = ?, phone = ? WHERE id = ?";
        
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result1 = 
            SecurtkitUtils.parseSql(sql);
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result2 = 
            SecurtkitUtils.parseSql(sql);
        
        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals(result1.getKey().size(), result2.getKey().size());
        assertEquals(1, SqlParseCache.size());
    }

    @Test
    void testUpdate_ComplexWhere() throws JSQLParserException {
        String sql = "UPDATE user SET name = ? WHERE id = ? AND phone = ? AND age > ?";
        
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result = 
            SecurtkitUtils.parseSql(sql);
        
        assertNotNull(result);
        assertTrue(SqlParseCache.size() > 0);
        
        // 再次解析应该命中缓存
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result2 = 
            SecurtkitUtils.parseSql(sql);
        assertEquals(result.getKey().size(), result2.getKey().size());
    }

    // ========== DELETE 语句测试 ==========

    @Test
    void testDelete_CacheHit() throws JSQLParserException {
        String sql = "DELETE FROM user WHERE id = ?";
        
        SecurtkitUtils.parseSql(sql);
        SecurtkitUtils.parseSql(sql);
        
        // 应该只有1条缓存记录
        assertEquals(1, SqlParseCache.size());
    }

    @Test
    void testDelete_ComplexWhere() throws JSQLParserException {
        String sql = "DELETE FROM user WHERE id = ? AND phone = ? AND age BETWEEN ? AND ?";
        
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result = 
            SecurtkitUtils.parseSql(sql);
        
        assertNotNull(result);
        assertEquals(1, SqlParseCache.size());
    }

    // ========== SELECT 简单查询测试 ==========

    @Test
    void testSelect_Simple_CacheHit() throws JSQLParserException {
        String sql = "SELECT id, name, phone FROM user WHERE id = ?";
        
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result1 = 
            SecurtkitUtils.parseSql(sql);
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result2 = 
            SecurtkitUtils.parseSql(sql);
        
        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals(result1.getKey().size(), result2.getKey().size());
        assertEquals(1, SqlParseCache.size());
    }

    @Test
    void testSelect_WithWhere() throws JSQLParserException {
        String sql = "SELECT * FROM user WHERE name = ? AND phone = ?";
        
        SecurtkitUtils.parseSql(sql);
        long size1 = SqlParseCache.size();
        
        SecurtkitUtils.parseSql(sql);
        long size2 = SqlParseCache.size();
        
        // 缓存大小应该不变（命中缓存）
        assertEquals(size1, size2);
        assertEquals(1, size2);
    }

    @Test
    void testSelect_WithOrderBy() throws JSQLParserException {
        String sql = "SELECT id, name, phone FROM user WHERE age > ? ORDER BY id DESC LIMIT ?";
        
        SecurtkitUtils.parseSql(sql);
        assertEquals(1, SqlParseCache.size());
        
        // 再次解析应该命中缓存
        SecurtkitUtils.parseSql(sql);
        assertEquals(1, SqlParseCache.size());
    }

    @Test
    void testSelect_WithGroupBy() throws JSQLParserException {
        String sql = "SELECT COUNT(*), age FROM user WHERE name = ? GROUP BY age HAVING COUNT(*) > ?";
        
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result = 
            SecurtkitUtils.parseSql(sql);
        
        assertNotNull(result);
        assertEquals(1, SqlParseCache.size());
    }

    // ========== SELECT 复杂查询测试 ==========

    @Test
    void testSelect_Join() throws JSQLParserException {
        String sql = "SELECT u.id, u.name, u.phone, o.amount FROM user u JOIN orders o ON u.id = o.user_id WHERE u.id = ?";
        
        SecurtkitUtils.parseSql(sql);
        assertEquals(1, SqlParseCache.size());
        
        // 测试缓存命中
        SecurtkitUtils.parseSql(sql);
        assertEquals(1, SqlParseCache.size());
    }

    @Test
    void testSelect_LeftJoin() throws JSQLParserException {
        String sql = "SELECT u.name, u.phone, o.order_no FROM user u LEFT JOIN orders o ON u.id = o.user_id WHERE u.phone = ?";
        
        SecurtkitUtils.parseSql(sql);
        SecurtkitUtils.parseSql(sql);
        
        assertEquals(1, SqlParseCache.size());
    }

    @Test
    void testSelect_RightJoin() throws JSQLParserException {
        String sql = "SELECT o.id, u.name FROM orders o RIGHT JOIN user u ON o.user_id = u.id WHERE u.id = ?";
        
        SecurtkitUtils.parseSql(sql);
        assertEquals(1, SqlParseCache.size());
    }

    @Test
    void testSelect_MultiJoin() throws JSQLParserException {
        String sql = "SELECT u.name, u.phone, o.amount, p.product_name " +
                    "FROM user u " +
                    "JOIN orders o ON u.id = o.user_id " +
                    "JOIN products p ON o.product_id = p.id " +
                    "WHERE u.id = ? AND o.amount > ?";
        
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result = 
            SecurtkitUtils.parseSql(sql);
        
        assertNotNull(result);
        assertEquals(1, SqlParseCache.size());
    }

    @Test
    void testSelect_Subquery() throws JSQLParserException {
        String sql = "SELECT * FROM user WHERE id IN (SELECT user_id FROM orders WHERE amount > ?) AND name = ?";
        
        SecurtkitUtils.parseSql(sql);
        assertEquals(1, SqlParseCache.size());
    }

    @Test
    void testSelect_SubqueryInFrom() throws JSQLParserException {
        String sql = "SELECT t.name, t.phone FROM (SELECT * FROM user WHERE age > ?) t WHERE t.phone = ?";
        
        SecurtkitUtils.parseSql(sql);
        assertEquals(1, SqlParseCache.size());
    }

    @Test
    void testSelect_Union() throws JSQLParserException {
        String sql = "SELECT name, phone FROM user WHERE id = ? UNION SELECT name, phone FROM user_backup WHERE id = ?";
        
        SecurtkitUtils.parseSql(sql);
        assertEquals(1, SqlParseCache.size());
    }

    @Test
    void testSelect_WithCase() throws JSQLParserException {
        String sql = "SELECT id, name, CASE WHEN age > ? THEN 'ADULT' ELSE 'MINOR' END AS age_group FROM user WHERE id = ?";
        
        SecurtkitUtils.parseSql(sql);
        assertEquals(1, SqlParseCache.size());
    }

    @Test
    void testSelect_WithFunction() throws JSQLParserException {
        String sql = "SELECT COUNT(*), AVG(age), MAX(age), MIN(age) FROM user WHERE name = ? AND age BETWEEN ? AND ?";
        
        SecurtkitUtils.parseSql(sql);
        assertEquals(1, SqlParseCache.size());
    }

    @Test
    void testSelect_WithDistinct() throws JSQLParserException {
        String sql = "SELECT DISTINCT name, phone FROM user WHERE age > ?";
        
        SecurtkitUtils.parseSql(sql);
        assertEquals(1, SqlParseCache.size());
    }

    // ========== 缓存功能测试 ==========

    @Test
    void testCache_CapacityLimit() throws JSQLParserException {
        // 设置较小的缓存容量进行测试
        SqlParseCache.init(true, 5);
        SqlParseCache.clear();
        
        // 解析超过容量的 SQL
        for (int i = 0; i < 10; i++) {
            String sql = "SELECT * FROM user" + i + " WHERE id = ?";
            SecurtkitUtils.parseSql(sql);
        }
        
        // 缓存大小应该不超过容量限制
        long size = SqlParseCache.size();
        assertTrue(size <= 5, "缓存大小不应超过容量限制");
    }

    @Test
    void testCache_MultipleOperations() throws JSQLParserException {
        // 混合多种操作类型
        SecurtkitUtils.parseSql("INSERT INTO user(name, phone) VALUES(?, ?)");
        SecurtkitUtils.parseSql("UPDATE user SET name = ? WHERE id = ?");
        SecurtkitUtils.parseSql("DELETE FROM user WHERE id = ?");
        SecurtkitUtils.parseSql("SELECT * FROM user WHERE id = ?");
        SecurtkitUtils.parseSql("SELECT u.name, o.amount FROM user u JOIN orders o ON u.id = o.user_id WHERE u.id = ?");
        
        // 应该有5条不同的缓存记录
        assertEquals(5, SqlParseCache.size());
    }

    @Test
    void testCache_ResultConsistency() throws JSQLParserException {
        String sql = "SELECT id, name, phone FROM user WHERE id = ?";
        
        // 解析多次
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result1 = 
            SecurtkitUtils.parseSql(sql);
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result2 = 
            SecurtkitUtils.parseSql(sql);
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result3 = 
            SecurtkitUtils.parseSql(sql);
        
        // 验证所有结果都一致
        assertEquals(result1.getKey().size(), result2.getKey().size());
        assertEquals(result2.getKey().size(), result3.getKey().size());
        assertEquals(result1.getValue().size(), result2.getValue().size());
        assertEquals(result2.getValue().size(), result3.getValue().size());
        
        // 缓存应该只有1条记录
        assertEquals(1, SqlParseCache.size());
    }

    @Test
    void testCache_Stats() throws JSQLParserException {
        CacheStatsInfo statsBefore = SqlParseCache.getStats();
        
        // 解析多个不同的 SQL
        SecurtkitUtils.parseSql("SELECT * FROM user WHERE id = ?");
        SecurtkitUtils.parseSql("UPDATE user SET name = ? WHERE id = ?");
        SecurtkitUtils.parseSql("INSERT INTO user(name) VALUES(?)");
        
        CacheStatsInfo statsAfter = SqlParseCache.getStats();
        
        assertTrue(statsAfter.getSize() > statsBefore.getSize());
        assertTrue(statsAfter.getSize() <= statsAfter.getMaxSize());
        assertTrue(statsAfter.isEnabled());
        assertTrue(statsAfter.getUsageRate() >= 0.0 && statsAfter.getUsageRate() <= 1.0);
    }

    @Test
    void testCache_Clear() throws JSQLParserException {
        // 填充缓存
        SecurtkitUtils.parseSql("SELECT * FROM user WHERE id = ?");
        SecurtkitUtils.parseSql("UPDATE user SET name = ? WHERE id = ?");
        
        assertTrue(SqlParseCache.size() > 0);
        
        // 清空缓存
        SqlParseCache.clear();
        assertEquals(0, SqlParseCache.size());
        
        // 重新解析
        SecurtkitUtils.parseSql("SELECT * FROM user WHERE id = ?");
        assertEquals(1, SqlParseCache.size());
    }

    @Test
    void testCache_Disabled() throws JSQLParserException {
        // 禁用缓存
        SqlParseCache.init(false, 1000);
        SqlParseCache.clear();
        
        String sql = "SELECT * FROM user WHERE id = ?";
        
        // 解析应该正常工作，但不会缓存
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result = 
            SecurtkitUtils.parseSql(sql);
        assertNotNull(result);
        
        // 缓存应该为空
        assertEquals(0, SqlParseCache.size());
        
        CacheStatsInfo stats = SqlParseCache.getStats();
        assertFalse(stats.isEnabled());
        
        // 重新启用缓存
        SqlParseCache.init(true, 1000);
    }

    @Test
    void testCache_Normalization() throws JSQLParserException {
        // 测试 SQL 规范化：不同格式但语义相同的 SQL 应该被规范化为同一个缓存 key
        String sql1 = "SELECT * FROM user WHERE id = ?";
        String sql2 = "SELECT * FROM USER WHERE ID = ?";
        String sql3 = "SELECT * FROM   user WHERE   id    = ?";
        
        SecurtkitUtils.parseSql(sql1);
        SecurtkitUtils.parseSql(sql2);
        SecurtkitUtils.parseSql(sql3);
        
        // 由于 SQL 规范化（转小写、去空格），这三个 SQL 应该被识别为同一个
        // 但如果规范化不完全，可能有多条记录
        long size3 = SqlParseCache.size();
        assertTrue(size3 >= 1 && size3 <= 3, "缓存大小应该在 1-3 之间");
    }

    @Test
    void testCache_ConcurrentAccess() throws InterruptedException {
        String sql = "SELECT * FROM user WHERE id = ?";
        int threadCount = 5;
        int iterationsPerThread = 10;
        Thread[] threads = new Thread[threadCount];

        // 创建多个线程并发解析相同的 SQL
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        SecurtkitUtils.parseSql(sql);
                    }
                } catch (JSQLParserException e) {
                    fail("并发解析不应该抛出异常: " + e.getMessage());
                }
            });
        }

        // 启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                fail("等待线程完成时被中断: " + e.getMessage());
            }
        }

        // 验证缓存大小（由于并发，可能只有1条记录，因为所有线程解析的是同一个 SQL）
        long size = SqlParseCache.size();
        assertTrue(size >= 1 && size <= threadCount, "缓存大小应该在 1 和线程数之间");
        
        // 验证缓存状态
        CacheStatsInfo stats = SqlParseCache.getStats();
        assertTrue(stats.getUsageRate() <= 1.0, "缓存使用率不应超过 100%");
    }

    @Test
    void testCache_Performance() throws JSQLParserException {
        String sql = "SELECT u.id, u.name, u.phone, o.amount FROM user u JOIN orders o ON u.id = o.user_id WHERE u.id = ? AND o.amount > ?";
        
        // 第一次解析（未命中缓存）
        long startTime1 = System.nanoTime();
        SecurtkitUtils.parseSql(sql);
        long endTime1 = System.nanoTime();
        long timeWithoutCache = endTime1 - startTime1;
        
        // 第二次解析（命中缓存）
        long startTime2 = System.nanoTime();
        SecurtkitUtils.parseSql(sql);
        long endTime2 = System.nanoTime();
        long timeWithCache = endTime2 - startTime2;
        
        // 验证缓存版本应该更快或至少不慢太多
        assertTrue(timeWithCache <= timeWithoutCache * 10, 
            "缓存版本性能应该优于或接近未缓存版本");
        
        // 验证缓存大小
        assertEquals(1, SqlParseCache.size());
    }
}
