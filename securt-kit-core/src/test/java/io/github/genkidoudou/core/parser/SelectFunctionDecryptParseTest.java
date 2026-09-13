package io.github.genkidoudou.core.parser;

import cn.hutool.core.lang.Pair;
import io.github.genkidoudou.core.TableCache;
import io.github.genkidoudou.core.config.FieldEncryptorProperties;
import io.github.genkidoudou.core.parser.dto.ColumnTableDto;
import io.github.genkidoudou.core.parser.dto.FieldEncryptorInfoDto;
import io.github.genkidoudou.core.parser.visitor.fieldparse.SelectExpressionColumnSupport;
import io.github.genkidoudou.core.strategy.FieldEncryptorStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectFunctionDecryptParseTest {

    @BeforeEach
    void initTableConfig() {
        TableCache.reset();
        FieldEncryptorProperties properties = new FieldEncryptorProperties();
        properties.setEnable(true);

        FieldEncryptorProperties.TableConfig table = new FieldEncryptorProperties.TableConfig();
        table.setTableName("user");

        FieldEncryptorProperties.FieldConfig phone = new FieldEncryptorProperties.FieldConfig();
        phone.setFieldName("phone");
        phone.setStrategy(TestStrategy.class.getName());

        FieldEncryptorProperties.FieldConfig idCard = new FieldEncryptorProperties.FieldConfig();
        idCard.setFieldName("id_card");
        idCard.setStrategy(TestStrategy.class.getName());

        table.setFields(Arrays.asList(phone, idCard));
        properties.setTables(Collections.singletonList(table));
        TableCache.init(properties);
    }

    @Test
    void ifnullWithoutAliasRegistersPhone() throws Exception {
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result =
                SecurtkitUtils.parseSql("SELECT IFNULL(phone,'2') FROM user", "default");

        FieldEncryptorInfoDto field = findPhone(result.getValue());
        assertNotNull(field, "IFNULL(phone) should register user.phone, got: " + result.getValue());
        assertEquals("user", field.getSourceTableName());
        assertEquals("phone", field.getSourceColumn());
        assertEquals(Integer.valueOf(1), field.getResultColumnIndex());
        assertTrue(SelectExpressionColumnSupport.stripWhitespaceLower(field.getColumnName())
                .contains("ifnull"));
    }

    @Test
    void ifnullWithAliasUsesAliasAsColumnName() throws Exception {
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result =
                SecurtkitUtils.parseSql("SELECT IFNULL(phone,'2') AS phone FROM user", "default");

        FieldEncryptorInfoDto field = findPhone(result.getValue());
        assertNotNull(field);
        assertEquals("phone", field.getColumnName());
        assertEquals("phone", field.getSourceColumn());
    }

    @Test
    void concatUsesFirstEncryptedColumn() throws Exception {
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result =
                SecurtkitUtils.parseSql("SELECT CONCAT(phone, id_card) FROM user", "default");

        assertEquals(1, result.getValue().size(), "multi-column expression yields one mapping");
        FieldEncryptorInfoDto field = result.getValue().get(0);
        assertEquals("user", field.getSourceTableName());
        assertEquals("phone", field.getSourceColumn());
    }

    @Test
    void trimRegistersSourceColumn() throws Exception {
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result =
                SecurtkitUtils.parseSql("SELECT TRIM(phone) FROM user", "default");
        FieldEncryptorInfoDto field = findPhone(result.getValue());
        assertNotNull(field, "TRIM(phone) should register, got: " + result.getValue());
        assertEquals("phone", field.getSourceColumn());
    }

    @Test
    void bareColumnStillRegisters() throws Exception {
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result =
                SecurtkitUtils.parseSql("SELECT phone FROM user", "default");
        FieldEncryptorInfoDto field = findPhone(result.getValue());
        assertNotNull(field);
        assertEquals("phone", field.getColumnName());
        assertEquals(Integer.valueOf(1), field.getResultColumnIndex());
    }

    @Test
    void caseWhenRegistersFirstThenColumn() throws Exception {
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result =
                SecurtkitUtils.parseSql(
                        "SELECT CASE WHEN id = 1 THEN phone ELSE id_card END FROM user",
                        "default");
        FieldEncryptorInfoDto field = findPhone(result.getValue());
        assertNotNull(field, "CASE THEN phone should register, got: " + result.getValue());
        assertEquals("phone", field.getSourceColumn());
        assertEquals(Integer.valueOf(1), field.getResultColumnIndex());
    }

    @Test
    void lagOverRegistersPhone() throws Exception {
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result =
                SecurtkitUtils.parseSql(
                        "SELECT LAG(phone) OVER (ORDER BY id) FROM user",
                        "default");
        FieldEncryptorInfoDto field = findPhone(result.getValue());
        assertNotNull(field, "LAG(phone) should register, got: " + result.getValue());
        assertEquals("phone", field.getSourceColumn());
    }

    @Test
    void concatOperatorUsesFirstColumn() throws Exception {
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result =
                SecurtkitUtils.parseSql("SELECT phone || id_card FROM user", "default");
        assertEquals(1, result.getValue().size(), "got: " + result.getValue());
        assertEquals("phone", result.getValue().get(0).getSourceColumn());
    }

    @Test
    void scalarSubqueryRegistersInnerPhone() throws Exception {
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result =
                SecurtkitUtils.parseSql(
                        "SELECT (SELECT phone FROM user WHERE id = 1) AS phone FROM user",
                        "default");
        FieldEncryptorInfoDto field = findPhone(result.getValue());
        assertNotNull(field, "scalar subquery phone should register, got: " + result.getValue());
        assertEquals("phone", field.getColumnName());
        assertEquals("phone", field.getSourceColumn());
    }

    private static FieldEncryptorInfoDto findPhone(List<FieldEncryptorInfoDto> fields) {
        if (fields == null) {
            return null;
        }
        for (FieldEncryptorInfoDto field : fields) {
            if (field != null
                    && "user".equalsIgnoreCase(field.getSourceTableName())
                    && "phone".equalsIgnoreCase(field.getSourceColumn())
                    && field.getFieldEncryptor() != null) {
                return field;
            }
        }
        return null;
    }

    public static class TestStrategy implements FieldEncryptorStrategy {
        @Override
        public String encryption(String oldValue) {
            return "ENC:" + oldValue;
        }

        @Override
        public String decryption(String oldValue) {
            if (oldValue != null && oldValue.startsWith("ENC:")) {
                return oldValue.substring(4);
            }
            return oldValue;
        }
    }
}
