package io.github.test;

import io.github.hexlodev.core.utils.TableNameParser;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 TableNameParser 解析带空格的点号分隔表名（如 database. table）
 */
class TableNameParserWithSpaceTest {

    @Test
    void testParseTableNameWithSpaceAfterDot() {
        // 测试点号后有空格的情况：business_platform_sy. sys_user
        String sql = "SELECT * FROM business_platform_sy. sys_user WHERE id = 1";
        TableNameParser parser = new TableNameParser(sql);
        Set<String> tables = parser.tables();
        
        assertNotNull(tables);
        assertFalse(tables.isEmpty());
        // 应该解析出完整的表名 business_platform_sy.sys_user（去掉空格）
        assertTrue(tables.contains("business_platform_sy.sys_user"), 
                   "Expected business_platform_sy.sys_user, got: " + tables);
    }

    @Test
    void testParseMultipleTablesWithSpaceAfterDot() {
        // 测试多个表名都有点号后有空格的情况
        String sql = "SELECT * FROM business_platform_sy. sys_user u " +
                     "LEFT JOIN business_platform_sy. sys_dept d ON u.dept_id = d.dept_id " +
                     "LEFT JOIN business_platform_sy. sys_user_role ur ON u.user_id = ur.user_id " +
                     "LEFT JOIN business_platform_sy. sys_role r ON r.role_id = ur.role_id";
        TableNameParser parser = new TableNameParser(sql);
        Set<String> tables = parser.tables();
        
        assertNotNull(tables);
        assertFalse(tables.isEmpty());
        assertTrue(tables.size() >= 4, "Expected at least 4 tables, got: " + tables.size());
        
        // 验证所有表名都被正确解析
        assertTrue(tables.stream().anyMatch(t -> t.contains("sys_user") && t.contains("business_platform_sy")),
                   "Should contain sys_user table");
        assertTrue(tables.stream().anyMatch(t -> t.contains("sys_dept") && t.contains("business_platform_sy")),
                   "Should contain sys_dept table");
        assertTrue(tables.stream().anyMatch(t -> t.contains("sys_user_role") && t.contains("business_platform_sy")),
                   "Should contain sys_user_role table");
        assertTrue(tables.stream().anyMatch(t -> t.contains("sys_role") && t.contains("business_platform_sy")),
                   "Should contain sys_role table");
    }

    @Test
    void testParseComplexSqlWithSpaceAfterDot() {
        // 测试用户提供的完整 SQL
        String sql = "SELECT u.user_id, u.dept_id, u.user_name, u.nick_name, u.email, " +
                     "u.avatar_id, u.phonenumber, u.sex, u.status, u.del_flag, " +
                     "u.login_ip, u.login_date, u.create_by, u.create_time, u.remark, " +
                     "u.user_type, u.source, u.real_name, " +
                     "d.dept_id, d.parent_id, d.ancestors, d.dept_name, d.order_num, " +
                     "d.leader, d.status as dept_status, d.area_code, " +
                     "r.role_id, r.role_name, r.role_key, r.role_sort, r.data_scope, r.status as role_status " +
                     "FROM business_platform_sy. sys_user u " +
                     "LEFT JOIN business_platform_sy. sys_dept d ON u.dept_id = d.dept_id " +
                     "LEFT JOIN business_platform_sy. sys_user_role ur ON u.user_id = ur.user_id " +
                     "LEFT JOIN business_platform_sy. sys_role r ON r.role_id = ur.role_id " +
                     "WHERE u.user_id = 1985684407922077696";
        
        TableNameParser parser = new TableNameParser(sql);
        Set<String> tables = parser.tables();
        
        assertNotNull(tables);
        assertFalse(tables.isEmpty());
        assertTrue(tables.size() >= 4, "Expected at least 4 tables, got: " + tables.size());
        
        // 验证所有表名都被正确解析
        assertTrue(tables.stream().anyMatch(t -> t.contains("sys_user")),
                   "Should contain sys_user table. Got: " + tables);
        assertTrue(tables.stream().anyMatch(t -> t.contains("sys_dept")),
                   "Should contain sys_dept table. Got: " + tables);
        assertTrue(tables.stream().anyMatch(t -> t.contains("sys_user_role")),
                   "Should contain sys_user_role table. Got: " + tables);
        assertTrue(tables.stream().anyMatch(t -> t.contains("sys_role")),
                   "Should contain sys_role table. Got: " + tables);
    }

    @Test
    void testParseTableNameWithoutSpace() {
        // 测试正常情况：点号后没有空格
        String sql = "SELECT * FROM business_platform_sy.sys_user WHERE id = 1";
        TableNameParser parser = new TableNameParser(sql);
        Set<String> tables = parser.tables();
        
        assertNotNull(tables);
        assertFalse(tables.isEmpty());
        assertTrue(tables.contains("business_platform_sy.sys_user"), 
                   "Expected business_platform_sy.sys_user, got: " + tables);
    }

    @Test
    void testParseTableNameWithoutDatabase() {
        // 测试没有数据库名的情况
        String sql = "SELECT * FROM sys_user WHERE id = 1";
        TableNameParser parser = new TableNameParser(sql);
        Set<String> tables = parser.tables();
        
        assertNotNull(tables);
        assertFalse(tables.isEmpty());
        assertTrue(tables.contains("sys_user"), "Expected sys_user, got: " + tables);
    }
}

