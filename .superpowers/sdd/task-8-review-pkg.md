# Review Package Task 8 re-review
BASE: b62cb995cb5310ca031537ec73ba76557a000913
HEAD: cccd97254fa5152c75dc75eaefbcb40740a34110
## Commits
cccd972 fix(digest): align MyBatis SQL rewrite with computed digests
c2b9214 feat(digest): wire MyBatis parameter and result digest hooks

## Stat
 .../hexlodev/mybatis/EncryptInterceptor.java       | 320 ++++++++++
 .../hexlodev/mybatis/ParameterEncryptHelper.java   | 684 +++++++++++++++++++++
 .../hexlodev/mybatis/ResultDecryptHelper.java      | 371 +++++++++++
 .../hexlodev/mybatis/DigestParamHelperTest.java    | 180 ++++++
 4 files changed, 1555 insertions(+)

## Diff
diff --git a/securt-kit-mybatis/src/main/java/io/github/hexlodev/mybatis/EncryptInterceptor.java b/securt-kit-mybatis/src/main/java/io/github/hexlodev/mybatis/EncryptInterceptor.java
new file mode 100644
index 0000000..d84b753
--- /dev/null
+++ b/securt-kit-mybatis/src/main/java/io/github/hexlodev/mybatis/EncryptInterceptor.java
@@ -0,0 +1,320 @@
+package io.github.hexlodev.mybatis;
+
+import cn.hutool.core.lang.Pair;
+import cn.hutool.core.util.StrUtil;
+import io.github.hexlodev.core.config.ConfigInitializer;
+import io.github.hexlodev.core.config.DigestConfigRegistry;
+import io.github.hexlodev.core.config.EncryptModeHolder;
+import io.github.hexlodev.core.exception.SqlParseException;
+import io.github.hexlodev.core.parser.SecurtkitUtils;
+import io.github.hexlodev.core.parser.SqlParseCache;
+import io.github.hexlodev.core.parser.dto.ColumnTableDto;
+import io.github.hexlodev.core.parser.dto.FieldEncryptorInfoDto;
+import lombok.extern.slf4j.Slf4j;
+import org.apache.ibatis.cache.CacheKey;
+import org.apache.ibatis.executor.Executor;
+import org.apache.ibatis.mapping.BoundSql;
+import org.apache.ibatis.mapping.MappedStatement;
+import org.apache.ibatis.plugin.Interceptor;
+import org.apache.ibatis.plugin.Intercepts;
+import org.apache.ibatis.plugin.Invocation;
+import org.apache.ibatis.plugin.Plugin;
+import org.apache.ibatis.plugin.Signature;
+import org.apache.ibatis.session.ResultHandler;
+import org.apache.ibatis.session.RowBounds;
+
+import java.sql.Connection;
+import java.util.Collections;
+import java.util.List;
+import java.util.Map;
+import java.util.Properties;
+import java.util.Set;
+
+/**
+ * MyBatis 字段加解密插件（mode=MYBATIS）
+ * <p>
+ * 挂载在 {@link Executor} 上，执行前加密参数、执行后解密结果，配置、SQL 解析与加解密逻辑
+ * 全部复用 securt-kit-core，与 JDBC 通道共用同一份加密清单和失败策略。
+ * </p>
+ *
+ * <p>处理流程：</p>
+ * <ol>
+ *   <li>非 {@code MYBATIS} 模式直接透传，避免与 JDBC 通道双重加密</li>
+ *   <li>取 BoundSql，命中 skip-comment 时透传</li>
+ *   <li>解析表名，无需加密的语句透传（快速路径）</li>
+ *   <li>解析 SQL 得到「占位符 → 表.列」与「结果列 → 表.列」映射</li>
+ *   <li>加密参数（就地改写），执行 SQL，解密结果，最后还原参数明文</li>
+ * </ol>
+ *
+ * <p>与其它插件的顺序：本插件读取 BoundSql 但不改写 SQL 文本，建议注册在分页插件之后
+ * （即先执行分页改写，再执行参数加密），避免分页插件生成的 count 语句绕过加密。</p>
+ *
+ * @author hexlodev
+ * @since 1.3.0
+ */
+@Intercepts({
+        @Signature(type = Executor.class, method = "update",
+                args = {MappedStatement.class, Object.class}),
+        @Signature(type = Executor.class, method = "query",
+                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
+        @Signature(type = Executor.class, method = "query",
+                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
+                        CacheKey.class, BoundSql.class})
+})
+@Slf4j
+public class EncryptInterceptor implements Interceptor {
+
+    /**
+     * 6 参数 query 签名的参数个数（此时 BoundSql 由调用方传入，改写可直接生效）
+     */
+    private static final int QUERY_ARGS_WITH_BOUND_SQL = 6;
+
+    @Override
+    public Object intercept(Invocation invocation) throws Throwable {
+        if (!EncryptModeHolder.isMybatis()) {
+            return invocation.proceed();
+        }
+
+        Object[] args = invocation.getArgs();
+        if (args == null || args.length < 2 || !(args[0] instanceof MappedStatement)) {
+            return invocation.proceed();
+        }
+
+        MappedStatement mappedStatement = (MappedStatement) args[0];
+        Object parameter = args[1];
+        boolean query = "query".equals(invocation.getMethod().getName());
+        boolean callerBoundSql = args.length == QUERY_ARGS_WITH_BOUND_SQL && args[5] instanceof BoundSql;
+
+        BoundSql boundSql = resolveBoundSql(mappedStatement, parameter, args, callerBoundSql);
+        if (boundSql == null) {
+            return invocation.proceed();
+        }
+
+        String sql = boundSql.getSql();
+        if (StrUtil.isBlank(sql)) {
+            return invocation.proceed();
+        }
+        if (ConfigInitializer.shouldSkipByComment(sql)) {
+            log.debug("Skip encryption by comment token [statement={}]", mappedStatement.getId());
+            return invocation.proceed();
+        }
+
+        String datasourceId = DatasourceIdResolver.resolve();
+
+        // 快速路径：SQL 涉及的表都没有配置加密字段时直接透传
+        Set<String> tables = parseTableNames(sql);
+        if (!SecurtkitUtils.needEncrypt(tables, datasourceId)
+                && !hasConfiguredDigest(tables, datasourceId)) {
+            if (log.isDebugEnabled()) {
+                log.debug("No encrypted table involved, passthrough [statement={}, tables={}, datasource-id={}]",
+                        mappedStatement.getId(), tables, datasourceId);
+            }
+            return invocation.proceed();
+        }
+
+        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseResult = parseSql(sql, datasourceId, tables);
+        if (parseResult == null) {
+            return invocation.proceed();
+        }
+
+        // 摘要必须基于业务明文计算；BoundSql 改写完成后重新解析索引，再执行字段加密。
+        boolean digestRewritten = ParameterEncryptHelper.applyDigests(
+                parameter, boundSql, parseResult, tables, datasourceId,
+                resolveConnection(invocation));
+        if (digestRewritten) {
+            parseResult = parseSql(boundSql.getSql(), datasourceId, tables);
+            if (!callerBoundSql) {
+                MappedStatement wrapped = wrapBoundSql(mappedStatement, boundSql);
+                if (wrapped != null) {
+                    args[0] = wrapped;
+                }
+            }
+        }
+
+        Map<String, Object> originals = Collections.emptyMap();
+        if (ParameterEncryptHelper.isScalarStringParameter(parameter)) {
+            // 参数对象本身就是待绑定值（单个未命名简单参数），只能整体替换
+            String encrypted = ParameterEncryptHelper.encryptScalarParameter(parameter, boundSql, parseResult, datasourceId);
+            if (encrypted != null) {
+                args[1] = encrypted;
+            }
+        } else {
+            originals = ParameterEncryptHelper.encryptParameters(parameter, boundSql, parseResult, datasourceId);
+            if (!callerBoundSql && ParameterEncryptHelper.hasBoundSqlOnlyRewrite(boundSql, originals)) {
+                // foreach 中的不可变元素只改写在 BoundSql 附加参数上，
+                // 必须把当前 BoundSql 透传给下游，否则 Executor 会重新构建导致改写丢失
+                MappedStatement wrapped = wrapBoundSql(mappedStatement, boundSql);
+                if (wrapped != null) {
+                    args[0] = wrapped;
+                }
+            }
+        }
+
+        // 一级缓存命中时返回的是上一次已解密的对象，必须跳过，避免重复解密
+        boolean cached = query && isLocallyCached(invocation, mappedStatement, args, boundSql);
+
+        try {
+            Object result = invocation.proceed();
+            if (query && result != null && !cached) {
+                ResultDecryptHelper.decryptResult(result, parseResult, tables, datasourceId);
+            }
+            return result;
+        } finally {
+            ParameterEncryptHelper.restoreParameters(parameter, boundSql, originals);
+        }
+    }
+
+    /**
+     * 判断本次查询是否会命中 MyBatis 一级缓存
+     * <p>
+     * 结果解密是「就地改写映射对象」，而一级缓存持有的正是同一批对象引用，
+     * 因此缓存命中时结果已是明文，重复解密会破坏数据。
+     * </p>
+     *
+     * @return true 表示命中一级缓存，应跳过解密
+     */
+    private boolean isLocallyCached(Invocation invocation, MappedStatement mappedStatement,
+                                    Object[] args, BoundSql boundSql) {
+        if (!(invocation.getTarget() instanceof Executor) || args.length < 4) {
+            return false;
+        }
+        if (args[3] != null) {
+            // 传入 ResultHandler 时 MyBatis 不读取一级缓存，一定会重新映射
+            return false;
+        }
+        try {
+            Executor executor = (Executor) invocation.getTarget();
+            CacheKey cacheKey;
+            if (args.length == QUERY_ARGS_WITH_BOUND_SQL && args[4] instanceof CacheKey) {
+                cacheKey = (CacheKey) args[4];
+            } else {
+                RowBounds rowBounds = args[2] instanceof RowBounds ? (RowBounds) args[2] : RowBounds.DEFAULT;
+                cacheKey = executor.createCacheKey(mappedStatement, args[1], rowBounds, boundSql);
+            }
+            return executor.isCached(mappedStatement, cacheKey);
+        } catch (Exception e) {
+            log.debug("Failed to check local cache [statement={}]: {}", mappedStatement.getId(), e.getMessage());
+            return false;
+        }
+    }
+
+    @Override
+    public Object plugin(Object target) {
+        return Plugin.wrap(target, this);
+    }
+
+    @Override
+    public void setProperties(Properties properties) {
+        // 本插件的行为完全由 securtkit.encryptor 配置驱动，无需插件级属性
+    }
+
+    /**
+     * 获取当前语句的 BoundSql
+     * <p>
+     * 6 参数 query 使用调用方传入的实例（改写直接生效）；其余签名只能自行构建，
+     * 此时对参数对象图的改写依然有效（下游按属性路径读取同一批对象）。
+     * </p>
+     */
+    private BoundSql resolveBoundSql(MappedStatement mappedStatement, Object parameter,
+                                     Object[] args, boolean callerBoundSql) {
+        if (callerBoundSql) {
+            return (BoundSql) args[5];
+        }
+        try {
+            return mappedStatement.getBoundSql(parameter);
+        } catch (Exception e) {
+            log.debug("Failed to build BoundSql, passthrough [statement={}]: {}",
+                    mappedStatement.getId(), e.getMessage());
+            return null;
+        }
+    }
+
+    private Set<String> parseTableNames(String sql) {
+        try {
+            Set<String> tables = SqlParseCache.parseTableNames(sql);
+            return tables != null ? tables : Collections.<String>emptySet();
+        } catch (Exception e) {
+            log.warn("Failed to parse table names from SQL [sqlLength={}]: {}", sql.length(), e.getMessage());
+            return Collections.emptySet();
+        }
+    }
+
+    private boolean hasConfiguredDigest(Set<String> tables, String datasourceId) {
+        for (String table : tables) {
+            if (DigestConfigRegistry.hasDigest(table, datasourceId)) {
+                return true;
+            }
+        }
+        return false;
+    }
+
+    private Connection resolveConnection(Invocation invocation) {
+        if (!(invocation.getTarget() instanceof Executor)) {
+            return null;
+        }
+        try {
+            return ((Executor) invocation.getTarget()).getTransaction().getConnection();
+        } catch (Exception e) {
+            log.debug("Failed to resolve connection for digest RELOAD: {}", e.getMessage());
+            return null;
+        }
+    }
+
+    /**
+     * 解析 SQL 获取字段映射
+     * <p>
+     * 与 JDBC 通道一致：涉及加密表却解析失败时快速失败，避免明文落库。
+     * </p>
+     */
+    private Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseSql(String sql, String datasourceId,
+                                                                                    Set<String> tables) {
+        try {
+            return SecurtkitUtils.parseSql(sql, datasourceId);
+        } catch (Exception e) {
+            log.error("Failed to parse SQL for encryption [sqlLength={}, tables={}, datasource-id={}]: {}",
+                    sql.length(), tables, datasourceId, e.getMessage(), e);
+            throw new SqlParseException("SQL parsing failed for encryption", e, sql);
+        }
+    }
+
+    /**
+     * 用当前（已改写附加参数的）BoundSql 包装 MappedStatement，保证下游执行使用同一实例
+     *
+     * @return 包装后的 MappedStatement，复制失败时返回 null（调用方保持原状）
+     */
+    private MappedStatement wrapBoundSql(MappedStatement ms, BoundSql boundSql) {
+        try {
+            MappedStatement.Builder builder = new MappedStatement.Builder(ms.getConfiguration(), ms.getId(),
+                    parameterObject -> boundSql, ms.getSqlCommandType());
+            builder.resource(ms.getResource());
+            builder.parameterMap(ms.getParameterMap());
+            builder.resultMaps(ms.getResultMaps());
+            builder.resultSetType(ms.getResultSetType());
+            builder.fetchSize(ms.getFetchSize());
+            builder.timeout(ms.getTimeout());
+            builder.statementType(ms.getStatementType());
+            builder.keyGenerator(ms.getKeyGenerator());
+            builder.keyProperty(join(ms.getKeyProperties()));
+            builder.keyColumn(join(ms.getKeyColumns()));
+            builder.resultSets(join(ms.getResultSets()));
+            builder.databaseId(ms.getDatabaseId());
+            builder.lang(ms.getLang());
+            builder.resultOrdered(ms.isResultOrdered());
+            builder.flushCacheRequired(ms.isFlushCacheRequired());
+            builder.useCache(ms.isUseCache());
+            builder.cache(ms.getCache());
+            return builder.build();
+        } catch (Exception e) {
+            log.warn("【securt-kit】Failed to wrap MappedStatement [statement={}], IN parameters built by foreach "
+                    + "may stay unencrypted: {}", ms.getId(), e.getMessage());
+            return null;
+        }
+    }
+
+    private static String join(String[] values) {
+        if (values == null || values.length == 0) {
+            return null;
+        }
+        return String.join(",", values);
+    }
+}
diff --git a/securt-kit-mybatis/src/main/java/io/github/hexlodev/mybatis/ParameterEncryptHelper.java b/securt-kit-mybatis/src/main/java/io/github/hexlodev/mybatis/ParameterEncryptHelper.java
new file mode 100644
index 0000000..f832ac0
--- /dev/null
+++ b/securt-kit-mybatis/src/main/java/io/github/hexlodev/mybatis/ParameterEncryptHelper.java
@@ -0,0 +1,684 @@
+package io.github.hexlodev.mybatis;
+
+import cn.hutool.core.lang.Pair;
+import cn.hutool.core.util.StrUtil;
+import io.github.hexlodev.core.config.DigestConfigRegistry;
+import io.github.hexlodev.core.crypto.FieldCryptoService;
+import io.github.hexlodev.core.crypto.FieldCryptoServiceHolder;
+import io.github.hexlodev.core.digest.DigestRewriteResult;
+import io.github.hexlodev.core.digest.DigestService;
+import io.github.hexlodev.core.digest.DigestSqlRewriter;
+import io.github.hexlodev.core.digest.ResolvedDigestRule;
+import io.github.hexlodev.core.exception.SecurtKitException;
+import io.github.hexlodev.core.parser.dto.ColumnTableDto;
+import io.github.hexlodev.core.parser.dto.FieldEncryptorInfoDto;
+import io.github.hexlodev.core.parser.dto.ParameterMatchType;
+import io.github.hexlodev.core.strategy.like.LikeHandleContext;
+import io.github.hexlodev.core.strategy.like.LikeHandleResult;
+import io.github.hexlodev.core.strategy.like.LikePatternHandlerHolder;
+import lombok.extern.slf4j.Slf4j;
+import net.sf.jsqlparser.parser.CCJSqlParserUtil;
+import net.sf.jsqlparser.statement.Statement;
+import net.sf.jsqlparser.statement.insert.Insert;
+import net.sf.jsqlparser.statement.select.Values;
+import net.sf.jsqlparser.statement.update.Update;
+import org.apache.ibatis.mapping.BoundSql;
+import org.apache.ibatis.mapping.ParameterMapping;
+import org.apache.ibatis.reflection.MetaObject;
+import org.apache.ibatis.reflection.SystemMetaObject;
+import org.apache.ibatis.session.Configuration;
+
+import java.sql.Connection;
+import java.util.ArrayList;
+import java.util.Collections;
+import java.util.HashMap;
+import java.util.HashSet;
+import java.util.LinkedHashMap;
+import java.util.List;
+import java.util.Locale;
+import java.util.Map;
+import java.util.Set;
+
+/**
+ * MyBatis 参数加密助手
+ * <p>
+ * 与 JDBC 通道的 {@code ParameterEncryptor} 语义对齐：由 SQL 解析结果得到
+ * 「占位符序号 → 表.列」映射，再按 {@link BoundSql#getParameterMappings()} 的顺序
+ * （序号从 1 开始，与 JDBC 参数位一致）定位参数属性，对需要加密的字符串值执行加密。
+ * </p>
+ * <p>
+ * 加密采用「就地改写 + 执行后还原」策略：绑定前把明文替换为密文，SQL 执行结束后由
+ * {@link #restoreParameters} 还原，避免业务对象上残留密文。
+ * </p>
+ *
+ * @author hexlodev
+ * @since 1.3.0
+ */
+@Slf4j
+public final class ParameterEncryptHelper {
+
+    private ParameterEncryptHelper() {
+    }
+
+    /**
+     * 使用参数明文计算摘要，并将摘要参数接入当前 BoundSql。
+     *
+     * @return true 表示 BoundSql 的 SQL 或参数映射已改写，调用方需保证执行同一实例
+     */
+    public static boolean applyDigests(
+            Object parameter,
+            BoundSql boundSql,
+            Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseResult,
+            Set<String> tables,
+            String datasourceId) {
+        return applyDigests(parameter, boundSql, parseResult, tables, datasourceId, null);
+    }
+
+    static boolean applyDigests(
+            Object parameter,
+            BoundSql boundSql,
+            Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseResult,
+            Set<String> tables,
+            String datasourceId,
+            Connection reloadConnection) {
+        if (boundSql == null || StrUtil.isBlank(boundSql.getSql())
+                || !isSupportedWrite(boundSql.getSql())) {
+            return false;
+        }
+        String dsId = normalizeDatasourceId(datasourceId);
+        String table = singleDigestTable(tables, dsId);
+        if (table == null) {
+            return false;
+        }
+
+        List<ResolvedDigestRule> rules = DigestConfigRegistry.getRules(table, dsId);
+        List<String> targets = new ArrayList<String>(rules.size());
+        for (ResolvedDigestRule rule : rules) {
+            targets.add(rule.getTargetField());
+        }
+        if (isMultiRowInsert(boundSql.getSql())) {
+            log.warn("【securt-kit】Multi-row INSERT is not supported for MyBatis digest rewrite");
+            return false;
+        }
+
+        List<ParameterMapping> originalMappings = boundSql.getParameterMappings();
+        Map<Integer, ColumnTableDto> columnsByIndex = buildParameterIndexMap(parseResult);
+        MetaObject parameterMetaObject = parameter == null ? null : SystemMetaObject.forObject(parameter);
+        Map<String, String> availablePlain = new LinkedHashMap<String, String>();
+        Map<Integer, Object> valuesByIndex = new LinkedHashMap<Integer, Object>();
+        for (int i = 0; originalMappings != null && i < originalMappings.size(); i++) {
+            int index = i + 1;
+            Object value = isScalarStringParameter(parameter)
+                    ? parameter
+                    : readValue(parameter, parameterMetaObject, boundSql,
+                    originalMappings.get(i).getProperty());
+            valuesByIndex.put(index, value);
+            ColumnTableDto column = columnsByIndex.get(index);
+            if (column != null && equalsIgnoreCase(table, column.getSourceTableName())) {
+                availablePlain.put(column.getSourceColumn(),
+                        value == null ? null : String.valueOf(value));
+            }
+        }
+
+        boolean insert = isInsert(boundSql.getSql());
+        ReloadContext reload = buildReloadContext(
+                boundSql.getSql(), valuesByIndex, columnsByIndex, dsId);
+        Map<String, String> digests = new DigestService().computeTargetDigests(
+                table, dsId, availablePlain, insert, reloadConnection,
+                reload.whereSql, reload.whereParams);
+        if (digests.isEmpty()) {
+            return false;
+        }
+
+        List<String> computedTargets = new ArrayList<String>(targets.size());
+        for (String target : targets) {
+            if (getIgnoreCase(digests, target) != null) {
+                computedTargets.add(target);
+            }
+        }
+        DigestRewriteResult rewrite = DigestSqlRewriter.tryAppendTargets(
+                boundSql.getSql(), computedTargets);
+        if (rewrite.getWarnMessage() != null) {
+            log.warn("【securt-kit】MyBatis digest SQL was not rewritten: {}", rewrite.getWarnMessage());
+        }
+
+        List<ParameterMapping> mappings = originalMappings == null
+                ? new ArrayList<ParameterMapping>()
+                : new ArrayList<ParameterMapping>(originalMappings);
+        Configuration mappingConfiguration = new Configuration();
+        Set<String> appendedTargets = new HashSet<String>();
+        boolean mappingChanged = false;
+        List<String> rewriteTargets = rewrite.getAppendedTargetFields();
+        List<Integer> rewriteIndexes = rewrite.getAppendedParameterIndexes();
+        for (int i = 0; i < rewriteTargets.size() && i < rewriteIndexes.size(); i++) {
+            String target = rewriteTargets.get(i);
+            String digest = getIgnoreCase(digests, target);
+            if (digest == null) {
+                continue;
+            }
+            String property = digestProperty(target);
+            int mappingIndex = Math.min(Math.max(rewriteIndexes.get(i) - 1, 0), mappings.size());
+            mappings.add(mappingIndex,
+                    new ParameterMapping.Builder(mappingConfiguration, property, String.class).build());
+            boundSql.setAdditionalParameter(property, digest);
+            appendedTargets.add(target.toLowerCase(Locale.ROOT));
+            mappingChanged = true;
+        }
+
+        for (Map.Entry<Integer, ColumnTableDto> entry : columnsByIndex.entrySet()) {
+            String target = entry.getValue().getSourceColumn();
+            String digest = getIgnoreCase(digests, target);
+            int mappingIndex = entry.getKey() - 1;
+            if (digest == null || mappingIndex < 0 || mappingIndex >= mappings.size()
+                    || appendedTargets.contains(target.toLowerCase(Locale.ROOT))) {
+                continue;
+            }
+            String property = digestProperty(target);
+            mappings.set(mappingIndex,
+                    new ParameterMapping.Builder(mappingConfiguration, property, String.class).build());
+            boundSql.setAdditionalParameter(property, digest);
+            mappingChanged = true;
+        }
+
+        MetaObject boundSqlMetaObject = SystemMetaObject.forObject(boundSql);
+        if (rewrite.isRewritten()) {
+            boundSqlMetaObject.setValue("sql", rewrite.getSql());
+        }
+        if (mappingChanged) {
+            boundSqlMetaObject.setValue("parameterMappings", mappings);
+        }
+        return rewrite.isRewritten() || mappingChanged;
+    }
+
+    private static String singleDigestTable(Set<String> tables, String datasourceId) {
+        String matched = null;
+        if (tables == null) {
+            return null;
+        }
+        for (String table : tables) {
+            if (!DigestConfigRegistry.hasDigest(table, datasourceId)) {
+                continue;
+            }
+            if (matched != null && !equalsIgnoreCase(matched, table)) {
+                throw new SecurtKitException("Digest write supports a single configured table only");
+            }
+            matched = table;
+        }
+        return matched;
+    }
+
+    private static boolean isInsert(String sql) {
+        try {
+            return CCJSqlParserUtil.parse(sql) instanceof Insert;
+        } catch (Exception e) {
+            return false;
+        }
+    }
+
+    private static boolean isSupportedWrite(String sql) {
+        try {
+            Statement statement = CCJSqlParserUtil.parse(sql);
+            return statement instanceof Insert || statement instanceof Update;
+        } catch (Exception e) {
+            return false;
+        }
+    }
+
+    private static ReloadContext buildReloadContext(
+            String sql, Map<Integer, Object> valuesByIndex,
+            Map<Integer, ColumnTableDto> columnsByIndex, String datasourceId) {
+        try {
+            Statement statement = CCJSqlParserUtil.parse(sql);
+            if (!(statement instanceof Update) || ((Update) statement).getWhere() == null) {
+                return ReloadContext.EMPTY;
+            }
+            String whereSql = ((Update) statement).getWhere().toString();
+            int totalCount = countPlaceholders(sql);
+            int whereCount = countPlaceholders(whereSql);
+            List<Object> whereParams = new ArrayList<Object>(whereCount);
+            FieldCryptoService cryptoService = FieldCryptoServiceHolder.get();
+            for (int index = totalCount - whereCount + 1; index <= totalCount; index++) {
+                Object value = valuesByIndex.get(index);
+                ColumnTableDto column = columnsByIndex.get(index);
+                if (value instanceof String && needEncrypt(cryptoService, column, datasourceId)) {
+                    String encrypted = encryptValue(
+                            cryptoService, column, (String) value, datasourceId, index);
+                    whereParams.add(encrypted == null ? value : encrypted);
+                } else {
+                    whereParams.add(value);
+                }
+            }
+            return new ReloadContext(whereSql, whereParams);
+        } catch (Exception e) {
+            throw new SecurtKitException("Cannot resolve UPDATE WHERE parameters for digest RELOAD", e);
+        }
+    }
+
+    private static int countPlaceholders(String sql) {
+        int count = 0;
+        for (int i = 0; sql != null && i < sql.length(); i++) {
+            if (sql.charAt(i) == '?') {
+                count++;
+            }
+        }
+        return count;
+    }
+
+    private static boolean isMultiRowInsert(String sql) {
+        try {
+            Statement statement = CCJSqlParserUtil.parse(sql);
+            if (!(statement instanceof Insert)
+                    || !(((Insert) statement).getSelect() instanceof Values)) {
+                return false;
+            }
+            Values values = (Values) ((Insert) statement).getSelect();
+            return values.getExpressions() != null
+                    && values.getExpressions().size() > 1
+                    && values.getExpressions().get(0)
+                    instanceof net.sf.jsqlparser.expression.operators.relational.ExpressionList;
+        } catch (Exception e) {
+            return false;
+        }
+    }
+
+    private static String digestProperty(String target) {
+        return "__securtkit_digest_" + target.toLowerCase(Locale.ROOT);
+    }
+
+    private static String getIgnoreCase(Map<String, String> values, String key) {
+        for (Map.Entry<String, String> entry : values.entrySet()) {
+            if (equalsIgnoreCase(entry.getKey(), key)) {
+                return entry.getValue();
+            }
+        }
+        return null;
+    }
+
+    private static boolean equalsIgnoreCase(String left, String right) {
+        return left != null && right != null && left.equalsIgnoreCase(right);
+    }
+
+    private static final class ReloadContext {
+        private static final ReloadContext EMPTY =
+                new ReloadContext(null, Collections.emptyList());
+
+        private final String whereSql;
+        private final List<Object> whereParams;
+
+        private ReloadContext(String whereSql, List<Object> whereParams) {
+            this.whereSql = whereSql;
+            this.whereParams = whereParams;
+        }
+    }
+
+    /**
+     * 判断参数对象本身就是待绑定的标量值
+     * <p>
+     * Mapper 方法只有一个未加 {@code @Param} 的简单参数时，MyBatis 会把参数对象原样绑定到
+     * 每个占位符（见 {@code DefaultParameterHandler} 对 TypeHandler 的判断），
+     * 此时属性路径无效，只能整体替换参数对象。
+     * </p>
+     *
+     * @param parameter MyBatis 参数对象，可为 null
+     * @return true 表示需要走 {@link #encryptScalarParameter} 分支
+     */
+    public static boolean isScalarStringParameter(Object parameter) {
+        return parameter instanceof String;
+    }
+
+    /**
+     * 加密标量参数（参数对象本身即待绑定值）
+     *
+     * @param parameter    参数对象，必须是 String
+     * @param boundSql     当前语句的 BoundSql
+     * @param parseResult  core 的 SQL 解析结果
+     * @param datasourceId 数据源标识
+     * @return 密文；无需加密或加密未生效时返回 null
+     */
+    public static String encryptScalarParameter(Object parameter, BoundSql boundSql,
+                                                Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseResult,
+                                                String datasourceId) {
+        if (!isScalarStringParameter(parameter)) {
+            return null;
+        }
+        Map<Integer, ColumnTableDto> indexMap = buildParameterIndexMap(parseResult);
+        if (indexMap.isEmpty()) {
+            return null;
+        }
+        List<ParameterMapping> parameterMappings = boundSql == null ? null : boundSql.getParameterMappings();
+        int mappingCount = parameterMappings == null ? 0 : parameterMappings.size();
+        if (mappingCount == 0) {
+            return null;
+        }
+
+        String dsId = normalizeDatasourceId(datasourceId);
+        FieldCryptoService cryptoService = FieldCryptoServiceHolder.get();
+        String plain = (String) parameter;
+
+        // 标量参数会被绑定到所有占位符，取第一个需要加密的字段即可
+        for (int parameterIndex = 1; parameterIndex <= mappingCount; parameterIndex++) {
+            ColumnTableDto dto = indexMap.get(parameterIndex);
+            if (!needEncrypt(cryptoService, dto, dsId)) {
+                continue;
+            }
+            String encrypted = encryptValue(cryptoService, dto, plain, dsId, parameterIndex);
+            if (encrypted != null && !encrypted.equals(plain)) {
+                if (log.isDebugEnabled()) {
+                    log.debug("Encrypted scalar MyBatis parameter [index={}, table={}, column={}, datasource-id={}]",
+                            parameterIndex, dto.getSourceTableName(), dto.getSourceColumn(), dsId);
+                }
+                return encrypted;
+            }
+        }
+        return null;
+    }
+
+    /**
+     * 加密参数对象上的字段
+     * <p>
+     * 属性值可能来自两处：参数对象本身（{@code #{phone}}、{@code #{user.phone}}）或 BoundSql
+     * 附加参数（动态 SQL / {@code foreach} 产生的 {@code __frch_item_0}、{@code __frch_item_0.phone}）。
+ * 两者都按 MyBatis 的 MetaObject 语义读写，因此 {@code foreach} 中的实体元素同样可被加密。
+ * MyBatis-Plus Wrapper 条件值（{@code ew.paramNameValuePairs.MPGENVALn}）由
+ * {@link MpWrapperParamSupport} 直接改写 Map，避免深层 MetaObject 路径漏加密。
+ * </p>
+     *
+     * @param parameter    MyBatis 参数对象，可能是实体、Map 或 {@code ParamMap}，可为 null
+     * @param boundSql     当前语句的 BoundSql，不能为 null
+     * @param parseResult  core 的 SQL 解析结果（key 为占位符映射）
+     * @param datasourceId 数据源标识，为空按默认数据源处理
+     * @return 被改写的属性路径 → 原始明文，供 {@link #restoreParameters} 还原；无改写时返回空 Map
+     */
+    public static Map<String, Object> encryptParameters(Object parameter, BoundSql boundSql,
+                                                       Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseResult,
+                                                       String datasourceId) {
+        if (boundSql == null) {
+            return Collections.emptyMap();
+        }
+        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
+        if (parameterMappings == null || parameterMappings.isEmpty()) {
+            return Collections.emptyMap();
+        }
+        Map<Integer, ColumnTableDto> indexMap = buildParameterIndexMap(parseResult);
+        if (indexMap.isEmpty()) {
+            return Collections.emptyMap();
+        }
+
+        String dsId = normalizeDatasourceId(datasourceId);
+        FieldCryptoService cryptoService = FieldCryptoServiceHolder.get();
+        MetaObject parameterMetaObject = parameter == null ? null : SystemMetaObject.forObject(parameter);
+        Map<String, Object> originals = new LinkedHashMap<>();
+
+        try {
+            for (int i = 0; i < parameterMappings.size(); i++) {
+                int parameterIndex = i + 1;
+                ColumnTableDto dto = indexMap.get(parameterIndex);
+                if (!needEncrypt(cryptoService, dto, dsId)) {
+                    continue;
+                }
+
+                String property = parameterMappings.get(i).getProperty();
+                if (StrUtil.isBlank(property)) {
+                    continue;
+                }
+                if (originals.containsKey(property)) {
+                    // 同一属性被多个占位符引用时只加密一次，避免二次加密
+                    if (log.isDebugEnabled()) {
+                        log.debug("Property already encrypted, skip [property={}, index={}]", property, parameterIndex);
+                    }
+                    continue;
+                }
+
+                Object raw = readValue(parameter, parameterMetaObject, boundSql, property);
+                if (raw == null) {
+                    continue;
+                }
+                if (!(raw instanceof String)) {
+                    if (log.isDebugEnabled()) {
+                        log.debug("Skip non-String parameter [property={}, index={}, type={}]",
+                                property, parameterIndex, raw.getClass().getName());
+                    }
+                    continue;
+                }
+
+                String plain = (String) raw;
+                String encrypted = encryptValue(cryptoService, dto, plain, dsId, parameterIndex);
+                if (encrypted == null || encrypted.equals(plain)) {
+                    continue;
+                }
+                if (writeValue(parameter, parameterMetaObject, boundSql, property, encrypted)) {
+                    originals.put(property, plain);
+                    if (log.isDebugEnabled()) {
+                        log.debug("Encrypted MyBatis parameter [property={}, index={}, table={}, column={}, datasource-id={}]",
+                                property, parameterIndex, dto.getSourceTableName(), dto.getSourceColumn(), dsId);
+                    }
+                }
+            }
+        } catch (RuntimeException e) {
+            // 中途失败时先还原已改写的属性，避免业务对象残留密文
+            restoreParameters(parameter, parameterMetaObject, boundSql, originals);
+            throw e;
+        }
+
+        return originals;
+    }
+
+    /**
+     * 还原被加密改写的参数
+     *
+     * @param parameter MyBatis 参数对象，可为 null
+     * @param boundSql  当前语句的 BoundSql，可为 null
+     * @param originals {@link #encryptParameters} 返回的属性路径 → 原始明文
+     */
+    public static void restoreParameters(Object parameter, BoundSql boundSql, Map<String, Object> originals) {
+        if (originals == null || originals.isEmpty()) {
+            return;
+        }
+        restoreParameters(parameter,
+                parameter == null ? null : SystemMetaObject.forObject(parameter),
+                boundSql,
+                originals);
+    }
+
+    /**
+     * 判断是否存在「仅写入 BoundSql 附加参数」的改写
+     * <p>
+     * {@code foreach} 中的不可变元素（如 {@code List<String>} 的 IN 条件）只能写进 BoundSql
+     * 的附加参数，调用方必须保证下游执行使用的是同一个 BoundSql 实例，否则改写会丢失。
+     * </p>
+     * <p>
+     * MP Wrapper 的 {@code ew.paramNameValuePairs.*} 写在 Wrapper 自身的 Map 上，不需要透传 BoundSql。
+     * </p>
+     *
+     * @param boundSql  当前语句的 BoundSql
+     * @param originals {@link #encryptParameters} 的返回值
+     * @return true 表示需要把改写后的 BoundSql 透传给下游执行
+     */
+    public static boolean hasBoundSqlOnlyRewrite(BoundSql boundSql, Map<String, Object> originals) {
+        if (boundSql == null || originals == null || originals.isEmpty()) {
+            return false;
+        }
+        for (String property : originals.keySet()) {
+            if (MpWrapperParamSupport.isWrapperParamProperty(property)) {
+                continue;
+            }
+            if (property.indexOf('.') < 0 && hasAdditionalParameter(boundSql, property)) {
+                return true;
+            }
+        }
+        return false;
+    }
+
+    private static void restoreParameters(Object parameter, MetaObject parameterMetaObject, BoundSql boundSql,
+                                          Map<String, Object> originals) {
+        if (originals == null || originals.isEmpty()) {
+            return;
+        }
+        for (Map.Entry<String, Object> entry : originals.entrySet()) {
+            if (!writeValue(parameter, parameterMetaObject, boundSql, entry.getKey(), entry.getValue())) {
+                log.warn("【securt-kit】Failed to restore plain value for property: {}", entry.getKey());
+            }
+        }
+    }
+
+    private static boolean needEncrypt(FieldCryptoService cryptoService, ColumnTableDto dto, String datasourceId) {
+        if (dto == null || StrUtil.isBlank(dto.getSourceTableName()) || StrUtil.isBlank(dto.getSourceColumn())) {
+            return false;
+        }
+        return cryptoService.needEncrypt(dto.getSourceTableName(), dto.getSourceColumn(), datasourceId);
+    }
+
+    /**
+     * 执行单个值的加密（含 LIKE 前置处理）
+     *
+     * @return 密文；LIKE 处理器要求跳过时返回 null
+     */
+    private static String encryptValue(FieldCryptoService cryptoService, ColumnTableDto dto,
+                                       String plain, String datasourceId, int parameterIndex) {
+        String table = dto.getSourceTableName();
+        String column = dto.getSourceColumn();
+        String plainToEncrypt = plain;
+
+        if (dto.getMatchType() == ParameterMatchType.LIKE) {
+            LikeHandleResult likeResult = LikePatternHandlerHolder.getHandler().handle(
+                    plain, new LikeHandleContext(table, column, datasourceId, parameterIndex));
+            if (likeResult == null || likeResult.getAction() == LikeHandleResult.Action.SKIP) {
+                log.debug("LIKE handler skip encryption [table={}, column={}, index={}]: {}",
+                        table, column, parameterIndex,
+                        likeResult != null ? likeResult.getMessage() : "null result");
+                return null;
+            }
+            if (likeResult.getAction() == LikeHandleResult.Action.REJECT) {
+                throw new IllegalArgumentException(likeResult.getMessage() != null
+                        ? likeResult.getMessage()
+                        : "LIKE pattern rejected by LikePatternHandler");
+            }
+            plainToEncrypt = likeResult.getValueForEncrypt();
+        }
+
+        return cryptoService.encrypt(table, column, plainToEncrypt, datasourceId);
+    }
+
+    /**
+     * 构建占位符序号到字段信息的映射，与 JDBC 通道保持一致（序号从 1 开始）
+     */
+    private static Map<Integer, ColumnTableDto> buildParameterIndexMap(
+            Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseResult) {
+        if (parseResult == null || parseResult.getKey() == null || parseResult.getKey().isEmpty()) {
+            return Collections.emptyMap();
+        }
+        Map<Integer, ColumnTableDto> indexMap = new HashMap<>();
+        for (ColumnTableDto dto : parseResult.getKey().values()) {
+            if (dto == null) {
+                continue;
+            }
+            Integer index = dto.getInsertFieldIndex();
+            if (index != null && index > 0) {
+                indexMap.putIfAbsent(index, dto);
+            }
+        }
+        return indexMap;
+    }
+
+    /**
+     * 读取属性值
+     * <p>
+     * 优先走 MP Wrapper 的 {@code paramNameValuePairs} 直读；再尝试 BoundSql 附加参数与 MetaObject。
+     * MetaObject 对 Map 根对象的复合路径可能抛异常，捕获后按「跳过」处理。
+     * </p>
+     */
+    private static Object readValue(Object parameter, MetaObject parameterMetaObject,
+                                    BoundSql boundSql, String property) {
+        if (MpWrapperParamSupport.isWrapperParamProperty(property)) {
+            Object wrapperValue = MpWrapperParamSupport.read(parameter, property);
+            if (wrapperValue != null) {
+                return wrapperValue;
+            }
+        }
+        if (hasAdditionalParameter(boundSql, property)) {
+            try {
+                return boundSql.getAdditionalParameter(property);
+            } catch (Exception e) {
+                log.debug("Failed to read additional parameter [property={}]: {}", property, e.getMessage());
+            }
+        }
+        if (parameterMetaObject == null) {
+            return null;
+        }
+        try {
+            return parameterMetaObject.getValue(property);
+        } catch (Exception e) {
+            if (log.isDebugEnabled()) {
+                log.debug("Parameter property not readable, skip [property={}]: {}", property, e.getMessage());
+            }
+            return null;
+        }
+    }
+
+    /**
+     * 写入属性值
+     * <p>
+     * MP Wrapper 路径会同时尝试写入 {@code paramNameValuePairs} 与 BoundSql 附加参数，
+     * 保证 ParameterHandler 无论从哪一侧取值都能拿到密文。
+     * </p>
+     */
+    private static boolean writeValue(Object parameter, MetaObject parameterMetaObject, BoundSql boundSql,
+                                      String property, Object value) {
+        boolean written = false;
+
+        if (MpWrapperParamSupport.isWrapperParamProperty(property)) {
+            if (MpWrapperParamSupport.write(parameter, property, value)) {
+                written = true;
+            }
+            // BoundSql 附加参数里若已有 ew 根对象，也同步一份（与 Wrapper Map 通常是同一引用）
+            if (hasAdditionalParameter(boundSql, property)) {
+                try {
+                    boundSql.setAdditionalParameter(property, value);
+                    written = true;
+                } catch (Exception e) {
+                    log.debug("Failed to write Wrapper value to additional parameter [property={}]: {}",
+                            property, e.getMessage());
+                }
+            }
+            if (written) {
+                return true;
+            }
+        }
+
+        if (hasAdditionalParameter(boundSql, property)) {
+            try {
+                boundSql.setAdditionalParameter(property, value);
+                return true;
+            } catch (Exception e) {
+                log.debug("Failed to write additional parameter [property={}]: {}", property, e.getMessage());
+            }
+        }
+        if (parameterMetaObject == null) {
+            return false;
+        }
+        try {
+            parameterMetaObject.setValue(property, value);
+            return true;
+        } catch (Exception e) {
+            if (log.isDebugEnabled()) {
+                log.debug("Parameter property not writable, skip [property={}]: {}", property, e.getMessage());
+            }
+            return false;
+        }
+    }
+
+    private static boolean hasAdditionalParameter(BoundSql boundSql, String property) {
+        if (boundSql == null || StrUtil.isBlank(property)) {
+            return false;
+        }
+        try {
+            return boundSql.hasAdditionalParameter(property);
+        } catch (Exception e) {
+            return false;
+        }
+    }
+
+    private static String normalizeDatasourceId(String datasourceId) {
+        return StrUtil.isBlank(datasourceId) ? DatasourceIdResolver.DEFAULT_DATASOURCE_ID : datasourceId;
+    }
+}
diff --git a/securt-kit-mybatis/src/main/java/io/github/hexlodev/mybatis/ResultDecryptHelper.java b/securt-kit-mybatis/src/main/java/io/github/hexlodev/mybatis/ResultDecryptHelper.java
new file mode 100644
index 0000000..7dd15a6
--- /dev/null
+++ b/securt-kit-mybatis/src/main/java/io/github/hexlodev/mybatis/ResultDecryptHelper.java
@@ -0,0 +1,371 @@
+package io.github.hexlodev.mybatis;
+
+import cn.hutool.core.lang.Pair;
+import cn.hutool.core.util.StrUtil;
+import io.github.hexlodev.core.crypto.FieldCryptoService;
+import io.github.hexlodev.core.crypto.FieldCryptoServiceHolder;
+import io.github.hexlodev.core.digest.DigestReadSupport;
+import io.github.hexlodev.core.parser.dto.ColumnTableDto;
+import io.github.hexlodev.core.parser.dto.FieldEncryptorInfoDto;
+import lombok.extern.slf4j.Slf4j;
+import org.apache.ibatis.reflection.MetaObject;
+import org.apache.ibatis.reflection.SystemMetaObject;
+
+import java.util.Collection;
+import java.util.Collections;
+import java.util.HashMap;
+import java.util.LinkedHashMap;
+import java.util.LinkedHashSet;
+import java.util.List;
+import java.util.Locale;
+import java.util.Map;
+import java.util.Set;
+
+/**
+ * MyBatis 结果解密助手
+ * <p>
+ * 依据 core 的 SQL 解析结果（{@code Pair.getValue()} 中的 {@link FieldEncryptorInfoDto}）
+ * 对映射结果进行解密，覆盖三类返回值：
+ * </p>
+ * <ul>
+ *   <li>实体（含 List&lt;实体&gt;）：按列名 / 驼峰属性名定位 setter（P0）</li>
+ *   <li>Map（含 List&lt;Map&gt;）：按列名（忽略大小写）与驼峰 key 定位（P1）</li>
+ *   <li>标量与 JDK 内置类型：跳过，避免误改</li>
+ * </ul>
+ * <p>
+ * 解密统一走 {@code FieldCryptoService} 门面，失败按配置的失败策略降级（迁移期兼容明文）。
+ * </p>
+ *
+ * @author hexlodev
+ * @since 1.3.0
+ */
+@Slf4j
+public final class ResultDecryptHelper {
+
+    private ResultDecryptHelper() {
+    }
+
+    /**
+     * 解密查询结果
+     *
+     * @param result       Executor 返回的结果对象，通常是 {@code List}
+     * @param parseResult  core 的 SQL 解析结果（value 为需要解密的字段清单）
+     * @param datasourceId 数据源标识，为空按默认数据源处理
+     */
+    public static void decryptResult(Object result,
+                                    Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseResult,
+                                    String datasourceId) {
+        decryptResult(result, parseResult, Collections.<String>emptySet(), datasourceId);
+    }
+
+    /**
+     * 解密查询结果后，使用同一行中的摘要目标列校验明文源字段。
+     */
+    public static void decryptResult(
+            Object result,
+            Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseResult,
+            Set<String> tables,
+            String datasourceId) {
+        if (result == null || parseResult == null) {
+            return;
+        }
+        List<FieldEncryptorInfoDto> fields = parseResult.getValue();
+        String dsId = StrUtil.isBlank(datasourceId) ? DatasourceIdResolver.DEFAULT_DATASOURCE_ID : datasourceId;
+        List<FieldEncryptorInfoDto> safeFields = fields == null
+                ? Collections.<FieldEncryptorInfoDto>emptyList() : fields;
+        Map<String, FieldEncryptorInfoDto> nameToField = buildNameIndex(safeFields);
+        if (!nameToField.isEmpty()) {
+            decryptObject(result, safeFields, nameToField, dsId);
+        }
+        verifyDigest(result, safeFields, tables, dsId);
+    }
+
+    /**
+     * 下划线转驼峰
+     * <p>
+     * 不含下划线时原样返回（{@code phone} → {@code phone}、{@code userName} → {@code userName}），
+     * 含下划线时按 MyBatis {@code mapUnderscoreToCamelCase} 的习惯转换（{@code user_name} → {@code userName}）。
+     * </p>
+     *
+     * @param name 列名，可为 null
+     * @return 驼峰形式的属性名
+     */
+    public static String underlineToCamel(String name) {
+        if (name == null || name.indexOf('_') < 0) {
+            return name;
+        }
+        StringBuilder sb = new StringBuilder(name.length());
+        boolean upperNext = false;
+        for (int i = 0; i < name.length(); i++) {
+            char c = name.charAt(i);
+            if (c == '_') {
+                upperNext = true;
+                continue;
+            }
+            if (upperNext) {
+                sb.append(Character.toUpperCase(c));
+                upperNext = false;
+            } else {
+                sb.append(Character.toLowerCase(c));
+            }
+        }
+        return sb.toString();
+    }
+
+    @SuppressWarnings("unchecked")
+    private static void decryptObject(Object target, List<FieldEncryptorInfoDto> fields,
+                                      Map<String, FieldEncryptorInfoDto> nameToField, String datasourceId) {
+        if (target == null || isSkippableType(target)) {
+            return;
+        }
+        if (target instanceof Collection) {
+            for (Object element : (Collection<Object>) target) {
+                decryptObject(element, fields, nameToField, datasourceId);
+            }
+            return;
+        }
+        if (target instanceof Map) {
+            decryptMap((Map<Object, Object>) target, nameToField, datasourceId);
+            return;
+        }
+        decryptEntity(target, fields, datasourceId);
+    }
+
+    @SuppressWarnings("unchecked")
+    private static void verifyDigest(Object target, List<FieldEncryptorInfoDto> fields,
+                                     Set<String> tables, String datasourceId) {
+        if (target == null || tables == null || tables.isEmpty() || isSkippableType(target)) {
+            return;
+        }
+        if (target instanceof Collection) {
+            for (Object element : (Collection<Object>) target) {
+                verifyDigest(element, fields, tables, datasourceId);
+            }
+            return;
+        }
+
+        Set<String> required = DigestReadSupport.requiredColumns(tables, datasourceId);
+        if (required.isEmpty()) {
+            return;
+        }
+        Map<String, String> values = target instanceof Map
+                ? digestValuesFromMap((Map<Object, Object>) target, fields, required)
+                : digestValuesFromEntity(target, fields, required);
+        DigestReadSupport.verifyResultRow(tables, datasourceId, values);
+    }
+
+    private static Map<String, String> digestValuesFromMap(
+            Map<Object, Object> row, List<FieldEncryptorInfoDto> fields, Set<String> required) {
+        Map<String, String> values = new LinkedHashMap<String, String>();
+        for (String column : required) {
+            ValueLookup lookup = findMapValue(row, candidateNames(column, fields));
+            if (lookup.found) {
+                values.put(column, lookup.value == null ? null : String.valueOf(lookup.value));
+            }
+        }
+        return values;
+    }
+
+    private static Map<String, String> digestValuesFromEntity(
+            Object entity, List<FieldEncryptorInfoDto> fields, Set<String> required) {
+        Map<String, String> values = new LinkedHashMap<String, String>();
+        MetaObject metaObject;
+        try {
+            metaObject = SystemMetaObject.forObject(entity);
+        } catch (Exception e) {
+            return values;
+        }
+        for (String column : required) {
+            for (String property : candidateNames(column, fields)) {
+                if (!metaObject.hasGetter(property)) {
+                    continue;
+                }
+                try {
+                    Object value = metaObject.getValue(property);
+                    values.put(column, value == null ? null : String.valueOf(value));
+                    break;
+                } catch (Exception e) {
+                    log.debug("Failed to read digest result property [property={}]: {}",
+                            property, e.getMessage());
+                }
+            }
+        }
+        return values;
+    }
+
+    private static Set<String> candidateNames(String column, List<FieldEncryptorInfoDto> fields) {
+        Set<String> candidates = new LinkedHashSet<String>();
+        addCandidate(candidates, column);
+        addCandidate(candidates, underlineToCamel(column));
+        for (FieldEncryptorInfoDto field : fields) {
+            if (field != null && column.equalsIgnoreCase(field.getSourceColumn())) {
+                candidates.addAll(nameCandidates(field));
+            }
+        }
+        return candidates;
+    }
+
+    private static ValueLookup findMapValue(Map<Object, Object> row, Set<String> candidates) {
+        for (Map.Entry<Object, Object> entry : row.entrySet()) {
+            if (entry.getKey() == null) {
+                continue;
+            }
+            for (String candidate : candidates) {
+                if (candidate.equalsIgnoreCase(entry.getKey().toString())) {
+                    return new ValueLookup(true, entry.getValue());
+                }
+            }
+        }
+        return ValueLookup.NOT_FOUND;
+    }
+
+    /**
+     * Map 结果解密：按列名 / 源列名 / 驼峰 key 匹配（忽略大小写）
+     */
+    private static void decryptMap(Map<Object, Object> map, Map<String, FieldEncryptorInfoDto> nameToField,
+                                   String datasourceId) {
+        if (map.isEmpty()) {
+            return;
+        }
+        FieldCryptoService cryptoService = FieldCryptoServiceHolder.get();
+        for (Map.Entry<Object, Object> entry : map.entrySet()) {
+            Object key = entry.getKey();
+            Object value = entry.getValue();
+            if (key == null || !(value instanceof String)) {
+                continue;
+            }
+            FieldEncryptorInfoDto field = nameToField.get(normalize(key.toString()));
+            if (field == null) {
+                continue;
+            }
+            String decrypted = decrypt(cryptoService, field, (String) value, datasourceId);
+            if (decrypted != null && !decrypted.equals(value)) {
+                entry.setValue(decrypted);
+            }
+        }
+    }
+
+    /**
+     * 实体结果解密：按列名、源列名及其驼峰形式尝试定位属性
+     */
+    private static void decryptEntity(Object entity, List<FieldEncryptorInfoDto> fields, String datasourceId) {
+        MetaObject metaObject;
+        try {
+            metaObject = SystemMetaObject.forObject(entity);
+        } catch (Exception e) {
+            log.debug("Failed to create MetaObject for result [type={}]: {}",
+                    entity.getClass().getName(), e.getMessage());
+            return;
+        }
+
+        FieldCryptoService cryptoService = FieldCryptoServiceHolder.get();
+        for (FieldEncryptorInfoDto field : fields) {
+            // 候选属性名：列名 / 源列名及其驼峰形式（MyBatis 反射按大小写敏感匹配）
+            for (String property : nameCandidates(field)) {
+                if (!metaObject.hasGetter(property) || !metaObject.hasSetter(property)) {
+                    continue;
+                }
+                Object value;
+                try {
+                    value = metaObject.getValue(property);
+                } catch (Exception e) {
+                    log.debug("Failed to read result property [property={}]: {}", property, e.getMessage());
+                    continue;
+                }
+                if (!(value instanceof String)) {
+                    // 属性存在但不是字符串，无需继续尝试其他候选名
+                    break;
+                }
+                String decrypted = decrypt(cryptoService, field, (String) value, datasourceId);
+                if (decrypted != null && !decrypted.equals(value)) {
+                    try {
+                        metaObject.setValue(property, decrypted);
+                    } catch (Exception e) {
+                        log.debug("Failed to write decrypted value [property={}]: {}", property, e.getMessage());
+                    }
+                }
+                break;
+            }
+        }
+    }
+
+    private static String decrypt(FieldCryptoService cryptoService, FieldEncryptorInfoDto field,
+                                  String cipher, String datasourceId) {
+        try {
+            return cryptoService.decrypt(field.getSourceTableName(), field.getSourceColumn(), cipher, datasourceId);
+        } catch (Exception e) {
+            if (log.isDebugEnabled()) {
+                log.debug("Failed to decrypt result field [table={}, column={}], using original value: {}",
+                        field.getSourceTableName(), field.getSourceColumn(), e.getMessage());
+            }
+            return cipher;
+        }
+    }
+
+    /**
+     * 构建「列名（规范化） → 字段信息」索引，覆盖别名、源列名及其驼峰形式
+     */
+    private static Map<String, FieldEncryptorInfoDto> buildNameIndex(List<FieldEncryptorInfoDto> fields) {
+        Map<String, FieldEncryptorInfoDto> index = new HashMap<>();
+        for (FieldEncryptorInfoDto field : fields) {
+            if (field == null || StrUtil.isBlank(field.getSourceTableName())
+                    || StrUtil.isBlank(field.getSourceColumn())) {
+                continue;
+            }
+            for (String candidate : nameCandidates(field)) {
+                index.putIfAbsent(normalize(candidate), field);
+            }
+        }
+        return index;
+    }
+
+    private static Set<String> nameCandidates(FieldEncryptorInfoDto field) {
+        Set<String> candidates = new LinkedHashSet<>(4);
+        addCandidate(candidates, field.getColumnName());
+        addCandidate(candidates, underlineToCamel(field.getColumnName()));
+        addCandidate(candidates, field.getSourceColumn());
+        addCandidate(candidates, underlineToCamel(field.getSourceColumn()));
+        return candidates;
+    }
+
+    private static void addCandidate(Set<String> candidates, String name) {
+        if (StrUtil.isNotBlank(name)) {
+            candidates.add(name.trim());
+        }
+    }
+
+    private static String normalize(String name) {
+        return name == null ? null : name.trim().toLowerCase(Locale.ROOT);
+    }
+
+    /**
+     * 判断是否为无需遍历的类型
+     * <p>
+     * 标量、日期、枚举、数组等不承载映射结果；JDK 内置类型（{@code java.*} / {@code javax.*}）
+     * 中除 Collection / Map 外一律跳过，避免对 MyBatis 之外的对象做反射改写。
+     * </p>
+     */
+    private static boolean isSkippableType(Object target) {
+        if (target instanceof Collection || target instanceof Map) {
+            return false;
+        }
+        Class<?> clazz = target.getClass();
+        if (clazz.isArray() || clazz.isEnum() || clazz.isPrimitive()) {
+            return true;
+        }
+        String name = clazz.getName();
+        return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jakarta.");
+    }
+
+    private static final class ValueLookup {
+        private static final ValueLookup NOT_FOUND = new ValueLookup(false, null);
+
+        private final boolean found;
+        private final Object value;
+
+        private ValueLookup(boolean found, Object value) {
+            this.found = found;
+            this.value = value;
+        }
+    }
+}
diff --git a/securt-kit-mybatis/src/test/java/io/github/hexlodev/mybatis/DigestParamHelperTest.java b/securt-kit-mybatis/src/test/java/io/github/hexlodev/mybatis/DigestParamHelperTest.java
new file mode 100644
index 0000000..dbb47f0
--- /dev/null
+++ b/securt-kit-mybatis/src/test/java/io/github/hexlodev/mybatis/DigestParamHelperTest.java
@@ -0,0 +1,180 @@
+package io.github.hexlodev.mybatis;
+
+import cn.hutool.core.lang.Pair;
+import io.github.hexlodev.core.cache.StrategyCache;
+import io.github.hexlodev.core.config.DigestConfigRegistry;
+import io.github.hexlodev.core.config.FieldEncryptorProperties;
+import io.github.hexlodev.core.digest.ResolvedDigestRule;
+import io.github.hexlodev.core.exception.DigestMismatchException;
+import io.github.hexlodev.core.parser.dto.ColumnTableDto;
+import io.github.hexlodev.core.parser.dto.FieldEncryptorInfoDto;
+import io.github.hexlodev.core.strategy.HmacSha256DigestStrategy;
+import org.apache.ibatis.builder.StaticSqlSource;
+import org.apache.ibatis.mapping.BoundSql;
+import org.apache.ibatis.mapping.ParameterMapping;
+import org.apache.ibatis.session.Configuration;
+import org.junit.jupiter.api.BeforeEach;
+import org.junit.jupiter.api.Test;
+
+import java.util.ArrayList;
+import java.util.Arrays;
+import java.util.Collections;
+import java.util.LinkedHashMap;
+import java.util.List;
+import java.util.Map;
+
+import static org.junit.jupiter.api.Assertions.assertEquals;
+import static org.junit.jupiter.api.Assertions.assertFalse;
+import static org.junit.jupiter.api.Assertions.assertThrows;
+import static org.junit.jupiter.api.Assertions.assertTrue;
+
+class DigestParamHelperTest {
+
+    private static final String TABLE = "user_account";
+    private static final String DATASOURCE = "test";
+
+    private HmacSha256DigestStrategy strategy;
+
+    @BeforeEach
+    void setUp() {
+        DigestConfigRegistry.clear();
+        StrategyCache.clear();
+        strategy = new HmacSha256DigestStrategy("mybatis-digest-secret");
+        StrategyCache.registerStrategy(HmacSha256DigestStrategy.class, strategy);
+        DigestConfigRegistry.register(DATASOURCE, TABLE, Collections.singletonList(
+                new ResolvedDigestRule(
+                        TABLE,
+                        Arrays.asList("phone", "id_card"),
+                        "row_digest",
+                        HmacSha256DigestStrategy.class,
+                        FieldEncryptorProperties.PartialUpdate.FAIL,
+                        true,
+                        FieldEncryptorProperties.FailurePolicy.FAIL_FAST)));
+    }
+
+    @Test
+    void appendsInsertDigestSqlMappingAndAdditionalParameter() {
+        Map<String, Object> parameter = new LinkedHashMap<String, Object>();
+        parameter.put("phone", "13800138000");
+        parameter.put("idCard", "110101199001011234");
+        BoundSql boundSql = boundSql(
+                "INSERT INTO user_account(phone, id_card) VALUES (?, ?)",
+                Arrays.asList("phone", "idCard"),
+                parameter);
+
+        boolean rewritten = ParameterEncryptHelper.applyDigests(
+                parameter,
+                boundSql,
+                pair(column(1, "phone"), column(2, "id_card")),
+                Collections.singleton(TABLE),
+                DATASOURCE);
+
+        Map<String, String> sourceValues = new LinkedHashMap<String, String>();
+        sourceValues.put("phone", "13800138000");
+        sourceValues.put("id_card", "110101199001011234");
+        String expected = strategy.digest(sourceValues);
+
+        assertTrue(rewritten);
+        assertTrue(boundSql.getSql().toLowerCase().contains("row_digest"));
+        assertEquals(3, boundSql.getParameterMappings().size());
+        assertEquals("__securtkit_digest_row_digest",
+                boundSql.getParameterMappings().get(2).getProperty());
+        assertEquals(expected,
+                boundSql.getAdditionalParameter("__securtkit_digest_row_digest"));
+    }
+
+    @Test
+    void skipsUncomputedDigestTargetWithoutAddingUnboundPlaceholder() {
+        DigestConfigRegistry.clear();
+        DigestConfigRegistry.register(DATASOURCE, TABLE, Arrays.asList(
+                new ResolvedDigestRule(
+                        TABLE,
+                        Collections.singletonList("phone"),
+                        "phone_digest",
+                        HmacSha256DigestStrategy.class,
+                        FieldEncryptorProperties.PartialUpdate.SKIP,
+                        true,
+                        FieldEncryptorProperties.FailurePolicy.FAIL_FAST),
+                new ResolvedDigestRule(
+                        TABLE,
+                        Collections.singletonList("id_card"),
+                        "id_card_digest",
+                        HmacSha256DigestStrategy.class,
+                        FieldEncryptorProperties.PartialUpdate.SKIP,
+                        true,
+                        FieldEncryptorProperties.FailurePolicy.FAIL_FAST)));
+        Map<String, Object> parameter = new LinkedHashMap<String, Object>();
+        parameter.put("phone", "13800138000");
+        parameter.put("id", 1L);
+        BoundSql boundSql = boundSql(
+                "UPDATE user_account SET phone = ? WHERE id = ?",
+                Arrays.asList("phone", "id"),
+                parameter);
+
+        boolean rewritten = ParameterEncryptHelper.applyDigests(
+                parameter,
+                boundSql,
+                pair(column(1, "phone"), column(2, "id")),
+                Collections.singleton(TABLE),
+                DATASOURCE);
+
+        assertTrue(rewritten);
+        assertTrue(boundSql.getSql().toLowerCase().contains("phone_digest"));
+        assertFalse(boundSql.getSql().toLowerCase().contains("id_card_digest"));
+        assertEquals(boundSql.getParameterMappings().size(), countPlaceholders(boundSql.getSql()));
+        assertEquals(3, boundSql.getParameterMappings().size());
+    }
+
+    @Test
+    void verifiesMapDigestAfterResultDecryption() {
+        Map<String, String> sourceValues = new LinkedHashMap<String, String>();
+        sourceValues.put("phone", "13800138000");
+        sourceValues.put("id_card", "110101199001011234");
+        Map<String, Object> row = new LinkedHashMap<String, Object>(sourceValues);
+        row.put("row_digest", "tampered");
+
+        assertThrows(DigestMismatchException.class, () -> ResultDecryptHelper.decryptResult(
+                row,
+                Pair.of(Collections.<String, ColumnTableDto>emptyMap(),
+                        Collections.<FieldEncryptorInfoDto>emptyList()),
+                Collections.singleton(TABLE),
+                DATASOURCE));
+    }
+
+    private static int countPlaceholders(String sql) {
+        int count = 0;
+        for (int i = 0; i < sql.length(); i++) {
+            if (sql.charAt(i) == '?') {
+                count++;
+            }
+        }
+        return count;
+    }
+
+    private static BoundSql boundSql(String sql, List<String> properties, Object parameter) {
+        Configuration configuration = new Configuration();
+        List<ParameterMapping> mappings = new ArrayList<ParameterMapping>();
+        for (String property : properties) {
+            mappings.add(new ParameterMapping.Builder(configuration, property, Object.class).build());
+        }
+        return new StaticSqlSource(configuration, sql, mappings).getBoundSql(parameter);
+    }
+
+    private static Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> pair(
+            ColumnTableDto... columns) {
+        Map<String, ColumnTableDto> mappings = new LinkedHashMap<String, ColumnTableDto>();
+        for (ColumnTableDto column : columns) {
+            mappings.put(String.valueOf(column.getInsertFieldIndex()), column);
+        }
+        return Pair.of(mappings, Collections.<FieldEncryptorInfoDto>emptyList());
+    }
+
+    private static ColumnTableDto column(int index, String name) {
+        return ColumnTableDto.builder()
+                .sourceTableName(TABLE)
+                .sourceColumn(name)
+                .insertFieldIndex(index)
+                .fromSourceTable(true)
+                .build();
+    }
+}

