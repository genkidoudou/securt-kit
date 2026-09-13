# Review Package Task 7
BASE: 30f5127e44161b9fb9298327b3c77a9ef8ead48c
HEAD: b62cb995cb5310ca031537ec73ba76557a000913
## Commits
b62cb99 feat(digest): optional verify-on-read for JDBC ResultSet

## Stat
 .../hexlodev/core/digest/DigestReadSupport.java    | 50 ++++++++++++++++
 .../core/interceptor/ResultSetDecryptingProxy.java | 69 ++++++++++++++++++++++
 .../core/digest/DigestReadSupportTest.java         | 69 ++++++++++++++++++++++
 3 files changed, 188 insertions(+)

## Diff
diff --git a/securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestReadSupport.java b/securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestReadSupport.java
new file mode 100644
index 0000000..2e9e28c
--- /dev/null
+++ b/securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestReadSupport.java
@@ -0,0 +1,50 @@
+package io.github.hexlodev.core.digest;
+
+import io.github.hexlodev.core.config.DigestConfigRegistry;
+
+import java.util.Collections;
+import java.util.LinkedHashSet;
+import java.util.List;
+import java.util.Map;
+import java.util.Set;
+
+/**
+ * Bridges decrypted JDBC result rows to digest verification.
+ */
+public final class DigestReadSupport {
+
+    private DigestReadSupport() {
+    }
+
+    public static Set<String> requiredColumns(Set<String> tables, String datasourceId) {
+        if (tables == null || tables.isEmpty()) {
+            return Collections.emptySet();
+        }
+        Set<String> columns = new LinkedHashSet<String>();
+        for (String table : tables) {
+            List<ResolvedDigestRule> rules = DigestConfigRegistry.getRules(table, datasourceId);
+            for (ResolvedDigestRule rule : rules) {
+                if (rule.isVerifyOnRead()) {
+                    columns.addAll(rule.getSourceFields());
+                    columns.add(rule.getTargetField());
+                }
+            }
+        }
+        return columns;
+    }
+
+    public static void verifyResultRow(Set<String> tables,
+                                       String datasourceId,
+                                       Map<String, String> columnValues) {
+        if (tables == null || tables.isEmpty()) {
+            return;
+        }
+        Map<String, String> values = columnValues == null
+                ? Collections.<String, String>emptyMap()
+                : columnValues;
+        DigestService service = new DigestService();
+        for (String table : tables) {
+            service.verifyRow(table, datasourceId, values, values);
+        }
+    }
+}
diff --git a/securt-kit-core/src/main/java/io/github/hexlodev/core/interceptor/ResultSetDecryptingProxy.java b/securt-kit-core/src/main/java/io/github/hexlodev/core/interceptor/ResultSetDecryptingProxy.java
index ab72b5f..9faa43e 100644
--- a/securt-kit-core/src/main/java/io/github/hexlodev/core/interceptor/ResultSetDecryptingProxy.java
+++ b/securt-kit-core/src/main/java/io/github/hexlodev/core/interceptor/ResultSetDecryptingProxy.java
@@ -1,10 +1,11 @@
 package io.github.hexlodev.core.interceptor;
 
 import cn.hutool.core.lang.Pair;
 import io.github.hexlodev.core.TableCache;
 import io.github.hexlodev.core.cache.StrategyCache;
+import io.github.hexlodev.core.digest.DigestReadSupport;
 import io.github.hexlodev.core.exception.EncryptionHandler;
 import io.github.hexlodev.core.logging.SqlLogger;
 import io.github.hexlodev.core.parser.SecurtkitUtils;
 import io.github.hexlodev.core.parser.dto.ColumnTableDto;
 import io.github.hexlodev.core.parser.dto.FieldEncryptorInfoDto;
@@ -54,10 +55,12 @@ final class ResultSetDecryptingProxy implements InvocationHandler {
     /**
      * 数据源标识（多数据源场景）
      */
     private final String datasourceId;
 
+    private boolean currentRowVerified = true;
+
     private ResultSetDecryptingProxy(ResultSet delegate, Set<String> tables, Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> pair, String sql, String datasourceId) {
         this.delegate = delegate;
         this.tables = tables == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(tables));
         this.pair = pair;
         this.sql = sql;
@@ -111,13 +114,21 @@ final class ResultSetDecryptingProxy implements InvocationHandler {
 
     @Override
     public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
         String name = method.getName();
 
+        if (isColumnRead(name, args)) {
+            verifyCurrentRowOnce();
+        }
+
         // 优先执行原始调用
         Object result = method.invoke(delegate, args);
 
+        if ("next".equals(name)) {
+            currentRowVerified = !Boolean.TRUE.equals(result);
+            return result;
+        }
         if (result == null) {
             return null;
         }
         if (!SecurtkitUtils.needEncrypt(this.tables) && null == this.pair) {
             return result;
@@ -207,10 +218,68 @@ final class ResultSetDecryptingProxy implements InvocationHandler {
         }
 
         return result;
     }
 
+    private boolean isColumnRead(String methodName, Object[] args) {
+        return methodName.startsWith("get")
+                && args != null
+                && args.length > 0
+                && (args[0] instanceof Integer || args[0] instanceof String);
+    }
+
+    private void verifyCurrentRowOnce() {
+        if (currentRowVerified) {
+            return;
+        }
+        Set<String> requiredColumns = DigestReadSupport.requiredColumns(tables, datasourceId);
+        if (requiredColumns.isEmpty()) {
+            currentRowVerified = true;
+            return;
+        }
+
+        Map<String, String> row = new LinkedHashMap<String, String>();
+        try {
+            ResultSetMetaData metadata = getMetaData();
+            for (int index = 1; index <= metadata.getColumnCount(); index++) {
+                String label = metadata.getColumnLabel(index);
+                String columnName = metadata.getColumnName(index);
+                if (!containsIgnoreCase(requiredColumns, label)
+                        && !containsIgnoreCase(requiredColumns, columnName)) {
+                    continue;
+                }
+                String key = label == null || label.isEmpty() ? columnName : label;
+                String value = maybeDecryptWithInfo(key, delegate.getString(index));
+                if (label != null && !label.isEmpty()) {
+                    row.put(label, value);
+                }
+                if (columnName != null && !columnName.isEmpty()) {
+                    row.put(columnName, value);
+                }
+            }
+        } catch (SQLException e) {
+            log.debug("Unable to collect result row for digest verification", e);
+            currentRowVerified = true;
+            return;
+        }
+
+        DigestReadSupport.verifyResultRow(tables, datasourceId, row);
+        currentRowVerified = true;
+    }
+
+    private boolean containsIgnoreCase(Set<String> values, String candidate) {
+        if (candidate == null) {
+            return false;
+        }
+        for (String value : values) {
+            if (candidate.equalsIgnoreCase(value)) {
+                return true;
+            }
+        }
+        return false;
+    }
+
     /**
      * 获取 ResultSetMetaData，使用缓存避免重复获取
      * ResultSetMetaData 在 ResultSet 生命周期内不变，可以安全缓存
      * 
      * @return ResultSetMetaData 元数据对象
diff --git a/securt-kit-core/src/test/java/io/github/hexlodev/core/digest/DigestReadSupportTest.java b/securt-kit-core/src/test/java/io/github/hexlodev/core/digest/DigestReadSupportTest.java
new file mode 100644
index 0000000..2b035d1
--- /dev/null
+++ b/securt-kit-core/src/test/java/io/github/hexlodev/core/digest/DigestReadSupportTest.java
@@ -0,0 +1,69 @@
+package io.github.hexlodev.core.digest;
+
+import io.github.hexlodev.core.cache.StrategyCache;
+import io.github.hexlodev.core.config.DigestConfigRegistry;
+import io.github.hexlodev.core.config.FieldEncryptorProperties;
+import io.github.hexlodev.core.exception.DigestMismatchException;
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
+import static org.junit.jupiter.api.Assertions.assertThrows;
+
+class DigestReadSupportTest {
+
+    private static final String TABLE = "user_account";
+    private static final String DATASOURCE = "test";
+
+    @BeforeEach
+    void setUp() {
+        DigestConfigRegistry.clear();
+        StrategyCache.clear();
+        StrategyCache.registerStrategy(
+                HmacSha256DigestStrategy.class,
+                new HmacSha256DigestStrategy("test-secret"));
+    }
+
+    @Test
+    void fallbackReturnsRowWhenDigestDoesNotMatch() {
+        registerRule(FieldEncryptorProperties.FailurePolicy.FALLBACK);
+
+        assertDoesNotThrow(() -> DigestReadSupport.verifyResultRow(
+                Collections.singleton(TABLE), DATASOURCE, rowWithDigest("wrong")));
+    }
+
+    @Test
+    void failFastThrowsWhenDigestDoesNotMatch() {
+        registerRule(FieldEncryptorProperties.FailurePolicy.FAIL_FAST);
+
+        assertThrows(DigestMismatchException.class, () -> DigestReadSupport.verifyResultRow(
+                Collections.singleton(TABLE), DATASOURCE, rowWithDigest("wrong")));
+    }
+
+    private Map<String, String> rowWithDigest(String digest) {
+        Map<String, String> row = new LinkedHashMap<String, String>();
+        row.put("Phone", "13800138000");
+        row.put("ID_CARD", "110101199001011234");
+        row.put("ROW_DIGEST", digest);
+        return row;
+    }
+
+    private void registerRule(FieldEncryptorProperties.FailurePolicy failurePolicy) {
+        ResolvedDigestRule rule = new ResolvedDigestRule(
+                TABLE,
+                Arrays.asList("phone", "id_card"),
+                "row_digest",
+                HmacSha256DigestStrategy.class,
+                FieldEncryptorProperties.PartialUpdate.SKIP,
+                true,
+                failurePolicy);
+        DigestConfigRegistry.register(
+                DATASOURCE, TABLE, Collections.singletonList(rule));
+    }
+}

