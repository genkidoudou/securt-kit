package io.github.hexlodev.core.config;

import cn.hutool.core.util.StrUtil;
import io.github.hexlodev.core.digest.ResolvedDigestRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按数据源和表保存已解析的摘要规则。
 */
public final class DigestConfigRegistry {

    private static final Map<String, Map<String, List<ResolvedDigestRule>>> RULES =
            new ConcurrentHashMap<>();

    private DigestConfigRegistry() {
    }

    public static void register(String datasourceId, String table, List<ResolvedDigestRule> rules) {
        if (StrUtil.isBlank(table)) {
            throw new IllegalArgumentException("table must not be blank");
        }
        String ds = normalizeDatasourceId(datasourceId);
        String normalizedTable = normalizeTable(table);
        List<ResolvedDigestRule> immutableRules = rules == null
                ? Collections.<ResolvedDigestRule>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(rules));
        RULES.computeIfAbsent(ds, key -> new ConcurrentHashMap<>())
                .put(normalizedTable, immutableRules);
    }

    public static List<ResolvedDigestRule> getRules(String table, String datasourceId) {
        if (StrUtil.isBlank(table)) {
            return Collections.emptyList();
        }
        Map<String, List<ResolvedDigestRule>> tableRules = RULES.get(normalizeDatasourceId(datasourceId));
        if (tableRules == null) {
            return Collections.emptyList();
        }
        List<ResolvedDigestRule> rules = tableRules.get(normalizeTable(table));
        return rules == null ? Collections.<ResolvedDigestRule>emptyList() : rules;
    }

    public static boolean hasDigest(String table, String datasourceId) {
        return !getRules(table, datasourceId).isEmpty();
    }

    public static void clear() {
        RULES.clear();
    }

    private static String normalizeDatasourceId(String datasourceId) {
        return StrUtil.isBlank(datasourceId)
                ? DataSourceConfigManager.DEFAULT_DATASOURCE_ID
                : datasourceId;
    }

    private static String normalizeTable(String table) {
        return table.trim().toLowerCase(Locale.ROOT);
    }
}
