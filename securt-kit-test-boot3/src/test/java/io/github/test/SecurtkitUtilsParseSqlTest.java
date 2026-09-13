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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spring Boot test for SecurtkitUtils.parseSql
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SecurtkitUtilsParseSqlTest {

    @BeforeAll
    void setupTableEncryptConfig() {
        // Minimal encryption config to initialize TableCache (used by visitor)
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

        TableCache.init(props);
    }

    @Test
    void testParseSql_UpdateWithPlaceholders() throws JSQLParserException {
        String sql = "UPDATE user SET name = ?, age = ?, phone = ? WHERE id = ?";

        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result = SecurtkitUtils.parseSql(sql);
        assertNotNull(result, "parseSql should return non-null pair");

        Map<String, ColumnTableDto> map = result.getKey();
        List<FieldEncryptorInfoDto> infos = result.getValue();

        assertNotNull(map, "placeholder to column mapping should not be null");
        assertNotNull(infos, "field encryptor infos should not be null");

        boolean hasAnyPlaceholder = map.keySet().stream().anyMatch(k -> k.startsWith(SecurtkitUtils.PLACEHOLDER));
        assertTrue(hasAnyPlaceholder, "should contain placeholder keys starting with " + SecurtkitUtils.PLACEHOLDER);
    }

    @Test
    void testParseSql_SelectWithWherePlaceholders() throws JSQLParserException {
        String sql = "SELECT u.id, u.name FROM user u WHERE u.phone = ? AND u.name = ?";

        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> result = SecurtkitUtils.parseSql(sql);
        assertNotNull(result);
        Map<String, ColumnTableDto> map = result.getKey();
        assertNotNull(map);

        Collection<String> keys = map.keySet();
        assertFalse(keys.isEmpty(), "mapping keys should not be empty for select placeholders");
        assertTrue(keys.stream().anyMatch(k -> k.startsWith(SecurtkitUtils.PLACEHOLDER)),
                "keys should include SECURT_KIT_PLACEHOLDER_* entries");
    }

    @Test
    void testQuestionToPlaceholderUtility() {
        String sql = "INSERT INTO user(name, phone) VALUES(?, ?)";
        String replaced = SecurtkitUtils.question2Placeholder(sql);
        assertTrue(replaced.contains(SecurtkitUtils.PLACEHOLDER + "0"));
        assertTrue(replaced.contains(SecurtkitUtils.PLACEHOLDER + "1"));
    }
}
