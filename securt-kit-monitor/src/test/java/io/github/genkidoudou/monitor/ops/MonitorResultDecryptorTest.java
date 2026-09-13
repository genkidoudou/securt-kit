package io.github.genkidoudou.monitor.ops;

import io.github.genkidoudou.core.config.ConfigInitializer;
import io.github.genkidoudou.core.config.TableConfigRegistry;
import io.github.genkidoudou.core.crypto.FieldCryptoService;
import io.github.genkidoudou.core.crypto.FieldCryptoServiceHolder;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitorResultDecryptorTest {

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
    void decryptsConfiguredColumnAndLeavesOthers() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1);
        row.put("phone", "ENC:13800138000");
        row.put("age", 28);
        row.put("note", "plain-note");

        Map<String, Object> plain = MonitorResultDecryptor.decryptRow(
                null, Collections.singletonList("user"), row);

        assertEquals("13800138000", plain.get("phone"));
        assertEquals(28, plain.get("age"));
        assertEquals("plain-note", plain.get("note"));
        assertEquals("ENC:13800138000", row.get("phone"));
    }

    @Test
    void unmatchedLabelLeftUnchangedWithoutTable() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("phone", "ENC:13800138000");
        Map<String, Object> plain = MonitorResultDecryptor.decryptRow(
                null, Collections.<String>emptyList(), row);
        assertEquals("ENC:13800138000", plain.get("phone"));
    }

    @Test
    void decryptRowsProducesDistinctPlainFromCipher() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("phone", "ENC:13900139000");
        List<Map<String, Object>> cipher = Collections.singletonList(row);
        List<Map<String, Object>> plain = MonitorResultDecryptor.decryptRows(
                "default", Collections.singletonList("user"), cipher);
        assertEquals(1, plain.size());
        assertNotEquals(cipher.get(0).get("phone"), plain.get(0).get("phone"));
        assertEquals("13900139000", plain.get(0).get("phone"));
        assertTrue(MonitorResultDecryptor.isConfiguredEncryptColumn("user", "phone", null));
    }

    @Test
    void customCryptoServiceIsHonored() {
        FieldCryptoServiceHolder.set(new FieldCryptoService() {
            @Override
            public boolean needEncrypt(String table, String column, String datasourceId) {
                return "secret".equalsIgnoreCase(column);
            }

            @Override
            public String encrypt(String table, String column, String plain, String datasourceId) {
                return plain;
            }

            @Override
            public String decrypt(String table, String column, String cipher, String datasourceId) {
                return "opened";
            }
        });
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("secret", "xxx");
        row.put("phone", "ENC:1");
        Map<String, Object> plain = MonitorResultDecryptor.decryptConfiguredColumns(
                null, "any", row);
        assertEquals("opened", plain.get("secret"));
        assertEquals("ENC:1", plain.get("phone"));
    }
}
