package io.github.genkidoudou.monitor.ops;

import io.github.genkidoudou.core.cache.StrategyCache;
import io.github.genkidoudou.core.config.ConfigInitializer;
import io.github.genkidoudou.core.config.DigestConfigRegistry;
import io.github.genkidoudou.core.config.EncryptModeHolder;
import io.github.genkidoudou.core.config.FieldEncryptorProperties;
import io.github.genkidoudou.core.config.TableConfigRegistry;
import io.github.genkidoudou.core.crypto.FieldCryptoServiceHolder;
import io.github.genkidoudou.core.digest.ResolvedDigestRule;
import io.github.genkidoudou.core.strategy.FieldEncryptorStrategy;
import io.github.genkidoudou.core.strategy.HmacSha256DigestStrategy;
import io.github.genkidoudou.monitor.MonitorProperties;
import io.github.genkidoudou.monitor.dto.ApiResponse;
import io.github.genkidoudou.monitor.dto.RowVerifyRequest;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模拟 MYBATIS 模式（JDBC 不解密）下单行验签：先解密源字段再比对摘要。
 */
class MonitorRowVerifyCompatTest {

    private static final String HMAC_KEY = "monitor-row-verify-secret";

    private JdbcDataSource ds;
    private MonitorOpsFacade facade;
    private HmacSha256DigestStrategy digestStrategy;

    @BeforeEach
    void setUp() throws Exception {
        ConfigInitializer.reset();
        TableConfigRegistry.clear();
        DigestConfigRegistry.clear();
        FieldCryptoServiceHolder.reset();
        EncryptModeHolder.setMode(FieldEncryptorProperties.Mode.MYBATIS);

        digestStrategy = new HmacSha256DigestStrategy(HMAC_KEY);
        StrategyCache.registerStrategy(HmacSha256DigestStrategy.class, digestStrategy);

        Map<String, Class<? extends FieldEncryptorStrategy>> fields = new HashMap<>();
        fields.put("phone", PrefixEncryptStrategy.class);
        TableConfigRegistry.registerTable("default", "digest_user", fields);
        DigestConfigRegistry.register("default", "digest_user", Collections.singletonList(
                new ResolvedDigestRule("digest_user", Collections.singletonList("phone"),
                        "row_digest", HmacSha256DigestStrategy.class,
                        FieldEncryptorProperties.PartialUpdate.RELOAD, true,
                        FieldEncryptorProperties.FailurePolicy.FAIL_FAST)));

        ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:row_verify_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("phone", "13800138000");
        String digest = digestStrategy.digest(sources);

        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE digest_user ("
                    + "id BIGINT PRIMARY KEY, phone VARCHAR(100), row_digest VARCHAR(200))");
            st.execute("INSERT INTO digest_user(id, phone, row_digest) VALUES (1, 'ENC:13800138000', '"
                    + digest + "')");
            st.execute("INSERT INTO digest_user(id, phone, row_digest) VALUES (2, 'ENC:13800138000', 'TAMPERED')");
        }

        MonitorProperties props = new MonitorProperties();
        props.getTablePrimaryKeys().put("digest_user", "id");
        FieldEncryptorProperties enc = new FieldEncryptorProperties();
        enc.setEnable(true);
        enc.setMode(FieldEncryptorProperties.Mode.MYBATIS);
        FieldEncryptorProperties.TableConfig tc = new FieldEncryptorProperties.TableConfig();
        tc.setTableName("digest_user");
        FieldEncryptorProperties.FieldConfig fc = new FieldEncryptorProperties.FieldConfig();
        fc.setFieldName("phone");
        tc.setFields(Collections.singletonList(fc));
        FieldEncryptorProperties.DigestConfig dr = new FieldEncryptorProperties.DigestConfig();
        dr.setSourceFields(Collections.singletonList("phone"));
        dr.setTargetField("row_digest");
        tc.setDigest(Collections.singletonList(dr));
        enc.setTables(Collections.singletonList(tc));
        facade = new MonitorOpsFacade(props, enc, ds);
    }

    @AfterEach
    void tearDown() {
        EncryptModeHolder.reset();
        DigestConfigRegistry.clear();
        TableConfigRegistry.clear();
        FieldCryptoServiceHolder.reset();
        ConfigInitializer.reset();
    }

    @Test
    void intactCiphertextRowVerifiesAfterDecrypt() {
        RowVerifyRequest request = new RowVerifyRequest();
        request.setTable("digest_user");
        request.setId(1L);
        ApiResponse<?> response = facade.rowVerify(request);
        assertTrue(response.isSuccess(), response.getMessage());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertEquals(Boolean.TRUE, data.get("verified"));
        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) data.get("row");
        assertEquals("13800138000", cell(row, "phone"));
    }

    @Test
    void tamperedDigestFails() {
        RowVerifyRequest request = new RowVerifyRequest();
        request.setTable("digest_user");
        request.setId(2L);
        ApiResponse<?> response = facade.rowVerify(request);
        assertTrue(response.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertEquals(Boolean.FALSE, data.get("verified"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> details = (List<Map<String, Object>>) data.get("details");
        assertFalse(details.isEmpty());
        assertEquals(Boolean.FALSE, details.get(0).get("verified"));
    }

    @Test
    void missingDigestRulesRejected() {
        DigestConfigRegistry.clear();
        RowVerifyRequest request = new RowVerifyRequest();
        request.setTable("digest_user");
        request.setId(1L);
        ApiResponse<?> response = facade.rowVerify(request);
        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().contains("摘要"));
    }

    private static Object cell(Map<String, Object> row, String name) {
        if (row == null || name == null) {
            return null;
        }
        if (row.containsKey(name)) {
            return row.get(name);
        }
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }
}
