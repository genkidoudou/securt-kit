package io.github.genkidoudou.core.parser;

import cn.hutool.core.lang.Pair;
import io.github.genkidoudou.core.TableCache;
import io.github.genkidoudou.core.config.FieldEncryptorProperties;
import io.github.genkidoudou.core.parser.dto.ColumnTableDto;
import io.github.genkidoudou.core.parser.dto.FieldEncryptorInfoDto;
import io.github.genkidoudou.core.strategy.FieldEncryptorStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateLiteralEncryptFieldParseTest {

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
        table.setFields(Collections.singletonList(phone));
        properties.setTables(Arrays.asList(table));

        TableCache.init(properties);
    }

    @Test
    void updateLiteralReportsConfiguredSetField() throws Exception {
        assertNotNull(TableCache.getTableFieldEncryptStrategy("user", "phone", "default"),
                "测试前置条件：phone 应已配置加密策略");

        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result =
                SecurtkitUtils.parseSql(
                        "UPDATE user SET PHONE = '18111111111' WHERE id = 1",
                        "default");

        assertTrue(result.getValue().stream().anyMatch(field ->
                        "user".equalsIgnoreCase(field.getSourceTableName())
                                && "phone".equalsIgnoreCase(field.getSourceColumn())
                                && field.getFieldEncryptor() != null),
                "UPDATE 字面量赋值应识别已配置的 phone 加密字段，实际结果: " + result.getValue());
    }

    public static class TestStrategy implements FieldEncryptorStrategy {
        @Override
        public String encryption(String oldValue) {
            return oldValue;
        }

        @Override
        public String decryption(String oldValue) {
            return oldValue;
        }
    }
}
