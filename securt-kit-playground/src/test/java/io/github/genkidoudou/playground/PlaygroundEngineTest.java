package io.github.genkidoudou.playground;

import io.github.genkidoudou.playground.dto.ApiResponse;
import io.github.genkidoudou.playground.support.PlaygroundDispatcher;
import io.github.genkidoudou.playground.support.PlaygroundExchange;
import io.github.genkidoudou.playground.support.PlaygroundResourceLoader;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaygroundEngineTest {

    @Test
    void propertiesDefaults() {
        PlaygroundProperties p = new PlaygroundProperties();
        assertEquals("/playground", p.getPath());
        assertFalse(p.isAuthEnabled());
        assertFalse(p.isEnabled());
        assertTrue(p.getAllowedTables().contains("user"));
    }

    @Test
    void resourceLoaderServesIndex() {
        PlaygroundResourceLoader loader = new PlaygroundResourceLoader();
        assertEquals("support/playground/http/resources", PlaygroundResourceLoader.DEFAULT_RESOURCE_ROOT);
        PlaygroundResourceLoader.Resource resource = loader.load("/index.html");
        assertNotNull(resource);
        String html = new String(resource.getContent(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(html.contains("Securt-Kit Playground"));
        assertFalse(html.contains("加密解密工具"));
        assertTrue(html.contains("/playground/css/style.css"));
        assertTrue(html.contains("/playground/js/app.js"));
        assertTrue(html.contains("人员维护"));
        assertTrue(html.contains("复杂查询"));
        assertTrue(html.contains("id=\"tabPerson\""));
        assertTrue(html.contains("id=\"tabScenarios\""));
        assertTrue(html.contains("id=\"scenarioList\""));
        assertTrue(html.contains("id=\"seedPreviewCard\""));
        assertTrue(html.contains("id=\"userPreviewTable\""));
        assertTrue(html.contains("id=\"ordersPreviewTable\""));
        assertTrue(html.contains("id=\"scenarioSqlEditor\""));
        assertTrue(html.contains("id=\"runSqlBtn\""));
        assertTrue(html.contains("id=\"sqlRunBypassHint\""));
        assertTrue(html.contains("明文字面量不会自动加密"));
        assertTrue(html.contains("id=\"useSkipCheckbox\""));
        assertTrue(html.contains("id=\"plainPanel\""));
        assertTrue(html.contains("id=\"cipherPanel\""));
        assertTrue(html.contains("id=\"sqlMetaPanel\""));
        assertTrue(html.contains("id=\"scenarioEvidencePanels\""));
        assertTrue(resource.getContent().length > 0);
        assertTrue(resource.getContentType().contains("text/html"));
    }

    @Test
    void dispatcherServesIndex() {
        PlaygroundProperties props = new PlaygroundProperties();
        PlaygroundEngine engine = new PlaygroundEngine(props, null, null);
        PlaygroundDispatcher dispatcher = new PlaygroundDispatcher(engine, props);
        PlaygroundExchange ex = new PlaygroundExchange("GET", "/");
        dispatcher.dispatch(ex);
        assertEquals(200, ex.getStatusCode());
        assertNotNull(ex.getBodyBytes());
        assertTrue(ex.getContentType().contains("text/html"));
    }

    @Test
    void playgroundScriptWiresPersonAndScenarioApis() {
        PlaygroundResourceLoader.Resource resource =
                new PlaygroundResourceLoader().load("/js/app.js");
        assertNotNull(resource);
        String script = new String(resource.getContent(), java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(script.contains("/api/person/preflight.json"));
        assertTrue(script.contains("/api/person/list.json"));
        assertTrue(script.contains("/api/scenarios.json"));
        assertTrue(script.contains("/api/scenarios/run.json"));
        assertTrue(script.contains("/api/scenarios/seed-preview.json"));
        assertTrue(script.contains("/api/scenarios/sql-run.json"));
        assertTrue(script.contains("plainRows"));
        assertTrue(script.contains("cipherRows"));
        assertTrue(script.contains("sqlMeta"));
        assertTrue(script.contains("setTab"));
        assertTrue(script.contains("runScenario"));
        assertTrue(script.contains("loadScenarios"));
        assertTrue(script.contains("loadSeedPreview"));
        assertTrue(script.contains("runSql"));
        assertTrue(script.contains("exampleSqlPlain"));
        assertTrue(script.contains("SECURT_SKIP 原值"));
        assertTrue(script.contains("sql-run.json"));
    }

    @Test
    void playgroundStylesProvideDualTabAndThreePanels() {
        PlaygroundResourceLoader.Resource resource =
                new PlaygroundResourceLoader().load("/css/style.css");
        assertNotNull(resource);
        String css = new String(resource.getContent(), java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(css.contains(".main-tabs"));
        assertTrue(css.contains(".evidence-panels"));
        assertTrue(css.contains("grid-template-columns: repeat(3, minmax(0, 1fr))"));
        assertTrue(css.contains("@media (max-width: 900px)"));
        assertTrue(css.contains(".scenario-layout"));
        assertTrue(css.contains(".seed-preview-grid"));
        assertTrue(css.contains(".sql-workbench"));
    }

    @Test
    void disallowedTableRejected() throws Exception {
        DataSource ds = memoryDs();
        PlaygroundProperties props = new PlaygroundProperties();
        props.setAllowedTables(Collections.singletonList("user"));
        PlaygroundEngine engine = new PlaygroundEngine(props, ds, null);
        io.github.genkidoudou.playground.dto.CrudRequest req = new io.github.genkidoudou.playground.dto.CrudRequest();
        req.setTable("secret");
        Map<String, Object> fields = new HashMap<>();
        fields.put("name", "x");
        req.setFields(fields);
        ApiResponse<?> resp = engine.insert(req);
        assertFalse(resp.isSuccess());
        assertTrue(resp.getMessage().contains("白名单"));
    }

    @Test
    void insertAndQueryRoundTrip() throws Exception {
        DataSource ds = memoryDs();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE \"user\" (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100), phone VARCHAR(50), age INT, email VARCHAR(100))");
        }
        PlaygroundProperties props = new PlaygroundProperties();
        PlaygroundEngine engine = new PlaygroundEngine(props, ds, null);

        io.github.genkidoudou.playground.dto.CrudRequest insert = new io.github.genkidoudou.playground.dto.CrudRequest();
        insert.setTable("user");
        Map<String, Object> fields = new HashMap<>();
        fields.put("name", "Alice");
        fields.put("phone", "13800138000");
        fields.put("age", 30);
        fields.put("email", "a@example.com");
        insert.setFields(fields);
        ApiResponse<?> insertResp = engine.insert(insert);
        assertTrue(insertResp.isSuccess(), insertResp.getMessage());

        io.github.genkidoudou.playground.dto.CrudRequest query = new io.github.genkidoudou.playground.dto.CrudRequest();
        query.setTable("user");
        query.setLimit(10);
        ApiResponse<?> queryResp = engine.query(query);
        assertTrue(queryResp.isSuccess(), queryResp.getMessage());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) queryResp.getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("rows");
        assertFalse(rows.isEmpty());
    }

    @Test
    void metaAndDatasourcesShape() throws Exception {
        DataSource ds = memoryDs();
        PlaygroundEngine engine = new PlaygroundEngine(new PlaygroundProperties(), ds, null);
        ApiResponse<?> meta = engine.meta();
        assertTrue(meta.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> metaData = (Map<String, Object>) meta.getData();
        assertNotNull(metaData.get("allowedTables"));
        assertEquals("/monitor", metaData.get("monitorPath"));

        ApiResponse<?> dsResp = engine.datasources();
        assertTrue(dsResp.isSuccess());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) dsResp.getData();
        assertEquals(1, list.size());
        assertEquals("default", list.get(0).get("id"));
    }

    private static DataSource memoryDs() throws Exception {
        Class.forName("org.h2.Driver");
        final String url = "jdbc:h2:mem:playground_ut;DB_CLOSE_DELAY=-1;MODE=MySQL";
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