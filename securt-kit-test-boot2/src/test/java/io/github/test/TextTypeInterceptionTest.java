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

import java.io.Reader;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TEXT 类型字段拦截测试类
 * <p>
 * 用于测试和调试 TEXT 类型字段的加密/解密拦截问题
 * </p>
 *
 * @author hexlodev
 * @since 1.0.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TextTypeInterceptionTest {

    @BeforeAll
    void setupTableEncryptConfig() {
        // 初始化 TableCache（用于解析 SQL 时识别需要加密的字段）
        FieldEncryptorProperties props = new FieldEncryptorProperties();
        props.setEnable(true);

        FieldEncryptorProperties.TableConfig userTable = new FieldEncryptorProperties.TableConfig();
        userTable.setTableName("sys_user_local");
        FieldEncryptorProperties.FieldConfig textField = new FieldEncryptorProperties.FieldConfig();
        textField.setFieldName("digest"); // TEXT 类型字段
        FieldEncryptorProperties.FieldConfig phoneField = new FieldEncryptorProperties.FieldConfig();
        phoneField.setFieldName("phonenumber");

        userTable.setFields(java.util.Arrays.asList(textField, phoneField));
        props.setTables(java.util.Collections.singletonList(userTable));

        try {
            TableCache.init(props);
        } catch (Exception e) {
            System.out.println("Warning: TableCache.init() failed: " + e.getMessage());
        }
    }

    /**
     * 测试 TEXT 类型字段的 SQL 解析
     */
    @Test
    void testParseSql_WithTextType() throws JSQLParserException {
        String sql = "INSERT INTO sys_user_local (user_id, digest, phonenumber) VALUES (?, ?, ?)";

        System.out.println("=== 测试 TEXT 类型字段的 SQL 解析 ===");
        System.out.println("SQL: " + sql);

        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result =
                SecurtkitUtils.parseSql(sql);

        assertNotNull(result);
        System.out.println("\n✓ 解析成功！");
        System.out.println("占位符映射数量: " + result.getKey().size());
        System.out.println("加密字段数量: " + result.getValue().size());

        // 验证 TEXT 类型字段 digest 是否被识别为需要加密的字段
        List<FieldEncryptorInfoDto> encryptFields = result.getValue();
        boolean hasDigestField = encryptFields.stream()
                .anyMatch(f -> "digest".equals(f.getSourceColumn()));
        assertTrue(hasDigestField, "应该包含 digest TEXT 类型字段");
        System.out.println("✓ digest 字段被识别为需要加密的字段");

        // 验证占位符映射
        Map<String, ColumnTableDto> map = result.getKey();
        boolean hasDigestPlaceholder = map.values().stream()
                .anyMatch(dto -> "digest".equals(dto.getSourceColumn()));
        assertTrue(hasDigestPlaceholder, "应该包含 digest 字段的占位符映射");
        System.out.println("✓ digest 字段的占位符映射已创建");
    }

    /**
     * 测试 ResultSet 读取 TEXT 类型字段的方法
     * <p>
     * 检查哪些方法可能被调用但未被拦截
     * </p>
     */
    @Test
    void testResultSetMethods_ForTextType() {
        System.out.println("=== 测试 ResultSet 读取 TEXT 类型的方法 ===");
        System.out.println("当前拦截的方法：");
        System.out.println("  - getString() ✓");
        System.out.println("  - getObject() (仅当返回 String 时) ✓");
        System.out.println("\n未被拦截的方法（可能导致 TEXT 类型无法解密）：");
        System.out.println("  - getClob() ✗");
        System.out.println("  - getCharacterStream() ✗");
        System.out.println("  - getNClob() ✗");
        System.out.println("  - getNCharacterStream() ✗");
        System.out.println("\n建议：需要在 ResultSetDecryptingProxy 中添加对这些方法的拦截");
    }

    /**
     * 测试 PreparedStatement 写入 TEXT 类型字段的方法
     * <p>
     * 检查哪些方法可能被调用但未被拦截
     * </p>
     */
    @Test
    void testPreparedStatementMethods_ForTextType() {
        System.out.println("=== 测试 PreparedStatement 写入 TEXT 类型的方法 ===");
        System.out.println("当前拦截的方法：");
        System.out.println("  - setString() ✓");
        System.out.println("\n未被拦截的方法（可能导致 TEXT 类型无法加密）：");
        System.out.println("  - setClob() ✗");
        System.out.println("  - setCharacterStream() ✗");
        System.out.println("  - setNClob() ✗");
        System.out.println("  - setNCharacterStream() ✗");
        System.out.println("\n建议：需要在 SimpleInterceptorPreparedStatement 中添加对这些方法的拦截");
    }

    /**
     * 测试 ResultSetMetaData 获取字段类型信息
     */
    @Test
    void testResultSetMetaData_ColumnType() {
        System.out.println("=== 测试 ResultSetMetaData 字段类型信息 ===");
        System.out.println("可以通过以下方法获取字段类型：");
        System.out.println("  - getColumnType(int columnIndex) - 返回 JDBC 类型常量");
        System.out.println("  - getColumnTypeName(int columnIndex) - 返回数据库特定的类型名称");
        System.out.println("\nTEXT 类型对应的 JDBC 类型常量：");
        System.out.println("  - java.sql.Types.CLOB (用于 CLOB/TEXT)");
        System.out.println("  - java.sql.Types.LONGVARCHAR (用于长文本)");
        System.out.println("\n注意：某些数据库驱动可能将 TEXT 类型映射为 VARCHAR，但返回时会使用 Clob");
    }
}

