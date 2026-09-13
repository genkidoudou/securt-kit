# Review Package Task 5
BASE: fcdb133a1e3c6cfeceace6ed274af1f4d5a95581
HEAD: dc969fc4c34a7d7ae0678658a0172d0b3a2b382f
## Commits
dc969fc feat(digest): add DigestService for compute and verify

## Stat
 .../github/hexlodev/core/digest/DigestService.java | 238 +++++++++++++++++++++
 .../core/exception/DigestMismatchException.java    |  15 ++
 .../hexlodev/core/digest/DigestServiceTest.java    | 167 +++++++++++++++
 3 files changed, 420 insertions(+)

## Diff
diff --git a/securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestService.java b/securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestService.java
new file mode 100644
index 0000000..d32079a
--- /dev/null
+++ b/securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestService.java
@@ -0,0 +1,238 @@
+package io.github.hexlodev.core.digest;
+
+import io.github.hexlodev.core.cache.StrategyCache;
+import io.github.hexlodev.core.config.DigestConfigRegistry;
+import io.github.hexlodev.core.config.FieldEncryptorProperties;
+import io.github.hexlodev.core.crypto.FieldCryptoService;
+import io.github.hexlodev.core.crypto.FieldCryptoServiceHolder;
+import io.github.hexlodev.core.exception.DigestMismatchException;
+import io.github.hexlodev.core.exception.SecurtKitException;
+import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;
+import lombok.extern.slf4j.Slf4j;
+
+import java.sql.Connection;
+import java.sql.PreparedStatement;
+import java.sql.ResultSet;
+import java.sql.SQLException;
+import java.util.ArrayList;
+import java.util.Collections;
+import java.util.LinkedHashMap;
+import java.util.List;
+import java.util.Locale;
+import java.util.Map;
+
+/**
+ * 摘要计算、部分更新补读与读侧验签的统一入口。
+ */
+@Slf4j
+public class DigestService {
+
+    public Map<String, String> computeTargetDigests(
+            String table,
+            String datasourceId,
+            Map<String, String> availablePlainByColumn,
+            boolean insert,
+            Connection connForReload,
+            String reloadWhereSql,
+            List<Object> reloadWhereParams) {
+        List<ResolvedDigestRule> rules = DigestConfigRegistry.getRules(table, datasourceId);
+        if (rules.isEmpty()) {
+            return Collections.emptyMap();
+        }
+
+        Map<String, String> available = availablePlainByColumn == null
+                ? Collections.<String, String>emptyMap()
+                : availablePlainByColumn;
+        Map<String, String> targetDigests = new LinkedHashMap<String, String>();
+        for (ResolvedDigestRule rule : rules) {
+            List<String> missing = findMissing(rule.getSourceFields(), available);
+            Map<String, String> reloaded = Collections.emptyMap();
+            if (!missing.isEmpty()) {
+                FieldEncryptorProperties.PartialUpdate policy = rule.getPartialUpdate();
+                if (policy == FieldEncryptorProperties.PartialUpdate.SKIP) {
+                    continue;
+                }
+                if (policy == FieldEncryptorProperties.PartialUpdate.FAIL) {
+                    throw partialUpdateFailure(table, rule, missing);
+                }
+                if (insert) {
+                    throw new SecurtKitException("INSERT cannot RELOAD missing digest source fields for target "
+                            + rule.getTargetField());
+                }
+                reloaded = reloadMissingValues(
+                        table, datasourceId, missing, connForReload, reloadWhereSql, reloadWhereParams);
+            }
+
+            LinkedHashMap<String, String> ordered = new LinkedHashMap<String, String>();
+            for (String sourceField : rule.getSourceFields()) {
+                Lookup value = lookupIgnoreCase(available, sourceField);
+                ordered.put(sourceField, value.found
+                        ? value.value
+                        : lookupIgnoreCase(reloaded, sourceField).value);
+            }
+            FieldEncryptorStrategy strategy = StrategyCache.getStrategy(rule.getStrategyClass());
+            String digest = strategy.digest(ordered);
+            if (digest == null) {
+                throw new SecurtKitException("Digest strategy returned null for target "
+                        + rule.getTargetField());
+            }
+            targetDigests.put(rule.getTargetField().toLowerCase(Locale.ROOT), digest);
+        }
+        return targetDigests;
+    }
+
+    public void verifyRow(String table,
+                          String datasourceId,
+                          Map<String, String> plainByColumn,
+                          Map<String, String> digestByTarget) {
+        List<ResolvedDigestRule> rules = DigestConfigRegistry.getRules(table, datasourceId);
+        for (ResolvedDigestRule rule : rules) {
+            if (!rule.isVerifyOnRead()) {
+                continue;
+            }
+            Lookup digest = lookupIgnoreCase(digestByTarget, rule.getTargetField());
+            if (!digest.found) {
+                log.debug("Digest target column is unavailable: table={}, target={}",
+                        table, rule.getTargetField());
+                continue;
+            }
+            if (digest.value == null) {
+                log.warn("Digest target column is null: table={}, target={}",
+                        table, rule.getTargetField());
+                continue;
+            }
+
+            List<String> missing = findMissing(rule.getSourceFields(), plainByColumn);
+            if (!missing.isEmpty()) {
+                log.debug("Digest source columns are unavailable: table={}, target={}, missing={}",
+                        table, rule.getTargetField(), missing);
+                continue;
+            }
+
+            LinkedHashMap<String, String> ordered = new LinkedHashMap<String, String>();
+            for (String sourceField : rule.getSourceFields()) {
+                ordered.put(sourceField, lookupIgnoreCase(plainByColumn, sourceField).value);
+            }
+            FieldEncryptorStrategy strategy = StrategyCache.getStrategy(rule.getStrategyClass());
+            if (!strategy.verifyDigest(ordered, digest.value)) {
+                handleMismatch(table, rule);
+            }
+        }
+    }
+
+    private Map<String, String> reloadMissingValues(
+            String table,
+            String datasourceId,
+            List<String> missing,
+            Connection connection,
+            String reloadWhereSql,
+            List<Object> reloadWhereParams) {
+        if (connection == null || reloadWhereSql == null || reloadWhereSql.trim().isEmpty()) {
+            throw new SecurtKitException("RELOAD requires a Connection and WHERE condition");
+        }
+        validateIdentifier(table, true);
+        for (String column : missing) {
+            validateIdentifier(column, false);
+        }
+
+        String condition = reloadWhereSql.trim();
+        if (condition.regionMatches(true, 0, "where ", 0, 6)) {
+            condition = condition.substring(6).trim();
+        }
+        if (condition.isEmpty()) {
+            throw new SecurtKitException("RELOAD requires a non-empty WHERE condition");
+        }
+
+        StringBuilder sql = new StringBuilder("SELECT ");
+        for (int i = 0; i < missing.size(); i++) {
+            if (i > 0) {
+                sql.append(", ");
+            }
+            sql.append(missing.get(i));
+        }
+        sql.append(" FROM ").append(table).append(" WHERE ").append(condition);
+
+        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
+            List<Object> params = reloadWhereParams == null
+                    ? Collections.emptyList()
+                    : reloadWhereParams;
+            for (int i = 0; i < params.size(); i++) {
+                statement.setObject(i + 1, params.get(i));
+            }
+            try (ResultSet resultSet = statement.executeQuery()) {
+                if (!resultSet.next()) {
+                    throw new SecurtKitException("RELOAD found no row for digest calculation");
+                }
+                Map<String, String> values = new LinkedHashMap<String, String>();
+                FieldCryptoService cryptoService = FieldCryptoServiceHolder.get();
+                for (String column : missing) {
+                    String stored = resultSet.getString(column);
+                    values.put(column, cryptoService.decrypt(table, column, stored, datasourceId));
+                }
+                return values;
+            }
+        } catch (SQLException e) {
+            throw new SecurtKitException("Failed to RELOAD digest source fields", e);
+        }
+    }
+
+    private void handleMismatch(String table, ResolvedDigestRule rule) {
+        FieldEncryptorProperties.FailurePolicy policy = rule.getFailurePolicy();
+        if (policy == FieldEncryptorProperties.FailurePolicy.FAIL_FAST) {
+            throw new DigestMismatchException("Digest verification failed: table=" + table
+                    + ", target=" + rule.getTargetField());
+        }
+        if (policy == FieldEncryptorProperties.FailurePolicy.SKIP) {
+            log.debug("Digest mismatch skipped: table={}, target={}", table, rule.getTargetField());
+            return;
+        }
+        log.warn("Digest verification failed, returning row by fallback policy: table={}, target={}",
+                table, rule.getTargetField());
+    }
+
+    private SecurtKitException partialUpdateFailure(
+            String table, ResolvedDigestRule rule, List<String> missing) {
+        return new SecurtKitException("Missing digest source fields for partial update: table="
+                + table + ", target=" + rule.getTargetField() + ", missing=" + missing);
+    }
+
+    private List<String> findMissing(List<String> required, Map<String, String> values) {
+        List<String> missing = new ArrayList<String>();
+        for (String column : required) {
+            if (!lookupIgnoreCase(values, column).found) {
+                missing.add(column);
+            }
+        }
+        return missing;
+    }
+
+    private Lookup lookupIgnoreCase(Map<String, String> values, String column) {
+        if (values != null) {
+            for (Map.Entry<String, String> entry : values.entrySet()) {
+                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(column)) {
+                    return new Lookup(true, entry.getValue());
+                }
+            }
+        }
+        return Lookup.NOT_FOUND;
+    }
+
+    private void validateIdentifier(String identifier, boolean qualified) {
+        String pattern = qualified ? "[A-Za-z0-9_$.]+" : "[A-Za-z0-9_$]+";
+        if (identifier == null || !identifier.matches(pattern)) {
+            throw new SecurtKitException("Unsafe SQL identifier for digest RELOAD");
+        }
+    }
+
+    private static final class Lookup {
+        private static final Lookup NOT_FOUND = new Lookup(false, null);
+
+        private final boolean found;
+        private final String value;
+
+        private Lookup(boolean found, String value) {
+            this.found = found;
+            this.value = value;
+        }
+    }
+}
diff --git a/securt-kit-core/src/main/java/io/github/hexlodev/core/exception/DigestMismatchException.java b/securt-kit-core/src/main/java/io/github/hexlodev/core/exception/DigestMismatchException.java
new file mode 100644
index 0000000..7bbe880
--- /dev/null
+++ b/securt-kit-core/src/main/java/io/github/hexlodev/core/exception/DigestMismatchException.java
@@ -0,0 +1,15 @@
+package io.github.hexlodev.core.exception;
+
+/**
+ * 摘要验签失败异常。
+ */
+public class DigestMismatchException extends SecurtKitException {
+
+    public DigestMismatchException(String message) {
+        super(message);
+    }
+
+    public DigestMismatchException(String message, Throwable cause) {
+        super(message, cause);
+    }
+}
diff --git a/securt-kit-core/src/test/java/io/github/hexlodev/core/digest/DigestServiceTest.java b/securt-kit-core/src/test/java/io/github/hexlodev/core/digest/DigestServiceTest.java
new file mode 100644
index 0000000..9eade35
--- /dev/null
+++ b/securt-kit-core/src/test/java/io/github/hexlodev/core/digest/DigestServiceTest.java
@@ -0,0 +1,167 @@
+package io.github.hexlodev.core.digest;
+
+import io.github.hexlodev.core.cache.StrategyCache;
+import io.github.hexlodev.core.config.DigestConfigRegistry;
+import io.github.hexlodev.core.config.FieldEncryptorProperties;
+import io.github.hexlodev.core.exception.DigestMismatchException;
+import io.github.hexlodev.core.exception.SecurtKitException;
+import io.github.hexlodev.core.strategy.HmacSha256DigestStrategy;
+import org.junit.jupiter.api.BeforeEach;
+import org.junit.jupiter.api.Test;
+
+import java.util.Arrays;
+import java.util.Collections;
+import java.util.LinkedHashMap;
+import java.util.Map;
+
+import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
+import static org.junit.jupiter.api.Assertions.assertEquals;
+import static org.junit.jupiter.api.Assertions.assertThrows;
+import static org.junit.jupiter.api.Assertions.assertTrue;
+
+class DigestServiceTest {
+
+    private static final String TABLE = "user_account";
+    private static final String DATASOURCE = "test";
+
+    private final DigestService service = new DigestService();
+    private HmacSha256DigestStrategy strategy;
+
+    @BeforeEach
+    void setUp() {
+        DigestConfigRegistry.clear();
+        StrategyCache.clear();
+        strategy = new HmacSha256DigestStrategy("test-secret");
+        StrategyCache.registerStrategy(HmacSha256DigestStrategy.class, strategy);
+        registerRule(FieldEncryptorProperties.PartialUpdate.SKIP, true,
+                FieldEncryptorProperties.FailurePolicy.FALLBACK);
+    }
+
+    @Test
+    void computesDigestInConfiguredSourceOrderIgnoringColumnCase() {
+        Map<String, String> available = new LinkedHashMap<String, String>();
+        available.put("ID_CARD", "110101199001011234");
+        available.put("Phone", "13800138000");
+
+        Map<String, String> actual = service.computeTargetDigests(
+                TABLE.toUpperCase(), DATASOURCE, available, false, null, null, null);
+
+        Map<String, String> ordered = new LinkedHashMap<String, String>();
+        ordered.put("phone", "13800138000");
+        ordered.put("id_card", "110101199001011234");
+        assertEquals(Collections.singletonMap("row_digest", strategy.digest(ordered)), actual);
+    }
+
+    @Test
+    void skipWhenPartial() {
+        Map<String, String> partial = Collections.singletonMap("phone", "13800138000");
+
+        Map<String, String> actual = service.computeTargetDigests(
+                TABLE, DATASOURCE, partial, false, null, null, null);
+
+        assertTrue(actual.isEmpty());
+    }
+
+    @Test
+    void failWhenPartialAndFailPolicy() {
+        registerRule(FieldEncryptorProperties.PartialUpdate.FAIL, true,
+                FieldEncryptorProperties.FailurePolicy.FALLBACK);
+
+        assertThrows(SecurtKitException.class, () -> service.computeTargetDigests(
+                TABLE, DATASOURCE, Collections.singletonMap("phone", "13800138000"),
+                false, null, null, null));
+    }
+
+    @Test
+    void insertCannotReloadMissingSources() {
+        registerRule(FieldEncryptorProperties.PartialUpdate.RELOAD, true,
+                FieldEncryptorProperties.FailurePolicy.FALLBACK);
+
+        SecurtKitException error = assertThrows(SecurtKitException.class,
+                () -> service.computeTargetDigests(
+                        TABLE, DATASOURCE, Collections.singletonMap("phone", "13800138000"),
+                        true, null, null, null));
+
+        assertTrue(error.getMessage().contains("INSERT cannot RELOAD"));
+    }
+
+    @Test
+    void reloadWithoutConnectionOrWhereFails() {
+        registerRule(FieldEncryptorProperties.PartialUpdate.RELOAD, true,
+                FieldEncryptorProperties.FailurePolicy.FALLBACK);
+
+        assertThrows(SecurtKitException.class, () -> service.computeTargetDigests(
+                TABLE, DATASOURCE, Collections.singletonMap("phone", "13800138000"),
+                false, null, null, Collections.emptyList()));
+    }
+
+    @Test
+    void verifyMismatchFallbackDoesNotThrow() {
+        assertDoesNotThrow(() -> service.verifyRow(
+                TABLE, DATASOURCE, completePlainValues(), Collections.singletonMap("row_digest", "wrong")));
+    }
+
+    @Test
+    void verifyMismatchRetryDoesNotThrow() {
+        registerRule(FieldEncryptorProperties.PartialUpdate.SKIP, true,
+                FieldEncryptorProperties.FailurePolicy.RETRY);
+
+        assertDoesNotThrow(() -> service.verifyRow(
+                TABLE, DATASOURCE, completePlainValues(), Collections.singletonMap("row_digest", "wrong")));
+    }
+
+    @Test
+    void verifyMismatchSkipDoesNotThrow() {
+        registerRule(FieldEncryptorProperties.PartialUpdate.SKIP, true,
+                FieldEncryptorProperties.FailurePolicy.SKIP);
+
+        assertDoesNotThrow(() -> service.verifyRow(
+                TABLE, DATASOURCE, completePlainValues(), Collections.singletonMap("row_digest", "wrong")));
+    }
+
+    @Test
+    void verifyMismatchFailFastThrows() {
+        registerRule(FieldEncryptorProperties.PartialUpdate.SKIP, true,
+                FieldEncryptorProperties.FailurePolicy.FAIL_FAST);
+
+        assertThrows(DigestMismatchException.class, () -> service.verifyRow(
+                TABLE, DATASOURCE, completePlainValues(), Collections.singletonMap("row_digest", "wrong")));
+    }
+
+    @Test
+    void verifySkipsDisabledOrIncompleteRows() {
+        registerRule(FieldEncryptorProperties.PartialUpdate.SKIP, false,
+                FieldEncryptorProperties.FailurePolicy.FAIL_FAST);
+        assertDoesNotThrow(() -> service.verifyRow(
+                TABLE, DATASOURCE, completePlainValues(), Collections.singletonMap("row_digest", "wrong")));
+
+        registerRule(FieldEncryptorProperties.PartialUpdate.SKIP, true,
+                FieldEncryptorProperties.FailurePolicy.FAIL_FAST);
+        assertDoesNotThrow(() -> service.verifyRow(
+                TABLE, DATASOURCE, Collections.singletonMap("phone", "13800138000"),
+                Collections.singletonMap("row_digest", "wrong")));
+        assertDoesNotThrow(() -> service.verifyRow(
+                TABLE, DATASOURCE, completePlainValues(), Collections.<String, String>emptyMap()));
+    }
+
+    private Map<String, String> completePlainValues() {
+        Map<String, String> values = new LinkedHashMap<String, String>();
+        values.put("phone", "13800138000");
+        values.put("id_card", "110101199001011234");
+        return values;
+    }
+
+    private void registerRule(FieldEncryptorProperties.PartialUpdate partialUpdate,
+                              boolean verifyOnRead,
+                              FieldEncryptorProperties.FailurePolicy failurePolicy) {
+        ResolvedDigestRule rule = new ResolvedDigestRule(
+                TABLE,
+                Arrays.asList("phone", "id_card"),
+                "ROW_DIGEST",
+                HmacSha256DigestStrategy.class,
+                partialUpdate,
+                verifyOnRead,
+                failurePolicy);
+        DigestConfigRegistry.register(DATASOURCE, TABLE, Collections.singletonList(rule));
+    }
+}

