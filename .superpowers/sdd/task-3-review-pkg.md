# Review Package Task 3 (re-review)
BASE: c6d3246d4d19e41a6270b20361fd940538da3015
HEAD: cedbee6283217f962888f83f04ebb1ee1fa73b06

## Commits
cedbee6 fix(digest): tighten digest target and source-field startup validation
554d0c3 feat(digest): register and validate digest configs at startup


## Stat
 .superpowers/sdd/task-3-report.md                  |  75 +++++++++
 .../hexlodev/core/config/ConfigInitializer.java    | 172 ++++++++++++++++++++
 .../hexlodev/core/config/DigestConfigRegistry.java |  66 ++++++++
 .../hexlodev/core/digest/ResolvedDigestRule.java   |  66 ++++++++
 .../core/config/DigestConfigRegistryTest.java      | 174 +++++++++++++++++++++
 5 files changed, 553 insertions(+)


## Diff (task files only)
diff --git a/securt-kit-core/src/main/java/io/github/hexlodev/core/config/ConfigInitializer.java b/securt-kit-core/src/main/java/io/github/hexlodev/core/config/ConfigInitializer.java
index 4bc9ed9..d3db2d2 100644
--- a/securt-kit-core/src/main/java/io/github/hexlodev/core/config/ConfigInitializer.java
+++ b/securt-kit-core/src/main/java/io/github/hexlodev/core/config/ConfigInitializer.java
@@ -1,17 +1,20 @@
 package io.github.hexlodev.core.config;
 
 import cn.hutool.core.collection.CollectionUtil;
 import cn.hutool.core.util.ClassUtil;
 import cn.hutool.core.util.StrUtil;
 import io.github.hexlodev.core.cache.StrategyCache;
+import io.github.hexlodev.core.digest.ResolvedDigestRule;
+import io.github.hexlodev.core.exception.ConfigurationException;
 import io.github.hexlodev.core.exception.EncryptionHandler;
 import io.github.hexlodev.core.parser.SqlParseCache;
 import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;
+import io.github.hexlodev.core.strategy.HmacSha256DigestStrategy;
 import io.github.hexlodev.core.config.TableConfigRegistry;
 import lombok.extern.slf4j.Slf4j;
 
 import java.util.*;
 import java.util.Locale;
 import java.util.concurrent.atomic.AtomicBoolean;
 import java.util.regex.Pattern;
 
@@ -74,16 +77,17 @@ public class ConfigInitializer {
 
             // 初始化配置管理器
             configManager = new DataSourceConfigManager(properties);
             IGNORE_TABLE_CASE = properties.isIgnoreTableCase();
             initSkipCommentConfig(properties.getSkipComment());
 
             // 验证配置
             validateConfiguration(properties);
+            validateAndRegisterDigestConfiguration(properties);
 
             // 判断是否使用多数据源配置（tables 中指定了 datasource-id）
             boolean isMultiDatasource = isMultiDatasourceConfig(properties);
 
             if (isMultiDatasource) {
                 // 多数据源场景：为每个数据源初始化配置
                 log.debug("【securt-kit】检测到多数据源配置，开始初始化各数据源配置");
 
@@ -111,16 +115,17 @@ public class ConfigInitializer {
             // 初始化异常处理策略（使用全局配置）
             EncryptionHandler.initFromConfig(properties);
 
             log.debug("【securt-kit】配置初始化完成");
         } catch (Exception e) {
             // 初始化失败，重置状态以便下次重试
             log.error("【securt-kit】ConfigInitializer initialization failed", e);
             INITIALIZED.set(false);
+            DigestConfigRegistry.clear();
             throw new RuntimeException("Failed to initialize ConfigInitializer", e);
         }
     }
 
     /**
      * 验证配置的有效性
      *
      * @param properties 配置属性
@@ -210,16 +215,182 @@ public class ConfigInitializer {
             }
             errorMsg.append("\n请检查配置文件并修复上述错误。");
             throw new IllegalArgumentException(errorMsg.toString());
         }
 
         log.debug("【securt-kit】配置验证通过，共配置 {} 个表", tables.size());
     }
 
+    private static void validateAndRegisterDigestConfiguration(FieldEncryptorProperties properties) {
+        Map<String, Map<String, List<ResolvedDigestRule>>> resolvedByDatasource = new HashMap<>();
+        Map<String, Set<String>> targetsByTable = new HashMap<>();
+        Map<String, Set<String>> encryptedFieldsByTable = new HashMap<>();
+
+        if (CollectionUtil.isEmpty(properties.getTables())) {
+            return;
+        }
+
+        for (FieldEncryptorProperties.TableConfig table : properties.getTables()) {
+            String datasourceId = StrUtil.isBlank(table.getDatasourceId())
+                    ? DataSourceConfigManager.DEFAULT_DATASOURCE_ID
+                    : table.getDatasourceId();
+            String tableName = extractPureTableName(table.getTableName());
+            String tableKey = datasourceId + '\0' + tableName.toLowerCase(Locale.ROOT);
+            encryptedFieldsByTable.computeIfAbsent(tableKey, key -> new HashSet<>())
+                    .addAll(encryptedFieldNames(table));
+        }
+
+        for (FieldEncryptorProperties.TableConfig table : properties.getTables()) {
+            if (CollectionUtil.isEmpty(table.getDigest())) {
+                continue;
+            }
+
+            String datasourceId = StrUtil.isBlank(table.getDatasourceId())
+                    ? DataSourceConfigManager.DEFAULT_DATASOURCE_ID
+                    : table.getDatasourceId();
+            String tableName = extractPureTableName(table.getTableName());
+            String tableKey = datasourceId + '\0' + tableName.toLowerCase(Locale.ROOT);
+            Set<String> encryptedFields = encryptedFieldsByTable.get(tableKey);
+            Set<String> targetFields = targetsByTable.computeIfAbsent(tableKey, key -> new HashSet<>());
+            List<ResolvedDigestRule> rules = resolvedByDatasource
+                    .computeIfAbsent(datasourceId, key -> new HashMap<>())
+                    .computeIfAbsent(tableName.toLowerCase(Locale.ROOT), key -> new ArrayList<>());
+
+            for (FieldEncryptorProperties.DigestConfig digest : table.getDigest()) {
+                if (digest == null) {
+                    throw new ConfigurationException("表 '" + tableName + "' 的摘要配置不能为空");
+                }
+                if (CollectionUtil.isEmpty(digest.getSourceFields())) {
+                    throw new ConfigurationException("表 '" + tableName + "' 的摘要 sourceFields 不能为空");
+                }
+                for (String sourceField : digest.getSourceFields()) {
+                    if (StrUtil.isBlank(sourceField)) {
+                        throw new ConfigurationException(
+                                "表 '" + tableName + "' 的摘要 sourceFields 不能包含空字段");
+                    }
+                }
+                if (StrUtil.isBlank(digest.getTargetField())) {
+                    throw new ConfigurationException("表 '" + tableName + "' 的摘要 targetField 不能为空");
+                }
+
+                String targetField = digest.getTargetField().trim();
+                String normalizedTarget = targetField.toLowerCase(Locale.ROOT);
+                if (!targetFields.add(normalizedTarget)) {
+                    throw new ConfigurationException("表 '" + tableName + "' 的摘要 targetField 重复: " + targetField);
+                }
+                if (encryptedFields.contains(normalizedTarget)) {
+                    throw new ConfigurationException(
+                            "表 '" + tableName + "' 的摘要 targetField 不能同时配置为加密字段: " + targetField);
+                }
+
+                Class<? extends FieldEncryptorStrategy> strategyClass =
+                        resolveDigestStrategy(properties, digest, tableName);
+                FieldEncryptorStrategy strategy = createDigestStrategy(properties, strategyClass);
+                if (!strategy.supportsDigest()) {
+                    throw new ConfigurationException(
+                            "摘要策略不支持 digest 操作: " + strategyClass.getName());
+                }
+
+                FieldEncryptorProperties.PartialUpdate partialUpdate = digest.getPartialUpdate() != null
+                        ? digest.getPartialUpdate()
+                        : properties.getDigestPartialUpdate();
+                if (partialUpdate == null) {
+                    partialUpdate = FieldEncryptorProperties.PartialUpdate.RELOAD;
+                }
+                boolean verifyOnRead = digest.getVerifyOnRead() != null
+                        ? digest.getVerifyOnRead()
+                        : properties.isDigestVerifyOnRead();
+                FieldEncryptorProperties.FailurePolicy failurePolicy = digest.getFailurePolicy() != null
+                        ? digest.getFailurePolicy()
+                        : properties.getDigestFailurePolicy();
+                if (failurePolicy == null) {
+                    failurePolicy = properties.getFailurePolicy();
+                }
+                if (failurePolicy == null) {
+                    failurePolicy = FieldEncryptorProperties.FailurePolicy.FALLBACK;
+                }
+
+                rules.add(new ResolvedDigestRule(
+                        tableName,
+                        digest.getSourceFields(),
+                        targetField,
+                        strategyClass,
+                        partialUpdate,
+                        verifyOnRead,
+                        failurePolicy));
+            }
+        }
+
+        DigestConfigRegistry.clear();
+        for (Map.Entry<String, Map<String, List<ResolvedDigestRule>>> datasourceEntry
+                : resolvedByDatasource.entrySet()) {
+            for (Map.Entry<String, List<ResolvedDigestRule>> tableEntry
+                    : datasourceEntry.getValue().entrySet()) {
+                DigestConfigRegistry.register(
+                        datasourceEntry.getKey(), tableEntry.getKey(), tableEntry.getValue());
+            }
+        }
+    }
+
+    private static Set<String> encryptedFieldNames(FieldEncryptorProperties.TableConfig table) {
+        Set<String> names = new HashSet<>();
+        if (CollectionUtil.isNotEmpty(table.getFields())) {
+            for (FieldEncryptorProperties.FieldConfig field : table.getFields()) {
+                if (field != null && StrUtil.isNotBlank(field.getFieldName())) {
+                    names.add(field.getFieldName().trim().toLowerCase(Locale.ROOT));
+                }
+            }
+        }
+        return names;
+    }
+
+    @SuppressWarnings("unchecked")
+    private static Class<? extends FieldEncryptorStrategy> resolveDigestStrategy(
+            FieldEncryptorProperties properties,
+            FieldEncryptorProperties.DigestConfig digest,
+            String tableName) {
+        String strategyName = StrUtil.isNotBlank(digest.getStrategy())
+                ? digest.getStrategy()
+                : properties.getDigestStrategy();
+        if (StrUtil.isBlank(strategyName)) {
+            throw new ConfigurationException("表 '" + tableName + "' 未配置摘要策略");
+        }
+        try {
+            Class<?> strategyClass = ClassUtil.loadClass(strategyName);
+            if (!FieldEncryptorStrategy.class.isAssignableFrom(strategyClass)) {
+                throw new ConfigurationException("摘要策略未实现 FieldEncryptorStrategy: " + strategyName);
+            }
+            return (Class<? extends FieldEncryptorStrategy>) strategyClass;
+        } catch (ConfigurationException e) {
+            throw e;
+        } catch (Exception e) {
+            throw new ConfigurationException("无法加载摘要策略: " + strategyName, e);
+        }
+    }
+
+    private static FieldEncryptorStrategy createDigestStrategy(
+            FieldEncryptorProperties properties,
+            Class<? extends FieldEncryptorStrategy> strategyClass) {
+        if (HmacSha256DigestStrategy.class.equals(strategyClass)) {
+            if (StrUtil.isBlank(properties.getDigestHmacKey())) {
+                throw new ConfigurationException(
+                        "使用 HmacSha256DigestStrategy 时 digest-hmac-key 不能为空");
+            }
+            StrategyCache.registerStrategy(
+                    HmacSha256DigestStrategy.class,
+                    new HmacSha256DigestStrategy(properties.getDigestHmacKey()));
+        }
+        try {
+            return StrategyCache.getStrategy(strategyClass);
+        } catch (Exception e) {
+            throw new ConfigurationException("无法创建摘要策略: " + strategyClass.getName(), e);
+        }
+    }
+
     /**
      * 判断是否使用多数据源配置
      *
      * @param properties 配置属性
      * @return 如果 tables 中指定了 datasource-id，返回 true
      */
     private static boolean isMultiDatasourceConfig(FieldEncryptorProperties properties) {
         if (properties == null) {
@@ -405,16 +576,17 @@ public class ConfigInitializer {
      * 重置初始化状态（用于测试或配置热更新）
      */
     public static void reset() {
         INITIALIZED.set(false);
         configManager = null;
         IGNORE_TABLE_CASE = true;
         SKIP_COMMENT_ENABLED = false;
         SKIP_COMMENT_TOKEN = "SECURT_SKIP";
+        DigestConfigRegistry.clear();
         log.info("【securt-kit】ConfigInitializer reset completed");
     }
 
     /**
      * 是否忽略表名大小写
      *
      * @return true 表示忽略大小写
      */
diff --git a/securt-kit-core/src/main/java/io/github/hexlodev/core/config/DigestConfigRegistry.java b/securt-kit-core/src/main/java/io/github/hexlodev/core/config/DigestConfigRegistry.java
new file mode 100644
index 0000000..fbb7e0c
--- /dev/null
+++ b/securt-kit-core/src/main/java/io/github/hexlodev/core/config/DigestConfigRegistry.java
@@ -0,0 +1,66 @@
+package io.github.hexlodev.core.config;
+
+import cn.hutool.core.util.StrUtil;
+import io.github.hexlodev.core.digest.ResolvedDigestRule;
+
+import java.util.ArrayList;
+import java.util.Collections;
+import java.util.List;
+import java.util.Locale;
+import java.util.Map;
+import java.util.concurrent.ConcurrentHashMap;
+
+/**
+ * 按数据源和表保存已解析的摘要规则。
+ */
+public final class DigestConfigRegistry {
+
+    private static final Map<String, Map<String, List<ResolvedDigestRule>>> RULES =
+            new ConcurrentHashMap<>();
+
+    private DigestConfigRegistry() {
+    }
+
+    public static void register(String datasourceId, String table, List<ResolvedDigestRule> rules) {
+        if (StrUtil.isBlank(table)) {
+            throw new IllegalArgumentException("table must not be blank");
+        }
+        String ds = normalizeDatasourceId(datasourceId);
+        String normalizedTable = normalizeTable(table);
+        List<ResolvedDigestRule> immutableRules = rules == null
+                ? Collections.<ResolvedDigestRule>emptyList()
+                : Collections.unmodifiableList(new ArrayList<>(rules));
+        RULES.computeIfAbsent(ds, key -> new ConcurrentHashMap<>())
+                .put(normalizedTable, immutableRules);
+    }
+
+    public static List<ResolvedDigestRule> getRules(String table, String datasourceId) {
+        if (StrUtil.isBlank(table)) {
+            return Collections.emptyList();
+        }
+        Map<String, List<ResolvedDigestRule>> tableRules = RULES.get(normalizeDatasourceId(datasourceId));
+        if (tableRules == null) {
+            return Collections.emptyList();
+        }
+        List<ResolvedDigestRule> rules = tableRules.get(normalizeTable(table));
+        return rules == null ? Collections.<ResolvedDigestRule>emptyList() : rules;
+    }
+
+    public static boolean hasDigest(String table, String datasourceId) {
+        return !getRules(table, datasourceId).isEmpty();
+    }
+
+    public static void clear() {
+        RULES.clear();
+    }
+
+    private static String normalizeDatasourceId(String datasourceId) {
+        return StrUtil.isBlank(datasourceId)
+                ? DataSourceConfigManager.DEFAULT_DATASOURCE_ID
+                : datasourceId;
+    }
+
+    private static String normalizeTable(String table) {
+        return table.trim().toLowerCase(Locale.ROOT);
+    }
+}
diff --git a/securt-kit-core/src/main/java/io/github/hexlodev/core/digest/ResolvedDigestRule.java b/securt-kit-core/src/main/java/io/github/hexlodev/core/digest/ResolvedDigestRule.java
new file mode 100644
index 0000000..4950ca3
--- /dev/null
+++ b/securt-kit-core/src/main/java/io/github/hexlodev/core/digest/ResolvedDigestRule.java
@@ -0,0 +1,66 @@
+package io.github.hexlodev.core.digest;
+
+import io.github.hexlodev.core.config.FieldEncryptorProperties;
+import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;
+
+import java.util.ArrayList;
+import java.util.Collections;
+import java.util.List;
+
+/**
+ * 启动时完成继承与校验后的摘要规则。
+ */
+public final class ResolvedDigestRule {
+
+    private final String tableName;
+    private final List<String> sourceFields;
+    private final String targetField;
+    private final Class<? extends FieldEncryptorStrategy> strategyClass;
+    private final FieldEncryptorProperties.PartialUpdate partialUpdate;
+    private final boolean verifyOnRead;
+    private final FieldEncryptorProperties.FailurePolicy failurePolicy;
+
+    public ResolvedDigestRule(String tableName,
+                              List<String> sourceFields,
+                              String targetField,
+                              Class<? extends FieldEncryptorStrategy> strategyClass,
+                              FieldEncryptorProperties.PartialUpdate partialUpdate,
+                              boolean verifyOnRead,
+                              FieldEncryptorProperties.FailurePolicy failurePolicy) {
+        this.tableName = tableName;
+        this.sourceFields = Collections.unmodifiableList(new ArrayList<>(sourceFields));
+        this.targetField = targetField;
+        this.strategyClass = strategyClass;
+        this.partialUpdate = partialUpdate;
+        this.verifyOnRead = verifyOnRead;
+        this.failurePolicy = failurePolicy;
+    }
+
+    public String getTableName() {
+        return tableName;
+    }
+
+    public List<String> getSourceFields() {
+        return sourceFields;
+    }
+
+    public String getTargetField() {
+        return targetField;
+    }
+
+    public Class<? extends FieldEncryptorStrategy> getStrategyClass() {
+        return strategyClass;
+    }
+
+    public FieldEncryptorProperties.PartialUpdate getPartialUpdate() {
+        return partialUpdate;
+    }
+
+    public boolean isVerifyOnRead() {
+        return verifyOnRead;
+    }
+
+    public FieldEncryptorProperties.FailurePolicy getFailurePolicy() {
+        return failurePolicy;
+    }
+}
diff --git a/securt-kit-core/src/test/java/io/github/hexlodev/core/config/DigestConfigRegistryTest.java b/securt-kit-core/src/test/java/io/github/hexlodev/core/config/DigestConfigRegistryTest.java
new file mode 100644
index 0000000..21d6916
--- /dev/null
+++ b/securt-kit-core/src/test/java/io/github/hexlodev/core/config/DigestConfigRegistryTest.java
@@ -0,0 +1,174 @@
+package io.github.hexlodev.core.config;
+
+import io.github.hexlodev.core.cache.StrategyCache;
+import io.github.hexlodev.core.digest.ResolvedDigestRule;
+import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;
+import io.github.hexlodev.core.strategy.HmacSha256DigestStrategy;
+import org.junit.jupiter.api.AfterEach;
+import org.junit.jupiter.api.Test;
+
+import java.util.ArrayList;
+import java.util.Arrays;
+import java.util.Collections;
+
+import static org.junit.jupiter.api.Assertions.assertEquals;
+import static org.junit.jupiter.api.Assertions.assertFalse;
+import static org.junit.jupiter.api.Assertions.assertThrows;
+import static org.junit.jupiter.api.Assertions.assertTrue;
+
+class DigestConfigRegistryTest {
+
+    @AfterEach
+    void tearDown() {
+        ConfigInitializer.reset();
+        DigestConfigRegistry.clear();
+        StrategyCache.clear();
+    }
+
+    @Test
+    void rejectsTargetFieldAlsoEncrypted() {
+        FieldEncryptorProperties properties = baseProps();
+        properties.getTables().get(0).getFields().add(field("ROW_DIGEST"));
+
+        assertThrows(RuntimeException.class, () -> ConfigInitializer.initialize(properties));
+    }
+
+    @Test
+    void rejectsTargetFieldEncryptedByAnotherConfigForSameTableAndDatasource() {
+        FieldEncryptorProperties properties = baseProps();
+        FieldEncryptorProperties.TableConfig encryptedFields = new FieldEncryptorProperties.TableConfig();
+        encryptedFields.setTableName("USER");
+        encryptedFields.setFields(new ArrayList<FieldEncryptorProperties.FieldConfig>(
+                Collections.singletonList(field("ROW_DIGEST"))));
+        encryptedFields.setDigest(Collections.<FieldEncryptorProperties.DigestConfig>emptyList());
+        properties.setTables(Arrays.asList(properties.getTables().get(0), encryptedFields));
+
+        assertThrows(RuntimeException.class, () -> ConfigInitializer.initialize(properties));
+    }
+
+    @Test
+    void rejectsDuplicateTargetFieldIgnoringCase() {
+        FieldEncryptorProperties properties = baseProps();
+        FieldEncryptorProperties.DigestConfig duplicate = digest("phone", "ROW_DIGEST");
+        properties.getTables().get(0).getDigest().add(duplicate);
+
+        assertThrows(RuntimeException.class, () -> ConfigInitializer.initialize(properties));
+    }
+
+    @Test
+    void rejectsIncompleteAndUnsupportedDigestRules() {
+        FieldEncryptorProperties missingSource = baseProps();
+        missingSource.getTables().get(0).getDigest().get(0).setSourceFields(Collections.<String>emptyList());
+        assertThrows(RuntimeException.class, () -> ConfigInitializer.initialize(missingSource));
+
+        ConfigInitializer.reset();
+        FieldEncryptorProperties unsupported = baseProps();
+        unsupported.getTables().get(0).getDigest().get(0).setStrategy(UnsupportedDigestStrategy.class.getName());
+        assertThrows(RuntimeException.class, () -> ConfigInitializer.initialize(unsupported));
+    }
+
+    @Test
+    void rejectsBlankSourceFieldEntries() {
+        for (String sourceField : Arrays.asList(null, "", " ")) {
+            FieldEncryptorProperties properties = baseProps();
+            properties.getTables().get(0).getDigest().get(0)
+                    .setSourceFields(Collections.singletonList(sourceField));
+
+            assertThrows(RuntimeException.class, () -> ConfigInitializer.initialize(properties));
+            ConfigInitializer.reset();
+        }
+    }
+
+    @Test
+    void requiresHmacKeyAndRegistersConfiguredHmacStrategy() {
+        FieldEncryptorProperties missingKey = baseProps();
+        missingKey.setDigestHmacKey(" ");
+        assertThrows(RuntimeException.class, () -> ConfigInitializer.initialize(missingKey));
+
+        ConfigInitializer.reset();
+        FieldEncryptorProperties configured = baseProps();
+        ConfigInitializer.initialize(configured);
+        assertTrue(StrategyCache.contains(HmacSha256DigestStrategy.class));
+    }
+
+    @Test
+    void registersResolvedDigestRulesAndClearsOnReset() {
+        FieldEncryptorProperties properties = baseProps();
+        properties.setDigestPartialUpdate(FieldEncryptorProperties.PartialUpdate.FAIL);
+        properties.setDigestVerifyOnRead(true);
+        properties.setDigestFailurePolicy(null);
+        properties.setFailurePolicy(FieldEncryptorProperties.FailurePolicy.RETRY);
+
+        ConfigInitializer.initialize(properties);
+
+        assertTrue(DigestConfigRegistry.hasDigest("USER", "default"));
+        ResolvedDigestRule rule = DigestConfigRegistry.getRules("user", "default").get(0);
+        assertEquals("user", rule.getTableName());
+        assertEquals(Collections.singletonList("phone"), rule.getSourceFields());
+        assertEquals("row_digest", rule.getTargetField());
+        assertEquals(HmacSha256DigestStrategy.class, rule.getStrategyClass());
+        assertEquals(FieldEncryptorProperties.PartialUpdate.FAIL, rule.getPartialUpdate());
+        assertTrue(rule.isVerifyOnRead());
+        assertEquals(FieldEncryptorProperties.FailurePolicy.RETRY, rule.getFailurePolicy());
+
+        ConfigInitializer.reset();
+        assertFalse(DigestConfigRegistry.hasDigest("user", "default"));
+    }
+
+    @Test
+    void itemSettingsOverrideGlobalDigestSettings() {
+        FieldEncryptorProperties properties = baseProps();
+        FieldEncryptorProperties.DigestConfig digest = properties.getTables().get(0).getDigest().get(0);
+        digest.setPartialUpdate(FieldEncryptorProperties.PartialUpdate.SKIP);
+        digest.setVerifyOnRead(false);
+        digest.setFailurePolicy(FieldEncryptorProperties.FailurePolicy.FAIL_FAST);
+
+        ConfigInitializer.initialize(properties);
+
+        ResolvedDigestRule rule = DigestConfigRegistry.getRules("user", "default").get(0);
+        assertEquals(FieldEncryptorProperties.PartialUpdate.SKIP, rule.getPartialUpdate());
+        assertFalse(rule.isVerifyOnRead());
+        assertEquals(FieldEncryptorProperties.FailurePolicy.FAIL_FAST, rule.getFailurePolicy());
+    }
+
+    private FieldEncryptorProperties baseProps() {
+        FieldEncryptorProperties properties = new FieldEncryptorProperties();
+        properties.setEnable(true);
+        properties.setDigestStrategy(HmacSha256DigestStrategy.class.getName());
+        properties.setDigestHmacKey("k");
+
+        FieldEncryptorProperties.TableConfig table = new FieldEncryptorProperties.TableConfig();
+        table.setTableName("user");
+        table.setFields(new ArrayList<FieldEncryptorProperties.FieldConfig>(
+                Collections.singletonList(field("phone"))));
+        table.setDigest(new ArrayList<FieldEncryptorProperties.DigestConfig>(
+                Collections.singletonList(digest("phone", "row_digest"))));
+        properties.setTables(Collections.singletonList(table));
+        return properties;
+    }
+
+    private FieldEncryptorProperties.FieldConfig field(String name) {
+        FieldEncryptorProperties.FieldConfig field = new FieldEncryptorProperties.FieldConfig();
+        field.setFieldName(name);
+        return field;
+    }
+
+    private FieldEncryptorProperties.DigestConfig digest(String source, String target) {
+        FieldEncryptorProperties.DigestConfig digest = new FieldEncryptorProperties.DigestConfig();
+        digest.setSourceFields(Arrays.asList(source));
+        digest.setTargetField(target);
+        return digest;
+    }
+
+    public static class UnsupportedDigestStrategy implements FieldEncryptorStrategy {
+        @Override
+        public String encryption(String oldValue) {
+            return oldValue;
+        }
+
+        @Override
+        public String decryption(String oldValue) {
+            return oldValue;
+        }
+    }
+}

