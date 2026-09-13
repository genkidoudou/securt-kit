package io.github.genkidoudou.playground;

import cn.hutool.json.JSONUtil;
import io.github.genkidoudou.core.config.ConfigInitializer;
import io.github.genkidoudou.core.config.FieldEncryptorProperties;
import io.github.genkidoudou.playground.dto.ApiResponse;
import io.github.genkidoudou.playground.dto.ScenarioDescriptor;
import io.github.genkidoudou.playground.dto.ScenarioSqlRunRequest;
import io.github.genkidoudou.playground.support.PlaygroundDispatcher;
import io.github.genkidoudou.playground.support.PlaygroundExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaygroundSqlWorkbenchTest {

    private DataSource dataSource;
    private PlaygroundProperties properties;
    private PlaygroundEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        ConfigInitializer.reset();
        dataSource = memoryDs("sql_workbench_" + System.nanoTime());
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS orders");
            st.execute("DROP TABLE IF EXISTS \"user\"");
            st.execute("CREATE TABLE \"user\" (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100), "
                    + "phone VARCHAR(100), age INT, email VARCHAR(100))");
            st.execute("CREATE TABLE orders (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT, "
                    + "order_no VARCHAR(50), customer_name VARCHAR(100), customer_phone VARCHAR(100))");
            st.execute("INSERT INTO \"user\" (name, phone, age, email) VALUES "
                    + "('张三', '13800138000(加密)', 30, 'a@example.com')");
            st.execute("INSERT INTO orders (user_id, order_no, customer_name, customer_phone) VALUES "
                    + "(1, 'O-1', '张三', '13800138000(加密)')");
        }
        properties = new PlaygroundProperties();
        properties.setAuthEnabled(false);
        properties.setAllowedTables(Arrays.asList("user", "orders"));
        enableSkipComment();
        engine = new PlaygroundEngine(properties, dataSource, null);
    }

    @Test
    void scenarioDescriptorSerializesSampleFields() {
        ScenarioDescriptor d = ScenarioDescriptor.of("single-eq", "单表等值", "desc",
                Collections.singletonList(new ScenarioDescriptor.ParamSchema("phone", "手机号", true, "x")));
        Map<String, Object> samples = new LinkedHashMap<>();
        samples.put("phone", "13800138000");
        d.setSampleParams(samples);
        d.setExampleSqlPlain("SELECT * FROM \"user\" WHERE phone = ?");
        d.setExampleSqlCipher("/* SECURT_SKIP */ SELECT * FROM \"user\" WHERE phone = ?");
        d.setSampleHint("种子含 13800138000");
        Map<String, Object> map = d.toMap();
        assertEquals("13800138000", ((Map<?, ?>) map.get("sampleParams")).get("phone"));
        assertTrue(String.valueOf(map.get("exampleSqlPlain")).contains("SELECT"));
        assertTrue(String.valueOf(map.get("exampleSqlCipher")).contains("SECURT_SKIP"));
        assertFalse(String.valueOf(map.get("sampleHint")).isEmpty());
    }

    @Test
    void seedPreviewReturnsBothTables() {
        ApiResponse<Map<String, Object>> resp = engine.seedPreview(null);
        assertTrue(resp.isSuccess(), resp.getMessage());
        assertTrue(resp.getData().containsKey("userRows"));
        assertTrue(resp.getData().containsKey("ordersRows"));
        assertFalse(String.valueOf(resp.getData().get("encryptNote")).isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> users = (List<Map<String, Object>>) resp.getData().get("userRows");
        assertFalse(users.isEmpty());
        Object phone = users.get(0).entrySet().stream()
                .filter(e -> e.getKey() != null && "phone".equalsIgnoreCase(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        assertTrue(String.valueOf(phone).contains("加密"), "phone=" + phone + " row=" + users.get(0));
    }

    @Test
    void sqlRunAcceptsSelectAndRejectsWriteOrMulti() {
        ScenarioSqlRunRequest ok = new ScenarioSqlRunRequest();
        ok.setSql("SELECT id, name FROM \"user\" LIMIT 10");
        ApiResponse<Map<String, Object>> success = engine.sqlRun(ok);
        assertTrue(success.isSuccess(), success.getMessage());
        assertTrue(success.getData().containsKey("rows"));
        assertTrue(success.getData().containsKey("sqlMeta"));

        ScenarioSqlRunRequest multi = new ScenarioSqlRunRequest();
        multi.setSql("SELECT 1; SELECT 2");
        assertEquals(400, engine.sqlRun(multi).getCode());

        ScenarioSqlRunRequest update = new ScenarioSqlRunRequest();
        update.setSql("UPDATE \"user\" SET name = 'x'");
        assertEquals(400, engine.sqlRun(update).getCode());
        assertTrue(engine.sqlRun(update).getMessage().contains("UPDATE")
                || engine.sqlRun(update).getMessage().contains("只读"));
    }

    @Test
    void sqlRunAddsMybatisNoteWhenNotSkipped() {
        io.github.genkidoudou.core.config.EncryptModeHolder.setMode(
                io.github.genkidoudou.core.config.FieldEncryptorProperties.Mode.MYBATIS);
        try {
            ScenarioSqlRunRequest ok = new ScenarioSqlRunRequest();
            ok.setSql("SELECT id, phone FROM \"user\" WHERE phone = '13800138000' LIMIT 10");
            ok.setUseSkip(false);
            ApiResponse<Map<String, Object>> success = engine.sqlRun(ok);
            assertTrue(success.isSuccess(), success.getMessage());
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) success.getData().get("sqlMeta");
            assertEquals("MYBATIS", String.valueOf(meta.get("encryptMode")));
            assertFalse(Boolean.TRUE.equals(meta.get("skipped")));
            assertTrue(String.valueOf(meta.get("note")).contains("明文字面量"));
            assertTrue(String.valueOf(meta.get("note")).contains("运行场景"));
        } finally {
            io.github.genkidoudou.core.config.EncryptModeHolder.reset();
        }
    }

    @Test
    void validateSelectOnlyUnit() {
        PlaygroundEngine.validateSelectOnly("SELECT 1");
        PlaygroundEngine.validateSelectOnly("/* note */ SELECT id FROM \"user\"");
        assertThrows(IllegalArgumentException.class, () -> PlaygroundEngine.validateSelectOnly("SELECT 1;"));
        assertThrows(IllegalArgumentException.class, () -> PlaygroundEngine.validateSelectOnly("UPDATE t SET a=1"));
        assertThrows(IllegalArgumentException.class, () -> PlaygroundEngine.validateSelectOnly("INSERT INTO t VALUES (1)"));
    }

    @Test
    void useSkipPrependsTokenWhenEnabled() {
        ScenarioSqlRunRequest req = new ScenarioSqlRunRequest();
        req.setSql("SELECT id, phone FROM \"user\" LIMIT 5");
        req.setUseSkip(true);
        ApiResponse<Map<String, Object>> resp = engine.sqlRun(req);
        assertTrue(resp.isSuccess(), resp.getMessage());
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) resp.getData().get("sqlMeta");
        assertTrue(Boolean.TRUE.equals(meta.get("skipped")));
        assertTrue(String.valueOf(meta.get("sql")).contains("SECURT_SKIP"));
    }

    @Test
    void useSkipFailsWhenSkipDisabled() {
        ConfigInitializer.reset();
        ScenarioSqlRunRequest req = new ScenarioSqlRunRequest();
        req.setSql("SELECT 1");
        req.setUseSkip(true);
        ApiResponse<Map<String, Object>> resp = engine.sqlRun(req);
        assertEquals(400, resp.getCode());
        assertTrue(resp.getMessage().contains("SECURT_SKIP") || resp.getMessage().contains("skip"));
    }

    @Test
    void dispatcherWiresSeedPreviewAndSqlRun() {
        PlaygroundDispatcher dispatcher = new PlaygroundDispatcher(engine, properties);

        PlaygroundExchange preview = exchange("GET", "/api/scenarios/seed-preview.json", null);
        dispatcher.dispatch(preview);
        assertEquals(200, preview.getStatusCode());
        ApiResponse<?> previewBody = JSONUtil.toBean(preview.getBodyString(), ApiResponse.class);
        assertTrue(previewBody.isSuccess());

        PlaygroundExchange bad = exchange("POST", "/api/scenarios/sql-run.json",
                "{\"sql\":\"UPDATE \\\"user\\\" SET name='x'\"}");
        dispatcher.dispatch(bad);
        assertEquals(400, bad.getStatusCode());

        PlaygroundExchange ok = exchange("POST", "/api/scenarios/sql-run.json",
                "{\"sql\":\"SELECT id FROM \\\"user\\\" LIMIT 1\",\"useSkip\":false}");
        dispatcher.dispatch(ok);
        assertEquals(200, ok.getStatusCode());

        PlaygroundExchange run = exchange("POST", "/api/scenarios/run.json",
                "{\"scenarioId\":\"single-eq\"}");
        dispatcher.dispatch(run);
        assertEquals(503, run.getStatusCode());
    }

    @Test
    void scenarioApisRequireAuthWhenEnabled() {
        properties.setAuthEnabled(true);
        PlaygroundDispatcher dispatcher = new PlaygroundDispatcher(engine, properties);
        PlaygroundExchange preview = exchange("GET", "/api/scenarios/seed-preview.json", null);
        dispatcher.dispatch(preview);
        assertEquals(401, preview.getStatusCode());
        PlaygroundExchange sql = exchange("POST", "/api/scenarios/sql-run.json", "{\"sql\":\"SELECT 1\"}");
        dispatcher.dispatch(sql);
        assertEquals(401, sql.getStatusCode());
    }

    private void enableSkipComment() {
        FieldEncryptorProperties core = new FieldEncryptorProperties();
        core.setEnable(true);
        FieldEncryptorProperties.SkipCommentConfig skip = new FieldEncryptorProperties.SkipCommentConfig();
        skip.setEnable(true);
        skip.setToken("SECURT_SKIP");
        core.setSkipComment(skip);
        ConfigInitializer.initialize(core);
    }

    private PlaygroundExchange exchange(String method, String path, String body) {
        PlaygroundExchange ex = new PlaygroundExchange();
        ex.setMethod(method);
        ex.setPathInfo(path);
        ex.setBody(body);
        ex.setParams(new HashMap<String, String>());
        return ex;
    }

    private static DataSource memoryDs(String name) throws Exception {
        Class.forName("org.h2.Driver");
        final String url = "jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1;MODE=MySQL";
        return new DataSource() {
            @Override
            public Connection getConnection() throws java.sql.SQLException {
                return DriverManager.getConnection(url, "sa", "");
            }

            @Override
            public Connection getConnection(String username, String password) throws java.sql.SQLException {
                return DriverManager.getConnection(url, username, password);
            }

            @Override
            public java.io.PrintWriter getLogWriter() {
                return null;
            }

            @Override
            public void setLogWriter(java.io.PrintWriter out) {
            }

            @Override
            public void setLoginTimeout(int seconds) {
            }

            @Override
            public int getLoginTimeout() {
                return 0;
            }

            @Override
            public java.util.logging.Logger getParentLogger() {
                return java.util.logging.Logger.getLogger("global");
            }

            @Override
            public <T> T unwrap(Class<T> iface) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) {
                return false;
            }
        };
    }
}
