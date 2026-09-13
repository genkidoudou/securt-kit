package io.github.genkidoudou.monitor.ops;

import io.github.genkidoudou.core.config.ConfigInitializer;
import io.github.genkidoudou.core.config.TableConfigRegistry;
import io.github.genkidoudou.core.crypto.FieldCryptoServiceHolder;
import io.github.genkidoudou.core.parser.dto.FieldEncryptorInfoDto;
import io.github.genkidoudou.core.strategy.FieldEncryptorStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MonitorResultDecryptorFunctionProjectionTest {

    @BeforeEach
    void setUp() {
        ConfigInitializer.reset();
        TableConfigRegistry.clear();
        FieldCryptoServiceHolder.reset();
        Map<String, Class<? extends FieldEncryptorStrategy>> fields = new HashMap<>();
        fields.put("phone", PrefixEncryptStrategy.class);
        TableConfigRegistry.registerTable("default", "user", fields);
    }

    @AfterEach
    void tearDown() {
        TableConfigRegistry.clear();
        FieldCryptoServiceHolder.reset();
        ConfigInitializer.reset();
    }

    @Test
    void decryptsIfnullProjectionViaSelectMapping() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("IFNULL(phone,'2')", "ENC:13800138000");

        FieldEncryptorInfoDto mapping = FieldEncryptorInfoDto.builder()
                .columnName("ifnull(phone, '2')")
                .sourceTableName("user")
                .sourceColumn("phone")
                .resultColumnIndex(1)
                .build();

        List<Map<String, Object>> plain = MonitorResultDecryptor.decryptRows(
                "default",
                Collections.singletonList("user"),
                Collections.singletonList(row),
                Collections.singletonList(mapping));

        assertEquals(1, plain.size());
        assertEquals("13800138000", plain.get(0).get("IFNULL(phone,'2')"));
        assertNotEquals(row.get("IFNULL(phone,'2')"), plain.get(0).get("IFNULL(phone,'2')"));
    }

    @Test
    void decryptsAliasedIfnullViaFieldNameFallback() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("phone", "ENC:13900139000");

        List<Map<String, Object>> plain = MonitorResultDecryptor.decryptRows(
                "default",
                Collections.singletonList("user"),
                Collections.singletonList(row));

        assertEquals("13900139000", plain.get(0).get("phone"));
    }
}
