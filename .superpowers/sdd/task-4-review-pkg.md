# Review Package Task 4
BASE: cedbee6283217f962888f83f04ebb1ee1fa73b06
HEAD: fcdb133a1e3c6cfeceace6ed274af1f4d5a95581
## Commits
fcdb133 feat(digest): add single-table SQL rewriter for digest columns

## Stat
 .../hexlodev/core/digest/DigestRewriteResult.java  |  56 +++++
 .../hexlodev/core/digest/DigestSqlRewriter.java    | 231 +++++++++++++++++++++
 .../core/digest/DigestSqlRewriterTest.java         |  40 ++++
 3 files changed, 327 insertions(+)

## Diff
diff --git a/securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestRewriteResult.java b/securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestRewriteResult.java
new file mode 100644
index 0000000..04c39fa
--- /dev/null
+++ b/securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestRewriteResult.java
@@ -0,0 +1,56 @@
+package io.github.hexlodev.core.digest;
+
+import java.util.ArrayList;
+import java.util.Collections;
+import java.util.List;
+
+/**
+ * SQL 摘要列改写结果。
+ */
+public final class DigestRewriteResult {
+
+    private final String sql;
+    private final List<Integer> appendedParameterIndexes;
+    private final boolean rewritten;
+    private final String warnMessage;
+
+    public DigestRewriteResult(String sql,
+                               List<Integer> appendedParameterIndexes,
+                               boolean rewritten,
+                               String warnMessage) {
+        this.sql = sql;
+        this.appendedParameterIndexes = appendedParameterIndexes == null
+                ? Collections.<Integer>emptyList()
+                : Collections.unmodifiableList(new ArrayList<>(appendedParameterIndexes));
+        this.rewritten = rewritten;
+        this.warnMessage = warnMessage;
+    }
+
+    public String getSql() {
+        return sql;
+    }
+
+    public List<Integer> getAppendedParameterIndexes() {
+        return appendedParameterIndexes;
+    }
+
+    public boolean isRewritten() {
+        return rewritten;
+    }
+
+    public String getWarnMessage() {
+        return warnMessage;
+    }
+
+    static DigestRewriteResult unchanged(String sql) {
+        return new DigestRewriteResult(sql, Collections.<Integer>emptyList(), false, null);
+    }
+
+    static DigestRewriteResult notRewritten(String sql, String warnMessage) {
+        return new DigestRewriteResult(sql, Collections.<Integer>emptyList(), false, warnMessage);
+    }
+
+    static DigestRewriteResult rewritten(String sql, List<Integer> appendedParameterIndexes) {
+        return new DigestRewriteResult(sql, appendedParameterIndexes, true, null);
+    }
+}
diff --git a/securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestSqlRewriter.java b/securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestSqlRewriter.java
new file mode 100644
index 0000000..7891074
--- /dev/null
+++ b/securt-kit-core/src/main/java/io/github/hexlodev/core/digest/DigestSqlRewriter.java
@@ -0,0 +1,231 @@
+package io.github.hexlodev.core.digest;
+
+import net.sf.jsqlparser.expression.Expression;
+import net.sf.jsqlparser.expression.JdbcParameter;
+import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
+import net.sf.jsqlparser.parser.CCJSqlParserUtil;
+import net.sf.jsqlparser.schema.Column;
+import net.sf.jsqlparser.statement.Statement;
+import net.sf.jsqlparser.statement.insert.Insert;
+import net.sf.jsqlparser.statement.select.Values;
+import net.sf.jsqlparser.statement.update.Update;
+import net.sf.jsqlparser.statement.update.UpdateSet;
+
+import java.util.ArrayList;
+import java.util.HashSet;
+import java.util.List;
+import java.util.Locale;
+import java.util.Set;
+
+/**
+ * 单表 INSERT/UPDATE 追加摘要目标列（纯函数，无 JDBC 依赖）。
+ */
+public final class DigestSqlRewriter {
+
+    private DigestSqlRewriter() {
+    }
+
+    public static DigestRewriteResult tryAppendTargets(String sql, List<String> missingTargetFields) {
+        if (sql == null || sql.trim().isEmpty()) {
+            return DigestRewriteResult.notRewritten(sql, "SQL is empty");
+        }
+        if (missingTargetFields == null || missingTargetFields.isEmpty()) {
+            return DigestRewriteResult.unchanged(sql);
+        }
+        try {
+            Statement statement = CCJSqlParserUtil.parse(sql);
+            if (statement instanceof Insert) {
+                return rewriteInsert((Insert) statement, missingTargetFields);
+            }
+            if (statement instanceof Update) {
+                return rewriteUpdate((Update) statement, missingTargetFields);
+            }
+            return DigestRewriteResult.notRewritten(sql,
+                    "Digest SQL rewrite supports INSERT and UPDATE only");
+        } catch (Exception e) {
+            return DigestRewriteResult.notRewritten(sql,
+                    "Failed to parse SQL for digest rewrite: " + e.getMessage());
+        }
+    }
+
+    private static DigestRewriteResult rewriteInsert(Insert insert, List<String> missingTargetFields) {
+        List<Column> columns = insert.getColumns();
+        if (columns == null || columns.isEmpty()) {
+            return DigestRewriteResult.notRewritten(insert.toString(),
+                    "INSERT without explicit column list is not supported for digest rewrite");
+        }
+        if (!(insert.getSelect() instanceof Values)) {
+            return DigestRewriteResult.notRewritten(insert.toString(),
+                    "INSERT without VALUES is not supported for digest rewrite");
+        }
+
+        Set<String> existing = columnNames(columns);
+        List<String> toAppend = filterMissing(missingTargetFields, existing);
+        if (toAppend.isEmpty()) {
+            return DigestRewriteResult.unchanged(insert.toString());
+        }
+
+        Values values = (Values) insert.getSelect();
+        int placeholderCount = countPlaceholders(values.getExpressions());
+
+        List<Integer> appendedIndexes = new ArrayList<>();
+        for (String field : toAppend) {
+            columns.add(new Column(field));
+            appendedIndexes.add(placeholderCount + 1);
+            placeholderCount++;
+        }
+        appendJdbcParameters(values, toAppend.size());
+
+        return DigestRewriteResult.rewritten(insert.toString(), appendedIndexes);
+    }
+
+    private static DigestRewriteResult rewriteUpdate(Update update, List<String> missingTargetFields) {
+        if (hasJoins(update)) {
+            return DigestRewriteResult.notRewritten(update.toString(),
+                    "Multi-table UPDATE is not supported for digest rewrite");
+        }
+
+        List<UpdateSet> updateSets = update.getUpdateSets();
+        if (updateSets == null || updateSets.isEmpty()) {
+            return DigestRewriteResult.notRewritten(update.toString(),
+                    "UPDATE without SET clause is not supported for digest rewrite");
+        }
+
+        Set<String> existing = new HashSet<>();
+        for (UpdateSet updateSet : updateSets) {
+            existing.addAll(columnNames(updateSet.getColumns()));
+        }
+
+        List<String> toAppend = filterMissing(missingTargetFields, existing);
+        if (toAppend.isEmpty()) {
+            return DigestRewriteResult.unchanged(update.toString());
+        }
+
+        int setPlaceholderCount = countSetPlaceholders(updateSets);
+        List<Integer> appendedIndexes = new ArrayList<>();
+        for (String field : toAppend) {
+            UpdateSet updateSet = new UpdateSet();
+            updateSet.add(new Column(field), new JdbcParameter());
+            updateSets.add(updateSet);
+            appendedIndexes.add(setPlaceholderCount + 1);
+            setPlaceholderCount++;
+        }
+
+        return DigestRewriteResult.rewritten(update.toString(), appendedIndexes);
+    }
+
+    private static boolean hasJoins(Update update) {
+        if (update.getStartJoins() != null && !update.getStartJoins().isEmpty()) {
+            return true;
+        }
+        return update.getJoins() != null && !update.getJoins().isEmpty();
+    }
+
+    private static List<String> filterMissing(List<String> missingTargetFields, Set<String> existing) {
+        List<String> toAppend = new ArrayList<>();
+        for (String field : missingTargetFields) {
+            if (field == null || field.trim().isEmpty()) {
+                continue;
+            }
+            if (!existing.contains(normalize(field))) {
+                toAppend.add(field);
+                existing.add(normalize(field));
+            }
+        }
+        return toAppend;
+    }
+
+    private static Set<String> columnNames(List<Column> columns) {
+        Set<String> names = new HashSet<>();
+        if (columns == null) {
+            return names;
+        }
+        for (Column column : columns) {
+            if (column != null && column.getColumnName() != null) {
+                names.add(normalize(column.getColumnName()));
+            }
+        }
+        return names;
+    }
+
+    private static String normalize(String name) {
+        String trimmed = name.trim();
+        if (trimmed.length() >= 2) {
+            char first = trimmed.charAt(0);
+            char last = trimmed.charAt(trimmed.length() - 1);
+            if ((first == '`' && last == '`')
+                    || (first == '"' && last == '"')
+                    || (first == '\'' && last == '\'')) {
+                trimmed = trimmed.substring(1, trimmed.length() - 1);
+            }
+        }
+        return trimmed.toLowerCase(Locale.ROOT);
+    }
+
+    private static int countSetPlaceholders(List<UpdateSet> updateSets) {
+        int count = 0;
+        for (UpdateSet updateSet : updateSets) {
+            ExpressionList<?> values = updateSet.getValues();
+            if (values != null) {
+                count += countPlaceholders(values);
+            }
+        }
+        return count;
+    }
+
+    @SuppressWarnings("unchecked")
+    private static void appendJdbcParameters(Values values, int count) {
+        ExpressionList<Expression> expressions = (ExpressionList<Expression>) values.getExpressions();
+        if (expressions == null || expressions.isEmpty()) {
+            return;
+        }
+        if (expressions.size() > 1 && expressions.get(0) instanceof ExpressionList) {
+            for (Expression expression : expressions) {
+                appendJdbcParametersToRow((ExpressionList<Expression>) expression, count);
+            }
+            return;
+        }
+        if (expressions.get(0) instanceof ExpressionList) {
+            appendJdbcParametersToRow((ExpressionList<Expression>) expressions.get(0), count);
+            return;
+        }
+        appendJdbcParametersToRow(expressions, count);
+    }
+
+    private static void appendJdbcParametersToRow(ExpressionList<Expression> row, int count) {
+        for (int i = 0; i < count; i++) {
+            row.add(new JdbcParameter());
+        }
+    }
+
+    private static int countPlaceholders(ExpressionList<?> expressions) {
+        if (expressions == null || expressions.isEmpty()) {
+            return 0;
+        }
+        if (expressions.size() > 1 && expressions.get(0) instanceof ExpressionList) {
+            int total = 0;
+            for (Expression expression : expressions) {
+                total += countPlaceholders((ExpressionList<?>) expression);
+            }
+            return total;
+        }
+        if (expressions.get(0) instanceof ExpressionList) {
+            return countPlaceholders((ExpressionList<?>) expressions.get(0));
+        }
+        int count = 0;
+        for (Expression expression : expressions) {
+            count += countPlaceholders(expression);
+        }
+        return count;
+    }
+
+    private static int countPlaceholders(Expression expression) {
+        if (expression instanceof JdbcParameter) {
+            return 1;
+        }
+        if (expression instanceof ExpressionList) {
+            return countPlaceholders((ExpressionList<?>) expression);
+        }
+        return 0;
+    }
+}
diff --git a/securt-kit-core/src/test/java/io/github/hexlodev/core/digest/DigestSqlRewriterTest.java b/securt-kit-core/src/test/java/io/github/hexlodev/core/digest/DigestSqlRewriterTest.java
new file mode 100644
index 0000000..4785543
--- /dev/null
+++ b/securt-kit-core/src/test/java/io/github/hexlodev/core/digest/DigestSqlRewriterTest.java
@@ -0,0 +1,40 @@
+package io.github.hexlodev.core.digest;
+
+import org.junit.jupiter.api.Test;
+
+import java.util.Collections;
+
+import static org.junit.jupiter.api.Assertions.*;
+
+class DigestSqlRewriterTest {
+
+    @Test
+    void appendInsertColumn() {
+        DigestRewriteResult r = DigestSqlRewriter.tryAppendTargets(
+                "INSERT INTO user (phone, name) VALUES (?, ?)",
+                Collections.singletonList("row_digest"));
+        assertTrue(r.isRewritten());
+        assertTrue(r.getSql().toLowerCase().contains("row_digest"));
+        assertEquals(Collections.singletonList(Integer.valueOf(3)), r.getAppendedParameterIndexes());
+    }
+
+    @Test
+    void appendUpdateSet() {
+        DigestRewriteResult r = DigestSqlRewriter.tryAppendTargets(
+                "UPDATE user SET phone = ? WHERE id = ?",
+                Collections.singletonList("row_digest"));
+        assertTrue(r.isRewritten());
+        assertTrue(r.getSql().toLowerCase().contains("row_digest"));
+        // 新 ? 在 SET 末尾、WHERE 之前 → 索引 2；WHERE id 顺延为 3
+        assertEquals(Collections.singletonList(Integer.valueOf(2)), r.getAppendedParameterIndexes());
+    }
+
+    @Test
+    void skipMultiTable() {
+        DigestRewriteResult r = DigestSqlRewriter.tryAppendTargets(
+                "UPDATE user u JOIN t ON u.id=t.id SET u.phone = ?",
+                Collections.singletonList("row_digest"));
+        assertFalse(r.isRewritten());
+        assertNotNull(r.getWarnMessage());
+    }
+}

