package io.github.hexlodev.core.digest;

import cn.hutool.core.lang.Pair;
import io.github.hexlodev.core.config.DigestConfigRegistry;
import io.github.hexlodev.core.exception.SecurtKitException;
import io.github.hexlodev.core.parser.dto.ColumnTableDto;
import io.github.hexlodev.core.parser.dto.FieldEncryptorInfoDto;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Values;
import net.sf.jsqlparser.statement.update.Update;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * JDBC 写路径的摘要 SQL 改写与参数绑定支持。
 */
public final class DigestWriteSupport {

    private DigestWriteSupport() {
    }

    public static DigestRewriteResult rewriteForConfiguredDigests(
            String sql, Set<String> tables, String datasourceId) {
        if (!isSupportedWrite(sql)) {
            return null;
        }
        String table = singleDigestTable(tables, datasourceId);
        if (table == null) {
            return null;
        }
        List<String> targets = new ArrayList<String>();
        for (ResolvedDigestRule rule : DigestConfigRegistry.getRules(table, datasourceId)) {
            targets.add(rule.getTargetField());
        }
        if (isMultiRowInsert(sql)) {
            return DigestRewriteResult.notRewritten(
                    sql, "Multi-row INSERT is not supported for digest rewrite");
        }
        return DigestSqlRewriter.tryAppendTargets(sql, targets);
    }

    public static Set<Integer> applyDigestsBeforeEncrypt(
            String sql,
            Set<String> tables,
            String datasourceId,
            Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> pair,
            Map<Integer, Object> parameterValues,
            DigestRewriteResult rewriteResult,
            PreparedStatement delegate) throws SQLException {
        if (!isSupportedWrite(sql)
                || (rewriteResult != null
                && rewriteResult.getWarnMessage() != null
                && rewriteResult.getWarnMessage().startsWith("Multi-row INSERT"))) {
            return Collections.emptySet();
        }
        String table = singleDigestTable(tables, datasourceId);
        if (table == null || delegate == null) {
            return Collections.emptySet();
        }

        Map<Integer, ColumnTableDto> columnsByIndex = columnsByIndex(pair, table);
        Map<String, String> availablePlain = new LinkedHashMap<String, String>();
        if (parameterValues != null) {
            for (Map.Entry<Integer, ColumnTableDto> entry : columnsByIndex.entrySet()) {
                if (!parameterValues.containsKey(entry.getKey())) {
                    continue;
                }
                Object value = parameterValues.get(entry.getKey());
                availablePlain.put(entry.getValue().getSourceColumn(),
                        value == null ? null : String.valueOf(value));
            }
        }

        ReloadContext reload = reloadContext(sql, parameterValues);
        Connection connection = reload.insert ? null : delegate.getConnection();
        Map<String, String> digests = new DigestService().computeTargetDigests(
                table,
                datasourceId,
                availablePlain,
                reload.insert,
                connection,
                reload.whereSql,
                reload.whereParams);
        if (digests.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Integer> boundIndexes = new HashSet<Integer>();
        for (Map.Entry<Integer, ColumnTableDto> entry : columnsByIndex.entrySet()) {
            String digest = getIgnoreCase(digests, entry.getValue().getSourceColumn());
            if (digest != null) {
                bind(delegate, parameterValues, entry.getKey(), digest);
                boundIndexes.add(entry.getKey());
            }
        }

        if (rewriteResult != null) {
            int digestPosition = 0;
            for (Map.Entry<String, String> digest : digests.entrySet()) {
                if (digestPosition >= rewriteResult.getAppendedParameterIndexes().size()) {
                    break;
                }
                Integer index = rewriteResult.getAppendedParameterIndexes().get(digestPosition++);
                if (!boundIndexes.contains(index)) {
                    bind(delegate, parameterValues, index, digest.getValue());
                    boundIndexes.add(index);
                }
            }
        }
        return boundIndexes;
    }

    private static void bind(PreparedStatement delegate,
                             Map<Integer, Object> parameterValues,
                             int index,
                             String digest) throws SQLException {
        delegate.setString(index, digest);
        if (parameterValues != null) {
            parameterValues.put(index, digest);
        }
    }

    private static Map<Integer, ColumnTableDto> columnsByIndex(
            Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> pair, String table) {
        if (pair == null || pair.getKey() == null) {
            return Collections.emptyMap();
        }
        Map<Integer, ColumnTableDto> result = new LinkedHashMap<Integer, ColumnTableDto>();
        for (ColumnTableDto column : pair.getKey().values()) {
            if (column == null || column.getInsertFieldIndex() == null
                    || column.getSourceColumn() == null
                    || !equalsIgnoreCase(table, column.getSourceTableName())) {
                continue;
            }
            result.putIfAbsent(column.getInsertFieldIndex(), column);
        }
        return result;
    }

    private static String singleDigestTable(Set<String> tables, String datasourceId) {
        String matched = null;
        if (tables != null) {
            for (String table : tables) {
                if (!DigestConfigRegistry.hasDigest(table, datasourceId)) {
                    continue;
                }
                if (matched != null && !equalsIgnoreCase(matched, table)) {
                    throw new SecurtKitException("Digest write supports a single configured table only");
                }
                matched = table;
            }
        }
        return matched;
    }

    private static ReloadContext reloadContext(String sql, Map<Integer, Object> parameterValues) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (statement instanceof Insert) {
                return new ReloadContext(true, null, Collections.emptyList());
            }
            if (!(statement instanceof Update)) {
                throw new SecurtKitException("Digest write supports INSERT and UPDATE only");
            }
            Update update = (Update) statement;
            if (update.getWhere() == null) {
                return new ReloadContext(false, null, Collections.emptyList());
            }
            String whereSql = update.getWhere().toString();
            int whereParameterCount = countPlaceholders(whereSql);
            int totalParameterCount = countPlaceholders(sql);
            List<Object> whereParams = new ArrayList<Object>();
            for (int index = totalParameterCount - whereParameterCount + 1;
                 index <= totalParameterCount; index++) {
                if (parameterValues == null || !parameterValues.containsKey(index)) {
                    throw new SecurtKitException(
                            "Cannot resolve UPDATE WHERE parameters for digest RELOAD");
                }
                whereParams.add(parameterValues.get(index));
            }
            return new ReloadContext(false, whereSql, whereParams);
        } catch (SecurtKitException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurtKitException("Cannot parse SQL for digest write", e);
        }
    }

    private static int countPlaceholders(String sql) {
        int count = 0;
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == '?') {
                count++;
            }
        }
        return count;
    }

    private static boolean isMultiRowInsert(String sql) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (!(statement instanceof Insert)) {
                return false;
            }
            Insert insert = (Insert) statement;
            if (!(insert.getSelect() instanceof Values)) {
                return false;
            }
            Values values = (Values) insert.getSelect();
            return values.getExpressions() != null
                    && values.getExpressions().size() > 1
                    && values.getExpressions().get(0)
                    instanceof net.sf.jsqlparser.expression.operators.relational.ExpressionList;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isSupportedWrite(String sql) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            return statement instanceof Insert || statement instanceof Update;
        } catch (Exception e) {
            return false;
        }
    }

    private static String getIgnoreCase(Map<String, String> values, String key) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (equalsIgnoreCase(entry.getKey(), key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null
                && left.toLowerCase(Locale.ROOT).equals(right.toLowerCase(Locale.ROOT));
    }

    private static final class ReloadContext {
        private final boolean insert;
        private final String whereSql;
        private final List<Object> whereParams;

        private ReloadContext(boolean insert, String whereSql, List<Object> whereParams) {
            this.insert = insert;
            this.whereSql = whereSql;
            this.whereParams = whereParams;
        }
    }
}
