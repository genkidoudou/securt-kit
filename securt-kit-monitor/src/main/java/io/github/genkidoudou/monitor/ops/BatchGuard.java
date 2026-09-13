package io.github.genkidoudou.monitor.ops;

import io.github.genkidoudou.core.config.FieldEncryptorProperties;
import io.github.genkidoudou.monitor.MonitorProperties;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 刷数 WHERE / 表白名单校验。
 */
public final class BatchGuard {

    private static final Pattern MULTI_STATEMENT = Pattern.compile(";\\s*\\S", Pattern.DOTALL);
    private static final Pattern DANGEROUS = Pattern.compile(
            "(?i)\\b(UNION|INTO\\s+OUTFILE|LOAD_FILE|SLEEP\\s*\\(|BENCHMARK\\s*\\()\\b");

    private BatchGuard() {
    }

    public static String validateWhere(String where, MonitorProperties properties) {
        if (where == null || where.trim().isEmpty()) {
            boolean allowEmpty = properties != null && properties.isBatchAllowEmptyWhere();
            if (!allowEmpty) {
                return "WHERE 不能为空（可配置 securtkit.monitor.batch-allow-empty-where=true）";
            }
            return null;
        }
        String w = where.trim();
        if (w.regionMatches(true, 0, "where ", 0, 6)) {
            w = w.substring(6).trim();
        }
        if (w.isEmpty()) {
            return validateWhere("", properties);
        }
        if (MULTI_STATEMENT.matcher(w).find()) {
            return "WHERE 不允许包含多语句";
        }
        if (DANGEROUS.matcher(w).find()) {
            return "WHERE 包含不允许的关键字";
        }
        return null;
    }

    public static String normalizeWhere(String where) {
        if (where == null) {
            return "";
        }
        String w = where.trim();
        if (w.regionMatches(true, 0, "where ", 0, 6)) {
            w = w.substring(6).trim();
        }
        return w;
    }

    public static boolean isConfiguredTable(FieldEncryptorProperties props, String table) {
        if (props == null || props.getTables() == null || table == null) {
            return false;
        }
        String canonical = PrimaryKeyResolver.canonicalize(table);
        for (FieldEncryptorProperties.TableConfig tc : props.getTables()) {
            if (tc.getTableName() == null) {
                continue;
            }
            if (!PrimaryKeyResolver.canonicalize(tc.getTableName()).equals(canonical)) {
                continue;
            }
            boolean hasFields = tc.getFields() != null && !tc.getFields().isEmpty();
            boolean hasDigest = tc.getDigest() != null && !tc.getDigest().isEmpty();
            if (hasFields || hasDigest) {
                return true;
            }
        }
        return false;
    }

    public static String assertAllowedTable(FieldEncryptorProperties props,
                                           MonitorProperties monitorProperties,
                                           String table) {
        if (table == null || table.trim().isEmpty()) {
            return "table 不能为空";
        }
        boolean allowUnconfigured = monitorProperties != null
                && monitorProperties.isBatchAllowUnconfiguredTables();
        if (!allowUnconfigured && !isConfiguredTable(props, table)) {
            return "表不在允许范围：仅允许已配置加密或摘要的表";
        }
        // 拒绝明显非法标识
        String bare = PrimaryKeyResolver.canonicalize(table);
        for (int i = 0; i < bare.length(); i++) {
            char c = bare.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '.')) {
                return "非法表名";
            }
        }
        return null;
    }

    public static boolean equalsIgnoreCase(String a, String b) {
        if (a == null) {
            return b == null;
        }
        return a.equalsIgnoreCase(b);
    }

    public static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }
}
