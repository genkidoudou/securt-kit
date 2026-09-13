package io.github.genkidoudou.core.digest;

import io.github.genkidoudou.core.config.DigestConfigRegistry;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bridges decrypted JDBC result rows to digest verification.
 */
public final class DigestReadSupport {

    private DigestReadSupport() {
    }

    public static Set<String> requiredColumns(Set<String> tables, String datasourceId) {
        if (tables == null || tables.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> columns = new LinkedHashSet<String>();
        for (String table : tables) {
            List<ResolvedDigestRule> rules = DigestConfigRegistry.getRules(table, datasourceId);
            for (ResolvedDigestRule rule : rules) {
                if (rule.isVerifyOnRead()) {
                    columns.addAll(rule.getSourceFields());
                    columns.add(rule.getTargetField());
                }
            }
        }
        return columns;
    }

    public static void verifyResultRow(Set<String> tables,
                                       String datasourceId,
                                       Map<String, String> columnValues) {
        if (tables == null || tables.isEmpty()) {
            return;
        }
        Map<String, String> values = columnValues == null
                ? Collections.<String, String>emptyMap()
                : columnValues;
        DigestService service = new DigestService();
        for (String table : tables) {
            service.verifyRow(table, datasourceId, values, values);
        }
    }
}
