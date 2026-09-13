package io.github.genkidoudou.core.digest;

import io.github.genkidoudou.core.cache.StrategyCache;
import io.github.genkidoudou.core.config.DigestConfigRegistry;
import io.github.genkidoudou.core.config.FieldEncryptorProperties;
import io.github.genkidoudou.core.crypto.FieldCryptoService;
import io.github.genkidoudou.core.crypto.FieldCryptoServiceHolder;
import io.github.genkidoudou.core.exception.DigestMismatchException;
import io.github.genkidoudou.core.exception.SecurtKitException;
import io.github.genkidoudou.core.strategy.HmacSha256DigestStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DigestServiceTest {

    private static final String TABLE = "user_account";
    private static final String DATASOURCE = "test";

    private final DigestService service = new DigestService();
    private HmacSha256DigestStrategy strategy;

    @BeforeEach
    void setUp() {
        DigestConfigRegistry.clear();
        StrategyCache.clear();
        strategy = new HmacSha256DigestStrategy("test-secret");
        StrategyCache.registerStrategy(HmacSha256DigestStrategy.class, strategy);
        registerRule(FieldEncryptorProperties.PartialUpdate.SKIP, true,
                FieldEncryptorProperties.FailurePolicy.FALLBACK);
    }

    @Test
    void computesDigestInConfiguredSourceOrderIgnoringColumnCase() {
        Map<String, String> available = new LinkedHashMap<String, String>();
        available.put("ID_CARD", "110101199001011234");
        available.put("Phone", "13800138000");

        Map<String, String> actual = service.computeTargetDigests(
                TABLE.toUpperCase(), DATASOURCE, available, false, null, null, null);

        Map<String, String> ordered = new LinkedHashMap<String, String>();
        ordered.put("phone", "13800138000");
        ordered.put("id_card", "110101199001011234");
        assertEquals(Collections.singletonMap("row_digest", strategy.digest(ordered)), actual);
    }

    @Test
    void skipWhenPartial() {
        Map<String, String> partial = Collections.singletonMap("phone", "13800138000");

        Map<String, String> actual = service.computeTargetDigests(
                TABLE, DATASOURCE, partial, false, null, null, null);

        assertTrue(actual.isEmpty());
    }

    @Test
    void failWhenPartialAndFailPolicy() {
        registerRule(FieldEncryptorProperties.PartialUpdate.FAIL, true,
                FieldEncryptorProperties.FailurePolicy.FALLBACK);

        assertThrows(SecurtKitException.class, () -> service.computeTargetDigests(
                TABLE, DATASOURCE, Collections.singletonMap("phone", "13800138000"),
                false, null, null, null));
    }

    @Test
    void insertCannotReloadMissingSources() {
        registerRule(FieldEncryptorProperties.PartialUpdate.RELOAD, true,
                FieldEncryptorProperties.FailurePolicy.FALLBACK);

        SecurtKitException error = assertThrows(SecurtKitException.class,
                () -> service.computeTargetDigests(
                        TABLE, DATASOURCE, Collections.singletonMap("phone", "13800138000"),
                        true, null, null, null));

        assertTrue(error.getMessage().contains("INSERT cannot RELOAD"));
    }

    @Test
    void reloadWithoutConnectionOrWhereFails() {
        registerRule(FieldEncryptorProperties.PartialUpdate.RELOAD, true,
                FieldEncryptorProperties.FailurePolicy.FALLBACK);

        assertThrows(SecurtKitException.class, () -> service.computeTargetDigests(
                TABLE, DATASOURCE, Collections.singletonMap("phone", "13800138000"),
                false, null, null, Collections.emptyList()));
    }

    @Test
    void verifyMismatchFallbackDoesNotThrow() {
        assertDoesNotThrow(() -> service.verifyRow(
                TABLE, DATASOURCE, completePlainValues(), Collections.singletonMap("row_digest", "wrong")));
    }

    @Test
    void verifyMismatchRetryDoesNotThrow() {
        registerRule(FieldEncryptorProperties.PartialUpdate.SKIP, true,
                FieldEncryptorProperties.FailurePolicy.RETRY);

        assertDoesNotThrow(() -> service.verifyRow(
                TABLE, DATASOURCE, completePlainValues(), Collections.singletonMap("row_digest", "wrong")));
    }

    @Test
    void verifyMismatchSkipDoesNotThrow() {
        registerRule(FieldEncryptorProperties.PartialUpdate.SKIP, true,
                FieldEncryptorProperties.FailurePolicy.SKIP);

        assertDoesNotThrow(() -> service.verifyRow(
                TABLE, DATASOURCE, completePlainValues(), Collections.singletonMap("row_digest", "wrong")));
    }

    @Test
    void verifyMismatchFailFastThrows() {
        registerRule(FieldEncryptorProperties.PartialUpdate.SKIP, true,
                FieldEncryptorProperties.FailurePolicy.FAIL_FAST);

        assertThrows(DigestMismatchException.class, () -> service.verifyRow(
                TABLE, DATASOURCE, completePlainValues(), Collections.singletonMap("row_digest", "wrong")));
    }

    @Test
    void verifySkipsDisabledOrIncompleteRows() {
        registerRule(FieldEncryptorProperties.PartialUpdate.SKIP, false,
                FieldEncryptorProperties.FailurePolicy.FAIL_FAST);
        assertDoesNotThrow(() -> service.verifyRow(
                TABLE, DATASOURCE, completePlainValues(), Collections.singletonMap("row_digest", "wrong")));

        registerRule(FieldEncryptorProperties.PartialUpdate.SKIP, true,
                FieldEncryptorProperties.FailurePolicy.FAIL_FAST);
        assertDoesNotThrow(() -> service.verifyRow(
                TABLE, DATASOURCE, Collections.singletonMap("phone", "13800138000"),
                Collections.singletonMap("row_digest", "wrong")));
        assertDoesNotThrow(() -> service.verifyRow(
                TABLE, DATASOURCE, completePlainValues(), Collections.<String, String>emptyMap()));
    }

    @Test
    void reloadMergesMissingEncryptedColumnFromConnection() throws Exception {
        registerRule(FieldEncryptorProperties.PartialUpdate.RELOAD, true,
                FieldEncryptorProperties.FailurePolicy.FALLBACK);

        FieldCryptoServiceHolder.set(new FieldCryptoService() {
            @Override
            public boolean needEncrypt(String table, String column, String datasourceId) {
                return "id_card".equalsIgnoreCase(column);
            }

            @Override
            public String encrypt(String table, String column, String plain, String datasourceId) {
                return "ENC:" + plain;
            }

            @Override
            public String decrypt(String table, String column, String cipher, String datasourceId) {
                if (cipher != null && cipher.startsWith("ENC:")) {
                    return cipher.substring(4);
                }
                return cipher;
            }
        });

        try {
            Connection connection = reloadConnection("ENC:110101199001011234");
            Map<String, String> available = Collections.singletonMap("phone", "13800138000");
            Map<String, String> actual = service.computeTargetDigests(
                    TABLE, DATASOURCE, available, false, connection, "id = ?",
                    Collections.<Object>singletonList(1L));

            Map<String, String> ordered = new LinkedHashMap<String, String>();
            ordered.put("phone", "13800138000");
            ordered.put("id_card", "110101199001011234");
            assertEquals(Collections.singletonMap("row_digest", strategy.digest(ordered)), actual);
        } finally {
            FieldCryptoServiceHolder.reset();
        }
    }

    private Connection reloadConnection(final String storedIdCard) {
        return (Connection) java.lang.reflect.Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new java.lang.reflect.InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args)
                            throws Throwable {
                        if ("prepareStatement".equals(method.getName())) {
                            return reloadPreparedStatement(storedIdCard);
                        }
                        if ("close".equals(method.getName())) {
                            return null;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    private PreparedStatement reloadPreparedStatement(final String storedIdCard) {
        return (PreparedStatement) java.lang.reflect.Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                new java.lang.reflect.InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args)
                            throws Throwable {
                        String name = method.getName();
                        if ("setObject".equals(name) || "close".equals(name)) {
                            return null;
                        }
                        if ("executeQuery".equals(name)) {
                            return reloadResultSet(storedIdCard);
                        }
                        throw new UnsupportedOperationException(name);
                    }
                });
    }

    private ResultSet reloadResultSet(final String storedIdCard) {
        return (ResultSet) java.lang.reflect.Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                new java.lang.reflect.InvocationHandler() {
                    private boolean consumed;

                    @Override
                    public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args)
                            throws Throwable {
                        String name = method.getName();
                        if ("next".equals(name)) {
                            if (consumed) {
                                return false;
                            }
                            consumed = true;
                            return true;
                        }
                        if ("getString".equals(name)) {
                            return storedIdCard;
                        }
                        if ("close".equals(name)) {
                            return null;
                        }
                        throw new UnsupportedOperationException(name);
                    }
                });
    }

    private Map<String, String> completePlainValues() {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("phone", "13800138000");
        values.put("id_card", "110101199001011234");
        return values;
    }

    private void registerRule(FieldEncryptorProperties.PartialUpdate partialUpdate,
                              boolean verifyOnRead,
                              FieldEncryptorProperties.FailurePolicy failurePolicy) {
        ResolvedDigestRule rule = new ResolvedDigestRule(
                TABLE,
                Arrays.asList("phone", "id_card"),
                "ROW_DIGEST",
                HmacSha256DigestStrategy.class,
                partialUpdate,
                verifyOnRead,
                failurePolicy);
        DigestConfigRegistry.register(DATASOURCE, TABLE, Collections.singletonList(rule));
    }
}
