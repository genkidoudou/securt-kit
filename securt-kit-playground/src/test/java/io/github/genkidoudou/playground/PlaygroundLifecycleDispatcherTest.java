package io.github.genkidoudou.playground;

import io.github.genkidoudou.playground.support.PlaygroundDispatcher;
import io.github.genkidoudou.playground.support.PlaygroundExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaygroundLifecycleDispatcherTest {

    private PlaygroundDispatcher dispatcher;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = memoryDs();
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS digest_user (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "name VARCHAR(255), phone VARCHAR(255), age INT, email VARCHAR(255), row_digest VARCHAR(255))");
        }
        PlaygroundProperties properties = new PlaygroundProperties();
        properties.setAllowedTables(Collections.singletonList("digest_user"));
        dispatcher = new PlaygroundDispatcher(new PlaygroundEngine(properties, dataSource, null), properties);
    }

    @Test
    void preflightRouteReturnsReadinessPayload() {
        PlaygroundExchange exchange = new PlaygroundExchange("GET", "/api/lifecycle/preflight.json");

        dispatcher.dispatch(exchange);

        assertEquals(200, exchange.getStatusCode());
        assertTrue(exchange.getBodyString().contains("\"ready\""));
        assertTrue(exchange.getBodyString().contains("\"checks\""));
    }

    @Test
    void lifecyclePostRouteRejectsWrongMethod() {
        PlaygroundExchange exchange = new PlaygroundExchange("GET", "/api/lifecycle/insert.json");

        dispatcher.dispatch(exchange);

        assertEquals(400, exchange.getStatusCode());
        assertTrue(exchange.getBodyString().contains("POST"));
    }

    @Test
    void lifecycleValidationCodeBecomesHttpStatus() {
        PlaygroundExchange exchange = new PlaygroundExchange("POST", "/api/lifecycle/query.json");
        exchange.setBody("{}");

        dispatcher.dispatch(exchange);

        assertEquals(400, exchange.getStatusCode());
        assertTrue(exchange.getBodyString().contains("recordId"));
    }

    @Test
    void authenticatedLifecycleRouteRequiresLogin() {
        PlaygroundProperties properties = new PlaygroundProperties();
        properties.setAuthEnabled(true);
        PlaygroundDispatcher secured = new PlaygroundDispatcher(
                new PlaygroundEngine(properties, null, null), properties);
        PlaygroundExchange exchange = new PlaygroundExchange("GET", "/api/lifecycle/preflight.json");

        secured.dispatch(exchange);

        assertEquals(401, exchange.getStatusCode());
    }

    private static DataSource memoryDs() throws Exception {
        Class.forName("org.h2.Driver");
        final String url = "jdbc:h2:mem:playground_dispatcher;DB_CLOSE_DELAY=-1;MODE=MySQL";
        return new DataSource() {
            @Override public Connection getConnection() throws java.sql.SQLException {
                return DriverManager.getConnection(url, "sa", "");
            }
            @Override public Connection getConnection(String username, String password)
                    throws java.sql.SQLException {
                return DriverManager.getConnection(url, username, password);
            }
            @Override public java.io.PrintWriter getLogWriter() { return null; }
            @Override public void setLogWriter(java.io.PrintWriter out) { }
            @Override public void setLoginTimeout(int seconds) { }
            @Override public int getLoginTimeout() { return 0; }
            @Override public java.util.logging.Logger getParentLogger() {
                return java.util.logging.Logger.getLogger("global");
            }
            @Override public <T> T unwrap(Class<T> iface) { throw new UnsupportedOperationException(); }
            @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        };
    }
}
