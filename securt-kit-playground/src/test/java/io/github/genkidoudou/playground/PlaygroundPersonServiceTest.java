package io.github.genkidoudou.playground;

import io.github.genkidoudou.core.cache.StrategyCache;
import io.github.genkidoudou.core.config.ConfigInitializer;
import io.github.genkidoudou.core.config.DigestConfigRegistry;
import io.github.genkidoudou.core.config.EncryptModeHolder;
import io.github.genkidoudou.core.config.FieldEncryptorProperties;
import io.github.genkidoudou.core.config.TableConfigRegistry;
import io.github.genkidoudou.core.digest.ResolvedDigestRule;
import io.github.genkidoudou.core.strategy.FieldEncryptorStrategy;
import io.github.genkidoudou.core.strategy.HmacSha256DigestStrategy;
import io.github.genkidoudou.playground.dto.ApiResponse;
import io.github.genkidoudou.playground.dto.PersonPreflight;
import io.github.genkidoudou.playground.dto.PersonRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaygroundPersonServiceTest {

    private DataSource dataSource;
    private PlaygroundPersonService service;

    @BeforeEach
    void setUp() throws Exception {
        ConfigInitializer.reset();
        TableConfigRegistry.clear();
        DigestConfigRegistry.clear();
        StrategyCache.clear();
        EncryptModeHolder.setMode(null);
        dataSource = memoryDs("playground_person_ut");
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS playground_person");
            st.execute("CREATE TABLE playground_person (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "name VARCHAR(255), phone VARCHAR(255), id_card VARCHAR(255), age INT, row_digest VARCHAR(255))");
        }
        PlaygroundProperties properties = new PlaygroundProperties();
        properties.setAllowedTables(Collections.singletonList("playground_person"));
        service = new PlaygroundPersonService(properties, dataSource, null);
    }

    @Test
    void preflightReportsMissingEncryptAndDigest() {
        PersonPreflight result = service.preflight(null).getData();
        assertFalse(result.isReady());
        assertTrue(result.getChecks().stream()
                .anyMatch(c -> "encrypted-phone".equals(c.getId()) && !c.isPassed()));
        assertTrue(result.getChecks().stream()
                .anyMatch(c -> "person-digest".equals(c.getId()) && !c.isPassed()));
    }

    @Test
    void preflightReadyWhenConfigMatches() {
        configurePersonProtection();
        PersonPreflight result = service.preflight(null).getData();
        assertTrue(result.isReady(), result.getChecks().toString());
    }

    @Test
    void preflightReadyUnderMybatisMode() {
        configurePersonProtection();
        EncryptModeHolder.setMode(FieldEncryptorProperties.Mode.MYBATIS);
        PersonPreflight result = service.preflight(null).getData();
        assertTrue(result.isReady(), result.getChecks().toString());
        assertTrue(result.getChecks().stream()
                .anyMatch(c -> "encrypt-mode-info".equals(c.getId()) && c.isPassed()
                        && c.getMessage().contains("MYBATIS")));
        assertTrue(result.getChecks().stream().noneMatch(c -> "jdbc-mode".equals(c.getId())));
    }

    @Test
    void createRejectsMissingRequiredFields() throws Exception {
        PersonRequest request = new PersonRequest();
        request.setName("张三");
        assertEquals(400, service.create(request).getCode());
        assertEquals(0L, rowCount());
    }

    @Test
    void createUpdateDeleteAndDualViewList() throws Exception {
        configurePersonProtection();
        List<String> sqls = new ArrayList<>();
        PlaygroundPersonService recording = new PlaygroundPersonService(
                allowedProperties(), recording(dataSource, sqls), null);

        PersonRequest create = validPerson();
        ApiResponse<Map<String, Object>> created = recording.create(create);
        assertTrue(created.isSuccess(), created.getMessage());
        Long id = ((Number) created.getData().get("id")).longValue();
        assertNotNull(id);

        @SuppressWarnings("unchecked")
        Map<String, Object> createdRow = (Map<String, Object>) created.getData().get("row");
        assertEquals("13800138000", createdRow.get("phone"));

        PersonRequest businessList = new PersonRequest();
        businessList.setView("business");
        ApiResponse<Map<String, Object>> business = recording.list(businessList);
        assertTrue(business.isSuccess());
        assertEquals(1, ((List<?>) business.getData().get("rows")).size());
        @SuppressWarnings("unchecked")
        Map<String, Object> businessRow = (Map<String, Object>) ((List<?>) business.getData().get("rows")).get(0);
        assertEquals("13800138000", businessRow.get("phone"));

        PersonRequest rawList = new PersonRequest();
        rawList.setView("raw");
        ApiResponse<Map<String, Object>> raw = recording.list(rawList);
        assertTrue(raw.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> rawRow = (Map<String, Object>) ((List<?>) raw.getData().get("rows")).get(0);
        assertNotEquals("13800138000", rawRow.get("phone"));
        assertNotEquals("110101199001011234", rawRow.get("idCard"));

        assertTrue(sqls.stream().anyMatch(sql -> sql.contains("SECURT_SKIP") && sql.contains("INSERT")));
        assertTrue(sqls.stream().filter(sql -> sql.contains("SELECT")).allMatch(sql -> sql.contains("SECURT_SKIP")));

        PersonRequest update = validPerson();
        update.setId(id);
        update.setPhone("13900139000");
        assertTrue(recording.update(update).isSuccess());

        PersonRequest delete = new PersonRequest();
        delete.setId(id);
        assertTrue(recording.delete(delete).isSuccess());
        assertEquals(0L, rowCount());
    }

    @Test
    void createUnderJdbcModeDoesNotDoubleEncrypt() {
        configurePersonProtection();
        EncryptModeHolder.setMode(FieldEncryptorProperties.Mode.JDBC);
        assertTrue(service.create(validPerson()).isSuccess());

        PersonRequest rawList = new PersonRequest();
        rawList.setView("raw");
        @SuppressWarnings("unchecked")
        Map<String, Object> rawRow = (Map<String, Object>) ((List<?>) service.list(rawList).getData().get("rows")).get(0);
        assertEquals("13800138000" + PrefixEncryptStrategy.PREFIX, rawRow.get("phone"));
        assertEquals("110101199001011234" + PrefixEncryptStrategy.PREFIX, rawRow.get("idCard"));
    }

    @Test
    void listFiltersByNamePhoneAndIdCardEquality() {
        configurePersonProtection();
        service.create(validPerson());
        PersonRequest other = validPerson();
        other.setName("李四");
        other.setPhone("13700137000");
        other.setIdCard("110101199101011235");
        service.create(other);

        PersonRequest byPhone = new PersonRequest();
        byPhone.setView("business");
        byPhone.setPhone("13800138000");
        List<?> hit = (List<?>) service.list(byPhone).getData().get("rows");
        assertEquals(1, hit.size());

        PersonRequest miss = new PersonRequest();
        miss.setView("business");
        miss.setPhone("10000000000");
        assertEquals(0, ((List<?>) service.list(miss).getData().get("rows")).size());

        PersonRequest byIdCard = new PersonRequest();
        byIdCard.setView("business");
        byIdCard.setIdCard("110101199101011235");
        assertEquals(1, ((List<?>) service.list(byIdCard).getData().get("rows")).size());
    }

    @Test
    void updateAndDeleteReportMissingId() {
        configurePersonProtection();
        PersonRequest update = validPerson();
        update.setId(999L);
        assertEquals(404, service.update(update).getCode());

        PersonRequest delete = new PersonRequest();
        delete.setId(999L);
        assertEquals(404, service.delete(delete).getCode());
    }

    @Test
    void listRejectsInvalidView() {
        PersonRequest request = new PersonRequest();
        request.setView("cipher");
        assertEquals(400, service.list(request).getCode());
    }

    private void configurePersonProtection() {
        FieldEncryptorProperties core = new FieldEncryptorProperties();
        core.setEnable(true);
        core.setDigestHmacKey("playground-person-ut-secret");
        core.setDigestStrategy(HmacSha256DigestStrategy.class.getName());
        FieldEncryptorProperties.SkipCommentConfig skip =
                new FieldEncryptorProperties.SkipCommentConfig();
        skip.setEnable(true);
        skip.setToken("SECURT_SKIP");
        core.setSkipComment(skip);
        ConfigInitializer.initialize(core);
        StrategyCache.registerStrategy(PrefixEncryptStrategy.class, new PrefixEncryptStrategy());
        StrategyCache.registerStrategy(HmacSha256DigestStrategy.class,
                new HmacSha256DigestStrategy(core.getDigestHmacKey()));

        Map<String, Class<? extends FieldEncryptorStrategy>> fields = new HashMap<>();
        fields.put("phone", PrefixEncryptStrategy.class);
        fields.put("id_card", PrefixEncryptStrategy.class);
        TableConfigRegistry.registerTable("default", "playground_person", fields);
        DigestConfigRegistry.register("default", "playground_person", Collections.singletonList(
                new ResolvedDigestRule("playground_person",
                        java.util.Arrays.asList("phone", "id_card"),
                        "row_digest", HmacSha256DigestStrategy.class,
                        FieldEncryptorProperties.PartialUpdate.RELOAD, true,
                        FieldEncryptorProperties.FailurePolicy.FAIL_FAST)));
    }

    private PersonRequest validPerson() {
        PersonRequest request = new PersonRequest();
        request.setName("张三");
        request.setPhone("13800138000");
        request.setIdCard("110101199001011234");
        request.setAge(28);
        return request;
    }

    private PlaygroundProperties allowedProperties() {
        PlaygroundProperties properties = new PlaygroundProperties();
        properties.setAllowedTables(Collections.singletonList("playground_person"));
        return properties;
    }

    private long rowCount() throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM playground_person")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private DataSource memoryDs(String name) {
        String url = "jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
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

    private DataSource recording(DataSource delegate, List<String> sqls) {
        return new DataSource() {
            @Override public Connection getConnection() throws java.sql.SQLException {
                return proxy(delegate.getConnection(), sqls);
            }
            @Override public Connection getConnection(String username, String password)
                    throws java.sql.SQLException {
                return proxy(delegate.getConnection(username, password), sqls);
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

    private Connection proxy(Connection connection, List<String> sqls) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class[]{Connection.class},
                (proxy, method, args) -> {
                    try {
                        if ("prepareStatement".equals(method.getName()) && args != null && args.length >= 1) {
                            sqls.add(String.valueOf(args[0]));
                        }
                        return method.invoke(connection, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    public static final class PrefixEncryptStrategy implements FieldEncryptorStrategy {
        static final String PREFIX = "(加密)";

        @Override
        public String encryption(String oldValue) {
            return oldValue + PREFIX;
        }

        @Override
        public String decryption(String oldValue) {
            return oldValue == null ? null : oldValue.replace(PREFIX, "");
        }
    }
}
