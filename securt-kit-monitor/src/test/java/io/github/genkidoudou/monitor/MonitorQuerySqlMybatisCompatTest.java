package io.github.genkidoudou.monitor;

import io.github.genkidoudou.core.config.ConfigInitializer;
import io.github.genkidoudou.core.config.EncryptModeHolder;
import io.github.genkidoudou.core.config.FieldEncryptorProperties;
import io.github.genkidoudou.core.config.TableConfigRegistry;
import io.github.genkidoudou.core.crypto.FieldCryptoServiceHolder;
import io.github.genkidoudou.core.strategy.FieldEncryptorStrategy;
import io.github.genkidoudou.monitor.dto.ApiResponse;
import io.github.genkidoudou.monitor.dto.QuerySqlRequest;
import io.github.genkidoudou.monitor.dto.QuerySqlResponse;
import io.github.genkidoudou.monitor.ops.PrefixEncryptStrategy;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitorQuerySqlMybatisCompatTest {

    private JdbcDataSource ds;
    private MonitorEngine engine;
    private FieldEncryptorProperties enc;

    @BeforeEach
    void setUp() throws Exception {
        ConfigInitializer.reset();
        TableConfigRegistry.clear();
        FieldCryptoServiceHolder.reset();

        Map<String, Class<? extends FieldEncryptorStrategy>> fields = new HashMap<>();
        fields.put("phone", PrefixEncryptStrategy.class);
        TableConfigRegistry.registerTable("default", "user", fields);

        ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:query_sql_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE \"user\" (id BIGINT PRIMARY KEY, phone VARCHAR(100), age INT)");
            st.execute("INSERT INTO \"user\"(id, phone, age) VALUES (1, 'ENC:13800138000', 28)");
        }

        enc = new FieldEncryptorProperties();
        enc.setEnable(true);
        enc.setMode(FieldEncryptorProperties.Mode.MYBATIS);
        FieldEncryptorProperties.SkipCommentConfig skip = new FieldEncryptorProperties.SkipCommentConfig();
        skip.setEnable(false);
        enc.setSkipComment(skip);

        EncryptModeHolder.setMode(FieldEncryptorProperties.Mode.MYBATIS);
        MonitorProperties props = new MonitorProperties();
        engine = new MonitorEngine(props, enc, ds);
    }

    @AfterEach
    void tearDown() {
        EncryptModeHolder.reset();
        TableConfigRegistry.clear();
        FieldCryptoServiceHolder.reset();
        ConfigInitializer.reset();
    }

    @Test
    void mybatisModeWithoutSkipStillSplitsPlainAndCipher() {
        QuerySqlRequest request = new QuerySqlRequest();
        request.setSql("SELECT id, phone, age FROM \"user\"");
        ApiResponse<QuerySqlResponse> response = engine.querySql(request);
        assertTrue(response.isSuccess(), response.getMessage());
        QuerySqlResponse data = response.getData();
        List<Map<String, Object>> plain = data.getPlainRows();
        List<Map<String, Object>> cipher = data.getCipherRows();
        assertEquals(1, plain.size());
        assertEquals(1, cipher.size());
        assertEquals("ENC:13800138000", cell(cipher.get(0), "phone"));
        assertEquals("13800138000", cell(plain.get(0), "phone"));
        assertNotEquals(cell(cipher.get(0), "phone"), cell(plain.get(0), "phone"));
        assertEquals(cell(cipher.get(0), "age"), cell(plain.get(0), "age"));
    }

    @Test
    void jdbcModeWithoutSkipRejectsCipherView() {
        EncryptModeHolder.setMode(FieldEncryptorProperties.Mode.JDBC);
        enc.setMode(FieldEncryptorProperties.Mode.JDBC);
        QuerySqlRequest request = new QuerySqlRequest();
        request.setSql("SELECT id, phone FROM \"user\"");
        ApiResponse<QuerySqlResponse> response = engine.querySql(request);
        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().contains("skip-comment"));
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
