package io.github.genkidoudou.playground;

import cn.hutool.json.JSONUtil;
import io.github.genkidoudou.playground.dto.ApiResponse;
import io.github.genkidoudou.playground.support.PlaygroundDispatcher;
import io.github.genkidoudou.playground.support.PlaygroundExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaygroundPersonDispatcherTest {

    private PlaygroundDispatcher dispatcher;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = memoryDs();
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS playground_person");
            st.execute("CREATE TABLE playground_person (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "name VARCHAR(255), phone VARCHAR(255), id_card VARCHAR(255), age INT, row_digest VARCHAR(255))");
        }
        PlaygroundProperties properties = new PlaygroundProperties();
        properties.setAllowedTables(java.util.Collections.singletonList("playground_person"));
        properties.setAuthEnabled(false);
        PlaygroundEngine engine = new PlaygroundEngine(properties, dataSource, null);
        dispatcher = new PlaygroundDispatcher(engine, properties);
    }

    @Test
    void personRoutesRespondWithApiEnvelope() {
        PlaygroundExchange create = exchange("POST", "/api/person/create.json",
                "{\"name\":\"张三\",\"phone\":\"13800138000\",\"idCard\":\"110101199001011234\",\"age\":28}");
        dispatcher.dispatch(create);
        ApiResponse<?> createBody = JSONUtil.toBean(create.getBodyString(), ApiResponse.class);
        assertTrue(createBody.isSuccess());
        assertEquals(200, create.getStatusCode());

        PlaygroundExchange list = exchange("POST", "/api/person/list.json", "{\"view\":\"business\"}");
        dispatcher.dispatch(list);
        ApiResponse<?> listBody = JSONUtil.toBean(list.getBodyString(), ApiResponse.class);
        assertTrue(listBody.isSuccess());

        PlaygroundExchange preflight = exchange("GET", "/api/person/preflight.json", null);
        dispatcher.dispatch(preflight);
        assertEquals(200, preflight.getStatusCode());
    }

    private PlaygroundExchange exchange(String method, String path, String body) {
        PlaygroundExchange ex = new PlaygroundExchange();
        ex.setMethod(method);
        ex.setPathInfo(path);
        ex.setBody(body);
        ex.setParams(new HashMap<String, String>());
        return ex;
    }

    private DataSource memoryDs() {
        String url = "jdbc:h2:mem:playground_person_dispatcher;MODE=MySQL;DB_CLOSE_DELAY=-1";
        return new DataSource() {
            @Override public Connection getConnection() throws java.sql.SQLException {
                return DriverManager.getConnection(url, "sa", "");
            }
            @Override public Connection getConnection(String username, String password)
                    throws java.sql.SQLException {
                return DriverManager.getConnection(url, username, password);
            }
            @Override public <T> T unwrap(Class<T> iface) { throw new UnsupportedOperationException(); }
            @Override public boolean isWrapperFor(Class<?> iface) { return false; }
            @Override public java.io.PrintWriter getLogWriter() { return null; }
            @Override public void setLogWriter(java.io.PrintWriter out) { }
            @Override public void setLoginTimeout(int seconds) { }
            @Override public int getLoginTimeout() { return 0; }
            @Override public java.util.logging.Logger getParentLogger() {
                return java.util.logging.Logger.getGlobal();
            }
        };
    }
}
