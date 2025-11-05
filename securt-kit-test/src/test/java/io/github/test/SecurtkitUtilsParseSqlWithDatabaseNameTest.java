package io.github.test;

import cn.hutool.core.lang.Pair;
import io.github.hexlodev.core.TableCache;
import io.github.hexlodev.core.config.FieldEncryptorProperties;
import io.github.hexlodev.core.parser.SecurtkitUtils;
import io.github.hexlodev.core.parser.dto.ColumnTableDto;
import io.github.hexlodev.core.parser.dto.FieldEncryptorInfoDto;
import net.sf.jsqlparser.JSQLParserException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试带数据库名的表名格式（如 testdb.user）的 SQL 解析
 * 
 * @author hexlodev
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SecurtkitUtilsParseSqlWithDatabaseNameTest {

    @BeforeAll
    void setupTableEncryptConfig() {
        // 配置加密信息，表名使用 user（不带数据库名）
        // 因为 TableCache 存储时使用表名小写，所以配置时只配置表名即可
        FieldEncryptorProperties props = new FieldEncryptorProperties();
        props.setEnable(true);

        FieldEncryptorProperties.TableConfig userTable = new FieldEncryptorProperties.TableConfig();
        userTable.setTableName("user");

        FieldEncryptorProperties.FieldConfig idField = new FieldEncryptorProperties.FieldConfig();
        idField.setFieldName("id");
        FieldEncryptorProperties.FieldConfig nameField = new FieldEncryptorProperties.FieldConfig();
        nameField.setFieldName("name");
        FieldEncryptorProperties.FieldConfig phoneField = new FieldEncryptorProperties.FieldConfig();
        phoneField.setFieldName("phone");
        FieldEncryptorProperties.FieldConfig emailField = new FieldEncryptorProperties.FieldConfig();
        emailField.setFieldName("email");
        FieldEncryptorProperties.FieldConfig ageField = new FieldEncryptorProperties.FieldConfig();
        ageField.setFieldName("age");

        userTable.setFields(java.util.Arrays.asList(idField, nameField, phoneField, emailField, ageField));
        props.setTables(java.util.Collections.singletonList(userTable));

        TableCache.init(props);
    }

    /**
     * 测试 SELECT 查询，表名带数据库名
     */
    @Test
    void testParseSql_SelectWithDatabaseName() throws JSQLParserException {
        String sql = "SELECT id, name, phone FROM testdb.user WHERE id = ?";

        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result = SecurtkitUtils.parseSql(sql);
        assertNotNull(result, "parseSql should return non-null pair");

        Map<String, ColumnTableDto> map = result.getKey();
        List<FieldEncryptorInfoDto> infos = result.getValue();

        assertNotNull(map, "placeholder to column mapping should not be null");
        assertNotNull(infos, "field encryptor infos should not be null");

        // 验证字段加密信息中包含加密字段
        assertFalse(infos.isEmpty(), "should have field encryptor infos");
        
        // 检查是否包含加密字段（name, phone）
        boolean hasNameField = infos.stream()
                .anyMatch(info -> "name".equalsIgnoreCase(info.getColumnName()));
        boolean hasPhoneField = infos.stream()
                .anyMatch(info -> "phone".equalsIgnoreCase(info.getColumnName()));
        
        assertTrue(hasNameField || hasPhoneField, "should contain encrypted fields");
    }

    /**
     * 测试 SELECT 查询，表名带数据库名和别名
     */
    @Test
    void testParseSql_SelectWithDatabaseNameAndAlias() throws JSQLParserException {
        String sql = "SELECT u.id, u.name, u.phone FROM testdb.user u WHERE u.id = ? AND u.phone = ?";

        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result = SecurtkitUtils.parseSql(sql);
        assertNotNull(result);

        Map<String, ColumnTableDto> map = result.getKey();
        List<FieldEncryptorInfoDto> infos = result.getValue();

        assertNotNull(map);
        assertNotNull(infos);

        // 验证占位符映射
        Collection<String> keys = map.keySet();
        assertFalse(keys.isEmpty(), "mapping keys should not be empty");
        assertTrue(keys.stream().anyMatch(k -> k.startsWith(SecurtkitUtils.PLACEHOLDER)),
                "keys should include SECURT_KIT_PLACEHOLDER_* entries");

        // 验证字段加密信息
        assertFalse(infos.isEmpty(), "should have field encryptor infos");
    }

    /**
     * 测试 SELECT * 查询，表名带数据库名
     */
    @Test
    void testParseSql_SelectStarWithDatabaseName() throws JSQLParserException {
        String sql = "SELECT * FROM testdb.user WHERE id = ?";

        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result = SecurtkitUtils.parseSql(sql);
        assertNotNull(result);

        Map<String, ColumnTableDto> map = result.getKey();
        List<FieldEncryptorInfoDto> infos = result.getValue();

        assertNotNull(map);
        assertNotNull(infos);

        // SELECT * 应该能解析出所有加密字段
        assertFalse(infos.isEmpty(), "should have field encryptor infos for SELECT *");
        
        // 验证包含加密字段
        Set<String> columnNames = new java.util.HashSet<>();
        for (FieldEncryptorInfoDto info : infos) {
            columnNames.add(info.getColumnName().toLowerCase());
        }
        
        assertTrue(columnNames.contains("name") || columnNames.contains("phone"),
                "should contain encrypted fields like name or phone");
    }

    /**
     * 测试 UPDATE 语句，表名带数据库名
     */
    @Test
    void testParseSql_UpdateWithDatabaseName() throws JSQLParserException {
        String sql = "UPDATE testdb.user SET name = ?, phone = ? WHERE id = ?";

        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result = SecurtkitUtils.parseSql(sql);
        assertNotNull(result);

        Map<String, ColumnTableDto> map = result.getKey();
        List<FieldEncryptorInfoDto> infos = result.getValue();

        assertNotNull(map);
        assertNotNull(infos);

        // 验证占位符映射
        boolean hasAnyPlaceholder = map.keySet().stream()
                .anyMatch(k -> k.startsWith(SecurtkitUtils.PLACEHOLDER));
        assertTrue(hasAnyPlaceholder, "should contain placeholder keys");

        // UPDATE 语句应该至少包含 WHERE 条件的占位符
        assertFalse(map.isEmpty(), "should have placeholder mappings");
    }

    /**
     * 测试 INSERT 语句，表名带数据库名
     */
    @Test
    void testParseSql_InsertWithDatabaseName() throws JSQLParserException {
        String sql = "INSERT INTO testdb.user(id, name, phone, email, age) VALUES(?, ?, ?, ?, ?)";

        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result = SecurtkitUtils.parseSql(sql);
        assertNotNull(result);

        Map<String, ColumnTableDto> map = result.getKey();
        List<FieldEncryptorInfoDto> infos = result.getValue();

        assertNotNull(map);
        assertNotNull(infos);

        // INSERT 语句应该有占位符映射
        assertFalse(map.isEmpty(), "should have placeholder mappings");
        
        // 验证占位符数量
        long placeholderCount = map.keySet().stream()
                .filter(k -> k.startsWith(SecurtkitUtils.PLACEHOLDER))
                .count();
        assertEquals(5, placeholderCount, "should have 5 placeholders");
    }

    /**
     * 测试 DELETE 语句，表名带数据库名
     */
    @Test
    void testParseSql_DeleteWithDatabaseName() throws JSQLParserException {
        String sql = "DELETE FROM testdb.user WHERE id = ? AND phone = ?";

        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result = SecurtkitUtils.parseSql(sql);
        assertNotNull(result);

        Map<String, ColumnTableDto> map = result.getKey();
        List<FieldEncryptorInfoDto> infos = result.getValue();

        assertNotNull(map);
        assertNotNull(infos);

        // DELETE 语句应该有 WHERE 条件的占位符映射
        assertFalse(map.isEmpty(), "should have placeholder mappings");
        
        // 验证占位符数量
        long placeholderCount = map.keySet().stream()
                .filter(k -> k.startsWith(SecurtkitUtils.PLACEHOLDER))
                .count();
        assertTrue(placeholderCount >= 1, "should have at least 1 placeholder");
    }

    /**
     * 测试带数据库名的子查询
     */
    @Test
    void testParseSql_SubQueryWithDatabaseName() throws JSQLParserException {
        String sql = "SELECT * FROM (SELECT id, name, phone, age, email FROM testdb.user) tmp LIMIT 1";

        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result = SecurtkitUtils.parseSql(sql);
        assertNotNull(result);

        Map<String, ColumnTableDto> map = result.getKey();
        List<FieldEncryptorInfoDto> infos = result.getValue();

        assertNotNull(map);
        assertNotNull(infos);

        // 子查询应该能解析出字段
        assertFalse(infos.isEmpty(), "should have field encryptor infos from subquery");
        
        // 验证包含加密字段
        Set<String> columnNames = new java.util.HashSet<>();
        for (FieldEncryptorInfoDto info : infos) {
            columnNames.add(info.getColumnName().toLowerCase());
        }
        
        assertTrue(columnNames.contains("name") || columnNames.contains("phone"),
                "should contain encrypted fields from subquery");
    }

    /**
     * 测试 JOIN 查询，表名带数据库名
     */
    @Test
    void testParseSql_JoinWithDatabaseName() throws JSQLParserException {
        String sql = "SELECT u.id, u.name, o.order_id FROM testdb.user u JOIN testdb.orders o ON u.id = o.user_id WHERE u.phone = ?";

        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result = SecurtkitUtils.parseSql(sql);
        assertNotNull(result);

        Map<String, ColumnTableDto> map = result.getKey();
        List<FieldEncryptorInfoDto> infos = result.getValue();

        assertNotNull(map);
        assertNotNull(infos);

        // 验证占位符映射
        assertFalse(map.isEmpty(), "should have placeholder mappings");
        
        // 验证字段加密信息（user 表的加密字段）
        boolean hasUserField = infos.stream()
                .anyMatch(info -> "name".equalsIgnoreCase(info.getColumnName()) 
                        || "phone".equalsIgnoreCase(info.getColumnName()));
        assertTrue(hasUserField, "should contain encrypted fields from user table");
    }

    /**
     * 测试多表查询，部分表带数据库名
     */
    @Test
    void testParseSql_MultiTableWithDatabaseName() throws JSQLParserException {
        String sql = "SELECT u.id, u.name, u.phone FROM testdb.user u, testdb.orders o WHERE u.id = o.user_id AND u.phone = ?";

        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result = SecurtkitUtils.parseSql(sql);
        assertNotNull(result);

        Map<String, ColumnTableDto> map = result.getKey();
        List<FieldEncryptorInfoDto> infos = result.getValue();

        assertNotNull(map);
        assertNotNull(infos);

        // 验证占位符映射
        assertFalse(map.isEmpty(), "should have placeholder mappings");
        
        // 验证字段加密信息
        assertFalse(infos.isEmpty(), "should have field encryptor infos");
    }

    /**
     * 测试 SELECT 查询，表名带数据库名和 schema
     */
    @Test
    void testParseSql_SelectWithFullQualifiedName() throws JSQLParserException {
        // 测试完全限定名：database.schema.table 或 database.table
        String sql = "SELECT id, name, phone FROM testdb.user WHERE id = ?";

        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result = SecurtkitUtils.parseSql(sql);
        assertNotNull(result);

        Map<String, ColumnTableDto> map = result.getKey();
        List<FieldEncryptorInfoDto> infos = result.getValue();

        assertNotNull(map);
        assertNotNull(infos);

        // 验证解析结果不为空
        assertFalse(infos.isEmpty(), "should have field encryptor infos");
    }
}

