# Review Package Task 6 re-review
BASE: dc969fc4c34a7d7ae0678658a0172d0b3a2b382f
HEAD: 30f5127e44161b9fb9298327b3c77a9ef8ead48c
## Commits
30f5127 fix(digest): correct multi-target bind and RELOAD where params
58fdd4b feat(digest): wire JDBC write path with SQL rewrite and bind

## Stat
 .../hexlodev/core/digest/DigestWriteSupport.java   | 303 +++++++++++++++++++++
 .../interceptor/SimpleInterceptorConnection.java   |  70 +++--
 .../SimpleInterceptorPreparedStatement.java        | 298 +++++++++++++-------
 .../core/digest/DigestWriteSupportTest.java        | 246 +++++++++++++++++
 4 files changed, 800 insertions(+), 117 deletions(-)

## Diff
diff --git a/securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestWriteSupport.java b/securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestWriteSupport.java
new file mode 100644
index 0000000..2f7764a
--- /dev/null
+++ b/securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestWriteSupport.java
@@ -0,0 +1,303 @@
+package io.github.hexlodev.core.digest;
+
+import cn.hutool.core.lang.Pair;
+import io.github.hexlodev.core.config.DigestConfigRegistry;
+import io.github.hexlodev.core.exception.SecurtKitException;
+import io.github.hexlodev.core.parser.dto.ColumnTableDto;
+import io.github.hexlodev.core.parser.dto.FieldEncryptorInfoDto;
+import net.sf.jsqlparser.parser.CCJSqlParserUtil;
+import net.sf.jsqlparser.statement.Statement;
+import net.sf.jsqlparser.statement.insert.Insert;
+import net.sf.jsqlparser.statement.select.Values;
+import net.sf.jsqlparser.statement.update.Update;
+
+import java.sql.Connection;
+import java.sql.PreparedStatement;
+import java.sql.SQLException;
+import java.util.ArrayList;
+import java.util.Collections;
+import java.util.HashSet;
+import java.util.LinkedHashMap;
+import java.util.List;
+import java.util.Locale;
+import java.util.Map;
+import java.util.Set;
+
+/**
+ * JDBC 写路径的摘要 SQL 改写与参数绑定支持。
+ */
+public final class DigestWriteSupport {
+
+    private DigestWriteSupport() {
+    }
+
+    public static DigestRewriteResult rewriteForConfiguredDigests(
+            String sql, Set<String> tables, String datasourceId) {
+        if (!isSupportedWrite(sql)) {
+            return null;
+        }
+        String table = singleDigestTable(tables, datasourceId);
+        if (table == null) {
+            return null;
+        }
+        List<String> targets = new ArrayList<String>();
+        for (ResolvedDigestRule rule : DigestConfigRegistry.getRules(table, datasourceId)) {
+            targets.add(rule.getTargetField());
+        }
+        if (isMultiRowInsert(sql)) {
+            return DigestRewriteResult.notRewritten(
+                    sql, "Multi-row INSERT is not supported for digest rewrite");
+        }
+        return DigestSqlRewriter.tryAppendTargets(sql, targets);
+    }
+
+    public static Set<Integer> applyDigestsBeforeEncrypt(
+            String sql,
+            Set<String> tables,
+            String datasourceId,
+            Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> pair,
+            Map<Integer, Object> plainParameterValues,
+            Map<Integer, Object> storedParameterValues,
+            DigestRewriteResult rewriteResult,
+            PreparedStatement delegate) throws SQLException {
+        if (!isSupportedWrite(sql)
+                || (rewriteResult != null
+                && rewriteResult.getWarnMessage() != null
+                && rewriteResult.getWarnMessage().startsWith("Multi-row INSERT"))) {
+            return Collections.emptySet();
+        }
+        String table = singleDigestTable(tables, datasourceId);
+        if (table == null || delegate == null) {
+            return Collections.emptySet();
+        }
+
+        Map<Integer, ColumnTableDto> columnsByIndex = columnsByIndex(pair, table);
+        Map<String, String> availablePlain = new LinkedHashMap<String, String>();
+        // Digest input comes from preserved caller plaintext; setter-time field encryption
+        // may already have populated storedParameterValues with ciphertext.
+        if (plainParameterValues != null) {
+            for (Map.Entry<Integer, ColumnTableDto> entry : columnsByIndex.entrySet()) {
+                if (!plainParameterValues.containsKey(entry.getKey())) {
+                    continue;
+                }
+                Object value = plainParameterValues.get(entry.getKey());
+                availablePlain.put(entry.getValue().getSourceColumn(),
+                        value == null ? null : String.valueOf(value));
+            }
+        }
+
+        ReloadContext reload = reloadContext(sql, plainParameterValues, storedParameterValues);
+        Connection connection = reload.insert ? null : delegate.getConnection();
+        Map<String, String> digests = new DigestService().computeTargetDigests(
+                table,
+                datasourceId,
+                availablePlain,
+                reload.insert,
+                connection,
+                reload.whereSql,
+                reload.whereParams);
+        if (digests.isEmpty()) {
+            return Collections.emptySet();
+        }
+
+        Set<Integer> boundIndexes = new HashSet<Integer>();
+        Set<Integer> appendedIndexes = rewriteResult == null
+                ? Collections.<Integer>emptySet()
+                : new HashSet<Integer>(rewriteResult.getAppendedParameterIndexes());
+        for (Map.Entry<Integer, ColumnTableDto> entry : columnsByIndex.entrySet()) {
+            if (appendedIndexes.contains(entry.getKey())) {
+                continue;
+            }
+            String digest = getIgnoreCase(digests, entry.getValue().getSourceColumn());
+            if (digest != null) {
+                bind(delegate, plainParameterValues, storedParameterValues, entry.getKey(), digest);
+                boundIndexes.add(entry.getKey());
+            }
+        }
+
+        if (rewriteResult != null) {
+            List<Integer> indexes = rewriteResult.getAppendedParameterIndexes();
+            List<String> targets = rewriteResult.getAppendedTargetFields();
+            for (int position = 0;
+                 position < indexes.size() && position < targets.size();
+                 position++) {
+                Integer index = indexes.get(position);
+                String digest = getIgnoreCase(digests, targets.get(position));
+                if (index != null && digest != null && !boundIndexes.contains(index)) {
+                    bind(delegate, plainParameterValues, storedParameterValues, index, digest);
+                    boundIndexes.add(index);
+                }
+            }
+        }
+        return boundIndexes;
+    }
+
+    private static void bind(PreparedStatement delegate,
+                             Map<Integer, Object> plainParameterValues,
+                             Map<Integer, Object> storedParameterValues,
+                             int index,
+                             String digest) throws SQLException {
+        delegate.setString(index, digest);
+        if (plainParameterValues != null) {
+            plainParameterValues.put(index, digest);
+        }
+        if (storedParameterValues != null) {
+            storedParameterValues.put(index, digest);
+        }
+    }
+
+    private static Map<Integer, ColumnTableDto> columnsByIndex(
+            Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> pair, String table) {
+        if (pair == null || pair.getKey() == null) {
+            return Collections.emptyMap();
+        }
+        Map<Integer, ColumnTableDto> result = new LinkedHashMap<Integer, ColumnTableDto>();
+        for (ColumnTableDto column : pair.getKey().values()) {
+            if (column == null || column.getInsertFieldIndex() == null
+                    || column.getSourceColumn() == null
+                    || !equalsIgnoreCase(table, column.getSourceTableName())) {
+                continue;
+            }
+            result.putIfAbsent(column.getInsertFieldIndex(), column);
+        }
+        return result;
+    }
+
+    private static String singleDigestTable(Set<String> tables, String datasourceId) {
+        String matched = null;
+        if (tables != null) {
+            for (String table : tables) {
+                if (!DigestConfigRegistry.hasDigest(table, datasourceId)) {
+                    continue;
+                }
+                if (matched != null && !equalsIgnoreCase(matched, table)) {
+                    throw new SecurtKitException("Digest write supports a single configured table only");
+                }
+                matched = table;
+            }
+        }
+        return matched;
+    }
+
+    private static ReloadContext reloadContext(
+            String sql,
+            Map<Integer, Object> plainParameterValues,
+            Map<Integer, Object> storedParameterValues) {
+        try {
+            Statement statement = CCJSqlParserUtil.parse(sql);
+            if (statement instanceof Insert) {
+                return new ReloadContext(true, null, Collections.emptyList());
+            }
+            if (!(statement instanceof Update)) {
+                throw new SecurtKitException("Digest write supports INSERT and UPDATE only");
+            }
+            Update update = (Update) statement;
+            if (update.getWhere() == null) {
+                return new ReloadContext(false, null, Collections.emptyList());
+            }
+            String whereSql = update.getWhere().toString();
+            List<Object> whereParams = reloadWhereParameters(
+                    sql, plainParameterValues, storedParameterValues);
+            return new ReloadContext(false, whereSql, whereParams);
+        } catch (SecurtKitException e) {
+            throw e;
+        } catch (Exception e) {
+            throw new SecurtKitException("Cannot parse SQL for digest write", e);
+        }
+    }
+
+    static List<Object> reloadWhereParameters(
+            String sql,
+            Map<Integer, Object> plainParameterValues,
+            Map<Integer, Object> storedParameterValues) {
+        int whereParameterCount;
+        try {
+            Statement statement = CCJSqlParserUtil.parse(sql);
+            if (!(statement instanceof Update) || ((Update) statement).getWhere() == null) {
+                return Collections.emptyList();
+            }
+            whereParameterCount = countPlaceholders(((Update) statement).getWhere().toString());
+        } catch (Exception e) {
+            throw new SecurtKitException("Cannot parse SQL for digest write", e);
+        }
+        int totalParameterCount = countPlaceholders(sql);
+        List<Object> whereParams = new ArrayList<Object>();
+        for (int index = totalParameterCount - whereParameterCount + 1;
+             index <= totalParameterCount; index++) {
+            if (storedParameterValues != null && storedParameterValues.containsKey(index)) {
+                whereParams.add(storedParameterValues.get(index));
+            } else if (plainParameterValues != null && plainParameterValues.containsKey(index)) {
+                whereParams.add(plainParameterValues.get(index));
+            } else {
+                throw new SecurtKitException(
+                        "Cannot resolve UPDATE WHERE parameters for digest RELOAD");
+            }
+        }
+        return whereParams;
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
+    private static boolean isMultiRowInsert(String sql) {
+        try {
+            Statement statement = CCJSqlParserUtil.parse(sql);
+            if (!(statement instanceof Insert)) {
+                return false;
+            }
+            Insert insert = (Insert) statement;
+            if (!(insert.getSelect() instanceof Values)) {
+                return false;
+            }
+            Values values = (Values) insert.getSelect();
+            return values.getExpressions() != null
+                    && values.getExpressions().size() > 1
+                    && values.getExpressions().get(0)
+                    instanceof net.sf.jsqlparser.expression.operators.relational.ExpressionList;
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
+        return left != null && right != null
+                && left.toLowerCase(Locale.ROOT).equals(right.toLowerCase(Locale.ROOT));
+    }
+
+    private static final class ReloadContext {
+        private final boolean insert;
+        private final String whereSql;
+        private final List<Object> whereParams;
+
+        private ReloadContext(boolean insert, String whereSql, List<Object> whereParams) {
+            this.insert = insert;
+            this.whereSql = whereSql;
+            this.whereParams = whereParams;
+        }
+    }
+}
diff --git a/securt-kit-core/src/main/java/io/github/hexlodev/core/interceptor/SimpleInterceptorConnection.java b/securt-kit-core/src/main/java/io/github/hexlodev/core/interceptor/SimpleInterceptorConnection.java
index 3620926..1d6f1e2 100644
--- a/securt-kit-core/src/main/java/io/github/hexlodev/core/interceptor/SimpleInterceptorConnection.java
+++ b/securt-kit-core/src/main/java/io/github/hexlodev/core/interceptor/SimpleInterceptorConnection.java
@@ -1,8 +1,12 @@
 package io.github.hexlodev.core.interceptor;
 
-import cn.hutool.core.util.StrUtil;
+import io.github.hexlodev.core.config.ConfigInitializer;
+import io.github.hexlodev.core.config.EncryptModeHolder;
+import io.github.hexlodev.core.digest.DigestRewriteResult;
+import io.github.hexlodev.core.digest.DigestWriteSupport;
+import io.github.hexlodev.core.parser.SqlParseCache;
 import lombok.extern.slf4j.Slf4j;
 
 import java.sql.*;
 import java.util.Map;
 import java.util.Properties;
@@ -53,10 +57,42 @@ public class SimpleInterceptorConnection implements Connection {
     
     /**
      * 数据源标识（多数据源场景）
      */
     private final String datasourceId;
+
+    @FunctionalInterface
+    private interface PreparedStatementFactory {
+        PreparedStatement prepare(String sql) throws SQLException;
+    }
+
+    private PreparedStatement prepareIntercepted(
+            String sql, PreparedStatementFactory factory) throws SQLException {
+        String sqlToPrepare = sql;
+        DigestRewriteResult digestRewrite = null;
+        if (EncryptModeHolder.isJdbc() && !ConfigInitializer.shouldSkipByComment(sql)) {
+            try {
+                digestRewrite = DigestWriteSupport.rewriteForConfiguredDigests(
+                        sql, SqlParseCache.parseTableNames(sql), datasourceId);
+                if (digestRewrite != null && digestRewrite.isRewritten()) {
+                    sqlToPrepare = digestRewrite.getSql();
+                } else if (digestRewrite != null && digestRewrite.getWarnMessage() != null) {
+                    log.warn("Digest SQL was not rewritten: {}", digestRewrite.getWarnMessage());
+                }
+            } catch (RuntimeException e) {
+                log.warn("Failed to prepare digest SQL rewrite: {}", e.getMessage(), e);
+                throw e;
+            } catch (Exception e) {
+                throw new SQLException("Failed to parse SQL before digest rewrite", e);
+            }
+        }
+        PreparedStatement statement = factory.prepare(sqlToPrepare);
+        SimpleInterceptorPreparedStatement wrapped =
+                new SimpleInterceptorPreparedStatement(statement, sqlToPrepare, datasourceId);
+        wrapped.setDigestRewriteResult(digestRewrite);
+        return wrapped;
+    }
     
     /**
      * 构造函数（向后兼容）
      * 
      * <p>创建一个新的连接包装器，包装真实的数据库连接。
@@ -130,17 +166,14 @@ public class SimpleInterceptorConnection implements Connection {
      * @throws SQLException 如果创建PreparedStatement失败
      * @see SimpleInterceptorPreparedStatement
      */
     @Override
     public PreparedStatement prepareStatement(String sql) throws SQLException {
-        PreparedStatement statement = delegate.prepareStatement(sql);
         if (log.isDebugEnabled()) {
             log.debug("PreparedStatement created (datasource-id: {})", datasourceId);
         }
-        // 确保传递正确的 datasource-id
-        String dsId = StrUtil.isBlank(datasourceId) ? "default" : datasourceId;
-        return new SimpleInterceptorPreparedStatement(statement, sql, dsId);
+        return prepareIntercepted(sql, delegate::prepareStatement);
     }
     
     /**
      * 创建CallableStatement对象 - 拦截方法
      * 
@@ -255,17 +288,17 @@ public class SimpleInterceptorConnection implements Connection {
         return new SimpleInterceptorStatement(statement);
     }
     
     @Override
     public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
-        PreparedStatement statement = delegate.prepareStatement(sql, resultSetType, resultSetConcurrency);
         if (log.isTraceEnabled()) {
             log.trace("PreparedStatement created with type={}, concurrency={} (datasource-id: {})",
                     resultSetType, resultSetConcurrency, datasourceId);
         }
-        String dsId = StrUtil.isBlank(datasourceId) ? "default" : datasourceId;
-        return new SimpleInterceptorPreparedStatement(statement, sql, dsId);
+        return prepareIntercepted(sql,
+                preparedSql -> delegate.prepareStatement(
+                        preparedSql, resultSetType, resultSetConcurrency));
     }
     
     @Override
     public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
         CallableStatement statement = delegate.prepareCall(sql, resultSetType, resultSetConcurrency);
@@ -326,17 +359,17 @@ public class SimpleInterceptorConnection implements Connection {
         return new SimpleInterceptorStatement(statement);
     }
     
     @Override
     public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
-        PreparedStatement statement = delegate.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
         if (log.isTraceEnabled()) {
             log.trace("PreparedStatement created with type={}, concurrency={}, holdability={} (datasource-id: {})",
                     resultSetType, resultSetConcurrency, resultSetHoldability, datasourceId);
         }
-        String dsId = StrUtil.isBlank(datasourceId) ? "default" : datasourceId;
-        return new SimpleInterceptorPreparedStatement(statement, sql, dsId);
+        return prepareIntercepted(sql,
+                preparedSql -> delegate.prepareStatement(
+                        preparedSql, resultSetType, resultSetConcurrency, resultSetHoldability));
     }
     
     @Override
     public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
         CallableStatement statement = delegate.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
@@ -347,37 +380,34 @@ public class SimpleInterceptorConnection implements Connection {
         return new SimpleInterceptorCallableStatement(statement, sql);
     }
     
     @Override
     public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
-        PreparedStatement statement = delegate.prepareStatement(sql, autoGeneratedKeys);
         if (log.isTraceEnabled()) {
             log.trace("PreparedStatement created with autoGeneratedKeys={} (datasource-id: {})",
                     autoGeneratedKeys, datasourceId);
         }
-        String dsId = StrUtil.isBlank(datasourceId) ? "default" : datasourceId;
-        return new SimpleInterceptorPreparedStatement(statement, sql, dsId);
+        return prepareIntercepted(sql,
+                preparedSql -> delegate.prepareStatement(preparedSql, autoGeneratedKeys));
     }
     
     @Override
     public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
-        PreparedStatement statement = delegate.prepareStatement(sql, columnIndexes);
         if (log.isTraceEnabled()) {
             log.trace("PreparedStatement created with columnIndexes (datasource-id: {})", datasourceId);
         }
-        String dsId = StrUtil.isBlank(datasourceId) ? "default" : datasourceId;
-        return new SimpleInterceptorPreparedStatement(statement, sql, dsId);
+        return prepareIntercepted(sql,
+                preparedSql -> delegate.prepareStatement(preparedSql, columnIndexes));
     }
     
     @Override
     public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
-        PreparedStatement statement = delegate.prepareStatement(sql, columnNames);
         if (log.isTraceEnabled()) {
             log.trace("PreparedStatement created with columnNames (datasource-id: {})", datasourceId);
         }
-        String dsId = StrUtil.isBlank(datasourceId) ? "default" : datasourceId;
-        return new SimpleInterceptorPreparedStatement(statement, sql, dsId);
+        return prepareIntercepted(sql,
+                preparedSql -> delegate.prepareStatement(preparedSql, columnNames));
     }
     
     // JDBC 4.0 methods
     @Override
     public Clob createClob() throws SQLException {
diff --git a/securt-kit-core/src/main/java/io/github/hexlodev/core/interceptor/SimpleInterceptorPreparedStatement.java b/securt-kit-core/src/main/java/io/github/hexlodev/core/interceptor/SimpleInterceptorPreparedStatement.java
index 5396348..03a718d 100644
--- a/securt-kit-core/src/main/java/io/github/hexlodev/core/interceptor/SimpleInterceptorPreparedStatement.java
+++ b/securt-kit-core/src/main/java/io/github/hexlodev/core/interceptor/SimpleInterceptorPreparedStatement.java
@@ -1,10 +1,14 @@
 package io.github.hexlodev.core.interceptor;
 
 import cn.hutool.core.lang.Pair;
 import cn.hutool.core.util.StrUtil;
 import io.github.hexlodev.core.config.ConfigInitializer;
+import io.github.hexlodev.core.config.DigestConfigRegistry;
+import io.github.hexlodev.core.config.EncryptModeHolder;
+import io.github.hexlodev.core.digest.DigestRewriteResult;
+import io.github.hexlodev.core.digest.DigestWriteSupport;
 import io.github.hexlodev.core.parser.SecurtkitUtils;
 import io.github.hexlodev.core.parser.dto.ColumnTableDto;
 import io.github.hexlodev.core.parser.dto.FieldEncryptorInfoDto;
 import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;
 import lombok.extern.slf4j.Slf4j;
@@ -74,10 +78,17 @@ public class SimpleInterceptorPreparedStatement implements PreparedStatement {
      * key: 参数索引（从1开始，对应JDBC规范）
      * value: 参数值（已加密处理的值）
      */
     private final Map<Integer, Object> parameterValues = new LinkedHashMap<>();
 
+    /**
+     * 摘要计算使用的调用方原始参数，索引为改写后 SQL 的物理索引。
+     */
+    private final Map<Integer, Object> digestPlainParameterValues = new LinkedHashMap<>();
+
+    private DigestRewriteResult digestRewriteResult;
+
     /**
      * SQL解析结果，包含占位符到表字段的映射和需要加密的字段信息
      * 仅在表需要加密时才会进行解析
      */
     private Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> pair;
@@ -95,10 +106,54 @@ public class SimpleInterceptorPreparedStatement implements PreparedStatement {
     /**
      * SQL 执行器
      */
     private final SqlExecutor sqlExecutor;
 
+    public void setDigestRewriteResult(DigestRewriteResult digestRewriteResult) {
+        this.digestRewriteResult = digestRewriteResult;
+    }
+
+    private int delegateParameterIndex(int applicationIndex) {
+        int physicalIndex = applicationIndex;
+        if (digestRewriteResult == null) {
+            return physicalIndex;
+        }
+        for (Integer appendedIndex : digestRewriteResult.getAppendedParameterIndexes()) {
+            if (appendedIndex != null && appendedIndex <= physicalIndex) {
+                physicalIndex++;
+            }
+        }
+        return physicalIndex;
+    }
+
+    private boolean hasDigestConfiguration() {
+        for (String table : tables) {
+            if (DigestConfigRegistry.hasDigest(table, datasourceId)) {
+                return true;
+            }
+        }
+        return false;
+    }
+
+    private void applyDigestsBeforeExecute() throws SQLException {
+        if (skipEncrypt || !EncryptModeHolder.isJdbc()) {
+            return;
+        }
+        Set<Integer> digestIndexes = DigestWriteSupport.applyDigestsBeforeEncrypt(
+                sql,
+                tables,
+                datasourceId,
+                pair,
+                digestPlainParameterValues,
+                parameterValues,
+                digestRewriteResult,
+                delegate);
+        for (Integer index : digestIndexes) {
+            parameterValues.put(index, digestPlainParameterValues.get(index));
+        }
+    }
+
     @FunctionalInterface
     private interface SqlStringConsumer {
         void accept(String value) throws SQLException;
     }
 
@@ -204,11 +259,17 @@ public class SimpleInterceptorPreparedStatement implements PreparedStatement {
         }
 
         this.delegate = delegate;
         this.sql = sql.trim();
         this.datasourceId = StrUtil.isBlank(datasourceId) ? "default" : datasourceId;
-        this.skipEncrypt = ConfigInitializer.shouldSkipByComment(this.sql);
+        boolean skip = ConfigInitializer.shouldSkipByComment(this.sql);
+        if (!EncryptModeHolder.isJdbc()) {
+            // 非 JDBC 通道模式下，Driver 拦截仅透传，避免与 MyBatis 通道双重加密
+            skip = true;
+            log.debug("Encrypt mode is {}, JDBC interceptor passthrough", EncryptModeHolder.getMode());
+        }
+        this.skipEncrypt = skip;
         
         // 调试日志：记录 datasource-id 的来源
         if (StrUtil.isBlank(datasourceId)) {
             log.warn("PreparedStatement created with blank datasource-id, using default. SQL: {}", sql);
         } else {
@@ -233,11 +294,12 @@ public class SimpleInterceptorPreparedStatement implements PreparedStatement {
                         e.getMessage(), e);
                 this.tables = new HashSet<>();
             }
 
             // 如果表需要加密，则解析SQL获取字段映射关系（使用数据源标识）
-            if (SecurtkitUtils.needEncrypt(this.tables, this.datasourceId)) {
+            if (SecurtkitUtils.needEncrypt(this.tables, this.datasourceId)
+                    || hasDigestConfiguration()) {
                 try {
                     this.pair = SecurtkitUtils.parseSql(this.sql, this.datasourceId);
 
                     if (log.isDebugEnabled()) {
                         log.debug("SQL requires encryption [sql={}, tables={}, datasource-id={}, fieldsCount={}]",
@@ -274,10 +336,11 @@ public class SimpleInterceptorPreparedStatement implements PreparedStatement {
      * @return 查询结果集，已包装为支持自动解密的ResultSet
      * @throws SQLException 如果SQL执行失败
      */
     @Override
     public ResultSet executeQuery() throws SQLException {
+        applyDigestsBeforeExecute();
         return sqlExecutor.executeQuery(parameterValues);
     }
 
     /**
      * 执行更新SQL（INSERT/UPDATE/DELETE） - 拦截方法
@@ -287,10 +350,11 @@ public class SimpleInterceptorPreparedStatement implements PreparedStatement {
      * @return 受影响的行数
      * @throws SQLException 如果SQL执行失败
      */
     @Override
     public int executeUpdate() throws SQLException {
+        applyDigestsBeforeExecute();
         return sqlExecutor.executeUpdate(parameterValues);
     }
 
     /**
      * 执行SQL - 拦截方法
@@ -300,10 +364,11 @@ public class SimpleInterceptorPreparedStatement implements PreparedStatement {
      * @return 如果第一个结果是ResultSet对象则返回true，否则返回false
      * @throws SQLException 如果SQL执行失败
      */
     @Override
     public boolean execute() throws SQLException {
+        applyDigestsBeforeExecute();
         return sqlExecutor.execute(parameterValues);
     }
 
 
     /**
@@ -316,13 +381,15 @@ public class SimpleInterceptorPreparedStatement implements PreparedStatement {
      * @param x              参数值
      * @throws SQLException 如果设置参数失败
      */
     @Override
     public void setString(int parameterIndex, String x) throws SQLException {
-        String newValue = parameterEncryptor.encryptString(parameterIndex, x);
-        delegate.setString(parameterIndex, newValue);
-        parameterValues.put(parameterIndex, newValue);
+        int physicalIndex = delegateParameterIndex(parameterIndex);
+        String newValue = parameterEncryptor.encryptString(physicalIndex, x);
+        delegate.setString(physicalIndex, newValue);
+        parameterValues.put(physicalIndex, newValue);
+        digestPlainParameterValues.put(physicalIndex, x);
     }
 
     /**
      * 设置整数参数
      *
@@ -330,12 +397,14 @@ public class SimpleInterceptorPreparedStatement implements PreparedStatement {
      * @param x              参数值
      * @throws SQLException 如果设置参数失败
      */
     @Override
     public void setInt(int parameterIndex, int x) throws SQLException {
-        delegate.setInt(parameterIndex, x);
-        parameterValues.put(parameterIndex, x);
+        int physicalIndex = delegateParameterIndex(parameterIndex);
+        delegate.setInt(physicalIndex, x);
+        parameterValues.put(physicalIndex, x);
+        digestPlainParameterValues.put(physicalIndex, x);
     }
 
     /**
      * 设置长整数参数
      *
@@ -343,12 +412,14 @@ public class SimpleInterceptorPreparedStatement implements PreparedStatement {
      * @param x              参数值
      * @throws SQLException 如果设置参数失败
      */
     @Override
     public void setLong(int parameterIndex, long x) throws SQLException {
-        delegate.setLong(parameterIndex, x);
-        parameterValues.put(parameterIndex, x);
+        int physicalIndex = delegateParameterIndex(parameterIndex);
+        delegate.setLong(physicalIndex, x);
+        parameterValues.put(physicalIndex, x);
+        digestPlainParameterValues.put(physicalIndex, x);
     }
 
     /**
      * 设置双精度浮点数参数
      *
@@ -356,12 +427,14 @@ public class SimpleInterceptorPreparedStatement implements PreparedStatement {
      * @param x              参数值
      * @throws SQLException 如果设置参数失败
      */
     @Override
     public void setDouble(int parameterIndex, double x) throws SQLException {
-        delegate.setDouble(parameterIndex, x);
-        parameterValues.put(parameterIndex, x);
+        int physicalIndex = delegateParameterIndex(parameterIndex);
+        delegate.setDouble(physicalIndex, x);
+        parameterValues.put(physicalIndex, x);
+        digestPlainParameterValues.put(physicalIndex, x);
     }
 
     /**
      * 设置布尔值参数
      *
@@ -369,352 +442,383 @@ public class SimpleInterceptorPreparedStatement implements PreparedStatement {
      * @param x              参数值
      * @throws SQLException 如果设置参数失败
      */
     @Override
     public void setBoolean(int parameterIndex, boolean x) throws SQLException {
-        delegate.setBoolean(parameterIndex, x);
-        parameterValues.put(parameterIndex, x);
+        int physicalIndex = delegateParameterIndex(parameterIndex);
+        delegate.setBoolean(physicalIndex, x);
+        parameterValues.put(physicalIndex, x);
+        digestPlainParameterValues.put(physicalIndex, x);
     }
 
     // 其他PreparedStatement方法直接委托
     @Override
     public void setNull(int parameterIndex, int sqlType) throws SQLException {
-        delegate.setNull(parameterIndex, sqlType);
-        parameterValues.put(parameterIndex, null);
+        int physicalIndex = delegateParameterIndex(parameterIndex);
+        delegate.setNull(physicalIndex, sqlType);
+        parameterValues.put(physicalIndex, null);
+        digestPlainParameterValues.put(physicalIndex, null);
     }
 
     @Override
     public void setByte(int parameterIndex, byte x) throws SQLException {
-        delegate.setByte(parameterIndex, x);
+        delegate.setByte(delegateParameterIndex(parameterIndex), x);
     }
 
     @Override
     public void setShort(int parameterIndex, short x) throws SQLException {
-        delegate.setShort(parameterIndex, x);
+        delegate.setShort(delegateParameterIndex(parameterIndex), x);
     }
 
     @Override
     public void setFloat(int parameterIndex, float x) throws SQLException {
-        delegate.setFloat(parameterIndex, x);
+        delegate.setFloat(delegateParameterIndex(parameterIndex), x);
     }
 
     @Override
     public void setBigDecimal(int parameterIndex, java.math.BigDecimal x) throws SQLException {
-        delegate.setBigDecimal(parameterIndex, x);
+        delegate.setBigDecimal(delegateParameterIndex(parameterIndex), x);
     }
 
     @Override
     public void setBytes(int parameterIndex, byte[] x) throws SQLException {
-        delegate.setBytes(parameterIndex, x);
+        delegate.setBytes(delegateParameterIndex(parameterIndex), x);
     }
 
     @Override
     public void setDate(int parameterIndex, Date x) throws SQLException {
-        delegate.setDate(parameterIndex, x);
+        delegate.setDate(delegateParameterIndex(parameterIndex), x);
     }
 
     @Override
     public void setTime(int parameterIndex, Time x) throws SQLException {
-        delegate.setTime(parameterIndex, x);
+        delegate.setTime(delegateParameterIndex(parameterIndex), x);
     }
 
     @Override
     public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
-        delegate.setTimestamp(parameterIndex, x);
+        delegate.setTimestamp(delegateParameterIndex(parameterIndex), x);
     }
 
     @Override
     public void setAsciiStream(int parameterIndex, java.io.InputStream x, int length) throws SQLException {
-        delegate.setAsciiStream(parameterIndex, x, length);
+        delegate.setAsciiStream(delegateParameterIndex(parameterIndex), x, length);
     }
 
     @Override
     public void setUnicodeStream(int parameterIndex, java.io.InputStream x, int length) throws SQLException {
-        delegate.setUnicodeStream(parameterIndex, x, length);
+        delegate.setUnicodeStream(delegateParameterIndex(parameterIndex), x, length);
     }
 
     @Override
     public void setBinaryStream(int parameterIndex, java.io.InputStream x, int length) throws SQLException {
-        delegate.setBinaryStream(parameterIndex, x, length);
+        delegate.setBinaryStream(delegateParameterIndex(parameterIndex), x, length);
     }
 
     @Override
     public void clearParameters() throws SQLException {
         delegate.clearParameters();
         parameterValues.clear();
+        digestPlainParameterValues.clear();
     }
 
     @Override
     public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
-        Object newValue = parameterEncryptor.encryptObject(parameterIndex, x);
-        delegate.setObject(parameterIndex, newValue, targetSqlType);
-        parameterValues.put(parameterIndex, newValue);
+        int physicalIndex = delegateParameterIndex(parameterIndex);
+        Object newValue = parameterEncryptor.encryptObject(physicalIndex, x);
+        delegate.setObject(physicalIndex, newValue, targetSqlType);
+        parameterValues.put(physicalIndex, newValue);
+        digestPlainParameterValues.put(physicalIndex, x);
     }
 
     @Override
     public void setObject(int parameterIndex, Object x) throws SQLException {
-        Object newValue = parameterEncryptor.encryptObject(parameterIndex, x);
-        delegate.setObject(parameterIndex, newValue);
-        parameterValues.put(parameterIndex, newValue);
+        int physicalIndex = delegateParameterIndex(parameterIndex);
+        Object newValue = parameterEncryptor.encryptObject(physicalIndex, x);
+        delegate.setObject(physicalIndex, newValue);
+        parameterValues.put(physicalIndex, newValue);
+        digestPlainParameterValues.put(physicalIndex, x);
     }
 
     /**
      * 将当前PreparedStatement添加到批处理
      *
      * @throws SQLException 如果添加失败
      */
     @Override
     public void addBatch() throws SQLException {
         log.debug("Adding prepared statement to batch");
+        applyDigestsBeforeExecute();
         delegate.addBatch();
         parameterValues.clear();
+        digestPlainParameterValues.clear();
     }
 
     @Override
     public void setCharacterStream(int parameterIndex, java.io.Reader reader, int length) throws SQLException {
+        int physicalIndex = delegateParameterIndex(parameterIndex);
         if (reader != null) {
-            String processed = parameterEncryptor.encryptReader(parameterIndex, reader, length);
-            if (applyEncryptedString(parameterIndex, processed,
-                    value -> delegate.setCharacterStream(parameterIndex, new java.io.StringReader(value), value.length()),
+            String processed = parameterEncryptor.encryptReader(physicalIndex, reader, length);
+            if (applyEncryptedString(physicalIndex, processed,
+                    value -> delegate.setCharacterStream(physicalIndex, new java.io.StringReader(value), value.length()),
                     "characterStream(length)")) {
                 return;
             }
         }
-        delegate.setCharacterStream(parameterIndex, reader, length);
+        delegate.setCharacterStream(physicalIndex, reader, length);
     }
 
     @Override
     public void setRef(int parameterIndex, Ref x) throws SQLException {
-        delegate.setRef(parameterIndex, x);
+        delegate.setRef(delegateParameterIndex(parameterIndex), x);
     }
 
     @Override
     public void setBlob(int parameterIndex, Blob x) throws SQLException {
-        delegate.setBlob(parameterIndex, x);
+        delegate.setBlob(delegateParameterIndex(parameterIndex), x);
     }
 
     @Override
     public void setClob(int parameterIndex, Clob x) throws SQLException {
+        int physicalIndex = delegateParameterIndex(parameterIndex);
         if (x != null) {
-            String processed = parameterEncryptor.encryptClob(parameterIndex, x);
-            if (applyEncryptedString(parameterIndex, processed,
-                    value -> delegate.setClob(parameterIndex, toClob(value)),
+            String processed = parameterEncryptor.encryptClob(physicalIndex, x);
+            if (applyEncryptedString(physicalIndex, processed,
+                    value -> delegate.setClob(physicalIndex, toClob(value)),
                     "clob")) {
                 return;
             }
-            cacheOriginalClobValue(parameterIndex, x);
+            cacheOriginalClobValue(physicalIndex, x);
         }
-        delegate.setClob(parameterIndex, x);
+        delegate.setClob(physicalIndex, x);
     }
 
     @Override
     public void setArray(int parameterIndex, Array x) throws SQLException {
-        delegate.setArray(parameterIndex, x);
+        delegate.setArray(delegateParameterIndex(parameterIndex), x);
     }
 
     @Override
     public ResultSetMetaData getMetaData() throws SQLException {
         return delegate.getMetaData();
     }
 
     @Override
     public void setDate(int parameterIndex, Date x, java.util.Calendar cal) throws SQLException {
-        delegate.setDate(parameterIndex, x, cal);
+        delegate.setDate(delegateParameterIndex(parameterIndex), x, cal);
     }
 
     @Override
     public void setTime(int parameterIndex, Time x, java.util.Calendar cal) throws SQLException {
-        delegate.setTime(parameterIndex, x, cal);
+        delegate.setTime(delegateParameterIndex(parameterIndex), x, cal);
     }
 
     @Override
     public void setTimestamp(int parameterIndex, Timestamp x, java.util.Calendar cal) throws SQLException {
-        delegate.setTimestamp(parameterIndex, x, cal);
+        delegate.setTimestamp(delegateParameterIndex(parameterIndex), x, cal);
     }
 
     @Override
     public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
-        delegate.setNull(parameterIndex, sqlType, typeName);
+        delegate.setNull(delegateParameterIndex(parameterIndex), sqlType, typeName);
     }
 
     @Override
     public void setURL(int parameterIndex, java.net.URL x) throws SQLException {
-        delegate.setURL(parameterIndex, x);
+        delegate.setURL(delegateParameterIndex(parameterIndex), x);
     }
 
     @Override
     public ParameterMetaData getParameterMetaData() throws SQLException {
         return delegate.getParameterMetaData();
     }
 
     @Override
     public void setRowId(int parameterIndex, RowId x) throws SQLException {
-        delegate.setRowId(parameterIndex, x);
+        delegate.setRowId(delegateParameterIndex(parameterIndex), x);
     }
 
     @Override
     public void setNString(int parameterIndex, String value) throws SQLException {
-        delegate.setNString(parameterIndex, value);
+        int physicalIndex = delegateParameterIndex(parameterIndex);
+        String newValue = parameterEncryptor.encryptString(physicalIndex, value);
+        if (newValue != null) {
+            parameterValues.put(physicalIndex, newValue);
+        }
+        digestPlainParameterValues.put(physicalIndex, value);
+        delegate.setNString(physicalIndex, newValue);
     }
 
     @Override
     public void setNCharacterStream(int parameterIndex, java.io.Reader value, long length) throws SQLException {
+        int physicalIndex = delegateParameterIndex(parameterIndex);
         if (value != null) {
-            String processed = parameterEncryptor.encryptReader(parameterIndex, value, length);
-            if (applyEncryptedString(parameterIndex, processed,
-                    str -> delegate.setNCharacterStream(parameterIndex, new java.io.StringReader(str), str.length()),
+            String processed = parameterEncryptor.encryptReader(physicalIndex, value, length);
+            if (applyEncryptedString(physicalIndex, processed,
+                    str -> delegate.setNCharacterStream(physicalIndex, new java.io.StringReader(str), str.length()),
                     "nCharacterStream(long)")) {
                 return;
             }
         }
-        delegate.setNCharacterStream(parameterIndex, value, length);
+        delegate.setNCharacterStream(physicalIndex, value, length);
     }
 
     @Override
     public void setNClob(int parameterIndex, NClob value) throws SQLException {
+        int physicalIndex = delegateParameterIndex(parameterIndex);
         if (value != null) {
-            String processed = parameterEncryptor.encryptNClob(parameterIndex, value);
-            if (applyEncryptedString(parameterIndex, processed,
-                    text -> delegate.setNClob(parameterIndex, toNClob(text)),
+            String processed = parameterEncryptor.encryptNClob(physicalIndex, value);
+            if (applyEncryptedString(physicalIndex, processed,
+                    text -> delegate.setNClob(physicalIndex, toNClob(text)),
                     "nClob")) {
                 return;
             }
-            cacheOriginalNClobValue(parameterIndex, value);
+            cacheOriginalNClobValue(physicalIndex, value);
         }
-        delegate.setNClob(parameterIndex, value);
+        delegate.setNClob(physicalIndex, value);
     }
 
     @Override
     public void setClob(int parameterIndex, java.io.Reader reader, long length) throws SQLException {
+        int physicalIndex = delegateParameterIndex(parameterIndex);
         if (reader != null) {
-            String processed = parameterEncryptor.encryptReader(parameterIndex, reader, length);
-            if (applyEncryptedString(parameterIndex, processed,
-                    value -> delegate.setClob(parameterIndex, toClob(value)),
+            String processed = parameterEncryptor.encryptReader(physicalIndex, reader, length);
+            if (applyEncryptedString(physicalIndex, processed,
+                    value -> delegate.setClob(physicalIndex, toClob(value)),
                     "clob(reader,long)")) {
                 return;
             }
         }
-        delegate.setClob(parameterIndex, reader, length);
+        delegate.setClob(physicalIndex, reader, length);
     }
 
     @Override
     public void setBlob(int parameterIndex, java.io.InputStream inputStream, long length) throws SQLException {
-        delegate.setBlob(parameterIndex, inputStream, length);
+        delegate.setBlob(delegateParameterIndex(parameterIndex), inputStream, length);
     }
 
     @Override
     public void setNClob(int parameterIndex, java.io.Reader reader, long length) throws SQLException {
+        int physicalIndex = delegateParameterIndex(parameterIndex);
         if (reader != null) {
-            String processed = parameterEncryptor.encryptReader(parameterIndex, reader, length);
-            if (applyEncryptedString(parameterIndex, processed,
-                    text -> delegate.setNClob(parameterIndex, toNClob(text)),
+            String processed = parameterEncryptor.encryptReader(physicalIndex, reader, length);
+            if (applyEncryptedString(physicalIndex, processed,
+                    text -> delegate.setNClob(physicalIndex, toNClob(text)),
                     "nClob(reader,long)")) {
                 return;
             }
         }
-        delegate.setNClob(parameterIndex, reader, length);
+        delegate.setNClob(physicalIndex, reader, length);
     }
 
     @Override
     public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
-        delegate.setSQLXML(parameterIndex, xmlObject);
+        delegate.setSQLXML(delegateParameterIndex(parameterIndex), xmlObject);
     }
 
     @Override
     public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
-        delegate.setObject(parameterIndex, x, targetSqlType, scaleOrLength);
-        parameterValues.put(parameterIndex, x);
+        int physicalIndex = delegateParameterIndex(parameterIndex);
+        Object newValue = parameterEncryptor.encryptObject(physicalIndex, x);
+        delegate.setObject(physicalIndex, newValue, targetSqlType, scaleOrLength);
+        parameterValues.put(physicalIndex, newValue);
+        digestPlainParameterValues.put(physicalIndex, x);
     }
 
     @Override
     public void setAsciiStream(int parameterIndex, java.io.InputStream x, long length) throws SQLException {
-        delegate.setAsciiStream(parameterIndex, x, length);
+        delegate.setAsciiStream(delegateParameterIndex(parameterIndex), x, length);
     }
 
     @Override
     public void setBinaryStream(int parameterIndex, java.io.InputStream x, long length) throws SQLException {
-        delegate.setBinaryStream(parameterIndex, x, length);
+        delegate.setBinaryStream(delegateParameterIndex(parameterIndex), x, length);
     }
 
     @Override
     public void setCharacterStream(int parameterIndex, java.io.Reader reader, long length) throws SQLException {
+        int physicalIndex = delegateParameterIndex(parameterIndex);
         if (reader != null) {
-            String processed = parameterEncryptor.encryptReader(parameterIndex, reader, length);
-            if (applyEncryptedString(parameterIndex, processed,
-                    value -> delegate.setCharacterStream(parameterIndex, new java.io.StringReader(value), value.length()),
+            String processed = parameterEncryptor.encryptReader(physicalIndex, reader, length);
+            if (applyEncryptedString(physicalIndex, processed,
+                    value -> delegate.setCharacterStream(physicalIndex, new java.io.StringReader(value), value.length()),
                     "characterStream(long)")) {
                 return;
             }
         }
-        delegate.setCharacterStream(parameterIndex, reader, length);
+        delegate.setCharacterStream(physicalIndex, reader, length);
     }
 
     @Override
     public void setAsciiStream(int parameterIndex, java.io.InputStream x) throws SQLException {
-        delegate.setAsciiStream(parameterIndex, x);
+        delegate.setAsciiStream(delegateParameterIndex(parameterIndex), x);
     }
 
     @Override
     public void setBinaryStream(int parameterIndex, java.io.InputStream x) throws SQLException {
-        delegate.setBinaryStream(parameterIndex, x);
+        delegate.setBinaryStream(delegateParameterIndex(parameterIndex), x);
     }
 
     @Override
     public void setCharacterStream(int parameterIndex, java.io.Reader reader) throws SQLException {
+        int physicalIndex = delegateParameterIndex(parameterIndex);
         if (reader != null) {
-            String processed = parameterEncryptor.encryptReader(parameterIndex, reader, Long.MAX_VALUE);
-            if (applyEncryptedString(parameterIndex, processed,
-                    value -> delegate.setCharacterStream(parameterIndex, new java.io.StringReader(value)),
+            String processed = parameterEncryptor.encryptReader(physicalIndex, reader, Long.MAX_VALUE);
+            if (applyEncryptedString(physicalIndex, processed,
+                    value -> delegate.setCharacterStream(physicalIndex, new java.io.StringReader(value)),
                     "characterStream()")) {
                 return;
             }
         }
-        delegate.setCharacterStream(parameterIndex, reader);
+        delegate.setCharacterStream(physicalIndex, reader);
     }
 
     @Override
     public void setNCharacterStream(int parameterIndex, java.io.Reader value) throws SQLException {
+        int physicalIndex = delegateParameterIndex(parameterIndex);
         if (value != null) {
-            String processed = parameterEncryptor.encryptReader(parameterIndex, value, Long.MAX_VALUE);
-            if (applyEncryptedString(parameterIndex, processed,
-                    str -> delegate.setNCharacterStream(parameterIndex, new java.io.StringReader(str)),
+            String processed = parameterEncryptor.encryptReader(physicalIndex, value, Long.MAX_VALUE);
+            if (applyEncryptedString(physicalIndex, processed,
+                    str -> delegate.setNCharacterStream(physicalIndex, new java.io.StringReader(str)),
                     "nCharacterStream()")) {
                 return;
             }
         }
-        delegate.setNCharacterStream(parameterIndex, value);
+        delegate.setNCharacterStream(physicalIndex, value);
     }
 
     @Override
     public void setClob(int parameterIndex, java.io.Reader reader) throws SQLException {
+        int physicalIndex = delegateParameterIndex(parameterIndex);
         if (reader != null) {
-            String processed = parameterEncryptor.encryptReader(parameterIndex, reader, Long.MAX_VALUE);
-            if (applyEncryptedString(parameterIndex, processed,
-                    value -> delegate.setClob(parameterIndex, toClob(value)),
+            String processed = parameterEncryptor.encryptReader(physicalIndex, reader, Long.MAX_VALUE);
+            if (applyEncryptedString(physicalIndex, processed,
+                    value -> delegate.setClob(physicalIndex, toClob(value)),
                     "clob(reader)")) {
                 return;
             }
         }
-        delegate.setClob(parameterIndex, reader);
+        delegate.setClob(physicalIndex, reader);
     }
 
     @Override
     public void setBlob(int parameterIndex, java.io.InputStream inputStream) throws SQLException {
-        delegate.setBlob(parameterIndex, inputStream);
+        delegate.setBlob(delegateParameterIndex(parameterIndex), inputStream);
     }
 
     @Override
     public void setNClob(int parameterIndex, java.io.Reader reader) throws SQLException {
+        int physicalIndex = delegateParameterIndex(parameterIndex);
         if (reader != null) {
-            String processed = parameterEncryptor.encryptReader(parameterIndex, reader, Long.MAX_VALUE);
-            if (applyEncryptedString(parameterIndex, processed,
-                    text -> delegate.setNClob(parameterIndex, toNClob(text)),
+            String processed = parameterEncryptor.encryptReader(physicalIndex, reader, Long.MAX_VALUE);
+            if (applyEncryptedString(physicalIndex, processed,
+                    text -> delegate.setNClob(physicalIndex, toNClob(text)),
                     "nClob(reader)")) {
                 return;
             }
         }
-        delegate.setNClob(parameterIndex, reader);
+        delegate.setNClob(physicalIndex, reader);
     }
 
     /**
      * 将 Clob 转换为 String
      *
diff --git a/securt-kit-core/src/test/java/io/github/hexlodev/core/digest/DigestWriteSupportTest.java b/securt-kit-core/src/test/java/io/github/hexlodev/core/digest/DigestWriteSupportTest.java
new file mode 100644
index 0000000..f5897f8
--- /dev/null
+++ b/securt-kit-core/src/test/java/io/github/hexlodev/core/digest/DigestWriteSupportTest.java
@@ -0,0 +1,246 @@
+package io.github.hexlodev.core.digest;
+
+import cn.hutool.core.lang.Pair;
+import io.github.hexlodev.core.cache.StrategyCache;
+import io.github.hexlodev.core.config.DigestConfigRegistry;
+import io.github.hexlodev.core.config.FieldEncryptorProperties;
+import io.github.hexlodev.core.interceptor.SimpleInterceptorPreparedStatement;
+import io.github.hexlodev.core.parser.dto.ColumnTableDto;
+import io.github.hexlodev.core.parser.dto.FieldEncryptorInfoDto;
+import io.github.hexlodev.core.strategy.HmacSha256DigestStrategy;
+import org.junit.jupiter.api.BeforeEach;
+import org.junit.jupiter.api.Test;
+
+import java.lang.reflect.Proxy;
+import java.sql.PreparedStatement;
+import java.util.Arrays;
+import java.util.Collections;
+import java.util.LinkedHashMap;
+import java.util.List;
+import java.util.Map;
+
+import static org.junit.jupiter.api.Assertions.assertEquals;
+import static org.junit.jupiter.api.Assertions.assertFalse;
+
+class DigestWriteSupportTest {
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
+        strategy = new HmacSha256DigestStrategy("write-support-secret");
+        StrategyCache.registerStrategy(HmacSha256DigestStrategy.class, strategy);
+        DigestConfigRegistry.register(DATASOURCE, TABLE, Collections.singletonList(
+                new ResolvedDigestRule(
+                        TABLE,
+                        Arrays.asList("phone", "id_card"),
+                        "row_digest",
+                        HmacSha256DigestStrategy.class,
+                        FieldEncryptorProperties.PartialUpdate.FAIL,
+                        false,
+                        FieldEncryptorProperties.FailurePolicy.FALLBACK)));
+    }
+
+    @Test
+    void computesAndBindsAppendedInsertDigest() throws Exception {
+        Map<Integer, Object> values = new LinkedHashMap<Integer, Object>();
+        values.put(1, "13800138000");
+        values.put(2, "110101199001011234");
+        Map<Integer, String> bound = new LinkedHashMap<Integer, String>();
+
+        DigestWriteSupport.applyDigestsBeforeEncrypt(
+                "INSERT INTO user_account(phone, id_card, row_digest) VALUES (?, ?, ?)",
+                Collections.singleton(TABLE),
+                DATASOURCE,
+                pair(
+                        column(1, "phone"),
+                        column(2, "id_card"),
+                        column(3, "row_digest")),
+                values,
+                values,
+                new DigestRewriteResult(
+                        "INSERT INTO user_account(phone, id_card, row_digest) VALUES (?, ?, ?)",
+                        Collections.singletonList(3),
+                        Collections.singletonList("row_digest"),
+                        true,
+                        null),
+                recordingStatement(bound));
+
+        Map<String, String> ordered = new LinkedHashMap<String, String>();
+        ordered.put("phone", "13800138000");
+        ordered.put("id_card", "110101199001011234");
+        String expected = strategy.digest(ordered);
+        assertEquals(expected, bound.get(3));
+        assertEquals(expected, values.get(3));
+    }
+
+    @Test
+    void overwritesExistingTargetParameterWithoutRewrite() throws Exception {
+        Map<Integer, Object> values = new LinkedHashMap<Integer, Object>();
+        values.put(1, "13800138000");
+        values.put(2, "caller-supplied-digest");
+        values.put(3, "110101199001011234");
+        Map<Integer, String> bound = new LinkedHashMap<Integer, String>();
+
+        DigestWriteSupport.applyDigestsBeforeEncrypt(
+                "INSERT INTO user_account(phone, row_digest, id_card) VALUES (?, ?, ?)",
+                Collections.singleton(TABLE),
+                DATASOURCE,
+                pair(
+                        column(1, "phone"),
+                        column(2, "row_digest"),
+                        column(3, "id_card")),
+                values,
+                values,
+                new DigestRewriteResult(
+                        "INSERT INTO user_account(phone, row_digest, id_card) VALUES (?, ?, ?)",
+                        Collections.<Integer>emptyList(), false, null),
+                recordingStatement(bound));
+
+        Map<String, String> ordered = new LinkedHashMap<String, String>();
+        ordered.put("phone", "13800138000");
+        ordered.put("id_card", "110101199001011234");
+        String expected = strategy.digest(ordered);
+        assertEquals(Collections.singletonMap(2, expected), bound);
+        assertEquals(expected, values.get(2));
+    }
+
+    @Test
+    void bindsOnlyTheDigestTargetActuallyAppendedByRewrite() throws Exception {
+        DigestConfigRegistry.clear();
+        DigestConfigRegistry.register(DATASOURCE, TABLE, Arrays.asList(
+                rule(Collections.singletonList("phone"), "phone_digest"),
+                rule(Collections.singletonList("id_card"), "id_card_digest")));
+        String originalSql =
+                "INSERT INTO user_account(phone, phone_digest, id_card) VALUES (?, ?, ?)";
+        DigestRewriteResult rewrite = DigestWriteSupport.rewriteForConfiguredDigests(
+                originalSql, Collections.singleton(TABLE), DATASOURCE);
+        Map<Integer, Object> values = new LinkedHashMap<Integer, Object>();
+        values.put(1, "13800138000");
+        values.put(2, "caller-supplied-digest");
+        values.put(3, "110101199001011234");
+        Map<Integer, String> bound = new LinkedHashMap<Integer, String>();
+
+        DigestWriteSupport.applyDigestsBeforeEncrypt(
+                rewrite.getSql(),
+                Collections.singleton(TABLE),
+                DATASOURCE,
+                pair(
+                        column(1, "phone"),
+                        column(2, "phone_digest"),
+                        column(3, "id_card")),
+                values,
+                values,
+                rewrite,
+                recordingStatement(bound));
+
+        Map<String, String> phone = Collections.singletonMap("phone", "13800138000");
+        Map<String, String> idCard =
+                Collections.singletonMap("id_card", "110101199001011234");
+        assertEquals(strategy.digest(phone), bound.get(2));
+        assertEquals(strategy.digest(idCard), bound.get(4));
+    }
+
+    @Test
+    void reloadWhereUsesStoredValueAndFallsBackToPlainValue() {
+        Map<Integer, Object> plain = new LinkedHashMap<Integer, Object>();
+        plain.put(2, "plain-id");
+        plain.put(3, "plain-tenant");
+        Map<Integer, Object> stored = new LinkedHashMap<Integer, Object>();
+        stored.put(2, "encrypted-id");
+
+        List<Object> whereParams = DigestWriteSupport.reloadWhereParameters(
+                "UPDATE user_account SET phone = ? WHERE id = ? AND tenant = ?",
+                plain,
+                stored);
+
+        assertEquals(Arrays.<Object>asList("encrypted-id", "plain-tenant"), whereParams);
+    }
+
+    @Test
+    void skipsMultiRowInsertRewriteBecauseAppendedIndexesAreSingleRowOnly() {
+        DigestRewriteResult result = DigestWriteSupport.rewriteForConfiguredDigests(
+                "INSERT INTO user_account(phone, id_card) VALUES (?, ?), (?, ?)",
+                Collections.singleton(TABLE),
+                DATASOURCE);
+
+        assertFalse(result.isRewritten());
+    }
+
+    @Test
+    void shiftsApplicationParameterPastInsertedUpdateDigest() throws Exception {
+        Map<Integer, String> bound = new LinkedHashMap<Integer, String>();
+        PreparedStatement delegate = recordingStatement(bound);
+        SimpleInterceptorPreparedStatement statement = new SimpleInterceptorPreparedStatement(
+                delegate,
+                "UPDATE user_account SET phone = ?, row_digest = ? WHERE id = ?",
+                DATASOURCE);
+        statement.setDigestRewriteResult(new DigestRewriteResult(
+                "UPDATE user_account SET phone = ?, row_digest = ? WHERE id = ?",
+                Collections.singletonList(2), true, null));
+
+        statement.setString(2, "where-id");
+
+        assertEquals("where-id", bound.get(3));
+        assertFalse(bound.containsKey(2));
+    }
+
+    private ResolvedDigestRule rule(List<String> sources, String target) {
+        return new ResolvedDigestRule(
+                TABLE,
+                sources,
+                target,
+                HmacSha256DigestStrategy.class,
+                FieldEncryptorProperties.PartialUpdate.FAIL,
+                false,
+                FieldEncryptorProperties.FailurePolicy.FALLBACK);
+    }
+
+    private Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> pair(ColumnTableDto... columns) {
+        Map<String, ColumnTableDto> mappings = new LinkedHashMap<String, ColumnTableDto>();
+        for (ColumnTableDto column : columns) {
+            mappings.put(String.valueOf(column.getInsertFieldIndex()), column);
+        }
+        return Pair.of(mappings, Collections.<FieldEncryptorInfoDto>emptyList());
+    }
+
+    private ColumnTableDto column(int index, String name) {
+        return ColumnTableDto.builder()
+                .sourceTableName(TABLE)
+                .sourceColumn(name)
+                .insertFieldIndex(index)
+                .fromSourceTable(true)
+                .build();
+    }
+
+    private PreparedStatement recordingStatement(Map<Integer, String> bound) {
+        return (PreparedStatement) Proxy.newProxyInstance(
+                getClass().getClassLoader(),
+                new Class<?>[]{PreparedStatement.class},
+                (proxy, method, args) -> {
+                    if ("setString".equals(method.getName())) {
+                        bound.put((Integer) args[0], (String) args[1]);
+                        return null;
+                    }
+                    if ("getConnection".equals(method.getName())) {
+                        return null;
+                    }
+                    Class<?> returnType = method.getReturnType();
+                    if (returnType == boolean.class) {
+                        return false;
+                    }
+                    if (returnType == int.class) {
+                        return 0;
+                    }
+                    if (returnType == long.class) {
+                        return 0L;
+                    }
+                    return null;
+                });
+    }
+}

