package io.github.test;

import cn.hutool.core.lang.Pair;
import io.github.genkidoudou.core.TableCache;
import io.github.genkidoudou.core.config.FieldEncryptorProperties;
import io.github.genkidoudou.core.parser.SecurtkitUtils;
import io.github.genkidoudou.core.parser.dto.ColumnTableDto;
import io.github.genkidoudou.core.parser.dto.FieldEncryptorInfoDto;
import io.github.genkidoudou.shaded.jsqlparser.JSQLParserException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SQL 解析错误测试类
 * <p>
 * 用于测试和调试 SQL 解析过程中遇到的错误
 * </p>
 *
 * @author hexlodev
 * @since 1.0.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SqlParseErrorTest {

    @BeforeAll
    void setupTableEncryptConfig() {
        // 初始化 TableCache（用于解析 SQL 时识别需要加密的字段）
        FieldEncryptorProperties props = new FieldEncryptorProperties();
        props.setEnable(true);

        FieldEncryptorProperties.TableConfig userTable = new FieldEncryptorProperties.TableConfig();
        userTable.setTableName("sys_user_local");
        FieldEncryptorProperties.FieldConfig phoneField = new FieldEncryptorProperties.FieldConfig();
        phoneField.setFieldName("phonenumber");
        FieldEncryptorProperties.FieldConfig passwordField = new FieldEncryptorProperties.FieldConfig();
        passwordField.setFieldName("password");

        userTable.setFields(java.util.Arrays.asList(phoneField, passwordField));
        props.setTables(java.util.Collections.singletonList(userTable));

        try {
            TableCache.init(props);
        } catch (Exception e) {
            System.out.println("Warning: TableCache.init() failed: " + e.getMessage());
        }
    }

    /**
     * 测试原始的多行 INSERT SQL 语句（会报错的版本）
     */
    @Test
    void testInsert_MultiLineFormat_Original() {
        String sql = "INSERT INTO sys_user_local (user_id,\n" +
                "                            dept_id,\n" +
                "                            user_name,\n" +
                "                            nick_name,\n" +
                "                            user_type,\n" +
                "                            phonenumber,\n" +
                "                            password,\n" +
                "                            status,\n" +
                "                            del_flag,\n" +
                "                            is_head,\n" +
                "                            annual_leave_totalDays,\n" +
                "                            annual_leave_days,\n" +
                "                            personnel_type,\n" +
                "                            work_time,\n" +
                "                            digest,\n" +
                "                            create_by,\n" +
                "                            create_time,\n" +
                "                            update_by,\n" +
                "                            update_time)\n" +
                "VALUES (?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?)";

        System.out.println("=== 测试原始多行格式 SQL ===");
        System.out.println("SQL:\n" + sql);
        System.out.println("\n--- 替换占位符后的 SQL ---");
        String replacedSql = SecurtkitUtils.question2Placeholder(sql);
        System.out.println(replacedSql);

        try {
            Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result =
                    SecurtkitUtils.parseSql(sql);
            System.out.println("\n✓ 解析成功！");
            System.out.println("占位符映射数量: " + result.getKey().size());
            System.out.println("加密字段数量: " + result.getValue().size());
        } catch (JSQLParserException e) {
            System.out.println("\n✗ 解析失败！");
            System.out.println("错误信息: " + e.getMessage());
            e.printStackTrace();
            fail("SQL 解析失败: " + e.getMessage());
        }
    }

    /**
     * 测试单行格式的 INSERT SQL 语句（应该能成功）
     */
    @Test
    void testInsert_SingleLineFormat() throws JSQLParserException {
        String sql = "INSERT INTO sys_user_local (user_id, dept_id, user_name, nick_name, user_type, " +
                "phonenumber, password, status, del_flag, is_head, annual_leave_totalDays, " +
                "annual_leave_days, personnel_type, work_time, digest, create_by, create_time, " +
                "update_by, update_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        System.out.println("=== 测试单行格式 SQL ===");
        System.out.println("SQL: " + sql);
        System.out.println("\n--- 替换占位符后的 SQL ---");
        String replacedSql = SecurtkitUtils.question2Placeholder(sql);
        System.out.println(replacedSql);

        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result =
                SecurtkitUtils.parseSql(sql);

        assertNotNull(result);
        System.out.println("\n✓ 解析成功！");
        System.out.println("占位符映射数量: " + result.getKey().size());
        System.out.println("加密字段数量: " + result.getValue().size());
        assertEquals(19, result.getKey().size(), "应该有19个占位符映射");
    }

    /**
     * 测试压缩后的多行格式（去除多余空格）
     */
    @Test
    void testInsert_MultiLineFormat_Compressed() {
        String sql = "INSERT INTO sys_user_local (user_id,\n" +
                "dept_id,\n" +
                "user_name,\n" +
                "nick_name,\n" +
                "user_type,\n" +
                "phonenumber,\n" +
                "password,\n" +
                "status,\n" +
                "del_flag,\n" +
                "is_head,\n" +
                "annual_leave_totalDays,\n" +
                "annual_leave_days,\n" +
                "personnel_type,\n" +
                "work_time,\n" +
                "digest,\n" +
                "create_by,\n" +
                "create_time,\n" +
                "update_by,\n" +
                "update_time)\n" +
                "VALUES (?,\n" +
                "?,\n" +
                "?,\n" +
                "?,\n" +
                "?,\n" +
                "?,\n" +
                "?,\n" +
                "?,\n" +
                "?,\n" +
                "?,\n" +
                "?,\n" +
                "?,\n" +
                "?,\n" +
                "?,\n" +
                "?,\n" +
                "?,\n" +
                "?,\n" +
                "?,\n" +
                "?)";

        System.out.println("=== 测试压缩后的多行格式 SQL ===");
        System.out.println("SQL:\n" + sql);
        System.out.println("\n--- 替换占位符后的 SQL ---");
        String replacedSql = SecurtkitUtils.question2Placeholder(sql);
        System.out.println(replacedSql);

        try {
            Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result =
                    SecurtkitUtils.parseSql(sql);
            System.out.println("\n✓ 解析成功！");
            System.out.println("占位符映射数量: " + result.getKey().size());
            System.out.println("加密字段数量: " + result.getValue().size());
        } catch (JSQLParserException e) {
            System.out.println("\n✗ 解析失败！");
            System.out.println("错误信息: " + e.getMessage());
            e.printStackTrace();
            fail("SQL 解析失败: " + e.getMessage());
        }
    }

    /**
     * 测试占位符替换后的 SQL（用于调试）
     */
    @Test
    void testPlaceholderReplacement() {
        String sql = "INSERT INTO sys_user_local (user_id,\n" +
                "                            dept_id,\n" +
                "                            user_name,\n" +
                "                            nick_name,\n" +
                "                            user_type,\n" +
                "                            phonenumber,\n" +
                "                            password,\n" +
                "                            status,\n" +
                "                            del_flag,\n" +
                "                            is_head,\n" +
                "                            annual_leave_totalDays,\n" +
                "                            annual_leave_days,\n" +
                "                            personnel_type,\n" +
                "                            work_time,\n" +
                "                            digest,\n" +
                "                            create_by,\n" +
                "                            create_time,\n" +
                "                            update_by,\n" +
                "                            update_time)\n" +
                "VALUES (?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?,\n" +
                "        ?)";

        System.out.println("=== 测试占位符替换过程 ===");
        System.out.println("原始 SQL:\n" + sql);
        
        String replacedSql = SecurtkitUtils.question2Placeholder(sql);
        System.out.println("\n替换占位符后的 SQL:\n" + replacedSql);
        
        // 检查替换后的 SQL 格式
        System.out.println("\n--- 分析替换后的 SQL ---");
        String[] lines = replacedSql.split("\n");
        for (int i = 0; i < lines.length; i++) {
            System.out.println("行 " + (i + 1) + ": " + lines[i]);
        }
    }

    /**
     * 测试规范化后的 SQL（去除多余空格和换行）
     */
    @Test
    void testInsert_NormalizedFormat() throws JSQLParserException {
        // 将多行 SQL 规范化为单行
        String sql = "INSERT INTO sys_user_local (user_id, dept_id, user_name, nick_name, user_type, " +
                "phonenumber, password, status, del_flag, is_head, annual_leave_totalDays, " +
                "annual_leave_days, personnel_type, work_time, digest, create_by, create_time, " +
                "update_by, update_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        System.out.println("=== 测试规范化后的 SQL ===");
        System.out.println("SQL: " + sql);

        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result =
                SecurtkitUtils.parseSql(sql);

        assertNotNull(result);
        System.out.println("\n✓ 解析成功！");
        System.out.println("占位符映射数量: " + result.getKey().size());
        System.out.println("加密字段数量: " + result.getValue().size());
        
        // 验证加密字段
        List<FieldEncryptorInfoDto> encryptFields = result.getValue();
        assertTrue(encryptFields.stream().anyMatch(f -> "phonenumber".equals(f.getSourceColumn())),
                "应该包含 phonenumber 加密字段");
        assertTrue(encryptFields.stream().anyMatch(f -> "password".equals(f.getSourceColumn())),
                "应该包含 password 加密字段");
    }

    /**
     * 测试字段名包含下划线的情况
     */
    @Test
    void testInsert_WithUnderscoreFields() throws JSQLParserException {
        String sql = "INSERT INTO sys_user_local (user_id, dept_id, user_name, nick_name, user_type, " +
                "phonenumber, password, status, del_flag, is_head, annual_leave_totalDays, " +
                "annual_leave_days, personnel_type, work_time, digest, create_by, create_time, " +
                "update_by, update_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result =
                SecurtkitUtils.parseSql(sql);

        assertNotNull(result);
        assertEquals(19, result.getKey().size(), "应该有19个占位符");
        
        // 验证字段名映射
        Map<String, ColumnTableDto> map = result.getKey();
        boolean hasAnnualLeaveTotalDays = map.values().stream()
                .anyMatch(dto -> "annual_leave_totalDays".equals(dto.getSourceColumn()));
        assertTrue(hasAnnualLeaveTotalDays, "应该包含 annual_leave_totalDays 字段");
    }
}

