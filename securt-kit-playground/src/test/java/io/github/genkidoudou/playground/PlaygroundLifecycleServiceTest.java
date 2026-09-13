package io.github.genkidoudou.playground;

import io.github.genkidoudou.core.config.ConfigInitializer;
import io.github.genkidoudou.core.config.DigestConfigRegistry;
import io.github.genkidoudou.core.config.FieldEncryptorProperties;
import io.github.genkidoudou.core.config.TableConfigRegistry;
import io.github.genkidoudou.core.digest.ResolvedDigestRule;
import io.github.genkidoudou.core.strategy.HmacSha256DigestStrategy;
import io.github.genkidoudou.playground.dto.ApiResponse;
import io.github.genkidoudou.playground.dto.LifecyclePreflight;
import io.github.genkidoudou.playground.dto.LifecycleRequest;
import io.github.genkidoudou.playground.dto.LifecycleSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaygroundLifecycleServiceTest {

    private DataSource dataSource;
    private PlaygroundLifecycleService service;

    @BeforeEach
    void setUp() throws Exception {
        ConfigInitializer.reset();
        dataSource = memoryDs();
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS digest_user");
            st.execute("CREATE TABLE digest_user (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "name VARCHAR(255), phone VARCHAR(255), age INT, email VARCHAR(255), row_digest VARCHAR(255))");
        }
        PlaygroundProperties properties = new PlaygroundProperties();
        properties.setAllowedTables(Collections.singletonList("digest_user"));
        service = new PlaygroundLifecycleService(properties, dataSource, null);
    }

    @Test
    void preflightReportsEveryMissingRuntimePrerequisiteWithoutMutation() throws Exception {
        ApiResponse<LifecyclePreflight> response = service.preflight(null);

        assertTrue(response.isSuccess());
        assertFalse(response.getData().isReady());
        assertTrue(response.getData().getChecks().stream().anyMatch(c -> "encrypted-name".equals(c.getId()) && !c.isPassed()));
        assertTrue(response.getData().getChecks().stream().anyMatch(c -> "encrypted-phone".equals(c.getId()) && !c.isPassed()));
        assertTrue(response.getData().getChecks().stream().anyMatch(c -> "phone-digest".equals(c.getId()) && !c.isPassed()));
        assertTrue(response.getData().getChecks().stream().anyMatch(c -> "skip-comment".equals(c.getId()) && !c.isPassed()));
        assertEquals(0L, rowCount());
    }

    @Test
    void preflightIsReadyWhenResolvedConfigurationMatchesSchema() {
        FieldEncryptorProperties core = new FieldEncryptorProperties();
        core.setEnable(true);
        FieldEncryptorProperties.SkipCommentConfig skip =
                new FieldEncryptorProperties.SkipCommentConfig();
        skip.setEnable(true);
        skip.setToken("SECURT_SKIP");
        core.setSkipComment(skip);
        ConfigInitializer.initialize(core);

        Map<String, Class<? extends io.github.genkidoudou.core.strategy.FieldEncryptorStrategy>> fields =
                new HashMap<>();
        fields.put("name", HmacSha256DigestStrategy.class);
        fields.put("phone", HmacSha256DigestStrategy.class);
        TableConfigRegistry.registerTable("default", "digest_user", fields);
        DigestConfigRegistry.register("default", "digest_user", Collections.singletonList(
                new ResolvedDigestRule("digest_user", Collections.singletonList("phone"),
                        "row_digest", HmacSha256DigestStrategy.class,
                        FieldEncryptorProperties.PartialUpdate.RELOAD, true,
                        FieldEncryptorProperties.FailurePolicy.FAIL_FAST)));

        LifecyclePreflight result = service.preflight(null).getData();

        assertTrue(result.isReady(), result.getChecks().toString());
        assertTrue(result.getChecks().stream().allMatch(LifecyclePreflight.Check::isPassed));
    }

    @Test
    void insertRejectsClientControlledTableAndDigestWithoutMutation() throws Exception {
        LifecycleRequest tableRequest = validInsert();
        tableRequest.setTable("secret");
        ApiResponse<LifecycleSnapshot> tableResponse = service.insert(tableRequest);

        LifecycleRequest digestRequest = validInsert();
        Map<String, Object> fields = new HashMap<>();
        fields.put("row_digest", "forged");
        digestRequest.setFields(fields);
        ApiResponse<LifecycleSnapshot> digestResponse = service.insert(digestRequest);

        assertEquals(400, tableResponse.getCode());
        assertEquals(400, digestResponse.getCode());
        assertEquals(0L, rowCount());
    }

    @Test
    void updateRejectsUnknownFieldAndMissingRecordId() {
        LifecycleRequest request = new LifecycleRequest();
        request.setRecordId(1L);
        request.getFields().put("password", "secret");

        ApiResponse<LifecycleSnapshot> unknownField = service.update(request);
        request.setRecordId(null);
        request.getFields().clear();
        request.getFields().put("phone", "13900139000");
        ApiResponse<LifecycleSnapshot> missingId = service.update(request);

        assertEquals(400, unknownField.getCode());
        assertEquals(400, missingId.getCode());
    }

    @Test
    void insertQueryUpdateVerifyUseParameterizedSqlAndRejectDigestOnUpdate() throws Exception {
        List<String> preparedSql = new ArrayList<>();
        PlaygroundLifecycleService recording = new PlaygroundLifecycleService(
                allowedProperties(), recording(dataSource, preparedSql), null);

        LifecycleSnapshot inserted = recording.insert(validInsert()).getData();
        assertNotNull(inserted.getRecordId());

        LifecycleRequest byId = new LifecycleRequest();
        byId.setRecordId(inserted.getRecordId());
        assertEquals(200, recording.query(byId).getCode());

        LifecycleRequest update = new LifecycleRequest();
        update.setRecordId(inserted.getRecordId());
        update.getFields().put("phone", "13900139000");
        assertEquals(200, recording.update(update).getCode());

        LifecycleRequest digestUpdate = new LifecycleRequest();
        digestUpdate.setRecordId(inserted.getRecordId());
        digestUpdate.getFields().put("row_digest", "forged");
        assertEquals(400, recording.update(digestUpdate).getCode());

        assertEquals(200, recording.verify(byId).getCode());
        assertTrue(preparedSql.stream().anyMatch(sql ->
                sql.contains("INSERT INTO digest_user") && sql.contains("?")));
        assertTrue(preparedSql.stream().anyMatch(sql ->
                sql.contains("UPDATE digest_user SET") && sql.contains("?") && sql.contains("WHERE id = ?")));
        assertTrue(preparedSql.stream().noneMatch(sql ->
                sql.contains("UPDATE digest_user SET") && sql.contains("row_digest")));
    }

    @Test
    void verifyPassesOnIntactRecordAndFailsWhenBusinessReadThrows() {
        LifecycleSnapshot inserted = service.insert(validInsert()).getData();
        LifecycleRequest byId = new LifecycleRequest();
        byId.setRecordId(inserted.getRecordId());

        LifecycleSnapshot verified = service.verify(byId).getData();
        assertEquals("PASSED", verified.getStatus());
        assertTrue(verified.getAssertions().stream()
                .anyMatch(a -> "digest-valid".equals(a.getId()) && a.isPassed()));

        PlaygroundLifecycleService failing = new PlaygroundLifecycleService(
                allowedProperties(), verificationFailing(dataSource), null);
        LifecycleSnapshot broken = failing.query(byId).getData();
        assertEquals("FAILED", broken.getStatus());
        assertNotNull(broken.getError());
        assertEquals("DIGEST_VERIFICATION", broken.getError().getCategory());
    }

    @Test
    void queryReportsMissingRecordAndPlainDatabaseCannotClaimEncryption() {
        LifecycleRequest missing = new LifecycleRequest();
        missing.setRecordId(999L);
        assertEquals(404, service.query(missing).getCode());

        ApiResponse<LifecycleSnapshot> inserted = service.insert(validInsert());
        assertTrue(inserted.isSuccess());
        assertEquals("FAILED", inserted.getData().getStatus());
        assertTrue(inserted.getData().getAssertions().stream()
                .anyMatch(a -> "phone-encrypted".equals(a.getId()) && !a.isPassed()));
        assertNotNull(inserted.getData().getRecordId());
    }

    @Test
    void tamperRejectsUnsupportedTargetWithoutMutation() throws Exception {
        ApiResponse<LifecycleSnapshot> inserted = service.insert(validInsert());
        LifecycleRequest request = new LifecycleRequest();
        request.setRecordId(inserted.getData().getRecordId());
        request.setTamperTarget("email");

        ApiResponse<LifecycleSnapshot> response = service.tamper(request);

        assertEquals(400, response.getCode());
        assertEquals("demo@example.com", column(request.getRecordId(), "email"));

        request.setTamperTarget("name");
        assertEquals(400, service.tamper(request).getCode());
    }

    @Test
    void approvedTamperTargetsFailWhenVerificationDoesNotDetectThem() {
        for (String target : new String[]{"row_digest", "phone"}) {
            LifecycleSnapshot inserted = service.insert(validInsert()).getData();
            LifecycleRequest request = new LifecycleRequest();
            request.setRecordId(inserted.getRecordId());
            request.setTamperTarget(target);

            LifecycleSnapshot result = service.tamper(request).getData();

            assertEquals("FAILED", result.getStatus());
            assertFalse(result.getAssertions().get(0).isPassed());
            assertTrue(String.valueOf(result.getRawDatabaseRow().get(target)).startsWith("TAMPERED_"));
        }
    }

    @Test
    void snapshotUsesExplicitRawAndBusinessSelectPaths() throws Exception {
        List<String> preparedSql = new ArrayList<>();
        PlaygroundLifecycleService recordingService = new PlaygroundLifecycleService(
                allowedProperties(), recording(dataSource, preparedSql), null);

        recordingService.insert(validInsert());

        assertTrue(preparedSql.stream().anyMatch(sql ->
                sql.startsWith("/* SECURT_SKIP */ SELECT")));
        assertTrue(preparedSql.stream().anyMatch(sql ->
                sql.startsWith("SELECT id, name, phone")));
    }

    @Test
    void selectedDatasourceIsIsolatedAndUnknownIdIsRejected() throws Exception {
        DataSource secondary = memoryDs("playground_lifecycle_secondary");
        try (Connection c = secondary.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE digest_user (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "name VARCHAR(255), phone VARCHAR(255), age INT, email VARCHAR(255), row_digest VARCHAR(255))");
        }
        Map<String, DataSource> sources = new LinkedHashMap<>();
        sources.put("primary", dataSource);
        sources.put("secondary", secondary);
        PlaygroundLifecycleService multi = new PlaygroundLifecycleService(
                allowedProperties(), null, () -> sources);

        LifecycleRequest request = validInsert();
        request.setDatasourceId("secondary");
        assertTrue(multi.insert(request).isSuccess());
        assertEquals(0L, count(dataSource));
        assertEquals(1L, count(secondary));

        request = validInsert();
        request.setDatasourceId("missing");
        assertEquals(400, multi.insert(request).getCode());
    }

    @Test
    void expectedVerificationExceptionMakesTamperDetectionPass() {
        PlaygroundLifecycleService detecting = new PlaygroundLifecycleService(
                allowedProperties(), verificationFailing(dataSource), null);
        LifecycleSnapshot inserted = detecting.insert(validInsert()).getData();
        LifecycleRequest request = new LifecycleRequest();
        request.setRecordId(inserted.getRecordId());
        request.setTamperTarget("row_digest");

        LifecycleSnapshot result = detecting.tamper(request).getData();

        assertEquals("PASSED", result.getStatus());
        assertTrue(result.getAssertions().get(0).isPassed());
        assertNotNull(result.getError());
    }

    private LifecycleRequest validInsert() {
        LifecycleRequest request = new LifecycleRequest();
        request.setName("张三");
        request.setPhone("13800138000");
        request.setAge(28);
        request.setEmail("demo@example.com");
        return request;
    }

    private PlaygroundProperties allowedProperties() {
        PlaygroundProperties properties = new PlaygroundProperties();
        properties.setAllowedTables(Collections.singletonList("digest_user"));
        return properties;
    }

    private long rowCount() throws Exception {
        return count(dataSource);
    }

    private long count(DataSource source) throws Exception {
        try (Connection c = source.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM digest_user")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private String column(Long id, String column) throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT " + column + " FROM digest_user WHERE id = " + id)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private static DataSource memoryDs() throws Exception {
        return memoryDs("playground_lifecycle");
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

    private static DataSource recording(final DataSource delegate, final List<String> sqls) {
        return new DataSource() {
            @Override public Connection getConnection() throws java.sql.SQLException {
                return proxy(delegate.getConnection(), sqls);
            }
            @Override public Connection getConnection(String username, String password)
                    throws java.sql.SQLException {
                return proxy(delegate.getConnection(username, password), sqls);
            }
            @Override public java.io.PrintWriter getLogWriter() throws java.sql.SQLException {
                return delegate.getLogWriter();
            }
            @Override public void setLogWriter(java.io.PrintWriter out) throws java.sql.SQLException {
                delegate.setLogWriter(out);
            }
            @Override public void setLoginTimeout(int seconds) throws java.sql.SQLException {
                delegate.setLoginTimeout(seconds);
            }
            @Override public int getLoginTimeout() throws java.sql.SQLException {
                return delegate.getLoginTimeout();
            }
            @Override public java.util.logging.Logger getParentLogger() {
                return java.util.logging.Logger.getLogger("global");
            }
            @Override public <T> T unwrap(Class<T> iface) throws java.sql.SQLException {
                return delegate.unwrap(iface);
            }
            @Override public boolean isWrapperFor(Class<?> iface) throws java.sql.SQLException {
                return delegate.isWrapperFor(iface);
            }
        };
    }

    private static Connection proxy(final Connection connection, final List<String> sqls) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("prepareStatement".equals(method.getName())
                            && args != null && args.length > 0 && args[0] instanceof String) {
                        sqls.add((String) args[0]);
                    }
                    try {
                        return method.invoke(connection, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    private static DataSource verificationFailing(final DataSource delegate) {
        return new DataSource() {
            @Override public Connection getConnection() throws java.sql.SQLException {
                final Connection connection = delegate.getConnection();
                return (Connection) Proxy.newProxyInstance(
                        Connection.class.getClassLoader(),
                        new Class<?>[]{Connection.class},
                        (proxy, method, args) -> {
                            if ("prepareStatement".equals(method.getName())
                                    && args != null && args.length > 0
                                    && String.valueOf(args[0]).startsWith("SELECT id, name, phone")) {
                                throw new java.sql.SQLException("digest mismatch");
                            }
                            try {
                                return method.invoke(connection, args);
                            } catch (InvocationTargetException e) {
                                throw e.getCause();
                            }
                        });
            }
            @Override public Connection getConnection(String username, String password)
                    throws java.sql.SQLException {
                return getConnection();
            }
            @Override public java.io.PrintWriter getLogWriter() throws java.sql.SQLException {
                return delegate.getLogWriter();
            }
            @Override public void setLogWriter(java.io.PrintWriter out) throws java.sql.SQLException {
                delegate.setLogWriter(out);
            }
            @Override public void setLoginTimeout(int seconds) throws java.sql.SQLException {
                delegate.setLoginTimeout(seconds);
            }
            @Override public int getLoginTimeout() throws java.sql.SQLException {
                return delegate.getLoginTimeout();
            }
            @Override public java.util.logging.Logger getParentLogger() {
                return java.util.logging.Logger.getLogger("global");
            }
            @Override public <T> T unwrap(Class<T> iface) throws java.sql.SQLException {
                return delegate.unwrap(iface);
            }
            @Override public boolean isWrapperFor(Class<?> iface) throws java.sql.SQLException {
                return delegate.isWrapperFor(iface);
            }
        };
    }
}
