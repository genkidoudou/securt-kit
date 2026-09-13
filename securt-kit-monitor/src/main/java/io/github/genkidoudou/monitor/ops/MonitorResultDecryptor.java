package io.github.genkidoudou.monitor.ops;

import io.github.genkidoudou.core.TableCache;
import io.github.genkidoudou.core.crypto.FieldCryptoService;
import io.github.genkidoudou.core.crypto.FieldCryptoServiceHolder;
import io.github.genkidoudou.core.parser.dto.FieldEncryptorInfoDto;
import io.github.genkidoudou.core.parser.visitor.fieldparse.SelectExpressionColumnSupport;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Monitor 结果侧解密：按配置字段名或 SELECT 解析映射匹配列标签，不依赖 JDBC/MyBatis 拦截通道。
 *
 * <p>单表 SELECT 为最佳路径；函数投影优先用解析映射（别名/规范化表达式 → 源表.源列）。</p>
 */
@Slf4j
public final class MonitorResultDecryptor {

    private MonitorResultDecryptor() {
    }

    /**
     * 深拷贝行列表并对配置加密列解密。
     *
     * @param datasourceId    数据源 id，可为 null
     * @param tableCandidates SQL 解析出的表名；为空时尝试对行中每个列在所有已知场景下按候选表匹配
     * @param rows            库内原值行（密文）
     */
    public static List<Map<String, Object>> decryptRows(String datasourceId,
                                                        Collection<String> tableCandidates,
                                                        List<Map<String, Object>> rows) {
        return decryptRows(datasourceId, tableCandidates, rows, null);
    }

    /**
     * 深拷贝行列表并解密：优先使用 SELECT 解析出的字段映射，再回退 label==配置字段名。
     */
    public static List<Map<String, Object>> decryptRows(String datasourceId,
                                                        Collection<String> tableCandidates,
                                                        List<Map<String, Object>> rows,
                                                        List<FieldEncryptorInfoDto> selectFieldMappings) {
        if (rows == null || rows.isEmpty()) {
            return rows == null ? Collections.<Map<String, Object>>emptyList() : rows;
        }
        Map<String, FieldEncryptorInfoDto> labelIndex = buildLabelIndex(selectFieldMappings);
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>(rows.size());
        for (Map<String, Object> row : rows) {
            out.add(decryptRow(datasourceId, tableCandidates, row, labelIndex));
        }
        return out;
    }

    /**
     * 深拷贝单行并对配置加密列解密；非加密列与无法匹配的列保持原值。
     */
    public static Map<String, Object> decryptRow(String datasourceId,
                                                 Collection<String> tableCandidates,
                                                 Map<String, Object> row) {
        return decryptRow(datasourceId, tableCandidates, row, Collections.<String, FieldEncryptorInfoDto>emptyMap());
    }

    private static Map<String, Object> decryptRow(String datasourceId,
                                                  Collection<String> tableCandidates,
                                                  Map<String, Object> row,
                                                  Map<String, FieldEncryptorInfoDto> labelIndex) {
        if (row == null || row.isEmpty()) {
            return row == null ? Collections.<String, Object>emptyMap() : new LinkedHashMap<String, Object>(row);
        }
        FieldCryptoService crypto = FieldCryptoServiceHolder.get();
        Set<String> tables = normalizeTables(tableCandidates);
        Map<String, Object> copy = new LinkedHashMap<String, Object>(row.size());
        int ordinal = 0;
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            ordinal++;
            String label = entry.getKey();
            Object value = entry.getValue();
            FieldEncryptorInfoDto mapped = lookupMapping(labelIndex, label, ordinal);
            if (mapped != null) {
                copy.put(label, decryptMapped(crypto, datasourceId, mapped, value));
            } else {
                copy.put(label, decryptCell(crypto, datasourceId, tables, label, value));
            }
        }
        return copy;
    }

    /**
     * 仅解密指定表上已配置为加密的列（用于验签前处理源字段）。
     */
    public static Map<String, Object> decryptConfiguredColumns(String datasourceId,
                                                               String table,
                                                               Map<String, Object> row) {
        if (table == null || table.trim().isEmpty()) {
            return decryptRow(datasourceId, Collections.<String>emptyList(), row);
        }
        return decryptRow(datasourceId, Collections.singletonList(table.trim()), row);
    }

    private static Object decryptMapped(FieldCryptoService crypto,
                                        String datasourceId,
                                        FieldEncryptorInfoDto mapped,
                                        Object value) {
        if (value == null || !(value instanceof CharSequence)) {
            return value;
        }
        return safeDecrypt(crypto, mapped.getSourceTableName(), mapped.getSourceColumn(),
                value.toString(), datasourceId);
    }

    private static Object decryptCell(FieldCryptoService crypto,
                                      String datasourceId,
                                      Set<String> tables,
                                      String label,
                                      Object value) {
        if (value == null || label == null) {
            return value;
        }
        // 字段加密策略面向字符串；数字/日期等非文本列保持原值
        if (!(value instanceof CharSequence)) {
            return value;
        }
        String asString = value.toString();

        if (!tables.isEmpty()) {
            for (String table : tables) {
                if (crypto.needEncrypt(table, label, datasourceId)) {
                    return safeDecrypt(crypto, table, label, asString, datasourceId);
                }
            }
            return value;
        }

        // 无表候选时不猜测表名，避免误伤同名列
        log.debug("Skip decrypt for column {} without table candidates", label);
        return value;
    }

    private static String safeDecrypt(FieldCryptoService crypto,
                                      String table,
                                      String column,
                                      String cipher,
                                      String datasourceId) {
        try {
            return crypto.decrypt(table, column, cipher, datasourceId);
        } catch (Exception e) {
            log.warn("Monitor result decrypt failed [table={}, column={}]: {}",
                    table, column, e.getMessage());
            return cipher;
        }
    }

    private static Map<String, FieldEncryptorInfoDto> buildLabelIndex(List<FieldEncryptorInfoDto> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, FieldEncryptorInfoDto> index = new LinkedHashMap<String, FieldEncryptorInfoDto>();
        for (FieldEncryptorInfoDto dto : mappings) {
            if (dto == null || dto.getSourceTableName() == null || dto.getSourceColumn() == null) {
                continue;
            }
            putIndex(index, dto.getColumnName(), dto);
            putIndex(index, SelectExpressionColumnSupport.normalizeLabel(dto.getColumnName()), dto);
            putIndex(index, SelectExpressionColumnSupport.stripWhitespaceLower(dto.getColumnName()), dto);
            if (dto.getResultColumnIndex() != null) {
                index.put("#" + dto.getResultColumnIndex(), dto);
            }
        }
        return index;
    }

    private static void putIndex(Map<String, FieldEncryptorInfoDto> index, String key, FieldEncryptorInfoDto dto) {
        if (key == null || key.isEmpty()) {
            return;
        }
        index.putIfAbsent(key.toLowerCase(Locale.ROOT), dto);
    }

    private static FieldEncryptorInfoDto lookupMapping(Map<String, FieldEncryptorInfoDto> index,
                                                       String label,
                                                       int ordinal) {
        if (index == null || index.isEmpty() || label == null) {
            return null;
        }
        FieldEncryptorInfoDto hit = index.get(label.toLowerCase(Locale.ROOT));
        if (hit != null) {
            return hit;
        }
        hit = index.get(SelectExpressionColumnSupport.normalizeLabel(label));
        if (hit != null) {
            return hit;
        }
        hit = index.get(SelectExpressionColumnSupport.stripWhitespaceLower(label));
        if (hit != null) {
            return hit;
        }
        return index.get("#" + ordinal);
    }

    private static Set<String> normalizeTables(Collection<String> tableCandidates) {
        if (tableCandidates == null || tableCandidates.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> out = new LinkedHashSet<String>();
        for (String raw : tableCandidates) {
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            String t = raw.trim();
            // strip quotes / schema prefix lightly: use last segment
            if (t.contains(".")) {
                t = t.substring(t.lastIndexOf('.') + 1);
            }
            t = t.replace("\"", "").replace("`", "").replace("[", "").replace("]", "");
            if (!t.isEmpty()) {
                out.add(t);
            }
            // also keep lower for case-insensitive configs that store lowercase
            out.add(t.toLowerCase(Locale.ROOT));
        }
        return out;
    }

    /**
     * 判断列标签是否对应某表的加密字段（测试与文档用）。
     */
    public static boolean isConfiguredEncryptColumn(String table, String column, String datasourceId) {
        if (table == null || column == null) {
            return false;
        }
        try {
            Map<?, ?> fields = TableCache.getTableFieldEncryptInfo(table, datasourceId);
            if (fields == null || fields.isEmpty()) {
                return false;
            }
            for (Object key : fields.keySet()) {
                if (key != null && column.equalsIgnoreCase(String.valueOf(key))) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
